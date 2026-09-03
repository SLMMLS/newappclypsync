package com.clipsync.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.clipsync.app.data.Repository
import com.clipsync.app.service.ReceiverService

class MainActivity : ComponentActivity() {
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* fine either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val repository = Repository(applicationContext)

        setContent {
            ClipSyncTheme {
                val navController = rememberNavController()
                val pairedPc by repository.pairedPc.collectAsState(initial = null)

                // Keep the receiver alive whenever a PC is paired, not just
                // while this screen happens to be open.
                LaunchedEffect(pairedPc?.deviceId) {
                    if (pairedPc != null) {
                        ContextCompat.startForegroundService(
                            this@MainActivity,
                            Intent(this@MainActivity, ReceiverService::class.java),
                        )
                    }
                }

                NavHost(navController, startDestination = if (pairedPc == null) "pairing" else "home") {
                    composable("home") {
                        HomeScreen(
                            repository = repository,
                            onSettings = { navController.navigate("settings") },
                        )
                    }
                    composable("pairing") {
                        PairingScreen(repository = repository, onPaired = { navController.navigate("home") })
                    }
                    composable("settings") {
                        SettingsScreen(
                            repository = repository,
                            onBack = { navController.popBackStack() },
                            onUnpaired = { navController.navigate("pairing") },
                        )
                    }
                }
            }
        }
    }
}
