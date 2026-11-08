package com.nickfouf.classwave.ui

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.Context
import android.graphics.SurfaceTexture
import android.media.AudioDeviceInfo
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.layout.layout
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import org.webrtc.EglBase
import org.webrtc.EglRenderer
import org.webrtc.GlRectDrawer
import org.webrtc.RendererCommon
import org.webrtc.VideoFrame
import org.webrtc.VideoSink
import com.nickfouf.classwave.webrtc.AudioOutputDevice
import com.nickfouf.classwave.webrtc.CameraDevice
import com.nickfouf.classwave.webrtc.MeetingMediaSettings
import com.nickfouf.classwave.webrtc.WebRtcSessionManager
import com.nickfouf.classwave.network.ChatMessage


val DangerRed = Color(0xFFEF4444)
val ActiveGreen = Color(0xFF10B981)
val WarningYellow = Color(0xFFF59E0B)

data class ParticipantData(
    val id: String,
    val userId: String,
    val name: String,
    val isLocal: Boolean,
    val isTeacher: Boolean,
    val isMuted: Boolean,
    val hasVideo: Boolean,
    val track: VideoTrack?,
    val isPaused: Boolean = false,
    val isHandRaised: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoRoomScreen(
    token: String,
    roomId: String,
    initialMicOn: Boolean = false,
    initialCamOn: Boolean = false,
    initialMediaSettings: MeetingMediaSettings? = null,
    promptHangupEvent: Boolean = false,
    onPromptHandled: () -> Unit = {},
    profileViewModel: ProfileViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    courseViewModel: CourseViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    chatViewModel: ChatViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onLeaveRoom: () -> Unit
) {
    val profileState by profileViewModel.profileState.collectAsState()
    val courseState by courseViewModel.courseState.collectAsState()

    val course = (courseState as? CourseState.Success)?.courses?.find { it._id == roomId }
    val courseName = course?.name ?: "Live Class"
    val teacherName = course?.teacher?.let { "${it.firstName} ${it.lastName}" } ?: ""
    val teacherId = course?.teacher?._id

    if (profileState !is ProfileState.Success) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    val successState = profileState as ProfileState.Success
    val localName = "${successState.firstName} ${successState.lastName}"
    val role = successState.role
    val localUserId = successState.id

    BackHandler {
        onLeaveRoom()
    }

    val context = LocalContext.current
    val webRtcManager = remember(roomId, initialMediaSettings) {
        WebRtcSessionManager(
            context = context,
            roomId = roomId,
            myUserId = localUserId,
            localUserName = localName,
            initialMediaSettings = initialMediaSettings
        )
    }

    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? android.app.Activity)?.window
        if (window != null) {
            val originalColor = window.navigationBarColor
            val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, view)
            val originalLightNavBars = insetsController.isAppearanceLightNavigationBars

            window.navigationBarColor = android.graphics.Color.parseColor("#0F172A")
            insetsController.isAppearanceLightNavigationBars = false

            onDispose {
                window.navigationBarColor = originalColor
                insetsController.isAppearanceLightNavigationBars = originalLightNavBars
            }
        } else {
            onDispose {}
        }
    }

    var isMicOn by remember { mutableStateOf(initialMicOn) }
    var isCamOn by remember { mutableStateOf(initialCamOn) }
    var showTeacherEndDialog by remember { mutableStateOf(false) }
    var showMediaSettings by remember { mutableStateOf(false) }
    var showParticipantsSheet by remember { mutableStateOf(false) }
    val isClassEnded by webRtcManager.classEnded.collectAsState()

    var showChatSheet by remember { mutableStateOf(false) }
    val isChatSheetVisible by rememberUpdatedState(showChatSheet)
    val unreadChatCount by chatViewModel.unreadCount.collectAsState()
    val chatMessages by chatViewModel.messages.collectAsState()
    val isLoadingMore by chatViewModel.isLoading.collectAsState()

    LaunchedEffect(roomId) {
        chatViewModel.loadInitialMessages(token, roomId)
    }

    LaunchedEffect(webRtcManager) {
        webRtcManager.chatMessagesFlow.collect { message ->
            chatViewModel.addIncomingMessage(message, isChatVisible = isChatSheetVisible)
        }
    }

    LaunchedEffect(showChatSheet) {
        if (showChatSheet) {
            chatViewModel.markChatAsRead()
        }
    }

    LaunchedEffect(promptHangupEvent) {
        if (promptHangupEvent) {
            if (role == "teacher") {
                showTeacherEndDialog = true
            } else {
                onLeaveRoom()
            }
            onPromptHandled()
        }
    }

    var hasPermissions by remember { mutableStateOf(false) }

    LaunchedEffect(isClassEnded) {
        if (isClassEnded) {
            chatViewModel.clearMessages(roomId)
            webRtcManager.stopLocalVideo()
        }
    }

    LaunchedEffect(courseName) {
        com.nickfouf.classwave.utils.NotificationManagerHelper.showCallNotification(context, courseName, roomId)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermissions = permissions[Manifest.permission.CAMERA] == true &&
                permissions[Manifest.permission.RECORD_AUDIO] == true
    }

    LaunchedEffect(Unit) {
        val camPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        val micPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
        if (camPermission == PackageManager.PERMISSION_GRANTED && micPermission == PackageManager.PERMISSION_GRANTED) {
            hasPermissions = true
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        }
    }

    LaunchedEffect(hasPermissions) {
        if (hasPermissions) {
            webRtcManager.startLocalVideo(connectImmediately = true)
            webRtcManager.toggleAudio(isMicOn)
            webRtcManager.toggleVideo(isCamOn)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                if (isCamOn) webRtcManager.pauseCamera()
            } else if (event == Lifecycle.Event.ON_RESUME) {
                if (isCamOn) webRtcManager.resumeCamera()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            com.nickfouf.classwave.utils.NotificationManagerHelper.cancelCallNotification(context)
            webRtcManager.stopLocalVideo()
        }
    }

    val remoteTracks by webRtcManager.remoteVideoTracks.collectAsState()
    val localTrack by webRtcManager.localVideoTrackFlow.collectAsState()
    val remoteCameraPaused by webRtcManager.remoteCameraPaused.collectAsState()
    val remoteMicrophoneMuted by webRtcManager.remoteMicrophoneMuted.collectAsState()
    val cameraError by webRtcManager.cameraErrorFlow.collectAsState()
    val activeSpeakerUserId by webRtcManager.activeSpeakerUserId.collectAsState()
    val raisedHands by webRtcManager.raisedHands.collectAsState()
    val canRaiseHand = role != "teacher"
    val isHandRaised = canRaiseHand && raisedHands.containsKey(localUserId)

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(cameraError) {
        cameraError?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    LaunchedEffect(webRtcManager) {
        webRtcManager.endClassErrors.collect {
            snackbarHostState.showSnackbar(
                context.getString(com.nickfouf.classwave.R.string.end_class_failed)
            )
        }
    }

    LaunchedEffect(webRtcManager) {
        webRtcManager.handRaisedEvents.collect { raisedHand ->
            snackbarHostState.showSnackbar(
                context.getString(com.nickfouf.classwave.R.string.hand_raised_announcement, raisedHand.username)
            )
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val participants = remember(
        remoteTracks,
        localTrack,
        isMicOn,
        isCamOn,
        localName,
        localUserId,
        role,
        teacherId,
        remoteCameraPaused,
        remoteMicrophoneMuted,
        raisedHands
    ) {
        val list = mutableListOf<ParticipantData>()
        list.add(
            ParticipantData(
                id = "local",
                userId = localUserId,
                name = localName,
                isLocal = true,
                isTeacher = role == "teacher" || localUserId == teacherId,
                isMuted = !isMicOn,
                hasVideo = isCamOn,
                track = localTrack,
                isPaused = false,
                isHandRaised = role != "teacher" && raisedHands.containsKey(localUserId)
            )
        )
        remoteTracks.forEach { (producerId, remoteData) ->
            val isPaused = remoteCameraPaused[remoteData.userId] == true
            list.add(
                ParticipantData(
                    id = producerId,
                    userId = remoteData.userId,
                    name = remoteData.username,
                    isLocal = false,
                    isTeacher = remoteData.userId == teacherId,
                    isMuted = remoteMicrophoneMuted[remoteData.userId] ?: true,
                    hasVideo = !isPaused,
                    track = remoteData.track,
                    isPaused = isPaused,
                    isHandRaised = remoteData.userId != teacherId && raisedHands.containsKey(remoteData.userId)
                )
            )
        }
        list
    }

    var pinnedParticipantId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(participants.map { it.id }) {
        if (pinnedParticipantId != null && participants.none { it.id == pinnedParticipantId }) {
            pinnedParticipantId = null
        }
    }

    val activeSpeakerParticipantId = participants
        .firstOrNull { it.userId == activeSpeakerUserId }
        ?.id
    val defaultParticipantId = if (role == "teacher") {
        "local"
    } else {
        participants.firstOrNull { it.isTeacher }?.id
            ?: participants.firstOrNull { !it.isLocal }?.id
            ?: "local"
    }
    val focusedParticipantId = pinnedParticipantId
        ?: activeSpeakerParticipantId
        ?: defaultParticipantId

    LaunchedEffect(focusedParticipantId) {
        val videoProducerId = focusedParticipantId.takeUnless { it == "local" }
        webRtcManager.setPinnedUser(videoProducerId)
    }

    val focusedParticipant = participants.find { it.id == focusedParticipantId } ?: participants.firstOrNull()
    val otherParticipants = participants.filter { it.id != focusedParticipantId }

    val flipHorizontal by webRtcManager.flipHorizontal.collectAsState()
    val flipVertical by webRtcManager.flipVertical.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = courseName,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        if (teacherName.isNotBlank()) {
                            Text(
                                text = teacherName,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showMediaSettings = true }) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = stringResource(com.nickfouf.classwave.R.string.media_settings),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    IconButton(onClick = { showChatSheet = true }) {
                        BadgedBox(
                            badge = {
                                if (unreadChatCount > 0) {
                                    Badge(containerColor = DangerRed) {
                                        Text(unreadChatCount.toString(), color = Color.White)
                                    }
                                }
                            }
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Chat,
                                contentDescription = "Chat",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                    IconButton(onClick = { showParticipantsSheet = true }) {
                        Icon(Icons.Filled.People, contentDescription = "Συμμετέχοντες", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        bottomBar = {
            if (!isClassEnded && !isLandscape) {
                ControlBar(
                    isMicOn = isMicOn,
                    isCamOn = isCamOn,
                    isHandRaised = isHandRaised,
                    canRaiseHand = canRaiseHand,
                    onToggleMic = {
                        isMicOn = !isMicOn
                        webRtcManager.toggleAudio(isMicOn)
                    },
                    onToggleCam = {
                        isCamOn = !isCamOn
                        webRtcManager.toggleVideo(isCamOn)
                    },
                    onToggleHand = { webRtcManager.setHandRaised(!isHandRaised) },
                    onLeave = {
                        if (role == "teacher") showTeacherEndDialog = true else onLeaveRoom()
                    },
                    isLandscape = false
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (!isClassEnded) {
                if (isLandscape) {
                    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        focusedParticipant?.let {
                            BoxWithConstraints(
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                val targetAspectRatio = 16f / 9f
                                val parentAspectRatio = if (maxHeight > 0.dp) maxWidth / maxHeight else targetAspectRatio
                                val childModifier = if (parentAspectRatio > targetAspectRatio) {
                                    Modifier.fillMaxHeight().aspectRatio(targetAspectRatio)
                                } else {
                                    Modifier.fillMaxWidth().aspectRatio(targetAspectRatio)
                                }
                                ParticipantView(
                                    participant = it,
                                    eglBaseContext = webRtcManager.eglBaseContext,
                                    isPinned = pinnedParticipantId == it.id,
                                    isActiveSpeaker = activeSpeakerUserId == it.userId,
                                    modifier = childModifier,
                                    onPin = { pinnedParticipantId = it.id },
                                    onUnpin = { pinnedParticipantId = null },
                                    flipHorizontal = flipHorizontal,
                                    flipVertical = flipVertical
                                )
                            }
                        }

                        if (otherParticipants.isNotEmpty()) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(1),
                                modifier = Modifier.width(160.dp).fillMaxHeight(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(otherParticipants, key = { it.id }) { participant ->
                                    ParticipantView(
                                        participant = participant,
                                        eglBaseContext = webRtcManager.eglBaseContext,
                                        isPinned = false,
                                        isActiveSpeaker = activeSpeakerUserId == participant.userId,
                                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                                        onPin = { pinnedParticipantId = participant.id },
                                        flipHorizontal = flipHorizontal,
                                        flipVertical = flipVertical
                                    )
                                }
                            }
                        }

                        ControlBar(
                            isMicOn = isMicOn,
                            isCamOn = isCamOn,
                            isHandRaised = isHandRaised,
                            canRaiseHand = canRaiseHand,
                            onToggleMic = {
                                isMicOn = !isMicOn
                                webRtcManager.toggleAudio(isMicOn)
                            },
                            onToggleCam = {
                                isCamOn = !isCamOn
                                webRtcManager.toggleVideo(isCamOn)
                            },
                            onToggleHand = { webRtcManager.setHandRaised(!isHandRaised) },
                            onLeave = {
                                if (role == "teacher") showTeacherEndDialog = true else onLeaveRoom()
                            },
                            isLandscape = true
                        )
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        focusedParticipant?.let {
                            BoxWithConstraints(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                val targetAspectRatio = 9f / 16f
                                val parentAspectRatio = if (maxHeight > 0.dp) maxWidth / maxHeight else targetAspectRatio
                                val childModifier = if (parentAspectRatio > targetAspectRatio) {
                                    Modifier.fillMaxHeight().aspectRatio(targetAspectRatio)
                                } else {
                                    Modifier.fillMaxWidth().aspectRatio(targetAspectRatio)
                                }
                                ParticipantView(
                                    participant = it,
                                    eglBaseContext = webRtcManager.eglBaseContext,
                                    isPinned = pinnedParticipantId == it.id,
                                    isActiveSpeaker = activeSpeakerUserId == it.userId,
                                    modifier = childModifier,
                                    onPin = { pinnedParticipantId = it.id },
                                    onUnpin = { pinnedParticipantId = null },
                                    flipHorizontal = flipHorizontal,
                                    flipVertical = flipVertical
                                )
                            }
                        }

                        if (otherParticipants.isNotEmpty()) {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth().height(140.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(otherParticipants, key = { it.id }) { participant ->
                                    ParticipantView(
                                        participant = participant,
                                        eglBaseContext = webRtcManager.eglBaseContext,
                                        isPinned = false,
                                        isActiveSpeaker = activeSpeakerUserId == participant.userId,
                                        modifier = Modifier.fillMaxHeight().aspectRatio(9f / 16f),
                                        onPin = { pinnedParticipantId = participant.id },
                                        flipHorizontal = flipHorizontal,
                                        flipVertical = flipVertical
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .pointerInput(Unit) {},
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .background(DangerRed.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CallEnd,
                                contentDescription = null,
                                tint = DangerRed,
                                modifier = Modifier.size(50.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.nickfouf.classwave.R.string.class_ended_title),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.nickfouf.classwave.R.string.class_ended_message),
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(40.dp))
                        Button(
                            onClick = onLeaveRoom,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text(androidx.compose.ui.res.stringResource(com.nickfouf.classwave.R.string.return_to_dashboard), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (showChatSheet) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
            val density = androidx.compose.ui.platform.LocalDensity.current
            val statusBarHeight = WindowInsets.statusBars.getTop(density)
            val navBarHeight = WindowInsets.navigationBars.getBottom(density)
            val isTablet = configuration.screenWidthDp >= 600

            ModalBottomSheet(
                onDismissRequest = { showChatSheet = false },
                sheetState = sheetState,
                containerColor = Color.Transparent,
                dragHandle = null
            ) {
                val sheetScope = rememberCoroutineScope()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .layout { measurable, constraints ->
                            val offset = try { sheetState.requireOffset() } catch (e: Exception) { 0f }
                            val maxH = if (constraints.hasBoundedHeight) constraints.maxHeight else 10000

                            val topMarginPx = if (isTablet) 0 else statusBarHeight
                            val smallHeightPx = 140.dp.roundToPx() + navBarHeight

                            val partialOffset = maxH / 2f
                            val fraction = (offset / partialOffset).coerceIn(0f, 1f)

                            val expandedHeight = maxH - topMarginPx
                            val targetHeight = ((1f - fraction) * expandedHeight + fraction * smallHeightPx).roundToInt().coerceIn(0, maxH)

                            val placeable = measurable.measure(constraints.copy(minHeight = targetHeight, maxHeight = targetHeight))

                            val effectiveOffset = offset.coerceAtMost(partialOffset)
                            val yPosition = (maxH - effectiveOffset - targetHeight).roundToInt().coerceAtLeast(0)

                            layout(placeable.width, maxH) {
                                placeable.placeRelative(0, yPosition)
                            }
                        }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 16.dp)
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(bottom = 16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(32.dp)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                            )
                        }
                        Text(
                            text = "Chat",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                        val listState = androidx.compose.foundation.lazy.rememberLazyListState()

                        val shouldLoadMore by remember {
                            derivedStateOf {
                                val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
                                lastVisibleItem != null && lastVisibleItem.index >= chatMessages.size - 3
                            }
                        }

                        LaunchedEffect(shouldLoadMore) {
                            if (shouldLoadMore) {
                                chatViewModel.loadMoreMessages(token)
                            }
                        }

                        var unreadWhileScrolled by remember { mutableIntStateOf(0) }
                        var previousFirstMessageId by remember { mutableStateOf(chatMessages.firstOrNull()?._id) }

                        LaunchedEffect(chatMessages) {
                            val currentFirstId = chatMessages.firstOrNull()?._id
                            if (currentFirstId != previousFirstMessageId && previousFirstMessageId != null) {
                                val newItemsCount = chatMessages.indexOfFirst { it._id == previousFirstMessageId }
                                if (newItemsCount > 0) {
                                    if (listState.firstVisibleItemIndex <= newItemsCount + 1) {
                                        listState.animateScrollToItem(0)
                                        unreadWhileScrolled = 0
                                    } else {
                                        unreadWhileScrolled += newItemsCount
                                    }
                                }
                            }
                            previousFirstMessageId = currentFirstId
                        }

                        LaunchedEffect(listState.firstVisibleItemIndex) {
                            if (listState.firstVisibleItemIndex == 0) {
                                unreadWhileScrolled = 0
                            }
                        }

                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            androidx.compose.foundation.lazy.LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                reverseLayout = true,
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                itemsIndexed(chatMessages, key = { _, it -> it._id }) { index, msg ->
                                    val timeString = remember(msg.timestamp) { formatTimestampToHHmm(msg.timestamp) }

                                    val olderMsg = chatMessages.getOrNull(index + 1)
                                    val newerMsg = chatMessages.getOrNull(index - 1)

                                    val olderTimeString = remember(olderMsg?.timestamp) { olderMsg?.timestamp?.let { formatTimestampToHHmm(it) } }
                                    val newerTimeString = remember(newerMsg?.timestamp) { newerMsg?.timestamp?.let { formatTimestampToHHmm(it) } }

                                    val isSameGroupAsOlder = olderMsg?.username == msg.username && olderTimeString == timeString
                                    val isSameGroupAsNewer = newerMsg?.username == msg.username && newerTimeString == timeString

                                    val showName = !isSameGroupAsOlder
                                    val showTime = !isSameGroupAsNewer

                                    val topPadding = if (showName) 8.dp else 2.dp

                                    ChatBubble(
                                        message = msg,
                                        isLocal = msg.username == localName,
                                        showName = showName,
                                        showTime = showTime,
                                        timeString = timeString,
                                        modifier = Modifier.padding(top = topPadding)
                                    )
                                }
                                if (isLoadingMore) {
                                    item {
                                        Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                        }
                                    }
                                }
                            }

                            val showFloatingButton by remember { derivedStateOf { listState.firstVisibleItemIndex > 2 || unreadWhileScrolled > 0 } }

                            androidx.compose.animation.AnimatedVisibility(
                                visible = showFloatingButton,
                                enter = fadeIn() + scaleIn(),
                                exit = fadeOut() + scaleOut(),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = 8.dp, bottom = 8.dp)
                            ) {
                                SmallFloatingActionButton(
                                    onClick = {
                                        sheetScope.launch { listState.animateScrollToItem(0) }
                                    },
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    shape = CircleShape
                                ) {
                                    if (unreadWhileScrolled > 0) {
                                        BadgedBox(
                                            badge = {
                                                Badge(
                                                    containerColor = DangerRed,
                                                    contentColor = Color.White
                                                ) {
                                                    Text(text = unreadWhileScrolled.toString())
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Scroll to bottom")
                                        }
                                    } else {
                                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Scroll to bottom")
                                    }
                                }
                            }
                        }

                        var messageText by remember { mutableStateOf("") }
                        var isSendingMessage by remember { mutableStateOf(false) }
                        var sendMessageError by remember { mutableStateOf<String?>(null) }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = messageText,
                                onValueChange = {
                                    messageText = it
                                    sendMessageError = null
                                },
                                placeholder = { Text("Μήνυμα...") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(24.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                                ),
                                maxLines = 3
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    val outgoingMessage = messageText.trim()
                                    if (outgoingMessage.isNotEmpty() && !isSendingMessage) {
                                        isSendingMessage = true
                                        sendMessageError = null
                                        webRtcManager.sendChatMessage(outgoingMessage) { result ->
                                            isSendingMessage = false
                                            if (result.success) {
                                                if (messageText.trim() == outgoingMessage) {
                                                    messageText = ""
                                                }
                                                sheetScope.launch { listState.animateScrollToItem(0) }
                                            } else {
                                                sendMessageError = result.errorMessage
                                            }
                                        }
                                    }
                                },
                                enabled = messageText.isNotBlank() && !isSendingMessage,
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                            ) {
                                if (isSendingMessage) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White)
                                }
                            }
                        }
                        sendMessageError?.let { error ->
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        if (showParticipantsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showParticipantsSheet = false },
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                dragHandle = { BottomSheetDefaults.DragHandle() }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Text(
                        text = "Συμμετέχοντες (${participants.size})",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(8.dp))

                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            participants.sortedWith(
                                compareByDescending<ParticipantData> { it.isHandRaised }
                                    .thenByDescending { it.isTeacher }
                                    .thenByDescending { it.isLocal }
                                    .thenBy { it.name }
                            ),
                            key = { it.id }
                        ) { participant ->
                            ParticipantListItem(participant = participant)
                        }
                    }
                }
            }
        }

        if (showTeacherEndDialog) {
            AlertDialog(
                onDismissRequest = { showTeacherEndDialog = false },
                title = { Text(androidx.compose.ui.res.stringResource(com.nickfouf.classwave.R.string.end_class_title), fontWeight = FontWeight.Bold, color = DangerRed) },
                text = { Text(androidx.compose.ui.res.stringResource(com.nickfouf.classwave.R.string.end_class_message)) },
                confirmButton = {
                    Button(
                        onClick = {
                            showTeacherEndDialog = false
                            webRtcManager.endClassForEveryone(token)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                    ) {
                        Text(androidx.compose.ui.res.stringResource(com.nickfouf.classwave.R.string.yes), color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showTeacherEndDialog = false }) {
                        Text(androidx.compose.ui.res.stringResource(com.nickfouf.classwave.R.string.no))
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface
            )
        }

        if (showMediaSettings) {
            MediaSettingsDialog(
                webRtcManager = webRtcManager,
                onDismissRequest = { showMediaSettings = false }
            )
        }
    }
}

fun formatTimestampToHHmm(timestamp: String): String {
    return try {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.getDefault())
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        val date = sdf.parse(timestamp)
        val outputFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        outputFormat.timeZone = java.util.TimeZone.getDefault()
        date?.let { outputFormat.format(it) } ?: ""
    } catch (e: Exception) {
        ""
    }
}

fun getInitialsFromName(name: String): String {
    val nameWithoutTrailingLabel = name
        .replace(Regex("""\s*\([^)]*\)\s*$"""), "")
        .trim()
    val initials = nameWithoutTrailingLabel
        .split(Regex("""\s+"""))
        .mapNotNull { part -> part.firstOrNull(Char::isLetterOrDigit) }

    if (initials.isEmpty()) return ""
    return buildString {
        append(initials.first())
        if (initials.size > 1) append(initials.last())
    }.uppercase()
}

@Composable
fun ChatBubble(
    message: ChatMessage,
    isLocal: Boolean,
    showName: Boolean,
    showTime: Boolean,
    timeString: String,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isLocal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isLocal) Color.White else MaterialTheme.colorScheme.onSurface
    val alignment = if (isLocal) Alignment.End else Alignment.Start
    val shape = if (isLocal) {
        RoundedCornerShape(16.dp, 16.dp, if (showTime) 0.dp else 16.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, if (showTime) 0.dp else 16.dp)
    }

    val initials = remember(message.username) { getInitialsFromName(message.username) }

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalAlignment = alignment
    ) {
        if (showName) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 4.dp, start = 2.dp, end = 2.dp)
            ) {
                if (isLocal) {
                    Text(
                        text = message.username,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        AutoSizeText(initials, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    Box(
                        modifier = Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        AutoSizeText(initials, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = message.username,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .clip(shape)
                .background(backgroundColor)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = message.text,
                color = textColor,
                fontSize = 15.sp
            )
        }
        if (showTime) {
            Text(
                text = timeString,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)
            )
        }
    }
}

@Composable
fun ParticipantView(
    participant: ParticipantData,
    eglBaseContext: EglBase.Context,
    isPinned: Boolean,
    isActiveSpeaker: Boolean,
    modifier: Modifier = Modifier,
    onPin: () -> Unit,
    onUnpin: () -> Unit = {},
    flipHorizontal: Boolean = true,
    flipVertical: Boolean = false
) {
    var showPinHint by remember(participant.id) { mutableStateOf(false) }

    LaunchedEffect(showPinHint) {
        if (showPinHint) {
            delay(1000)
            showPinHint = false
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (isPinned || participant.isTeacher || isActiveSpeaker) 2.dp else 0.dp,
                color = when {
                    isPinned -> MaterialTheme.colorScheme.primary
                    participant.isTeacher -> MaterialTheme.colorScheme.primary
                    isActiveSpeaker -> ActiveGreen
                    else -> Color.Transparent
                },
                shape = RoundedCornerShape(12.dp)
            )
            .then(
                if (isPinned) {
                    Modifier
                } else {
                    Modifier.pointerInput(participant.id) {
                        detectTapGestures(onTap = {
                            if (showPinHint) {
                                showPinHint = false
                                onPin()
                            } else {
                                showPinHint = true
                            }
                        })
                    }
                }
            )
    ) {
        if (participant.track != null && participant.hasVideo && !participant.isPaused) {
            SafeVideoRenderer(
                videoTrack = participant.track,
                eglBaseContext = eglBaseContext,
                name = participant.name,
                isMuted = participant.isMuted,
                modifier = Modifier.fillMaxSize(),
                isLocal = participant.isLocal,
                flipHorizontal = if (participant.isLocal) flipHorizontal else false,
                flipVertical = if (participant.isLocal) flipVertical else false
            )
        } else {
            VideoPlaceholder(name = participant.name, isMuted = participant.isMuted, modifier = Modifier.fillMaxSize())
        }


        if (participant.isHandRaised) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = if (isPinned) 44.dp else 8.dp)
                    .size(28.dp)
                    .background(WarningYellow, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PanTool,
                    contentDescription = androidx.compose.ui.res.stringResource(com.nickfouf.classwave.R.string.hand_raised),
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }


        if (isPinned) {
            IconButton(
                onClick = onUnpin,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(48.dp),
                colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Transparent)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(Color.Black.copy(alpha = 0.62f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PushPin,
                        contentDescription = "Unpin participant",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        if (participant.isTeacher) {
            TeacherBadge(
                isActiveSpeaker = isActiveSpeaker,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            )
        } else if (isActiveSpeaker) {
            Icon(
                imageVector = Icons.Filled.GraphicEq,
                contentDescription = "Active speaker",
                tint = ActiveGreen,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .size(20.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .padding(3.dp)
            )
        }

        AnimatedVisibility(
            visible = showPinHint,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.PushPin,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Πατήστε ξανά\nγια Pin",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun TeacherBadge(
    isActiveSpeaker: Boolean,
    modifier: Modifier = Modifier
) {
    val roleLabel = stringResource(com.nickfouf.classwave.R.string.role_teacher)
    val labelStyle = TextStyle(
        color = MaterialTheme.colorScheme.onPrimary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold
    )
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelWidth = with(density) {
        textMeasurer.measure(
            text = AnnotatedString(roleLabel),
            style = labelStyle,
            maxLines = 1
        ).size.width.toDp()
    }

    BoxWithConstraints(modifier = modifier) {
        val activeSpeakerWidth = if (isActiveSpeaker) 20.dp else 0.dp
        val fullBadgeWidth = 18.dp + 15.dp + 5.dp + labelWidth + activeSpeakerWidth
        val showLabel = maxWidth >= fullBadgeWidth

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.94f))
                .padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.School,
                contentDescription = if (showLabel) null else roleLabel,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(15.dp)
            )
            if (showLabel) {
                Text(
                    text = roleLabel,
                    maxLines = 1,
                    softWrap = false,
                    style = labelStyle
                )
            }
            if (isActiveSpeaker) {
                Icon(
                    imageVector = Icons.Filled.GraphicEq,
                    contentDescription = "Active speaker",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

@Composable
fun SafeVideoRenderer(
    videoTrack: VideoTrack?,
    eglBaseContext: EglBase.Context,
    name: String,
    isMuted: Boolean,
    modifier: Modifier = Modifier,
    isLocal: Boolean = false,
    flipHorizontal: Boolean = true,
    flipVertical: Boolean = false,
    showOverlay: Boolean = true,
    cornerRadius: androidx.compose.ui.unit.Dp = 12.dp
) {
    var currentTrack by remember { mutableStateOf<VideoTrack?>(null) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                SurfaceViewRenderer(ctx).apply {
                    setEnableHardwareScaler(true)
                    setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                    init(eglBaseContext, null)
                    if (isLocal) setMirror(flipHorizontal)
                }
            },
            update = { view ->
                if (isLocal) {
                    view.setMirror(flipHorizontal)
                }
                if (currentTrack != videoTrack) {
                    currentTrack?.removeSink(view)
                    videoTrack?.addSink(view)
                    currentTrack = videoTrack
                }
            },
            onRelease = { view ->
                currentTrack?.removeSink(view)
                view.release()
            },
            modifier = Modifier.fillMaxSize()
        )

        if (showOverlay) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                CustomMarqueeText(
                    text = name,
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                if (isMuted) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Filled.MicOff, contentDescription = "Muted", tint = DangerRed, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

private class ClippedWebRtcTextureView(
    context: Context,
    sharedContext: EglBase.Context
) : TextureView(context), TextureView.SurfaceTextureListener, VideoSink {
    private val eglRenderer = EglRenderer("ClippedWebRtcTextureView")
    private var attachedTrack: VideoTrack? = null

    @Volatile
    private var isReleased = false

    init {
        surfaceTextureListener = this
        isOpaque = true
        eglRenderer.init(sharedContext, EglBase.CONFIG_PLAIN, GlRectDrawer())
    }

    fun setVideoTrack(track: VideoTrack?) {
        if (attachedTrack == track) return
        attachedTrack?.removeSink(this)
        attachedTrack = track
        track?.addSink(this)
    }

    fun setMirroring(horizontal: Boolean, vertical: Boolean) {
        eglRenderer.setMirror(horizontal)
        eglRenderer.setMirrorVertically(vertical)
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        if (isReleased) return
        eglRenderer.createEglSurface(surface)
        updateLayoutAspectRatio(width, height)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        if (!isReleased) updateLayoutAspectRatio(width, height)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        if (isReleased) return true
        eglRenderer.releaseEglSurface { surface.release() }
        return false
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    override fun onFrame(frame: VideoFrame) {
        if (!isReleased) eglRenderer.onFrame(frame)
    }

    fun releaseRenderer() {
        if (isReleased) return
        isReleased = true
        attachedTrack?.removeSink(this)
        attachedTrack = null
        eglRenderer.release()
    }

    private fun updateLayoutAspectRatio(width: Int, height: Int) {
        if (width > 0 && height > 0) {
            eglRenderer.setLayoutAspectRatio(width.toFloat() / height.toFloat())
        }
    }
}

@Composable
private fun ClippedVideoRenderer(
    videoTrack: VideoTrack,
    eglBaseContext: EglBase.Context,
    flipHorizontal: Boolean,
    flipVertical: Boolean,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            ClippedWebRtcTextureView(context, eglBaseContext).apply {
                setMirroring(flipHorizontal, flipVertical)
                setVideoTrack(videoTrack)
            }
        },
        update = { renderer ->
            renderer.setMirroring(flipHorizontal, flipVertical)
            renderer.setVideoTrack(videoTrack)
        },
        onRelease = { renderer -> renderer.releaseRenderer() },
        modifier = modifier
    )
}

@Composable
fun VideoPlaceholder(name: String, isMuted: Boolean, modifier: Modifier = Modifier) {
    val initials = remember(name) { getInitialsFromName(name) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.1f)),
            contentAlignment = Alignment.Center
        ) {
            AutoSizeText(text = initials, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.4f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            CustomMarqueeText(
                text = name,
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )
            if (isMuted) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Filled.MicOff, contentDescription = "Muted", tint = DangerRed, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
fun ControlBar(
    isMicOn: Boolean,
    isCamOn: Boolean,
    isHandRaised: Boolean,
    canRaiseHand: Boolean,
    onToggleMic: () -> Unit,
    onToggleCam: () -> Unit,
    onToggleHand: () -> Unit,
    onLeave: () -> Unit,
    isLandscape: Boolean = false
) {
    val buttons = @Composable {
        ControlButton(
            icon = if (isMicOn) Icons.Filled.Mic else Icons.Filled.MicOff,
            isActive = isMicOn,
            onClick = onToggleMic,
            activeColor = ActiveGreen
        )
        ControlButton(
            icon = if (isCamOn) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
            isActive = isCamOn,
            onClick = onToggleCam,
            activeColor = ActiveGreen
        )
        if (canRaiseHand) {
            ControlButton(
                icon = Icons.Filled.PanTool,
                isActive = isHandRaised,
                onClick = onToggleHand,
                activeColor = WarningYellow,
                contentDescription = androidx.compose.ui.res.stringResource(
                    if (isHandRaised) com.nickfouf.classwave.R.string.lower_hand
                    else com.nickfouf.classwave.R.string.raise_hand
                )
            )
        }
        FloatingActionButton(
            onClick = onLeave,
            containerColor = DangerRed,
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(Icons.Filled.CallEnd, contentDescription = "Αποχώρηση")
        }
    }

    if (isLandscape) {
        Box(
            modifier = Modifier.fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .padding(vertical = 16.dp, horizontal = 4.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                    .padding(vertical = 16.dp, horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                buttons()
            }
        }
    } else {
        Box(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                    .padding(vertical = 12.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                buttons()
            }
        }
    }
}

@Composable
fun ControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean,
    onClick: () -> Unit,
    activeColor: Color,
    contentDescription: String? = null
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (isActive) activeColor.copy(alpha = 0.2f) else Color.Transparent)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isActive) activeColor else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun SplitControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean,
    onToggleClick: () -> Unit,
    onSettingsClick: () -> Unit,
    activeColor: Color
) {
    val backgroundColor = if (isActive) activeColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    val contentColor = if (isActive) activeColor else MaterialTheme.colorScheme.onSurface
    val dividerColor = if (isActive) activeColor.copy(alpha = 0.3f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)

    Row(
        modifier = Modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(backgroundColor),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .clickable { onToggleClick() }
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
        }

        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight(0.6f)
                .background(dividerColor)
        )

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .clickable { onSettingsClick() }
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = "Settings",
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun ParticipantListItem(participant: ParticipantData) {
    val initials = remember(participant.name) { getInitialsFromName(participant.name) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    MaterialTheme.colorScheme.primary.copy(
                        alpha = if (participant.isTeacher) 0.22f else 0.15f
                    )
                )
                .then(
                    if (participant.isTeacher) {
                        Modifier.border(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                            CircleShape
                        )
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (participant.isTeacher) {
                Icon(
                    imageVector = Icons.Filled.School,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                AutoSizeText(
                    text = initials,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = participant.name,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                fontSize = 16.sp
            )
            if (participant.isTeacher) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(com.nickfouf.classwave.R.string.role_teacher),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                    if (participant.isLocal) {
                        Text(
                            text = "• Εγώ",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }
            } else if (participant.isLocal) {
                Text(
                    text = "Εγώ",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        if (participant.isHandRaised) {
            Icon(
                imageVector = Icons.Filled.PanTool,
                contentDescription = androidx.compose.ui.res.stringResource(com.nickfouf.classwave.R.string.hand_raised),
                tint = WarningYellow,
                modifier = Modifier
                    .size(28.dp)
                    .background(WarningYellow.copy(alpha = 0.12f), CircleShape)
                    .padding(6.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Icon(
            imageVector = if (participant.isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
            contentDescription = if (participant.isMuted) "Muted" else "Mic On",
            tint = if (participant.isMuted) DangerRed else ActiveGreen,
            modifier = Modifier
                .size(28.dp)
                .background(if (participant.isMuted) DangerRed.copy(alpha=0.1f) else ActiveGreen.copy(alpha=0.1f), CircleShape)
                .padding(6.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = if (participant.hasVideo) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
            contentDescription = if (participant.hasVideo) "Video On" else "Video Off",
            tint = if (participant.hasVideo) ActiveGreen else DangerRed,
            modifier = Modifier
                .size(28.dp)
                .background(if (participant.hasVideo) ActiveGreen.copy(alpha=0.1f) else DangerRed.copy(alpha=0.1f), CircleShape)
                .padding(6.dp)
        )
    }
}

private enum class MediaSettingsTab { Audio, Video }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaSettingsDialog(
    webRtcManager: WebRtcSessionManager,
    onDismissRequest: () -> Unit
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val currentCamName by webRtcManager.currentCameraName.collectAsState()
    val currentRes by webRtcManager.currentResolution.collectAsState()
    val flipHorizontal by webRtcManager.flipHorizontal.collectAsState()
    val flipVertical by webRtcManager.flipVertical.collectAsState()
    val availableAudioOutputs by webRtcManager.availableAudioOutputs.collectAsState()
    val currentAudioOutputId by webRtcManager.selectedAudioOutputId.collectAsState()
    val audioRoutingFailed by webRtcManager.audioRoutingFailed.collectAsState()
    val previewTrack by webRtcManager.cameraPreviewTrackFlow.collectAsState()

    var activeTab by remember { mutableStateOf(MediaSettingsTab.Audio) }
    var selectedCamName by remember { mutableStateOf(currentCamName) }
    var selectedRes by remember { mutableStateOf(currentRes) }
    var selectedFlipH by remember { mutableStateOf(flipHorizontal) }
    var selectedFlipV by remember { mutableStateOf(flipVertical) }
    var selectedAudioOutputId by remember { mutableStateOf(currentAudioOutputId) }
    var expandedCam by remember { mutableStateOf(false) }

    var originalAudioOutputId by remember { mutableStateOf(currentAudioOutputId) }

    val availableCameras = remember { webRtcManager.getAvailableCameras() }
    val availableResolutions = listOf(
        Pair(1920, 1080) to stringResource(com.nickfouf.classwave.R.string.quality_full_hd),
        Pair(1280, 720) to stringResource(com.nickfouf.classwave.R.string.quality_hd),
        Pair(640, 480) to stringResource(com.nickfouf.classwave.R.string.quality_standard),
        Pair(320, 240) to stringResource(com.nickfouf.classwave.R.string.quality_low)
    )

    LaunchedEffect(currentCamName) {
        if (selectedCamName.isEmpty() && currentCamName.isNotEmpty()) selectedCamName = currentCamName
    }

    LaunchedEffect(currentAudioOutputId, availableAudioOutputs) {
        if (originalAudioOutputId == null && currentAudioOutputId != null && selectedAudioOutputId == null) {
            originalAudioOutputId = currentAudioOutputId
            selectedAudioOutputId = currentAudioOutputId
        } else if (selectedAudioOutputId != null && availableAudioOutputs.none { it.id == selectedAudioOutputId }) {
            selectedAudioOutputId = currentAudioOutputId
        }
    }

    LaunchedEffect(audioRoutingFailed) {
        if (audioRoutingFailed) selectedAudioOutputId = currentAudioOutputId
    }

    LaunchedEffect(selectedCamName) {
        if (selectedCamName.isNotEmpty()) webRtcManager.previewCamera(selectedCamName)
    }
    LaunchedEffect(selectedRes) {
        webRtcManager.previewResolution(selectedRes.first, selectedRes.second)
    }

    val discardChanges: () -> Unit = {
        webRtcManager.discardVideoSettingsPreview()
        if (selectedAudioOutputId != originalAudioOutputId) {
            originalAudioOutputId?.let(webRtcManager::selectAudioOutput)
                ?: webRtcManager.clearAudioOutputSelection()
        }
        onDismissRequest()
    }

    Dialog(
        onDismissRequest = discardChanges,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = if (isLandscape) 16.dp else 12.dp)
                .widthIn(max = if (isLandscape) 560.dp else 720.dp)
                .fillMaxWidth()
                .fillMaxHeight(if (isLandscape) 0.92f else 0.9f),
            shape = RoundedCornerShape(if (isLandscape) 24.dp else 28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 16.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = if (isLandscape) 20.dp else 24.dp,
                            top = if (isLandscape) 10.dp else 20.dp,
                            end = 12.dp,
                            bottom = if (isLandscape) 8.dp else 14.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(com.nickfouf.classwave.R.string.media_settings),
                            style = if (isLandscape) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(com.nickfouf.classwave.R.string.media_settings_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = discardChanges) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(com.nickfouf.classwave.R.string.cancel))
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = if (isLandscape) 2.dp else 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MediaSettingsTabButton(
                        modifier = Modifier.weight(1f),
                        selected = activeTab == MediaSettingsTab.Audio,
                        icon = Icons.AutoMirrored.Filled.VolumeUp,
                        label = stringResource(com.nickfouf.classwave.R.string.audio),
                        compact = isLandscape,
                        onClick = { activeTab = MediaSettingsTab.Audio }
                    )
                    MediaSettingsTabButton(
                        modifier = Modifier.weight(1f),
                        selected = activeTab == MediaSettingsTab.Video,
                        icon = Icons.Filled.Videocam,
                        label = stringResource(com.nickfouf.classwave.R.string.video),
                        compact = isLandscape,
                        onClick = { activeTab = MediaSettingsTab.Video }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(top = if (isLandscape) 8.dp else 12.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (activeTab) {
                        MediaSettingsTab.Audio -> AudioSettingsContent(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(
                                    horizontal = if (isLandscape) 20.dp else 24.dp,
                                    vertical = if (isLandscape) 14.dp else 20.dp
                                ),
                            audioRoutingFailed = audioRoutingFailed,
                            availableAudioOutputs = availableAudioOutputs,
                            selectedAudioOutputId = selectedAudioOutputId,
                            onOutputSelected = { outputId ->
                                selectedAudioOutputId = outputId
                                webRtcManager.selectAudioOutput(outputId)
                            }
                        )

                        MediaSettingsTab.Video -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(
                                        horizontal = if (isLandscape) 20.dp else 24.dp,
                                        vertical = if (isLandscape) 14.dp else 20.dp
                                    ),
                                verticalArrangement = Arrangement.spacedBy(if (isLandscape) 14.dp else 18.dp)
                            ) {
                                VideoPreviewPane(
                                    previewTrack = previewTrack,
                                    webRtcManager = webRtcManager,
                                    flipHorizontal = selectedFlipH,
                                    flipVertical = selectedFlipV,
                                    compact = isLandscape
                                )
                                VideoSettingsControls(
                                    availableCameras = availableCameras,
                                    selectedCamName = selectedCamName,
                                    onCameraSelected = { selectedCamName = it },
                                    expandedCam = expandedCam,
                                    onCameraExpandedChange = { expandedCam = it },
                                    selectedFlipH = selectedFlipH,
                                    onFlipHorizontalChange = { selectedFlipH = it },
                                    selectedFlipV = selectedFlipV,
                                    onFlipVerticalChange = { selectedFlipV = it },
                                    availableResolutions = availableResolutions,
                                    selectedResolution = selectedRes,
                                    onResolutionSelected = { selectedRes = it },
                                    compact = isLandscape
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = if (isLandscape) 8.dp else 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = discardChanges) {
                        Text(stringResource(com.nickfouf.classwave.R.string.cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (selectedCamName.isNotEmpty()) {
                                webRtcManager.applyVideoSettings(
                                    cameraName = selectedCamName,
                                    resolution = selectedRes,
                                    flipHorizontally = selectedFlipH,
                                    flipVertically = selectedFlipV
                                )
                            }
                            onDismissRequest()
                        }
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(com.nickfouf.classwave.R.string.save))
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioSettingsContent(
    modifier: Modifier = Modifier,
    audioRoutingFailed: Boolean,
    availableAudioOutputs: List<AudioOutputDevice>,
    selectedAudioOutputId: Int?,
    onOutputSelected: (Int) -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        SettingsSectionIntro(
            icon = Icons.Filled.Speaker,
            title = stringResource(com.nickfouf.classwave.R.string.speaker_output),
            description = stringResource(com.nickfouf.classwave.R.string.speaker_output_description)
        )

        if (audioRoutingFailed) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text(
                        stringResource(com.nickfouf.classwave.R.string.audio_output_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        if (availableAudioOutputs.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = stringResource(com.nickfouf.classwave.R.string.no_audio_outputs),
                    modifier = Modifier.padding(18.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                availableAudioOutputs.forEach { output ->
                    AudioOutputOption(
                        device = output,
                        selected = output.id == selectedAudioOutputId,
                        onClick = { onOutputSelected(output.id) }
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Info,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(com.nickfouf.classwave.R.string.audio_changes_immediately),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun VideoPreviewPane(
    modifier: Modifier = Modifier,
    previewTrack: VideoTrack?,
    webRtcManager: WebRtcSessionManager,
    flipHorizontal: Boolean,
    flipVertical: Boolean,
    compact: Boolean
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 18.dp)
    ) {
        SettingsSectionIntro(
            icon = Icons.Filled.Videocam,
            title = stringResource(com.nickfouf.classwave.R.string.video_preview),
            description = stringResource(com.nickfouf.classwave.R.string.video_settings_description)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (compact) Modifier.aspectRatio(16f / 9f) else Modifier.height(190.dp))
                .clip(RoundedCornerShape(if (compact) 16.dp else 18.dp))
                .background(Color.Black)
        ) {
            if (previewTrack != null) {
                ClippedVideoRenderer(
                    videoTrack = previewTrack,
                    eglBaseContext = webRtcManager.eglBaseContext,
                    modifier = Modifier.fillMaxSize(),
                    flipHorizontal = flipHorizontal,
                    flipVertical = flipVertical
                )
            } else {
                VideoPlaceholder(
                    name = stringResource(com.nickfouf.classwave.R.string.preview),
                    isMuted = true,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Surface(
                modifier = Modifier.padding(if (compact) 8.dp else 10.dp),
                color = Color.Black.copy(alpha = 0.62f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = stringResource(com.nickfouf.classwave.R.string.preview),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoSettingsControls(
    modifier: Modifier = Modifier,
    availableCameras: List<CameraDevice>,
    selectedCamName: String,
    onCameraSelected: (String) -> Unit,
    expandedCam: Boolean,
    onCameraExpandedChange: (Boolean) -> Unit,
    selectedFlipH: Boolean,
    onFlipHorizontalChange: (Boolean) -> Unit,
    selectedFlipV: Boolean,
    onFlipVerticalChange: (Boolean) -> Unit,
    availableResolutions: List<Pair<Pair<Int, Int>, String>>,
    selectedResolution: Pair<Int, Int>,
    onResolutionSelected: (Pair<Int, Int>) -> Unit,
    compact: Boolean
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 18.dp)
    ) {
        ExposedDropdownMenuBox(
            expanded = expandedCam,
            onExpandedChange = onCameraExpandedChange
        ) {
            val selectedCamera = availableCameras.find { it.deviceName == selectedCamName }
            OutlinedTextField(
                value = selectedCamera?.let {
                    stringResource(
                        if (it.isFrontFacing) com.nickfouf.classwave.R.string.front_camera
                        else com.nickfouf.classwave.R.string.back_camera
                    )
                } ?: stringResource(com.nickfouf.classwave.R.string.camera_unavailable),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(com.nickfouf.classwave.R.string.camera_selection)) },
                leadingIcon = {
                    Icon(
                        if (selectedCamera?.isFrontFacing != false) Icons.Filled.CameraFront else Icons.Filled.CameraRear,
                        contentDescription = null
                    )
                },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCam) },
                modifier = Modifier
                    .menuAnchor(
                        type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                        enabled = availableCameras.isNotEmpty()
                    )
                    .fillMaxWidth(),
                enabled = availableCameras.isNotEmpty(),
                singleLine = true
            )
            ExposedDropdownMenu(
                expanded = expandedCam,
                onDismissRequest = { onCameraExpandedChange(false) }
            ) {
                availableCameras.forEach { camera ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    stringResource(
                                        if (camera.isFrontFacing) com.nickfouf.classwave.R.string.front_camera
                                        else com.nickfouf.classwave.R.string.back_camera
                                    )
                                )
                                Text(
                                    camera.deviceName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        leadingIcon = {
                            Icon(
                                if (camera.isFrontFacing) Icons.Filled.CameraFront else Icons.Filled.CameraRear,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            onCameraSelected(camera.deviceName)
                            onCameraExpandedChange(false)
                        }
                    )
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column {
                SettingsSwitchRow(
                    icon = Icons.Filled.Flip,
                    title = stringResource(com.nickfouf.classwave.R.string.flip_horizontal),
                    description = stringResource(com.nickfouf.classwave.R.string.flip_horizontal_description),
                    checked = selectedFlipH,
                    compact = compact,
                    onCheckedChange = onFlipHorizontalChange
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsSwitchRow(
                    icon = Icons.Filled.SwapVert,
                    title = stringResource(com.nickfouf.classwave.R.string.flip_vertical),
                    description = stringResource(com.nickfouf.classwave.R.string.flip_vertical_description),
                    checked = selectedFlipV,
                    compact = compact,
                    onCheckedChange = onFlipVerticalChange
                )
            }
        }

        SettingsSectionIntro(
            icon = Icons.Filled.HighQuality,
            title = stringResource(com.nickfouf.classwave.R.string.resolution_quality),
            description = stringResource(com.nickfouf.classwave.R.string.quality_description)
        )
        if (compact) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                availableResolutions.chunked(2).forEach { rowOptions ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowOptions.forEach { option ->
                            VideoResolutionOption(
                                modifier = Modifier.weight(1f),
                                option = option,
                                selected = selectedResolution == option.first,
                                compact = true,
                                onClick = { onResolutionSelected(option.first) }
                            )
                        }
                        if (rowOptions.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                availableResolutions.forEach { option ->
                    VideoResolutionOption(
                        modifier = Modifier.fillMaxWidth(),
                        option = option,
                        selected = selectedResolution == option.first,
                        compact = false,
                        onClick = { onResolutionSelected(option.first) }
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoResolutionOption(
    modifier: Modifier,
    option: Pair<Pair<Int, Int>, String>,
    selected: Boolean,
    compact: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 6.dp else 12.dp,
                vertical = if (compact) 4.dp else 8.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Column(modifier = Modifier.padding(start = if (compact) 2.dp else 8.dp)) {
                Text(
                    text = option.second,
                    style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    "${option.first.first} × ${option.first.second}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun MediaSettingsTabButton(
    modifier: Modifier,
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    compact: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = if (compact) 8.dp else 11.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(19.dp),
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsSectionIntro(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(21.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AudioOutputOption(
    device: AudioOutputDevice,
    selected: Boolean,
    onClick: () -> Unit
) {
    val isBuiltIn = device.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER ||
        device.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
    val fallbackName = when (device.type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> stringResource(com.nickfouf.classwave.R.string.built_in_speaker)
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> stringResource(com.nickfouf.classwave.R.string.phone_earpiece)
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> stringResource(com.nickfouf.classwave.R.string.wired_headset)
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER -> stringResource(com.nickfouf.classwave.R.string.bluetooth_audio)
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> stringResource(com.nickfouf.classwave.R.string.usb_audio)
        AudioDeviceInfo.TYPE_HEARING_AID -> stringResource(com.nickfouf.classwave.R.string.hearing_device)
        else -> stringResource(com.nickfouf.classwave.R.string.external_audio)
    }
    val title = if (!isBuiltIn && device.productName.isNotBlank() && device.productName != "unknown") {
        device.productName
    } else {
        fallbackName
    }
    val icon = when (device.type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> Icons.Filled.Speaker
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> Icons.Filled.PhoneAndroid
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER -> Icons.Filled.Bluetooth
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> Icons.Filled.Usb
        AudioDeviceInfo.TYPE_HEARING_AID -> Icons.Filled.Hearing
        else -> Icons.Filled.Headphones
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(16.dp),
        border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)) else null
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.surface,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(
                    text = if (selected) stringResource(com.nickfouf.classwave.R.string.current_audio_output)
                    else stringResource(com.nickfouf.classwave.R.string.tap_to_use_output),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            RadioButton(selected = selected, onClick = onClick)
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    compact: Boolean = false,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(if (compact) 12.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(modifier = Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
