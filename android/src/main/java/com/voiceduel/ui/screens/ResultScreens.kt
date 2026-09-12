package com.voiceduel.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voiceduel.ui.GameUiState

/** R5, first half — the score for the round that just ended. */
@Composable
fun RoundResultScreen(state: GameUiState, onDismissNotice: () -> Unit) {
    val outcome = state.lastOutcome ?: return
    val performerWasMe = outcome.performerId == state.playerId

    DuelScaffold {
        Text(
            text = "نتيجة الجولة ${outcome.number}",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = "${outcome.score}",
            style = MaterialTheme.typography.displayLarge,
            fontSize = 88.sp,
            color = MaterialTheme.colorScheme.primary,
        )

        Text(
            text = when {
                outcome.timedOut && performerWasMe -> "انتهى الوقت قبل إرسال تسجيلك"
                outcome.timedOut -> "انتهى وقت الجولة"
                performerWasMe -> "هذه نقاطك عن أدائك"
                else -> "هذه النقاط التي منحتَها لخصمك"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))
        ScoreBoard(state)
        Spacer(Modifier.height(24.dp))

        Text(
            text = "الجولة التالية بعد لحظات…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))
        NoticeBanner(notice = state.notice, onDismiss = onDismissNotice)
    }
}

/** R5, second half — the winner and the final totals. */
@Composable
fun GameOverScreen(
    state: GameUiState,
    onPlayAgain: () -> Unit,
    onDismissNotice: () -> Unit,
) {
    val outcome = state.finalOutcome ?: return
    val isDraw = outcome.winnerId == null
    val didWin = outcome.winnerId != null && outcome.winnerId == state.playerId

    DuelScaffold {
        Text(
            text = when {
                isDraw -> "🤝"
                didWin -> "🏆"
                else -> "😮‍💨"
            },
            style = MaterialTheme.typography.displayLarge,
            fontSize = 88.sp,
        )

        Text(
            text = when {
                isDraw -> "تعادل!"
                didWin -> "فزت بالمبارزة!"
                else -> "فاز خصمك هذه المرة"
            },
            style = MaterialTheme.typography.headlineMedium,
            color = if (didWin) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.onBackground
            },
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))
        ScoreBoard(state)
        Spacer(Modifier.height(32.dp))

        Button(onClick = onPlayAgain, modifier = Modifier.fillMaxWidth()) {
            Text("مباراة جديدة")
        }

        Spacer(Modifier.height(16.dp))
        NoticeBanner(notice = state.notice, onDismiss = onDismissNotice)
    }
}

/** Running totals for both players. */
@Composable
private fun ScoreBoard(state: GameUiState, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ScoreColumn(label = "أنت", score = state.myScore)
            ScoreColumn(label = "الخصم", score = state.opponentScore)
        }
    }
}

@Composable
private fun ScoreColumn(label: String, score: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "$score",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
