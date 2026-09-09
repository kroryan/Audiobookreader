package com.audiobookreader.desktop

import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.ui.graphics.Color

internal fun desktopColors(dark: Boolean) = if (dark) darkColors(
    primary = Color(0xFFB5C7FF), onPrimary = Color(0xFF172D64),
    secondary = Color(0xFF72D5DF), onSecondary = Color(0xFF00363D),
    background = Color(0xFF101622), onBackground = Color(0xFFE1E8F5),
    surface = Color(0xFF1B2638), onSurface = Color(0xFFE1E8F5),
    error = Color(0xFFFFB4AB),
) else lightColors(
    primary = Color(0xFF304E8D), onPrimary = Color.White,
    secondary = Color(0xFF006874), onSecondary = Color.White,
    background = Color(0xFFD8E6FA), onBackground = Color(0xFF15233C),
    surface = Color(0xFFE4EDFC), onSurface = Color(0xFF15233C),
    error = Color(0xFFA82020),
)
