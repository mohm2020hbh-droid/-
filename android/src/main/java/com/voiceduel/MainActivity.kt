package com.voiceduel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.voiceduel.ui.VoiceDuelApp
import com.voiceduel.ui.theme.VoiceDuelTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            VoiceDuelTheme {
                VoiceDuelApp()
            }
        }
    }
}
