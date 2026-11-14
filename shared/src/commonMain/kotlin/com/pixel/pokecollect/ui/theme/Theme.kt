package com.pixel.pokecollect.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
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
import com.pixel.pokecollect.settings.Appearance
import org.jetbrains.compose.resources.Font
import pokecollect.shared.generated.resources.Res
import pokecollect.shared.generated.resources.jetbrains_mono_bold
import pokecollect.shared.generated.resources.jetbrains_mono_medium
import pokecollect.shared.generated.resources.space_grotesk_bold
import pokecollect.shared.generated.resources.space_grotesk_medium
import pokecollect.shared.generated.resources.space_grotesk_regular

/**
 * Dark mode keeps the ink black: borders, offset shadows and the saturated fills look the same in
 * both themes. Only the page, the surfaces and the text on them change (warm charcoal, cream text).
 * The fixed colours are plain values; the themed ones read the palette [PokeCollectTheme] provides,
 * so they can only be read in composition.
 */
object PokeColors {
    /** Borders, offset shadows, and text and icons on saturated fills. Black in both themes. */
    val Ink = Color(0xFF000000)

    /** Secondary text on saturated fills (yellow, orange, type colours). */
    val InkVariant = Color(0xFF4A4842)
    val Primary = Color(0xFFF4D738)
    val Secondary = Color(0xFF2563EB)
    val Tertiary = Color(0xFF22C55E)
    val Error = Color(0xFFEF4444)
    val Caution = Color(0xFFFF9F5A)
    val Highlight = Color(0xFFFF8FB8)

    val Background: Color @Composable @ReadOnlyComposable get() = LocalPokePalette.current.background
    val Surface: Color @Composable @ReadOnlyComposable get() = LocalPokePalette.current.surface

    /** Text and icons on [Background] and [Surface]. */
    val OnSurface: Color @Composable @ReadOnlyComposable get() = LocalPokePalette.current.onSurface
    val OnSurfaceVariant: Color @Composable @ReadOnlyComposable get() = LocalPokePalette.current.onSurfaceVariant
}

@Immutable
private class PokePalette(
    val background: Color,
    val surface: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
)

private val LightPalette = PokePalette(
    background = Color(0xFFFFFDF2),
    surface = Color(0xFFFFFFFF),
    onSurface = PokeColors.Ink,
    onSurfaceVariant = PokeColors.InkVariant,
)

// Light enough that black borders and shadows still show (about 2:1 on the surface); cream text
// is ~10:1 on the surface, the muted text ~6:1.
private val DarkPalette = PokePalette(
    background = Color(0xFF34322B),
    surface = Color(0xFF46433A),
    onSurface = Color(0xFFFFFDF2),
    onSurfaceVariant = Color(0xFFCAC6BA),
)

private val LocalPokePalette = staticCompositionLocalOf { LightPalette }

/**
 * Text and icon colour for content drawn on [fill]: the theme's text colour on the page and on
 * surfaces, white on blue and black, and ink on every other saturated fill.
 */
@Composable
@ReadOnlyComposable
fun contentColorOn(fill: Color): Color = when (fill) {
    PokeColors.Background, PokeColors.Surface -> PokeColors.OnSurface
    PokeColors.Secondary, PokeColors.Ink -> Color.White
    else -> PokeColors.Ink
}

/** Secondary text on [fill]; see [contentColorOn]. */
@Composable
@ReadOnlyComposable
fun mutedColorOn(fill: Color): Color = when (fill) {
    PokeColors.Background, PokeColors.Surface -> PokeColors.OnSurfaceVariant
    else -> PokeColors.InkVariant
}

private fun materialColors(palette: PokePalette, dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        background = palette.background,
        surface = palette.surface,
        onBackground = palette.onSurface,
        onSurface = palette.onSurface,
        onSurfaceVariant = palette.onSurfaceVariant,
        outline = PokeColors.Ink,
        primary = PokeColors.Primary,
        onPrimary = PokeColors.Ink,
        secondary = PokeColors.Secondary,
        onSecondary = Color.White,
        tertiary = PokeColors.Tertiary,
        onTertiary = PokeColors.Ink,
        error = PokeColors.Error,
    )
}

private val LightColors = materialColors(LightPalette, dark = false)
private val DarkColors = materialColors(DarkPalette, dark = true)

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

/** Whether [appearance] means dark right now: the person's choice, or the phone's setting for System. */
@Composable
fun Appearance.isDark(): Boolean = when (this) {
    Appearance.System -> isSystemInDarkTheme()
    Appearance.Light -> false
    Appearance.Dark -> true
}

@Composable
fun PokeCollectTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val palette = if (dark) DarkPalette else LightPalette
    CompositionLocalProvider(LocalPokePalette provides palette) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = pokeTypography()) {
            // Text and icons without a colour of their own: the theme's text colour. Content on a
            // saturated fill sets its own (contentColorOn).
            CompositionLocalProvider(LocalContentColor provides palette.onSurface, content = content)
        }
    }
}
