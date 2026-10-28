package com.ecualizador.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.ecualizador.app.MainActivity
import com.ecualizador.app.R
import com.ecualizador.app.audio.PlayerManager

/**
 * Servicio en primer plano que mantiene el proceso vivo mientras el modo
 * global está activo, para que [PlayerManager.globalSession] pueda recibir
 * los broadcasts de sesiones de audio de las demás apps.
 *
 * Es el patrón que usan los ecualizadores globales de Play Store (Wavelet,
 * Poweramp EQ…): notificación persistente + receptor de sesiones.
 */
class GlobalSessionService : Service() {

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Modo global",
                NotificationManager.IMPORTANCE_MIN,
            )
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISABLE) {
            PlayerManager.setGlobalEnabled(false)
            stopSelf()
            return START_NOT_STICKY
        }

        // Entra en primer plano inmediatamente (contrato de startForegroundService).
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(
                this,
                NOTIF_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }

        // Registra el receptor de sesiones de audio.
        PlayerManager.globalSession.start(this)
        return START_STICKY
    }

    override fun onDestroy() {
        PlayerManager.globalSession.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val disableIntent = PendingIntent.getService(
            this,
            5,
            Intent(this, GlobalSessionService::class.java).setAction(ACTION_DISABLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_eq)
            .setContentTitle("Ecualizador global activo")
            .setContentText("Detectando las sesiones de audio de otras apps…")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(contentIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Desactivar",
                disableIntent,
            )
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "modo_global"
        private const val NOTIF_ID = 43

        const val ACTION_DISABLE = "com.ecualizador.app.action.DISABLE_GLOBAL"
    }
}
