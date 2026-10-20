package com.pixel.pokecollect.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.pixel.pokecollect.model.AiPackModelDelivery
import org.koin.compose.koinInject

@Composable
actual fun rememberDownloadConfirmation(): () -> Unit {
    val delivery = koinInject<AiPackModelDelivery>()
    // The answer arrives as pack state updates, so the result itself isn't needed.
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {}
    return remember(delivery, launcher) { { delivery.confirm(launcher) } }
}
