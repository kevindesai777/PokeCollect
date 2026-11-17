package com.pixel.pokecollect.ui.catcheffects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * Whether this device plays catch effects at all: Android 13 (API 33) and newer, and iOS. Some
 * effects need runtime shaders, which arrived in API 33; rather than keep simpler copies for
 * Android 12, older phones keep the plain fill and the About screen doesn't offer the switch.
 */
internal expect val catchEffectsSupported: Boolean

/** Reads the phone's "less motion" setting: Remove animations on Android, Reduce Motion on iOS. */
@Composable
internal expect fun rememberReducedMotionCheck(): () -> Boolean

/**
 * True while the phone asks apps for less motion. Read again each time the app comes back to the
 * foreground, which is where someone changing the setting returns from.
 */
@Composable
internal fun rememberSystemReducedMotion(): Boolean {
    val check = rememberReducedMotionCheck()
    var reduced by remember { mutableStateOf(check()) }
    LifecycleResumeEffect(check) {
        reduced = check()
        onPauseOrDispose {}
    }
    return reduced
}

/**
 * Whether ticking a card plays its effect: the device supports it, the switch on the About screen
 * is on, and the phone isn't asking for less motion. Provided once, in App.
 */
internal val LocalCatchEffects = staticCompositionLocalOf { false }
