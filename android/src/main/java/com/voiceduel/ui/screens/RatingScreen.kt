package com.voiceduel.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voiceduel.ui.GameUiState
import com.voiceduel.ui.GameViewModel

/**
 * R4 — listen and score.
 *
 * The judgement is entirely human (C3): the app plays the recording and offers
 * a 0–100 slider, nothing more.
 */
@Composable
fun RatingScreen(
    state: GameUiState,
    onScoreChange: (Int) -> Unit,
    onReplay: () -> Unit,
    onSubmit: () -> Unit,
    onDismissNotice: () -> Unit,
) {
    DuelScaffold {
        val round = state.round

        Text(
            text = "قيّم تقليد خصمك",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))

        if (round != null) {
            SoundCard(emoji = round.soundEmoji, name = round.soundName)
            Spacer(Modifier.height(24.dp))
        }

        if (!state.hasRecordingToRate) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(
                text = "جارٍ استقبال التسجيل…",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            NoticeBanner(notice = state.notice, onDismiss = onDismissNotice)
            return@DuelScaffold
        }

        Text(
            text = if (state.isPlayingRecording) "🔊 جارٍ التشغيل…" else "انتهى التشغيل",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.tertiary,
        )

        Spacer(Modifier.height(12.dp))

        OutlinedButton(onClick = onReplay, enabled = !state.isPlayingRecording) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Replay, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("إعادة الاستماع")
            }
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = "${state.pendingScore}",
            style = MaterialTheme.typography.displayLarge,
            fontSize = 64.sp,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "من 100",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Slider(
            value = state.pendingScore.toFloat(),
            onValueChange = { onScoreChange(it.toInt()) },
            valueRange = GameViewModel.MIN_SCORE.toFloat()..GameViewModel.MAX_SCORE.toFloat(),
            enabled = !state.isSubmittingRating,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = scoreLabel(state.pendingScore),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.secondary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onSubmit,
            enabled = !state.isSubmittingRating,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.isSubmittingRating) "جارٍ الإرسال…" else "إرسال التقييم")
        }

        Spacer(Modifier.height(16.dp))
        NoticeBanner(notice = state.notice, onDismiss = onDismissNotice)
    }
}

private fun scoreLabel(score: Int): String = when {
    score >= 90 -> "مطابق تمامًا! 🤯"
    score >= 70 -> "تقليد ممتاز 👏"
    score >= 50 -> "قريب من الصوت 🙂"
    score >= 30 -> "محاولة متواضعة 😅"
    else -> "بعيد عن المطلوب 😂"
}
