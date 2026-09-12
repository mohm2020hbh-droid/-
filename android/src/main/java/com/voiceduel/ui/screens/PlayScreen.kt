package com.voiceduel.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.voiceduel.ui.GameUiState

/**
 * R3 — the round screen.
 *
 * The performer records while the countdown runs; the rater waits. The
 * microphone permission is requested here, and a refusal is reported plainly
 * rather than crashing the app (C5).
 */
@Composable
fun PlayScreen(
    state: GameUiState,
    onMicrophonePermission: (Boolean) -> Unit,
    onSendNow: () -> Unit,
    onLeave: () -> Unit,
    onDismissNotice: () -> Unit,
) {
    val round = state.round ?: return
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
        onResult = onMicrophonePermission,
    )

    // Ask once per round, and only of the player who has to record.
    LaunchedEffect(round.number, state.isPerformer) {
        if (!state.isPerformer) return@LaunchedEffect

        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            onMicrophonePermission(true)
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DuelScaffold {
        Text(
            text = "الجولة ${round.number} من ${round.totalRounds}",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))
        SoundCard(emoji = round.soundEmoji, name = round.soundName)
        Spacer(Modifier.height(24.dp))

        if (state.isPerformer) {
            PerformerSection(
                state = state,
                onSendNow = onSendNow,
                onRetryPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
            )
        } else {
            RaterSection()
        }

        Spacer(Modifier.height(24.dp))
        NoticeBanner(notice = state.notice, onDismiss = onDismissNotice)
        Spacer(Modifier.height(8.dp))

        TextButton(onClick = onLeave) {
            Text("الخروج من المباراة")
        }
    }
}

@Composable
private fun PerformerSection(
    state: GameUiState,
    onSendNow: () -> Unit,
    onRetryPermission: () -> Unit,
) {
    val round = state.round ?: return

    Text(
        text = "دورك! قلّد الصوت الآن",
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.secondary,
        textAlign = TextAlign.Center,
    )

    Spacer(Modifier.height(16.dp))

    when {
        state.microphoneDenied -> {
            Text(
                text = "لم يُمنح إذن الميكروفون، لذا لن يُسجَّل صوتك في هذه الجولة",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRetryPermission, modifier = Modifier.fillMaxWidth()) {
                Text("السماح بالميكروفون")
            }
        }

        state.isSendingRecording -> {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(
                text = "جارٍ إرسال التسجيل…",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        state.isRecording -> {
            Text(
                text = "${state.secondsRemaining}",
                style = MaterialTheme.typography.displayLarge,
                fontSize = 72.sp,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "🔴 جارٍ التسجيل",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = {
                    val total = round.countdownSeconds.coerceAtLeast(1).toFloat()
                    state.secondsRemaining.toFloat() / total
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onSendNow, modifier = Modifier.fillMaxWidth()) {
                Text("أرسل الآن")
            }
        }

        else -> {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(
                text = "جارٍ تجهيز الميكروفون…",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RaterSection() {
    CircularProgressIndicator()
    Spacer(Modifier.height(16.dp))
    Text(
        text = "خصمك يسجّل الآن…",
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = "استعد للاستماع والتقييم",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}
