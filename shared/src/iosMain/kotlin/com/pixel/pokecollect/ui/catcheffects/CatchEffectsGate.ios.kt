package com.pixel.pokecollect.ui.catcheffects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

internal actual val catchEffectsSupported: Boolean = true

@Composable
internal actual fun rememberReducedMotionCheck(): () -> Boolean = remember { { UIAccessibilityIsReduceMotionEnabled() } }
