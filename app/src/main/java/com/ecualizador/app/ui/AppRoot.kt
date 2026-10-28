package com.ecualizador.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import com.ecualizador.app.audio.PlayerManager
import com.ecualizador.app.ui.screens.EqualizerScreen
import com.ecualizador.app.ui.screens.LibraryScreen
import com.ecualizador.app.ui.screens.PlayerScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot() {
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }

    val error by PlayerManager.error.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Permiso de notificaciones (Android 13+): para la notificación de medios.
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(error) {
        val message = error
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            PlayerManager.clearError()
        }
    }

    val titles = listOf("Biblioteca", "Reproductor", "Ecualizador")

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(titles[tab], fontWeight = FontWeight.SemiBold)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = {
                        Icon(Icons.Filled.MusicNote, contentDescription = "Biblioteca")
                    },
                    label = { Text("Biblioteca") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Reproductor")
                    },
                    label = { Text("Reproductor") },
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = {
                        Icon(Icons.Filled.GraphicEq, contentDescription = "Ecualizador")
                    },
                    label = { Text("Ecualizador") },
                )
            }
        },
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding)) {
            when (tab) {
                0 -> LibraryScreen(
                    onPlay = { list, index ->
                        PlayerManager.play(list, index)
                        tab = 1
                    },
                )

                1 -> PlayerScreen(
                    onOpenLibrary = { tab = 0 },
                    onOpenEqualizer = { tab = 2 },
                )

                2 -> EqualizerScreen()
            }
        }
    }
}
