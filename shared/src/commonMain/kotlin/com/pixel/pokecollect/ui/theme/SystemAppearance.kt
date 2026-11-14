package com.pixel.pokecollect.ui.theme

import androidx.compose.runtime.Composable
import com.pixel.pokecollect.settings.Appearance

/**
 * Makes what the OS draws match the app's theme: status and navigation bar icons on Android; the
 * window's interface style on iOS (status bar, keyboard, system sheets).
 */
@Composable
internal expect fun SystemAppearance(appearance: Appearance, dark: Boolean)
