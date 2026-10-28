package com.ecualizador.app.audio

import kotlin.math.ln

/**
 * Un preajuste describe una curva de ganancia (en dB) a lo largo del espectro.
 * Los puntos son pares (frecuencia en Hz, ganancia en dB).
 */
data class EqPreset(
    val name: String,
    val points: List<Pair<Float, Float>>,
) {
    /** Ganancia en dB para una frecuencia dada, interpolando logarítmicamente. */
    fun gainAt(freqHz: Float): Float {
        if (points.isEmpty()) return 0f
        val first = points.first()
        val last = points.last()
        if (freqHz <= first.first) return first.second
        if (freqHz >= last.first) return last.second

        for (i in 0 until points.size - 1) {
            val (f0, g0) = points[i]
            val (f1, g1) = points[i + 1]
            if (freqHz in f0..f1) {
                if (f1 <= f0) return g1
                val t = ln(freqHz / f0) / ln(f1 / f0)
                return g0 + (g1 - g0) * t
            }
        }
        return last.second
    }
}

object Presets {

    const val CUSTOM_INDEX = -1

    val all: List<EqPreset> = listOf(
        EqPreset(
            "Plano",
            listOf(20f to 0f, 16_000f to 0f),
        ),
        EqPreset(
            "Pop",
            listOf(
                20f to -1f, 80f to 3f, 250f to 1.5f, 1_000f to 0f,
                3_000f to -1f, 6_000f to 1f, 16_000f to 2f,
            ),
        ),
        EqPreset(
            "Rock",
            listOf(
                20f to 5f, 100f to 4f, 300f to 1f, 1_000f to 0f,
                3_000f to 1f, 6_000f to 3f, 16_000f to 4f,
            ),
        ),
        EqPreset(
            "Jazz",
            listOf(
                20f to 4f, 100f to 1f, 300f to -1f, 1_000f to 1f,
                3_000f to 2f, 6_000f to 2f, 16_000f to 3f,
            ),
        ),
        EqPreset(
            "Clásico",
            listOf(
                20f to 4f, 100f to 3f, 300f to 0f, 1_000f to 0f,
                3_000f to 0f, 6_000f to 2f, 16_000f to 4f,
            ),
        ),
        EqPreset(
            "Voz",
            listOf(
                20f to -4f, 100f to -2f, 300f to 2f, 1_000f to 4f,
                3_000f to 4f, 6_000f to 2f, 16_000f to -2f,
            ),
        ),
        EqPreset(
            "Graves +",
            listOf(
                20f to 9f, 100f to 7f, 300f to 2f, 1_000f to 0f,
                3_000f to 0f, 16_000f to 1f,
            ),
        ),
        EqPreset(
            "Agudos +",
            listOf(
                20f to 0f, 300f to 0f, 1_000f to 1f, 3_000f to 4f,
                6_000f to 5f, 16_000f to 7f,
            ),
        ),
    )

    fun getOrNull(index: Int): EqPreset? = all.getOrNull(index)

    fun nameOf(index: Int): String =
        if (index in all.indices) all[index].name else "Personalizado"
}
