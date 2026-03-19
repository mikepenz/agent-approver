# Agent Instructions

## Data Compatibility

### history.json

Changes to models serialized into `history.json` (`ApprovalResult`, `ApprovalRequest`, `HookInput`, `RiskAnalysis`, and their transitive types) **must be backwards and forwards compatible**:

- **New fields** must have default values so older history files deserialize without errors.
- **Never remove or rename** a serialized field. Deprecate by ignoring it in code while keeping it in the model.
- **Enum values** may be added but never removed. Use `@SerialName` for wire stability.
- **Type changes** (e.g., `String` to `Int`) are not allowed. Add a new field instead.
- The JSON parser uses `ignoreUnknownKeys = true`, so new fields are safe for forward compatibility (newer files read by older code).

These rules ensure users don't lose history when upgrading or downgrading the app.
