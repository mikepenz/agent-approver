package com.mikepenz.agentbelay.usage

import co.touchlab.kermit.Logger
import com.mikepenz.agentbelay.state.AppStateManager
import com.mikepenz.agentbelay.storage.DatabaseStorage
import com.mikepenz.agentbelay.usage.pricing.CostCalculator
import com.mikepenz.agentbelay.usage.pricing.LiteLlmSource
import com.mikepenz.agentbelay.usage.pricing.PricingTable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Periodically drives every [UsageScanner], computes cost via [PricingTable],
 * and writes the resulting [com.mikepenz.agentbelay.model.UsageRecord]s into
 * SQLite. Emits a tick on [scans] after each successful pass so the Usage UI
 * can refresh.
 *
 * The first pass loads pricing — bundled snapshot if the network/cache lookup
 * fails — so the scanner is never blocked on remote data.
 */
class UsageIngestService(
    private val scope: CoroutineScope,
    private val scanners: List<UsageScanner>,
    private val storage: DatabaseStorage,
    private val stateManager: AppStateManager,
    private val pricingSource: LiteLlmSource,
    private val pollInterval: Duration = 60.seconds,
) {
    private val logger = Logger.withTag("UsageIngestService")
    private val mutex = Mutex()
    private var job: Job? = null
    @Volatile private var pricing: PricingTable = PricingTable.EMPTY

    private val _scans = MutableSharedFlow<Long>(replay = 1, extraBufferCapacity = 4)
    /** Epoch-millis of the most recent successful scan. */
    val scans: SharedFlow<Long> = _scans.asSharedFlow()

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            // Load pricing once on start. Bundled fallback guarantees a non-
            // empty table; a stale or networked refresh will overlay later.
            pricing = runCatching { pricingSource.load() }
                .getOrElse {
                    logger.w(it) { "pricing load failed; using bundled" }
                    runCatching { pricingSource.loadBundled() }.getOrDefault(PricingTable.EMPTY)
                }
            logger.i { "pricing table loaded with ${pricing.size} models" }

            while (isActive) {
                if (stateManager.state.value.settings.usageTrackingEnabled) {
                    runCatching { runOnePass() }
                        .onFailure { logger.w(it) { "scan pass failed: ${it.message}" } }
                }
                delay(pollInterval)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    /**
     * Force-trigger a single scan pass. Safe to call even when the loop is
     * running — the underlying mutex prevents overlapping passes.
     */
    suspend fun refreshNow(): Int = withContext(Dispatchers.IO) { runOnePass() }

    private suspend fun runOnePass(): Int = mutex.withLock {
        // No gate here on purpose: the `usageTrackingEnabled` setting only
        // disables the auto-refresh loop. A user-triggered `refreshNow()`
        // (e.g. the Usage tab's Refresh button) should always run, even when
        // background scanning is off.
        var totalInserted = 0
        for (scanner in scanners) {
            val cursors = storage.loadUsageCursors(scanner.source)
            val raw = runCatching { scanner.scan(cursors) }
                .onFailure { logger.w(it) { "${scanner.source} scan failed: ${it.message}" } }
                .getOrDefault(emptyList())
            if (raw.isEmpty()) continue

            // Stamp cost using the resolved pricing tier.
            val priced = raw.map { rec ->
                val tier = pricing.lookup(rec.model)
                if (tier == null) rec else rec.copy(
                    costUsd = CostCalculator.cost(
                        pricing = tier,
                        inputTokens = rec.inputTokens,
                        outputTokens = rec.outputTokens,
                        cacheReadTokens = rec.cacheReadTokens,
                        cacheWriteTokens = rec.cacheWriteTokens,
                        reasoningTokens = rec.reasoningTokens,
                    ),
                )
            }
            val inserted = storage.insertUsageRecords(priced)
            totalInserted += inserted

            // Update cursors per file: take the latest offset / mtime we saw.
            priced.groupBy { it.sourceFile }.forEach { (path, records) ->
                val maxOffset = records.maxOf { it.sourceOffset }
                val file = java.io.File(path)
                val mtime = if (file.exists()) file.lastModified() else System.currentTimeMillis()
                storage.upsertUsageCursor(
                    scanner.source,
                    ScanCursor(
                        sourceFile = path,
                        // For OpenCode the "offset" is actually a watermark
                        // millis value (set by the scanner itself); keep it.
                        lastOffset = maxOffset,
                        lastMtimeMillis = mtime,
                    ),
                )
            }
        }
        if (totalInserted > 0) {
            logger.i { "ingested $totalInserted usage records" }
        }
        _scans.tryEmit(System.currentTimeMillis())
        totalInserted
    }
}
