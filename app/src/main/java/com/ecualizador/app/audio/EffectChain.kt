package com.ecualizador.app.audio

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import kotlin.math.roundToInt

/**
 * Cadena de efectos de audio (Equalizer + BassBoost + LoudnessEnhancer)
 * ligada a una sesión de audio concreta.
 *
 * Se usa para:
 *  - la sesión del reproductor propio (la app), y
 *  - la sesión global 0 (mezcla de todas las apps), si el dispositivo lo permite.
 *
 * Las unidades de nivel son mB (milibelios): 1000 mB = 10 dB.
 */
class EffectChain(private val priority: Int) {

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var loudness: LoudnessEnhancer? = null

    /** Número de bandas reales que expone el dispositivo. */
    var bandCount: Int = 0
        private set

    /** Frecuencia central de cada banda, en Hz. */
    var centerFreqsHz: List<Int> = emptyList()
        private set

    /** Rango de nivel de banda admitido, en mB. */
    var bandRange: IntRange = -1500..1500
        private set

    var isActive: Boolean = false
        private set

    var lastError: String? = null
        private set

    /**
     * Crea los efectos sobre [sessionId]. Devuelve true si el ecualizador
     * quedó operativo (con bandas y con control).
     */
    fun attach(sessionId: Int): Boolean {
        release()
        return try {
            val eq = Equalizer(priority, sessionId)
            if (!eq.hasControl()) {
                eq.release()
                lastError = "Sin control sobre el ecualizador"
                return false
            }
            val bands = eq.numberOfBands.toInt()
            if (bands <= 0) {
                eq.release()
                lastError = "El dispositivo no expone bandas de ecualización"
                return false
            }
            // getCenterFreq() devuelve miliciclos (mHz) → Hz
            val freqs = (0 until bands).map { eq.getCenterFreq(it.toShort()) / 1000 }
            val range = eq.bandLevelRange

            equalizer = eq
            bandCount = bands
            centerFreqsHz = freqs
            bandRange = range[0].toInt()..range[1].toInt()

            bassBoost = try {
                BassBoost(priority, sessionId)
            } catch (_: Throwable) {
                null
            }
            loudness = try {
                LoudnessEnhancer(sessionId)
            } catch (_: Throwable) {
                null
            }

            isActive = true
            lastError = null
            true
        } catch (t: Throwable) {
            lastError = t.message ?: "Efectos de audio no disponibles"
            release()
            false
        }
    }

    /**
     * Prueba de lectura/escritura sobre una banda (útil para comprobar que la
     * sesión global realmente responde).
     */
    fun verifyLoopback(): Boolean {
        val eq = equalizer ?: return false
        return try {
            val original = eq.getBandLevel(0)
            val probe = 300.coerceIn(bandRange.first, bandRange.last).toShort()
            eq.setBandLevel(0, probe)
            val ok = eq.getBandLevel(0) == probe
            eq.setBandLevel(0, original)
            ok
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Aplica la configuración completa a la cadena.
     *
     * @param levelsMb niveles por banda en mB (el llamador añade ya el preamp)
     * @param eqEnabled estado general del ecualizador
     * @param bassStrength fuerza de graves 0..1000
     * @param boostMb ganancia del LoudnessEnhancer en mB (0..1000 = 0..10 dB)
     */
    fun apply(
        levelsMb: List<Float>,
        eqEnabled: Boolean,
        bassStrength: Float,
        boostMb: Float,
    ) {
        try {
            equalizer?.let { eq ->
                for (i in levelsMb.indices) {
                    if (i >= bandCount) break
                    val v = levelsMb[i]
                        .roundToInt()
                        .coerceIn(bandRange.first, bandRange.last)
                    eq.setBandLevel(i.toShort(), v.toShort())
                }
                eq.setEnabled(eqEnabled)
            }
            bassBoost?.let { bb ->
                if (bb.strengthSupported) {
                    bb.setStrength(bassStrength.roundToInt().coerceIn(0, 1000).toShort())
                    bb.setEnabled(true)
                }
            }
            loudness?.let { le ->
                val gain = boostMb.roundToInt().coerceIn(0, 1000)
                le.setTargetGain(gain)
                le.setEnabled(gain > 0)
            }
        } catch (t: Throwable) {
            lastError = t.message
        }
    }

    fun release() {
        try {
            equalizer?.release()
        } catch (_: Throwable) {
        }
        try {
            bassBoost?.release()
        } catch (_: Throwable) {
        }
        try {
            loudness?.release()
        } catch (_: Throwable) {
        }
        equalizer = null
        bassBoost = null
        loudness = null
        isActive = false
        bandCount = 0
        centerFreqsHz = emptyList()
    }
}
