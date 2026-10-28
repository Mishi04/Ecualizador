package com.ecualizador.app.ui

import java.util.Locale

/** "123456 ms" → "2:03" */
fun formatTime(ms: Int): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}

/** mB → dB con signo, p. ej. 400f → "+4,0 dB". */
fun formatDb(mB: Float): String {
    val dB = mB / 100f
    val sign = if (dB >= 0f) "+" else ""
    return String.format(Locale.getDefault(), "%s%.1f dB", sign, dB)
}

/** Hz → etiqueta corta ("60 Hz", "3.6 kHz"). */
fun formatFreq(hz: Int): String {
    return if (hz < 1000) {
        String.format(Locale.getDefault(), "%d Hz", hz)
    } else {
        String.format(Locale.getDefault(), "%.1f kHz", hz / 1000.0)
    }
}
