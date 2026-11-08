package com.nickfouf.classwave.webrtc

internal object OutgoingVideoSafety {
    fun canSendCameraFrames(
        callVideoEnabled: Boolean,
        appliedCameraName: String,
        capturedCameraName: String,
        desiredCameraName: String,
        cameraSwitchInProgress: Boolean,
        appliedResolution: Pair<Int, Int>,
        capturedResolution: Pair<Int, Int>
    ): Boolean {
        return callVideoEnabled &&
            appliedCameraName.isNotEmpty() &&
            !cameraSwitchInProgress &&
            capturedCameraName == appliedCameraName &&
            desiredCameraName == capturedCameraName &&
            capturedResolution == appliedResolution
    }
}
