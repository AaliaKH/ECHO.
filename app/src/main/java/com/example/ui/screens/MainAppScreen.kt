package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.UserRole
import com.example.ui.components.RelayerGlowOverlay
import com.example.ui.theme.EmergencyBackground
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.EmergencyRedDark
import com.example.ui.theme.EmergencySurface
import com.example.ui.theme.EmergencySurfaceVariant
import com.example.ui.theme.SignalGreenLight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber
import com.example.viewmodel.EchoMainViewModel
import com.example.viewmodel.RescueDashboardViewModel

enum class EchoAppTab {
    HOME,
    HISTORY,
    SATELLITE,
    RESCUE_HQ,
    PROFILE
}

@Composable
fun MainAppScreen(
    mainViewModel: EchoMainViewModel,
    dashboardViewModel: RescueDashboardViewModel
) {
    val context = LocalContext.current

    // Collect state flows
    val userProfile by mainViewModel.userProfile.collectAsState()
    val batteryInfo by mainViewModel.batteryInfo.collectAsState()
    val gpsInfo by mainViewModel.gpsInfo.collectAsState()
    val hasInternet by mainViewModel.hasInternet.collectAsState()
    val connectedPeers by mainViewModel.connectedPeers.collectAsState()
    val discoveredCount by mainViewModel.discoveredCount.collectAsState()
    val incomingSos by mainViewModel.incomingSos.collectAsState()
    val activeOriginSos by mainViewModel.activeOriginSos.collectAsState()
    val isMeshActive by mainViewModel.isMeshActive.collectAsState()
    val isSosSending by mainViewModel.activeSosSending.collectAsState()
    val relayerGlowActive by mainViewModel.relayerGlowActive.collectAsState()
    val relayedPacketInfo by mainViewModel.relayedPacketInfo.collectAsState()
    val proximityUpdates by mainViewModel.proximityUpdates.collectAsState()

    val relayedExactDistanceMeters: Float? = remember(relayedPacketInfo, gpsInfo) {
        val packet = relayedPacketInfo
        if (packet != null && gpsInfo.hasLocation &&
            packet.latitude != 0.0 && packet.longitude != 0.0 &&
            !(packet.latitude == 37.422065 && packet.longitude == -122.084089)
        ) {
            val results = FloatArray(1)
            Location.distanceBetween(
                gpsInfo.latitude,
                gpsInfo.longitude,
                packet.latitude,
                packet.longitude,
                results
            )
            results[0]
        } else null
    }
    val mySosHistory by mainViewModel.mySosHistory.collectAsState()
    val cachedMeshMessages by mainViewModel.cachedMeshMessages.collectAsState()
    val offlineSosLogs by mainViewModel.offlineSosLogs.collectAsState()

    val isAuthenticated by dashboardViewModel.isAuthenticated.collectAsState()
    val authorityId by dashboardViewModel.authorityId.collectAsState()
    val mfaCodeChallenge by dashboardViewModel.mfaCodeChallenge.collectAsState()
    val authError by dashboardViewModel.authError.collectAsState()
    val isWsConnected by dashboardViewModel.isWebSocketConnected.collectAsState()
    val incidents by dashboardViewModel.incidents.collectAsState()

    val isAuthority = userProfile.role == UserRole.AUTHORITY || isAuthenticated
    val isLoggedIn = userProfile.isLoggedIn || isAuthenticated

    var selectedTab by remember(isAuthority) {
        mutableStateOf(if (isAuthority) EchoAppTab.RESCUE_HQ else EchoAppTab.HOME)
    }

    // If user is not yet logged in, show multi-role Login Screen first
    if (!isLoggedIn) {
        LoginScreen(
            onLoginCivilian = { name, phone, bloodType, allergies, medicalIssues, handicap, notes ->
                mainViewModel.loginAsCivilian(
                    name = name,
                    phone = phone,
                    bloodType = bloodType,
                    allergies = allergies,
                    medicalIssues = medicalIssues,
                    handicap = handicap,
                    emergencyNotes = notes
                )
                selectedTab = EchoAppTab.HOME
            },
            onLoginAuthority = { officerName, id, agency, pass ->
                val success = mainViewModel.loginAsAuthority(officerName, id, agency, pass)
                if (success) {
                    dashboardViewModel.setAuthenticated(id)
                    selectedTab = EchoAppTab.RESCUE_HQ
                }
                success
            }
        )
        return
    }

    // Permissions check
    val requiredPermissions = remember {
        com.example.util.PermissionUtils.getRequiredPermissions()
    }

    var meshPermissionsGranted by remember {
        mutableStateOf(com.example.util.PermissionUtils.areMeshPermissionsGranted(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        meshPermissionsGranted = com.example.util.PermissionUtils.areMeshPermissionsGranted(context)
        if (meshPermissionsGranted) {
            mainViewModel.startMeshNetwork(context)
            mainViewModel.refreshHardwareState()
        }
    }

    LaunchedEffect(Unit) {
        if (!meshPermissionsGranted) {
            permissionLauncher.launch(requiredPermissions)
        } else {
            mainViewModel.startMeshNetwork(context)
            mainViewModel.refreshHardwareState()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = EmergencyBackground,
            bottomBar = {
                NavigationBar(
                    containerColor = EmergencySurface,
                    tonalElevation = 4.dp
                ) {
                    if (isAuthority) {
                        // Authority Navigation Bar: Rescue HQ, History, Satellite, Profile
                        NavigationBarItem(
                            selected = selectedTab == EchoAppTab.RESCUE_HQ,
                            onClick = { selectedTab = EchoAppTab.RESCUE_HQ },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.AdminPanelSettings,
                                    contentDescription = "Rescue HQ",
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            label = { Text("Rescue HQ", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = WarningAmber,
                                selectedTextColor = WarningAmber,
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted,
                                indicatorColor = WarningAmber.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.testTag("nav_tab_rescue")
                        )

                        NavigationBarItem(
                            selected = selectedTab == EchoAppTab.HISTORY,
                            onClick = { selectedTab = EchoAppTab.HISTORY },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = "History",
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            label = { Text("History", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = SignalGreenLight,
                                selectedTextColor = SignalGreenLight,
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted,
                                indicatorColor = SignalGreenLight.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.testTag("nav_tab_history")
                        )

                        NavigationBarItem(
                            selected = selectedTab == EchoAppTab.SATELLITE,
                            onClick = { selectedTab = EchoAppTab.SATELLITE },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Public,
                                    contentDescription = "Satellite",
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            label = { Text("Satellite", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = SignalGreenLight,
                                selectedTextColor = SignalGreenLight,
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted,
                                indicatorColor = SignalGreenLight.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.testTag("nav_tab_satellite")
                        )

                        NavigationBarItem(
                            selected = selectedTab == EchoAppTab.PROFILE,
                            onClick = { selectedTab = EchoAppTab.PROFILE },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = "Profile",
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            label = { Text("Profile", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = TextPrimary,
                                selectedTextColor = TextPrimary,
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted,
                                indicatorColor = EmergencySurfaceVariant
                            ),
                            modifier = Modifier.testTag("nav_tab_profile")
                        )
                    } else {
                        // Civilian Navigation Bar: SOS, History, Satellite, Profile (Rescue HQ is strictly excluded!)
                        NavigationBarItem(
                            selected = selectedTab == EchoAppTab.HOME,
                            onClick = { selectedTab = EchoAppTab.HOME },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Emergency,
                                    contentDescription = "SOS",
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            label = { Text("SOS", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = EmergencyRed,
                                selectedTextColor = EmergencyRed,
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted,
                                indicatorColor = EmergencyRed.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.testTag("nav_tab_home")
                        )

                        NavigationBarItem(
                            selected = selectedTab == EchoAppTab.HISTORY,
                            onClick = { selectedTab = EchoAppTab.HISTORY },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = "History",
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            label = { Text("History", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = SignalGreenLight,
                                selectedTextColor = SignalGreenLight,
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted,
                                indicatorColor = SignalGreenLight.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.testTag("nav_tab_history")
                        )

                        NavigationBarItem(
                            selected = selectedTab == EchoAppTab.SATELLITE,
                            onClick = { selectedTab = EchoAppTab.SATELLITE },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Public,
                                    contentDescription = "Satellite",
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            label = { Text("Satellite", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = SignalGreenLight,
                                selectedTextColor = SignalGreenLight,
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted,
                                indicatorColor = SignalGreenLight.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.testTag("nav_tab_satellite")
                        )

                        NavigationBarItem(
                            selected = selectedTab == EchoAppTab.PROFILE,
                            onClick = { selectedTab = EchoAppTab.PROFILE },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.MedicalServices,
                                    contentDescription = "Profile",
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            label = { Text("Profile", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = TextPrimary,
                                selectedTextColor = TextPrimary,
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted,
                                indicatorColor = EmergencySurfaceVariant
                            ),
                            modifier = Modifier.testTag("nav_tab_profile")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (!meshPermissionsGranted) {
                        Surface(
                            color = EmergencyRedDark,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Mesh networking permissions required",
                                    style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontWeight = FontWeight.Bold),
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = { permissionLauncher.launch(requiredPermissions) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = EmergencyRedDark),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("GRANT", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                }
                            }
                        }
                    }

                    when (selectedTab) {
                        EchoAppTab.HOME -> {
                            HomeScreen(
                                deviceName = mainViewModel.meshManager.myDeviceName,
                                batteryInfo = batteryInfo,
                                gpsInfo = gpsInfo,
                                hasInternet = hasInternet,
                                userProfile = userProfile,
                                connectedPeers = connectedPeers,
                                isMeshActive = isMeshActive,
                                isSosSending = isSosSending,
                                relayerGlowActive = relayerGlowActive,
                                relayedPacketInfo = relayedPacketInfo,
                                onTriggerSos = { emergencyType -> mainViewModel.triggerSos(emergencyType) },
                                onDismissGlow = { mainViewModel.dismissRelayGlow() },
                                onRequestBatteryOpt = { mainViewModel.requestIgnoreBatteryOptimizations(context) },
                                onNavigateToProfile = { selectedTab = EchoAppTab.PROFILE },
                                onNavigateToJourney = { selectedTab = EchoAppTab.HISTORY },
                                onRefreshLocation = { mainViewModel.refreshHardwareState() },
                                onSimulateRelayHop = { mainViewModel.simulateRelayHopEvent() },
                                discoveredCount = discoveredCount,
                                activeOriginSos = activeOriginSos,
                                incomingSos = incomingSos,
                                onDismissIncomingSos = { mainViewModel.dismissIncomingSos() },
                                cachedMeshMessages = cachedMeshMessages,
                                offlineSosLogs = offlineSosLogs,
                                proximityUpdates = proximityUpdates
                            )
                        }
                        EchoAppTab.HISTORY -> {
                            MessageJourneyScreen(
                                sosList = mySosHistory,
                                onSimulateHop = { msgId -> mainViewModel.simulateHopForMySos(msgId) }
                            )
                        }
                        EchoAppTab.SATELLITE -> {
                            SatelliteScreen()
                        }
                        EchoAppTab.RESCUE_HQ -> {
                            if (isAuthority) {
                                RescueDashboardScreen(
                                    isAuthenticated = isAuthenticated,
                                    authorityId = authorityId,
                                    mfaCodeChallenge = mfaCodeChallenge,
                                    authError = authError,
                                    isWebSocketConnected = isWsConnected,
                                    incidents = incidents,
                                    currentGpsInfo = gpsInfo,
                                    onLogin = { id, pass, mfa -> dashboardViewModel.login(id, pass, mfa) },
                                    onLogout = {
                                        dashboardViewModel.logout()
                                        mainViewModel.logout()
                                    },
                                    onDispatchRescue = { msgId, unit, notes ->
                                        dashboardViewModel.dispatchRescue(msgId, unit, notes)
                                    }
                                )
                            } else {
                                // Fallback if civilian somehow landed on HQ
                                selectedTab = EchoAppTab.HOME
                            }
                        }
                        EchoAppTab.PROFILE -> {
                            SetupProfileScreen(
                                currentProfile = userProfile,
                                fixedDeviceName = mainViewModel.meshManager.myDeviceName,
                                onSaveProfile = { name, phone, blood, allergies, meds, handicap, notes ->
                                    mainViewModel.saveProfile(
                                        name = name,
                                        bloodType = blood,
                                        medicalIssues = meds,
                                        handicap = handicap,
                                        emergencyNotes = notes,
                                        allergies = allergies,
                                        phone = phone
                                    )
                                },
                                onLogout = {
                                    dashboardViewModel.logout()
                                    mainViewModel.logout()
                                }
                            )
                        }
                    }
                }
            }
        }

        // Compute device-to-device short range BLE proximity for relayer popup
        val relayedProximityDistanceMeters: Float? = remember(relayedPacketInfo, proximityUpdates) {
            val packet = relayedPacketInfo
            if (packet != null) {
                val originName = packet.originDeviceName
                val originId = packet.originDeviceId
                val ranging = proximityUpdates[originName]
                    ?: proximityUpdates[originId]
                    ?: proximityUpdates.values.find {
                        it.deviceName.equals(originName, ignoreCase = true) ||
                        it.deviceId.equals(originId, ignoreCase = true)
                    }
                ranging?.estimatedDistanceMeters
            } else null
        }

        // Global Screen Glowing Bright Red on SOS relay for visual confirmation
        RelayerGlowOverlay(
            isActive = relayerGlowActive,
            packet = relayedPacketInfo,
            exactDistanceMeters = relayedExactDistanceMeters,
            proximityDistanceMeters = relayedProximityDistanceMeters,
            onDismiss = { mainViewModel.dismissRelayGlow() }
        )
    }
}
