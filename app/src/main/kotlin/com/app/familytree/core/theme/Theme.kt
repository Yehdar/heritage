package com.app.familytree.core.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Beige = Color(0xFFF4EBE1)
val Espresso = Color(0xFF2C221E)
val WarmGrey = Color(0xFF8C8077)
val SoftBrown = Color(0xFF5C4E43)
private val palette = lightColorScheme(primary = Espresso, onPrimary = Color.White, secondary = SoftBrown, background = Beige, surface = Beige, surfaceContainer = Color.White, onBackground = Espresso, onSurface = Espresso, onSurfaceVariant = WarmGrey, outline = WarmGrey)
private val typography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 34.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontSize = 28.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 11.sp, letterSpacing = 1.5.sp)
)
@Composable fun HeritageTheme(content: @Composable () -> Unit) { MaterialTheme(colorScheme = palette, typography = typography, content = content) }
