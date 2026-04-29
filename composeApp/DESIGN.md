# Agent Belay — Design System

This document describes the Compose design-system primitives and the
preview-driven visual-regression workflow that ships with the app.
It complements `README.md` (product overview) and `CLAUDE.md`
(developer entry point).

Canonical design spec:
<https://api.anthropic.com/v1/design/h/sFpNYNZE1FmpRSLF6oAjZQ>

Theme source of truth: `src/jvmMain/kotlin/com/mikepenz/agentbelay/ui/theme/Theme.kt`.

Shared primitives: `src/jvmMain/kotlin/com/mikepenz/agentbelay/ui/components/`.

## Theme tokens

Two tiers of tokens, layered on top of a Material3 `ColorScheme`:

### 1. Raw palette constants (dark-only, in `Theme.kt`)

`GraphiteBg`, `GraphiteSurface`, `GraphiteSurface2`, `GraphiteSurface3`,
`GraphiteChrome`, `InkPrimary`, `InkSecondary`, `InkTertiary`,
`InkMuted`, `InkSubtle`, `Line1`, `Line2`, `Line3`, `AccentEmerald`,
`AccentEmeraldInk`, `AccentEmeraldTint`.

These are the dark-scale raw colors. They are safe to read from
non-`@Composable` scopes (top-level `val`s, data-class defaults).
Inside `@Composable` functions prefer the accessor below so the theme
swap takes effect.

### 2. Theme-aware semantic tokens (`AgentBelayColors`)

`AgentBelayColors.<token>` is a read-only composable accessor backed by
a `staticCompositionLocalOf<AgentBelayColorsPalette>`. Available
tokens:

| Token group | Names |
|---|---|
| Backgrounds | `background`, `surface`, `surface2`, `surface3`, `chrome` |
| Ink | `inkPrimary`, `inkSecondary`, `inkTertiary`, `inkMuted`, `inkSubtle` |
| Hairlines | `line1`, `line2`, `line3` |
| Emerald accent | `accentEmerald`, `accentEmeraldInk`, `accentEmeraldTint` |
| Theme flag | `isDark` |

Palettes: `AgentBelayColorsDark`, `AgentBelayColorsLight`. Selection
happens in `AgentBelayTheme(themeMode = …)`.

### Dimensions (`AgentBelayDimens`)

Canonical density / iconography tokens:

| Token | Value | Use |
|---|---|---|
| `IconXSmall` | 12dp | Dense inline glyphs |
| `IconSmall` | 14dp | Pill leading icons |
| `IconMedium` | 16dp | Most in-card icons |
| `IconLarge` | 20dp | Toolbar / section |
| `IconXLarge` | 24dp | Avatars / hero |
| `CardCornerRadius` | 10dp | |
| `CardPaddingHorizontal` | 12dp | |
| `CardPaddingVertical` | 10dp | |
| `ListRowHeight` | 44dp | |
| `ListRowGutter` | 12dp | |
| `SectionSpacing` | 16dp | |

## Shared primitives (`ui/components/`)

| Primitive | File | Purpose |
|---|---|---|
| `PillSegmented` | `PillSegmented.kt` | Pill-style segmented control; `Role.Tab`, `selected` semantics |
| `AgentBelayCard` | `DesignPrimitives.kt` | Bordered card surface with theme-aware hairline |
| `StatusPill` | `DesignPrimitives.kt` | Approval decision pills (Approved, Denied, Auto-*, Protection-Blocked) |
| `ToolTag` | `DesignPrimitives.kt` | Coloured tool-name tag (Bash, Web, Edit, …) |
| `RiskPill` | `DesignPrimitives.kt` | Risk score 1–5 pill with via-label (claude / copilot / ollama) |
| `SourceTag` | `DesignPrimitives.kt` | Claude Code / Copilot source tag |
| `DesignToggle` | `DesignPrimitives.kt` | iOS-style switch; `Role.Switch`, `toggleableState` + `stateDescription` |
| `SectionLabel` | `DesignPrimitives.kt` | Uppercase 10sp section header |
| `SkeletonRow` | `ScreenStates.kt` | Shimmering skeleton primitive for loading states |
| `ScreenLoadingState` | `ScreenStates.kt` | Full-screen loading scaffold (list of `SkeletonRow`s) |
| `ScreenErrorState` | `ScreenStates.kt` | Full-screen error scaffold (DangerRed banner + Retry) |
| `KbdKey` | `shell/AppSidebar.kt` | Keyboard-key glyph; used in sidebar + footer hints |

## State-matrix coverage

Each screen has `@Preview` functions covering the full matrix:

| Surface | Empty | Loading | Partial | Full | Error | Hover | Light |
|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| ApprovalsScreen | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| ApprovalCard | — | ✅ (Analyzing) | — | ✅ (Risk 1/2/3/5) | ✅ (RiskError) | — | ✅ |
| HistoryScreen | ✅ | ✅ | — | ✅ | ✅ | ✅ | ✅ |
| HistoryRow | — | — | — | ✅ | — | ✅ (RowHover) | ✅ |
| StatisticsScreen | ✅ | ✅ | — | ✅ | ✅ | ✅ | ✅ |
| SettingsScreen (General / Integrations / Risk / Protections / Capabilities) | — | ✅ | — | ✅ | ✅ | ✅ | ✅ |
| AppSidebar | — | — | — | ✅ | — | ✅ | ✅ |
| SlimWindow | ✅ | — | — | ✅ (AskUserQuestion / Plan) | — | ✅ | ✅ |
| AgentBelayShell | — | — | — | ✅ | — | — | ✅ |
| ProtectionLogTab | ✅ | — | — | ✅ | — | — | ✅ |
| ContentDetailWindow / LicensesWindow | — | ✅ (Licenses) | — | ✅ | — | — | ✅ |
| CommandPaletteHost | ✅ (Closed) | — | — | ✅ (Open) | — | — | ✅ |

Total: **122 `@Preview` functions**, all rendering successfully.

## Preview workflow (compose-buddy)

Compose previews are rendered headlessly with compose-buddy-cli.

```bash
COMPOSE_BUDDY_CLI=~/path/to/compose-buddy-cli/bin/compose-buddy-cli

# full render
$COMPOSE_BUDDY_CLI render \
  --project . \
  --module :composeApp \
  --renderer desktop \
  --output /tmp/agent-belay-previews \
  --build --format agent --hierarchy --semantics all

# re-render a subset (no --build after pure preview edits)
$COMPOSE_BUDDY_CLI render \
  --project . --module :composeApp --renderer desktop \
  --output /tmp/agent-belay-previews \
  --preview "*Sidebar*"
```

The `manifest.json` with `--format agent --hierarchy` carries per-node
bounds + semantics, used by the iteration plan for a11y audits
(touch-target sizing, content descriptions, traversal groups).

## Adding a new preview

1. **Wrap in `PreviewScaffold`** — provides `AgentBelayTheme` and sane
   defaults:

   ```kotlin
   @Preview
   @Composable
   private fun PreviewMyComponent() {
       PreviewScaffold {
           MyComponent(…)
       }
   }
   ```

2. **Compact components get the picture-frame treatment.** Compose-Buddy's
   desktop renderer *silently ignores* `widthDp` / `heightDp` annotation
   args — rendering always happens on a 1280×800 canvas. Wrap compact
   components so they render at their real surface width:

   ```kotlin
   PreviewScaffold {
       Box(
           modifier = Modifier.fillMaxSize().padding(20.dp),
           contentAlignment = Alignment.TopCenter,
       ) {
           Box(modifier = Modifier.width(380.dp)) { ApprovalCard(…) }
       }
   }
   ```

3. **Light-theme variant** — add a parallel `PreviewFooLight`:

   ```kotlin
   @Preview
   @Composable
   private fun PreviewMyComponentLight() {
       PreviewScaffold(themeMode = ThemeMode.LIGHT) { MyComponent(…) }
   }
   ```

4. **CompositionLocals.** If the component reads a `CompositionLocal`
   with an `error("…")` default (e.g. `LocalCommandPaletteController`),
   wrap the preview in a scaffold that provides a fake:

   ```kotlin
   CompositionLocalProvider(
       LocalCommandPaletteController provides remember { CommandPaletteController() },
   ) { content() }
   ```

5. **Skip side effects in headless composition.** Guard
   `focusRequester.requestFocus()` and similar with
   `LocalInspectionMode.current` — otherwise the internal
   FocusRequester error corrupts the compose-buddy stdout stream and
   knocks out unrelated previews.

## Accessibility

The Iter-5 pass introduced explicit semantics on the interactive
primitives:

- `DesignToggle` → `Role.Switch` + `toggleableState` + `stateDescription`.
- `PillSegmented` tab → `Role.Tab` + `selected` + `isTraversalGroup` on the container.
- `AppSidebar` nav rows → `Role.Tab` + `selected` + `onClickLabel`
  ("Open Approvals" etc.). Visual height raised from 32 → 36 dp.
  `ChromeIconButton` (slim window) raised 24 → 28 dp.
- Keyboard-shortcut hints in `ApprovalsScreen` footer merge into a
  single semantic group with a readable
  `contentDescription` ("Keyboard shortcut Command Enter to allow once").
- External-link icon in expanded HistoryRow now carries a content
  description so screen readers announce the action.

Touch-target deviations intentionally kept for visual density:
the sidebar row remains 36 dp tall (design spec calls for high-density
navigation). Wide horizontal hit area (≥200 dp wide) compensates.
