package com.pocketsloth.app.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.pocketsloth.app.ui.theme.LossCurveColor

@Composable
fun LossCurveCanvas(
    lossPoints: List<Float>,
    modifier: Modifier = Modifier,
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val axisLabelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp),
    ) {
        val w = size.width
        val h = size.height
        val padL = 8f
        val padR = 8f
        val padT = 12f
        val padB = 12f
        val chartW = w - padL - padR
        val chartH = h - padT - padB

        // Horizontal grid
        for (i in 0..4) {
            val y = padT + chartH * i / 4f
            drawLine(gridColor, Offset(padL, y), Offset(w - padR, y), strokeWidth = 1f)
        }

        if (lossPoints.size < 2) {
            // Placeholder baseline
            drawLine(
                color = axisLabelColor.copy(alpha = 0.35f),
                start = Offset(padL, padT + chartH * 0.5f),
                end = Offset(w - padR, padT + chartH * 0.5f),
                strokeWidth = 2f,
            )
            return@Canvas
        }

        val maxLoss = (lossPoints.maxOrNull() ?: 1f).coerceAtLeast(0.01f)
        val minLoss = (lossPoints.minOrNull() ?: 0f)
        val range = (maxLoss - minLoss).coerceAtLeast(0.01f)

        val path = Path()
        lossPoints.forEachIndexed { index, loss ->
            val x = padL + chartW * index / (lossPoints.size - 1).coerceAtLeast(1)
            val norm = (loss - minLoss) / range
            val y = padT + chartH * (1f - norm)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = LossCurveColor,
            style = Stroke(
                width = 3.5f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )

        // Latest point marker
        val last = lossPoints.last()
        val lastX = padL + chartW
        val lastY = padT + chartH * (1f - (last - minLoss) / range)
        drawCircle(LossCurveColor, radius = 6f, center = Offset(lastX, lastY))
    }
}
