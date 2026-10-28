package com.ecualizador.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Curva de respuesta en frecuencia dibujada con un spline Catmull-Rom
 * a través de los niveles de cada banda.
 *
 * @param values niveles por banda en mB
 * @param maxAbsMb valor máximo absoluto del rango (p. ej. 1500)
 */
@Composable
fun EqCurve(
    values: List<Float>,
    maxAbsMb: Float,
    modifier: Modifier = Modifier,
) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    val lineColor = MaterialTheme.colorScheme.primary
    val fillTop = lineColor.copy(alpha = 0.35f)
    val fillBottom = lineColor.copy(alpha = 0f)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val midY = h / 2f
        val amplitude = h / 2f - 4.dp.toPx()

        if (values.isEmpty()) {
            drawLine(
                color = outline,
                start = Offset(0f, midY),
                end = Offset(w, midY),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
            return@Canvas
        }

        val n = values.size
        val points = values.mapIndexed { i, v ->
            val x = if (n == 1) w / 2f else w * i / (n - 1)
            val norm = (v / maxAbsMb).coerceIn(-1f, 1f)
            Offset(x, midY - norm * amplitude)
        }

        // Línea de referencia (0 dB)
        drawLine(
            color = outline,
            start = Offset(0f, midY),
            end = Offset(w, midY),
            strokeWidth = 1.dp.toPx(),
        )

        // Spline Catmull-Rom convertido a curvas cúbicas de Bézier
        val curve = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 0 until points.size - 1) {
                val p0 = points[maxOf(i - 1, 0)]
                val p1 = points[i]
                val p2 = points[i + 1]
                val p3 = points[minOf(i + 2, points.lastIndex)]
                val c1 = Offset(
                    p1.x + (p2.x - p0.x) / 6f,
                    p1.y + (p2.y - p0.y) / 6f,
                )
                val c2 = Offset(
                    p2.x - (p3.x - p1.x) / 6f,
                    p2.y - (p3.y - p1.y) / 6f,
                )
                cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y)
            }
        }

        // Relleno entre la curva y la línea central
        val fill = Path().apply {
            addPath(curve)
            lineTo(points.last().x, midY)
            lineTo(points.first().x, midY)
            close()
        }
        drawPath(
            path = fill,
            brush = Brush.verticalGradient(
                colors = listOf(fillTop, fillBottom),
                startY = 0f,
                endY = h,
            ),
        )

        drawPath(
            path = curve,
            color = lineColor,
            style = Stroke(
                width = 3.dp.toPx(),
                cap = StrokeCap.Round,
            ),
        )
    }
}
