<p align="center">
  <img src="icons/app.png" alt="Agent Approver" width="128">
</p>

<h1 align="center">Agent Approver</h1>

<p align="center">
  A desktop application that gives you full control over what AI coding agents can do on your machine.
</p>

<p align="center">
  <a href="https://github.com/mikepenz/agent-approver/actions/workflows/ci.yml"><img src="https://github.com/mikepenz/agent-approver/actions/workflows/ci.yml/badge.svg" alt="CI"></a>
  <a href="https://github.com/mikepenz/agent-approver/blob/main/LICENSE"><img src="https://img.shields.io/badge/License-Apache%202.0-blue.svg" alt="License"></a>
</p>

---

Agent Approver is a **human-in-the-loop gateway** for AI coding agents like [Claude Code](https://docs.anthropic.com/en/docs/claude-code) and [GitHub Copilot](https://github.com/features/copilot). It intercepts tool requests (file edits, shell commands, web fetches, etc.) via hook events, displays them in a review UI, and lets you approve or deny each action before it executes.

<p align="center">
  <img src="screenshots/approval_risk.png" alt="Approval UI with risk analysis" width="280">
  &nbsp;&nbsp;
  <img src="screenshots/history.png" alt="History tab" width="280">
  &nbsp;&nbsp;
  <img src="screenshots/settings_protections_1.png" alt="Protection engine modules" width="280">
</p>

### Key Features

- **Approval UI** — Review pending tool requests with syntax-highlighted diffs, command previews, and context
- **Protection Engine** — Built-in rules that block dangerous operations (destructive commands, credential access, supply-chain attacks)
- **Risk Analysis** — Optional AI-powered risk scoring (1-5) via Claude CLI or GitHub Copilot to auto-approve safe operations
- **Always Allow** — Grant persistent permissions for trusted tool patterns
- **Away Mode** — Disable timeouts for remote/async approval workflows
- **History** — Searchable log of all past approval decisions
- **System Tray** — Runs in the background with badge notifications for pending approvals
- **Cross-Platform** — macOS, Windows, and Linux (currently only macOS pre-built packages are provided)

## Quick Start

### 1. Install

Download the latest macOS DMG from [Releases](https://github.com/mikepenz/agent-approver/releases), or build from source for any platform:

```bash
git clone https://github.com/mikepenz/agent-approver.git
cd agent-approver
./gradlew :composeApp:run    # requires JDK 17+
```

> **macOS note:** The release DMG is currently **unsigned**. On first launch, macOS will block it. Go to **System Settings > Privacy & Security** and click **Open Anyway** to allow it.

### 2. Connect Your Agent

Agent Approver integrates via [hooks](https://docs.anthropic.com/en/docs/claude-code/hooks) — lightweight HTTP callbacks that AI agents fire before executing tools. The app registers two types of hooks:

| Hook | Purpose | Claude Code | GitHub Copilot |
|------|---------|:-----------:|:--------------:|
| **PreToolUse** | Runs the Protection Engine to block or modify dangerous requests _before_ the agent acts | Supported | Supported |
| **PermissionRequest** | Presents the request in the Approval UI for interactive human review | Supported | Supported¹ |

¹ Requires GitHub Copilot CLI **v1.0.16 or later** (added the `permissionRequest` hook event) and **v0.0.422 or later** for user-scoped hook loading from `~/.copilot/hooks/`.

Both Claude Code and GitHub Copilot now support the full interactive approval flow plus `PreToolUse` Protection Engine pre-checks.

**Claude Code** — In Settings > Integrations, click **Register Hooks** to add both hook entries to `~/.claude/settings.json`.

**GitHub Copilot** — In Settings > Integrations, click **Register** under GitHub Copilot. This installs the bridge scripts under `~/.agent-approver/` and writes both hook entries (`permissionRequest` + `preToolUse`) into a single user-scoped `~/.copilot/hooks/agent-approver.json` — no per-project setup needed.

### 3. Review & Approve

When Claude Code requests permission to use a tool:

1. The request hits Agent Approver's local HTTP server
2. The **Protection Engine** evaluates it against built-in safety rules — dangerous requests are blocked or modified automatically
3. If the request passes, it appears in the **Approvals** tab for your review
4. You approve or deny — the response is sent back to the agent
5. All decisions are logged in the searchable **History** tab

## Protection Engine

Built-in modules detect and block dangerous patterns, inspired in part by [claude-hooks](https://github.com/jspanos/claude-hooks):

| Module | Examples |
|--------|----------|
| Destructive Commands | `rm -rf`, `git reset --hard`, force push |
| Credential & Supply-Chain Protection | `.env` files, SSH keys, `curl \| bash`, base64 decode + exec |
| Tool Bypass Prevention | `sed -i`, `perl -pi`, echo redirects bypassing Edit tool |
| Environment Safety | Bare `pip install`, absolute paths, uncommitted file edits |

Each module can be configured to: **Auto Block**, **Ask** (prompt user), **Auto-correct**, **Log Only**, or **Disabled**.

## Risk Analysis

Agent Approver can optionally score each request's risk level (1–5) using AI, allowing safe operations (risk 1) to be auto-approved and critical ones (risk 5) to be auto-denied.

Two backends are supported:
- **Claude** — Spawns a `claude` CLI process to analyze the request.
- **Copilot** — Uses the GitHub Copilot API for risk assessment.

> **macOS note (Claude backend):** Because risk analysis spawns a `claude` CLI process, macOS may show a file access permission dialog. This permission is **not required** — you can safely deny it. The risk analysis will still work correctly.

<p align="center">
  <img src="screenshots/settings_general.png" alt="General settings" width="280">
  &nbsp;&nbsp;
  <img src="screenshots/settings_integrations.png" alt="Integration settings" width="280">
  &nbsp;&nbsp;
  <img src="screenshots/settings_risk_analysis.png" alt="Risk analysis settings" width="280">
</p>
<p align="center">
  <img src="screenshots/settings_protection_rules.png" alt="Protection rules detail" width="280">
</p>

## Tech Stack

- **[Kotlin 2.3](https://kotlinlang.org/)** with Kotlin Multiplatform (JVM target)
- **[Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/)** for the desktop UI
- **[Ktor](https://ktor.io/)** for the embedded HTTP server that receives hook callbacks
- **[SQLite](https://www.sqlite.org/)** for persistent history storage
- **[Nucleus](https://github.com/niclas-4712/nucleus)** for native window decorations and macOS dock integration

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for development setup and guidelines.

## Acknowledgements

The Protection Engine modules are inspired in part by [claude-hooks](https://github.com/jspanos/claude-hooks) by [@jspanos](https://github.com/jspanos).

## License

Licensed under Apache 2.0. See [LICENSE](LICENSE) for details.
