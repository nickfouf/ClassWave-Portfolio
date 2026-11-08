package com.nickfouf.classwave.ui

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airbnb.lottie.compose.*
import com.nickfouf.classwave.R


@Composable
fun LoginScreen(
    authViewModel: AuthViewModel = viewModel(),
    onLoginSuccess: (String) -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToForgotPassword: () -> Unit
) {
    val context = LocalContext.current

    BackHandler {
        (context as? Activity)?.moveTaskToBack(true)
    }

    val authState by authViewModel.authState.collectAsState()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.graduation_hat))
    val progress by animateLottieCompositionAsState(composition = composition, iterations = LottieConstants.IterateForever)

    LaunchedEffect(Unit) {
        authViewModel.resetState()
    }

    LaunchedEffect(authState) {
        if (authState is AuthState.Success) {
            onLoginSuccess((authState as AuthState.Success).token)
        }
    }

    Surface(modifier = Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }, color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().imePadding().verticalScroll(scrollState).padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            LottieAnimation(composition = composition, progress = { progress }, modifier = Modifier.size(120.dp).padding(bottom = 16.dp))

            Text(text = stringResource(id = R.string.app_name), fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary, letterSpacing = (-0.5).sp)
            Text(text = stringResource(id = R.string.education_starts_here), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp, bottom = 40.dp))

val isErrorState = authState is AuthState.Error
            val strongErrorColor = MaterialTheme.colorScheme.error

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(id = R.string.email_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = isErrorState,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onBackground, 
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground, 
                    errorTextColor = MaterialTheme.colorScheme.onBackground,
                    focusedBorderColor = MaterialTheme.colorScheme.primary, 
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), 
                    errorBorderColor = strongErrorColor,
                    focusedLabelColor = MaterialTheme.colorScheme.primary, 
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant, 
                    errorLabelColor = strongErrorColor,
                    errorCursorColor = strongErrorColor,
                    cursorColor = MaterialTheme.colorScheme.primary, 
                    focusedContainerColor = MaterialTheme.colorScheme.surface, 
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    errorContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(id = R.string.password_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    focusManager.clearFocus()
                    if (email.isNotBlank() && password.isNotBlank()) authViewModel.login(email, password)
                }),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = image, 
                            contentDescription = stringResource(id = R.string.toggle_password_visibility), 
                            tint = if (isErrorState) strongErrorColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                isError = isErrorState,
                supportingText = {
                    Text(
                        text = if (isErrorState) stringResource(id = (authState as AuthState.Error).messageId) else " ", 
                        color = if (isErrorState) strongErrorColor else Color.Transparent, 
                        modifier = Modifier.fillMaxWidth(),
                        fontWeight = FontWeight.SemiBold
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onBackground, 
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground, 
                    errorTextColor = MaterialTheme.colorScheme.onBackground,
                    focusedBorderColor = MaterialTheme.colorScheme.primary, 
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), 
                    errorBorderColor = strongErrorColor,
                    focusedLabelColor = MaterialTheme.colorScheme.primary, 
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant, 
                    errorLabelColor = strongErrorColor,
                    errorCursorColor = strongErrorColor,
                    errorTrailingIconColor = strongErrorColor,
                    cursorColor = MaterialTheme.colorScheme.primary, 
                    focusedContainerColor = MaterialTheme.colorScheme.surface, 
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    errorContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            Box(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 32.dp), contentAlignment = Alignment.CenterEnd) {
                Text(
                    text = stringResource(id = R.string.forgot_password), 
                    color = MaterialTheme.colorScheme.primary, 
                    fontWeight = FontWeight.SemiBold, 
                    fontSize = 14.sp, 
                    modifier = Modifier.clickable { onNavigateToForgotPassword() }
                )
            }

            Button(
                onClick = { focusManager.clearFocus(); authViewModel.login(email, password) },
                modifier = Modifier.fillMaxWidth().height(56.dp).animateContentSize(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White, disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                enabled = authState !is AuthState.Loading && email.isNotBlank() && password.isNotBlank()
            ) {
                if (authState is AuthState.Loading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 3.dp)
                } else {
                    Text(text = stringResource(id = R.string.login_button), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(id = R.string.new_to_classwave), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                Text(text = stringResource(id = R.string.create_account), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.clickable { onNavigateToRegister() })
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
