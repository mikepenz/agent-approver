package com.mikepenz.agentbelay.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.agentbelay.model.GlobalHotkey
import com.mikepenz.agentbelay.model.HotkeyModifier
import com.mikepenz.agentbelay.ui.icons.LucideX
import com.mikepenz.agentbelay.ui.theme.AgentBelayColors
import com.mikepenz.agentbelay.ui.theme.DangerRed
import java.awt.event.KeyEvent as AwtKeyEvent

/**
 * Click to start capturing a key combination, click again (or press the X) to
 * cancel / clear, then press a non-modifier key while at least one modifier
 * is held to commit the new binding. Designed to slot into a `SettingItem`'s
 * `right` slot.
 *
 * The captured combination is stored using AWT virtual key codes so it
 * round-trips with Compose's key-event APIs and serialises cleanly.
 */
@Composable
fun HotkeyCaptureField(
    hotkey: GlobalHotkey?,
    onChange: (GlobalHotkey?) -> Unit,
    hasError: Boolean = false,
) {
    var capturing by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(capturing) {
        if (capturing) {
            // Try to grab focus so onPreviewKeyEvent receives the keystrokes.
            // FocusRequester.requestFocus() can throw if the node hasn't been
            // composed yet; LaunchedEffect runs after composition so this is
            // safe in practice, but we wrap it defensively.
            runCatching { focusRequester.requestFocus() }
        }
    }

    // Click-handler semantics:
    //  - Idle + no hotkey: click → start capturing
    //  - Idle + has hotkey: click → clear + start capturing fresh
    //  - Capturing: click → cancel (revert to whatever was set)
    val onClickField = {
        if (capturing) {
            capturing = false
        } else {
            // Per spec: clicking on a configured hotkey removes it and starts
            // a new capture session.
            if (hotkey != null) onChange(null)
            capturing = true
        }
    }

    Row(
        modifier = Modifier
            .heightIn(min = 34.dp)
            .widthIn(min = 200.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(AgentBelayColors.surface)
            .border(
                1.dp,
                when {
                    hasError -> DangerRed.copy(alpha = 0.55f)
                    capturing -> AgentBelayColors.inkSecondary
                    else -> AgentBelayColors.line1
                },
                RoundedCornerShape(7.dp),
            )
            .clickable { onClickField() }
            .focusRequester(focusRequester)
            .onFocusChanged { state ->
                // If we were capturing and lost focus (user clicked away),
                // exit capture mode without changing the binding.
                if (capturing && !state.isFocused) capturing = false
            }
            .focusable()
            .onPreviewKeyEvent { event -> handleCaptureKeyEvent(event, capturing, onCommit = {
                onChange(it)
                capturing = false
            }, onCancel = { capturing = false }) }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Important: do NOT put `Modifier.weight(1f)` on these children. A
        // weighted child inside a Row that's measured with a large max-width
        // (which is what we get when we sit inside `SettingItem`'s right
        // slot) fills the entire available width, which pushes the
        // SettingItem label out to zero. Letting the Row size to content
        // (with `widthIn(min = …)` for the empty state) keeps the label
        // visible on the left.
        when {
            capturing -> {
                Text(
                    text = "Press a key combination…",
                    color = AgentBelayColors.inkSecondary,
                    fontSize = 12.sp,
                )
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(AgentBelayColors.surface2)
                        .clickable { capturing = false },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = LucideX,
                        contentDescription = "Cancel hotkey capture",
                        tint = AgentBelayColors.inkSecondary,
                        modifier = Modifier.size(11.dp),
                    )
                }
            }
            hotkey != null -> HotkeyKeyCaps(hotkey)
            else -> Text(
                text = "Click to set a shortcut",
                color = AgentBelayColors.inkMuted,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun HotkeyKeyCaps(hotkey: GlobalHotkey) {
    val parts = remember(hotkey) {
        // Stable rendering order: modifiers in CONTROL/SHIFT/ALT/META order
        // (most natural reading), then the key.
        val orderedMods = hotkeyModifierOrder.filter { it in hotkey.modifiers }.map { it.label() }
        orderedMods + keyDisplayName(hotkey.keyCode)
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        parts.forEachIndexed { idx, part ->
            if (idx > 0) {
                Text(
                    text = "+",
                    color = AgentBelayColors.inkMuted,
                    fontSize = 11.sp,
                )
            }
            KeyCap(part)
        }
    }
}

@Composable
private fun KeyCap(label: String) {
    Box(
        modifier = Modifier
            .height(22.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(AgentBelayColors.surface2)
            .border(1.dp, AgentBelayColors.line1, RoundedCornerShape(5.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = AgentBelayColors.inkPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.2.sp,
        )
    }
}

private val hotkeyModifierOrder = listOf(
    HotkeyModifier.CONTROL,
    HotkeyModifier.SHIFT,
    HotkeyModifier.ALT,
    HotkeyModifier.META,
)

private fun HotkeyModifier.label(): String {
    val isMac = System.getProperty("os.name").orEmpty().lowercase().contains("mac")
    return when (this) {
        HotkeyModifier.CONTROL -> if (isMac) "⌃" else "Ctrl"
        HotkeyModifier.SHIFT -> if (isMac) "⇧" else "Shift"
        HotkeyModifier.ALT -> if (isMac) "⌥" else "Alt"
        HotkeyModifier.META -> if (isMac) "⌘" else "Win"
    }
}

private fun keyDisplayName(awtCode: Int): String {
    // KeyEvent.getKeyText is the canonical pretty name on the JVM (handles
    // F1-F24, arrows, etc.) — fall back to the code point when the AWT
    // helper returns something unusably terse.
    val name = AwtKeyEvent.getKeyText(awtCode)
    if (name.isNotBlank() && !name.startsWith("Unknown")) return name
    return "Key $awtCode"
}

/**
 * Returns true if the event was consumed (so it doesn't propagate to other
 * shortcut handlers further up the tree).
 */
private fun handleCaptureKeyEvent(
    event: KeyEvent,
    capturing: Boolean,
    onCommit: (GlobalHotkey) -> Unit,
    onCancel: () -> Unit,
): Boolean {
    if (!capturing) return false
    if (event.type != KeyEventType.KeyDown) return true  // swallow up events while capturing

    // Escape cancels without changing the binding.
    if (event.key == Key.Escape) {
        onCancel()
        return true
    }
    // Pure modifier keystrokes don't produce a usable shortcut on their own —
    // wait for the user to press the actual key while a modifier is held.
    if (event.key in modifierKeys) return true

    val mods = mutableSetOf<HotkeyModifier>()
    if (event.isCtrlPressed) mods += HotkeyModifier.CONTROL
    if (event.isShiftPressed) mods += HotkeyModifier.SHIFT
    if (event.isAltPressed) mods += HotkeyModifier.ALT
    if (event.isMetaPressed) mods += HotkeyModifier.META
    if (mods.isEmpty()) {
        // Reject bare keys to avoid accidentally hijacking single letters.
        return true
    }
    val awtCode = composeKeyToAwt(event)
    if (awtCode == AwtKeyEvent.VK_UNDEFINED) return true
    onCommit(GlobalHotkey(keyCode = awtCode, modifiers = mods))
    return true
}

private val modifierKeys = setOf(
    Key.CtrlLeft, Key.CtrlRight,
    Key.ShiftLeft, Key.ShiftRight,
    Key.AltLeft, Key.AltRight,
    Key.MetaLeft, Key.MetaRight,
)

private fun composeKeyToAwt(event: KeyEvent): Int {
    // Pull the underlying AWT KeyEvent out of Compose's wrapper so we get the
    // actual VK_* code. `nativeKeyEvent` on Desktop is an `InternalKeyEvent`
    // whose own `nativeEvent` field is the java.awt.event.KeyEvent — older
    // Compose versions exposed the AWT event directly. Try both shapes.
    val awt = event.nativeKeyEvent.unwrapAwtKeyEvent()
    if (awt != null && awt.keyCode != AwtKeyEvent.VK_UNDEFINED) return awt.keyCode

    // Fallback: derive a code from the codepoint — only really useful for
    // letters/digits where AWT VK_* values numerically equal the upper-case
    // ASCII code.
    val codePoint = event.utf16CodePoint
    if (codePoint in 0x21..0x7E) return codePoint.toChar().uppercaseChar().code
    return AwtKeyEvent.VK_UNDEFINED
}

private fun Any.unwrapAwtKeyEvent(): AwtKeyEvent? {
    if (this is AwtKeyEvent) return this
    // Compose Desktop wraps the AWT event inside an InternalKeyEvent whose
    // `nativeEvent` property holds the original. Reach it via reflection so
    // we don't take a hard dep on the internal class.
    val getter = runCatching {
        val prop = this::class.java.methods.firstOrNull { it.name == "getNativeEvent" }
        prop?.invoke(this)
    }.getOrNull()
    return getter as? AwtKeyEvent
}
