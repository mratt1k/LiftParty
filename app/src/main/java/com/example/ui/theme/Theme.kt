package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LiftPartyDarkColorScheme = darkColorScheme(
    primary = CoralPrimary,
    onPrimary = CoralOnPrimary,
    primaryContainer = CoralContainer,
    onPrimaryContainer = CoralOnContainer,
    secondary = CyanSecondary,
    onSecondary = CyanOnSecondary,
    secondaryContainer = CyanContainer,
    onSecondaryContainer = CyanOnContainer,
    tertiary = GoldTertiary,
    onTertiary = GoldOnTertiary,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = LoungeSurface,
    onSurface = TextPrimary,
    surfaceVariant = LoungeSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = LoungeSurface,
    surfaceContainer = LoungeSurfaceElevated,
    surfaceContainerHigh = LoungeSurfaceHighest,
    outline = OutlineSubtle,
    error = ErrorCoral,
)

private val LiftPartyShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun LiftPartyTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = LiftPartyDarkColorScheme,
        typography = LiftPartyTypography,
        shapes = LiftPartyShapes,
        content = content,
    )
}
