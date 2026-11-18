package com.malbiseo.ui.theme

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
import androidx.compose.ui.unit.sp

// 말비서 브랜드: 차분한 인디고 + 포인트 앰버
private val Light = lightColorScheme(
    primary = Color(0xFF4F46E5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E8FF),
    onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = Color(0xFF64748B),
    secondaryContainer = Color(0xFFEFF1F7),
    onSecondaryContainer = Color(0xFF1F2433),
    tertiary = Color(0xFFF59E0B),
    tertiaryContainer = Color(0xFFFFF1D6),
    onTertiaryContainer = Color(0xFF5A3A00),
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF161925),
    surface = Color.White,
    onSurface = Color(0xFF161925),
    surfaceVariant = Color(0xFFEEF0F6),
    onSurfaceVariant = Color(0xFF5B6172),
    outline = Color(0xFFC9CDD9),
    outlineVariant = Color(0xFFE6E8EF),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFAFD),
    surfaceContainer = Color(0xFFF3F4F9),
    surfaceContainerHigh = Color(0xFFEEEFF5),
    surfaceContainerHighest = Color(0xFFE8EAF1),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFA5B4FC),
    onPrimary = Color(0xFF1E1B4B),
    primaryContainer = Color(0xFF312E81),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFF94A3B8),
    secondaryContainer = Color(0xFF232838),
    onSecondaryContainer = Color(0xFFE2E6F0),
    tertiary = Color(0xFFFBBF24),
    tertiaryContainer = Color(0xFF4A3510),
    onTertiaryContainer = Color(0xFFFFE7B0),
    background = Color(0xFF0E1016),
    onBackground = Color(0xFFE7E9F0),
    surface = Color(0xFF171A23),
    onSurface = Color(0xFFE7E9F0),
    surfaceVariant = Color(0xFF232733),
    onSurfaceVariant = Color(0xFF9EA4B5),
    outline = Color(0xFF3A3F4E),
    outlineVariant = Color(0xFF262A36),
    surfaceContainerLowest = Color(0xFF0B0D12),
    surfaceContainerLow = Color(0xFF14171F),
    surfaceContainer = Color(0xFF191C25),
    surfaceContainerHigh = Color(0xFF20232D),
    surfaceContainerHighest = Color(0xFF282C37),
)

/** 달력에서 쓰는 요일 색 */
val SundayColor = Color(0xFFE5484D)
val SaturdayColor = Color(0xFF3B82F6)

private val base = Typography()
private val AppTypography = Typography(
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 28.sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
)

@Composable
fun MalbiseoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
