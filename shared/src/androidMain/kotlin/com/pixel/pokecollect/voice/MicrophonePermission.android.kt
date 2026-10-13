package com.pixel.pokecollect.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberMicrophonePermission(onResult: (granted: Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val latest by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { latest(it) }
    return remember(context, launcher) {
        {
            if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                latest(true)
            } else {
                launcher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
}
