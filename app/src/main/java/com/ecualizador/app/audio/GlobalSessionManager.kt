package com.ecualizador.app.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.audiofx.AudioEffect
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Estado del modo global (sesiones de otras apps). */
enum class GlobalStatus {
    /** Modo global apagado. */
    DISABLED,

    /** Servicio activo escuchando las sesiones de audio de otras apps. */
    ACTIVE,
}

/** Configución de efectos compartida por el PlayerManager. */
data class FxSettings(
    val levelsMb: List<Float>,
    val eqEnabled: Boolean,
    val bassStrength: Float,
    val boostMb: Float,
)

/** Sesión de otra app que está siendo procesada (o lo intentó). */
data class GlobalSession(
    val sessionId: Int,
    val packageName: String,
    val appName: String,
    val active: Boolean,
    val detail: String? = null,
)

/**
 * Modo global: igual que Wavelet/Poweramp EQ sin root.
 *
 * Las apps que reproducen música (Spotify, YouTube, VLC, etc.) anuncian sus
 * sesiones de audio con los broadcasts ACTION_OPEN/CLOSE_AUDIO_EFFECT_CONTROL_SESSION.
 * Este gestor registra un receptor para esas sesiones y les adjunta la cadena
 * de efectos (Equalizer + BassBoost + LoudnessEnhancer) con prioridad alta.
 *
 * Debe ejecutarse con un servicio en primer plano ([com.ecualizador.app.service.
 * GlobalSessionService]) para recibir los broadcasts en Android 8+.
 */
class GlobalSessionManager {

    private class SessionFx(
        val sessionId: Int,
        val info: GlobalSession,
        val chain: EffectChain,
    )

    private var receiver: BroadcastReceiver? = null
    private var context: Context? = null
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessions = LinkedHashMap<Int, SessionFx>()

    var status: GlobalStatus = GlobalStatus.DISABLED
        private set

    /** Proveedor de la configuración actual (lo conecta el PlayerManager). */
    var settingsProvider: (() -> FxSettings)? = null

    private val _activeSessions = MutableStateFlow<List<GlobalSession>>(emptyList())
    val activeSessions: StateFlow<List<GlobalSession>> = _activeSessions

    /**
     * Registra el receptor de sesiones. Se invoca desde el servicio en
     * primer plano, que mantiene vivo el proceso para recibir los broadcasts.
     */
    fun start(context: Context) {
        if (status == GlobalStatus.ACTIVE) return
        val appContext = context.applicationContext
        this.context = appContext

        val r = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
                if (sessionId <= 0) return
                val packageName = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME) ?: ""
                when (intent.action) {
                    AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION ->
                        scope.launch { openSession(sessionId, packageName) }

                    AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION ->
                        scope.launch { closeSession(sessionId) }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        }
        ContextCompat.registerReceiver(appContext, r, filter, ContextCompat.RECEIVER_EXPORTED)
        receiver = r
        status = GlobalStatus.ACTIVE
    }

    /** Desregistra el receptor y libera todas las cadenas de efectos. */
    fun stop() {
        status = GlobalStatus.DISABLED
        receiver?.let { r ->
            try {
                context?.unregisterReceiver(r)
            } catch (_: Throwable) {
            }
        }
        receiver = null
        scope.launch {
            mutex.withLock {
                sessions.values.forEach { it.chain.release() }
                sessions.clear()
                _activeSessions.value = emptyList()
            }
        }
    }

    /** Aplica la configuración a todas las sesiones activas. */
    fun apply(
        levelsMb: List<Float>,
        eqEnabled: Boolean,
        bassStrength: Float,
        boostMb: Float,
    ) {
        if (status != GlobalStatus.ACTIVE) return
        scope.launch {
            mutex.withLock {
                sessions.values.forEach {
                    it.chain.apply(levelsMb, eqEnabled, bassStrength, boostMb)
                }
            }
        }
    }

    // -------------------------------------------------------------- sesiones
    private suspend fun openSession(sessionId: Int, packageName: String) {
        mutex.withLock {
            // Los id de sesión se reciclan: si ya teníamos uno, liberarlo.
            sessions.remove(sessionId)?.chain?.release()

            var appName = packageName
            try {
                val pm = context?.packageManager
                if (pm != null && packageName.isNotEmpty()) {
                    appName = pm.getApplicationLabel(
                        pm.getApplicationInfo(packageName, 0),
                    ).toString()
                }
            } catch (_: Throwable) {
            }
            if (appName.isEmpty()) appName = "App desconocida"

            val chain = EffectChain(priority = 2000)
            val ok = chain.attach(sessionId)
            if (ok) {
                settingsProvider?.invoke()?.let { s ->
                    chain.apply(s.levelsMb, s.eqEnabled, s.bassStrength, s.boostMb)
                }
            }

            sessions[sessionId] = SessionFx(
                sessionId = sessionId,
                info = GlobalSession(
                    sessionId = sessionId,
                    packageName = packageName,
                    appName = appName,
                    active = ok,
                    detail = if (!ok) {
                        chain.lastError
                            ?: "No se pudo tomar el control de esta sesión"
                    } else {
                        null
                    },
                ),
                chain = chain,
            )
            _activeSessions.value = sessions.values.map { it.info }
        }
    }

    private suspend fun closeSession(sessionId: Int) {
        mutex.withLock {
            sessions.remove(sessionId)?.chain?.release()
            _activeSessions.value = sessions.values.map { it.info }
        }
    }
}
