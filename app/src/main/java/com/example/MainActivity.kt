package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.service.EchoMeshService
import com.example.ui.screens.MainAppScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.util.PermissionUtils
import com.example.viewmodel.EchoMainViewModel
import com.example.viewmodel.EchoViewModelFactory
import com.example.viewmodel.RescueDashboardViewModel

class MainActivity : ComponentActivity() {

    private val mainViewModel: EchoMainViewModel by viewModels {
        EchoViewModelFactory(application as EchoApplication)
    }

    private val dashboardViewModel: RescueDashboardViewModel by viewModels {
        EchoViewModelFactory(application as EchoApplication)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (PermissionUtils.areMeshPermissionsGranted(this)) {
            EchoMeshService.startService(applicationContext)
        }

        setContent {
            MyApplicationTheme {
                MainAppScreen(
                    mainViewModel = mainViewModel,
                    dashboardViewModel = dashboardViewModel
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mainViewModel.refreshHardwareState()
        if (PermissionUtils.areMeshPermissionsGranted(this)) {
            EchoMeshService.startService(applicationContext)
        }
    }
}

