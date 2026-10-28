package com.ecualizador.app.data

import android.content.Context

/**
 * Persistencia simple de la configuración del ecualizador y del boost.
 */
class SettingsStore(context: Context) {

    private val prefs =
        context.getSharedPreferences("ecualizador_prefs", Context.MODE_PRIVATE)

    fun loadBands(): List<Float> = prefs.getString("bands", "")
        ?.split(',')
        ?.mapNotNull { it.toFloatOrNull() }
        ?: emptyList()

    fun saveBands(values: List<Float>) {
        prefs.edit().putString("bands", values.joinToString(",")).apply()
    }

    var preampMb: Float
        get() = prefs.getFloat("preamp", 0f)
        set(value) {
            prefs.edit().putFloat("preamp", value).apply()
        }

    var boostMb: Float
        get() = prefs.getFloat("boost", 0f)
        set(value) {
            prefs.edit().putFloat("boost", value).apply()
        }

    var bassStrength: Float
        get() = prefs.getFloat("bass", 0f)
        set(value) {
            prefs.edit().putFloat("bass", value).apply()
        }

    var eqEnabled: Boolean
        get() = prefs.getBoolean("eq_enabled", true)
        set(value) {
            prefs.edit().putBoolean("eq_enabled", value).apply()
        }

    var presetIndex: Int
        get() = prefs.getInt("preset", -1)
        set(value) {
            prefs.edit().putInt("preset", value).apply()
        }

    var globalEnabled: Boolean
        get() = prefs.getBoolean("global_enabled", false)
        set(value) {
            prefs.edit().putBoolean("global_enabled", value).apply()
        }
}
