package com.nickfouf.classwave.webrtc

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutgoingVideoSafetyTest {
    private val hd = Pair(1280, 720)

    @Test
    fun `matching applied and captured settings may send when call video is on`() {
        assertTrue(
            canSend(
                appliedCamera = "front",
                capturedCamera = "front",
                desiredCamera = "front"
            )
        )
    }

    @Test
    fun `previewing another camera keeps outgoing video black`() {
        assertFalse(
            canSend(
                appliedCamera = "front",
                capturedCamera = "back",
                desiredCamera = "back"
            )
        )
    }

    @Test
    fun `camera transition keeps outgoing video black before first new frame`() {
        assertFalse(
            canSend(
                appliedCamera = "back",
                capturedCamera = "front",
                desiredCamera = "back",
                switching = true
            )
        )
    }

    @Test
    fun `unsaved preview resolution keeps outgoing video black`() {
        assertFalse(
            canSend(
                appliedCamera = "front",
                capturedCamera = "front",
                desiredCamera = "front",
                capturedResolution = Pair(640, 480)
            )
        )
    }

    @Test
    fun `call video off always keeps outgoing video black`() {
        assertFalse(
            canSend(
                callVideoEnabled = false,
                appliedCamera = "front",
                capturedCamera = "front",
                desiredCamera = "front"
            )
        )
    }

    private fun canSend(
        callVideoEnabled: Boolean = true,
        appliedCamera: String,
        capturedCamera: String,
        desiredCamera: String,
        switching: Boolean = false,
        capturedResolution: Pair<Int, Int> = hd
    ): Boolean {
        return OutgoingVideoSafety.canSendCameraFrames(
            callVideoEnabled = callVideoEnabled,
            appliedCameraName = appliedCamera,
            capturedCameraName = capturedCamera,
            desiredCameraName = desiredCamera,
            cameraSwitchInProgress = switching,
            appliedResolution = hd,
            capturedResolution = capturedResolution
        )
    }
}
