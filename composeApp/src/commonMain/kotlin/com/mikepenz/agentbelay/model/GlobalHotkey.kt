package com.mikepenz.agentbelay.model

import kotlinx.serialization.Serializable

/**
 * A configurable, OS-level keyboard shortcut. Stored as the AWT virtual key
 * code (e.g. `KeyEvent.VK_A`) plus the set of modifiers that must be held.
 *
 * AWT codes round-trip through `kotlinx.serialization` cleanly and are the
 * format the Compose `onPreviewKeyEvent` capture path produces, so we keep
 * them as the canonical representation. The platform-specific translation to
 * native keycodes is done at register time inside `GlobalHotkeyManager`.
 */
@Serializable
data class GlobalHotkey(
    /** AWT `KeyEvent.getKeyCode()`. */
    val keyCode: Int,
    /** Modifier keys that must be held alongside [keyCode]. Order is irrelevant. */
    val modifiers: Set<HotkeyModifier> = emptySet(),
)

@Serializable
enum class HotkeyModifier { CONTROL, SHIFT, ALT, META }
