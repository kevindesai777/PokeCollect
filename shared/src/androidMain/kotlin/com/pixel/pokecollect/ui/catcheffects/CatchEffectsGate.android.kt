package com.pixel.pokecollect.ui.catcheffects

import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

internal actual val catchEffectsSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/** Developer options and Accessibility's "Remove animations" both set the animator scale to 0. */
@Composable
internal actual fun rememberReducedMotionCheck(): () -> Boolean {
    val resolver = LocalContext.current.applicationContext.contentResolver
    return remember(resolver) {
        { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    }
}
