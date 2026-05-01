package com.mikepenz.agentbelay.usage.scanner

import co.touchlab.kermit.Logger
import com.mikepenz.agentbelay.model.Source
import com.mikepenz.agentbelay.model.UsageRecord
import com.mikepenz.agentbelay.usage.ScanCursor
import com.mikepenz.agentbelay.usage.UsageScanner
import kotlinx.datetime.Instant
import java.io.File
import java.sql.DriverManager

/**
 * OpenCode (sst/opencode) stores everything in a single SQLite database at
 * `~/.opencode/data/opencode.db`. Per-message token usage lives on the
 * `messages` table (Drizzle ORM). Schema names vary slightly across releases —
 * the queries below probe column-info and adapt.
 *
 * We use the database's own `mtime` plus a `MAX(updated_at)` watermark stored
 * in the cursor's `last_offset` field to decide when to re-query. Because
 * SQLite is content-addressable here, dedup is keyed by message id when
 * present, otherwise by `(session, ts, in/out)`.
 */
class OpenCodeUsageScanner(
    private val dbPathOverride: File? = null,
) : UsageScanner {
    private val logger = Logger.withTag("OpenCodeUsageScanner")
    override val source: Source = Source.OPENCODE

    override fun scan(cursors: Map<String, ScanCursor>): List<UsageRecord> {
        val dbFile = dbPathOverride ?: File(ScannerSupport.homeDir(), ".opencode/data/opencode.db")
        if (!dbFile.exists() || !dbFile.isFile) return emptyList()
        val cursor = cursors[dbFile.absolutePath]
        // mtime + last-known watermark guard so re-scans of an idle DB are cheap.
        if (cursor != null && cursor.lastMtimeMillis >= dbFile.lastModified()) return emptyList()

        return runCatching { read(dbFile, cursor) }
            .onFailure { logger.w(it) { "OpenCode scan failed: ${it.message}" } }
            .getOrDefault(emptyList())
    }

    private fun read(dbFile: File, cursor: ScanCursor?): List<UsageRecord> {
        val out = mutableListOf<UsageRecord>()
        val url = "jdbc:sqlite:${dbFile.absolutePath}"
        DriverManager.getConnection(url).use { conn ->
            // Discover the messages table + relevant columns without assuming a
            // specific OpenCode release. Newer schemas use `messages` plus a
            // `tokens` JSON blob; older schemas split into discrete columns.
            val tableName = listOf("messages", "message_table", "Message").firstOrNull { table ->
                conn.metaData.getColumns(null, null, table, null).use { rs -> rs.next() }
            } ?: return emptyList()

            val cols = mutableSetOf<String>()
            conn.metaData.getColumns(null, null, tableName, null).use { rs ->
                while (rs.next()) cols.add(rs.getString("COLUMN_NAME").lowercase())
            }
            fun col(name: String): String? = if (name.lowercase() in cols) name else null

            val tsCol = col("created_at") ?: col("createdAt") ?: col("timestamp") ?: col("ts") ?: return emptyList()
            val sessionCol = col("session_id") ?: col("sessionId") ?: col("session") ?: return emptyList()
            val inputCol = col("input_tokens") ?: col("input") ?: col("tokens_input")
            val outputCol = col("output_tokens") ?: col("output") ?: col("tokens_output")
            val cacheReadCol = col("cache_read") ?: col("cache_read_tokens") ?: col("cacheRead")
            val cacheWriteCol = col("cache_write") ?: col("cache_write_tokens") ?: col("cacheWrite")
            val reasoningCol = col("reasoning") ?: col("reasoning_tokens")
            val modelCol = col("model")
            val idCol = col("id") ?: col("message_id")
            val roleCol = col("role")

            if (inputCol == null && outputCol == null) return emptyList()

            val sinceMillis = cursor?.lastOffset ?: 0L
            val select = buildString {
                append("SELECT ")
                append(listOfNotNull(idCol, sessionCol, tsCol, modelCol, inputCol, outputCol, cacheReadCol, cacheWriteCol, reasoningCol, roleCol).joinToString(", "))
                append(" FROM ").append(tableName)
                append(" WHERE ").append(tsCol).append(" > ?")
                if (roleCol != null) append(" AND lower(").append(roleCol).append(") LIKE 'assistant%'")
                append(" ORDER BY ").append(tsCol).append(" ASC")
            }
            var maxTsMillis = sinceMillis
            conn.prepareStatement(select).use { ps ->
                ps.setLong(1, sinceMillis)
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        val tsMillis = rs.getLong(tsCol)
                        if (tsMillis > maxTsMillis) maxTsMillis = tsMillis
                        val input = inputCol?.let { rs.getLong(it) } ?: 0L
                        val output = outputCol?.let { rs.getLong(it) } ?: 0L
                        val cacheRead = cacheReadCol?.let { rs.getLong(it) } ?: 0L
                        val cacheWrite = cacheWriteCol?.let { rs.getLong(it) } ?: 0L
                        val reasoning = reasoningCol?.let { rs.getLong(it) } ?: 0L
                        if (input + output + cacheRead + cacheWrite + reasoning == 0L) continue
                        val model = modelCol?.let { rs.getString(it) }
                        val sessionId = rs.getString(sessionCol) ?: continue
                        val id = idCol?.let { rs.getString(it) } ?: "$tsMillis:$input:$output"
                        out.add(
                            UsageRecord(
                                harness = source,
                                sessionId = sessionId,
                                timestamp = Instant.fromEpochMilliseconds(tsMillis),
                                model = model,
                                inputTokens = input,
                                outputTokens = output,
                                cacheReadTokens = cacheRead,
                                cacheWriteTokens = cacheWrite,
                                reasoningTokens = reasoning,
                                sourceFile = dbFile.absolutePath,
                                sourceOffset = tsMillis,
                                dedupKey = "opencode:${dbFile.absolutePath}:$id",
                            )
                        )
                    }
                }
            }
        }
        return out
    }
}
