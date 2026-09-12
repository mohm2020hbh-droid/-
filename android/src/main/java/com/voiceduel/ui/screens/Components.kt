package com.voiceduel.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.voiceduel.ui.ConnectionStatus

/** The common page frame: themed background, padding, and centred content. */
@Composable
fun DuelScaffold(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            content = content,
        )
    }
}

/**
 * A dismissible message bar.
 *
 * Every failure the player can hit — a bad code, a lost socket, a refused
 * microphone — surfaces here instead of crashing or freezing (R7).
 */
@Composable
fun NoticeBanner(
    notice: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(visible = notice != null, modifier = modifier) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = notice.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "إغلاق التنبيه")
                }
            }
        }
    }
}

/** A small always-visible transport indicator. */
@Composable
fun ConnectionChip(status: ConnectionStatus, modifier: Modifier = Modifier) {
    val (label, color) = when (status) {
        ConnectionStatus.CONNECTED -> "متصل" to MaterialTheme.colorScheme.tertiary
        ConnectionStatus.CONNECTING -> "جارٍ الاتصال…" to MaterialTheme.colorScheme.secondary
        ConnectionStatus.LOST -> "انقطع الاتصال" to MaterialTheme.colorScheme.error
        ConnectionStatus.DISCONNECTED -> "غير متصل" to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Text(
        text = label,
        color = color,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

/** The target sound, shown to both players — the rater needs it to judge. */
@Composable
fun SoundCard(emoji: String, name: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = emoji, style = MaterialTheme.typography.displayLarge)
            Text(
                text = name,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
    }
}
