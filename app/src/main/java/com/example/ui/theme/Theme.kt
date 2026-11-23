package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

@Immutable
data class ExtendedDealColors(
    val priceAccent: Color,
    val priceAccentContainer: Color,
    val success: Color,
    val successContainer: Color,
    val coupon: Color,
    val couponContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val livePulse: Color,
    val chromeBarBg: Color,
    val hairline: Color,
    val alertBannerBg: Color,
    val alertBannerOnBg: Color
)

val LocalExtendedDealColors = staticCompositionLocalOf {
    ExtendedDealColors(
        priceAccent = LightPriceAccent,
        priceAccentContainer = LightPriceAccentContainer,
        success = LightSuccess,
        successContainer = LightSuccessContainer,
        coupon = LightCoupon,
        couponContainer = LightCouponContainer,
        warning = LightWarning,
        warningContainer = LightWarningContainer,
        livePulse = LightLivePulse,
        chromeBarBg = LightChromeBarBg,
        hairline = LightHairline,
        alertBannerBg = LightBannerBg,
        alertBannerOnBg = LightBannerOnBg
    )
}

private val AppleDarkColorScheme = darkColorScheme(
    primary = AppleDarkPrimary,
    onPrimary = AppleDarkOnPrimary,
    primaryContainer = AppleDarkPrimaryContainer,
    onPrimaryContainer = AppleDarkOnPrimaryContainer,
    secondary = AppleDarkSecondary,
    onSecondary = AppleDarkOnSecondary,
    secondaryContainer = AppleDarkSecondaryContainer,
    onSecondaryContainer = AppleDarkOnSecondaryContainer,
    tertiary = AppleDarkTertiary,
    onTertiary = AppleDarkOnTertiary,
    tertiaryContainer = AppleDarkTertiaryContainer,
    onTertiaryContainer = AppleDarkOnTertiaryContainer,
    error = AppleDarkError,
    onError = AppleDarkOnError,
    errorContainer = AppleDarkErrorContainer,
    onErrorContainer = AppleDarkOnErrorContainer,
    background = AppleDarkBackground,
    onBackground = AppleDarkOnBackground,
    surface = AppleDarkSurface,
    onSurface = AppleDarkOnSurface,
    surfaceVariant = AppleDarkSurfaceVariant,
    onSurfaceVariant = AppleDarkOnSurfaceVariant,
    surfaceContainerLowest = AppleDarkSurfaceContainerLowest,
    surfaceContainerLow = AppleDarkSurfaceContainerLow,
    surfaceContainer = AppleDarkSurfaceContainer,
    surfaceContainerHigh = AppleDarkSurfaceContainerHigh,
    surfaceContainerHighest = AppleDarkSurfaceContainerHighest,
    outline = AppleDarkOutline,
    outlineVariant = AppleDarkOutlineVariant
)

private val AppleLightColorScheme = lightColorScheme(
    primary = AppleLightPrimary,
    onPrimary = AppleLightOnPrimary,
    primaryContainer = AppleLightPrimaryContainer,
    onPrimaryContainer = AppleLightOnPrimaryContainer,
    secondary = AppleLightSecondary,
    onSecondary = AppleLightOnSecondary,
    secondaryContainer = AppleLightSecondaryContainer,
    onSecondaryContainer = AppleLightOnSecondaryContainer,
    tertiary = AppleLightTertiary,
    onTertiary = AppleLightOnTertiary,
    tertiaryContainer = AppleLightTertiaryContainer,
    onTertiaryContainer = AppleLightOnTertiaryContainer,
    error = AppleLightError,
    onError = AppleLightOnError,
    errorContainer = AppleLightErrorContainer,
    onErrorContainer = AppleLightOnErrorContainer,
    background = AppleLightBackground,
    onBackground = AppleLightOnBackground,
    surface = AppleLightSurface,
    onSurface = AppleLightOnSurface,
    surfaceVariant = AppleLightSurfaceVariant,
    onSurfaceVariant = AppleLightOnSurfaceVariant,
    surfaceContainerLowest = AppleLightSurfaceContainerLowest,
    surfaceContainerLow = AppleLightSurfaceContainerLow,
    surfaceContainer = AppleLightSurfaceContainer,
    surfaceContainerHigh = AppleLightSurfaceContainerHigh,
    surfaceContainerHighest = AppleLightSurfaceContainerHighest,
    outline = AppleLightOutline,
    outlineVariant = AppleLightOutlineVariant
)

private val AppleLightExtendedColors = ExtendedDealColors(
    priceAccent = LightPriceAccent,
    priceAccentContainer = LightPriceAccentContainer,
    success = LightSuccess,
    successContainer = LightSuccessContainer,
    coupon = LightCoupon,
    couponContainer = LightCouponContainer,
    warning = LightWarning,
    warningContainer = LightWarningContainer,
    livePulse = LightLivePulse,
    chromeBarBg = LightChromeBarBg,
    hairline = LightHairline,
    alertBannerBg = LightBannerBg,
    alertBannerOnBg = LightBannerOnBg
)

private val AppleDarkExtendedColors = ExtendedDealColors(
    priceAccent = DarkPriceAccent,
    priceAccentContainer = DarkPriceAccentContainer,
    success = DarkSuccess,
    successContainer = DarkSuccessContainer,
    coupon = DarkCoupon,
    couponContainer = DarkCouponContainer,
    warning = DarkWarning,
    warningContainer = DarkWarningContainer,
    livePulse = DarkLivePulse,
    chromeBarBg = DarkChromeBarBg,
    hairline = DarkHairline,
    alertBannerBg = DarkBannerBg,
    alertBannerOnBg = DarkBannerOnBg
)

// Apple-style continuous squircle corner scale
private val AppleShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> AppleDarkColorScheme
        else -> AppleLightColorScheme
    }

    val extendedColors = if (darkTheme) AppleDarkExtendedColors else AppleLightExtendedColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalExtendedDealColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = AppleShapes,
            content = content
        )
    }
}

object WorthItTheme {
    val extendedColors: ExtendedDealColors
        @Composable
        get() = LocalExtendedDealColors.current
}
