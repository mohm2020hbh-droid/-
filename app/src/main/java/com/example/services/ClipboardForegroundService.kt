package com.example.services

import android.app.*
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.database.AppDatabase
import com.example.database.ClipboardType
import com.example.repositories.ClipboardRepository
import kotlinx.coroutines.*

class ClipboardForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var clipboardRepository: ClipboardRepository
    private var clipboardListener: ClipboardManager.OnPrimaryClipChangedListener? = null
    private var isMonitoring = false

    companion object {
        const val CHANNEL_ID = "clipflow_foreground_channel"
        const val NOTIFICATION_ID = 101
        const val ACTION_STOP = "com.example.ACTION_STOP_SERVICE"
        const val ACTION_START = "com.example.ACTION_START_SERVICE"
        
        var isServiceRunning = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        val database = AppDatabase.getDatabase(this)
        clipboardRepository = ClipboardRepository(database.clipboardDao())
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        isServiceRunning = true
        startForeground(NOTIFICATION_ID, buildNotification())
        startClipboardMonitoring()

        return START_STICKY
    }

    private fun startClipboardMonitoring() {
        if (isMonitoring) return
        isMonitoring = true

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
            val clipData = clipboard.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val text = clipData.getItemAt(0).text?.toString() ?: ""
                if (text.isNotEmpty()) {
                    serviceScope.launch {
                        val type = if (text.startsWith("http://") || text.startsWith("https://")) {
                            ClipboardType.LINK
                        } else if (text.length > 150 && (text.contains("{") || text.contains("class ") || text.contains("fun "))) {
                            ClipboardType.CODE
                        } else {
                            ClipboardType.TEXT
                        }
                        clipboardRepository.insertItem(
                            content = text,
                            type = type,
                            sourceApp = "منسوخ في الخلفية"
                        )
                    }
                }
            }
        }

        // Note: Android 10+ restricts clipboard access in the background.
        // The listener is added, but it will only fire if our application or the IME has focus.
        try {
            clipboard.addPrimaryClipChangedListener(clipboardListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, ClipboardForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openIntent = Intent(this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("مراقب الحافظة نشط")
            .setContentText("COPY يعمل في الخلفية لمراقبة وحفظ الحافظة محليًا.")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "إيقاف الخدمة", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "مراقب الحافظة COPY",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "قناة إشعارات لخدمة مراقبة حافظة COPY"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        isMonitoring = false
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardListener?.let {
            try {
                clipboard.removePrimaryClipChangedListener(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        serviceScope.cancel()
    }
}
