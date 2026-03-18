package com.mikepenz.agentapprover

import androidx.compose.material3.Text
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Agent Approver",
    ) {
        // Placeholder — Task 15 will wire App(...) with real dependencies
        Text("Agent Approver")
    }
}
