package com.voiceduel.data

import android.content.Context
import com.voiceduel.BuildConfig

/**
 * Remembers the server address between launches.
 *
 * There is no account system (C2); this is the only thing the app persists, so
 * a tester does not have to retype their server's address on every run.
 */
class ServerConfig(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, null)?.takeIf { it.isNotBlank() }
            ?: BuildConfig.DEFAULT_SERVER_URL
        set(value) {
            prefs.edit().putString(KEY_SERVER_URL, value.trim()).apply()
        }

    companion object {
        private const val PREFS_NAME = "voice_duel_prefs"
        private const val KEY_SERVER_URL = "server_url"

        /** True for an address the OkHttp WebSocket client can actually dial. */
        fun isValidUrl(url: String): Boolean {
            val trimmed = url.trim()
            return (trimmed.startsWith("ws://") || trimmed.startsWith("wss://")) &&
                trimmed.length > "wss://".length
        }
    }
}
