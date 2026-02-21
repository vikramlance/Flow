package com.flow.presentation.timer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * T038 — Foreground service that runs the Focus Timer countdown independently of the UI.
 *
 * Start via [TimerForegroundService.start] and stop via [TimerForegroundService.stop].
 * Broadcasts [ACTION_TIMER_FINISHED] when the countdown reaches zero.
 */
class TimerForegroundService : Service() {

    private val scope = CoroutineScope(Dispatchers.Default)
    private var countdownJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val durationSeconds = intent?.getIntExtra(EXTRA_DURATION_SECONDS, 25 * 60) ?: (25 * 60)
        startForeground(NOTIFICATION_ID, buildNotification("Focus timer running…"))
        startCountdown(durationSeconds)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun startCountdown(durationSeconds: Int) {
        countdownJob?.cancel()
        countdownJob = scope.launch {
            var remaining = durationSeconds
            while (remaining > 0) {
                delay(1_000L)
                remaining--
                val notif = buildNotification(formatTime(remaining))
                (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                    .notify(NOTIFICATION_ID, notif)
            }
            onTimerFinished()
        }
    }

    private fun onTimerFinished() {
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 1_500)
        } catch (_: Exception) {}
        sendBroadcast(Intent(ACTION_TIMER_FINISHED).setPackage(packageName))
        stopSelf()
    }

    private fun buildNotification(text: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Focus Timer")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Focus Timer", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    private fun formatTime(seconds: Int): String =
        "%02d:%02d".format(seconds / 60, seconds % 60)

    companion object {
        const val ACTION_TIMER_FINISHED = "com.flow.timer.TIMER_FINISHED"
        const val EXTRA_DURATION_SECONDS = "duration_seconds"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "flow_timer"

        fun start(context: Context, durationSeconds: Int) {
            val intent = Intent(context, TimerForegroundService::class.java)
                .putExtra(EXTRA_DURATION_SECONDS, durationSeconds)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TimerForegroundService::class.java))
        }
    }
}
