package com.ecualizador.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ecualizador.app.audio.GlobalStatus
import com.ecualizador.app.audio.PlayerManager
import com.ecualizador.app.audio.Presets
import com.ecualizador.app.ui.components.EqCurve
import com.ecualizador.app.ui.components.VerticalSlider
import com.ecualizador.app.ui.formatDb
import com.ecualizador.app.ui.formatFreq
import com.ecualizador.app.ui.theme.DangerColor
import com.ecualizador.app.ui.theme.SuccessColor
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun EqualizerScreen() {
    val bandCount by PlayerManager.bandCount.collectAsState()
    val centerFreqs by PlayerManager.centerFreqs.collectAsState()
    val bandValues by PlayerManager.bandValues.collectAsState()
    val bandRange by PlayerManager.bandRange.collectAsState()
    val preamp by PlayerManager.preampMb.collectAsState()
    val bass by PlayerManager.bassStrength.collectAsState()
    val boost by PlayerManager.boostMb.collectAsState()
    val eqEnabled by PlayerManager.eqEnabled.collectAsState()
    val presetIndex by PlayerManager.presetIndex.collectAsState()
    val globalStatus by PlayerManager.globalStatus.collectAsState()
    val globalSessions by PlayerManager.globalSession.activeSessions.collectAsState()

    val rangeStart = bandRange.first.toFloat()
    val rangeEnd = bandRange.last.toFloat()
    val maxAbs = maxOf(abs(bandRange.first), abs(bandRange.last)).toFloat()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(4.dp))

        // ------------------------------------------------ Ecualizador principal
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Ecualizador",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Switch(
                        checked = eqEnabled,
                        onCheckedChange = { PlayerManager.setEqEnabled(it) },
                    )
                }

                Spacer(Modifier.height(8.dp))

                EqCurve(
                    values = bandValues,
                    maxAbsMb = maxAbs,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp),
                )

                Spacer(Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Preajuste",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PresetDropdown(presetIndex = presetIndex)
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // --------------------------------------------------------- Bandas
        if (bandCount == 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
                ),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = "Ecualizador no disponible",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Tu dispositivo no expone un ecualizador por hardware (o la app no tiene control sobre él). El boost de volumen sigue disponible.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 16.dp)) {
                    Text(
                        text = "Bandas (${bandCount})",
                        modifier = Modifier.padding(horizontal = 4.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(
                            4.dp,
                            Alignment.CenterHorizontally,
                        ),
                    ) {
                        for (index in 0 until bandCount) {
                            val value = bandValues
                                .getOrElse(index) { 0f }
                                .coerceIn(rangeStart, rangeEnd)
                            val freq = centerFreqs.getOrElse(index) { 0 }

                            Column(
                                modifier = Modifier.width(54.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = formatDb(value),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                VerticalSlider(
                                    value = value,
                                    onValueChange = { PlayerManager.setBand(index, it) },
                                    valueRange = rangeStart..rangeEnd,
                                    enabled = eqEnabled,
                                    sliderHeight = 170.dp,
                                    sliderWidth = 44.dp,
                                )
                                Text(
                                    text = formatFreq(freq),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // -------------------------------------------- Preamp, graves y boost
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                SliderRow(
                    label = "Preamp",
                    valueText = formatDb(preamp),
                    value = preamp,
                    onValueChange = { PlayerManager.setPreamp(it) },
                    valueRange = -PlayerManager.MAX_PREAMP_MB..PlayerManager.MAX_PREAMP_MB,
                    enabled = eqEnabled,
                )

                Spacer(Modifier.height(6.dp))

                SliderRow(
                    label = "Graves (BassBoost)",
                    valueText = "${(bass / 10f).roundToInt()} %",
                    value = bass,
                    onValueChange = { PlayerManager.setBass(it) },
                    valueRange = 0f..PlayerManager.MAX_BASS,
                )

                Spacer(Modifier.height(6.dp))

                SliderRow(
                    label = "Boost de volumen",
                    valueText = formatDb(boost),
                    value = boost,
                    onValueChange = { PlayerManager.setBoost(it) },
                    valueRange = 0f..PlayerManager.MAX_BOOST_MB,
                )

                Text(
                    text = "El boost supera el volumen normal del sistema (hasta +10 dB) y también se aplica en el modo global.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // ------------------------------------------------------- Modo global
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Language,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Modo global (todas las apps)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Aplica el EQ y el boost al audio de Spotify, YouTube, etc.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = globalStatus == GlobalStatus.ACTIVE,
                        onCheckedChange = { PlayerManager.setGlobalEnabled(it) },
                    )
                }

                Spacer(Modifier.height(10.dp))

                when (globalStatus) {
                    GlobalStatus.ACTIVE -> StatusRow(
                        icon = Icons.Filled.CheckCircle,
                        color = SuccessColor,
                        text = "Activo: detectando y ecualizando las sesiones de audio de otras apps.",
                    )

                    GlobalStatus.DISABLED -> StatusRow(
                        icon = Icons.Filled.Language,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        text = "Desactivado: solo afecta al audio de esta app.",
                    )
                }

                if (globalStatus == GlobalStatus.ACTIVE) {
                    Spacer(Modifier.height(10.dp))

                    if (globalSessions.isEmpty()) {
                        Text(
                            text = "Aún no hay sesiones detectadas. Abre una app que reproduzca música (Spotify, YouTube…). Si ya estaba sonando al activar el modo global, pausa y reanuda la reproducción.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            globalSessions.forEach { session ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (session.active) {
                                            Icons.Filled.CheckCircle
                                        } else {
                                            Icons.Filled.ErrorOutline
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = if (session.active) {
                                            SuccessColor
                                        } else {
                                            DangerColor
                                        },
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = session.appName,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (session.active) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                    if (!session.active) {
                                        Text(
                                            text = "sin control",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = DangerColor,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Cómo funciona: cada app que suena anuncia su sesión de audio y aquí se le aplica el ecualizador y el boost (igual que hacen Wavelet o Poweramp EQ). Desactiva otros ecualizadores para que esta app tome el control.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SliderRow(
    label: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean = true,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled,
        )
    }
}

@Composable
private fun StatusRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: androidx.compose.ui.graphics.Color,
    text: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = color,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color,
        )
    }
}

@Composable
private fun PresetDropdown(presetIndex: Int) {
    var open by remember { mutableStateOf(false) }

    Row {
        FilledTonalButton(onClick = { open = true }) {
            Text(Presets.nameOf(presetIndex))
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Presets.all.forEachIndexed { index, preset ->
                DropdownMenuItem(
                    text = { Text(preset.name) },
                    onClick = {
                        PlayerManager.applyPreset(index)
                        open = false
                    },
                    trailingIcon = {
                        if (index == presetIndex) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                            )
                        }
                    },
                )
            }
        }
    }
}
