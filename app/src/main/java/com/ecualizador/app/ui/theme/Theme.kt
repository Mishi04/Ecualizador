package com.ecualizador.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Background = Color(0xFF0E0E14)
private val SurfaceColor = Color(0xFF17171F)
private val SurfaceHigh = Color(0xFF1F1F2A)
private val Cyan = Color(0xFF22D3EE)
private val Purple = Color(0xFFA78BFA)
private val TextPrimary = Color(0xFFECECF2)
private val TextSecondary = Color(0xFF9A9AAE)
private val Success = Color(0xFF4ADE80)
private val Danger = Color(0xFFFF6B6B)

val SuccessColor = Success
val DangerColor = Danger

private val AppColorScheme = darkColorScheme(
    primary = Cyan,
    onPrimary = Color(0xFF04222A),
    onPrimaryContainer = Color(0xFFBDF2FD),
    primaryContainer = Color(0xFF0E4A57),
    secondary = Purple,
    onSecondary = Color(0xFF1B1030),
    secondaryContainer = Color(0xFF3A2A66),
    onSecondaryContainer = Color(0xFFE4DBFF),
    background = Background,
    onBackground = TextPrimary,
    surface = SurfaceColor,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = TextSecondary,
    outline = Color(0xFF3A3A4A),
    outlineVariant = Color(0xFF2A2A38),
    error = Danger,
    onError = Color(0xFF2B0505),
)

@Composable
fun EcualizadorTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AppColorScheme, content = content)
}
