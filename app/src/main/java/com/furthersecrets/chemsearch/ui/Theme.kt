package com.furthersecrets.chemsearch.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.furthersecrets.chemsearch.data.AppColorScheme

private fun darkColors(accent: AppColorScheme) = when (accent) {
    AppColorScheme.BLUE -> darkColorScheme(
    primary           = Color(0xFF3B82F6),
    onPrimary         = Color.White,
    primaryContainer  = Color(0xFF1D4ED8),
    background        = Color(0xFF0F172A),
    surface           = Color(0xFF1E293B),
    surfaceVariant    = Color(0xFF1E293B),
    onBackground      = Color(0xFFF1F5F9),
    onSurface         = Color(0xFFF1F5F9),
    onSurfaceVariant  = Color(0xFFCBD5E1),
    outline           = Color(0xFF334155),
    error             = Color(0xFFEF4444)
    )
    AppColorScheme.VIOLET -> darkColorScheme(
        primary = Color(0xFFA78BFA),
        onPrimary = Color(0xFF1F1147),
        primaryContainer = Color(0xFF6D28D9),
        background = Color(0xFF151225),
        surface = Color(0xFF211A35),
        surfaceVariant = Color(0xFF2D2545),
        onBackground = Color(0xFFF4F0FF),
        onSurface = Color(0xFFF4F0FF),
        onSurfaceVariant = Color(0xFFD8CFF5),
        outline = Color(0xFF4A3F67),
        error = Color(0xFFEF4444)
    )
    AppColorScheme.EMERALD -> darkColorScheme(
        primary = Color(0xFF34D399),
        onPrimary = Color(0xFF052E22),
        primaryContainer = Color(0xFF047857),
        background = Color(0xFF0D1714),
        surface = Color(0xFF17211F),
        surfaceVariant = Color(0xFF21312C),
        onBackground = Color(0xFFEAF7F2),
        onSurface = Color(0xFFEAF7F2),
        onSurfaceVariant = Color(0xFFC3D8D0),
        outline = Color(0xFF39554B),
        error = Color(0xFFEF4444)
    )
    AppColorScheme.ROSE -> darkColorScheme(
        primary = Color(0xFFFB7185),
        onPrimary = Color(0xFF4C0519),
        primaryContainer = Color(0xFFBE123C),
        background = Color(0xFF1D1017),
        surface = Color(0xFF2A1720),
        surfaceVariant = Color(0xFF3A202B),
        onBackground = Color(0xFFFFF1F4),
        onSurface = Color(0xFFFFF1F4),
        onSurfaceVariant = Color(0xFFF6C7D2),
        outline = Color(0xFF593341),
        error = Color(0xFFEF4444)
    )
    AppColorScheme.AMBER -> darkColorScheme(
        primary = Color(0xFFFBBF24),
        onPrimary = Color(0xFF3A2600),
        primaryContainer = Color(0xFFB45309),
        background = Color(0xFF18130B),
        surface = Color(0xFF241C10),
        surfaceVariant = Color(0xFF342817),
        onBackground = Color(0xFFFFF7E6),
        onSurface = Color(0xFFFFF7E6),
        onSurfaceVariant = Color(0xFFE9D6B8),
        outline = Color(0xFF56442A),
        error = Color(0xFFEF4444)
    )
}

private fun lightColors(accent: AppColorScheme) = when (accent) {
    AppColorScheme.BLUE -> lightColorScheme(
    primary           = Color(0xFF2563EB),
    onPrimary         = Color.White,
    primaryContainer  = Color(0xFFDBEAFE),
    background        = Color(0xFFF8FAFC),
    surface           = Color.White,
    surfaceVariant    = Color(0xFFF1F5F9),
    onBackground      = Color(0xFF0F172A),
    onSurface         = Color(0xFF0F172A),
    onSurfaceVariant  = Color(0xFF475569),
    outline           = Color(0xFFE2E8F0),
    error             = Color(0xFFDC2626)
    )
    AppColorScheme.VIOLET -> lightColorScheme(
        primary = Color(0xFF6D28D9),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFEDE9FE),
        background = Color(0xFFFBFAFF),
        surface = Color.White,
        surfaceVariant = Color(0xFFF4F1FF),
        onBackground = Color(0xFF1F1930),
        onSurface = Color(0xFF1F1930),
        onSurfaceVariant = Color(0xFF625A77),
        outline = Color(0xFFDCD4F0),
        error = Color(0xFFDC2626)
    )
    AppColorScheme.EMERALD -> lightColorScheme(
        primary = Color(0xFF047857),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD1FAE5),
        background = Color(0xFFF7FCFA),
        surface = Color.White,
        surfaceVariant = Color(0xFFEAF7F2),
        onBackground = Color(0xFF10201B),
        onSurface = Color(0xFF10201B),
        onSurfaceVariant = Color(0xFF4B665D),
        outline = Color(0xFFCBE0D7),
        error = Color(0xFFDC2626)
    )
    AppColorScheme.ROSE -> lightColorScheme(
        primary = Color(0xFFE11D48),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFE4E6),
        background = Color(0xFFFFFAFB),
        surface = Color.White,
        surfaceVariant = Color(0xFFFFF1F4),
        onBackground = Color(0xFF2A1218),
        onSurface = Color(0xFF2A1218),
        onSurfaceVariant = Color(0xFF76505A),
        outline = Color(0xFFF1CFD7),
        error = Color(0xFFDC2626)
    )
    AppColorScheme.AMBER -> lightColorScheme(
        primary = Color(0xFFB45309),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFEF3C7),
        background = Color(0xFFFFFBF2),
        surface = Color.White,
        surfaceVariant = Color(0xFFFFF5D8),
        onBackground = Color(0xFF241709),
        onSurface = Color(0xFF241709),
        onSurfaceVariant = Color(0xFF715B38),
        outline = Color(0xFFEBD7AC),
        error = Color(0xFFDC2626)
    )
}

@Composable
fun ChemSearchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorScheme: AppColorScheme = AppColorScheme.BLUE,
    oledDarkTheme: Boolean = false,
    highContrastOutlines: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = chemSearchColorScheme(
            darkTheme = darkTheme,
            colorScheme = colorScheme,
            oledDarkTheme = oledDarkTheme,
            highContrastOutlines = highContrastOutlines
        ),
        content = content
    )
}

internal fun chemSearchColorScheme(
    darkTheme: Boolean,
    colorScheme: AppColorScheme,
    oledDarkTheme: Boolean,
    highContrastOutlines: Boolean = false
): ColorScheme {
    val colors = if (darkTheme) darkColors(colorScheme) else lightColors(colorScheme)
    val adjusted = if (darkTheme && oledDarkTheme) colors.withOledDarkSurfaces() else colors
    return if (highContrastOutlines) adjusted.withHighContrastOutlines(darkTheme) else adjusted
}

private fun ColorScheme.withOledDarkSurfaces(): ColorScheme {
    val black = Color.Black
    val low = Color(0xFF0A0A0A)
    val raised = Color(0xFF141414)
    val variant = Color(0xFF1A1A1A)
    val high = Color(0xFF222222)
    return copy(
        background = black,
        surface = black,
        surfaceVariant = variant,
        surfaceDim = black,
        surfaceBright = high,
        surfaceContainerLowest = black,
        surfaceContainerLow = low,
        surfaceContainer = raised,
        surfaceContainerHigh = variant,
        surfaceContainerHighest = high,
        inverseSurface = Color(0xFFE5E7EB),
        outline = outline.copy(alpha = 0.9f),
        outlineVariant = Color(0xFF333333)
    )
}

private fun ColorScheme.withHighContrastOutlines(darkTheme: Boolean): ColorScheme =
    copy(
        outline = if (darkTheme) onSurface.copy(alpha = 0.72f) else primary.copy(alpha = 0.62f),
        outlineVariant = if (darkTheme) onSurface.copy(alpha = 0.42f) else primary.copy(alpha = 0.30f)
    )
