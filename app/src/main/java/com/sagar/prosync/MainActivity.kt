package com.sagar.prosync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.sagar.prosync.auth.LoginScreen
import com.sagar.prosync.auth.RegisterScreen
import com.sagar.prosync.data.SessionStore
import com.sagar.prosync.data.SettingsStore
import com.sagar.prosync.device.DeviceManager
import com.sagar.prosync.ui.HomeScreen
import com.sagar.prosync.ui.ServerConfigScreen
import com.sagar.prosync.ui.SettingsScreen
import com.sagar.prosync.ui.SetupScreen
import com.sagar.prosync.ui.theme.ProSyncTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Ensure device ID exists early
        DeviceManager.getOrCreateDeviceId(this)

        setContent {
            ProSyncTheme {
                val context = LocalContext.current
                val sessionStore = remember { SessionStore(context) }
                val settingsStore = remember { SettingsStore(context) }

                // --- FIX: Injected Server Config check into the launch logic ---
                var currentScreen by remember {
                    mutableStateOf(
                        when {
                            settingsStore.serverUrl.isBlank() -> "SERVER_CONFIG"
                            sessionStore.getToken().isNullOrEmpty() -> "LOGIN"
                            !settingsStore.isSetupComplete -> "SETUP"
                            else -> "HOME"
                        }
                    )
                }

                when (currentScreen) {
                    // --- NEW: Route for Server Configuration ---
                    "SERVER_CONFIG" -> {
                        ServerConfigScreen(
                            onConfigSaved = {
                                // Once they enter the URL, send them to log in
                                currentScreen = "LOGIN"
                            }
                        )
                    }

                    "LOGIN" -> {
                        LoginScreen(
                            onLoginSuccess = {
                                currentScreen = if (settingsStore.isSetupComplete) "HOME" else "SETUP"
                            },
                            onNavigateToRegister = {
                                // Switch the screen to REGISTER
                                currentScreen = "REGISTER"
                            }
                        )
                    }

                    "REGISTER" -> {
                        // Assuming your RegisterScreen has similar callbacks
                        RegisterScreen(
                            onRegisterSuccess = {
                                // Once registered, send them back to login (or directly to SETUP if your API auto-logs them in)
                                currentScreen = "LOGIN"
                            },
                            onNavigateToLogin = {
                                // If they click "Already have an account? Login"
                                currentScreen = "LOGIN"
                            }
                        )
                    }

                    "SETUP" -> {
                        SetupScreen(
                            onFinishSetup = {
                                currentScreen = "HOME"
                            }
                        )
                    }

                    "HOME" -> {
                        HomeScreen(
                            onNavigateToSettings = {
                                currentScreen = "SETTINGS"
                            }
                        )
                    }

                    "SETTINGS" -> {
                        SettingsScreen(
                            onNavigateBack = {
                                currentScreen = "HOME"
                            }
                        )
                    }
                }
            }
        }
    }
}