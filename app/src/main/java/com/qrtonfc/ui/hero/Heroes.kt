package com.qrtonfc.ui.hero

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val HeroSize = 280.dp

@Composable
private fun loop(motion: Boolean, millis: Int, label: String): State<Float> =
    if (motion) {
        rememberInfiniteTransition(label = label).animateFloat(
            0f, 1f, infiniteRepeatable(tween(millis, easing = LinearEasing)), label = label,
        )
    } else remember { mutableFloatStateOf(0.35f) }

/**
 * A phone with NFC ripples pulsing from its antenna and a tag card gliding toward it.
 * For the verify step the card is drawn dashed, and while the tag is still on the phone
 * ([waitingForLift]) it drifts away instead, hinting "lift it first".
 */
@Composable
fun TapHero(accent: Color, icon: ImageVector, dashed: Boolean, waitingForLift: Boolean, motion: Boolean) {
    val t by loop(motion, 2200, "tap")
    val bob by if (motion) {
        rememberInfiniteTransition(label = "bob").animateFloat(
            0f, 1f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob",
        )
    } else remember { mutableFloatStateOf(0.5f) }

    Box(Modifier.size(HeroSize), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height * 0.42f)
            // Ripples
            for (i in 0 until 3) {
                val p = (t + i / 3f) % 1f
                drawCircle(
                    accent.copy(alpha = (1f - p) * 0.55f),
                    radius = 30.dp.toPx() + p * size.minDimension * 0.42f,
                    center = c, style = Stroke(2.dp.toPx()),
                )
            }
            // Phone body
            val pw = 118.dp.toPx(); val ph = 214.dp.toPx()
            val topLeft = Offset(size.width / 2 - pw / 2, size.height / 2 - ph / 2)
            drawRoundRect(
                Brush.verticalGradient(listOf(Color(0xFF1E2433), Color(0xFF121621)), topLeft.y, topLeft.y + ph),
                topLeft, Size(pw, ph), CornerRadius(26.dp.toPx()),
            )
            drawRoundRect(accent.copy(alpha = 0.7f), topLeft, Size(pw, ph), CornerRadius(26.dp.toPx()), style = Stroke(2.dp.toPx()))
            // Camera bump
            drawRoundRect(Color(0xFF2A3142), topLeft + Offset(12.dp.toPx(), 12.dp.toPx()), Size(30.dp.toPx(), 30.dp.toPx()), CornerRadius(9.dp.toPx()))
            // Antenna glow
            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.55f), Color.Transparent), c, 46.dp.toPx()), 46.dp.toPx(), c)

            // Tag card
            val travel = if (waitingForLift) 26.dp.toPx() + bob * 28.dp.toPx() else -bob * 46.dp.toPx()
            val tw = 96.dp.toPx(); val th = 62.dp.toPx()
            val tagTopLeft = Offset(size.width / 2 + 18.dp.toPx() - tw / 2, size.height * 0.72f + travel - th / 2)
            rotate(-10f, tagTopLeft + Offset(tw / 2, th / 2)) {
                drawRoundRect(Color(0xF0F8FAFC).copy(alpha = if (dashed) 0.10f else 0.92f), tagTopLeft, Size(tw, th), CornerRadius(12.dp.toPx()))
                drawRoundRect(
                    if (dashed) accent else accent.copy(alpha = 0.0f), tagTopLeft, Size(tw, th), CornerRadius(12.dp.toPx()),
                    style = Stroke(2.dp.toPx(), pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(14f, 10f), t * 48f) else null),
                )
                // Tiny NFC coil on the card
                val coil = tagTopLeft + Offset(tw - 26.dp.toPx(), th / 2)
                for (k in 1..3) {
                    drawCircle(
                        (if (dashed) accent else Color(0xFF475569)).copy(alpha = 0.8f),
                        radius = k * 5.dp.toPx(), center = coil, style = Stroke(1.5.dp.toPx()),
                    )
                }
            }
        }
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(34.dp).offset(y = (-28).dp))
    }
}

/** Spinning gradient arc around a pulsing icon, for the short write/verify moments. */
@Composable
fun BusyHero(accent: Color, icon: ImageVector, motion: Boolean) {
    val t by loop(motion, 900, "busy")
    Box(Modifier.size(HeroSize), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension * 0.34f
            drawCircle(accent.copy(alpha = 0.12f), r)
            drawCircle(accent.copy(alpha = 0.25f), r, style = Stroke(2.dp.toPx()))
            rotate(t * 360f) {
                drawArc(
                    Brush.sweepGradient(listOf(Color.Transparent, accent, Color.White)),
                    startAngle = 0f, sweepAngle = 300f, useCenter = false,
                    topLeft = center - Offset(r, r), size = Size(r * 2, r * 2),
                    style = Stroke(8.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
        val pulse = 1f + 0.08f * sin(t * 2 * PI).toFloat()
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(54.dp).graphicsLayer { scaleX = pulse; scaleY = pulse })
    }
}

/** Padlock whose shackle drops shut with a bounce, plus a shock-wave ring. */
@Composable
fun LockHero(accent: Color) {
    val drop = remember { Animatable(1f) }
    val ring = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        drop.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium))
    }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(140)
        ring.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
    }
    Canvas(Modifier.size(HeroSize)) {
        val c = center
        drawCircle(accent.copy(alpha = (1f - ring.value) * 0.6f), radius = 60.dp.toPx() + ring.value * 80.dp.toPx(), style = Stroke(3.dp.toPx()))
        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.35f), Color.Transparent), c, 120.dp.toPx()))

        val bw = 104.dp.toPx(); val bh = 84.dp.toPx()
        val bodyTop = c.y - bh / 2 + 22.dp.toPx()
        // Shackle
        val sw = 64.dp.toPx(); val sh = 58.dp.toPx()
        translate(top = -drop.value * 26.dp.toPx()) {
            val p = Path().apply {
                moveTo(c.x - sw / 2, bodyTop + 6.dp.toPx())
                lineTo(c.x - sw / 2, bodyTop - sh + sw / 2)
                arcTo(androidx.compose.ui.geometry.Rect(c.x - sw / 2, bodyTop - sh, c.x + sw / 2, bodyTop - sh + sw), 180f, 180f, false)
                lineTo(c.x + sw / 2, bodyTop + 6.dp.toPx())
            }
            drawPath(p, Color(0xFFE2E8F0), style = Stroke(13.dp.toPx(), cap = StrokeCap.Round))
        }
        // Body
        drawRoundRect(
            Brush.verticalGradient(listOf(accent, accent.copy(red = accent.red * 0.7f, green = accent.green * 0.6f)), bodyTop, bodyTop + bh),
            Offset(c.x - bw / 2, bodyTop), Size(bw, bh), CornerRadius(20.dp.toPx()),
        )
        // Keyhole
        drawCircle(Color(0xFF0B0D12), 9.dp.toPx(), Offset(c.x, bodyTop + bh * 0.42f))
        drawRoundRect(Color(0xFF0B0D12), Offset(c.x - 4.dp.toPx(), bodyTop + bh * 0.45f), Size(8.dp.toPx(), 20.dp.toPx()), CornerRadius(4.dp.toPx()))
    }
}

/** Check mark that draws itself inside a filling disc, with a particle burst. */
@Composable
fun SuccessHero(accent: Color) {
    val disc = remember { Animatable(0f) }
    val check = remember { Animatable(0f) }
    val burst = remember { Animatable(0f) }
    LaunchedEffect(Unit) { disc.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(120); check.animateTo(1f, tween(420, easing = FastOutSlowInEasing)) }
    LaunchedEffect(Unit) { burst.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }

    val particles = remember {
        List(28) { i ->
            val angle = i / 28f * 2 * PI.toFloat() + (i % 3) * 0.11f
            Triple(angle, 0.7f + (i * 37 % 10) / 20f, if (i % 2 == 0) 4f else 2.5f)
        }
    }
    Canvas(Modifier.size(HeroSize)) {
        val r = size.minDimension * 0.28f
        particles.forEachIndexed { i, (angle, speed, dot) ->
            val d = r + burst.value * size.minDimension * 0.24f * speed
            val color = if (i % 3 == 0) Color.White else accent
            drawCircle(
                color.copy(alpha = (1f - burst.value).coerceIn(0f, 1f)),
                dot.dp.toPx() * (1.2f - burst.value * 0.5f),
                center + Offset(cos(angle) * d, sin(angle) * d),
            )
        }
        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.4f), Color.Transparent), center, r * 2f), r * 2f)
        drawCircle(accent, r * disc.value)

        val path = Path().apply {
            moveTo(center.x - r * 0.42f, center.y + r * 0.02f)
            lineTo(center.x - r * 0.1f, center.y + r * 0.34f)
            lineTo(center.x + r * 0.46f, center.y - r * 0.3f)
        }
        val measure = PathMeasure().apply { setPath(path, false) }
        val partial = Path()
        measure.getSegment(0f, measure.length * check.value, partial, true)
        drawPath(partial, Color(0xFF052E1C), style = Stroke(11.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** Cross in a coral disc that shakes once on entry. */
@Composable
fun FailHero(accent: Color) {
    val shake = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        shake.animateTo(0f, keyframes {
            durationMillis = 480
            -18f at 60; 16f at 140; -12f at 220; 8f at 300; -4f at 380
        })
    }
    Canvas(Modifier.size(HeroSize).graphicsLayer { translationX = shake.value * density }) {
        val r = size.minDimension * 0.28f
        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.35f), Color.Transparent), center, r * 2f), r * 2f)
        drawCircle(accent.copy(alpha = 0.18f), r)
        drawCircle(accent, r, style = Stroke(4.dp.toPx()))
        val k = r * 0.36f
        val s = Stroke(10.dp.toPx(), cap = StrokeCap.Round)
        drawLine(accent, center + Offset(-k, -k), center + Offset(k, k), s.width, StrokeCap.Round)
        drawLine(accent, center + Offset(k, -k), center + Offset(-k, k), s.width, StrokeCap.Round)
    }
}
