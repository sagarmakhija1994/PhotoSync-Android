package com.sagar.prosync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.sagar.prosync.data.SessionStore
import com.sagar.prosync.device.DeviceManager
import com.sagar.prosync.navigation.AppNavGraph
import com.sagar.prosync.navigation.Routes
import com.sagar.prosync.ui.theme.ProSyncTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Ensure device ID exists early
        DeviceManager.getOrCreateDeviceId(this)

        val sessionStore = SessionStore(this)
        val startDestination =
            if (sessionStore.getToken() == null) Routes.LOGIN else Routes.HOME

        setContent {
            ProSyncTheme {
                AppNavGraph(startDestination = startDestination)
            }
        }

        /*Thread {
            try {
                val url = java.net.URL("http://192.168.0.181:8000/health")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.requestMethod = "GET"

                val code = conn.responseCode
                android.util.Log.d("NET_TEST", "Response code = $code")
            } catch (e: Exception) {
                android.util.Log.e("NET_TEST", "Network error", e)
            }
        }.start()*/
    }
}
