package com.nickfouf.classwave.webrtc

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.client.Ack
import org.json.JSONObject
import org.json.JSONArray
import org.mediasoup.droid.Device
import org.mediasoup.droid.MediasoupClient
import org.mediasoup.droid.SendTransport
import org.mediasoup.droid.RecvTransport
import org.mediasoup.droid.Producer
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import com.nickfouf.classwave.network.ChatMessage

data class RemoteVideoTrack(val track: VideoTrack, val username: String, val userId: String)

data class CameraDevice(val deviceName: String, val isFrontFacing: Boolean)

data class AudioOutputDevice(val id: Int, val type: Int, val productName: String)

data class MeetingMediaSettings(
    val cameraName: String,
    val videoWidth: Int,
    val videoHeight: Int,
    val flipHorizontally: Boolean,
    val flipVertically: Boolean,
    val audioOutputId: Int?
)

data class RaisedHand(val userId: String, val username: String)

private const val AUDIO_DEVICE_TYPE_USB_HEADSET = 22
private const val MIN_RENDERER_DETACH_DELAY_MS = 100L
private const val CAMERA_RELEASE_TIMEOUT_MS = 1_000L
private const val CHAT_SEND_TIMEOUT_MS = 10_000L

data class ChatSendResult(val success: Boolean, val errorMessage: String? = null)

class WebRtcSessionManager(
    private val context: Context,
    private val roomId: String,
    private val myUserId: String,
    private val localUserName: String,
    private val initialMediaSettings: MeetingMediaSettings? = null
) {
    val eglBaseContext: EglBase.Context = EglBase.create().eglBaseContext
    private val peerConnectionFactory: PeerConnectionFactory
    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
    private val originalAudioMode = audioManager.mode
    @Suppress("DEPRECATION")
    private val originalSpeakerphoneOn = audioManager.isSpeakerphoneOn
    @Suppress("DEPRECATION")
    private val originalBluetoothScoOn = audioManager.isBluetoothScoOn
    private var videoCapturer: VideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    private var localVideoTrack: VideoTrack? = null
    private var cameraPreviewTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null
    private var isAudioEnabled = false
    @Volatile
    private var isVideoEnabled = false

    private val cameraStateLock = Any()
    private var capturedCameraName = ""
    private var desiredCameraName = ""
    private var cameraSwitchInProgress = false
    private var capturedResolution = Pair(1280, 720)


    private val _remoteVideoTracks = MutableStateFlow<Map<String, RemoteVideoTrack>>(emptyMap())
    val remoteVideoTracks: StateFlow<Map<String, RemoteVideoTrack>> = _remoteVideoTracks

    private val _localVideoTrackFlow = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrackFlow: StateFlow<VideoTrack?> = _localVideoTrackFlow

    private val _cameraPreviewTrackFlow = MutableStateFlow<VideoTrack?>(null)
    val cameraPreviewTrackFlow: StateFlow<VideoTrack?> = _cameraPreviewTrackFlow

    val currentCameraName = MutableStateFlow(initialMediaSettings?.cameraName.orEmpty())
    val currentResolution = MutableStateFlow(
        initialMediaSettings?.let { Pair(it.videoWidth, it.videoHeight) } ?: Pair(1280, 720)
    )
    val flipHorizontal = MutableStateFlow(initialMediaSettings?.flipHorizontally ?: true)
    val flipVertical = MutableStateFlow(initialMediaSettings?.flipVertically ?: false)

    private val _availableAudioOutputs = MutableStateFlow<List<AudioOutputDevice>>(emptyList())
    val availableAudioOutputs: StateFlow<List<AudioOutputDevice>> = _availableAudioOutputs

    private val _selectedAudioOutputId = MutableStateFlow<Int?>(null)
    val selectedAudioOutputId: StateFlow<Int?> = _selectedAudioOutputId

    private val _audioRoutingFailed = MutableStateFlow(false)
    val audioRoutingFailed: StateFlow<Boolean> = _audioRoutingFailed

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            refreshAudioOutputs()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            refreshAudioOutputs()
        }
    }

    private var communicationDeviceChangedListener: Any? = null

    private val _classEnded = MutableStateFlow(false)
    val classEnded: StateFlow<Boolean> = _classEnded

    private val _endClassErrors = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val endClassErrors = _endClassErrors.asSharedFlow()

    private val _chatMessagesFlow = kotlinx.coroutines.flow.MutableSharedFlow<ChatMessage>(extraBufferCapacity = 64)
    val chatMessagesFlow = _chatMessagesFlow.asSharedFlow()

    private val _remoteCameraPaused = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val remoteCameraPaused: StateFlow<Map<String, Boolean>> = _remoteCameraPaused

    private val _remoteMicrophoneMuted = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val remoteMicrophoneMuted: StateFlow<Map<String, Boolean>> = _remoteMicrophoneMuted

    private val _cameraErrorFlow = MutableStateFlow<String?>(null)
    val cameraErrorFlow: StateFlow<String?> = _cameraErrorFlow

    private val _activeSpeakerUserId = MutableStateFlow<String?>(null)
    val activeSpeakerUserId: StateFlow<String?> = _activeSpeakerUserId

    private val _raisedHands = MutableStateFlow<Map<String, RaisedHand>>(emptyMap())
    val raisedHands: StateFlow<Map<String, RaisedHand>> = _raisedHands

    private val _handRaisedEvents = kotlinx.coroutines.flow.MutableSharedFlow<RaisedHand>(extraBufferCapacity = 16)
    val handRaisedEvents = _handRaisedEvents.asSharedFlow()

    private var socket: Socket? = null
    private var device: Device? = null
    private var sendTransport: SendTransport? = null
    private var recvTransport: RecvTransport? = null
    
    private var videoProducer: Producer? = null
    private var audioProducer: Producer? = null

    private val consumeMutex = Mutex()
    private val mediaGeneration = AtomicInteger(0)
    private val consumingProducerIds = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var isDisposed = false

    private val releaseStateLock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val stopCallbacks = mutableListOf<() -> Unit>()
    private var releaseStartedAtMillis = 0L
    private var nativeMediaReleaseCompleted = false

    init {
        MediasoupClient.initialize(context.applicationContext)
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                .setEnableInternalTracer(true)
                .setNativeLibraryName("mediasoupclient_so")
                .createInitializationOptions()
        )

        val options = PeerConnectionFactory.Options()
        val defaultVideoEncoderFactory = DefaultVideoEncoderFactory(eglBaseContext, true, true)
        val defaultVideoDecoderFactory = DefaultVideoDecoderFactory(eglBaseContext)

        val audioDeviceModule = JavaAudioDeviceModule.builder(context.applicationContext)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setAudioDeviceModule(audioDeviceModule)
            .setVideoEncoderFactory(defaultVideoEncoderFactory)
            .setVideoDecoderFactory(defaultVideoDecoderFactory)
            .createPeerConnectionFactory()
            
        audioDeviceModule.release()
        initializeAudioRouting()
    }

    private fun initializeAudioRouting() {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, Handler(Looper.getMainLooper()))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val listener = AudioManager.OnCommunicationDeviceChangedListener { refreshAudioOutputs() }
            communicationDeviceChangedListener = listener
            audioManager.addOnCommunicationDeviceChangedListener(context.mainExecutor, listener)
        }
        refreshAudioOutputs()
        initialMediaSettings?.audioOutputId?.let(::selectAudioOutput)
    }

    private fun refreshAudioOutputs() {
        val devices = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.availableCommunicationDevices
        } else {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .filter { it.isSink && isLegacyCommunicationOutput(it.type) }
        }

        val outputs = devices
            .distinctBy { it.id }
            .sortedWith(compareBy<AudioDeviceInfo> { audioOutputSortOrder(it.type) }.thenBy { it.productName.toString() })
            .map { AudioOutputDevice(it.id, it.type, it.productName.toString()) }

        _availableAudioOutputs.value = outputs
        _selectedAudioOutputId.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.communicationDevice?.id
        } else {
            inferLegacySelectedOutput(outputs)?.id
        }
    }

    private fun isLegacyCommunicationOutput(type: Int): Boolean = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AUDIO_DEVICE_TYPE_USB_HEADSET -> true
        else -> false
    }

    private fun audioOutputSortOrder(type: Int): Int = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> 0
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> 1
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> 2
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER -> 3
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AUDIO_DEVICE_TYPE_USB_HEADSET -> 4
        AudioDeviceInfo.TYPE_HEARING_AID -> 5
        else -> 6
    }

    @Suppress("DEPRECATION")
    private fun inferLegacySelectedOutput(outputs: List<AudioOutputDevice>): AudioOutputDevice? {
        val preferredTypes = when {
            audioManager.isBluetoothScoOn -> listOf(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
            audioManager.isSpeakerphoneOn -> listOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
            else -> listOf(
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AUDIO_DEVICE_TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_USB_DEVICE,
                AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
            )
        }
        return preferredTypes.firstNotNullOfOrNull { type -> outputs.firstOrNull { it.type == type } }
            ?: outputs.firstOrNull()
    }

    fun selectAudioOutput(deviceId: Int) {
        _audioRoutingFailed.value = false
        val selected = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.availableCommunicationDevices.firstOrNull { it.id == deviceId }
        } else {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.id == deviceId }
        }

        if (selected == null) {
            _audioRoutingFailed.value = true
            refreshAudioOutputs()
            return
        }

        val accepted = runCatching {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.setCommunicationDevice(selected)
            } else {
                selectLegacyAudioOutput(selected.type)
                true
            }
        }.getOrElse {
            Log.e("WebRTC", "Failed to select audio output", it)
            false
        }

        _audioRoutingFailed.value = !accepted
        if (accepted) _selectedAudioOutputId.value = selected.id
    }

    fun clearAudioOutputSelection() {
        _audioRoutingFailed.value = false
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                audioManager.stopBluetoothSco()
                @Suppress("DEPRECATION")
                run {
                    audioManager.isBluetoothScoOn = originalBluetoothScoOn
                    audioManager.isSpeakerphoneOn = originalSpeakerphoneOn
                }
            }
            refreshAudioOutputs()
        }.onFailure {
            Log.e("WebRTC", "Failed to restore the system audio output", it)
            _audioRoutingFailed.value = true
        }
    }

    @Suppress("DEPRECATION")
    private fun selectLegacyAudioOutput(type: Int) {
        if (type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
            audioManager.isSpeakerphoneOn = false
            audioManager.startBluetoothSco()
            audioManager.isBluetoothScoOn = true
        } else {
            audioManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false
            audioManager.isSpeakerphoneOn = type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        }
    }

    fun getAvailableCameras(): List<CameraDevice> {
        val enumerator = Camera2Enumerator(context)
        return enumerator.deviceNames.map {
            CameraDevice(it, enumerator.isFrontFacing(it))
        }
    }

    fun snapshotMediaSettings(): MeetingMediaSettings {
        val resolution = currentResolution.value
        return MeetingMediaSettings(
            cameraName = currentCameraName.value,
            videoWidth = resolution.first,
            videoHeight = resolution.second,
            flipHorizontally = flipHorizontal.value,
            flipVertically = flipVertical.value,
            audioOutputId = selectedAudioOutputId.value
        )
    }

    fun previewCamera(deviceName: String) {
        requestCapturedCamera(deviceName)
    }

    fun previewResolution(width: Int, height: Int) {
        val resolution = Pair(width, height)
        val previousResolution = synchronized(cameraStateLock) {
            if (capturedResolution == resolution) {
                null
            } else {
                val previous = capturedResolution
                capturedResolution = resolution
                previous
            }
        }
        if (previousResolution == null) return

        updateOutgoingVideoTrackState()
        runCatching { videoCapturer?.changeCaptureFormat(width, height, 30) }
            .onFailure {
                synchronized(cameraStateLock) {
                    if (capturedResolution == resolution) {
                        capturedResolution = previousResolution
                    }
                }
                Log.e("WebRTC", "Failed to change preview resolution", it)
                _cameraErrorFlow.value = "Failed to change camera resolution."
                updateOutgoingVideoTrackState()
            }
    }

    fun applyVideoSettings(
        cameraName: String,
        resolution: Pair<Int, Int>,
        flipHorizontally: Boolean,
        flipVertically: Boolean
    ) {
        localVideoTrack?.setEnabled(false)
        currentCameraName.value = cameraName
        currentResolution.value = resolution
        flipHorizontal.value = flipHorizontally
        flipVertical.value = flipVertically

        previewResolution(resolution.first, resolution.second)
        requestCapturedCamera(cameraName)
        updateOutgoingVideoTrackState()
    }

    fun discardVideoSettingsPreview() {
        val appliedResolution = currentResolution.value
        previewResolution(appliedResolution.first, appliedResolution.second)
        requestCapturedCamera(currentCameraName.value)
    }

    private fun requestCapturedCamera(deviceName: String) {
        if (deviceName.isEmpty()) return

        synchronized(cameraStateLock) {
            desiredCameraName = deviceName
        }
        updateOutgoingVideoTrackState()
        startNextCameraSwitchIfNeeded()
    }

    private fun startNextCameraSwitchIfNeeded() {
        val cameraCapturer = videoCapturer as? CameraVideoCapturer ?: return
        val target = synchronized(cameraStateLock) {
            when {
                cameraSwitchInProgress -> null
                desiredCameraName.isEmpty() || desiredCameraName == capturedCameraName -> null
                else -> {
                    cameraSwitchInProgress = true
                    desiredCameraName
                }
            }
        }

        if (target == null) {
            updateOutgoingVideoTrackState()
            return
        }

        updateOutgoingVideoTrackState()
        try {
            cameraCapturer.switchCamera(
                object : CameraVideoCapturer.CameraSwitchHandler {
                    override fun onCameraSwitchDone(isFrontCamera: Boolean) {
                        synchronized(cameraStateLock) {
                            capturedCameraName = target
                            cameraSwitchInProgress = false
                        }
                        updateOutgoingVideoTrackState()
                        startNextCameraSwitchIfNeeded()
                    }

                    override fun onCameraSwitchError(errorDescription: String) {
                        synchronized(cameraStateLock) {
                            cameraSwitchInProgress = false
                            if (desiredCameraName == target) {
                                desiredCameraName = capturedCameraName
                            }
                        }
                        _cameraErrorFlow.value = "Camera switch failed: $errorDescription"
                        updateOutgoingVideoTrackState()
                        startNextCameraSwitchIfNeeded()
                    }
                },
                target
            )
        } catch (error: Exception) {
            synchronized(cameraStateLock) {
                cameraSwitchInProgress = false
                if (desiredCameraName == target) {
                    desiredCameraName = capturedCameraName
                }
            }
            Log.e("WebRTC", "Failed to switch preview camera", error)
            _cameraErrorFlow.value = "Camera switch failed."
            updateOutgoingVideoTrackState()
            startNextCameraSwitchIfNeeded()
        }
    }

    private fun updateOutgoingVideoTrackState() {
        val canSend = synchronized(cameraStateLock) {
            OutgoingVideoSafety.canSendCameraFrames(
                callVideoEnabled = isVideoEnabled,
                appliedCameraName = currentCameraName.value,
                capturedCameraName = capturedCameraName,
                desiredCameraName = desiredCameraName,
                cameraSwitchInProgress = cameraSwitchInProgress,
                appliedResolution = currentResolution.value,
                capturedResolution = capturedResolution
            )
        }
        localVideoTrack?.setEnabled(canSend)
    }

    fun startLocalVideo(connectImmediately: Boolean = true) {
        if (videoCapturer != null) return

        try {
            val videoSource = peerConnectionFactory.createVideoSource(false)
            surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBaseContext)

            videoCapturer = createCameraCapturer(Camera2Enumerator(context))
            videoCapturer?.initialize(surfaceTextureHelper, context, videoSource.capturerObserver)
            videoCapturer?.startCapture(currentResolution.value.first, currentResolution.value.second, 30)

            localVideoTrack = peerConnectionFactory.createVideoTrack("local_video_track", videoSource)
            localVideoTrack?.setEnabled(false)
            _localVideoTrackFlow.value = localVideoTrack

            cameraPreviewTrack = peerConnectionFactory.createVideoTrack("camera_preview_track", videoSource)
            cameraPreviewTrack?.setEnabled(true)
            _cameraPreviewTrackFlow.value = cameraPreviewTrack

            val audioConstraints = MediaConstraints()
            val audioSource = peerConnectionFactory.createAudioSource(audioConstraints)
            localAudioTrack = peerConnectionFactory.createAudioTrack("local_audio_track", audioSource)
            localAudioTrack?.setEnabled(isAudioEnabled)

            if (connectImmediately) {
                connectToServer()
            }
        } catch (e: Exception) {
            Log.e("WebRTC", "Σφάλμα εκκίνησης κάμερας: ${e.message}")
        }
    }

    fun connectToServerManually() {
        when {
            socket == null -> connectToServer()
            socket?.connected() != true -> socket?.connect()
        }
    }

    fun toggleAudio(enabled: Boolean) {
        isAudioEnabled = enabled
        localAudioTrack?.setEnabled(enabled)
        emitMicrophoneState()
    }

    private fun emitMicrophoneState() {
        if (socket?.connected() != true) return

        socket?.emit("microphone-state-changed", JSONObject().apply {
            put("roomId", roomId)
            put("isMuted", !isAudioEnabled)
        })
    }

    fun toggleVideo(enabled: Boolean) {
        isVideoEnabled = enabled
        updateOutgoingVideoTrackState()
        emitCameraState(isPaused = !enabled)
    }

    private fun emitCameraState(isPaused: Boolean = !isVideoEnabled) {
        if (socket?.connected() != true) return

        socket?.emit("camera-state-changed", JSONObject().apply {
            put("roomId", roomId)
            put("isPaused", isPaused)
        })
    }

    fun pauseCamera() {
        try {
            videoCapturer?.stopCapture()
            emitCameraState(isPaused = true)
        } catch (e: Exception) {
            Log.e("WebRTC", "Σφάλμα κατά την παύση κάμερας: ${e.message}")
        }
    }

    fun resumeCamera() {
        try {
            val resolution = synchronized(cameraStateLock) { capturedResolution }
            videoCapturer?.startCapture(resolution.first, resolution.second, 30)
            emitCameraState()
            _cameraErrorFlow.value = null
        } catch (e: Exception) {
            Log.e("WebRTC", "Σφάλμα κατά την επαναφορά κάμερας: ${e.message}")
            _cameraErrorFlow.value = "Αποτυχία επαναφοράς κάμερας."
        }
    }

    fun endClassForEveryone(token: String) {
        val activeSocket = socket
        if (activeSocket?.connected() != true) {
            _endClassErrors.tryEmit(Unit)
            return
        }

        activeSocket.emit("end-class", JSONObject().apply {
            put("roomId", roomId)
            put("token", token)
        }, object : Ack {
            override fun call(vararg args: Any) {
                val response = args.firstOrNull() as? JSONObject
                if (response?.optBoolean("success", false) != true) {
                    Log.e("WebRTC", "Failed to end class: ${response?.optString("error")}")
                    _endClassErrors.tryEmit(Unit)
                }
            }
        })
    }

    fun sendChatMessage(message: String, onResult: (ChatSendResult) -> Unit = {}) {
        val normalizedMessage = message.trim()
        if (normalizedMessage.isEmpty()) {
            onResult(ChatSendResult(success = false, errorMessage = "Message cannot be empty."))
            return
        }

        val activeSocket = socket
        if (activeSocket?.connected() != true) {
            onResult(ChatSendResult(success = false, errorMessage = "Connection lost. Try again."))
            return
        }

        val completed = AtomicBoolean(false)
        val timeout = Runnable {
            if (completed.compareAndSet(false, true)) {
                Log.e("WebRTC", "Timed out while sending chat message")
                onResult(ChatSendResult(success = false, errorMessage = "The message timed out. Try again."))
            }
        }
        mainHandler.postDelayed(timeout, CHAT_SEND_TIMEOUT_MS)

        activeSocket.emit("send-chat-message", JSONObject().apply {
            put("roomId", roomId)
            put("message", normalizedMessage)
        }, object : Ack {
            override fun call(vararg args: Any) {
                val response = args.firstOrNull() as? JSONObject
                val result = if (response?.optBoolean("success", false) == true) {
                    ChatSendResult(success = true)
                } else {
                    val serverError = response?.optString("error")
                        ?.takeIf { it.isNotBlank() }
                        ?: "Unable to send the message."
                    Log.e("WebRTC", "Failed to send chat message: $serverError")
                    ChatSendResult(success = false, errorMessage = serverError)
                }

                if (completed.compareAndSet(false, true)) {
                    mainHandler.removeCallbacks(timeout)
                    mainHandler.post { onResult(result) }
                }
            }
        })
    }

    fun setHandRaised(isRaised: Boolean) {
        val activeSocket = socket
        if (activeSocket?.connected() != true) {
            Log.w("WebRTC", "Cannot change hand state while the socket is disconnected")
            return
        }

        activeSocket.emit("raise-hand", JSONObject().apply {
            put("roomId", roomId)
            put("isRaised", isRaised)
        }, object : Ack {
            override fun call(vararg args: Any) {
                val response = args.firstOrNull() as? JSONObject ?: return
                if (!response.optBoolean("success", false)) {
                    Log.e("WebRTC", "Failed to change hand state: ${response.optString("error")}")
                }
            }
        })
    }

    private fun connectToServer() {
        try {
            socket = IO.socket(com.nickfouf.classwave.BuildConfig.SERVER_URL)

            socket?.on(Socket.EVENT_CONNECT) {
                if (isDisposed) return@on

                Log.d("WebRTC", "✅ Συνδέθηκε στο Socket.io!")
                val generation = mediaGeneration.incrementAndGet()
                resetMediaSession()
                joinRoom(generation)
            }

            socket?.on(Socket.EVENT_DISCONNECT) { args ->
                if (isDisposed) return@on

                Log.w("WebRTC", "Socket disconnected: ${args.firstOrNull() ?: "unknown reason"}")
                mediaGeneration.incrementAndGet()
                resetMediaSession()
            }

            socket?.on("receive-chat-message") { args ->
                try {
                    val data = args[0] as JSONObject
                    val msg = ChatMessage(
                        _id = data.optString("_id"),
                        userId = data.optString("userId"),
                        username = data.optString("username"),
                        text = data.optString("text"),
                        timestamp = data.optString("timestamp")
                    )
                    _chatMessagesFlow.tryEmit(msg)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            socket?.on("new-producer") { args ->
                val data = args[0] as JSONObject
                val producerId = data.getString("producerId")
                val userId = data.optString("userId", "Unknown")
                val username = data.optString("username", "Συμμετέχων")
                val isPaused = data.optBoolean("isPaused", false)
                val isMuted = data.optBoolean("isMuted", true)
                _remoteCameraPaused.update { currentMap ->
                    val newMap = currentMap.toMutableMap()
                    newMap[userId] = isPaused
                    newMap
                }
                _remoteMicrophoneMuted.update { currentMap ->
                    currentMap + (userId to isMuted)
                }
                consumeTrack(producerId, userId, username, mediaGeneration.get())
            }

            socket?.on("class-ended") { _classEnded.value = true }

            socket?.on("active-speaker-changed") { args ->
                try {
                    val data = args.firstOrNull() as? JSONObject ?: return@on
                    _activeSpeakerUserId.value = data.optString("userId").takeIf { it.isNotBlank() }
                } catch (e: Exception) {
                    Log.e("WebRTC", "Error handling active speaker update: ${e.message}")
                }
            }

            socket?.on("hand-raised") { args ->
                try {
                    val data = args.firstOrNull() as? JSONObject ?: return@on
                    val userId = data.optString("userId")
                    if (userId.isBlank()) return@on

                    val raisedHand = RaisedHand(
                        userId = userId,
                        username = data.optString("username", "Participant").ifBlank { "Participant" }
                    )
                    val isRaised = data.optBoolean("isRaised", false)
                    val wasRaised = _raisedHands.value.containsKey(userId)

                    _raisedHands.update { currentHands ->
                        if (isRaised) currentHands + (userId to raisedHand) else currentHands - userId
                    }

                    if (isRaised && !wasRaised && userId != myUserId) {
                        _handRaisedEvents.tryEmit(raisedHand)
                    }
                } catch (e: Exception) {
                    Log.e("WebRTC", "Error handling raised hand update: ${e.message}")
                }
            }

            socket?.on("producer-closed") { args ->
                val data = args[0] as JSONObject
                val producerId = data.getString("producerId")
                consumingProducerIds.remove(producerId)

                _remoteVideoTracks.update { currentMap ->
                    val newMap = currentMap.toMutableMap()
                    newMap.remove(producerId)
                    newMap
                }
            }

            socket?.on("user-camera-state-changed") { args ->
                val data = args[0] as JSONObject
                val userId = data.optString("userId")
                val isPaused = data.optBoolean("isPaused", false)
                
                _remoteCameraPaused.update { currentMap ->
                    val newMap = currentMap.toMutableMap()
                    newMap[userId] = isPaused
                    newMap
                }
            }

            socket?.on("user-microphone-state-changed") { args ->
                try {
                    val data = args.firstOrNull() as? JSONObject ?: return@on
                    val userId = data.optString("userId")
                    if (userId.isBlank()) return@on

                    _remoteMicrophoneMuted.update { currentMap ->
                        currentMap + (userId to data.optBoolean("isMuted", true))
                    }
                } catch (e: Exception) {
                    Log.e("WebRTC", "Error handling microphone state update: ${e.message}")
                }
            }

            socket?.on("user-left") { args ->
                val data = args.firstOrNull() as? JSONObject ?: return@on
                removeRemoteUser(data.optString("userId"))
            }

            socket?.connect()
        } catch (e: Exception) {
            Log.e("WebRTC", "❌ Σφάλμα σύνδεσης Socket: ${e.message}")
        }
    }

    private fun isGenerationActive(generation: Int): Boolean {
        return !isDisposed && mediaGeneration.get() == generation && socket?.connected() == true
    }

    @Synchronized
    private fun resetMediaSession() {
        val oldVideoProducer = videoProducer.also { videoProducer = null }
        val oldAudioProducer = audioProducer.also { audioProducer = null }
        val oldSendTransport = sendTransport.also { sendTransport = null }
        val oldRecvTransport = recvTransport.also { recvTransport = null }

        runCatching { oldVideoProducer?.close() }
            .onFailure { Log.w("WebRTC", "Failed to close stale video producer", it) }
        runCatching { oldAudioProducer?.close() }
            .onFailure { Log.w("WebRTC", "Failed to close stale audio producer", it) }
        runCatching { oldSendTransport?.close() }
            .onFailure { Log.w("WebRTC", "Failed to close stale send transport", it) }
        runCatching { oldRecvTransport?.close() }
            .onFailure { Log.w("WebRTC", "Failed to close stale receive transport", it) }

        device = null
        consumingProducerIds.clear()
        _remoteVideoTracks.value = emptyMap()
        _remoteCameraPaused.value = emptyMap()
        _remoteMicrophoneMuted.value = emptyMap()
        _activeSpeakerUserId.value = null
        _raisedHands.value = emptyMap()
    }

    private fun removeRemoteUser(userId: String) {
        if (userId.isBlank()) return

        val producerIds = _remoteVideoTracks.value
            .filterValues { it.userId == userId }
            .keys
        consumingProducerIds.removeAll(producerIds)
        _remoteVideoTracks.update { tracks -> tracks.filterValues { it.userId != userId } }
        _remoteCameraPaused.update { it - userId }
        _remoteMicrophoneMuted.update { it - userId }
        _raisedHands.update { it - userId }
        if (_activeSpeakerUserId.value == userId) {
            _activeSpeakerUserId.value = null
        }
    }

    private fun joinRoom(generation: Int) {
        if (!isGenerationActive(generation)) return

        val joinData = JSONObject().apply {
            put("roomId", roomId)
            put("userId", myUserId)
            put("username", localUserName)
            put("isMuted", !isAudioEnabled)
            put("isPaused", !isVideoEnabled)
        }

        socket?.emit("join-room", joinData, object : Ack {
            override fun call(vararg args: Any) {
                if (!isGenerationActive(generation)) return

                val response = args.firstOrNull() as? JSONObject ?: return
                if (response.getBoolean("success")) {
                    applyRaisedHandsSnapshot(response.optJSONArray("raisedHands"))
                    emitMicrophoneState()
                    emitCameraState()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            if (!isGenerationActive(generation)) return@launch
                            val rtpCapabilities = response.getJSONObject("rtpCapabilities").toString()
                            Log.d("WebRTC", "✅ Μπήκαμε στο δωμάτιο. RTP Capabilities: $rtpCapabilities")

                            val newDevice = Device()
                            newDevice.load(rtpCapabilities, null)
                            if (!isGenerationActive(generation)) return@launch
                            device = newDevice

                            createSendTransport(generation)
                            createRecvTransport(generation)
                        } catch(e: Exception) {
                            Log.e("WebRTC", "Error in joinRoom: ${e.message}")
                        }
                    }
                } else {
                    Log.e("WebRTC", "❌ Αποτυχία εισόδου στο δωμάτιο")
                }
            }
        })
    }

    private fun applyRaisedHandsSnapshot(raisedHands: JSONArray?) {
        if (raisedHands == null) {
            _raisedHands.value = emptyMap()
            return
        }

        val snapshot = buildMap {
            for (index in 0 until raisedHands.length()) {
                val item = raisedHands.optJSONObject(index) ?: continue
                val userId = item.optString("userId")
                if (userId.isBlank()) continue

                put(
                    userId,
                    RaisedHand(
                        userId = userId,
                        username = item.optString("username", "Participant").ifBlank { "Participant" }
                    )
                )
            }
        }
        _raisedHands.value = snapshot
    }

    // Send transport
    private fun createSendTransport(generation: Int) {
        if (!isGenerationActive(generation)) return

        socket?.emit("createWebRtcTransport", JSONObject().put("roomId", roomId), object : Ack {
            override fun call(vararg args: Any) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        if (!isGenerationActive(generation)) return@launch
                        val response = args.firstOrNull() as? JSONObject ?: return@launch
                        if (response.getBoolean("success")) {
                            val params = response.getJSONObject("params")
                            val currentDevice = device ?: return@launch
                            val newTransport = currentDevice.createSendTransport(
                                sendTransportListener,
                                params.getString("id"),
                                params.getJSONObject("iceParameters").toString(),
                                params.getJSONArray("iceCandidates").toString(),
                                params.getJSONObject("dtlsParameters").toString()
                            )

                            if (!isGenerationActive(generation)) {
                                newTransport.close()
                                return@launch
                            }
                            sendTransport = newTransport
                            produceMedia(generation)
                        }
                    } catch (e: Exception) {
                        Log.e("WebRTC", "Error in createSendTransport: ${e.message}")
                    }
                }
            }
        })
    }

    private val sendTransportListener = object : SendTransport.Listener {
        override fun onConnect(transport: org.mediasoup.droid.Transport, dtlsParameters: String) {
            if (transport.id != sendTransport?.id || socket?.connected() != true) return

            Log.d("WebRTC", "🔄 Ανταλλαγή κλειδιών DTLS (SendTransport)...")
            val latch = CountDownLatch(1)
            val data = JSONObject().apply {
                put("transportId", transport.id)
                put("dtlsParameters", JSONObject(dtlsParameters))
            }
            socket?.emit("transport-connect", data, object : Ack {
                override fun call(vararg args: Any) {
                    latch.countDown()
                }
            })
            latch.await(5, TimeUnit.SECONDS)
        }

        override fun onProduce(transport: org.mediasoup.droid.Transport, kind: String, rtpParameters: String, appData: String): String {
            if (transport.id != sendTransport?.id || socket?.connected() != true) return ""

            Log.d("WebRTC", "🎬 Ο Server ξεκινά να λαμβάνει $kind...")
            var serverProducerId = ""
            val latch = CountDownLatch(1)
            val data = JSONObject().apply {
                put("transportId", transport.id)
                put("kind", kind)
                put("rtpParameters", JSONObject(rtpParameters))
                put("roomId", roomId)
                put("userId", myUserId)
            }

            socket?.emit("transport-produce", data, object : Ack {
                override fun call(vararg args: Any) {
                    try {
                        val response = args[0] as JSONObject
                        if (response.getBoolean("success")) {
                            serverProducerId = response.getString("id")
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        latch.countDown()
                    }
                }
            })
            latch.await(5, TimeUnit.SECONDS)
            Log.d("WebRTC", "✅ Το $kind ανεβαίνει! Producer ID: $serverProducerId")
            return serverProducerId
        }

        override fun onProduceData(transport: org.mediasoup.droid.Transport, sctpStreamParameters: String, label: String, protocol: String, appData: String): String {
            return ""
        }

        override fun onConnectionStateChange(transport: org.mediasoup.droid.Transport, connectionState: String) {
            Log.d("WebRTC", "SendTransport State: $connectionState")
        }
    }

    // Receive transport
    private fun createRecvTransport(generation: Int) {
        if (!isGenerationActive(generation)) return

        socket?.emit("createWebRtcTransport", JSONObject().put("roomId", roomId), object : Ack {
            override fun call(vararg args: Any) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        if (!isGenerationActive(generation)) return@launch
                        val response = args.firstOrNull() as? JSONObject ?: return@launch
                        if (response.getBoolean("success")) {
                            val params = response.getJSONObject("params")
                            val currentDevice = device ?: return@launch
                            val newTransport = currentDevice.createRecvTransport(
                                recvTransportListener,
                                params.getString("id"),
                                params.getJSONObject("iceParameters").toString(),
                                params.getJSONArray("iceCandidates").toString(),
                                params.getJSONObject("dtlsParameters").toString()
                            )

                            if (!isGenerationActive(generation)) {
                                newTransport.close()
                                return@launch
                            }
                            recvTransport = newTransport

                            socket?.emit("getProducers", JSONObject().put("roomId", roomId), object : Ack {
                                override fun call(vararg args: Any) {
                                    if (!isGenerationActive(generation) || recvTransport?.id != newTransport.id) return
                                    val producers = args.firstOrNull() as? JSONArray ?: return
                                    for (i in 0 until producers.length()) {
                                        val p = producers.getJSONObject(i)
                                        val userId = p.optString("userId", "Unknown")
                                        val isPaused = p.optBoolean("isPaused", false)
                                        val isMuted = p.optBoolean("isMuted", true)
                                        _remoteCameraPaused.update { currentMap ->
                                            val newMap = currentMap.toMutableMap()
                                            newMap[userId] = isPaused
                                            newMap
                                        }
                                        _remoteMicrophoneMuted.update { currentMap ->
                                            currentMap + (userId to isMuted)
                                        }
                                        consumeTrack(
                                            p.getString("producerId"),
                                            userId,
                                            p.optString("username", "Συμμετέχων"),
                                            generation
                                        )
                                    }
                                }
                            })
                        }
                    } catch (e: Exception) {
                        Log.e("WebRTC", "Error in createRecvTransport: ${e.message}")
                    }
                }
            }
        })
    }

    private val recvTransportListener = object : RecvTransport.Listener {
        override fun onConnect(transport: org.mediasoup.droid.Transport, dtlsParameters: String) {
            if (transport.id != recvTransport?.id || socket?.connected() != true) return

            val latch = CountDownLatch(1)
            val data = JSONObject().apply {
                put("transportId", transport.id)
                put("dtlsParameters", JSONObject(dtlsParameters))
            }
            socket?.emit("transport-connect", data, object : Ack {
                override fun call(vararg args: Any) {
                    latch.countDown()
                }
            })
            latch.await(5, TimeUnit.SECONDS)
        }

        override fun onConnectionStateChange(transport: org.mediasoup.droid.Transport, connectionState: String) {
            Log.d("WebRTC", "RecvTransport State: $connectionState")
        }
    }

    // Remote media
    private fun consumeTrack(producerId: String, userId: String, username: String, generation: Int) {
        if (!isGenerationActive(generation)) return
        val receivingTransport = recvTransport ?: return
        val currentDevice = device ?: return
        if (!consumingProducerIds.add(producerId)) return

        val data = JSONObject().apply {
            put("roomId", roomId)
            put("transportId", receivingTransport.id)
            put("producerId", producerId)
            put("rtpCapabilities", JSONObject(currentDevice.rtpCapabilities))
        }

        socket?.emit("transport-consume", data, object : Ack {
            override fun call(vararg args: Any) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        if (!isGenerationActive(generation) || recvTransport?.id != receivingTransport.id) {
                            consumingProducerIds.remove(producerId)
                            return@launch
                        }

                        val response = args.firstOrNull() as? JSONObject ?: run {
                            consumingProducerIds.remove(producerId)
                            return@launch
                        }
                        if (response.getBoolean("success")) {
                            val params = response.getJSONObject("params")

                            val consumer = consumeMutex.withLock {
                                receivingTransport.consume(
                                    { c ->
                                        if (isGenerationActive(generation) && recvTransport?.id == receivingTransport.id) {
                                            consumingProducerIds.remove(producerId)
                                            _remoteVideoTracks.update { it - producerId }
                                        }
                                        Log.d("WebRTC", "Consumer Closed: ${c.id}")
                                    },
                                    params.getString("id"),
                                    params.getString("producerId"),
                                    params.getString("kind"),
                                    params.getJSONObject("rtpParameters").toString()
                                )
                            }

                            if (!isGenerationActive(generation) || recvTransport?.id != receivingTransport.id) {
                                consumer.close()
                                consumingProducerIds.remove(producerId)
                                return@launch
                            }

                            val track = consumer.track
                            if (track is VideoTrack) {
                                Log.d("WebRTC", "📥 Έγινε λήψη VideoTrack! ID: ${track.id()}")
                                val supersededProducerIds = _remoteVideoTracks.value
                                    .filterValues { it.userId == userId }
                                    .keys
                                consumingProducerIds.removeAll(supersededProducerIds)
                                _remoteVideoTracks.update { currentMap ->
                                    val newMap = currentMap
                                        .filterValues { it.userId != userId }
                                        .toMutableMap()
                                    newMap[producerId] = RemoteVideoTrack(track, username, userId)
                                    newMap
                                }
                            } else if (track is AudioTrack) {
                                track.setEnabled(true)
                            }

                            if (!isGenerationActive(generation)) return@launch
                            socket?.emit("consumer-resume", JSONObject().put("consumerId", consumer.id), object : Ack {
                                override fun call(vararg args: Any) {
                                    Log.d("WebRTC", "✅ Η ροή του Consumer ${consumer.id} ξεπάγωσε!")
                                }
                            })
                        } else {
                            consumingProducerIds.remove(producerId)
                        }
                    } catch (e: Exception) {
                        consumingProducerIds.remove(producerId)
                        Log.e("WebRTC", "Error in consumeTrack: ${e.message}")
                    }
                }
            }
        })
    }

    private fun produceMedia(generation: Int) {
        if (!isGenerationActive(generation)) return
        val producingTransport = sendTransport ?: return
        try {
            if (localVideoTrack != null) {
                val encodings = listOf(
                    org.webrtc.RtpParameters.Encoding("q", true, 4.0).apply { maxBitrateBps = 100000 },
                    org.webrtc.RtpParameters.Encoding("h", true, 2.0).apply { maxBitrateBps = 300000 },
                    org.webrtc.RtpParameters.Encoding("f", true, 1.0).apply { maxBitrateBps = 900000 }
                )
                val newVideoProducer = producingTransport.produce(
                    object : Producer.Listener {
                        override fun onTransportClose(producer: Producer) {}
                    },
                    localVideoTrack,
                    encodings, null, null
                )
                if (!isGenerationActive(generation) || sendTransport?.id != producingTransport.id) {
                    newVideoProducer.close()
                    return
                }
                videoProducer = newVideoProducer
            }
            if (localAudioTrack != null) {
                val newAudioProducer = producingTransport.produce(
                    object : Producer.Listener {
                        override fun onTransportClose(producer: Producer) {}
                    },
                    localAudioTrack,
                    null, null, null
                )
                if (!isGenerationActive(generation) || sendTransport?.id != producingTransport.id) {
                    newAudioProducer.close()
                    return
                }
                audioProducer = newAudioProducer
            }
        } catch (e: Exception) {
            Log.e("WebRTC", "❌ Σφάλμα στην παραγωγή βίντεο/ήχου: ${e.message}")
        }
    }

    private fun createCameraCapturer(enumerator: CameraEnumerator): VideoCapturer? {
        val deviceNames = enumerator.deviceNames
        
        val cameraEventsHandler = object : CameraVideoCapturer.CameraEventsHandler {
            override fun onCameraError(errorDescription: String) {
                _cameraErrorFlow.value = "Σφάλμα κάμερας: $errorDescription"
            }
            override fun onCameraDisconnected() {
                _cameraErrorFlow.value = "Η κάμερα αποσυνδέθηκε (ίσως χρησιμοποιείται από άλλη εφαρμογή)."
                pauseCamera()
            }
            override fun onCameraFreezed(errorDescription: String) {
                _cameraErrorFlow.value = "Η κάμερα πάγωσε: $errorDescription"
            }
            override fun onCameraOpening(cameraName: String) {
                _cameraErrorFlow.value = null
            }
            override fun onFirstFrameAvailable() {}
            override fun onCameraClosed() {
                scheduleNativeMediaRelease()
            }
        }

        val preferredDeviceName = currentCameraName.value.takeIf { it in deviceNames }
        if (preferredDeviceName != null) {
            initializeCameraState(preferredDeviceName)
            return enumerator.createCapturer(preferredDeviceName, cameraEventsHandler)
        }

        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                initializeCameraState(deviceName)
                return enumerator.createCapturer(deviceName, cameraEventsHandler)
            }
        }
        for (deviceName in deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                initializeCameraState(deviceName)
                return enumerator.createCapturer(deviceName, cameraEventsHandler)
            }
        }
        return null
    }

    private fun initializeCameraState(deviceName: String) {
        synchronized(cameraStateLock) {
            capturedCameraName = deviceName
            desiredCameraName = deviceName
            capturedResolution = currentResolution.value
            cameraSwitchInProgress = false
        }
        currentCameraName.value = deviceName
    }

    // Cleanup
    fun stopLocalVideo(onStopped: (() -> Unit)? = null) {
        var shouldStartRelease = false
        var callbackAfterCompleted: (() -> Unit)? = null
        synchronized(releaseStateLock) {
            if (nativeMediaReleaseCompleted) {
                callbackAfterCompleted = onStopped
            } else {
                onStopped?.let(stopCallbacks::add)
                if (!isDisposed) {
                    isDisposed = true
                    releaseStartedAtMillis = SystemClock.uptimeMillis()
                    shouldStartRelease = true
                }
            }
        }

        callbackAfterCompleted?.let { callback -> mainHandler.post(callback) }
        if (!shouldStartRelease) return

        mediaGeneration.incrementAndGet()
        try {
            _localVideoTrackFlow.value = null
            _cameraPreviewTrackFlow.value = null
            resetMediaSession()
            socket?.disconnect()
            socket?.off()
            socket = null

            videoCapturer?.stopCapture()
        } catch (e: Exception) {
            Log.e("WebRTC", "Failed while releasing the media session", e)
        } finally {
            releaseAudioRouting()
            if (videoCapturer == null) scheduleNativeMediaRelease()
            mainHandler.postDelayed(::completeNativeMediaRelease, CAMERA_RELEASE_TIMEOUT_MS)
        }
    }

    private fun scheduleNativeMediaRelease() {
        if (!isDisposed) return
        val elapsed = SystemClock.uptimeMillis() - releaseStartedAtMillis
        val remainingRendererDetachDelay = (MIN_RENDERER_DETACH_DELAY_MS - elapsed).coerceAtLeast(0L)
        mainHandler.postDelayed(::completeNativeMediaRelease, remainingRendererDetachDelay)
    }

    private fun completeNativeMediaRelease() {
        val callbacks = synchronized(releaseStateLock) {
            if (nativeMediaReleaseCompleted) return
            nativeMediaReleaseCompleted = true
            stopCallbacks.toList().also { stopCallbacks.clear() }
        }

        try {
            videoCapturer?.dispose()
            videoCapturer = null
            surfaceTextureHelper?.dispose()
            surfaceTextureHelper = null
            localVideoTrack = null
            cameraPreviewTrack = null
            localAudioTrack = null
        } catch (e: Exception) {
            Log.e("WebRTC", "Failed while releasing native media resources", e)
        } finally {
            runCatching { peerConnectionFactory.dispose() }
                .onFailure { Log.w("WebRTC", "Failed to release the peer connection factory", it) }
            callbacks.forEach { callback ->
                runCatching(callback)
                    .onFailure { Log.e("WebRTC", "Failed to finish media shutdown", it) }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun releaseAudioRouting() {
        runCatching { audioManager.unregisterAudioDeviceCallback(audioDeviceCallback) }
            .onFailure { Log.w("WebRTC", "Failed to unregister the audio device callback", it) }

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (communicationDeviceChangedListener as? AudioManager.OnCommunicationDeviceChangedListener)?.let {
                    audioManager.removeOnCommunicationDeviceChangedListener(it)
                }
                communicationDeviceChangedListener = null
                audioManager.clearCommunicationDevice()
            } else {
                audioManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = originalBluetoothScoOn
                audioManager.isSpeakerphoneOn = originalSpeakerphoneOn
            }
            audioManager.mode = originalAudioMode
        }.onFailure {
            Log.w("WebRTC", "Failed to restore the system audio route", it)
        }
    }

    fun setPinnedUser(producerId: String?) {
        try {
            val params = org.json.JSONObject()
            if (producerId != null) {
                params.put("producerId", producerId)
            } else {
                params.put("producerId", org.json.JSONObject.NULL)
            }
            socket?.emit("set-pinned-user", params)
        } catch (e: Exception) {
            Log.e("WebRTC", "Error setting pinned user: ${e.message}")
        }
    }
}
