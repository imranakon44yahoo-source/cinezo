package com.pashe.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pashe.app.R

object PasheColors {
    val Teal = Color(0xFF00796B)
    val TealDark = Color(0xFF004D40)
    val TealLight = Color(0xFFB2DFDB)
    val Mint = Color(0xFFE0F2EF)
    val Amber = Color(0xFFFFB300)
    val Background = Color(0xFFF6FBF9)
    val Surface = Color(0xFFFFFFFF)
    val Ink = Color(0xFF1B2B28)
    val InkMuted = Color(0xFF4F615D)

    val Taken = Color(0xFF2E7D32)
    val OnTaken = Color(0xFFFFFFFF)
    val TakenBg = Color(0xFFE8F5E9)
    val Pending = Color(0xFF8D6E00)
    val PendingBg = Color(0xFFFFF8E1)
    val Missed = Color(0xFFC62828)
    val MissedBg = Color(0xFFFFEBEE)

    val AlarmBackground = Color(0xFF004D40)
    val OnAlarm = Color(0xFFFFFFFF)
}

val HindSiliguri = FontFamily(
    Font(R.font.hind_siliguri_regular, FontWeight.Normal),
    Font(R.font.hind_siliguri_medium, FontWeight.Medium),
    Font(R.font.hind_siliguri_semibold, FontWeight.SemiBold),
    Font(R.font.hind_siliguri_bold, FontWeight.Bold),
)

private val Base = Typography()

private fun TextStyle.hind() = copy(fontFamily = HindSiliguri)

private val PasheTypography = Typography(
    displayLarge = Base.displayLarge.hind(), displayMedium = Base.displayMedium.hind(), displaySmall = Base.displaySmall.hind(),
    headlineLarge = Base.headlineLarge.hind(), headlineMedium = Base.headlineMedium.hind(), headlineSmall = Base.headlineSmall.hind(),
    titleLarge = Base.titleLarge.hind().copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Base.titleMedium.hind().copy(fontWeight = FontWeight.SemiBold),
    titleSmall = Base.titleSmall.hind(),
    bodyLarge = Base.bodyLarge.hind(), bodyMedium = Base.bodyMedium.hind(), bodySmall = Base.bodySmall.hind(),
    labelLarge = Base.labelLarge.hind().copy(fontWeight = FontWeight.SemiBold),
    labelMedium = Base.labelMedium.hind(), labelSmall = Base.labelSmall.hind(),
)

private val PasheColorScheme = lightColorScheme(
    primary = PasheColors.Teal,
    onPrimary = Color.White,
    primaryContainer = PasheColors.TealLight,
    onPrimaryContainer = PasheColors.TealDark,
    secondary = PasheColors.Taken,
    onSecondary = Color.White,
    secondaryContainer = PasheColors.Mint,
    onSecondaryContainer = PasheColors.TealDark,
    tertiary = PasheColors.Amber,
    background = PasheColors.Background,
    onBackground = PasheColors.Ink,
    surface = PasheColors.Surface,
    onSurface = PasheColors.Ink,
    surfaceVariant = PasheColors.Mint,
    onSurfaceVariant = PasheColors.InkMuted,
    error = PasheColors.Missed,
)

private val PasheShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
)

/** Always light: high contrast matters more than dark mode for elderly users. */
@Composable
fun PasheTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = PasheColorScheme, typography = PasheTypography, shapes = PasheShapes, content = content)
}
