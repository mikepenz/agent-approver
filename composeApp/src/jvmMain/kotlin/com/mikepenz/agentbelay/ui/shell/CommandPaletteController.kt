package com.mikepenz.agentbelay.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Stable
class CommandPaletteController {
    var isOpen by mutableStateOf(false)
        private set

    fun open() { isOpen = true }
    fun close() { isOpen = false }
    fun toggle() { isOpen = !isOpen }
}

val LocalCommandPaletteController = compositionLocalOf<CommandPaletteController> {
    error("CommandPaletteController not provided")
}

@Composable
fun rememberCommandPaletteController(): CommandPaletteController =
    remember { CommandPaletteController() }
