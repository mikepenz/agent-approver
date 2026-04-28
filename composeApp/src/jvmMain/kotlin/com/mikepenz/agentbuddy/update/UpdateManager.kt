package com.mikepenz.agentbuddy.update

import com.mikepenz.agentbuddy.VERSION
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
 * hardcoded to the `mikepenz/agent-buddy` GitHub repo — never read from settings
 * or user input — so a compromised settings file cannot redirect the updater
 * to a malicious host. SHA-512 verification of the downloaded installer happens
 * inside [NucleusUpdater.downloadUpdate]; the UI never sees the bytes.
 */
class UpdateManager(private val scope: CoroutineScope) {

    init {
        // Nucleus's `ExecutableRuntime.type()` reads `-Dnucleus.executable.type`
        // or a `.nucleus-executable-type` marker file next to the launcher,
        // both of which are populated by the Nucleus plugin's *packaging*
        // task. Our CI bypasses that task — it runs `createDistributable`
        // and packages DMG/ZIP/MSI/DEB manually — so neither signal is
        // present in installed builds, and `isUpdateSupported()` would
        // wrongly return `false`.
        //
        // Set the system property here, but ONLY when running inside a real
        // jpackage installation (`jpackage.app-version` is injected by
        // jpackage launchers exclusively — never in dev / IDE / `java -jar`,
        // so the dev-build gate is preserved). Do NOT set
        // `config.executableType`: on macOS the runtime forces it null so
        // the FileSelector can prefer `.zip` over `.dmg` for silent
        // in-place updates (see Nucleus NucleusUpdater.kt comment block
        // around the `format` resolution). Setting it would break that.
        if (System.getProperty("jpackage.app-version") != null &&
            System.getProperty("nucleus.executable.type") == null
        ) {
            val osName = System.getProperty("os.name", "").lowercase()
            val type = when {
                "mac" in osName -> "dmg"
                "win" in osName -> "msi"
                "linux" in osName -> "deb"
                else -> null
            }
            if (type != null) System.setProperty("nucleus.executable.type", type)
        }
    }

    private val updater = NucleusUpdater {
        provider = GitHubProvider(owner = "mikepenz", repo = "agent-buddy")
        currentVersion = VERSION
        channel = "latest"
        allowDowngrade = false
        allowPrerelease = false
    }

    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private var inFlight: Job? = null

    val isSupported: Boolean get() = updater.isUpdateSupported()

    val currentVersion: String get() = VERSION

    fun check() {
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
}
