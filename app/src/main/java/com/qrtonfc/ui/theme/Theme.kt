package com.qrtonfc.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.qrtonfc.flow.Stage

object Palette {
    val Bg = Color(0xFF0B0D12)
    val Surface = Color(0xFF151923)
    val Glass = Color(0x33FFFFFF)
    val GlassBorder = Color(0x26FFFFFF)
    val Text = Color(0xFFF1F5F9)
    val TextDim = Color(0xFF94A3B8)

    val Cyan = Color(0xFF22D3EE)
    val Violet = Color(0xFF8B5CF6)
    val Amber = Color(0xFFF59E0B)
    val Teal = Color(0xFF2DD4BF)
    val Mint = Color(0xFF34D399)
    val Coral = Color(0xFFFB7185)
}

fun accentFor(stage: Stage): Color = when (stage) {
    Stage.SCAN -> Palette.Cyan
    Stage.WRITE -> Palette.Violet
    Stage.LOCK -> Palette.Amber
    Stage.VERIFY -> Palette.Teal
    Stage.DONE -> Palette.Mint
    Stage.FAILED -> Palette.Coral
}

/** Second gradient color, a neighbour of the accent, for the animated background. */
fun secondaryFor(stage: Stage): Color = when (stage) {
    Stage.SCAN -> Palette.Violet
    Stage.WRITE -> Palette.Cyan
    Stage.LOCK -> Palette.Coral
    Stage.VERIFY -> Palette.Cyan
    Stage.DONE -> Palette.Teal
    Stage.FAILED -> Palette.Amber
}

private val Display = FontFamily.SansSerif

private val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = (-0.5).sp, lineHeight = 36.sp),
    titleMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
    bodyLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.2.sp),
    labelMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, letterSpacing = 1.2.sp),
)

@Composable
fun QrToNfcTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Cyan,
            secondary = Palette.Violet,
            background = Palette.Bg,
            surface = Palette.Surface,
            onSurface = Palette.Text,
            onBackground = Palette.Text,
        ),
        typography = AppTypography,
        content = content,
    )
}
