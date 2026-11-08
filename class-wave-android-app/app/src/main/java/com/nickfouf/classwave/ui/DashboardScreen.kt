package com.nickfouf.classwave.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.delay
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nickfouf.classwave.R
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.BackHandler
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    token: String,
    initialTab: Int = 0,
    profileViewModel: ProfileViewModel = viewModel(),
    courseViewModel: CourseViewModel = viewModel(),
    onTabSelected: (Int) -> Unit = {},
    onJoinRoom: (String) -> Unit,
    onLogout: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToDisplayLanguage: () -> Unit,
    onCreateCourse: () -> Unit,
    onEnrollCourse: () -> Unit,
    onNavigateToCourse: (String) -> Unit
) {
    val pagerState = rememberPagerState(initialPage = initialTab) { 3 }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var showNotificationDialog by remember { mutableStateOf(false) }
    var isPermanentlyDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            val activity = context as? Activity
            val shouldShowRationale = activity?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.POST_NOTIFICATIONS) } ?: false
            if (!shouldShowRationale) {
                isPermanentlyDenied = true
                showNotificationDialog = true
            }
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val isGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!isGranted) {
                val activity = context as? Activity
                val shouldShowRationale = activity?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.POST_NOTIFICATIONS) } ?: false
                if (shouldShowRationale) {
                    isPermanentlyDenied = false
                    showNotificationDialog = true
                } else {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    BackHandler {
        if (pagerState.currentPage != 0) {
            coroutineScope.launch { pagerState.animateScrollToPage(0) }
        } else {
            (context as? Activity)?.moveTaskToBack(true)
        }
    }

    val selectedBottomTab = pagerState.targetPage

    val profileState by profileViewModel.profileState.collectAsState()
    val courseState by courseViewModel.courseState.collectAsState()

    LaunchedEffect(token) {
        profileViewModel.fetchProfile(token)
        courseViewModel.fetchCourses(token)
        courseViewModel.connectSocket(token)
    }

    val liveCoursesIds = remember { mutableStateOf(setOf<String>()) }
    var isFirstCourseLoad by remember { mutableStateOf(true) }

    LaunchedEffect(courseState) {
        if (courseState is CourseState.Success) {
            val courses = (courseState as CourseState.Success).courses
            val currentLive = courses.filter { it.isLive }.map { it._id }.toSet()
            
            if (!isFirstCourseLoad) {
                val newlyLive = currentLive - liveCoursesIds.value
                newlyLive.forEach { id ->
                    val c = courses.find { it._id == id }
                    if (c != null) {
                        com.nickfouf.classwave.utils.NotificationManagerHelper.showLiveClassNotification(context, c.name, c._id)
                    }
                }
            } else {
                isFirstCourseLoad = false
            }
            liveCoursesIds.value = currentLive
        }
    }

    LaunchedEffect(courseState, profileState) {
        if (courseState is CourseState.Success && profileState is ProfileState.Success) {
            val courses = (courseState as CourseState.Success).courses
            val userId = (profileState as ProfileState.Success).id
            com.nickfouf.classwave.utils.NotificationManagerHelper.scheduleMeetingNotifications(context, courses, userId)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            courseViewModel.disconnectSocket()
        }
    }
    
    LaunchedEffect(pagerState.targetPage) {
        onTabSelected(pagerState.targetPage)
    }

    val initials = if (profileState is ProfileState.Success) {
        val state = profileState as ProfileState.Success
        "${state.firstName.take(1)}${state.lastName.take(1)}".uppercase()
    } else ""

    val role = if (profileState is ProfileState.Success) {
        (profileState as ProfileState.Success).role
    } else "student"

    val parsedColor = remember(profileState) {
        try {
            if (profileState is ProfileState.Success) {
                Color(android.graphics.Color.parseColor((profileState as ProfileState.Success).iconColor))
            } else null
        } catch (e: Exception) { null }
    }

    val avatarColor = parsedColor ?: MaterialTheme.colorScheme.primary
    val focusManager = LocalFocusManager.current

    val topBarAlpha by remember {
        derivedStateOf {
            val offset = pagerState.currentPage + pagerState.currentPageOffsetFraction
            when {
                offset <= 1f -> 1f 
                offset >= 2f -> 0f 
                else -> 2f - offset 
            }
        }
    }

    Scaffold(
        modifier = Modifier.pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    icon = { Icon(if (selectedBottomTab == 0) Icons.Filled.Home else Icons.Outlined.Home, contentDescription = "Home") },
                    label = { Text(stringResource(id = R.string.tab_home), fontWeight = if (selectedBottomTab == 0) FontWeight.Bold else FontWeight.Normal) },
                    selected = selectedBottomTab == 0,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary, indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                )
                NavigationBarItem(
                    icon = { Icon(if (selectedBottomTab == 1) Icons.Filled.LibraryBooks else Icons.Outlined.LibraryBooks, contentDescription = "Courses") },
                    label = { Text(stringResource(id = R.string.tab_courses), fontWeight = if (selectedBottomTab == 1) FontWeight.Bold else FontWeight.Normal) },
                    selected = selectedBottomTab == 1,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary, indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                )
                NavigationBarItem(
                    icon = { Icon(if (selectedBottomTab == 2) Icons.Filled.Settings else Icons.Outlined.Settings, contentDescription = "Settings") },
                    label = { Text(stringResource(id = R.string.tab_profile), fontWeight = if (selectedBottomTab == 2) FontWeight.Bold else FontWeight.Normal) },
                    selected = selectedBottomTab == 2,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(2) } },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary, indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                )
            }
        }
    ) { paddingValues ->
        val topPadding = paddingValues.calculateTopPadding()
        val bottomPadding = paddingValues.calculateBottomPadding()

        Box(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> CalendarHomeContent(
                        paddingValues = PaddingValues(top = topPadding + 72.dp, bottom = bottomPadding + 16.dp),
                        role = role,
                        courses = (courseState as? CourseState.Success)?.courses ?: emptyList(),
                        onJoinRoom = onJoinRoom
                    )
                    1 -> CoursesContent(
                        paddingValues = PaddingValues(top = topPadding + 72.dp, bottom = bottomPadding),
                        role = role,
                        token = token,
                        courseViewModel = courseViewModel,
                        onCreateCourse = onCreateCourse,
                        onEnrollCourse = onEnrollCourse,
                        onNavigateToCourse = onNavigateToCourse
                    )
                    2 -> SettingsContent(
                        paddingValues = PaddingValues(top = topPadding + 16.dp, bottom = bottomPadding + 16.dp),
                        onNavigateToProfile = onNavigateToProfile, 
                        onNavigateToDisplayLanguage = onNavigateToDisplayLanguage, 
                        onLogout = onLogout
                    )
                }
            }

            if (showNotificationDialog) {
                NotificationPermissionDialog(
                    onConfirm = {
                        showNotificationDialog = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onDismiss = { showNotificationDialog = false },
                    isPermanentlyDenied = isPermanentlyDenied,
                    onOpenSettings = {
                        showNotificationDialog = false
                        val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }
                )
            }

            if (topBarAlpha > 0f) {
                TopAppBar(
                    modifier = Modifier.graphicsLayer { alpha = topBarAlpha },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.School,
                                    contentDescription = "ClassWave Icon",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(id = R.string.app_name),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = (-0.5).sp
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onNavigateToProfile, modifier = Modifier.padding(end = 8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(avatarColor.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (initials.isNotBlank()) {
                                    AutoSizeText(
                                        text = initials,
                                        color = avatarColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                } else {
                                    Icon(Icons.Filled.Person, contentDescription = "Profile", tint = avatarColor)
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            }
        }
    }
}

data class ScheduledCourse(val id: String, val name: String, val teacher: String, val time: String)

enum class CalendarViewMode { MONTH, WEEK }

@Composable
fun CalendarHomeContent(
    paddingValues: PaddingValues,
    role: String,
    courses: List<com.nickfouf.classwave.network.CourseResponse>,
    onJoinRoom: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf(Calendar.getInstance()) }
    var viewMode by remember { mutableStateOf(CalendarViewMode.MONTH) }

    val weekEvents = remember(courses) {
        courses.flatMap { course ->
            course.schedule?.mapNotNull { item ->
                try {
                    val timeParts = item.time.split("-").map { it.trim() }
                    if (timeParts.size != 2) return@mapNotNull null
                    val startParts = timeParts[0].split(":")
                    val startHour = startParts[0].toFloat() + startParts[1].toFloat() / 60f
                    
                    val endParts = timeParts[1].split(":")
                    val endHour = endParts[0].toFloat() + endParts[1].toFloat() / 60f
                    
                    val dateParts = item.date.split("-")
                    val year = dateParts[0].toInt()
                    val month = dateParts[1].toInt() - 1
                    val day = dateParts[2].toInt()
                    
                    val cal = java.util.Calendar.getInstance().apply { set(year, month, day) }
                    val dayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK)
                    val dayIndex = if (dayOfWeek == java.util.Calendar.SUNDAY) 6 else dayOfWeek - 2
                    
                    val teacherNameStr = if (course.teacher != null) "${course.teacher.lastName} ${course.teacher.firstName}" else ""
                    WeekEvent(
                        id = item.id,
                        name = "${course.name} - ${item.topic}",
                        dayIndex = dayIndex,
                        startHour = startHour,
                        durationHours = endHour - startHour,
                        color = try { Color(android.graphics.Color.parseColor(course.color)) } catch (e: Exception) { Color(0xFF3B82F6) },
                        dateString = item.date,
                        originalTime = item.time,
                        courseName = course.name,
                        teacherName = teacherNameStr,
                        courseId = course._id
                    )
                } catch (e: Exception) { null }
            } ?: emptyList()
        }
    }

    val eventDates = remember(courses) {
        courses.flatMap { it.schedule?.map { item -> item.date } ?: emptyList() }.distinct()
    }
    
    val liveCourses = remember(courses) {
        courses.filter { it.isLive }.map { course ->
            val teacherNameStr = if (course.teacher != null) "${course.teacher.lastName} ${course.teacher.firstName}" else ""
            ScheduledCourse(
                id = course._id, 
                name = course.name, 
                teacher = teacherNameStr, 
                time = "Live Now"
            )
        }
    }

    val headerContent = @Composable {
        Column {
            Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                LiveBanner(liveCourses = liveCourses, onJoinRoom = onJoinRoom, onShowDialog = { showDialog = true })
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (viewMode == CalendarViewMode.MONTH) stringResource(id = R.string.todays_schedule) else stringResource(R.string.weekly_schedule),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    lineHeight = 24.sp,
                    maxLines = 2
                )
                
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(modifier = Modifier.padding(4.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (viewMode == CalendarViewMode.MONTH) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { viewMode = CalendarViewMode.MONTH }
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(
                                stringResource(R.string.month), 
                                color = if (viewMode == CalendarViewMode.MONTH) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (viewMode == CalendarViewMode.WEEK) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { viewMode = CalendarViewMode.WEEK }
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(
                                stringResource(R.string.week), 
                                color = if (viewMode == CalendarViewMode.WEEK) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }

    androidx.compose.animation.Crossfade(targetState = viewMode, label = "Calendar View Mode", modifier = Modifier.fillMaxSize()) { mode ->
        when (mode) {
            CalendarViewMode.MONTH -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    contentPadding = PaddingValues(top = paddingValues.calculateTopPadding(), bottom = paddingValues.calculateBottomPadding() + 16.dp)
                ) {
                    item { headerContent() }
                    item {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                            MonthCalendarGrid(selectedDate = selectedDate, eventDates = eventDates, onDaySelected = { selectedDate = it })
                        }
                    }
                    item {
                        Text(
                            text = stringResource(id = R.string.todays_schedule),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 8.dp)
                        )
                    }
                    item {
                        val selectedDateString = String.format(java.util.Locale.US, "%04d-%02d-%02d", selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH) + 1, selectedDate.get(Calendar.DAY_OF_MONTH))
                        val eventsForDay = weekEvents.filter { it.dateString == selectedDateString }.sortedBy { it.startHour }

                        if (eventsForDay.isNotEmpty()) {
                            Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                eventsForDay.forEach { event ->
                                    TimelineCard(
                                        time = event.originalTime ?: "",
                                        dateString = event.dateString,
                                        courseName = event.courseName,
                                        teacherName = event.teacherName,
                                        role = role,
                                        onAction = { onJoinRoom(event.courseId) }
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = stringResource(id = R.string.no_scheduled_classes),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                            )
                        }
                    }
                }
            }
            CalendarViewMode.WEEK -> {
                val weekScrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(weekScrollState)
                        .padding(top = paddingValues.calculateTopPadding())
                ) {
                    headerContent()
                    Spacer(modifier = Modifier.height(16.dp))
                    WeekCalendarGrid(
                        selectedDate = selectedDate, 
                        events = weekEvents,
                        onDaySelected = { selectedDate = it },
                        modifier = Modifier.fillMaxWidth().padding(bottom = paddingValues.calculateBottomPadding() + 16.dp)
                    )
                }
            }
        }
    }

    if (showDialog) {
        LiveCoursesDialog(
            courses = liveCourses,
            onDismiss = { showDialog = false },
            onJoinRoom = onJoinRoom
        )
    }
}

data class WeekEvent(
    val id: String? = null,
    val name: String,
    val dayIndex: Int,
    val startHour: Float,
    val durationHours: Float,
    val color: Color,
    val dateString: String? = null,
    val originalTime: String? = null,
    val courseName: String = "",
    val teacherName: String = "",
    val courseId: String = "",
    val groupId: String? = null,
    val repeatDays: List<Int>? = null,
    val repeatUntilDate: String? = null
)

@Composable
fun WeekCalendarGrid(
    selectedDate: Calendar, 
    events: List<WeekEvent> = emptyList(),
    onDaySelected: (Calendar) -> Unit,
    onEventClick: ((WeekEvent) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val daysOfWeek = listOf(
        stringResource(id = R.string.day_mon), stringResource(id = R.string.day_tue), stringResource(id = R.string.day_wed),
        stringResource(id = R.string.day_thu), stringResource(id = R.string.day_fri), stringResource(id = R.string.day_sat), stringResource(id = R.string.day_sun)
    )
    
    val currentWeek = selectedDate.clone() as Calendar
    val dayOfWeek = currentWeek.get(Calendar.DAY_OF_WEEK)
    val offset = if (dayOfWeek == Calendar.SUNDAY) -6 else Calendar.MONDAY - dayOfWeek
    currentWeek.add(Calendar.DAY_OF_MONTH, offset)
    
    val todayCal = Calendar.getInstance()
    
    val currentMonthFormat = SimpleDateFormat("MMMM yyyy", LocalLocale.current.platformLocale)

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.padding(horizontal = 20.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            var currentWeekStart by remember { mutableStateOf(currentWeek) }
            
            val visibleDates = (0..6).map { 
                val day = currentWeekStart.clone() as Calendar
                day.add(Calendar.DAY_OF_MONTH, it)
                day
            }
            val visibleMonthTitle = currentMonthFormat.format(currentWeekStart.time).replaceFirstChar { if (it.isLowerCase()) it.titlecase(LocalLocale.current.platformLocale) else it.toString() }

            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp), 
                horizontalArrangement = Arrangement.SpaceBetween, 
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(visibleMonthTitle, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onBackground)
                Row {
                    IconButton(onClick = {
                        val newCal = currentWeekStart.clone() as Calendar
                        newCal.add(Calendar.DAY_OF_MONTH, -7)
                        currentWeekStart = newCal
                        onDaySelected(newCal)
                    }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    IconButton(onClick = {
                        val newCal = currentWeekStart.clone() as Calendar
                        newCal.add(Calendar.DAY_OF_MONTH, 7)
                        currentWeekStart = newCal
                        onDaySelected(newCal)
                    }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            
            Row(modifier = Modifier.fillMaxWidth().padding(start = 50.dp, end = 16.dp, bottom = 8.dp)) {
                visibleDates.forEachIndexed { index, date ->
                    val isToday = date.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                                  date.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)
                                  
                    val isSelected = date.get(Calendar.YEAR) == selectedDate.get(Calendar.YEAR) &&
                                     date.get(Calendar.DAY_OF_YEAR) == selectedDate.get(Calendar.DAY_OF_YEAR)

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onDaySelected(date) },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(daysOfWeek[index], fontSize = 12.sp, color = if (isToday || isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else if (isToday) MaterialTheme.colorScheme.primary.copy(alpha=0.2f) else Color.Transparent),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = date.get(Calendar.DAY_OF_MONTH).toString(),
                                color = if (isSelected) Color.White else if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

            val scrollState = rememberScrollState()
            val coroutineScope = rememberCoroutineScope()
            val hourHeight = 60.dp
            val viewportHeight = 240.dp
            
            val density = androidx.compose.ui.platform.LocalDensity.current
            val totalHeightPx: Float = with(density) { (hourHeight * 24).toPx() }
            val viewportHeightPx: Float = with(density) { viewportHeight.toPx() }
            val maxScroll = maxOf(0f, totalHeightPx - viewportHeightPx)

            LaunchedEffect(Unit) {
                if (maxScroll > 0) {
                    val currentHour = todayCal.get(Calendar.HOUR_OF_DAY)
                    val targetHour = maxOf(0, currentHour - 1)
                    val initialScroll: Float = with(density) { (hourHeight * targetHour).toPx() }
                    scrollState.scrollTo(initialScroll.toInt().coerceIn(0, maxScroll.toInt()))
                }
            }

            Row(modifier = Modifier.fillMaxWidth().height(viewportHeight)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(scrollState, enabled = false)
                ) {
                    Column {
                        for (hour in 0..23) {
                            Row(
                                modifier = Modifier.fillMaxWidth().height(hourHeight),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = String.format("%02d:00", hour),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(50.dp).padding(top = 4.dp, end = 8.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                                )
                                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                                    Row(modifier = Modifier.fillMaxSize()) {
                                        for(i in 0..6) {
                                            Box(modifier = Modifier.weight(1f).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.02f)))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(hourHeight * 24).padding(start = 50.dp)) {
                    val dayWidth = maxWidth / 7
                    
                    val visibleDateStrings = visibleDates.map {
                        String.format(java.util.Locale.US, "%04d-%02d-%02d", it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1, it.get(Calendar.DAY_OF_MONTH))
                    }
                    
                    val filteredEvents = events.filter { event ->
                        event.dateString == null || visibleDateStrings.contains(event.dateString)
                    }
                    
                    filteredEvents.forEach { event ->
                        val xOffset = dayWidth * event.dayIndex
                        val yOffset = hourHeight * event.startHour
                        val eventHeight = hourHeight * event.durationHours
                        
                        Box(
                            modifier = Modifier
                                .offset(x = xOffset, y = yOffset)
                                .width(dayWidth)
                                .height(eventHeight)
                                .padding(1.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(event.color.copy(alpha = 0.2f))
                                .border(1.dp, event.color.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .clickable { onEventClick?.invoke(event) }
                                .padding(2.dp)
                        ) {
                            Text(event.name, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = event.color, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 10.sp)
                        }
                    }
                    
                    val currentHour = todayCal.get(Calendar.HOUR_OF_DAY) + todayCal.get(Calendar.MINUTE) / 60f
                    val currentDayOfWeek = todayCal.get(Calendar.DAY_OF_WEEK)
                    val todayIndex = if (currentDayOfWeek == Calendar.SUNDAY) 6 else currentDayOfWeek - 2
                    
                    val isCurrentWeek = visibleDates.any { it.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) && it.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR) }

                    if (isCurrentWeek && todayIndex in 0..6) {
                        val yOffset = hourHeight * currentHour
                        Box(
                            modifier = Modifier
                                .offset(y = yOffset)
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(Color.Red.copy(alpha = 0.5f))
                        )
                        Box(
                            modifier = Modifier
                                .offset(x = (dayWidth * todayIndex), y = yOffset - 3.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color.Red)
                        )
                    }
                }
                }

                Column(
                    modifier = Modifier
                        .width(48.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                val target = maxOf(0, scrollState.value - (viewportHeightPx / 2).toInt())
                                scrollState.animateScrollTo(target)
                            }
                        }
                    ) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Scroll Up", tint = MaterialTheme.colorScheme.primary)
                    }
                    
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .width(24.dp)
                    ) {
                        val trackHeight = constraints.maxHeight.toFloat()
                        val thumbHeightRatio = if (totalHeightPx > 0) (viewportHeightPx / totalHeightPx).coerceIn(0.1f, 1f) else 1f
                        val thumbHeightPx = trackHeight * thumbHeightRatio
                        val maxThumbY = trackHeight - thumbHeightPx
                        val currentScrollRatio = if (maxScroll > 0) (scrollState.value.toFloat() / maxScroll) else 0f
                        val thumbY = currentScrollRatio * maxThumbY

                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .width(4.dp)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                        )
                        
                        Box(
                            modifier = Modifier
                                .offset { IntOffset(0, thumbY.toInt()) }
                                .width(24.dp)
                                .height(with(density) { thumbHeightPx.toDp() })
                                .padding(horizontal = 8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .pointerInput(Unit) {
                                    detectVerticalDragGestures { change, dragAmount ->
                                        change.consume()
                                        if (maxThumbY > 0) {
                                            val dragRatio = dragAmount / maxThumbY
                                            val scrollDelta = dragRatio * maxScroll
                                            coroutineScope.launch {
                                                scrollState.scrollTo((scrollState.value + scrollDelta).toInt().coerceIn(0, maxScroll.toInt()))
                                            }
                                        }
                                    }
                                }
                        )
                    }

                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                val target = minOf(maxScroll.toInt(), scrollState.value + (viewportHeightPx / 2).toInt())
                                scrollState.animateScrollTo(target)
                            }
                        }
                    ) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Scroll Down", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
fun LiveBanner(liveCourses: List<ScheduledCourse>, onJoinRoom: (String) -> Unit, onShowDialog: () -> Unit) {
    if (liveCourses.isEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(id = R.string.no_live_classes),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    } else if (liveCourses.size == 1) {
        val course = liveCourses.first()
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.1f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFEF4444))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.live_now), color = Color(0xFFEF4444), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    CustomMarqueeText(text = course.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp)
                    CustomMarqueeText(text = course.teacher, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = { onJoinRoom(course.id) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text(stringResource(R.string.join_meeting), fontWeight = FontWeight.Bold)
                }
            }
        }
    } else {
        Card(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onShowDialog() },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.1f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFEF4444))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(id = R.string.multiple_live_classes, liveCourses.size),
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFEF4444),
                        fontSize = 16.sp
                    )
                }
                Text(
                    text = stringResource(id = R.string.view_classes),
                    color = Color(0xFFEF4444),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun MonthCalendarGrid(selectedDate: Calendar, eventDates: List<String> = emptyList(), onDaySelected: (Calendar) -> Unit) {
    val daysOfWeek = listOf(
        stringResource(id = R.string.day_mon), stringResource(id = R.string.day_tue), stringResource(id = R.string.day_wed),
        stringResource(id = R.string.day_thu), stringResource(id = R.string.day_fri), stringResource(id = R.string.day_sat), stringResource(id = R.string.day_sun)
    )
    
    var currentCalendar by remember { mutableStateOf(Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }) }
    
    val currentMonthFormat = SimpleDateFormat("MMMM yyyy", LocalLocale.current.platformLocale)
    val monthTitle = currentMonthFormat.format(currentCalendar.time).replaceFirstChar { if (it.isLowerCase()) it.titlecase(LocalLocale.current.platformLocale) else it.toString() }

    val tempCal = currentCalendar.clone() as Calendar
    tempCal.set(Calendar.DAY_OF_MONTH, 1)
    val startDayOfWeek = tempCal.get(Calendar.DAY_OF_WEEK)
    val startOffset = if (startDayOfWeek == Calendar.SUNDAY) 6 else startDayOfWeek - 2
    
    val daysInMonth = currentCalendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    
    val todayCal = Calendar.getInstance()

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(monthTitle, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onBackground)
                Row {
                    IconButton(onClick = {
                        val newCal = currentCalendar.clone() as Calendar
                        newCal.add(Calendar.MONTH, -1)
                        currentCalendar = newCal
                    }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    IconButton(onClick = {
                        val newCal = currentCalendar.clone() as Calendar
                        newCal.add(Calendar.MONTH, 1)
                        currentCalendar = newCal
                    }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                daysOfWeek.forEach { day ->
                    Text(day, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(12.dp))

            val totalCells = startOffset + daysInMonth
            val rows = (totalCells + 6) / 7

            for (r in 0 until rows) {
                Row(modifier = Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    for (c in 0 until 7) {
                        val idx = r * 7 + c
                        val day = idx - startOffset + 1
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (day in 1..daysInMonth) {
                                val isSelected = day == selectedDate.get(Calendar.DAY_OF_MONTH) &&
                                                 currentCalendar.get(Calendar.MONTH) == selectedDate.get(Calendar.MONTH) &&
                                                 currentCalendar.get(Calendar.YEAR) == selectedDate.get(Calendar.YEAR)
                                                 
                                val isToday = day == todayCal.get(Calendar.DAY_OF_MONTH) &&
                                              currentCalendar.get(Calendar.MONTH) == todayCal.get(Calendar.MONTH) &&
                                              currentCalendar.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR)
                                              
                                val dateString = String.format(java.util.Locale.US, "%04d-%02d-%02d", currentCalendar.get(Calendar.YEAR), currentCalendar.get(Calendar.MONTH) + 1, day)
                                val hasEvent = eventDates.contains(dateString)

                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .aspectRatio(1f)
                                        .clip(CircleShape)
                                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                        .clickable {
                                            val newSelectedDate = currentCalendar.clone() as Calendar
                                            newSelectedDate.set(Calendar.DAY_OF_MONTH, day)
                                            onDaySelected(newSelectedDate)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                        Text(
                                            text = day.toString(),
                                            color = if (isSelected) Color.White else if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 15.sp
                                        )
                                        if (hasEvent) {
                                            Spacer(Modifier.height(2.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(4.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isSelected) Color.White else MaterialTheme.colorScheme.primary)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LiveCoursesDialog(courses: List<ScheduledCourse>, onDismiss: () -> Unit, onJoinRoom: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.select_live_class), fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                courses.forEach { course ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha=0.1f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                CustomMarqueeText(text = course.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                                CustomMarqueeText(text = course.teacher, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = { onDismiss(); onJoinRoom(course.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                Text(stringResource(R.string.join_meeting), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
        containerColor = MaterialTheme.colorScheme.background
    )
}

@Composable
fun TimelineCard(
    time: String,
    dateString: String?,
    courseName: String,
    teacherName: String,
    role: String,
    onAction: () -> Unit
) {
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    
    LaunchedEffect(Unit) {
        while(true) {
            kotlinx.coroutines.delay(10000L)
            currentTime = System.currentTimeMillis()
        }
    }

    val status = remember(time, dateString, currentTime) {
        if (dateString == null) return@remember "upcoming"
        try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
            val timeParts = time.split("-").map { it.trim() }
            if (timeParts.size == 2) {
                val start = sdf.parse("$dateString ${timeParts[0]}")?.time ?: 0L
                val end = sdf.parse("$dateString ${timeParts[1]}")?.time ?: 0L
                when {
                    currentTime > end -> "ended"
                    currentTime in start..end -> "now"
                    else -> "upcoming"
                }
            } else "upcoming"
        } catch (e: Exception) {
            "upcoming"
        }
    }

    val textColor = when(status) {
        "ended" -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    
    val primaryColor = when(status) {
        "ended" -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.primary
    }

    val timeParts = remember(time) { time.split("-").map { it.trim() } }
    val startTime = timeParts.getOrElse(0) { time }
    val endTime = timeParts.getOrElse(1) { "" }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onAction() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (status == "now") {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(50))
                    .background(primaryColor)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.width(58.dp)
            ) {
                Text(
                    text = startTime,
                    fontWeight = FontWeight.Bold,
                    color = primaryColor,
                    fontSize = 16.sp
                )
                if (endTime.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = endTime,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = if (status == "ended") 0.6f else 1f
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                CustomMarqueeText(
                    text = courseName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Spacer(modifier = Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Person,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = if (status == "ended") 0.6f else 1f
                        )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    CustomMarqueeText(
                        text = teacherName,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = if (status == "ended") 0.6f else 1f
                        )
                    )
                }
            }

            if (status == "now" || status == "ended") {
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (status == "now") {
                                Color(0xFFEF4444).copy(alpha = 0.1f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f)
                            }
                        )
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (status == "now") {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEF4444))
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            text = if (status == "now") stringResource(R.string.status_now) else stringResource(R.string.status_has_ended),
                            color = if (status == "now") Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoursesContent(
    paddingValues: PaddingValues,
    role: String,
    token: String,
    courseViewModel: CourseViewModel = viewModel(),
    onCreateCourse: () -> Unit,
    onEnrollCourse: () -> Unit,
    onNavigateToCourse: (String) -> Unit
) {
    val courseState by courseViewModel.courseState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(token) {
        courseViewModel.fetchCourses(token)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = courseState) {
            is CourseState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            is CourseState.Success -> {
                if (state.courses.isEmpty()) {
                    Text(
                        text = if (role == "teacher") stringResource(id = R.string.no_courses_created) else stringResource(id = R.string.no_courses_enrolled),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(top = paddingValues.calculateTopPadding() + 8.dp, bottom = 100.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(state.courses) { course ->
                            val color = try { Color(android.graphics.Color.parseColor(course.color)) } catch (e: Exception) { Color(0xFF3B82F6) }
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier.fillMaxWidth().aspectRatio(0.85f)
                            ) {
                                Column(modifier = Modifier.fillMaxSize().clickable { onNavigateToCourse(course._id) }) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1.2f)
                                            .background(color.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Filled.AutoStories, 
                                            contentDescription = null, 
                                            modifier = Modifier.size(48.dp),
                                            tint = color
                                        )
                                    }
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surface)
                                            .padding(16.dp)
                                            .weight(1f)
                                    ) {
                                        CustomMarqueeText(
                                            text = course.name, 
                                            fontWeight = FontWeight.Bold, 
                                            fontSize = 16.sp
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        CustomMarqueeText(
                                            text = if (role == "teacher") stringResource(R.string.enrolled_count, course.enrolledStudents?.size ?: 0) else stringResource(R.string.teacher_prefix, course.teacher?.lastName ?: ""), 
                                            fontSize = 12.sp, 
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                    }
                                }
                            }
                        }
                    }
                }
            }
            is CourseState.Error -> {
                Text(
                    text = state.message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            else -> {}
        }

        ExtendedFloatingActionButton(
            onClick = if (role == "teacher") onCreateCourse else onEnrollCourse,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = paddingValues.calculateBottomPadding() + 16.dp, end = 24.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White
        ) {
            Icon(
                if (role == "teacher") Icons.Filled.Add else Icons.Filled.QrCodeScanner, 
                contentDescription = null
            )
            Spacer(Modifier.width(8.dp))
            Text(if (role == "teacher") stringResource(R.string.create) else stringResource(R.string.enroll), fontWeight = FontWeight.Bold)
        }
    }
    
}


@Composable
fun SettingsContent(
    paddingValues: PaddingValues,
    onNavigateToProfile: () -> Unit,
    onNavigateToDisplayLanguage: () -> Unit,
    onLogout: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = paddingValues,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = stringResource(id = R.string.settings_title),
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        item { SettingsOptionCard(title = stringResource(id = R.string.profile_settings), icon = Icons.Outlined.Person, onClick = onNavigateToProfile) }
        item { SettingsOptionCard(title = stringResource(id = R.string.display_language_settings), icon = Icons.Outlined.Language, onClick = onNavigateToDisplayLanguage) }
        
        item {
            val context = LocalContext.current
            SettingsOptionCard(
                title = stringResource(id = R.string.notifications_settings),
                icon = Icons.Outlined.NotificationsActive,
                onClick = { 
                    val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }
            )
        }
        
        item { Spacer(modifier = Modifier.height(32.dp)) }
        item {
            Button(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444), contentColor = Color.White),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                Icon(Icons.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(id = R.string.logout), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun SettingsOptionCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.clickable { onClick() }.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun CustomMarqueeText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null
) {
    var textWidth by remember { mutableIntStateOf(0) }
    var containerWidth by remember { mutableIntStateOf(0) }
    val offsetX = remember { androidx.compose.animation.core.Animatable(0f) }

    val density = androidx.compose.ui.platform.LocalDensity.current
    val gap = 48.dp
    val gapPx = with(density) { gap.toPx().toInt() }

    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(textWidth, containerWidth, gapPx) {
        if (textWidth > 0 && containerWidth > 0 && textWidth > containerWidth) {
            val distanceToScroll = textWidth + gapPx
            val duration = ((distanceToScroll / 120f) * 1000f).toLong().coerceAtLeast(3000L)
            
            MarqueeSync.updateMaxAndStart(duration)
            
            MarqueeSync.trigger.collect {
                offsetX.snapTo(0f)
                offsetX.animateTo(
                    targetValue = -distanceToScroll.toFloat(),
                    animationSpec = androidx.compose.animation.core.tween(
                        durationMillis = duration.toInt(), 
                        easing = androidx.compose.animation.core.LinearEasing
                    )
                )
            }
        } else {
            offsetX.snapTo(0f)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .onSizeChanged { containerWidth = it.width }
    ) {
        Row(
            modifier = Modifier
                .wrapContentWidth(unbounded = true, align = Alignment.Start)
                .offset { IntOffset(offsetX.value.toInt(), 0) }
        ) {
            Text(
                text = text,
                color = color,
                fontSize = fontSize,
                fontWeight = fontWeight,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.onSizeChanged { textWidth = it.width }
            )

            if (textWidth > containerWidth) {
                Spacer(modifier = Modifier.width(gap))
                Text(
                    text = text,
                    color = color,
                    fontSize = fontSize,
                    fontWeight = fontWeight,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}


@Composable
fun NotificationPermissionDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isPermanentlyDenied: Boolean,
    onOpenSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.permission_notifications_title), fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Text(
                text = if (isPermanentlyDenied) stringResource(R.string.permission_notifications_denied)
                       else stringResource(R.string.permission_notifications_rationale),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(onClick = if (isPermanentlyDenied) onOpenSettings else onConfirm) {
                Text(if (isPermanentlyDenied) stringResource(R.string.open_settings) else stringResource(R.string.allow))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.not_now))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

object MarqueeSync {
    val trigger = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    var maxDuration = 0L
    private var isRunning = false
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.SupervisorJob())

    fun updateMaxAndStart(duration: Long) {
        if (duration > maxDuration) {
            maxDuration = duration
        }
        if (!isRunning) {
            isRunning = true
            scope.launch {
                kotlinx.coroutines.delay(100)
                while(true) {
                    trigger.tryEmit(Unit)
                    kotlinx.coroutines.delay(maxDuration + 3000L)
                }
            }
        }
    }
}
