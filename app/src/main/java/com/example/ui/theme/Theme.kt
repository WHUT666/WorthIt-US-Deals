package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Immutable
data class ExtendedDealColors(
    val success: Color,
    val successContainer: Color,
    val coupon: Color,
    val couponContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val livePulse: Color,
    val alertBannerBg: Color,
    val alertBannerOnBg: Color
)

val LocalExtendedDealColors = staticCompositionLocalOf {
    ExtendedDealColors(
        success = LightSuccess,
        successContainer = LightSuccessContainer,
        coupon = LightCoupon,
        couponContainer = LightCouponContainer,
        warning = LightWarning,
        warningContainer = LightWarningContainer,
        livePulse = LightLivePulse,
        alertBannerBg = LightBannerBg,
        alertBannerOnBg = LightBannerOnBg
    )
}

private val DarkColorScheme = darkColorScheme(
    primary = WorthDarkPrimary,
    onPrimary = WorthDarkOnPrimary,
    primaryContainer = WorthDarkPrimaryContainer,
    onPrimaryContainer = WorthDarkOnPrimaryContainer,
    secondary = WorthDarkSecondary,
    onSecondary = WorthDarkOnSecondary,
    secondaryContainer = WorthDarkSecondaryContainer,
    onSecondaryContainer = WorthDarkOnSecondaryContainer,
    tertiary = WorthDarkTertiary,
    onTertiary = WorthDarkOnTertiary,
    tertiaryContainer = WorthDarkTertiaryContainer,
    onTertiaryContainer = WorthDarkOnTertiaryContainer,
    background = WorthDarkBackground,
    onBackground = WorthDarkOnBackground,
    surface = WorthDarkSurface,
    onSurface = WorthDarkOnSurface,
    surfaceVariant = WorthDarkSurfaceVariant,
    onSurfaceVariant = WorthDarkOnSurfaceVariant,
    outline = WorthDarkOutline,
    outlineVariant = WorthDarkOutlineVariant
)

private val LightColorScheme = lightColorScheme(
    primary = WorthPrimary,
    onPrimary = WorthOnPrimary,
    primaryContainer = WorthPrimaryContainer,
    onPrimaryContainer = WorthOnPrimaryContainer,
    secondary = WorthSecondary,
    onSecondary = WorthOnSecondary,
    secondaryContainer = WorthSecondaryContainer,
    onSecondaryContainer = WorthOnSecondaryContainer,
    tertiary = WorthTertiary,
    onTertiary = WorthOnTertiary,
    tertiaryContainer = WorthTertiaryContainer,
    onTertiaryContainer = WorthOnTertiaryContainer,
    background = WorthBackground,
    onBackground = WorthOnBackground,
    surface = WorthSurface,
    onSurface = WorthOnSurface,
    surfaceVariant = WorthSurfaceVariant,
    onSurfaceVariant = WorthOnSurfaceVariant,
    outline = WorthOutline,
    outlineVariant = WorthOutlineVariant
)

private val LightExtendedColors = ExtendedDealColors(
    success = LightSuccess,
    successContainer = LightSuccessContainer,
    coupon = LightCoupon,
    couponContainer = LightCouponContainer,
    warning = LightWarning,
    warningContainer = LightWarningContainer,
    livePulse = LightLivePulse,
    alertBannerBg = LightBannerBg,
    alertBannerOnBg = LightBannerOnBg
)

private val DarkExtendedColors = ExtendedDealColors(
    success = DarkSuccess,
    successContainer = DarkSuccessContainer,
    coupon = DarkCoupon,
    couponContainer = DarkCouponContainer,
    warning = DarkWarning,
    warningContainer = DarkWarningContainer,
    livePulse = DarkLivePulse,
    alertBannerBg = DarkBannerBg,
    alertBannerOnBg = DarkBannerOnBg
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(LocalExtendedDealColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

object WorthItTheme {
    val extendedColors: ExtendedDealColors
        @Composable
        get() = LocalExtendedDealColors.current
}
