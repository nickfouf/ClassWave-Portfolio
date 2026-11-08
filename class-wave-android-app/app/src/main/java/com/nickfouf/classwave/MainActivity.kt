package com.nickfouf.classwave

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.nickfouf.classwave.ui.ForgotPasswordScreen
import com.nickfouf.classwave.ui.LoginScreen
import com.nickfouf.classwave.ui.WelcomeScreen
import com.nickfouf.classwave.ui.ProfileSettingsScreen
import com.nickfouf.classwave.ui.DisplayLanguageSettingsScreen
import com.nickfouf.classwave.ui.CreateCourseScreen
import com.nickfouf.classwave.ui.EnrollCourseScreen
import com.nickfouf.classwave.ui.theme.ClassWaveTheme
import com.nickfouf.classwave.webrtc.MeetingMediaSettings

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        
        val sharedPref = getSharedPreferences("ClassWavePrefs", Context.MODE_PRIVATE)
        val savedLang = sharedPref.getString("app_language", "el") ?: "el"
        
        val locale = java.util.Locale(savedLang)
        java.util.Locale.setDefault(locale)
        val config = android.content.res.Configuration(resources.configuration)
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
        
        super.onCreate(savedInstanceState)
        com.nickfouf.classwave.utils.NotificationManagerHelper.createNotificationChannels(this)

        val savedToken = sharedPref.getString("auth_token", null)

        setContent {
            var themeMode by rememberSaveable { androidx.compose.runtime.mutableIntStateOf(sharedPref.getInt("theme_mode", 0)) }
            var currentLang by rememberSaveable { mutableStateOf(savedLang) }

            val darkTheme = when (themeMode) {
                1 -> false
                2 -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            ClassWaveTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var currentScreen by rememberSaveable { 
                        mutableStateOf(
                            intent.getStringExtra("navigate_to")?.takeIf { savedToken != null } ?: "welcome"
                        ) 
                    }
                    var promptHangupEvent by rememberSaveable { mutableStateOf(intent.getBooleanExtra("prompt_hangup", false)) }
                    var currentDashboardTab by rememberSaveable { mutableIntStateOf(0) }
                    var pendingMediaSettings by remember { mutableStateOf<MeetingMediaSettings?>(null) }

                    DisposableEffect(Unit) {
                        val listener = androidx.core.util.Consumer<android.content.Intent> { newIntent ->
                            val target = newIntent.getStringExtra("navigate_to")
                            val isHangup = newIntent.getBooleanExtra("prompt_hangup", false)
                            val currentToken = sharedPref.getString("auth_token", "") ?: ""
                            
                            if (isHangup) {
                                promptHangupEvent = true
                            }
                            
                            if (target != null && currentToken.isNotEmpty()) {
                                currentScreen = target
                            }
                        }
                        addOnNewIntentListener(listener)
                        onDispose { removeOnNewIntentListener(listener) }
                    }

                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(500)) + scaleIn(
                                initialScale = 0.95f,
                                animationSpec = tween(500)
                            )) togetherWith
                                    (fadeOut(animationSpec = tween(500)))
                        },
                        label = "Screen Transition"
                    ) { screen ->

                        when {
                            screen == "welcome" -> {
                                WelcomeScreen(
                                    onNavigateNext = {
                                        if (savedToken != null) {
                                            currentScreen = "dashboard_$savedToken"
                                        } else {
                                            currentScreen = "login"
                                        }
                                    }
                                )
                            }

                            screen == "login" -> {
                                LoginScreen(
                                    onLoginSuccess = { token ->
                                        sharedPref.edit().putString("auth_token", token).apply()
                                        currentScreen = "dashboard_$token"
                                    },
                                    onNavigateToRegister = {
                                        currentScreen = "register"
                                    },
                                    onNavigateToForgotPassword = {
                                        currentScreen = "forgot_password"
                                    }
                                )
                            }

                            screen == "forgot_password" -> {
                                ForgotPasswordScreen(
                                    onNavigateBack = {
                                        currentScreen = "login"
                                    }
                                )
                            }

                            screen == "register" -> {
                                com.nickfouf.classwave.ui.RegisterScreen(
                                    onRegisterSuccess = { token ->
                                        sharedPref.edit().putString("auth_token", token).apply()
                                        currentScreen = "dashboard_$token"
                                    },
                                    onNavigateToLogin = {
                                        currentScreen = "login"
                                    }
                                )
                            }

                            screen.startsWith("dashboard_") -> {
                                val token = screen.removePrefix("dashboard_")
                                com.nickfouf.classwave.ui.DashboardScreen(
                                    token = token,
                                    initialTab = currentDashboardTab,
                                    onTabSelected = { currentDashboardTab = it },
                                    onJoinRoom = { courseId ->
                                        pendingMediaSettings = null
                                        currentScreen = "pre_call_$courseId"
                                    },
                                    onLogout = {
                                        sharedPref.edit().remove("auth_token").apply()
                                        currentDashboardTab = 0
                                        currentScreen = "login"
                                    },
                                    onNavigateToProfile = { currentScreen = "profile_settings" },
                                    onNavigateToDisplayLanguage = { currentScreen = "display_language_settings" },
                                    onCreateCourse = { currentScreen = "create_course" },
                                    onEnrollCourse = { currentScreen = "enroll_course" },
                                    onNavigateToCourse = { courseId -> currentScreen = "course_$courseId" }
                                )
                            }

                            screen.startsWith("pre_call_") -> {
                                val courseId = screen.removePrefix("pre_call_")
                                val currentToken = sharedPref.getString("auth_token", "") ?: ""
                                com.nickfouf.classwave.ui.PreCallScreen(
                                    token = currentToken,
                                    roomId = courseId,
                                    onJoinCall = { micOn, camOn, mediaSettings ->
                                        pendingMediaSettings = mediaSettings
                                        currentScreen = "video_room_${courseId}_${micOn}_${camOn}"
                                    },
                                    onNavigateBack = {
                                        currentScreen = "dashboard_$currentToken"
                                    }
                                )
                            }

                            screen.startsWith("video_room_") -> {
                                val args = screen.removePrefix("video_room_").split("_")
                                val courseId = args[0]
                                val micOn = args.getOrNull(1)?.toBoolean() ?: false
                                val camOn = args.getOrNull(2)?.toBoolean() ?: false
                                val currentToken = sharedPref.getString("auth_token", "") ?: ""
                                com.nickfouf.classwave.ui.VideoRoomScreen(
                                    token = currentToken,
                                    roomId = courseId,
                                    initialMicOn = micOn,
                                    initialCamOn = camOn,
                                    initialMediaSettings = pendingMediaSettings,
                                    promptHangupEvent = promptHangupEvent,
                                    onPromptHandled = { promptHangupEvent = false },
                                    onLeaveRoom = {
                                        pendingMediaSettings = null
                                        currentScreen = "dashboard_$currentToken"
                                    }
                                )
                            }

                            screen == "profile_settings" -> {
                                val currentToken = sharedPref.getString("auth_token", "") ?: ""
                                ProfileSettingsScreen(
                                    token = currentToken,
                                    onNavigateBack = {
                                        currentScreen = "dashboard_$currentToken"
                                    }
                                )
                            }

                            screen == "display_language_settings" -> {
                                DisplayLanguageSettingsScreen(
                                    onNavigateBack = {
                                        val currentToken = sharedPref.getString("auth_token", "") ?: ""
                                        currentScreen = "dashboard_$currentToken"
                                    },
                                    themeMode = themeMode,
                                    onThemeChange = { newMode ->
                                        sharedPref.edit().putInt("theme_mode", newMode).apply()
                                        themeMode = newMode
                                    },
                                    currentLanguage = currentLang,
                                    onLanguageChange = { newLang ->
                                        if (newLang != currentLang) {
                                            sharedPref.edit().putString("app_language", newLang).apply()
                                            currentLang = newLang
                                            recreate()
                                        }
                                    }
                                )
                            }

                            screen == "create_course" -> {
                                val currentToken = sharedPref.getString("auth_token", "") ?: ""
                                CreateCourseScreen(
                                    token = currentToken,
                                    onNavigateBack = {
                                        currentScreen = "dashboard_$currentToken"
                                    }
                                )
                            }

                            screen == "enroll_course" -> {
                                val currentToken = sharedPref.getString("auth_token", "") ?: ""
                                EnrollCourseScreen(
                                    token = currentToken,
                                    onNavigateBack = {
                                        currentScreen = "dashboard_$currentToken"
                                    }
                                )
                            }

                            screen.startsWith("course_") -> {
                                val courseId = screen.removePrefix("course_")
                                val currentToken = sharedPref.getString("auth_token", "") ?: ""
                                com.nickfouf.classwave.ui.CourseScreen(
                                    token = currentToken,
                                    courseId = courseId,
                                    onNavigateBack = {
                                        currentScreen = "dashboard_$currentToken"
                                    },
                                    onStartMeeting = { roomCourseId ->
                                        currentScreen = "pre_call_$roomCourseId"
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
