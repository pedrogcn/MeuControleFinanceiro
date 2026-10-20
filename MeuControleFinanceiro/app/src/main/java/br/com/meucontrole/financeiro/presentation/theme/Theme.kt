package br.com.meucontrole.financeiro.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import br.com.meucontrole.financeiro.domain.model.ThemeMode

private val Light = lightColorScheme(
    primary = Color(0xFF0B6B53),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA6F2D3),
    onPrimaryContainer = Color(0xFF002117),
    secondary = Color(0xFF4B635A),
    secondaryContainer = Color(0xFFCDE9DC),
    onSecondaryContainer = Color(0xFF072019),
    tertiary = Color(0xFF3D6373),
    tertiaryContainer = Color(0xFFC1E8FB),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    background = Color(0xFFF7FAF7),
    surface = Color(0xFFF7FAF7),
    surfaceVariant = Color(0xFFDBE5DE),
    outline = Color(0xFF6F7973)
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8AD6B8),
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF00513E),
    onPrimaryContainer = Color(0xFFA6F2D3),
    secondary = Color(0xFFB2CCC1),
    secondaryContainer = Color(0xFF334B43),
    onSecondaryContainer = Color(0xFFCDE9DC),
    tertiary = Color(0xFFA5CCDF),
    tertiaryContainer = Color(0xFF244C5B),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    background = Color(0xFF0F1512),
    surface = Color(0xFF0F1512),
    surfaceVariant = Color(0xFF3F4944),
    outline = Color(0xFF89938D)
)

/** Cores semânticas para valores financeiros (entradas, saídas, alertas). */
@Immutable
data class FinanceColors(val income: Color, val expense: Color, val warning: Color, val neutral: Color)

val LocalFinanceColors = staticCompositionLocalOf {
    FinanceColors(Color(0xFF1B7F4B), Color(0xFFB3261E), Color(0xFF9A6700), Color(0xFF5F6B66))
}

@Composable
fun MeuControleTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SISTEMA -> isSystemInDarkTheme()
        ThemeMode.CLARO -> false
        ThemeMode.ESCURO -> true
    }
    val fin = if (dark) FinanceColors(Color(0xFF7FD8A0), Color(0xFFFFB4AB), Color(0xFFF2C14E), Color(0xFFB0BCB6))
    else FinanceColors(Color(0xFF1B7F4B), Color(0xFFB3261E), Color(0xFF8A5A00), Color(0xFF5F6B66))
    androidx.compose.runtime.CompositionLocalProvider(LocalFinanceColors provides fin) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
    }
}
