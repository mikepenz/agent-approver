# Security Policy

## Reporting a Vulnerability

If you discover a security vulnerability in Agent Buddy, please report it responsibly.

**Do not open a public GitHub issue for security vulnerabilities.**

Instead, please send an email to [opensource-sec@mikepenz.dev](mailto:opensource-sec@mikepenz.dev) with:

- A description of the vulnerability
- Steps to reproduce the issue
- Any potential impact assessment

## Mitigations — 2026-04-15

**Data-at-rest protection for the SQLite history database**

Sensitive history columns are now AES-GCM encrypted at the application layer
before being written to `agent-buddy.db`. The encrypted columns are
`raw_request_json`, `raw_response_json`, `tool_input_json`, `feedback`,
`protection_detail`, and `risk_message`. Indexed / filterable columns
(`id`, `type`, `source`, `tool_name`, `tool_type`, `session_id`, `decision`,
`requested_at`, `decided_at`, `cwd`) remain in plaintext because they are
used in `WHERE` / `ORDER BY` / `GROUP BY` clauses.

Encryption parameters:

- AES/GCM/NoPadding, 256-bit key, 12-byte random IV per value, 128-bit auth tag
- Storage format: `v1:<base64(iv || ciphertext || tag)>` — the `v1:` prefix is
  a version tag for future rotation
- Backward compatibility: pre-encryption rows lack the `v1:` prefix and are
  returned as-is by the decrypt path; they upgrade in place on the next write

**Key storage.** The 256-bit key is stored in the platform keyring where
possible, falling back to a file only when the keyring is unavailable:

| Platform | Primary store                                   | Fallback                 |
|----------|-------------------------------------------------|--------------------------|
| macOS    | Keychain (`com.mikepenz.agentbuddy` / `db.key`) | `<dataDir>/db.key` (0600) |
| Windows  | Credential Manager (same service/account)        | `<dataDir>\db.key` (ACL)  |
| Linux    | freedesktop Secret Service (GNOME Keyring / KWallet) | `<dataDir>/db.key` (0600) |

The keyring path resists offline disk reads because the OS gates access by
user login session. The file fallback kicks in on headless Linux sessions, on
hosts without a running Secret Service daemon, or when the keyring is locked
— in those environments, key protection degrades to the POSIX file mode
(`rw-------`) or, on Windows without a keyring, the user's default ACL.

**Log sanitization**

By default the application no longer writes raw commands, AI explanations, or
request/response bodies into log streams. A new `verboseLogging` setting
(default `false`) re-enables full detail when explicitly toggled in
Settings → Diagnostics. The flag is read at every sensitive call site through
`com.mikepenz.agentbuddy.logging.Logging.verbose` and is updated
synchronously from `AppStateManager.updateSettings`, so no restart is required.

**Remaining gaps**

- When the platform keyring is used, key access is gated by the OS login
  session. Any process running as the same logged-in user can still query the
  keyring via the same APIs — the protection is against *offline* disk reads
  (stolen laptops, backup tarballs, mistakenly synced cloud folders), not
  against co-resident malware.
- When the file fallback is used (e.g. headless Linux without a running
  Secret Service daemon), the key at `<dataDir>/db.key` is readable by any
  process running as the same user and is not protected from offline disk
  reads either. Document-level encryption still raises the bar for casual
  disk access but is weaker than the keyring path.
- `cwd` is intentionally left in plaintext for grouping. Treat the dataDir as
  user-confidential.
- `verboseLogging`, when enabled, restores the previous behaviour and will
  again emit raw content to whichever logger sink is configured. Turn it back
  off after diagnosing.

