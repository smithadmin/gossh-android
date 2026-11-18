package com.example.gossh

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) { GoSshApp() }
            }
        }
    }
}

@Composable
fun GoSshApp() {
    var screen by remember { mutableStateOf("main") }
    // With no sessions open, the connect form is the home screen.
    val eff = if (screen == "main" && AppStore.sessions.isEmpty()) "connect" else screen
    BackHandler(enabled = eff != "main" && !(eff == "connect" && AppStore.sessions.isEmpty())) {
        screen = "main"
    }
    // safeDrawing includes the keyboard, so the terminal shrinks (and re-fits) when it opens.
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        when (eff) {
            "connect" -> ConnectScreen(
                canCancel = AppStore.sessions.isNotEmpty(),
                onCancel = { screen = "main" },
                onConnected = { screen = "main" },
            )
            "buttons" -> ButtonEditorScreen { screen = "main" }
            "colors" -> ColorEditorScreen { screen = "main" }
            else -> MainScreen { screen = it }
        }
    }
}
