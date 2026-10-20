package com.pixel.pokecollect.model

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.gms.common.api.ApiException
import com.google.android.play.core.aipacks.AiPackManagerFactory
import com.google.android.play.core.aipacks.AiPackState
import com.google.android.play.core.aipacks.AiPackStateUpdateListener
import com.google.android.play.core.aipacks.model.AiPackErrorCode
import com.google.android.play.core.aipacks.model.AiPackStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Gets the Gemma file from Google Play: the fast-follow AI pack `gemma_pack` (module :gemma_pack),
 * which Play downloads right after install and unpacks to internal storage, where LiteRT-LM can
 * open it by path.
 *
 * Debug builds use a file pushed with adb first, so development never waits for Play. Release
 * builds only use the pack, so a phone that has the adb file still shows the real download.
 */
class AiPackModelDelivery(private val context: Context) : ModelDelivery {
    private val manager = AiPackManagerFactory.getInstance(context)
    private val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    private val _state = MutableStateFlow<DeliveryState>(DeliveryState.Checking)
    override val state: StateFlow<DeliveryState> = _state.asStateFlow()

    private val listener = AiPackStateUpdateListener { pack -> if (pack.name() == PACK) update(pack) }
    private var listening = false

    /** The model file, or null while it isn't on the phone. Don't cache it: app updates move the pack. */
    fun modelFile(): File? = (if (debuggable) developerModelFile(context) else null) ?: packFile()

    override fun request() {
        if (modelFile() != null) {
            _state.value = DeliveryState.Delivered
            return
        }
        if (!listening) {
            manager.registerListener(listener)
            listening = true
        }
        // fast-follow starts on its own after install; fetch() resumes it, or starts it under
        // bundletool's local testing, where fast-follow packs behave like on-demand ones.
        manager.fetch(listOf(PACK))
            .addOnSuccessListener { states -> states.packStates()[PACK]?.let(::update) }
            .addOnFailureListener { e ->
                Log.w(TAG, "fetch($PACK) failed", e)
                _state.value = DeliveryState.Failed(failureReason((e as? ApiException)?.statusCode))
            }
    }

    /** Play's dialog: download over mobile data now, or get the app from Play first. */
    fun confirm(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        manager.showConfirmationDialog(launcher)
    }

    private fun update(pack: AiPackState) {
        val next = deliveryState(pack.status(), pack.errorCode(), pack.bytesDownloaded(), pack.totalBytesToDownload())
        Log.i(TAG, "$PACK: status ${pack.status()}, ${pack.bytesDownloaded()} / ${pack.totalBytesToDownload()} bytes, error ${pack.errorCode()}")
        _state.value = when {
            // Completed only counts once the file is really there.
            next == DeliveryState.Delivered && packFile() == null -> DeliveryState.Failed("The model downloaded, but $MODEL_FILE isn't in it.")
            else -> next
        }
    }

    private fun packFile(): File? =
        manager.getPackLocation(PACK)?.assetsPath()?.let { File(it, MODEL_FILE) }?.takeIf { it.canRead() }

    private companion object {
        const val TAG = "PokeCollect.Delivery"
        const val PACK = "gemma_pack"
    }
}

/** Play's pack status, in the app's words. */
internal fun deliveryState(status: Int, errorCode: Int, bytes: Long, totalBytes: Long): DeliveryState = when (status) {
    AiPackStatus.COMPLETED -> DeliveryState.Delivered
    AiPackStatus.NOT_INSTALLED, AiPackStatus.PENDING, AiPackStatus.DOWNLOADING -> DeliveryState.Downloading(bytes, totalBytes)
    // Downloaded; Play is unpacking it.
    AiPackStatus.TRANSFERRING -> DeliveryState.Downloading(totalBytes, totalBytes)
    AiPackStatus.WAITING_FOR_WIFI -> DeliveryState.WaitingForWifi(totalBytes)
    AiPackStatus.REQUIRES_USER_CONFIRMATION -> DeliveryState.NeedsConfirmation
    AiPackStatus.CANCELED -> DeliveryState.Failed("The download was cancelled.")
    AiPackStatus.FAILED -> DeliveryState.Failed(failureReason(errorCode))
    else -> DeliveryState.Checking
}

internal fun failureReason(errorCode: Int?): String = when (errorCode) {
    AiPackErrorCode.NETWORK_ERROR -> "No connection. The model comes from Google Play once; connect and try again."
    AiPackErrorCode.INSUFFICIENT_STORAGE -> "Not enough storage for the model (about 600 MB). Free some space and try again."
    AiPackErrorCode.APP_UNAVAILABLE, AiPackErrorCode.PACK_UNAVAILABLE, AiPackErrorCode.APP_NOT_OWNED,
    AiPackErrorCode.UNRECOGNIZED_INSTALLATION, AiPackErrorCode.API_NOT_AVAILABLE ->
        "This copy of PokeCollect can't get the model from Google Play. Install it from Play. " +
            "Development build: adb push $MODEL_FILE /data/local/tmp/llm/"
    null -> "The model download didn't start. Try again."
    else -> "The model download failed (error $errorCode). Try again."
}
