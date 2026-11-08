package com.nickfouf.classwave.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.nickfouf.classwave.BuildConfig
import com.nickfouf.classwave.webrtc.MeetingMediaSettings
import com.nickfouf.classwave.webrtc.WebRtcSessionManager
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.launch
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreCallScreen(
    token: String,
    roomId: String,
    profileViewModel: ProfileViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    courseViewModel: CourseViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onJoinCall: (Boolean, Boolean, MeetingMediaSettings) -> Unit,
    onNavigateBack: () -> Unit
) {
    val profileState by profileViewModel.profileState.collectAsState()
    val courseState by courseViewModel.courseState.collectAsState()
    var isJoining by rememberSaveable { mutableStateOf(false) }
    
    BackHandler(enabled = !isJoining) {
        onNavigateBack()
    }
    
    val course = (courseState as? CourseState.Success)?.courses?.find { it._id == roomId }
    val courseName = course?.name ?: "Live Class"
    val role = (profileState as? ProfileState.Success)?.role ?: "student"
    
    var isMeetingStarted by rememberSaveable(roomId) { mutableStateOf(course?.isLive == true) }
    var activeSocket by remember { mutableStateOf<Socket?>(null) }

    LaunchedEffect(course?.isLive) {
        if (course?.isLive == true) isMeetingStarted = true
    }
    
    var isMicOn by rememberSaveable { mutableStateOf(false) }
    var isCamOn by rememberSaveable { mutableStateOf(false) }
    var participantCount by remember { mutableIntStateOf(0) }
    var hasPermissions by remember { mutableStateOf(false) }
    var showMediaSettings by rememberSaveable { mutableStateOf(false) }
    
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
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
    
    val localUserId = (profileState as? ProfileState.Success)?.id ?: "unknown"
    val localName = (profileState as? ProfileState.Success)?.let { "${it.firstName} ${it.lastName}" } ?: "User"
    val webRtcManager = remember(roomId) { WebRtcSessionManager(context, roomId, localUserId, localName) }
    
    LaunchedEffect(hasPermissions, isMeetingStarted) {
        if (hasPermissions && isMeetingStarted) {
            webRtcManager.startLocalVideo(connectImmediately = false)
        }
    }
    
    LaunchedEffect(isMicOn) { if (isMeetingStarted) webRtcManager.toggleAudio(isMicOn) }
    LaunchedEffect(isCamOn) { if (isMeetingStarted) webRtcManager.toggleVideo(isCamOn) }
    
    DisposableEffect(roomId, token) {
        var socket: Socket? = null
        try {
            socket = IO.socket(BuildConfig.SERVER_URL)
            activeSocket = socket
            socket.on(Socket.EVENT_CONNECT) {
                socket.emit("authenticate", token)
                socket.emit("subscribe-room-info", JSONObject().apply { put("roomId", roomId) })
            }
            socket.on("room-info-update") { args ->
                val data = args.firstOrNull() as? JSONObject ?: return@on
                coroutineScope.launch {
                    participantCount = data.optInt("count", 0)
                    isMeetingStarted = data.optBoolean("isLive", false)
                }
            }
            socket.on("room-participants-update") { args ->
                val data = args.firstOrNull() as? JSONObject ?: return@on
                coroutineScope.launch {
                    participantCount = data.optInt("count", 0)
                }
            }
            socket.on("class-started") {
                coroutineScope.launch {
                    isMeetingStarted = true
                }
            }
            socket.on("class-ended") {
                coroutineScope.launch {
                    isMeetingStarted = false
                }
            }
            socket.connect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        onDispose {
            socket?.emit("unsubscribe-room-info", JSONObject().apply { put("roomId", roomId) })
            socket?.off()
            socket?.disconnect()
            webRtcManager.stopLocalVideo()
        }
    }
    
    val localTrack by webRtcManager.localVideoTrackFlow.collectAsState()
    val flipHorizontal by webRtcManager.flipHorizontal.collectAsState()
    val flipVertical by webRtcManager.flipVertical.collectAsState()

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    AnimatedContent(targetState = isMeetingStarted, label = "PreCall State Transition") { started ->
        if (!started) {
            if (role == "teacher") {
                TeacherStartMeetingContent(
                    courseName = courseName,
                    onStartMeeting = {
                        activeSocket?.emit("start-class", JSONObject().apply {
                            put("roomId", roomId)
                            put("userId", localUserId)
                        })
                        isMeetingStarted = true
                    },
                    onNavigateBack = onNavigateBack
                )
            } else {
                StudentWaitingContent(
                    courseName = courseName,
                    onNavigateBack = onNavigateBack
                )
            }
        } else {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    TopAppBar(
                        title = { Text("Ετοιμασία", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold) },
                        navigationIcon = {
                            IconButton(onClick = onNavigateBack, enabled = !isJoining) {
                                Icon(Icons.Filled.ArrowBack, "Πίσω", tint = MaterialTheme.colorScheme.onBackground)
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { showMediaSettings = true },
                                enabled = !isJoining
                            ) {
                                Icon(
                                    Icons.Filled.Settings,
                                    contentDescription = androidx.compose.ui.res.stringResource(
                                        com.nickfouf.classwave.R.string.media_settings
                                    ),
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                }
            ) { paddingValues ->
                val headerContent = @Composable {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = courseName,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Filled.People, "Participants", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "$participantCount Συμμετέχοντες στην κλήση",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                val cameraPreviewContent = @Composable {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                    ) {
                        if (hasPermissions) {
                            if (isCamOn && !isJoining) {
                                localTrack?.let { track ->
                                    webRtcManager.eglBaseContext?.let { eglCtx ->
                                        SafeVideoRenderer(
                                            videoTrack = track as org.webrtc.VideoTrack,
                                            eglBaseContext = eglCtx,
                                            name = "Εγώ",
                                            isMuted = !isMicOn,
                                            modifier = Modifier.fillMaxSize(),
                                            isLocal = true,
                                            flipHorizontal = flipHorizontal,
                                            flipVertical = flipVertical,
                                            showOverlay = false,
                                            cornerRadius = 24.dp
                                        )
                                    }
                                } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                }
                            } else {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            modifier = Modifier
                                                .size(80.dp)
                                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Filled.VideocamOff, "Camera Off", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
                                        }
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text("Η κάμερα είναι απενεργοποιημένη", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Απαιτείται πρόσβαση στην κάμερα\nκαι το μικρόφωνο", color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp))
                            }
                        }
                    }
                }

                val controlsContent = @Composable {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FloatingActionButton(
                            onClick = { if (!isJoining) isMicOn = !isMicOn },
                            containerColor = if (isMicOn) MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f) else DangerRed,
                            contentColor = if (isMicOn) MaterialTheme.colorScheme.onBackground else Color.White,
                            shape = CircleShape,
                            modifier = Modifier.size(64.dp),
                            elevation = FloatingActionButtonDefaults.elevation(0.dp)
                        ) {
                            Icon(
                                imageVector = if (isMicOn) Icons.Filled.Mic else Icons.Filled.MicOff,
                                contentDescription = "Toggle Mic",
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(32.dp))
                        FloatingActionButton(
                            onClick = { if (!isJoining) isCamOn = !isCamOn },
                            containerColor = if (isCamOn) MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f) else DangerRed,
                            contentColor = if (isCamOn) MaterialTheme.colorScheme.onBackground else Color.White,
                            shape = CircleShape,
                            modifier = Modifier.size(64.dp),
                            elevation = FloatingActionButtonDefaults.elevation(0.dp)
                        ) {
                            Icon(
                                imageVector = if (isCamOn) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                                contentDescription = "Toggle Cam",
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }

                val joinButtonContent = @Composable {
                    Button(
                        onClick = {
                            if (isJoining) return@Button
                            isJoining = true
                            val micEnabled = isMicOn
                            val cameraEnabled = isCamOn
                            val mediaSettings = webRtcManager.snapshotMediaSettings()
                            coroutineScope.launch {
                                withFrameNanos { }
                                withFrameNanos { }
                                webRtcManager.stopLocalVideo {
                                    onJoinCall(micEnabled, cameraEnabled, mediaSettings)
                                }
                            }
                        },
                        enabled = !isJoining,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .shadow(8.dp, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Συμμετοχή", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 24.dp)
                ) {
                    if (isLandscape) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(vertical = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                cameraPreviewContent()
                            }
                            Column(
                                modifier = Modifier.width(320.dp).fillMaxHeight(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                headerContent()
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    controlsContent()
                                    Spacer(modifier = Modifier.height(32.dp))
                                    joinButtonContent()
                                }
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            headerContent()
                            Spacer(modifier = Modifier.height(24.dp))
                            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                cameraPreviewContent()
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            controlsContent()
                            Spacer(modifier = Modifier.height(32.dp))
                            joinButtonContent()
                        }
                    }
                }
            }

            if (showMediaSettings) {
                MediaSettingsDialog(
                    webRtcManager = webRtcManager,
                    onDismissRequest = { showMediaSettings = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherStartMeetingContent(
    courseName: String,
    onStartMeeting: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Έναρξη Μαθήματος", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.Filled.ArrowBack, "Πίσω") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.PlayCircleOutline, 
                    contentDescription = null, 
                    modifier = Modifier.size(80.dp), 
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(32.dp))
            Text(
                "Είστε έτοιμος να ξεκινήσετε;", 
                fontSize = 24.sp, 
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Θα ξεκινήσετε το μάθημα:\n$courseName", 
                textAlign = TextAlign.Center, 
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp
            )
            Spacer(Modifier.height(48.dp))
            Button(
                onClick = onStartMeeting,
                modifier = Modifier.fillMaxWidth().height(56.dp).shadow(8.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Εκκίνηση Μαθήματος", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentWaitingContent(
    courseName: String,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Αναμονή", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.Filled.ArrowBack, "Πίσω") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(64.dp), 
                    strokeWidth = 6.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(32.dp))
            Text(
                "Το μάθημα δεν έχει ξεκινήσει", 
                fontSize = 24.sp, 
                fontWeight = FontWeight.Bold, 
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Παρακαλώ περιμένετε να ξεκινήσει ο καθηγητής το μάθημα $courseName. Η οθόνη θα ανανεωθεί αυτόματα.", 
                textAlign = TextAlign.Center, 
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp,
                lineHeight = 22.sp
            )
        }
    }
}
