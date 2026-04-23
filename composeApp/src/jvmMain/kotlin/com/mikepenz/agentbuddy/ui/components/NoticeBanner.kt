package com.mikepenz.agentbuddy.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.mikepenz.agentbuddy.state.AppNotice

private const val LOG_HINT = "~/.agent-buddy/logs/agent-buddy.log"

@Composable
fun NoticeBannerStack(
    notices: List<AppNotice>,
    onDismiss: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (notices.isEmpty()) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        notices.forEach { notice ->
            NoticeBanner(notice = notice, onDismiss = { onDismiss(notice.id) })
        }
    }
}

@Composable
private fun NoticeBanner(notice: AppNotice, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(
                text = "A problem was captured — details saved to $LOG_HINT",
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = notice.message,
                style = MaterialTheme.typography.bodySmall,
            )
            if (!notice.detail.isNullOrBlank()) {
                Text(
                    text = notice.detail,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = {
                    val payload = buildString {
                        appendLine(notice.message)
                        notice.detail?.let { appendLine(it) }
                        append("Log: ").append(LOG_HINT)
                    }
                    clipboard.setText(AnnotatedString(payload))
                }) { Text("Copy details") }
                TextButton(onClick = onDismiss) { Text("Dismiss") }
            }
        }
    }
}
