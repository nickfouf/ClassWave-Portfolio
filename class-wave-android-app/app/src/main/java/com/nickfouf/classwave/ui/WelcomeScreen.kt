package com.nickfouf.classwave.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nickfouf.classwave.R
import kotlinx.coroutines.delay

@Composable
fun WelcomeScreen(onNavigateNext: () -> Unit) {
    var showTitle by remember { mutableStateOf(true) }
    var showIcon by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var isExiting by remember { mutableStateOf(false) }

    val titleAlpha by animateFloatAsState(targetValue = if (isExiting) 0f else if (showTitle) 1f else 0f, animationSpec = tween(600), label = "titleAlpha")
    val titleOffsetY by animateFloatAsState(targetValue = if (isExiting) -100f else if (showTitle) 0f else -50f, animationSpec = tween(600, easing = FastOutSlowInEasing), label = "titleOffset")
    val iconAlpha by animateFloatAsState(targetValue = if (isExiting) 0f else if (showIcon) 1f else 0f, animationSpec = tween(500), label = "iconAlpha")
    val iconScale by animateFloatAsState(targetValue = if (isExiting) 0.5f else if (showIcon) 1f else 0.5f, animationSpec = tween(500, easing = FastOutSlowInEasing), label = "iconScale")
    val infoAlpha by animateFloatAsState(targetValue = if (isExiting) 0f else if (showInfo) 1f else 0f, animationSpec = tween(600), label = "infoAlpha")
    val infoOffsetY by animateFloatAsState(targetValue = if (isExiting) 100f else if (showInfo) 0f else 50f, animationSpec = tween(600, easing = FastOutSlowInEasing), label = "infoOffset")

    LaunchedEffect(key1 = true) {
        delay(300)
        showIcon = true
        delay(300)
        showInfo = true
        delay(2000)
        isExiting = true
        delay(600)
        onNavigateNext()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(
                text = stringResource(id = R.string.app_name),
                fontSize = 48.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = (-1).sp,
                modifier = Modifier.graphicsLayer { alpha = titleAlpha; translationY = titleOffsetY }
            )
            Spacer(modifier = Modifier.height(56.dp))
            Icon(
                imageVector = Icons.Default.School,
                contentDescription = "University Icon",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(72.dp).graphicsLayer { alpha = iconAlpha; scaleX = iconScale; scaleY = iconScale }
            )
            Spacer(modifier = Modifier.height(40.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer { alpha = infoAlpha; translationY = infoOffsetY }) {
                Text(text = stringResource(id = R.string.developed_by), fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = stringResource(id = R.string.developer_name), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = stringResource(id = R.string.developer_role), fontSize = 16.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            }
        }
    }
}
