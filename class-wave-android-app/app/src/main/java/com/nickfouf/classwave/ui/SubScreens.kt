package com.nickfouf.classwave.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import com.nickfouf.classwave.R
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.window.Dialog

@Composable
private fun MeetingDetailsDialog(
    event: WeekEvent,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 400.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.meeting_details),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.cancel),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = event.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        event.dateString?.takeIf { it.isNotBlank() }?.let { date ->
                            MeetingDetailRow(
                                icon = Icons.Outlined.CalendarMonth,
                                text = date
                            )
                        }
                        event.originalTime?.takeIf { it.isNotBlank() }?.let { time ->
                            MeetingDetailRow(
                                icon = Icons.Outlined.Schedule,
                                text = time
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = onEdit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.edit_course),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.delete_meeting),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun MeetingDetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun timePickerInitialValue(value: String, defaultHour: Int): Pair<Int, Int> {
    val parts = value.trim().split(":")
    val hour = parts.getOrNull(0)
        ?.toIntOrNull()
        ?.takeIf { it in 0..23 }
        ?: defaultHour
    val minute = parts.getOrNull(1)
        ?.toIntOrNull()
        ?.takeIf { it in 0..59 }
        ?: 0
    return hour to minute
}

@Composable
fun ProfileSettingsScreen(token: String, onNavigateBack: () -> Unit, viewModel: ProfileViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val profileState by viewModel.profileState.collectAsState()

    var firstName by remember { mutableStateOf((profileState as? ProfileState.Success)?.firstName ?: "") }
    var lastName by remember { mutableStateOf((profileState as? ProfileState.Success)?.lastName ?: "") }
    var email by remember { mutableStateOf((profileState as? ProfileState.Success)?.email ?: "") }
    var iconColor by remember { mutableStateOf((profileState as? ProfileState.Success)?.iconColor ?: "#1D4ED8") }
    var showColorDialog by remember { mutableStateOf(false) }

    val extendedColorPalette = listOf(
        "#1D4ED8", "#10B981", "#F59E0B", "#EF4444",
        "#8B5CF6", "#EC4899", "#06B6D4", "#64748B"
    )

    LaunchedEffect(token) {
        viewModel.fetchProfile(token)
    }

    LaunchedEffect(profileState) {
        if (profileState is ProfileState.Success) {
            val successState = profileState as ProfileState.Success
            firstName = successState.firstName
            lastName = successState.lastName
            email = successState.email
            iconColor = successState.iconColor
        }
    }

    val initials = if (firstName.isNotBlank() || lastName.isNotBlank()) {
        "${firstName.take(1)}${lastName.take(1)}".uppercase()
    } else {
        ""
    }

    val avatarColor = try { Color(android.graphics.Color.parseColor(iconColor)) } catch(e: Exception) { Color(0xFF1D4ED8) }

    if (showColorDialog) {
        var tempColor by remember { mutableStateOf(iconColor) }

        AlertDialog(
            onDismissRequest = { showColorDialog = false },
            title = {
                Text(text = stringResource(id = R.string.choose_icon_color), fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    extendedColorPalette.chunked(4).forEach { rowColors ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            rowColors.forEach { colorHex ->
                                val color = try { Color(android.graphics.Color.parseColor(colorHex)) } catch(e: Exception) { Color.Gray }
                                Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .background(color.copy(alpha = 0.15f), CircleShape)
                                            .border(
                                                width = if (tempColor == colorHex) 2.dp else 1.dp,
                                                color = if (tempColor == colorHex) color else color.copy(alpha = 0.3f),
                                                shape = CircleShape
                                            )
                                            .clip(CircleShape)
                                            .clickable { tempColor = colorHex },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (initials.isNotBlank()) {
                                            AutoSizeText(
                                                text = initials,
                                                color = color,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 20.sp
                                            )
                                        } else {
                                            Icon(Icons.Outlined.Person, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
                                        }
                                    }
                                    if (tempColor == colorHex) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .offset(x = (-2).dp, y = (-2).dp)
                                                .size(22.dp)
                                                .background(MaterialTheme.colorScheme.surface, CircleShape)
                                                .border(2.dp, color, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Filled.Check, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    iconColor = tempColor
                    showColorDialog = false
                    viewModel.updateProfile(token, firstName, lastName, iconColor) {}
                }) {
                    Text(stringResource(id = R.string.ok).uppercase(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showColorDialog = false }) {
                    Text(stringResource(id = R.string.cancel).uppercase(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    SubScreenScaffold(
        title = stringResource(id = R.string.profile_settings),
        onNavigateBack = onNavigateBack
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val scale by animateFloatAsState(targetValue = if (isPressed) 0.9f else 1f, animationSpec = tween(150), label = "scale")

            Box(
                modifier = Modifier
                    .size(110.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null
                    ) { showColorDialog = true },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(avatarColor.copy(alpha = 0.1f), CircleShape)
                        .border(2.dp, avatarColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (initials.isNotBlank()) {
                        AutoSizeText(
                            text = initials,
                            color = avatarColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 40.sp
                        )
                    } else {
                        Icon(Icons.Outlined.Person, contentDescription = null, tint = avatarColor, modifier = Modifier.size(50.dp))
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 0.dp, y = 0.dp)
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                        .border(2.dp, avatarColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_custom_palette),
                        contentDescription = "Change Color",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(23.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(32.dp))

            if (profileState is ProfileState.Loading) {
                CircularProgressIndicator()
            } else {
                OutlinedTextField(
                    value = firstName,
                    onValueChange = { firstName = it },
                    label = { Text(stringResource(id = R.string.first_name_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = lastName,
                    onValueChange = { lastName = it },
                    label = { Text(stringResource(id = R.string.last_name_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Email,
                                contentDescription = "Email",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = stringResource(id = R.string.email_label),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = email,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))

                val hasTextChanges = if (profileState is ProfileState.Success) {
                    val state = profileState as ProfileState.Success
                    firstName.trim() != state.firstName || lastName.trim() != state.lastName
                } else false

                Button(
                    onClick = { viewModel.updateProfile(token, firstName.trim(), lastName.trim(), iconColor, onNavigateBack) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                    enabled = hasTextChanges && firstName.isNotBlank() && lastName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                        disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                ) {
                    Text(text = stringResource(id = R.string.save_changes), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseScreen(
    token: String,
    courseId: String,
    onNavigateBack: () -> Unit,
    onStartMeeting: (String) -> Unit = {},
    courseViewModel: CourseViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    profileViewModel: ProfileViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val courseState by courseViewModel.courseState.collectAsState()
    val profileState by profileViewModel.profileState.collectAsState()
    val context = LocalContext.current
    
    val course = (courseState as? CourseState.Success)?.courses?.find { it._id == courseId }
    val role = (profileState as? ProfileState.Success)?.role ?: "student"

    if (course == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }
    var newCourseName by remember { mutableStateOf(course.name) }
    
    var showScheduleDialog by remember { mutableStateOf(false) }
    var scheduleDate by remember { mutableStateOf("") }
    var scheduleStartTime by remember { mutableStateOf("") }
    var scheduleEndTime by remember { mutableStateOf("") }
    var scheduleTopic by remember { mutableStateOf("") }
    var isRepeatingEvent by remember { mutableStateOf(false) }
    var repeatDays by remember { mutableStateOf(setOf<Int>()) }
    var repeatUntilDate by remember { mutableStateOf("") }
    var editingScheduleItemId by remember { mutableStateOf<String?>(null) }
    var editingScheduleGroupId by remember { mutableStateOf<String?>(null) }
    var showDeleteSeriesDialog by remember { mutableStateOf(false) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    var showRepeatUntilDatePicker by remember { mutableStateOf(false) }

    var showTimeErrorDialog by remember { mutableStateOf(false) }

    var selectedEventForAction by remember { mutableStateOf<WeekEvent?>(null) }
    var showEventActionDialog by remember { mutableStateOf(false) }

    var viewMode by remember { mutableStateOf(CalendarViewMode.MONTH) }
    var selectedDate by remember { mutableStateOf(java.util.Calendar.getInstance()) }

    val eventDates = course.schedule?.map { it.date } ?: emptyList()

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_course_confirm_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.delete_course_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    courseViewModel.deleteCourse(token, course._id, onNavigateBack)
                }) {
                    Text(stringResource(R.string.yes), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.no))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showScheduleDialog) {
        AlertDialog(
            onDismissRequest = { showScheduleDialog = false },
            title = { Text(if (editingScheduleItemId != null) stringResource(R.string.edit_course) else stringResource(R.string.schedule_class), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = scheduleTopic,
                        onValueChange = { scheduleTopic = it },
                        label = { Text(stringResource(R.string.meeting_title)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = scheduleDate,
                            onValueChange = {},
                            label = { Text(if (isRepeatingEvent) stringResource(R.string.start_date) else stringResource(R.string.date_format_prompt), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            singleLine = true,
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Box(modifier = Modifier.matchParentSize().clickable { showDatePicker = true })
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = scheduleStartTime,
                                onValueChange = {},
                                label = { Text(stringResource(R.string.start_time), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                singleLine = true,
                                readOnly = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Box(modifier = Modifier.matchParentSize().clickable { showStartTimePicker = true })
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = scheduleEndTime,
                                onValueChange = {},
                                label = { Text(stringResource(R.string.end_time), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                singleLine = true,
                                readOnly = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Box(modifier = Modifier.matchParentSize().clickable { showEndTimePicker = true })
                        }
                    }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.repeat_weekly), modifier = Modifier.weight(1f))
                            Switch(
                                checked = isRepeatingEvent,
                                onCheckedChange = { isRepeatingEvent = it }
                            )
                        }
                        if (isRepeatingEvent) {
                            val daysOfWeek = listOf("M", "T", "W", "T", "F", "S", "S")
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                daysOfWeek.forEachIndexed { index, day ->
                                    val isSelected = repeatDays.contains(index)
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable {
                                                repeatDays = if (isSelected) repeatDays - index else repeatDays + index
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(day, color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = repeatUntilDate,
                                    onValueChange = {},
                                    label = { Text(stringResource(R.string.repeat_until), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    singleLine = true,
                                    readOnly = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Box(modifier = Modifier.matchParentSize().clickable { showRepeatUntilDatePicker = true })
                            }
                        }
                }
            },
            confirmButton = {
                val isFormValid = scheduleTopic.isNotBlank() && scheduleDate.isNotBlank() && scheduleStartTime.isNotBlank() && scheduleEndTime.isNotBlank() && (!isRepeatingEvent || (repeatDays.isNotEmpty() && repeatUntilDate.isNotBlank()))
                TextButton(
                    onClick = {
                        if (isFormValid) {
                            val startParts = scheduleStartTime.split(":")
                            val endParts = scheduleEndTime.split(":")
                            val startMins = startParts[0].toInt() * 60 + startParts[1].toInt()
                            val endMins = endParts[0].toInt() * 60 + endParts[1].toInt()

                            if (endMins <= startMins) {
                                showTimeErrorDialog = true
                            } else {
                                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                val newItems = mutableListOf<com.nickfouf.classwave.network.ScheduleItem>()
                                val newGroupId = if (isRepeatingEvent) java.util.UUID.randomUUID().toString() else null
                                val repDaysList = if (isRepeatingEvent) repeatDays.toList() else null
                                val repUntil = if (isRepeatingEvent) repeatUntilDate else null

                                if (isRepeatingEvent) {
                                    try {
                                        val startCal = java.util.Calendar.getInstance()
                                        startCal.time = sdf.parse(scheduleDate)!!
                                        val endCal = java.util.Calendar.getInstance()
                                        endCal.time = sdf.parse(repeatUntilDate)!!
                                        
                                        val currCal = startCal.clone() as java.util.Calendar
                                        while (!currCal.after(endCal)) {
                                            val dayOfWeek = currCal.get(java.util.Calendar.DAY_OF_WEEK)
                                            val mapDay = if (dayOfWeek == java.util.Calendar.SUNDAY) 6 else dayOfWeek - 2
                                            if (repeatDays.contains(mapDay)) {
                                                newItems.add(
                                                    com.nickfouf.classwave.network.ScheduleItem(
                                                        id = java.util.UUID.randomUUID().toString(),
                                                        date = sdf.format(currCal.time),
                                                        time = "$scheduleStartTime - $scheduleEndTime",
                                                        topic = scheduleTopic,
                                                        groupId = newGroupId,
                                                        repeatDays = repDaysList,
                                                        repeatUntilDate = repUntil
                                                    )
                                                )
                                            }
                                            currCal.add(java.util.Calendar.DAY_OF_MONTH, 1)
                                        }
                                    } catch (e: Exception) { e.printStackTrace() }
                                } else {
                                    newItems.add(
                                        com.nickfouf.classwave.network.ScheduleItem(
                                            id = java.util.UUID.randomUUID().toString(),
                                            date = scheduleDate,
                                            time = "$scheduleStartTime - $scheduleEndTime",
                                            topic = scheduleTopic,
                                            groupId = null,
                                            repeatDays = null,
                                            repeatUntilDate = null
                                        )
                                    )
                                }

                                val oldSchedule = course.schedule ?: emptyList()
                                val filteredSchedule = if (editingScheduleGroupId != null) {
                                    oldSchedule.filterNot { it.groupId == editingScheduleGroupId }
                                } else if (editingScheduleItemId != null) {
                                    oldSchedule.filterNot { it.id == editingScheduleItemId }
                                } else {
                                    oldSchedule
                                }
                                
                                val updatedSchedule = filteredSchedule + newItems
                                courseViewModel.updateCourse(token, course._id, course.name, updatedSchedule) {
                                    showScheduleDialog = false
                                    scheduleTopic = ""
                                    scheduleDate = ""
                                    scheduleStartTime = ""
                                    scheduleEndTime = ""
                                    isRepeatingEvent = false
                                    repeatDays = setOf()
                                    repeatUntilDate = ""
                                    editingScheduleItemId = null
                                    editingScheduleGroupId = null
                                }
                            }
                        }
                    },
                    enabled = isFormValid
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showScheduleDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showTimeErrorDialog) {
        AlertDialog(
            onDismissRequest = { showTimeErrorDialog = false },
            title = { Text(stringResource(R.string.invalid_time_range), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
            text = { Text(stringResource(R.string.invalid_time_range_message)) },
            confirmButton = {
                TextButton(onClick = { showTimeErrorDialog = false }) {
                    Text(stringResource(R.string.ok))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDate.timeInMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
                        cal.timeInMillis = it
                        scheduleDate = String.format(java.util.Locale.US, "%04d-%02d-%02d", cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH))
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showStartTimePicker) {
        val (initialHour, initialMinute) = timePickerInitialValue(
            value = scheduleStartTime,
            defaultHour = 10
        )
        val timePickerState = rememberTimePickerState(
            initialHour = initialHour,
            initialMinute = initialMinute
        )
        AlertDialog(
            onDismissRequest = { showStartTimePicker = false },
            title = { Text(stringResource(R.string.start_time)) },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    scheduleStartTime = String.format(java.util.Locale.US, "%02d:%02d", timePickerState.hour, timePickerState.minute)
                    showStartTimePicker = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showStartTimePicker = false }) { Text(stringResource(R.string.cancel)) }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showEndTimePicker) {
        val (initialHour, initialMinute) = timePickerInitialValue(
            value = scheduleEndTime,
            defaultHour = 11
        )
        val timePickerState = rememberTimePickerState(
            initialHour = initialHour,
            initialMinute = initialMinute
        )
        AlertDialog(
            onDismissRequest = { showEndTimePicker = false },
            title = { Text(stringResource(R.string.end_time)) },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    scheduleEndTime = String.format(java.util.Locale.US, "%02d:%02d", timePickerState.hour, timePickerState.minute)
                    showEndTimePicker = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showEndTimePicker = false }) { Text(stringResource(R.string.cancel)) }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showRepeatUntilDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDate.timeInMillis)
        DatePickerDialog(
            onDismissRequest = { showRepeatUntilDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
                        cal.timeInMillis = it
                        repeatUntilDate = String.format(java.util.Locale.US, "%04d-%02d-%02d", cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH))
                    }
                    showRepeatUntilDatePicker = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showRepeatUntilDatePicker = false }) { Text(stringResource(R.string.cancel)) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showEventActionDialog && selectedEventForAction != null) {
        val event = selectedEventForAction!!
        MeetingDetailsDialog(
            event = event,
            onDismiss = { showEventActionDialog = false },
            onDelete = {
                if (event.groupId != null) {
                    showEventActionDialog = false
                    showDeleteSeriesDialog = true
                } else {
                    val updatedSchedule = course.schedule?.filterNot {
                        if (event.id != null) it.id == event.id else (it.topic == event.name && it.date == event.dateString && it.time == event.originalTime)
                    } ?: emptyList()
                    courseViewModel.updateCourse(token, course._id, course.name, updatedSchedule) {
                        showEventActionDialog = false
                        selectedEventForAction = null
                    }
                }
            },
            onEdit = {
                showEventActionDialog = false
                editingScheduleItemId = event.id
                editingScheduleGroupId = event.groupId
                scheduleTopic = event.name
                scheduleDate = event.dateString ?: ""
                if (event.originalTime != null) {
                    val timeParts = event.originalTime.split("-").map { it.trim() }
                    if (timeParts.size == 2) {
                        scheduleStartTime = timeParts[0]
                        scheduleEndTime = timeParts[1]
                    }
                }
                if (event.groupId != null) {
                    isRepeatingEvent = true
                    repeatDays = event.repeatDays?.toSet() ?: setOf()
                    repeatUntilDate = event.repeatUntilDate ?: ""
                } else {
                    isRepeatingEvent = false
                    repeatDays = setOf()
                    repeatUntilDate = ""
                }
                showScheduleDialog = true
            }
        )
    }

    if (showDeleteSeriesDialog && selectedEventForAction != null) {
        val event = selectedEventForAction!!
        AlertDialog(
            onDismissRequest = { showDeleteSeriesDialog = false },
            title = { Text(stringResource(R.string.delete_meeting), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.delete_series_prompt)) },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = {
                        val updatedSchedule = course.schedule?.filterNot { it.id == event.id } ?: emptyList()
                        courseViewModel.updateCourse(token, course._id, course.name, updatedSchedule) {
                            showDeleteSeriesDialog = false
                            selectedEventForAction = null
                        }
                    }) {
                        Text(stringResource(R.string.delete_only_this), color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = {
                        val updatedSchedule = course.schedule?.filterNot { it.groupId == event.groupId } ?: emptyList()
                        courseViewModel.updateCourse(token, course._id, course.name, updatedSchedule) {
                            showDeleteSeriesDialog = false
                            selectedEventForAction = null
                        }
                    }) {
                        Text(stringResource(R.string.delete_entire_series), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSeriesDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(stringResource(R.string.rename_course), fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newCourseName,
                    onValueChange = { newCourseName = it },
                    label = { Text(stringResource(R.string.course_new_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newCourseName.isNotBlank() && newCourseName != course.name) {
                        courseViewModel.updateCourse(token, course._id, newCourseName.trim()) {
                            showRenameDialog = false
                        }
                    } else {
                        showRenameDialog = false
                    }
                }) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showQrDialog) {
        val qrBitmap = remember(course._id) { generateQrCode(course._id, 512) }
        AlertDialog(
            onDismissRequest = { showQrDialog = false },
            title = { Text(stringResource(R.string.scan_qr), fontWeight = FontWeight.Bold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    if (qrBitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = qrBitmap,
                            contentDescription = "QR Code",
                            modifier = Modifier.size(200.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    } else {
                        Text(stringResource(R.string.error_generating_qr), color = MaterialTheme.colorScheme.error)
                    }
                    Text(course._id, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                }
            },
            confirmButton = {
                TextButton(onClick = { showQrDialog = false }) {
                    Text(stringResource(R.string.ok))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    SubScreenScaffold(
        title = stringResource(R.string.course_details),
        onNavigateBack = onNavigateBack
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = course.name,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            if (!course.description.isNullOrBlank()) {
                Text(
                    text = course.description,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {

                            Text(stringResource(R.string.teacher_prefix, course.teacher?.lastName ?: ""), fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.instructor), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.People, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.enrolled_count, course.enrolledStudents?.size ?: 0), fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.students), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable {
                            val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("Course ID", course._id)
                            clipboardManager.setPrimaryClip(clip)
                            android.widget.Toast.makeText(context, context.getString(R.string.id_copied), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Filled.QrCode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(course._id, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(stringResource(R.string.course_id_tap_copy), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable {
                            showQrDialog = true
                        }
                    ) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.share_qr_code), fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.tap_to_show_qr), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            if (role == "teacher") {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { 
                        courseViewModel.setCourseLiveStatus(token, course._id, true) {
                            onStartMeeting(course._id) 
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(Color.White.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Videocam,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.start_meeting_now),
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Host a live interactive session",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 14.sp
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.course_schedule),
                    fontWeight = FontWeight.Bold, 
                    fontSize = 18.sp, 
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
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                stringResource(R.string.month), 
                                color = if (viewMode == CalendarViewMode.MONTH) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (viewMode == CalendarViewMode.WEEK) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { viewMode = CalendarViewMode.WEEK }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                stringResource(R.string.week), 
                                color = if (viewMode == CalendarViewMode.WEEK) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            val weekEvents = remember(course.schedule) {
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
                        
                        WeekEvent(
                            id = item.id,
                            name = item.topic,
                            dayIndex = dayIndex,
                            startHour = startHour,
                            durationHours = endHour - startHour,
                            color = try { Color(android.graphics.Color.parseColor(course.color)) } catch (e: Exception) { Color(0xFF3B82F6) },
                            dateString = item.date,
                            originalTime = item.time,
                            groupId = item.groupId,
                            repeatDays = item.repeatDays,
                            repeatUntilDate = item.repeatUntilDate
                        )
                    } catch (e: Exception) { null }
                } ?: emptyList()
            }

            androidx.compose.animation.Crossfade(targetState = viewMode, label = "Course Calendar View Mode") { mode ->
                when (mode) {
                    CalendarViewMode.MONTH -> {
                        Column {
                            MonthCalendarGrid(selectedDate = selectedDate, eventDates = eventDates, onDaySelected = { selectedDate = it })
                            Spacer(modifier = Modifier.height(16.dp))
                            val selectedDateString = String.format(java.util.Locale.US, "%04d-%02d-%02d", selectedDate.get(java.util.Calendar.YEAR), selectedDate.get(java.util.Calendar.MONTH) + 1, selectedDate.get(java.util.Calendar.DAY_OF_MONTH))
                            val eventsForDay = weekEvents.filter { it.dateString == selectedDateString }
                            if (eventsForDay.isNotEmpty()) {
                                Text(
                                    text = stringResource(R.string.scheduled_meetings),
                                    fontWeight = FontWeight.Bold, 
                                    fontSize = 16.sp, 
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                                eventsForDay.forEach { event ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth().clickable { 
                                            if (role == "teacher") {
                                                selectedEventForAction = event
                                                showEventActionDialog = true
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(event.name, fontWeight = FontWeight.Bold)
                                                Text(event.originalTime ?: "", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    CalendarViewMode.WEEK -> {
                        WeekCalendarGrid(
                            selectedDate = selectedDate, 
                            events = weekEvents,
                            onDaySelected = { selectedDate = it },
                            onEventClick = { event ->
                                if (role == "teacher") {
                                    selectedEventForAction = event
                                    showEventActionDialog = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(400.dp)
                        )
                    }
                }
            }

            if (role == "teacher") {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { 
                        scheduleDate = String.format(java.util.Locale.US, "%04d-%02d-%02d", selectedDate.get(java.util.Calendar.YEAR), selectedDate.get(java.util.Calendar.MONTH) + 1, selectedDate.get(java.util.Calendar.DAY_OF_MONTH))
                        editingScheduleItemId = null
                        editingScheduleGroupId = null
                        scheduleTopic = ""
                        scheduleStartTime = ""
                        scheduleEndTime = ""
                        isRepeatingEvent = false
                        repeatDays = setOf()
                        repeatUntilDate = ""
                        showScheduleDialog = true 
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.EditCalendar, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.schedule_class), fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(8.dp))

                SettingsItemCard(
                    title = stringResource(R.string.rename_course),
                    subtitle = stringResource(R.string.course_new_name),
                    icon = Icons.Outlined.Edit,
                    onClick = { showRenameDialog = true }
                )
                SettingsItemCard(
                    title = stringResource(R.string.delete_course),
                    subtitle = stringResource(R.string.delete_course_confirm_title),
                    icon = Icons.Outlined.Delete,
                    onClick = { showDeleteDialog = true },
                    iconTint = MaterialTheme.colorScheme.error,
                    textColor = MaterialTheme.colorScheme.error
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsItemCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    textColor: Color = MaterialTheme.colorScheme.onBackground
) {
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
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = textColor)
                    Text(text = subtitle, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SelectionDialog(
    title: String,
    options: List<Pair<String, androidx.compose.ui.graphics.vector.ImageVector>>,
    selectedIndex: Int,
    onDismissRequest: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var tempSelectedIndex by remember { mutableIntStateOf(selectedIndex) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { tempSelectedIndex = index }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = tempSelectedIndex == index,
                            onClick = { tempSelectedIndex = index },
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Icon(option.second, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = option.first, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(tempSelectedIndex) }) {
                Text(stringResource(id = R.string.ok).uppercase(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(id = R.string.cancel).uppercase(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun DisplayLanguageSettingsScreen(
    onNavigateBack: () -> Unit,
    themeMode: Int,
    onThemeChange: (Int) -> Unit,
    currentLanguage: String,
    onLanguageChange: (String) -> Unit
) {
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    val themeOptions = listOf(
        Pair(stringResource(id = R.string.system_default), Icons.Outlined.SettingsSuggest),
        Pair(stringResource(id = R.string.light_mode), Icons.Outlined.LightMode),
        Pair(stringResource(id = R.string.dark_mode), Icons.Outlined.DarkMode)
    )

    val languageOptions = listOf(
        Pair(stringResource(id = R.string.english), Icons.Outlined.Language),
        Pair(stringResource(id = R.string.greek), Icons.Outlined.Translate)
    )

    val currentThemeLabel = themeOptions.getOrNull(themeMode)?.first ?: ""
    val currentLangLabel = if (currentLanguage == "el") stringResource(id = R.string.greek) else stringResource(id = R.string.english)

    if (showThemeDialog) {
        SelectionDialog(
            title = stringResource(id = R.string.theme_mode),
            options = themeOptions,
            selectedIndex = themeMode,
            onDismissRequest = { showThemeDialog = false },
            onConfirm = {
                onThemeChange(it)
                showThemeDialog = false
            }
        )
    }

    if (showLanguageDialog) {
        val selectedIndex = if (currentLanguage == "el") 1 else 0
        SelectionDialog(
            title = stringResource(id = R.string.language),
            options = languageOptions,
            selectedIndex = selectedIndex,
            onDismissRequest = { showLanguageDialog = false },
            onConfirm = {
                val newLang = if (it == 1) "el" else "en"
                onLanguageChange(newLang)
                showLanguageDialog = false
            }
        )
    }

    SubScreenScaffold(
        title = stringResource(id = R.string.display_language_settings),
        onNavigateBack = onNavigateBack
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsItemCard(
                title = stringResource(id = R.string.theme_mode),
                subtitle = currentThemeLabel,
                icon = Icons.Outlined.Palette,
                onClick = { showThemeDialog = true }
            )

            SettingsItemCard(
                title = stringResource(id = R.string.language),
                subtitle = currentLangLabel,
                icon = Icons.Outlined.Translate,
                onClick = { showLanguageDialog = true }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubScreenScaffold(
    title: String,
    onNavigateBack: () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    BackHandler {
        onNavigateBack()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        content = content
    )
}

@Composable
fun CreateCourseScreen(token: String, onNavigateBack: () -> Unit, courseViewModel: CourseViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf("#1D4ED8") }

    val colorPalette = listOf(
        "#1D4ED8", "#10B981", "#F59E0B", "#EF4444",
        "#8B5CF6", "#EC4899", "#06B6D4", "#64748B"
    )

    SubScreenScaffold(
        title = stringResource(id = R.string.create_course),
        onNavigateBack = onNavigateBack
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(id = R.string.course_name)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                )
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(id = R.string.course_description_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                )
            )
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(id = R.string.choose_icon_color),
                modifier = Modifier.align(Alignment.Start),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                colorPalette.chunked(4).forEach { rowColors ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        rowColors.forEach { colorHex ->
                            val color = try { Color(android.graphics.Color.parseColor(colorHex)) } catch(e: Exception) { Color.Gray }
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (selectedColor == colorHex) 3.dp else 0.dp,
                                        color = if (selectedColor == colorHex) MaterialTheme.colorScheme.onBackground else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedColor = colorHex },
                                contentAlignment = Alignment.Center
                            ) {
                                if (selectedColor == colorHex) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        courseViewModel.createCourse(token, name, description, selectedColor, onSuccess = onNavigateBack)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Filled.AddBox, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(id = R.string.create_course), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun EnrollCourseScreen(token: String, onNavigateBack: () -> Unit, courseViewModel: CourseViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    var courseId by remember { mutableStateOf("") }

    SubScreenScaffold(
        title = stringResource(id = R.string.enroll_course),
        onNavigateBack = onNavigateBack
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = courseId,
                onValueChange = { courseId = it },
                label = { Text(stringResource(id = R.string.course_code)) },
                placeholder = { Text(stringResource(id = R.string.enter_course_code)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF10B981),
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                )
            )
            Spacer(modifier = Modifier.height(16.dp))
            val context = LocalContext.current
            val scanner = remember {
                val options = com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions.Builder()
                    .setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE)
                    .build()
                com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(context, options)
            }

            Button(
                onClick = {
                    scanner.startScan()
                        .addOnSuccessListener { barcode ->
                            barcode.rawValue?.let { scannedCode ->
                                courseId = scannedCode
                            }
                        }
                        .addOnFailureListener { e ->
                            android.widget.Toast.makeText(context, "Scan failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha=0.1f), contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(id = R.string.scan_qr), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = {
                    if (courseId.isNotBlank()) {
                        courseViewModel.enrollCourse(token, courseId, onSuccess = onNavigateBack)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(id = R.string.enroll), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

fun generateQrCode(text: String, size: Int): androidx.compose.ui.graphics.ImageBitmap? {
    try {
        val bitMatrix = com.google.zxing.qrcode.QRCodeWriter().encode(
            text, 
            com.google.zxing.BarcodeFormat.QR_CODE, 
            size, 
            size
        )
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bmp = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bmp.setPixel(x, y, if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        return bmp.asImageBitmap()
    } catch (e: Exception) {
        return null
    }
}
