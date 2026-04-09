package com.mikepenz.agentapprover.storage

import co.touchlab.kermit.Logger
import com.mikepenz.agentapprover.model.*
import kotlinx.datetime.Instant
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

open class DatabaseStorage(
    private val dataDir: String,
    private val maxEntries: Int = 1000,
) {
    private val logger = Logger.withTag("DatabaseStorage")
    private val connection: Connection

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val toolInputSerializer = MapSerializer(String.serializer(), JsonElement.serializer())

    init {
        val dir = File(dataDir)
        if (!dir.exists()) dir.mkdirs()
        connection = DriverManager.getConnection("jdbc:sqlite:${File(dataDir, "agent-approver.db").absolutePath}")
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
                    requested_at      TEXT NOT NULL,
                    decided_at        TEXT NOT NULL,
                    tool_input_json   TEXT NOT NULL DEFAULT '{}'
                )
                """.trimIndent()
            )
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_history_decided_at ON history(decided_at)")
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_history_type ON history(type)")
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_history_session_id ON history(session_id)")
        }
        // Migrate older databases that pre-date the tool_input_json column.
        if (!hasColumn("history", "tool_input_json")) {
            connection.createStatement().use { stmt ->
                stmt.executeUpdate("ALTER TABLE history ADD COLUMN tool_input_json TEXT NOT NULL DEFAULT '{}'")
            }
        }
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

    open fun insert(result: ApprovalResult) {
        val type = if (result.protectionModule != null) "protection" else "approval"
        val sql = """
            INSERT OR REPLACE INTO history (
                id, type, source, tool_name, tool_type, session_id, cwd,
                decision, feedback,
                risk_level, risk_label, risk_message, risk_source,
                protection_module, protection_rule, protection_detail,
                raw_request_json, raw_response_json,
                requested_at, decided_at, tool_input_json
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
            ps.setString(9, result.feedback)
            result.riskAnalysis?.let {
                ps.setInt(10, it.risk)
                ps.setString(11, it.label)
                ps.setString(12, it.message)
                ps.setString(13, it.source)
            } ?: run {
                ps.setNull(10, java.sql.Types.INTEGER)
                ps.setNull(11, java.sql.Types.VARCHAR)
                ps.setNull(12, java.sql.Types.VARCHAR)
                ps.setNull(13, java.sql.Types.VARCHAR)
            }
            ps.setString(14, result.protectionModule)
            ps.setString(15, result.protectionRule)
            ps.setString(16, result.protectionDetail)
            ps.setString(17, result.request.rawRequestJson)
            ps.setString(18, result.rawResponseJson)
            ps.setString(19, result.request.timestamp.toString())
            ps.setString(20, result.decidedAt.toString())
            ps.setString(21, json.encodeToString(toolInputSerializer, result.request.hookInput.toolInput))
            ps.executeUpdate()
        }

        pruneOldEntries()
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

    fun loadAll(): List<ApprovalResult> {
        val results = mutableListOf<ApprovalResult>()
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT * FROM history ORDER BY decided_at DESC").use { rs ->
                while (rs.next()) {
                    results.add(rowToApprovalResult(rs))
                }
            }
        }
        return results
    }

    fun loadByType(type: String): List<ApprovalResult> {
        val results = mutableListOf<ApprovalResult>()
        connection.prepareStatement("SELECT * FROM history WHERE type = ? ORDER BY decided_at DESC").use { ps ->
            ps.setString(1, type)
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    results.add(rowToApprovalResult(rs))
                }
            }
        }
        return results
    }

    fun updateRawResponse(requestId: String, rawResponseJson: String) {
        connection.prepareStatement("UPDATE history SET raw_response_json = ? WHERE id = ?").use { ps ->
            ps.setString(1, rawResponseJson)
            ps.setString(2, requestId)
            ps.executeUpdate()
        }
    }

    fun clearAll() {
        connection.createStatement().use { stmt ->
            stmt.executeUpdate("DELETE FROM history")
        }
    }

    fun count(): Int {
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT COUNT(*) FROM history").use { rs ->
                rs.next()
                return rs.getInt(1)
            }
        }
    }

    fun close() {
        try {
            if (!connection.isClosed) {
                connection.close()
            }
        } catch (e: Exception) {
            logger.w { "Failed to close database connection: ${e.message}" }
        }
    }

    fun migrateFromJson(dataDir: String) {
        val historyFile = File(dataDir, "history.json")
        if (!historyFile.exists()) {
            logger.i { "No history.json found, skipping migration" }
            return
        }

        try {
            val serializer = ListSerializer(ApprovalResult.serializer())
            val entries = json.decodeFromString(serializer, historyFile.readText())
            logger.i { "Migrating ${entries.size} entries from history.json to SQLite" }

            for (entry in entries) {
                insert(entry)
            }

            val migratedFile = File(dataDir, "history.json.migrated")
            historyFile.renameTo(migratedFile)
            logger.i { "Migration complete, renamed history.json to history.json.migrated" }
        } catch (e: Exception) {
            logger.e { "Failed to migrate history.json: ${e.message}" }
        }
    }

    private fun rowToApprovalResult(rs: ResultSet): ApprovalResult {
        val riskLevel = rs.getInt("risk_level")
        val riskAnalysis = if (rs.wasNull()) null else RiskAnalysis(
            risk = riskLevel,
            label = rs.getString("risk_label") ?: "",
            message = rs.getString("risk_message") ?: "",
            source = rs.getString("risk_source") ?: "",
        )

        val toolInput: Map<String, JsonElement> = rs.getString("tool_input_json")
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
            rawRequestJson = rs.getString("raw_request_json"),
        )

        return ApprovalResult(
            request = request,
            decision = Decision.valueOf(rs.getString("decision")),
            feedback = rs.getString("feedback"),
            riskAnalysis = riskAnalysis,
            rawResponseJson = rs.getString("raw_response_json"),
            decidedAt = Instant.parse(rs.getString("decided_at")),
            protectionModule = rs.getString("protection_module"),
            protectionRule = rs.getString("protection_rule"),
            protectionDetail = rs.getString("protection_detail"),
        )
    }
}
