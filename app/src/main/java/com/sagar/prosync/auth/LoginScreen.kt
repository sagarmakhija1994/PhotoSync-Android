package com.sagar.prosync.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sagar.prosync.data.AuthRepository
import com.sagar.prosync.data.AuthResult
import com.sagar.prosync.device.DeviceRegistrationRepository
import com.sagar.prosync.device.DeviceRegistrationResult
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // State Management
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var isPendingApproval by remember { mutableStateOf(false) }

    // Repositories
    val authRepo = remember { AuthRepository(context) }
    val deviceRepo = remember { DeviceRegistrationRepository(context) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isPendingApproval) {
                // Approval Waiting State
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Waiting for Approval",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Your account or device is currently PENDING.\nPlease contact the administrator to activate your access.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = { isPendingApproval = false }) {
                    Text("Back to Login")
                }
            } else {
                // Standard Login UI
                Text("PhotoSync Login", style = MaterialTheme.typography.headlineSmall)

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    singleLine = true,
                    enabled = !loading
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    enabled = !loading,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                )

                Spacer(Modifier.height(16.dp))

                if (error != null) {
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                }

                Button(
                    modifier = Modifier.fillMaxWidth(0.8f),
                    enabled = !loading && username.isNotBlank() && password.isNotBlank(),
                    onClick = {
                        loading = true
                        error = null
                        scope.launch {
                            when (val authResult = authRepo.login(username, password)) {
                                is AuthResult.Success -> {
                                    // No need to call deviceRepo.register() here anymore
                                    onLoginSuccess()
                                }
                                is AuthResult.PendingApproval -> {
                                    isPendingApproval = true
                                }
                                is AuthResult.Error -> {
                                    error = authResult.message
                                }
                            }
                            loading = false
                        }
                    }
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Login")
                    }
                }

                Spacer(Modifier.height(16.dp))

                TextButton(onClick = {
                    // This requires passing a 'onNavigateToRegister' lambda to your LoginScreen
                    onNavigateToRegister()
                }) {
                    Text("Don't have an account? Register here")
                }
            }
        }
    }
}