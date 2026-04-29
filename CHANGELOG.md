# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased]

### Added
- Tray menu now includes a **Capabilities** sub-menu with one checkbox per
  registered `CapabilityModule` (Response Compression, Socratic Thinking),
  kept in sync with the Settings → Capabilities tab via `AppStateManager`.

### Changed
- Migrated the system tray from raw AWT (`SystemTray` + `PopupMenu`) to
  [ComposeNativeTray](https://github.com/kdroidFilter/ComposeNativeTray). The
  tray is now a Compose composable and its menu recomposes from the reactive
  app state. macOS no longer renders the menu-bar icon as a template image
  with a separately-coloured badge overlay; the baked-in coloured badge is
  used on all platforms.
- Renamed project from **Agent Buddy** to **Agent Belay** (repo `mikepenz/agent-belay`).
  Kotlin package (`com.mikepenz.agentbuddy` → `com.mikepenz.agentbelay`), macOS bundle ID
  (`com.mikepenz.agentbuddy` → `com.mikepenz.agentbelay`), and data-directory names
  updated accordingly. Existing installs auto-migrate on first launch: data dir
  (`AgentBuddy/` → `AgentBelay/`), DB file (`agent-buddy.db` → `agent-belay.db`),
  hook bridge dir (`~/.agent-buddy/` → `~/.agent-belay/`), Claude `SessionStart`
  command paths, Copilot hook file (`agent-buddy.json` → `agent-belay.json`), and
  keyring service entry are all moved in place. Users still on the original
  Agent Approver build also migrate in one launch — both legacy steps run in
  chronological order.

## [1.7.0] - 2026-04-04

### Added
- GitHub Copilot pre-tool-use protection support

## [1.6.1] - 2026-04-04

### Fixed
- Include `java.sql` module in native distribution

## [1.6.0] - 2026-04-04

### Added
- PipedTailHeadModule to block `tail`/`head` on piped input with corrective hints
- Corrective hints for existing protection modules
- Improved protections settings UI
- Dev mode Protection Log tab
- History filter row (All/Approvals/Protections) and protection badges

### Fixed
- Protection hits now appear in history, deny uses protection message as fallback
- Correct PreToolUse HTTP hook response format for block/allow
- Include `rawResponseJson` in history entries for AUTO_BLOCK and LOG_ONLY protections

## [1.5.4] - 2026-04-04

### Added
- ProtectionEngine with module/rule interfaces and 10 protection modules
- SQLite-backed DatabaseStorage with migration from JSON history
- Restructured Settings into 4-tab layout with Protections tab
- PreToolUse route with protection engine integration

## [1.5.3] - 2026-04-03

### Fixed
- Wire clipboard copy for hooks.json snippet and fix registration reactivity

## [1.5.2] - 2026-04-03

### Added
- Auto-detect Copilot CLI in NVM paths and configurable CLI path

### Fixed
- Resolve Copilot CLI path for packaged app

## [1.5.1] - 2026-04-03

### Fixed
- Extend PATH for `gh` CLI detection in packaged app

## [1.5.0] - 2026-04-03

### Added
- GitHub Copilot risk analyzer integration
- Query available models from Copilot SDK with dropdown picker
- Analyzer backend switching with Copilot lifecycle management
- Source badge on approval cards and history rows

### Fixed
- Hide Install GitHub CLI button when already authenticated
- Append JSON format instruction to Copilot system prompt
- `gh auth status` detection and fixed docs links

## [1.4.0] - 2026-03-30

### Added
- Nucleus notification APIs for dock badge and permission handling

## [1.3.1] - 2026-03-26

### Fixed
- Use `open` command for notification settings and remove unreliable badge detection

## [1.3.0] - 2026-03-26

### Added
- Native dock badge and notification settings guide
- macOS template tray icon with native colored badge overlay
- Approval sort toggle

## [1.2.0] - 2026-03-22

### Added
- Tool-specific card rendering (Bash, File, Search, WebFetch, Plan, AskUserQuestion)
- Away mode to disable timeouts for remote approval
- Dev mode with history replay via long press
- Resilient approval request handling with external resolution detection
- Open-in-browser button for WebFetch content
- Pop-out detail window with markdown rendering and syntax highlighting
- Configurable risk analysis model and system prompt viewer
- Start on boot support for macOS, Windows, and Linux
- Window position and size persistence across restarts

### Fixed
- Limit history row collapse/expand click to card summary only

## [1.1.0] - 2026-03-19

### Added
- Always Allow feature with split Approve button and permission suggestions
- Theme system with Nucleus dark mode detector
- AboutLibraries integration for open source license display
- HiDPI tray icon with badge overlay
- Copy button in history detail

### Fixed
- Handle macOS Quit from dock menu and Cmd+Q
- Crash on startup (AlertDialog outside Window)

## [1.0.0] - 2026-03-18

### Added
- Initial release
- Ktor HTTP server for Claude Code hook integration
- Approval UI with pending requests and history
- Risk analysis via Claude CLI subprocess
- Hook registration for `~/.claude/settings.json`
- System tray with badge notifications
- Settings persistence
- macOS, Windows, and Linux native packaging
