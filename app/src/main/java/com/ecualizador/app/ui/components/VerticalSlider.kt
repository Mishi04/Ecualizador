package com.ecualizador.app.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Slider vertical: se apoya en la rotación del Slider horizontal estándar.
 *
 * Se usa un [Layout] propio para medir al hijo con sus dimensiones reales
 * (el alto visible) y colocarlo centrado antes de rotarlo -90°, de modo que
 * el mínimo queda abajo y el máximo arriba.
 */
@Composable
fun VerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    sliderWidth: Dp = 46.dp,
    sliderHeight: Dp = 200.dp,
    enabled: Boolean = true,
) {
    val density = LocalDensity.current
    val widthPx = with(density) { sliderWidth.roundToPx() }
    val heightPx = with(density) { sliderHeight.roundToPx() }

    Layout(
        content = {
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                enabled = enabled,
                modifier = Modifier
                    .size(width = sliderHeight, height = sliderWidth)
                    .graphicsLayer { rotationZ = -90f },
            )
        },
        modifier = modifier,
    ) { measurables, _ ->
        val child = measurables[0].measure(
            Constraints.fixed(width = heightPx, height = widthPx),
        )
        layout(widthPx, heightPx) {
            // Centrar el hijo (más ancho que la caja) para que tras la
            // rotación quede exactamente dentro del área visible.
            child.place(x = (widthPx - heightPx) / 2, y = (heightPx - widthPx) / 2)
        }
    }
}
