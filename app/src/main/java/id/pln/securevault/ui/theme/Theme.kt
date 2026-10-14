package id.pln.securevault.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// ---- Palet cerah ----
val Violet = Color(0xFF6C4CF1)
val Purple = Color(0xFFA64CF1)
val Pink = Color(0xFFFF5C8A)
val Coral = Color(0xFFFF7A59)
val Teal = Color(0xFF00BFA6)
val Sky = Color(0xFF2FA8FF)
val Sun = Color(0xFFFFB020)
val Ink = Color(0xFF1F1A2E)

private val LightColors = lightColorScheme(
    primary = Violet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9E3FF),
    onPrimaryContainer = Color(0xFF22005D),
    secondary = Pink,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE0EA),
    onSecondaryContainer = Color(0xFF5C0026),
    tertiary = Teal,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCCF7F0),
    onTertiaryContainer = Color(0xFF003730),
    error = Color(0xFFE5484D),
    errorContainer = Color(0xFFFFE1E1),
    background = Color(0xFFF6F4FF),
    onBackground = Ink,
    surface = Color(0xFFF6F4FF),
    onSurface = Ink,
    surfaceVariant = Color(0xFFECE8FA),
    onSurfaceVariant = Color(0xFF5B5670),
    outline = Color(0xFFB9B3CE),
    outlineVariant = Color(0xFFDCD7EC),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color.White
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB7A6FF),
    onPrimary = Color(0xFF2B0A7A),
    primaryContainer = Color(0xFF4B2FC4),
    onPrimaryContainer = Color(0xFFE9E3FF),
    secondary = Color(0xFFFF8FB0),
    onSecondary = Color(0xFF5C0026),
    secondaryContainer = Color(0xFF8A1E48),
    onSecondaryContainer = Color(0xFFFFE0EA),
    tertiary = Color(0xFF4FE3CC),
    onTertiary = Color(0xFF003730),
    tertiaryContainer = Color(0xFF00574C),
    onTertiaryContainer = Color(0xFFCCF7F0),
    error = Color(0xFFFF8A8E),
    background = Color(0xFF141221),
    onBackground = Color(0xFFEAE6F7),
    surface = Color(0xFF141221),
    onSurface = Color(0xFFEAE6F7),
    surfaceVariant = Color(0xFF2A2640),
    onSurfaceVariant = Color(0xFFC9C3DD),
    outline = Color(0xFF6F6A85),
    outlineVariant = Color(0xFF3A3552),
    surfaceContainerLowest = Color(0xFF1B1830),
    surfaceContainerLow = Color(0xFF1E1B33),
    surfaceContainer = Color(0xFF221E38),
    surfaceContainerHigh = Color(0xFF262240),
    surfaceContainerHighest = Color(0xFF2A2645)
)

private val base = Typography()
private val AppTypography = base.copy(
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun SecureVaultTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
