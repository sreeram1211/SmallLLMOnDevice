package com.pocketsloth.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = SlothPrimary,
    onPrimary = SlothOnPrimary,
    primaryContainer = SlothPrimaryContainer,
    onPrimaryContainer = SlothOnPrimaryContainer,
    secondary = SlothSecondary,
    onSecondary = SlothOnSecondary,
    secondaryContainer = SlothSecondaryContainer,
    onSecondaryContainer = SlothOnSecondaryContainer,
    tertiary = SlothTertiary,
    onTertiary = SlothOnTertiary,
    tertiaryContainer = SlothTertiaryContainer,
    onTertiaryContainer = SlothOnTertiaryContainer,
    error = SlothError,
    onError = SlothOnError,
    errorContainer = SlothErrorContainer,
    onErrorContainer = SlothOnErrorContainer,
    background = SlothBackground,
    onBackground = SlothOnBackground,
    surface = SlothSurface,
    onSurface = SlothOnSurface,
    surfaceVariant = SlothSurfaceVariant,
    onSurfaceVariant = SlothOnSurfaceVariant,
    outline = SlothOutline,
    outlineVariant = SlothOutlineVariant,
    inverseSurface = SlothInverseSurface,
    inverseOnSurface = SlothInverseOnSurface,
    inversePrimary = SlothInversePrimary,
    surfaceContainerLowest = SlothSurfaceContainerLowest,
    surfaceContainerLow = SlothSurfaceContainerLow,
    surfaceContainer = SlothSurfaceContainer,
    surfaceContainerHigh = SlothSurfaceContainerHigh,
    surfaceContainerHighest = SlothSurfaceContainerHighest,
)

private val LightColorScheme = lightColorScheme(
    primary = SlothPrimaryLight,
    onPrimary = SlothOnPrimaryLight,
    primaryContainer = SlothPrimaryContainerLight,
    onPrimaryContainer = SlothOnPrimaryContainerLight,
    secondary = SlothSecondaryLight,
    onSecondary = SlothOnSecondaryLight,
    secondaryContainer = SlothSecondaryContainerLight,
    onSecondaryContainer = SlothOnSecondaryContainerLight,
    tertiary = SlothTertiaryLight,
    onTertiary = SlothOnTertiaryLight,
    tertiaryContainer = SlothTertiaryContainerLight,
    onTertiaryContainer = SlothOnTertiaryContainerLight,
    error = SlothErrorLight,
    onError = SlothOnErrorLight,
    errorContainer = SlothErrorContainerLight,
    onErrorContainer = SlothOnErrorContainerLight,
    background = SlothBackgroundLight,
    onBackground = SlothOnBackgroundLight,
    surface = SlothSurfaceLight,
    onSurface = SlothOnSurfaceLight,
    surfaceVariant = SlothSurfaceVariantLight,
    onSurfaceVariant = SlothOnSurfaceVariantLight,
    outline = SlothOutlineLight,
    outlineVariant = SlothOutlineVariantLight,
)

@Composable
fun PocketSlothTheme(
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PocketSlothTypography,
        content = content,
    )
}
