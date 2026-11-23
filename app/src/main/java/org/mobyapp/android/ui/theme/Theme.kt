package org.mobyapp.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/*
 * The app's colors, shared with the iOS app so both look alike. See
 * specifications.md section 15.
 *
 * - Actions, links and icons are very light gray in dark mode and dark gray in
 *   light mode. They are the color scheme's primary color, so text buttons,
 *   focused fields and icons use them by default.
 * - The brand blue is for filled buttons, the floating buttons and switches.
 * - Delete is the error red.
 */

private val ActionDark = Color(0xFFE3E3E3)
private val ActionLight = Color(0xFF1F1F1F)
private val BrandDark = Color(0xFFA8C8FF)
private val BrandLight = Color(0xFF1E5AA8)
private val OnBrandDark = Color(0xFF0A2A55)
private val OnBrandLight = Color.White

// iOS's system backgrounds, so screens and cards match.
private val BackgroundDark = Color.Black
private val CardDark = Color(0xFF1C1C1E)
private val CardHighDark = Color(0xFF2C2C2E)
private val BackgroundLight = Color(0xFFF2F2F7)
private val CardLight = Color.White
private val CardHighLight = Color(0xFFE5E5EA)

private val DarkColors = darkColorScheme(
    primary = ActionDark,
    onPrimary = Color.Black,
    primaryContainer = CardHighDark,
    onPrimaryContainer = ActionDark,
    secondary = ActionDark,
    secondaryContainer = CardHighDark,
    onSecondaryContainer = ActionDark,
    background = BackgroundDark,
    onBackground = Color.White,
    surface = BackgroundDark,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFAEAEB2),
    surfaceContainerLowest = BackgroundDark,
    surfaceContainerLow = CardDark,
    surfaceContainer = CardDark,
    surfaceContainerHigh = CardDark,
    surfaceContainerHighest = CardDark,
    surfaceVariant = CardHighDark,
    outline = Color(0xFF636366),
    outlineVariant = Color(0xFF38383A),
    error = Color(0xFFFF453A),
)

private val LightColors = lightColorScheme(
    primary = ActionLight,
    onPrimary = Color.White,
    primaryContainer = CardHighLight,
    onPrimaryContainer = ActionLight,
    secondary = ActionLight,
    secondaryContainer = CardHighLight,
    onSecondaryContainer = ActionLight,
    background = BackgroundLight,
    onBackground = Color.Black,
    surface = BackgroundLight,
    onSurface = Color.Black,
    onSurfaceVariant = Color(0xFF6C6C70),
    surfaceContainerLowest = CardLight,
    surfaceContainerLow = CardLight,
    surfaceContainer = CardLight,
    surfaceContainerHigh = CardLight,
    surfaceContainerHighest = CardLight,
    surfaceVariant = CardHighLight,
    outline = Color(0xFFAEAEB2),
    outlineVariant = Color(0xFFD1D1D6),
    error = Color(0xFFFF3B30),
)

@Composable
fun MobyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}

/** The brand blue and the text on it. */
object MobyColors {
    val brand: Color
        @Composable @ReadOnlyComposable get() = if (isSystemInDarkTheme()) BrandDark else BrandLight
    val onBrand: Color
        @Composable @ReadOnlyComposable get() = if (isSystemInDarkTheme()) OnBrandDark else OnBrandLight
}

/** Switches in the brand blue with a white thumb, as on iOS. */
@Composable
fun mobySwitchColors(): SwitchColors = SwitchDefaults.colors(
    checkedTrackColor = MobyColors.brand,
    checkedThumbColor = Color.White,
    checkedBorderColor = MobyColors.brand,
    checkedIconColor = MobyColors.brand,
)

/** A filled, fully rounded button in the brand blue, such as Post and Save. */
@Composable
fun MobyButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MobyColors.brand,
            contentColor = MobyColors.onBrand,
            disabledContainerColor = MobyColors.brand.copy(alpha = 0.45f),
            disabledContentColor = MobyColors.onBrand.copy(alpha = 0.7f),
        ),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = modifier.heightIn(min = 44.dp),
        content = content,
    )
}

/** A floating, fully rounded button in the brand blue, such as New post. */
@Composable
fun MobyFloatingButton(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    text: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        icon = icon,
        text = text,
        shape = CircleShape,
        containerColor = MobyColors.brand,
        contentColor = MobyColors.onBrand,
        modifier = modifier,
    )
}
