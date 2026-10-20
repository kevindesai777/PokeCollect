package com.pixel.pokecollect.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * How the model file reaches the phone, before [LocalModel.load] can use it.
 *
 * Android ships its own model: Google Play downloads it as an AI pack after install. iOS has
 * nothing to deliver, because the OS owns the model ([NothingToDeliver]).
 */
interface ModelDelivery {
    val state: StateFlow<DeliveryState>

    /** Starts or resumes the download if the model isn't on the phone yet. Safe to call again. */
    fun request()
}

sealed interface DeliveryState {
    /** Not known yet. */
    data object Checking : DeliveryState

    data object Delivered : DeliveryState

    /** [totalBytes] is 0 while the size isn't known. */
    data class Downloading(val bytes: Long, val totalBytes: Long) : DeliveryState

    /** Too big for mobile data without asking: the download waits for Wi-Fi unless the person agrees. */
    data class WaitingForWifi(val totalBytes: Long) : DeliveryState

    /** The store has to confirm the download, e.g. for an app that wasn't installed from it. */
    data object NeedsConfirmation : DeliveryState

    data class Failed(val reason: String) : DeliveryState
}

/** iOS: Apple's Foundation Models is part of the OS. */
object NothingToDeliver : ModelDelivery {
    override val state: StateFlow<DeliveryState> = MutableStateFlow(DeliveryState.Delivered).asStateFlow()

    override fun request() {}
}
