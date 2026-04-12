package com.sagar.prosync.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sagar.prosync.auth.LoginScreen
import com.sagar.prosync.auth.RegisterScreen
import com.sagar.prosync.ui.HomeScreen
import com.sagar.prosync.ui.SettingsScreen
import com.sagar.prosync.ui.SyncOptionsScreen

object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register" // New Route
    const val SYNC_OPTIONS = "sync_options"
    const val HOME = "home" // New
    const val SETTINGS = "settings" // New
}

@Composable
fun AppNavGraph(startDestination: String) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.SYNC_OPTIONS) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onNavigateToRegister = {
                    navController.navigate(Routes.REGISTER)
                }
            )
        }
        composable(Routes.REGISTER) {
            RegisterScreen(
                onRegisterSuccess = {
                    navController.popBackStack() // Go back to login after registering
                },
                onNavigateToLogin = {
                    navController.navigate(Routes.LOGIN)
                }
            )
        }
        composable(Routes.SYNC_OPTIONS) {
            SyncOptionsScreen()
        }
        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onLogout = {
                    // Navigate to Login and clear the entire backstack
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }

            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onLogout = {
                    // Navigate to Login and clear the entire backstack
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
