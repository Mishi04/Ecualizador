package com.ecualizador.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ecualizador.app.audio.PlayerManager
import com.ecualizador.app.ui.AppRoot
import com.ecualizador.app.ui.theme.EcualizadorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        PlayerManager.init(applicationContext)
        setContent {
            EcualizadorTheme {
                AppRoot()
            }
        }
    }
}
