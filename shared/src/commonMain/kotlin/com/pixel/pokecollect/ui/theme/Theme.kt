package com.pixel.pokecollect.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itdeveapps.neubrutalism.theme.neuBorder
import com.itdeveapps.neubrutalism.theme.neuShadow
import org.jetbrains.compose.resources.Font
import pokecollect.shared.generated.resources.Res
import pokecollect.shared.generated.resources.jetbrains_mono_bold
import pokecollect.shared.generated.resources.jetbrains_mono_medium
import pokecollect.shared.generated.resources.space_grotesk_bold
import pokecollect.shared.generated.resources.space_grotesk_medium
import pokecollect.shared.generated.resources.space_grotesk_regular

object PokeColors {
    val Background = Color(0xFFFFFDF2)
    val Surface = Color(0xFFFFFFFF)
    val Ink = Color(0xFF000000)
    val OnSurfaceVariant = Color(0xFF4A4842)
    val Primary = Color(0xFFF4D738)
    val Secondary = Color(0xFF2563EB)
    val Tertiary = Color(0xFF22C55E)
    val Error = Color(0xFFEF4444)
    val Caution = Color(0xFFFF9F5A)
    val Highlight = Color(0xFFFF8FB8)
}

private val LightColors = lightColorScheme(
    background = PokeColors.Background,
    surface = PokeColors.Surface,
    onBackground = PokeColors.Ink,
    onSurface = PokeColors.Ink,
    onSurfaceVariant = PokeColors.OnSurfaceVariant,
    outline = PokeColors.Ink,
    primary = PokeColors.Primary,
    onPrimary = PokeColors.Ink,
    secondary = PokeColors.Secondary,
    onSecondary = Color.White,
    tertiary = PokeColors.Tertiary,
    onTertiary = PokeColors.Ink,
    error = PokeColors.Error,
)

/** Saturated fills tuned so black text clears 4.5:1 on every type. */
private val TypeFills = mapOf(
    "normal" to Color(0xFFDCD0B3),
    "fire" to Color(0xFFFE9052),
    "water" to Color(0xFF63B9FE),
    "electric" to Color(0xFFFFD82B),
    "grass" to Color(0xFF6FEA77),
    "ice" to Color(0xFF7AEBFE),
    "fighting" to Color(0xFFFD7466),
    "poison" to Color(0xFFD784F0),
    "ground" to Color(0xFFF0AF5F),
    "flying" to Color(0xFF9BBEFD),
    "psychic" to Color(0xFFFF82B1),
    "bug" to Color(0xFFBCDB3A),
    "rock" to Color(0xFFCAAD71),
    "ghost" to Color(0xFFB08FF2),
    "dragon" to Color(0xFF8992FD),
    "dark" to Color(0xFF9F867D),
    "steel" to Color(0xFFACD1E4),
    "fairy" to Color(0xFFFEACE3),
)

fun typeFill(type: String): Color = TypeFills[type] ?: TypeFills.getValue("normal")

object PokeShapes {
    val Tag = RoundedCornerShape(6.dp)
    val Small = RoundedCornerShape(9.dp)
    val Medium = RoundedCornerShape(12.dp)
    val Large = RoundedCornerShape(14.dp)
    val Fab = RoundedCornerShape(22.dp)
}

/** Neubrutalist box: solid offset shadow (no blur), fill, ink border. Pass offset = 0.dp for "pressed". */
fun Modifier.brutal(
    fill: Color,
    shape: Shape,
    border: Dp = 2.dp,
    offset: Dp = 3.dp,
): Modifier = this
    .then(if (offset > 0.dp) Modifier.neuShadow(shape, offset, offset, PokeColors.Ink) else Modifier)
    .background(fill, shape)
    .neuBorder(border, PokeColors.Ink, shape)

// Fonts are bundled here rather than taken from the neubrutalism library: JitPack's rewritten
// module metadata points the library's iOS resources at a URL that 404s, so they never reach the
// app and loading them crashes.
@Composable
fun spaceGroteskFontFamily(): FontFamily = FontFamily(
    Font(Res.font.space_grotesk_regular, FontWeight.Normal),
    Font(Res.font.space_grotesk_medium, FontWeight.Medium),
    Font(Res.font.space_grotesk_bold, FontWeight.Bold),
)

@Composable
fun monoFontFamily(): FontFamily = FontFamily(
    Font(Res.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(Res.font.jetbrains_mono_bold, FontWeight.Bold),
)

@Composable
private fun pokeTypography(): Typography {
    val sg = spaceGroteskFontFamily()
    fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) =
        TextStyle(fontFamily = sg, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = tracking.sp)
    return Typography(
        displaySmall = style(46, 52, FontWeight.Bold, -1.5),
        headlineMedium = style(32, 40, FontWeight.Bold, -0.6),
        headlineSmall = style(30, 36, FontWeight.Bold, -0.6),
        titleLarge = style(28, 34, FontWeight.Bold, -0.6),
        titleMedium = style(26, 32, FontWeight.Bold, -0.5),
        titleSmall = style(19, 24, FontWeight.Bold),
        bodyLarge = style(18, 26, FontWeight.Medium),
        bodyMedium = style(16, 22, FontWeight.Normal),
        labelLarge = style(20, 24, FontWeight.Bold),
        labelMedium = style(14, 18, FontWeight.Bold, 0.6),
        labelSmall = style(13, 16, FontWeight.Bold, 0.4),
    )
}

@Composable
fun PokeCollectTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, typography = pokeTypography(), content = content)
}
