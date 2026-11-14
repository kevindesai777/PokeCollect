package com.pixel.pokecollect.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.pixel.pokecollect.settings.Appearance
import platform.UIKit.UIApplication
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene

/**
 * Sets the window's interface style, so the status bar, the keyboard and system sheets follow the
 * app's choice. System clears the override, and isSystemInDarkTheme() then reads the phone again.
 */
@Composable
internal actual fun SystemAppearance(appearance: Appearance, dark: Boolean) {
    LaunchedEffect(appearance) {
        val style = when (appearance) {
            Appearance.System -> UIUserInterfaceStyle.UIUserInterfaceStyleUnspecified
            Appearance.Light -> UIUserInterfaceStyle.UIUserInterfaceStyleLight
            Appearance.Dark -> UIUserInterfaceStyle.UIUserInterfaceStyleDark
        }
        UIApplication.sharedApplication.connectedScenes
            .filterIsInstance<UIWindowScene>()
            .flatMap { it.windows }
            .filterIsInstance<UIWindow>()
            .forEach { it.overrideUserInterfaceStyle = style }
    }
}
