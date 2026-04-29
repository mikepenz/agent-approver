# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Agent Buddy is a Kotlin Multiplatform (JVM-only) desktop application built with Compose Multiplatform. It acts as a Claude Code hook server — receiving `PermissionRequest` hook events via HTTP, displaying them in a UI for human review, and responding with allow/deny decisions.

## Build & Test Commands

```bash
./gradlew :composeApp:jvmRun                    # Run the app
./gradlew :composeApp:jvmTest                    # Run all tests
./gradlew :composeApp:jvmTest --tests "AppStateManagerTest"  # Run a single test class
./gradlew :composeApp:jvmTest --tests "*.storage.*"          # Run tests by package
./gradlew :composeApp:jvmJar                     # Build JAR
./gradlew packageDmg                             # Package macOS DMG
./gradlew packageMsi                             # Package Windows MSI
./gradlew packageDeb                             # Package Linux DEB
```

## Architecture

### Module Structure

Single module project: `:composeApp` with `commonMain` (shared models) and `jvmMain` (application code).

### Core Flow

1. **HTTP Server** (`server/ApprovalServer.kt`) — Ktor/Netty on port 19532 (configurable). Receives `POST /approve` (Claude Code), `POST /approve-copilot` (Copilot CLI `permissionRequest`), and `POST /pre-tool-use[-copilot]` (Protection Engine pre-checks). The Copilot integration is **user-scoped** (mirroring Claude Code) — `register(port)` writes two bridge scripts under `~/.agent-buddy/` plus a single `~/.copilot/hooks/agent-buddy.json` containing both `permissionRequest` and `preToolUse` entries.
2. **Adapter** (`server/ClaudeCodeAdapter.kt`) — Parses incoming JSON into `ApprovalRequest` model.
3. **State** (`state/AppStateManager.kt`) — Single `MutableStateFlow<AppState>` is the source of truth. Pending approvals are added to state and a `CompletableDeferred<ApprovalResult>` suspends the HTTP handler coroutine until the user acts or timeout fires.
4. **UI** (`ui/`) — Compose Material3 with three tabs: Approvals, History, Settings. Tool-specific card components render different tool types (Bash, FileOperation, WebFetch, Plan, AskUserQuestion).
5. **Persistence** (`storage/`) — `HistoryStorage` writes up to 250 approval results to JSON with 10-second debounce. `SettingsStorage` persists app configuration.
6. **Risk Analysis** (`risk/RiskAnalyzer.kt`) — Optional feature that shells out to the `claude` CLI to score approval risk (1-5). Results can auto-approve (risk 1) or auto-deny (risk 5).

### Platform Integration

- **Nucleus** library for native window decorations, macOS dock badge, and notification permissions.
- **AWT SystemTray** for cross-platform tray icon with badge overlay.
- **macOS-specific**: Template tray icons with colored badge overlay (`MacOsTrayBadge.kt`).

### Key Packages (`com.mikepenz.agentbuddy`)

| Package | Purpose |
|---------|---------|
| `model/` | Serializable data models (shared in `commonMain`) |
| `server/` | Ktor HTTP server and request parsing |
| `state/` | Reactive state management via StateFlow |
| `storage/` | JSON file persistence (history + settings) |
| `risk/` | AI-powered risk analysis via CLI subprocess |
| `hook/` | Claude Code hook registration in `~/.claude/settings.json` |
| `platform/` | OS-specific features (tray, startup, icons) |
| `ui/` | Compose UI components organized by tab |
| `ui/theme/` | Theme tokens — `AgentBuddyColors` semantic palette (dark/light), `AgentBuddyDimens` iconography + density, `PreviewScaffold` (accepts `ThemeMode`) |
| `ui/components/` | Shared primitives — `PillSegmented`, `AgentBuddyCard`, `StatusPill`/`RiskPill`/`ToolTag`/`SourceTag`, `DesignToggle`, `ScreenLoadingState` / `ScreenErrorState`, `Kbd` |

### Design System & Previews

See [`composeApp/DESIGN.md`](composeApp/DESIGN.md) for the full token
table, state-matrix coverage, and preview-authoring playbook. Previews
are rendered headlessly with
[compose-buddy-cli](https://github.com/mikepenz/compose-buddy) — set
`COMPOSE_BUDDY_CLI` to the installed binary and run:

```bash
$COMPOSE_BUDDY_CLI render \
  --project . --module :composeApp --renderer desktop \
  --output /tmp/agent-approver-previews \
  --build --format agent --hierarchy --semantics all
```

The manifest.json carries per-node bounds + semantics; that feeds the
iter-5 a11y audit (touch targets, `contentDescription`, `Role.*`).

### Data Compatibility

See `AGENTS.md` — models serialized to `history.json` and `settings.json` must maintain backward/forward compatibility. New fields need defaults, fields cannot be removed or renamed, enum values cannot be removed.

## Dependency Verification

Gradle dependency verification is enabled via `gradle/verification-metadata.xml`. When adding or updating dependencies, regenerate it:

```bash
./gradlew --write-verification-metadata sha256 resolveDependencies
```

Commit the updated `gradle/verification-metadata.xml` alongside dependency changes. Platform-specific artifacts (Compose Desktop, Skiko) and CI-injected dependencies are covered by `<trusted-artifacts>` rules.

### Nucleus Native Binary Audit (April 2026, refreshed for v1.14.4)

Nucleus (`io.github.kdroidfilter:nucleus.*`) ships pre-compiled native binaries (.dylib/.so/.dll) across several JARs at v1.14.4. Direct modules used here: `darkmode-detector`, `decorated-window-core`, `decorated-window-jni`, `decorated-window-material3`, `notification-macos`, `notification-linux`, `global-hotkey`, and `updater-runtime` (pure Kotlin, no natives). Security audit findings are unchanged from the v1.12.0 audit; the chain-of-trust below establishes byte-level continuity.

- **Source**: Open-source (MIT), single maintainer (Elie Gambache / `kdroidFilter`).
- **Build pipeline**: Natives are compiled from source in GitHub Actions CI (`build-natives.yaml`), not committed to the repo. Standard `clang` (macOS) / `gcc` (Linux) / MSVC 14.44 (Windows) invocations.
- **Binary analysis**: All binaries are small (~3–135 KB). Imports restricted to platform system frameworks only — Cocoa/AppKit/Foundation/Carbon (macOS), X11/D-Bus/glib (Linux), `ADVAPI32`/`USER32`/`dwmapi`/`KERNEL32`/MSVC CRT (Windows). Zero networking, zero process exec, zero remote-thread/memory-injection, zero persistence APIs (no `SMLoginItem*`/`LSSharedFileList*`/`HKLM\\…\\Run` writes), zero crypto, zero anti-debug, zero `__mod_init_func` or `DllMain` side effects, zero declared ObjC classes. Exports are exclusively `Java_io_github_kdroidfilter_nucleus_*` JNI symbols matching the Kotlin `Native*Bridge` classes.
- **Verification**: SHA-256 checksums pinned in `verification-metadata.xml`.
- **Version alignment note**: ComposeNativeTray 1.3.0 transitively depends on Nucleus 1.12.0; we pin all direct Nucleus deps to the same version to avoid a split resolution. Currently aligned at 1.14.4 across all direct deps.

**Chain of trust v1.12.0 → v1.14.1 → v1.14.4** (established April 2026):

| Module | macOS dylib | Linux .so | Windows DLL |
|---|---|---|---|
| `darkmode-detector` | byte-identical 1.12 → 1.14.4 | byte-identical 1.12 → 1.14.4 | functionally identical 1.14.1 → 1.14.4 (only PE TimeDateStamp differs) |
| `decorated-window-core` | byte-identical 1.12 → 1.14.4 | byte-identical 1.12 → 1.14.4 | functionally identical 1.14.1 → 1.14.4 |
| `decorated-window-jni` | changed 1.12 → 1.14.1, identical 1.14.1 → 1.14.4 (re-audited deeply) | byte-identical 1.12 → 1.14.4 | functionally identical 1.14.1 → 1.14.4 |
| `global-hotkey` | byte-identical 1.12 → 1.14.4 | byte-identical 1.12 → 1.14.4 | functionally identical 1.14.1 → 1.14.4 |
| `notification-macos`, `notification-linux`, `decorated-window-material3` | jars byte-identical 1.14.1 ≡ 1.14.4 | — | — |
| `core-runtime`, `freedesktop-icons`, `updater-runtime` | jars byte-identical 1.14.1 ≡ 1.14.4 (pure Kotlin or no natives) | — | — |

Source-side: zero native code changes between v1.14.1 and v1.14.4 tags. Two small Kotlin UI fixes in `decorated-window-core` (focus + dialog background). Zero CI workflow changes. Release commit GPG-verified. The Windows DLL byte changes between 1.14.1 and 1.14.4 are MSVC link-time non-determinism only (PE TimeDateStamp + its echo in the Debug Directory; six bytes total per binary; every other byte identical).

**When bumping the Nucleus version**: Re-audit the upstream `kdroidFilter/Nucleus` repo `vOLD..vNEW` for changes to `.m`/`.c`/`.cpp`/`.swift`/`.rs` source under each used module dir, plus `.github/workflows/build-natives.yaml` and any reusable workflows. Confirm the CI pipeline still compiles natives from source without injecting downloaded artifacts. Diff SHA-256s of the pre-existing dylibs/sos/dlls against the previous version; if any *unchanged* binary now differs without a corresponding source change, treat it as a build-system smell and investigate. For any *changed* binary, run `llvm-objdump --macho -p`, `llvm-nm -u`, and `strings -a` (compare against prior version) — flag any new networking / exec / persistence / privilege / injection imports. Refresh `verification-metadata.xml`. This rigor is necessary because Nucleus is a young, single-maintainer project without reproducible build attestation; falls back to Maven Central GPG signatures + GPG-verified release commits as the trust root.

### ComposeNativeTray Native Binary Audit (April 2026)

ComposeNativeTray (`io.github.kdroidfilter:composenativetray` v1.3.0) ships 6 pre-compiled native binaries inside the `-jvm` artifact:
- `composetray/native/darwin-aarch64/libMacTray.dylib` (156 KB)
- `composetray/native/darwin-x86-64/libMacTray.dylib` (131 KB)
- `composetray/native/linux-aarch64/libLinuxTray.so` (260 KB)
- `composetray/native/linux-x86-64/libLinuxTray.so` (228 KB)
- `composetray/native/win32-arm64/WinTray.dll` (23 KB)
- `composetray/native/win32-x86-64/WinTray.dll` (23 KB)

- **Source**: Same maintainer ecosystem as Nucleus ([`kdroidFilter/ComposeNativeTray`](https://github.com/kdroidFilter/ComposeNativeTray), MIT).
- **Build pipeline**: CI builds natives from source via `.github/workflows/build-natives.yaml`.
- **Binary analysis**: `strings` shows only the expected tray symbols (`NSStatusItem`, `org.kde.StatusNotifierItem`, `Shell_NotifyIconW`). No network (socket/curl/http), no exec/system, no credential access.
- **Verification**: SHA-256 checksums pinned in `verification-metadata.xml`.

**When bumping the ComposeNativeTray version**: same drill as for Nucleus — verify the CI pipeline still compiles from source, re-run `strings` on the bundled `.dylib`/`.so`/`.dll` for suspicious additions, and refresh `verification-metadata.xml`.

## Key Technical Details

- **Kotlin 2.3.20**, **Compose Multiplatform 1.10.0**, **Ktor 3.1.3**
- App version is in `gradle.properties` (`app.version`)
- Configuration cache is disabled due to KMP srcDir incompatibility
- App data stored in platform-specific dirs (macOS: `~/Library/Application Support/AgentBuddy/`, Linux: `~/.local/share/AgentBuddy/`)
- UI designed for compact ~350px side panel width
