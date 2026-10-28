package com.ecualizador.app.audio

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import androidx.core.content.ContextCompat
import com.ecualizador.app.data.MusicRepository
import com.ecualizador.app.data.SettingsStore
import com.ecualizador.app.data.Song
import com.ecualizador.app.service.GlobalSessionService
import com.ecualizador.app.service.PlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Núcleo de la app: reproductor, cola, ecualizador y boost.
 *
 * Es un singleton en el proceso de la app; lo comparten la UI y [PlaybackService].
 */
object PlayerManager {

    const val MAX_BOOST_MB = 1000f   // +10 dB
    const val MAX_PREAMP_MB = 1000f  // ±10 dB
    const val MAX_BASS = 1000f

    private var app: Context? = null
    private lateinit var settings: SettingsStore
    private lateinit var audioManager: AudioManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val playerChain = EffectChain(priority = 0)
    val globalSession = GlobalSessionManager().also {
        it.settingsProvider = { fxSettings() }
    }

    private var mediaPlayer: MediaPlayer? = null
    private var prepared = false
    private var playWhenReady = false
    private var queue: List<Song> = emptyList()
    private var queueIndex = -1
    private var tickerJob: Job? = null
    private var focusRequest: AudioFocusRequest? = null

    // ---------------------------------------------------------------- estado
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs

    private val _current = MutableStateFlow<Song?>(null)
    val current: StateFlow<Song?> = _current

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _positionMs = MutableStateFlow(0)
    val positionMs: StateFlow<Int> = _positionMs

    private val _durationMs = MutableStateFlow(0)
    val durationMs: StateFlow<Int> = _durationMs

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // ----------------------------------------------------- estado del EQ
    private val _bandCount = MutableStateFlow(0)
    val bandCount: StateFlow<Int> = _bandCount

    private val _centerFreqs = MutableStateFlow<List<Int>>(emptyList())
    val centerFreqs: StateFlow<List<Int>> = _centerFreqs

    private val _bandRange = MutableStateFlow(-1500..1500)
    val bandRange: StateFlow<IntRange> = _bandRange

    private val _bandValues = MutableStateFlow<List<Float>>(emptyList())
    val bandValues: StateFlow<List<Float>> = _bandValues

    private val _preampMb = MutableStateFlow(0f)
    val preampMb: StateFlow<Float> = _preampMb

    private val _boostMb = MutableStateFlow(0f)
    val boostMb: StateFlow<Float> = _boostMb

    private val _bassStrength = MutableStateFlow(0f)
    val bassStrength: StateFlow<Float> = _bassStrength

    private val _eqEnabled = MutableStateFlow(true)
    val eqEnabled: StateFlow<Boolean> = _eqEnabled

    private val _presetIndex = MutableStateFlow(Presets.CUSTOM_INDEX)
    val presetIndex: StateFlow<Int> = _presetIndex

    private val _globalStatus = MutableStateFlow(GlobalStatus.DISABLED)
    val globalStatus: StateFlow<GlobalStatus> = _globalStatus

    // ----------------------------------------------------------------- init
    fun init(context: Context) {
        if (app != null) return
        app = context.applicationContext
        settings = SettingsStore(app!!)
        audioManager = app!!.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        _eqEnabled.value = settings.eqEnabled
        _boostMb.value = settings.boostMb
        _bassStrength.value = settings.bassStrength
        _preampMb.value = settings.preampMb
        _presetIndex.value = settings.presetIndex

        // Crea el reproductor (y el ecualizador de la sesión) en caliente,
        // para que la pantalla del EQ tenga las bandas desde el primer momento.
        ensurePlayer()

        scope.launch { reloadLibrary() }

        if (settings.globalEnabled) {
            _globalStatus.value = GlobalStatus.ACTIVE
            scope.launch {
                try {
                    ContextCompat.startForegroundService(
                        app!!,
                        Intent(app!!, GlobalSessionService::class.java),
                    )
                } catch (_: Throwable) {
                    // Inicio en segundo plano restringido: el servicio sticky
                    // del sistema se encargará de reiniciarlo.
                }
            }
        }
    }

    suspend fun reloadLibrary() {
        val ctx = app ?: return
        _songs.value = MusicRepository.loadSongs(ctx)
    }

    // ------------------------------------------------------------ reproductor
    private fun ensurePlayer(): MediaPlayer {
        mediaPlayer?.let { return it }
        val ctx = app ?: error("PlayerManager no inicializado")

        val m = MediaPlayer()
        m.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        m.setOnPreparedListener { mp ->
            prepared = true
            _durationMs.value = mp.duration
            if (playWhenReady) {
                mp.start()
                _isPlaying.value = true
                startTicker()
            }
        }
        m.setOnCompletionListener { next(auto = true) }
        m.setOnErrorListener { _, what, extra ->
            _error.value = "No se pudo reproducir la canción (código $what/$extra)"
            _isPlaying.value = false
            playWhenReady = false
            stopTicker()
            true
        }

        mediaPlayer = m
        attachPlayerChain(m.audioSessionId)
        return m
    }

    private fun attachPlayerChain(sessionId: Int) {
        val ok = playerChain.attach(sessionId)
        if (ok) {
            _bandCount.value = playerChain.bandCount
            _centerFreqs.value = playerChain.centerFreqsHz
            _bandRange.value = playerChain.bandRange

            val stored = settings.loadBands()
            val bands = if (stored.size == playerChain.bandCount) {
                stored
            } else {
                List(playerChain.bandCount) { 0f }
            }
            _bandValues.value = bands
            settings.saveBands(bands)

            if (_presetIndex.value in Presets.all.indices) {
                applyPresetInternal(_presetIndex.value)
            } else {
                applyEffects()
            }
        } else {
            _bandCount.value = 0
            _centerFreqs.value = emptyList()
            _error.value = playerChain.lastError
                ?: "El ecualizador no está disponible en este dispositivo"
        }
    }

    /** Reproduce la lista desde el índice indicado. */
    fun play(list: List<Song>, startIndex: Int) {
        if (list.isEmpty() || startIndex !in list.indices) return
        val ctx = app ?: return
        queue = list
        queueIndex = startIndex

        val song = list[startIndex]
        _error.value = null
        _current.value = song
        _positionMs.value = 0

        try {
            val p = ensurePlayer()
            p.reset()
            prepared = false
            _durationMs.value = song.durationMs.toInt()
            p.setDataSource(ctx, song.uri)
            playWhenReady = true
            requestAudioFocus()
            p.prepareAsync()
            startPlaybackService()
        } catch (t: Throwable) {
            _error.value = "No se pudo abrir la canción: ${t.message}"
            _isPlaying.value = false
            playWhenReady = false
        }
    }

    fun toggle() {
        if (_isPlaying.value) pause() else resume()
    }

    fun resume() {
        val p = mediaPlayer ?: return
        if (_current.value == null) return
        if (!prepared) {
            playWhenReady = true
            return
        }
        try {
            if (!p.isPlaying) {
                requestAudioFocus()
                p.start()
                _isPlaying.value = true
                playWhenReady = true
                startTicker()
                startPlaybackService()
            }
        } catch (t: Throwable) {
            _error.value = t.message
        }
    }

    fun pause() {
        playWhenReady = false
        pauseInternal(abandonFocus = true)
    }

    /** Pausa conservando la intención de reproducción (pérdida transitoria de foco). */
    private fun pauseTransient() {
        pauseInternal(abandonFocus = false)
    }

    private fun pauseInternal(abandonFocus: Boolean) {
        val p = mediaPlayer ?: return
        try {
            if (prepared && p.isPlaying) p.pause()
        } catch (_: Throwable) {
        }
        _isPlaying.value = false
        stopTicker()
        if (abandonFocus) abandonAudioFocus()
    }

    fun stop() {
        playWhenReady = false
        pauseInternal(abandonFocus = true)
        try {
            mediaPlayer?.reset()
        } catch (_: Throwable) {
        }
        prepared = false
        _current.value = null
        _positionMs.value = 0
        _durationMs.value = 0
        queue = emptyList()
        queueIndex = -1
    }

    fun next(auto: Boolean = false) {
        if (queue.isEmpty()) return
        when {
            queueIndex + 1 < queue.size -> play(queue, queueIndex + 1)
            !auto -> play(queue, 0)
            else -> {
                // Fin de la cola: volver al inicio de la canción actual en pausa.
                pause()
                seekTo(0)
            }
        }
    }

    fun previous() {
        if (queue.isEmpty()) return
        if (_positionMs.value > 3_000) {
            seekTo(0)
            return
        }
        val prev = if (queueIndex - 1 >= 0) queueIndex - 1 else queue.size - 1
        play(queue, prev)
    }

    fun seekTo(ms: Int) {
        try {
            mediaPlayer?.let {
                if (prepared) {
                    it.seekTo(ms)
                    _positionMs.value = ms
                }
            }
        } catch (_: Throwable) {
        }
    }

    fun clearError() {
        _error.value = null
    }

    // ----------------------------------------------------------------- ticker
    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                val p = mediaPlayer
                _positionMs.value = try {
                    if (prepared && p != null) p.currentPosition else 0
                } catch (_: Throwable) {
                    0
                }
                delay(500)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
        val p = mediaPlayer
        _positionMs.value = try {
            if (prepared && p != null) p.currentPosition else 0
        } catch (_: Throwable) {
            0
        }
    }

    // ------------------------------------------------------- ajustes del EQ
    fun setBand(index: Int, valueMb: Float) {
        val current = _bandValues.value
        if (index !in current.indices) return
        val updated = current.toMutableList()
        updated[index] = valueMb
        _bandValues.value = updated
        _presetIndex.value = Presets.CUSTOM_INDEX
        settings.saveBands(updated)
        settings.presetIndex = Presets.CUSTOM_INDEX
        applyEffects()
    }

    fun setPreamp(valueMb: Float) {
        _preampMb.value = valueMb.coerceIn(-MAX_PREAMP_MB, MAX_PREAMP_MB)
        settings.preampMb = _preampMb.value
        applyEffects()
    }

    fun setBoost(valueMb: Float) {
        _boostMb.value = valueMb.coerceIn(0f, MAX_BOOST_MB)
        settings.boostMb = _boostMb.value
        applyEffects()
    }

    fun setBass(strength: Float) {
        _bassStrength.value = strength.coerceIn(0f, MAX_BASS)
        settings.bassStrength = _bassStrength.value
        applyEffects()
    }

    fun setEqEnabled(enabled: Boolean) {
        _eqEnabled.value = enabled
        settings.eqEnabled = enabled
        applyEffects()
    }

    fun applyPreset(index: Int) {
        applyPresetInternal(index)
        settings.presetIndex = index
        settings.saveBands(_bandValues.value)
    }

    private fun applyPresetInternal(index: Int) {
        val preset = Presets.getOrNull(index) ?: return
        val freqs = _centerFreqs.value
        if (freqs.isEmpty()) return
        // gainAt() devuelve dB → mB (1 dB = 100 mB)
        _bandValues.value = freqs.map { preset.gainAt(it.toFloat()) * 10f }
        _presetIndex.value = index
        applyEffects()
    }

    /** Instantánea de la configuración actual para las sesiones globales. */
    fun fxSettings(): FxSettings = FxSettings(
        levelsMb = _bandValues.value.map { it + _preampMb.value },
        eqEnabled = _eqEnabled.value,
        bassStrength = _bassStrength.value,
        boostMb = _boostMb.value,
    )

    private fun applyEffects() {
        val s = fxSettings()
        playerChain.apply(s.levelsMb, s.eqEnabled, s.bassStrength, s.boostMb)
        globalSession.apply(s.levelsMb, s.eqEnabled, s.bassStrength, s.boostMb)
    }

    // ------------------------------------------------------------- modo global
    fun setGlobalEnabled(enabled: Boolean) {
        val ctx = app ?: return
        settings.globalEnabled = enabled
        _globalStatus.value = if (enabled) GlobalStatus.ACTIVE else GlobalStatus.DISABLED
        if (enabled) {
            try {
                ContextCompat.startForegroundService(
                    ctx,
                    Intent(ctx, GlobalSessionService::class.java),
                )
            } catch (t: Throwable) {
                _error.value = "No se pudo iniciar el modo global: ${t.message}"
            }
        } else {
            try {
                ctx.stopService(Intent(ctx, GlobalSessionService::class.java))
            } catch (_: Throwable) {
            }
        }
    }

    // ------------------------------------------------------------- servicio
    private fun startPlaybackService() {
        val ctx = app ?: return
        try {
            ctx.startForegroundService(Intent(ctx, PlaybackService::class.java))
        } catch (_: Throwable) {
            // Restricciones de inicio en segundo plano: sin servicio, la
            // reproducción de todos modos continúa mientras la app esté viva.
        }
    }

    // ---------------------------------------------------------- control de foco
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> pause()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pauseTransient()
            AudioManager.AUDIOFOCUS_GAIN ->
                if (playWhenReady && !_isPlaying.value) resume()
        }
    }

    private fun requestAudioFocus() {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener(focusListener)
            .build()
        focusRequest = req
        audioManager.requestAudioFocus(req)
    }

    private fun abandonAudioFocus() {
        focusRequest?.let {
            try {
                audioManager.abandonAudioFocusRequest(it)
            } catch (_: Throwable) {
            }
        }
    }
}
