package com.mikepenz.agentbelay.update

import com.mikepenz.agentbelay.VERSION
import io.github.kdroidfilter.nucleus.updater.NucleusUpdater
import io.github.kdroidfilter.nucleus.updater.UpdateInfo
import io.github.kdroidfilter.nucleus.updater.UpdateResult
import io.github.kdroidfilter.nucleus.updater.provider.GitHubProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

sealed class UpdateUiState {
    data object Idle : UpdateUiState()
    data object Checking : UpdateUiState()
    data object UpToDate : UpdateUiState()
    data class Available(val version: String, val info: UpdateInfo) : UpdateUiState()
    data class Downloading(val percent: Int) : UpdateUiState()
    data class Ready(val version: String, val file: File) : UpdateUiState()
    data class Failed(val message: String) : UpdateUiState()
}

/**
 * Wraps [NucleusUpdater] with a [StateFlow] surface for Compose. The provider is
 * hardcoded to the `mikepenz/agent-belay` GitHub repo — never read from settings
 * or user input — so a compromised settings file cannot redirect the updater
 * to a malicious host. SHA-512 verification of the downloaded installer happens
 * inside [NucleusUpdater.downloadUpdate]; the UI never sees the bytes.
 */
open class UpdateManager(
    private val scope: CoroutineScope,
    private val allowPrereleaseProvider: () -> Boolean = { false },
) {

    @Volatile
    private var cachedAllowPrerelease: Boolean = allowPrereleaseProvider()

    @Volatile
    private var _updater: NucleusUpdater = buildUpdater(cachedAllowPrerelease)

    private fun buildUpdater(allowPrerelease: Boolean) = NucleusUpdater {
        provider = GitHubProvider(owner = "mikepenz", repo = "agent-belay")
        currentVersion = VERSION
        channel = "latest"
        allowDowngrade = false
        this.allowPrerelease = allowPrerelease
    }

    private val updater: NucleusUpdater
        get() {
            val current = allowPrereleaseProvider()
            if (current != cachedAllowPrerelease) {
                cachedAllowPrerelease = current
                _updater = buildUpdater(current)
            }
            return _updater
        }

    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    open val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private var inFlight: Job? = null

    open val isSupported: Boolean get() = updater.isUpdateSupported()

    val currentVersion: String get() = VERSION

    open fun check() {
        val current = _state.value
        if (current is UpdateUiState.Checking || current is UpdateUiState.Downloading) return
        inFlight?.cancel()
        inFlight = scope.launch {
            _state.value = UpdateUiState.Checking
            try {
                when (val r = updater.checkForUpdates()) {
                    is UpdateResult.Available ->
                        _state.value = UpdateUiState.Available(r.info.version, r.info)
                    is UpdateResult.NotAvailable ->
                        _state.value = UpdateUiState.UpToDate
                    is UpdateResult.Error ->
                        _state.value = UpdateUiState.Failed(r.exception.message ?: "Update check failed")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                _state.value = UpdateUiState.Failed(e.message ?: "Update check failed")
            }
        }
    }

    fun downloadAvailable() {
        val s = _state.value
        if (s is UpdateUiState.Available) download(s.info)
    }

    fun installCurrent() {
        val s = _state.value
        if (s is UpdateUiState.Ready) installAndRestart(s.file)
    }

    fun download(info: UpdateInfo) {
        if (_state.value is UpdateUiState.Downloading) return
        inFlight?.cancel()
        inFlight = scope.launch {
            _state.value = UpdateUiState.Downloading(0)
            try {
                updater.downloadUpdate(info).collect { progress ->
                    val file = progress.file
                    if (file != null) {
                        _state.value = UpdateUiState.Ready(info.version, file)
                    } else {
                        _state.value = UpdateUiState.Downloading(progress.percent.toInt())
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                _state.value = UpdateUiState.Failed(e.message ?: "Download failed")
            }
        }
    }

    /**
     * Triggers the OS installer and exits the current process. Caller must pass
     * ONLY the [File] received in [UpdateUiState.Ready] — that file has already
     * been SHA-512-verified against the YML metadata. Never pass an arbitrary
     * path: the runtime does not re-verify at the install boundary.
     */
    fun installAndRestart(file: File) {
        updater.installAndRestart(file)
    }

    fun reset() {
        inFlight?.cancel()
        _state.value = UpdateUiState.Idle
    }

    /**
     * Dev-mode-only: drives the state flow into [UpdateUiState.Available]
     * with a fake [UpdateInfo] so the in-app banner can be exercised
     * without an actual GitHub release. The associated [UpdateInfo] has no
     * downloadable file, so calling [downloadAvailable] from this state
     * will fail at the network layer — the intent is purely to verify the
     * banner UI and dismiss/install plumbing.
     *
     * Gated by callers (Main.kt fires it only when `--dev` is set); the
     * function itself does no checks so tests can drive it directly too.
     */
    fun simulateAvailable(version: String) {
        inFlight?.cancel()
        _state.value = UpdateUiState.Available(
            version = version,
            info = UpdateInfo(
                version = version,
                releaseDate = "1970-01-01",
                files = emptyList(),
                currentFile = io.github.kdroidfilter.nucleus.updater.UpdateFile(
                    url = "https://example.invalid/$version",
                    sha512 = "0".repeat(128),
                    size = 0L,
                    blockMapSize = null,
                    fileName = "AgentBelay-$version",
                ),
            ),
        )
    }
}
