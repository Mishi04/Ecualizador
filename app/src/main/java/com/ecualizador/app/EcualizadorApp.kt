package com.ecualizador.app

import android.app.Application
import com.ecualizador.app.audio.PlayerManager

class EcualizadorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PlayerManager.init(this)
    }
}
