package com.qrtonfc.ui.hero

import android.graphics.Rect
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Slow-drifting aurora of two color blobs; colors are animated by the caller per stage. */
@Composable
fun AuroraBackground(accent: Color, secondary: Color, motion: Boolean, modifier: Modifier = Modifier) {
    val t by if (motion) {
        rememberInfiniteTransition(label = "aurora").animateFloat(
            0f, (2 * Math.PI).toFloat(),
            infiniteRepeatable(tween(18_000, easing = LinearEasing)), label = "t",
        )
    } else remember { mutableFloatStateOf(0f) }

    Canvas(modifier) {
        drawRect(Color(0xFF0B0D12))
        val r = size.maxDimension * 0.75f
        val a = Offset(size.width * (0.25f + 0.15f * cos(t)), size.height * (0.22f + 0.08f * sin(t)))
        val b = Offset(size.width * (0.8f + 0.1f * sin(t * 1.3f)), size.height * (0.78f + 0.08f * cos(t)))
        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.42f), Color.Transparent), a, r), r, a)
        drawCircle(Brush.radialGradient(listOf(secondary.copy(alpha = 0.30f), Color.Transparent), b, r * 0.9f), r * 0.9f, b)
        // Fine vignette keeps text legible at the edges.
        drawRect(
            Brush.verticalGradient(
                0f to Color(0x990B0D12), 0.25f to Color.Transparent, 0.65f to Color.Transparent, 1f to Color(0xE60B0D12),
            )
        )
    }
}

/**
 * Corner brackets over the camera. They breathe in the middle of the screen while searching,
 * and spring onto the QR code's bounds as soon as one is seen.
 */
@Composable
fun ScanFrame(box: Rect?, accent: Color, motion: Boolean, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { maxHeight.toPx() }
        val pad = with(density) { 14.dp.toPx() }
        val side = min(w, h) * 0.64f
        val cx = w / 2; val cy = h * 0.42f

        val target = box?.let { floatArrayOf(it.left - pad, it.top - pad, it.right + pad, it.bottom + pad) }
            ?: floatArrayOf(cx - side / 2, cy - side / 2, cx + side / 2, cy + side / 2)
        val spec = spring<Float>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
        val l by animateFloatAsState(target[0], spec, label = "l")
        val t by animateFloatAsState(target[1], spec, label = "t")
        val r by animateFloatAsState(target[2], spec, label = "r")
        val b by animateFloatAsState(target[3], spec, label = "b")

        val breathe by if (motion && box == null) {
            rememberInfiniteTransition(label = "breathe").animateFloat(
                0.96f, 1.04f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "s",
            )
        } else remember { mutableFloatStateOf(1f) }

        Canvas(Modifier.fillMaxSize()) {
            val mx = (l + r) / 2; val my = (t + b) / 2
            val hw = (r - l) / 2 * breathe; val hh = (b - t) / 2 * breathe
            drawBrackets(mx - hw, my - hh, mx + hw, my + hh, accent, box != null)
        }
    }
}

private fun DrawScope.drawBrackets(l: Float, t: Float, r: Float, b: Float, color: Color, locked: Boolean) {
    val len = min(r - l, b - t) * 0.22f
    val stroke = Stroke(width = (if (locked) 6 else 4).dp.toPx(), cap = StrokeCap.Round)
    val radius = 18.dp.toPx()
    val path = Path().apply {
        // top-left
        moveTo(l, t + len); lineTo(l, t + radius); quadraticTo(l, t, l + radius, t); lineTo(l + len, t)
        // top-right
        moveTo(r - len, t); lineTo(r - radius, t); quadraticTo(r, t, r, t + radius); lineTo(r, t + len)
        // bottom-right
        moveTo(r, b - len); lineTo(r, b - radius); quadraticTo(r, b, r - radius, b); lineTo(r - len, b)
        // bottom-left
        moveTo(l + len, b); lineTo(l + radius, b); quadraticTo(l, b, l, b - radius); lineTo(l, b - len)
    }
    if (locked) {
        drawRoundRect(
            color.copy(alpha = 0.12f), Offset(l, t), Size(r - l, b - t),
            androidx.compose.ui.geometry.CornerRadius(radius),
        )
    }
    drawPath(path, color.copy(alpha = 0.35f), style = Stroke(stroke.width * 3, cap = StrokeCap.Round))
    drawPath(path, color, style = stroke)
}
