package com.example.delayme.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// Cozy Journal Palette
val CreamBackground = Color(0xFFFDFBF7) // Warm paper background
val MatchaGreen = Color(0xFFA8D8B9) // Soft Green (Focus)
val MutedPink = Color(0xFFE8A0BF) // Soft Pink (Distraction)
val BabyBlue = Color(0xFFAECBEB) // Soft Blue (Life)
val CreamyYellow = Color(0xFFFDF4C1) // Soft Yellow (Rest/Highlight)
val CharcoalGrey = Color(0xFF333333) // Soft Charcoal Grey (Text)
val WarmBrown = Color(0xFF5D4037) // Warm Brown (Alternative Text)

val NecessaryColor = MatchaGreen
val FragmentedColor = MutedPink
val LifeColor = BabyBlue
val RestColor = Color(0xFFE0E0E0) // Softer Grey for Rest

private val DarkColorScheme = darkColorScheme(
    primary = MatchaGreen,
    secondary = BabyBlue,
    tertiary = MutedPink,
    background = Color(0xFF2C2C2C), // Warm dark grey
    surface = Color(0xFF3E3E3E), // Slightly lighter warm grey
    onBackground = CreamBackground,
    onSurface = CreamBackground
)

private val LightColorScheme = lightColorScheme(
    primary = MatchaGreen,
    secondary = BabyBlue,
    tertiary = MutedPink,
    background = CreamBackground,
    surface = Color.White,
    onBackground = CharcoalGrey,
    onSurface = CharcoalGrey
)

@Composable
fun DelayMeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
