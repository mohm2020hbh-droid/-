package com.voiceduel.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voiceduel.ui.GameUiState

/** R2 — show the code so it can be passed to the other player out of band. */
@Composable
fun WaitingScreen(
    state: GameUiState,
    onLeave: () -> Unit,
    onDismissNotice: () -> Unit,
) {
    DuelScaffold {
        Text(
            text = "شارك هذا الكود مع خصمك",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        ) {
            Text(
                text = state.roomCode.orEmpty(),
                style = MaterialTheme.typography.displayLarge,
                fontSize = 64.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
            )
        }

        Spacer(Modifier.height(32.dp))

        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))

        Spacer(Modifier.height(16.dp))

        Text(
            text = "بانتظار انضمام اللاعب الثاني…",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(24.dp))
        NoticeBanner(notice = state.notice, onDismiss = onDismissNotice)
        Spacer(Modifier.height(8.dp))

        TextButton(onClick = onLeave) {
            Text("إلغاء والعودة")
        }
    }
}
