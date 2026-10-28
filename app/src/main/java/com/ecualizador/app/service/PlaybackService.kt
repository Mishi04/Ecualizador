package com.ecualizador.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media.app.NotificationCompat.MediaStyle
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.ecualizador.app.MainActivity
import com.ecualizador.app.R
import com.ecualizador.app.audio.PlayerManager
import com.ecualizador.app.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Servicio en primer plano que mantiene la notificación de medios
 * (controles en la pantalla de bloqueo / panel de medios) mientras suena música.
 */
class PlaybackService : Service() {

    private var session: MediaSessionCompat? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observeJob: Job? = null
    private var isForeground = false

    override fun onCreate() {
        super.onCreate()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Reproducción",
                NotificationManager.IMPORTANCE_LOW,
            )
        )

        session = MediaSessionCompat(this, "Ecualizador").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = PlayerManager.resume()
                override fun onPause() = PlayerManager.pause()
                override fun onSkipToNext() = PlayerManager.next()
                override fun onSkipToPrevious() = PlayerManager.previous()
                override fun onSeekTo(pos: Long) = PlayerManager.seekTo(pos.toInt())
                override fun onStop() {
                    PlayerManager.stop()
                    stopEverything()
                }
            })
            isActive = true
        }

        observeJob = scope.launch {
            combine(
                PlayerManager.current,
                PlayerManager.isPlaying,
                PlayerManager.positionMs,
            ) { song, playing, position -> Triple(song, playing, position) }
                .collect { (song, playing, position) ->
                    if (song == null) {
                        stopEverything()
                    } else {
                        updateSession(song, playing, position)
                        updateNotification(song, playing)
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> PlayerManager.toggle()
            ACTION_NEXT -> PlayerManager.next()
            ACTION_PREVIOUS -> PlayerManager.previous()
            ACTION_STOP -> {
                PlayerManager.stop()
                stopEverything()
                return START_NOT_STICKY
            }
        }

        val song = PlayerManager.current.value
        if (song == null) {
            // Reinicio del sistema sin nada que reproducir.
            stopEverything()
            return START_NOT_STICKY
        }
        // Siempre se entra en primer plano tras startForegroundService()
        // para cumplir el contrato de Android (5 s).
        val playing = PlayerManager.isPlaying.value
        enterForeground(buildNotification(song, playing))
        return START_STICKY
    }

    override fun onDestroy() {
        observeJob?.cancel()
        scope.cancel()
        session?.release()
        session = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ------------------------------------------------------------ utilidades
    private fun enterForeground(notification: Notification) {
        ServiceCompat.startForeground(
            this,
            NOTIF_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
        isForeground = true
    }

    private fun updateNotification(song: Song, playing: Boolean) {
        val notification = buildNotification(song, playing)
        if (!isForeground) {
            enterForeground(notification)
        } else {
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification)
        }
    }

    private fun stopEverything() {
        if (isForeground) {
            ServiceCompat.stopForeground(this, Service.STOP_FOREGROUND_REMOVE)
            isForeground = false
        }
        session?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_STOPPED, 0, 0f)
                .build()
        )
        stopSelf()
    }

    private fun updateSession(song: Song, playing: Boolean, position: Int) {
        val state = when {
            playing -> PlaybackStateCompat.STATE_PLAYING
            else -> PlaybackStateCompat.STATE_PAUSED
        }
        session?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(ACTIONS)
                .setState(state, position.toLong(), if (playing) 1f else 0f)
                .build()
        )
        session?.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, song.durationMs)
                .build()
        )
    }

    private fun buildNotification(song: Song, playing: Boolean): Notification {
        val token = session?.sessionToken

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        fun servicePending(action: String, requestCode: Int): PendingIntent =
            PendingIntent.getService(
                this,
                requestCode,
                Intent(this, PlaybackService::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_eq)
            .setContentTitle(song.title)
            .setContentText(song.artist)
            .setContentIntent(contentIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setOnlyAlertOnce(true)
            .setOngoing(playing)
            .setShowWhen(false)
            .setDeleteIntent(servicePending(ACTION_STOP, 4))
            .addAction(
                android.R.drawable.ic_media_previous,
                "Anterior",
                servicePending(ACTION_PREVIOUS, 3),
            )
            .addAction(
                if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (playing) "Pausar" else "Reproducir",
                servicePending(ACTION_PLAY_PAUSE, 1),
            )
            .addAction(
                android.R.drawable.ic_media_next,
                "Siguiente",
                servicePending(ACTION_NEXT, 2),
            )

        if (token != null) {
            builder.setStyle(
                MediaStyle()
                    .setMediaSession(token)
                    .setShowActionsInCompactView(0, 1, 2)
            )
        }
        return builder.build()
    }

    companion object {
        private const val CHANNEL_ID = "reproduccion"
        private const val NOTIF_ID = 42

        const val ACTION_PLAY_PAUSE = "com.ecualizador.app.action.PLAY_PAUSE"
        const val ACTION_NEXT = "com.ecualizador.app.action.NEXT"
        const val ACTION_PREVIOUS = "com.ecualizador.app.action.PREVIOUS"
        const val ACTION_STOP = "com.ecualizador.app.action.STOP"

        private const val ACTIONS =
            PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_STOP
    }
}
