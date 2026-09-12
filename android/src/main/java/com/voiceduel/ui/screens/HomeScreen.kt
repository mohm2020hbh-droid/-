package com.voiceduel.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.voiceduel.ui.ConnectionStatus
import com.voiceduel.ui.GameUiState
import com.voiceduel.ui.GameViewModel

/** R1 — create a room or join one with a code. */
@Composable
fun HomeScreen(
    state: GameUiState,
    onServerUrlChange: (String) -> Unit,
    onJoinCodeChange: (String) -> Unit,
    onCreateRoom: () -> Unit,
    onJoinRoom: () -> Unit,
    onDismissNotice: () -> Unit,
) {
    DuelScaffold {
        Text(
            text = "🎙️",
            style = MaterialTheme.typography.displayLarge,
        )
        Text(
            text = "مبارزة الأصوات",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "قلّد الصوت المطلوب، ودع خصمك يحكم عليك",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(24.dp))
        NoticeBanner(notice = state.notice, onDismiss = onDismissNotice)
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onCreateRoom,
            enabled = !state.isBusy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("إنشاء غرفة")
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = state.joinCodeInput,
            onValueChange = onJoinCodeChange,
            label = { Text("كود الغرفة") },
            placeholder = { Text("٤ أرقام") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = onJoinRoom,
            enabled = !state.isBusy && state.joinCodeInput.length == GameViewModel.ROOM_CODE_LENGTH,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("الانضمام لغرفة")
        }

        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = state.serverUrl,
            onValueChange = onServerUrlChange,
            label = { Text("عنوان الخادم") },
            supportingText = { Text("مثال: ws://192.168.1.5:8000/ws") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(8.dp))
        ConnectionChip(status = state.connection)
        if (state.connection == ConnectionStatus.CONNECTING) {
            Text(
                text = "جارٍ الاتصال بالخادم…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
