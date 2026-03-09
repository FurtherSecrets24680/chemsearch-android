package com.furthersecrets.chemsearch.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
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

private val LightColors = lightColorScheme(
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

@Composable
fun ChemSearchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
