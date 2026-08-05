package com.autobuy.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ==================== 색상 팔레트 ====================
// 프리미엄 다크 테마: 딥 퍼플 + 사이안 액센트

val PrimaryPurple = Color(0xFF7C3AED)        // 비비드 퍼플
val PrimaryPurpleLight = Color(0xFFA78BFA)    // 라이트 퍼플
val SecondaryTeal = Color(0xFF14B8A6)         // 사이안/틸
val AccentAmber = Color(0xFFF59E0B)           // 강조 앰버
val ErrorRed = Color(0xFFEF4444)              // 에러 레드
val SuccessGreen = Color(0xFF10B981)          // 성공 그린

val BackgroundDark = Color(0xFF09090B)        // 거의 블랙
val SurfaceDark = Color(0xFF18181B)           // 카드 배경
val SurfaceVariantDark = Color(0xFF27272A)    // 구분 선
val OnSurfaceDark = Color(0xFFF4F4F5)         // 기본 텍스트
val OnSurfaceVariantDark = Color(0xFFA1A1AA)  // 보조 텍스트

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryPurple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4C1D95),
    onPrimaryContainer = PrimaryPurpleLight,
    secondary = SecondaryTeal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF134E4A),
    onSecondaryContainer = Color(0xFF99F6E4),
    tertiary = AccentAmber,
    onTertiary = Color.Black,
    error = ErrorRed,
    background = BackgroundDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = Color(0xFF52525B)
)

@Composable
fun AutoBuyTheme(
    darkTheme: Boolean = true,  // 항상 다크 모드 기본값
    dynamicColor: Boolean = false,  // 다이나믹 컬러 비활성화 (커스텀 팔레트 우선)
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> DarkColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = BackgroundDark.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AutoBuyTypography,
        content = content
    )
}
