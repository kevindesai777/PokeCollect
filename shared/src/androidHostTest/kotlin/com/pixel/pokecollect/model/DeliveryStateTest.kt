package com.pixel.pokecollect.model

import com.google.android.play.core.aipacks.model.AiPackErrorCode
import com.google.android.play.core.aipacks.model.AiPackStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DeliveryStateTest {
    private val total = 584_417_280L

    @Test
    fun downloadingCarriesProgress() {
        assertEquals(DeliveryState.Downloading(100, total), deliveryState(AiPackStatus.DOWNLOADING, AiPackErrorCode.NO_ERROR, 100, total))
        assertEquals(DeliveryState.Downloading(0, total), deliveryState(AiPackStatus.PENDING, AiPackErrorCode.NO_ERROR, 0, total))
    }

    @Test
    fun unpackingCountsAsFullyDownloaded() {
        assertEquals(DeliveryState.Downloading(total, total), deliveryState(AiPackStatus.TRANSFERRING, AiPackErrorCode.NO_ERROR, 7, total))
    }

    @Test
    fun completedIsDelivered() {
        assertEquals(DeliveryState.Delivered, deliveryState(AiPackStatus.COMPLETED, AiPackErrorCode.NO_ERROR, total, total))
    }

    @Test
    fun mobileDataAndOtherInstallsNeedThePerson() {
        assertEquals(DeliveryState.WaitingForWifi(total), deliveryState(AiPackStatus.WAITING_FOR_WIFI, AiPackErrorCode.NO_ERROR, 0, total))
        assertEquals(DeliveryState.NeedsConfirmation, deliveryState(AiPackStatus.REQUIRES_USER_CONFIRMATION, AiPackErrorCode.NO_ERROR, 0, total))
    }

    @Test
    fun failuresSayWhatToDo() {
        val noSpace = deliveryState(AiPackStatus.FAILED, AiPackErrorCode.INSUFFICIENT_STORAGE, 0, total)
        assertIs<DeliveryState.Failed>(noSpace)
        assertTrue("storage" in noSpace.reason)
        val sideloaded = deliveryState(AiPackStatus.FAILED, AiPackErrorCode.PACK_UNAVAILABLE, 0, 0)
        assertIs<DeliveryState.Failed>(sideloaded)
        assertTrue("adb push" in sideloaded.reason)
        assertIs<DeliveryState.Failed>(deliveryState(AiPackStatus.CANCELED, AiPackErrorCode.NO_ERROR, 0, total))
    }
}
