package com.mikepenz.agentbelay.storage

import co.touchlab.kermit.Logger
import com.mikepenz.agentbelay.model.*
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

open class DatabaseStorage(
    private val dataDir: String,
    private val maxEntries: Int = 2500,
    /**
     * Optional column-level cipher applied to sensitive columns
     * (`raw_request_json`, `raw_response_json`, `tool_input_json`, `feedback`,
     * `protection_detail`, `risk_message`, `risk_raw_response`). Pass `null` to
     * disable encryption
     * — this is the test default so existing tests continue to pass without
     * key plumbing. Production wiring in `AppProviders` always supplies a
     * real cipher backed by [DbKeyManager].
     */
    private val cipher: ColumnCipher? = null,
) {
    private val logger = Logger.withTag("DatabaseStorage")
    private val connection: Connection

    /**
     * Serializes ALL access to the shared JDBC [connection]. SQLite JDBC connections
     * are not thread-safe, and stats queries now run on `Dispatchers.IO` while writes
     * may originate from coroutine resolves on a different thread. Every public method
     * that touches `connection` must hold this lock.
     */
    private val connectionLock = Any()

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val toolInputSerializer = MapSerializer(String.serializer(), JsonElement.serializer())
    private val redactionHitsSerializer = ListSerializer(RedactionHit.serializer())

    // ---- Column encryption helpers ---------------------------------------
    // Each helper is a no-op when [cipher] is null, so test code that omits
    // the cipher continues to read/write plaintext unchanged.
    private fun enc(value: String): String = cipher?.encrypt(value) ?: value
    private fun encNullable(value: String?): String? = cipher?.encryptNullable(value) ?: value
    private fun dec(value: String): String = cipher?.decrypt(value) ?: value
    private fun decNullable(value: String?): String? = cipher?.decryptNullable(value) ?: value

    init {
        val dir = File(dataDir)
        if (!dir.exists()) dir.mkdirs()
        connection = DriverManager.getConnection("jdbc:sqlite:${File(dataDir, "agent-belay.db").absolutePath}")
        connection.autoCommit = true
        createSchema()
    }

    private fun createSchema() {
        connection.createStatement().use { stmt ->
            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS history (
                    id                TEXT PRIMARY KEY,
                    type              TEXT NOT NULL,
                    source            TEXT NOT NULL,
                    tool_name         TEXT NOT NULL,
                    tool_type         TEXT NOT NULL,
                    session_id        TEXT NOT NULL,
                    cwd               TEXT NOT NULL DEFAULT '',
                    decision          TEXT NOT NULL,
                    feedback          TEXT,
                    risk_level        INTEGER,
                    risk_label        TEXT,
                    risk_message      TEXT,
                    risk_source       TEXT,
                    protection_module TEXT,
                    protection_rule   TEXT,
                    protection_detail TEXT,
                    raw_request_json  TEXT NOT NULL,
                    raw_response_json TEXT,
                    risk_raw_response TEXT,
                    requested_at      TEXT NOT NULL,
                    decided_at        TEXT NOT NULL,
                    tool_input_json   TEXT NOT NULL DEFAULT '{}'
                )
                """.trimIndent()
            )
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_history_decided_at ON history(decided_at)")
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_history_type ON history(type)")
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_history_session_id ON history(session_id)")
            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS usage_records (
                    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
                    harness             TEXT NOT NULL,
                    session_id          TEXT NOT NULL,
                    ts_millis           INTEGER NOT NULL,
                    model               TEXT,
                    input_tokens        INTEGER NOT NULL DEFAULT 0,
                    output_tokens       INTEGER NOT NULL DEFAULT 0,
                    cache_read_tokens   INTEGER NOT NULL DEFAULT 0,
                    cache_write_tokens  INTEGER NOT NULL DEFAULT 0,
                    reasoning_tokens    INTEGER NOT NULL DEFAULT 0,
                    cost_usd            REAL,
                    duration_ms         INTEGER,
                    source_file         TEXT NOT NULL,
                    source_offset       INTEGER NOT NULL,
                    dedup_key           TEXT NOT NULL UNIQUE
                )
                """.trimIndent()
            )
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_usage_ts ON usage_records(ts_millis)")
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_usage_harness_ts ON usage_records(harness, ts_millis)")
            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS usage_scan_cursor (
                    harness         TEXT NOT NULL,
                    source_file     TEXT NOT NULL,
                    last_offset     INTEGER NOT NULL,
                    last_mtime_ms   INTEGER NOT NULL,
                    PRIMARY KEY (harness, source_file)
                )
                """.trimIndent()
            )
        }
        // Migrate older databases that pre-date the tool_input_json column.
        if (!hasColumn("history", "tool_input_json")) {
            connection.createStatement().use { stmt ->
                stmt.executeUpdate("ALTER TABLE history ADD COLUMN tool_input_json TEXT NOT NULL DEFAULT '{}'")
            }
        }
        // Migrate older databases that pre-date the risk_raw_response column.
        if (!hasColumn("history", "risk_raw_response")) {
            connection.createStatement().use { stmt ->
                stmt.executeUpdate("ALTER TABLE history ADD COLUMN risk_raw_response TEXT")
            }
        }
        // Migrate older databases that pre-date the redaction_hits_json column.
        if (!hasColumn("history", "redaction_hits_json")) {
            connection.createStatement().use { stmt ->
                stmt.executeUpdate("ALTER TABLE history ADD COLUMN redaction_hits_json TEXT")
            }
        }
        backfillToolInputJson()
    }

    private fun hasColumn(table: String, column: String): Boolean {
        connection.createStatement().use { stmt ->
            stmt.executeQuery("PRAGMA table_info($table)").use { rs ->
                while (rs.next()) {
                    if (rs.getString("name") == column) return true
                }
            }
        }
        return false
    }

    /**
     * Backfills `tool_input_json` for rows that still have the migration
     * default `'{}'`. Extracts `tool_input` / `toolInput` from the stored
     * `raw_request_json` payload so that history entries created before the
     * column existed display their tool content correctly.
     */
    private fun backfillToolInputJson() {
        val rows = mutableListOf<Pair<String, String>>()
        connection.prepareStatement(
            "SELECT id, raw_request_json FROM history WHERE tool_input_json = '{}'"
        ).use { ps ->
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    rows.add(rs.getString("id") to rs.getString("raw_request_json"))
                }
            }
        }
        if (rows.isEmpty()) return

        var backfilled = 0
        connection.prepareStatement(
            "UPDATE history SET tool_input_json = ? WHERE id = ?"
        ).use { ps ->
            for ((id, storedRawJson) in rows) {
                // The raw_request_json column may be encrypted (if this row was
                // written after column encryption was enabled) or plaintext (if
                // this row predates encryption). `dec()` is a transparent
                // pass-through for unprefixed legacy values.
                val rawJson = try {
                    dec(storedRawJson)
                } catch (e: Exception) {
                    logger.w { "Skipping backfill for row $id: cannot decrypt raw_request_json (${e.message})" }
                    continue
                }
                val extracted = extractToolInputFromRawJson(rawJson) ?: continue
                if (extracted == "{}") continue
                // Re-encrypt before persisting so the new tool_input_json column
                // matches the at-rest encryption invariant.
                ps.setString(1, enc(extracted))
                ps.setString(2, id)
                ps.addBatch()
                backfilled++
            }
            if (backfilled > 0) ps.executeBatch()
        }
        if (backfilled > 0) {
            logger.i { "Backfilled tool_input_json for $backfilled history rows" }
        }
    }

    /**
     * Extracts the tool-input JSON object from a raw request payload. Handles
     * both Claude Code (`tool_input`) and Copilot (`toolInput` / `toolArgs`)
     * payload shapes.
     */
    private fun extractToolInputFromRawJson(rawJson: String): String? {
        return try {
            val obj = json.parseToJsonElement(rawJson).jsonObject
            // snake_case (Claude Code / Copilot v1.0.21+)
            (obj["tool_input"] as? JsonObject)?.let {
                return json.encodeToString(JsonElement.serializer(), it)
            }
            // camelCase (Copilot permissionRequest v1.0.16+)
            (obj["toolInput"] as? JsonObject)?.let {
                return json.encodeToString(JsonElement.serializer(), it)
            }
            // Legacy Copilot preToolUse (toolArgs is a JSON string)
            val argsString = (obj["toolArgs"] as? JsonPrimitive)?.contentOrNull
            if (!argsString.isNullOrBlank()) {
                // Validate it's actually JSON
                json.parseToJsonElement(argsString)
                return argsString
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    open fun insert(result: ApprovalResult): Unit = synchronized(connectionLock) {
        val type = if (result.protectionModule != null) "protection" else "approval"
        val sql = """
            INSERT OR REPLACE INTO history (
                id, type, source, tool_name, tool_type, session_id, cwd,
                decision, feedback,
                risk_level, risk_label, risk_message, risk_source,
                protection_module, protection_rule, protection_detail,
                raw_request_json, raw_response_json, risk_raw_response,
                requested_at, decided_at, tool_input_json, redaction_hits_json
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        connection.prepareStatement(sql).use { ps ->
            ps.setString(1, result.request.id)
            ps.setString(2, type)
            ps.setString(3, result.request.source.name)
            ps.setString(4, result.request.hookInput.toolName)
            ps.setString(5, result.request.toolType.name)
            ps.setString(6, result.request.hookInput.sessionId)
            ps.setString(7, result.request.hookInput.cwd)
            ps.setString(8, result.decision.name)
            ps.setString(9, encNullable(result.feedback))
            result.riskAnalysis?.let {
                ps.setInt(10, it.risk)
                ps.setString(11, it.label)
                ps.setString(12, encNullable(it.message))
                ps.setString(13, it.source)
            } ?: run {
                ps.setNull(10, java.sql.Types.INTEGER)
                ps.setNull(11, java.sql.Types.VARCHAR)
                ps.setNull(12, java.sql.Types.VARCHAR)
                ps.setNull(13, java.sql.Types.VARCHAR)
            }
            ps.setString(14, result.protectionModule)
            ps.setString(15, result.protectionRule)
            ps.setString(16, encNullable(result.protectionDetail))
            ps.setString(17, enc(result.request.rawRequestJson))
            ps.setString(18, encNullable(result.rawResponseJson))
            // Encrypted at rest: the raw LLM payload echoes the prompt (working
            // dir, tool args) and the model's free-form explanation back to us.
            ps.setString(19, encNullable(result.riskAnalysis?.rawResponse))
            ps.setString(20, result.request.timestamp.toString())
            ps.setString(21, result.decidedAt.toString())
            ps.setString(22, enc(json.encodeToString(toolInputSerializer, result.request.hookInput.toolInput)))
            // Redaction hits are stored as a JSON array of {moduleId, ruleId,
            // field, count} objects. Null when no redaction was scanned.
            // Encrypted because field names + counts are mildly sensitive
            // (e.g. "stderr / aws-access-key / count=3" reveals the row's
            // tool exposed a secret) and the encryption invariant is per-row.
            if (result.redactionHits.isEmpty()) {
                ps.setNull(23, java.sql.Types.VARCHAR)
            } else {
                ps.setString(23, enc(json.encodeToString(redactionHitsSerializer, result.redactionHits)))
            }
            ps.executeUpdate()
        }

        pruneOldEntries()
    }

    fun updateRedactionHits(requestId: String, hits: List<RedactionHit>): Unit = synchronized(connectionLock) {
        connection.prepareStatement("UPDATE history SET redaction_hits_json = ? WHERE id = ?").use { ps ->
            if (hits.isEmpty()) {
                ps.setNull(1, java.sql.Types.VARCHAR)
            } else {
                ps.setString(1, enc(json.encodeToString(redactionHitsSerializer, hits)))
            }
            ps.setString(2, requestId)
            ps.executeUpdate()
        }
    }

    private fun pruneOldEntries() {
        val currentCount = count()
        if (currentCount > maxEntries) {
            connection.prepareStatement(
                """
                DELETE FROM history WHERE id NOT IN (
                    SELECT id FROM history ORDER BY decided_at DESC LIMIT ?
                )
                """.trimIndent()
            ).use { ps ->
                ps.setInt(1, maxEntries)
                ps.executeUpdate()
            }
        }
    }

    fun loadAll(): List<ApprovalResult> = synchronized(connectionLock) {
        val results = mutableListOf<ApprovalResult>()
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT * FROM history ORDER BY decided_at DESC").use { rs ->
                while (rs.next()) {
                    tryRowToApprovalResult(rs)?.let(results::add)
                }
            }
        }
        results
    }

    fun loadByType(type: String): List<ApprovalResult> = synchronized(connectionLock) {
        val results = mutableListOf<ApprovalResult>()
        connection.prepareStatement("SELECT * FROM history WHERE type = ? ORDER BY decided_at DESC").use { ps ->
            ps.setString(1, type)
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    tryRowToApprovalResult(rs)?.let(results::add)
                }
            }
        }
        results
    }

    /**
     * Wraps [rowToApprovalResult] so a single corrupt / tampered / undecryptable
     * row cannot abort an entire history load. The row's id (plaintext) is
     * captured first so the warning can identify the bad entry without
     * exposing ciphertext.
     */
    private fun tryRowToApprovalResult(rs: ResultSet): ApprovalResult? {
        val rowId = try { rs.getString("id") } catch (_: Exception) { "<unknown>" }
        return try {
            rowToApprovalResult(rs)
        } catch (e: Exception) {
            logger.w { "Skipping history row $rowId: failed to deserialize (${e.javaClass.simpleName}: ${e.message})" }
            null
        }
    }

    fun updateRawResponse(requestId: String, rawResponseJson: String): Unit = synchronized(connectionLock) {
        connection.prepareStatement("UPDATE history SET raw_response_json = ? WHERE id = ?").use { ps ->
            ps.setString(1, enc(rawResponseJson))
            ps.setString(2, requestId)
            ps.executeUpdate()
        }
    }

    fun clearAll(): Unit = synchronized(connectionLock) {
        connection.createStatement().use { stmt ->
            stmt.executeUpdate("DELETE FROM history")
        }
    }

    fun count(): Int = synchronized(connectionLock) {
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT COUNT(*) FROM history").use { rs ->
                rs.next()
                rs.getInt(1)
            }
        }
    }

    /**
     * Aggregates the `history` table into a [StatsSummary] for the Statistics tab.
     *
     * Counts and protection-hit groupings are computed via SQL `GROUP BY`. Latency
     * percentiles use a Kotlin pass over the deliberated rows (manual + risk auto)
     * — SQLite has no native percentile and history is bounded by `maxEntries`,
     * so the in-memory cost is trivial.
     *
     * @param since lower bound on `decided_at` (inclusive); pass `null` for all-time.
     */
    fun queryStats(since: Instant?, until: Instant? = null): StatsSummary = synchronized(connectionLock) {
        val conditions = buildList {
            if (since != null) add("decided_at >= ?")
            if (until != null) add("decided_at < ?")
        }
        val whereClause = if (conditions.isEmpty()) "" else "WHERE ${conditions.joinToString(" AND ")}"
        fun bindBounds(ps: java.sql.PreparedStatement, startIndex: Int = 1): Int {
            var idx = startIndex
            if (since != null) ps.setString(idx++, since.toString())
            if (until != null) ps.setString(idx++, until.toString())
            return idx
        }

        // 1. Count by Decision (then collapse to DecisionGroup in Kotlin).
        val byGroup = mutableMapOf<DecisionGroup, Int>()
        var total = 0
        connection.prepareStatement(
            "SELECT decision, COUNT(*) AS c FROM history $whereClause GROUP BY decision"
        ).use { ps ->
            bindBounds(ps)
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    val decisionName = rs.getString("decision")
                    val count = rs.getInt("c")
                    val decision = runCatching { Decision.valueOf(decisionName) }.getOrNull() ?: continue
                    byGroup.merge(decision.group(), count, Int::plus)
                    total += count
                }
            }
        }

        // 2. Per-day breakdown. SQLite's `date()` understands ISO-8601 strings.
        val dailyByDate = sortedMapOf<String, MutableMap<DecisionGroup, Int>>()
        connection.prepareStatement(
            "SELECT date(decided_at) AS d, decision, COUNT(*) AS c FROM history $whereClause GROUP BY d, decision ORDER BY d ASC"
        ).use { ps ->
            bindBounds(ps)
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    val day = rs.getString("d") ?: continue
                    val decisionName = rs.getString("decision")
                    val count = rs.getInt("c")
                    val decision = runCatching { Decision.valueOf(decisionName) }.getOrNull() ?: continue
                    val map = dailyByDate.getOrPut(day) { mutableMapOf() }
                    map.merge(decision.group(), count, Int::plus)
                }
            }
        }
        val perDay = dailyByDate.mapNotNull { (day, groups) ->
            runCatching { LocalDate.parse(day) }.getOrNull()?.let { DailyCount(it, groups.toMap()) }
        }

        // 3. Latency for deliberated decisions only.
        val latencyByGroupRaw = mutableMapOf<DecisionGroup, MutableList<Double>>()
        val deliberatedDecisions = Decision.entries.filter { it.group().hasMeaningfulLatency }
        if (deliberatedDecisions.isNotEmpty()) {
            val placeholders = deliberatedDecisions.joinToString(",") { "?" }
            val boundsFilter = buildList {
                if (since != null) add("decided_at >= ?")
                if (until != null) add("decided_at < ?")
            }.let { if (it.isEmpty()) "" else " AND ${it.joinToString(" AND ")}" }
            connection.prepareStatement(
                "SELECT decision, requested_at, decided_at FROM history WHERE decision IN ($placeholders)$boundsFilter"
            ).use { ps ->
                deliberatedDecisions.forEachIndexed { i, d -> ps.setString(i + 1, d.name) }
                bindBounds(ps, deliberatedDecisions.size + 1)
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        val decision = runCatching { Decision.valueOf(rs.getString("decision")) }.getOrNull() ?: continue
                        val requested = runCatching { Instant.parse(rs.getString("requested_at")) }.getOrNull() ?: continue
                        val decided = runCatching { Instant.parse(rs.getString("decided_at")) }.getOrNull() ?: continue
                        val seconds = ((decided - requested).inWholeMilliseconds.coerceAtLeast(0L)) / 1000.0
                        latencyByGroupRaw.getOrPut(decision.group()) { mutableListOf() }.add(seconds)
                    }
                }
            }
        }

        val latencyByGroup = latencyByGroupRaw.mapValues { (_, samples) -> samples.toLatencyStats() }
        val latencyHistogramByGroup = latencyByGroupRaw.mapValues { (_, samples) ->
            val counts = MutableList(StatsSummary.BUCKET_LABELS.size) { 0 }
            samples.forEach { counts[StatsSummary.bucketIndex(it)]++ }
            counts.toList()
        }

        // 4. Top protection hits.
        val topProtections = mutableListOf<ProtectionHitCount>()
        val protectionBoundsClause = buildList {
            if (since != null) add("decided_at >= ?")
            if (until != null) add("decided_at < ?")
        }.let { if (it.isEmpty()) "" else " AND ${it.joinToString(" AND ")}" }
        connection.prepareStatement(
            """
            SELECT protection_module AS m, protection_rule AS r, COUNT(*) AS c
            FROM history
            WHERE protection_module IS NOT NULL$protectionBoundsClause
            GROUP BY m, r
            ORDER BY c DESC
            LIMIT 10
            """.trimIndent()
        ).use { ps ->
            bindBounds(ps)
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    topProtections.add(
                        ProtectionHitCount(
                            moduleId = rs.getString("m") ?: continue,
                            ruleId = rs.getString("r") ?: "",
                            count = rs.getInt("c"),
                        )
                    )
                }
            }
        }

        StatsSummary(
            totalDecisions = total,
            byGroup = byGroup,
            perDay = perDay,
            latencyByGroup = latencyByGroup,
            latencyHistogramByGroup = latencyHistogramByGroup,
            topProtections = topProtections,
        )
    }

    private fun List<Double>.toLatencyStats(): LatencyStats {
        if (isEmpty()) return LatencyStats(0, 0.0, 0.0, 0.0)
        val sorted = sorted()
        val avg = sorted.average()
        val p50 = sorted.percentile(0.50)
        val p90 = sorted.percentile(0.90)
        return LatencyStats(sorted.size, avg, p50, p90)
    }

    private fun List<Double>.percentile(p: Double): Double {
        // Nearest-rank percentile on a pre-sorted list. The rank is `ceil(p * N)`,
        // converted to a 0-based index by subtracting 1. For N=4 at p50 this yields
        // rank=2 → index 1 (the second-smallest sample), matching the standard
        // nearest-rank definition.
        if (isEmpty()) return 0.0
        val rank = kotlin.math.ceil(p * size).toInt().coerceIn(1, size)
        return this[rank - 1]
    }

    // ── Usage records ──────────────────────────────────────────────────────
    //
    // Stored alongside `history` but in a separate table so the existing
    // approval-history schema is untouched. Inserts use `INSERT OR IGNORE` on
    // `dedup_key` so re-scanning the same source file is idempotent.

    /**
     * Bulk-inserts [records], skipping any whose `dedupKey` already exists.
     * Returns the number of newly-inserted rows.
     */
    fun insertUsageRecords(records: List<com.mikepenz.agentbelay.model.UsageRecord>): Int =
        synchronized(connectionLock) {
            if (records.isEmpty()) return 0
            val sql = """
                INSERT OR IGNORE INTO usage_records (
                    harness, session_id, ts_millis, model,
                    input_tokens, output_tokens, cache_read_tokens, cache_write_tokens, reasoning_tokens,
                    cost_usd, duration_ms, source_file, source_offset, dedup_key
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
            var inserted = 0
            connection.prepareStatement(sql).use { ps ->
                for (rec in records) {
                    ps.setString(1, rec.harness.name)
                    ps.setString(2, rec.sessionId)
                    ps.setLong(3, rec.timestamp.toEpochMilliseconds())
                    if (rec.model == null) ps.setNull(4, java.sql.Types.VARCHAR) else ps.setString(4, rec.model)
                    ps.setLong(5, rec.inputTokens)
                    ps.setLong(6, rec.outputTokens)
                    ps.setLong(7, rec.cacheReadTokens)
                    ps.setLong(8, rec.cacheWriteTokens)
                    ps.setLong(9, rec.reasoningTokens)
                    if (rec.costUsd == null) ps.setNull(10, java.sql.Types.REAL) else ps.setDouble(10, rec.costUsd)
                    if (rec.durationMs == null) ps.setNull(11, java.sql.Types.INTEGER) else ps.setLong(11, rec.durationMs)
                    ps.setString(12, rec.sourceFile)
                    ps.setLong(13, rec.sourceOffset)
                    ps.setString(14, rec.dedupKey)
                    ps.addBatch()
                }
                inserted = ps.executeBatch().count { it >= 0 || it == java.sql.Statement.SUCCESS_NO_INFO }
            }
            inserted
        }

    fun loadUsageCursors(harness: com.mikepenz.agentbelay.model.Source): Map<String, com.mikepenz.agentbelay.usage.ScanCursor> =
        synchronized(connectionLock) {
            val out = mutableMapOf<String, com.mikepenz.agentbelay.usage.ScanCursor>()
            connection.prepareStatement(
                "SELECT source_file, last_offset, last_mtime_ms FROM usage_scan_cursor WHERE harness = ?"
            ).use { ps ->
                ps.setString(1, harness.name)
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        val file = rs.getString("source_file")
                        out[file] = com.mikepenz.agentbelay.usage.ScanCursor(
                            sourceFile = file,
                            lastOffset = rs.getLong("last_offset"),
                            lastMtimeMillis = rs.getLong("last_mtime_ms"),
                        )
                    }
                }
            }
            out
        }

    fun upsertUsageCursor(
        harness: com.mikepenz.agentbelay.model.Source,
        cursor: com.mikepenz.agentbelay.usage.ScanCursor,
    ): Unit = synchronized(connectionLock) {
        connection.prepareStatement(
            """
            INSERT INTO usage_scan_cursor (harness, source_file, last_offset, last_mtime_ms)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(harness, source_file) DO UPDATE SET
                last_offset = excluded.last_offset,
                last_mtime_ms = excluded.last_mtime_ms
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, harness.name)
            ps.setString(2, cursor.sourceFile)
            ps.setLong(3, cursor.lastOffset)
            ps.setLong(4, cursor.lastMtimeMillis)
            ps.executeUpdate()
        }
    }

    /**
     * Aggregate token / cost / request totals per harness within an optional
     * time window. Time bounds are epoch-millis; pass `null` for unbounded.
     */
    fun queryUsageTotals(
        sinceMillis: Long? = null,
        untilMillis: Long? = null,
    ): List<UsageHarnessTotals> = synchronized(connectionLock) {
        val conditions = buildList {
            if (sinceMillis != null) add("ts_millis >= ?")
            if (untilMillis != null) add("ts_millis < ?")
        }
        val where = if (conditions.isEmpty()) "" else "WHERE ${conditions.joinToString(" AND ")}"
        val sql = """
            SELECT
                harness,
                COUNT(*)                 AS requests,
                COUNT(DISTINCT session_id) AS sessions,
                COALESCE(SUM(input_tokens), 0)        AS input_tokens,
                COALESCE(SUM(output_tokens), 0)       AS output_tokens,
                COALESCE(SUM(cache_read_tokens), 0)   AS cache_read_tokens,
                COALESCE(SUM(cache_write_tokens), 0)  AS cache_write_tokens,
                COALESCE(SUM(reasoning_tokens), 0)    AS reasoning_tokens,
                COALESCE(SUM(cost_usd), 0)            AS cost_usd
            FROM usage_records
            $where
            GROUP BY harness
        """.trimIndent()
        val out = mutableListOf<UsageHarnessTotals>()
        connection.prepareStatement(sql).use { ps ->
            var idx = 1
            if (sinceMillis != null) ps.setLong(idx++, sinceMillis)
            if (untilMillis != null) ps.setLong(idx++, untilMillis)
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    val harnessStr = rs.getString("harness") ?: continue
                    val source = runCatching {
                        com.mikepenz.agentbelay.model.Source.valueOf(harnessStr)
                    }.getOrNull() ?: continue
                    out.add(
                        UsageHarnessTotals(
                            harness = source,
                            requests = rs.getInt("requests"),
                            sessions = rs.getInt("sessions"),
                            inputTokens = rs.getLong("input_tokens"),
                            outputTokens = rs.getLong("output_tokens"),
                            cacheReadTokens = rs.getLong("cache_read_tokens"),
                            cacheWriteTokens = rs.getLong("cache_write_tokens"),
                            reasoningTokens = rs.getLong("reasoning_tokens"),
                            costUsd = rs.getDouble("cost_usd"),
                        )
                    )
                }
            }
        }
        out
    }

    /**
     * Per-day request volume per harness within the window, used to render the
     * Activity sparkline on the Usage detail panel. Returned as
     * `harness -> ordered (epochDayUtc, requests)`.
     */
    fun queryUsagePerDay(
        sinceMillis: Long? = null,
        untilMillis: Long? = null,
    ): Map<com.mikepenz.agentbelay.model.Source, List<UsageDailyCount>> = synchronized(connectionLock) {
        val conditions = buildList {
            if (sinceMillis != null) add("ts_millis >= ?")
            if (untilMillis != null) add("ts_millis < ?")
        }
        val where = if (conditions.isEmpty()) "" else "WHERE ${conditions.joinToString(" AND ")}"
        val sql = """
            SELECT
                harness,
                CAST(ts_millis / 86400000 AS INTEGER) AS day,
                COUNT(*) AS requests
            FROM usage_records
            $where
            GROUP BY harness, day
            ORDER BY harness, day
        """.trimIndent()
        val out = mutableMapOf<com.mikepenz.agentbelay.model.Source, MutableList<UsageDailyCount>>()
        connection.prepareStatement(sql).use { ps ->
            var idx = 1
            if (sinceMillis != null) ps.setLong(idx++, sinceMillis)
            if (untilMillis != null) ps.setLong(idx++, untilMillis)
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    val harnessStr = rs.getString("harness") ?: continue
                    val source = runCatching {
                        com.mikepenz.agentbelay.model.Source.valueOf(harnessStr)
                    }.getOrNull() ?: continue
                    out.getOrPut(source) { mutableListOf() }.add(
                        UsageDailyCount(rs.getLong("day"), rs.getInt("requests"))
                    )
                }
            }
        }
        out
    }

    fun clearUsage(): Unit = synchronized(connectionLock) {
        connection.createStatement().use { stmt ->
            stmt.executeUpdate("DELETE FROM usage_records")
            stmt.executeUpdate("DELETE FROM usage_scan_cursor")
        }
    }

    fun usageRecordCount(): Int = synchronized(connectionLock) {
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT COUNT(*) FROM usage_records").use { rs ->
                rs.next()
                rs.getInt(1)
            }
        }
    }

    fun close(): Unit = synchronized(connectionLock) {
        try {
            if (!connection.isClosed) {
                connection.close()
            }
        } catch (e: Exception) {
            logger.w { "Failed to close database connection: ${e.message}" }
        }
    }

    private fun rowToApprovalResult(rs: ResultSet): ApprovalResult {
        val riskLevel = rs.getInt("risk_level")
        val riskAnalysis = if (rs.wasNull()) null else RiskAnalysis(
            risk = riskLevel,
            label = rs.getString("risk_label") ?: "",
            message = decNullable(rs.getString("risk_message")) ?: "",
            source = rs.getString("risk_source") ?: "",
            rawResponse = decNullable(rs.getString("risk_raw_response")),
        )

        val toolInput: Map<String, JsonElement> = decNullable(rs.getString("tool_input_json"))
            ?.takeIf { it.isNotBlank() }
            ?.let {
                try {
                    json.decodeFromString(toolInputSerializer, it)
                } catch (e: Exception) {
                    logger.w { "Failed to decode tool_input_json: ${e.message}" }
                    emptyMap()
                }
            }
            ?: emptyMap()

        val hookInput = HookInput(
            sessionId = rs.getString("session_id"),
            toolName = rs.getString("tool_name"),
            toolInput = toolInput,
            cwd = rs.getString("cwd"),
        )

        val request = ApprovalRequest(
            id = rs.getString("id"),
            source = Source.valueOf(rs.getString("source")),
            toolType = ToolType.valueOf(rs.getString("tool_type")),
            hookInput = hookInput,
            timestamp = Instant.parse(rs.getString("requested_at")),
            rawRequestJson = dec(rs.getString("raw_request_json")),
        )

        val redactionHits: List<RedactionHit> = decNullable(rs.getString("redaction_hits_json"))
            ?.takeIf { it.isNotBlank() }
            ?.let {
                try {
                    json.decodeFromString(redactionHitsSerializer, it)
                } catch (e: Exception) {
                    logger.w { "Failed to decode redaction_hits_json: ${e.message}" }
                    emptyList()
                }
            }
            ?: emptyList()

        return ApprovalResult(
            request = request,
            decision = Decision.valueOf(rs.getString("decision")),
            feedback = decNullable(rs.getString("feedback")),
            riskAnalysis = riskAnalysis,
            rawResponseJson = decNullable(rs.getString("raw_response_json")),
            decidedAt = Instant.parse(rs.getString("decided_at")),
            protectionModule = rs.getString("protection_module"),
            protectionRule = rs.getString("protection_rule"),
            protectionDetail = decNullable(rs.getString("protection_detail")),
            redactionHits = redactionHits,
        )
    }
}
