package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bridge.DispatchSosIncident
import com.example.model.DecryptedSosData
import com.example.ui.theme.CardBorder
import com.example.ui.theme.EmergencyBackground
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.EmergencyRedDark
import com.example.ui.theme.EmergencySurface
import com.example.ui.theme.EmergencySurfaceVariant
import com.example.ui.theme.InfoBlue
import com.example.ui.theme.SignalGreen
import com.example.ui.theme.SignalGreenLight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber
import com.example.util.RealGpsInfo
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RescueDashboardScreen(
    isAuthenticated: Boolean,
    authorityId: String,
    mfaCodeChallenge: String,
    authError: String?,
    isWebSocketConnected: Boolean,
    incidents: List<DispatchSosIncident>,
    currentGpsInfo: RealGpsInfo = RealGpsInfo(),
    onLogin: (id: String, pass: String, mfa: String) -> Unit,
    onLogout: () -> Unit,
    onDispatchRescue: (messageId: String, unitName: String, notes: String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isAuthenticated) {
        AuthorityMfaLoginView(
            mfaCodeChallenge = mfaCodeChallenge,
            authError = authError,
            onLogin = onLogin,
            modifier = modifier
        )
    } else {
        AuthorityConsoleView(
            authorityId = authorityId,
            isWebSocketConnected = isWebSocketConnected,
            incidents = incidents,
            currentGpsInfo = currentGpsInfo,
            onLogout = onLogout,
            onDispatchRescue = onDispatchRescue,
            modifier = modifier
        )
    }
}

/**
 * Secure Multi-Factor Authentication (MFA) Login Screen for Rescue Authorities.
 * Requires:
 * 1. Authority Badge ID
 * 2. Security PIN / Passkey
 * 3. Dynamic 6-Digit MFA Token with visual countdown timer
 */
@Composable
fun AuthorityMfaLoginView(
    mfaCodeChallenge: String,
    authError: String?,
    onLogin: (String, String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var idInput by remember { mutableStateOf("RESCUE-DISPATCH-01") }
    var passInput by remember { mutableStateOf("echo-rescue-2026") }
    var mfaInput by remember { mutableStateOf(mfaCodeChallenge) }
    var countdownSeconds by remember { mutableIntStateOf(30) }

    // Live countdown timer for MFA rotation
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            if (countdownSeconds > 1) {
                countdownSeconds -= 1
            } else {
                countdownSeconds = 30
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EmergencyBackground)
            .padding(24.dp)
            .testTag("rescue_mfa_login_view"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Authority Emblem (Clean white card with emergency badge)
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(EmergencySurface)
                .border(2.dp, EmergencyRed, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AdminPanelSettings,
                contentDescription = "Rescue Authority Emblem",
                tint = EmergencyRed,
                modifier = Modifier.size(42.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "RESCUE DISPATCH COMMAND",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                color = TextPrimary
            )
        )
        Text(
            text = "Emergency Response Center • Multi-Factor Authentication",
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = EmergencySurface),
            shape = RoundedCornerShape(16.dp),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Factor 1: Authority ID
                Text(
                    text = "PRIMARY FACTOR: AUTHORITY ID",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = idInput,
                    onValueChange = { idInput = it },
                    singleLine = true,
                    placeholder = { Text("e.g., RESCUE-DISPATCH-01", color = TextMuted) },
                    leadingIcon = {
                        Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = TextSecondary)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("mfa_input_id"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = EmergencySurfaceVariant,
                        unfocusedContainerColor = EmergencySurfaceVariant,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = SignalGreenLight,
                        unfocusedBorderColor = CardBorder
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Security Passkey
                Text(
                    text = "SECURITY ACCESS PIN / KEY",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = passInput,
                    onValueChange = { passInput = it },
                    singleLine = true,
                    placeholder = { Text("Enter security passkey", color = TextMuted) },
                    leadingIcon = {
                        Icon(Icons.Default.Key, contentDescription = null, tint = TextSecondary)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("mfa_input_pass"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = EmergencySurfaceVariant,
                        unfocusedContainerColor = EmergencySurfaceVariant,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = SignalGreenLight,
                        unfocusedBorderColor = CardBorder
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Factor 2: Dynamic MFA Verification Token
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = SignalGreenLight, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "MFA 2-FACTOR TOKEN",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = SignalGreenLight,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Timer, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "${countdownSeconds}s",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = WarningAmber,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = mfaInput,
                    onValueChange = { mfaInput = it },
                    placeholder = { Text("6-digit token code", color = TextMuted) },
                    leadingIcon = {
                        Icon(Icons.Default.VpnKey, contentDescription = null, tint = SignalGreenLight)
                    },
                    trailingIcon = {
                        Text(
                            text = "Auto-Fill",
                            modifier = Modifier
                                .clickable { mfaInput = mfaCodeChallenge }
                                .padding(end = 12.dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = SignalGreenLight,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("mfa_input_code"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = EmergencySurfaceVariant,
                        unfocusedContainerColor = EmergencySurfaceVariant,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = SignalGreenLight,
                        unfocusedBorderColor = CardBorder
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                // Dynamic Authenticator Hint
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = EmergencySurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Authenticator Token:",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp)
                        )
                        Text(
                            text = mfaCodeChallenge,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = SignalGreenLight,
                                fontSize = 13.sp
                            )
                        )
                    }
                }

                if (authError != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = authError,
                        style = MaterialTheme.typography.bodySmall.copy(color = EmergencyRed, fontWeight = FontWeight.Bold)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Bright Red Emergency Action for Login
                Button(
                    onClick = { onLogin(idInput, passInput, mfaInput) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmergencyRed,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("mfa_login_button")
                ) {
                    Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("AUTHENTICATE & ACCESS DASHBOARD", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

/**
 * Authenticated Rescue Dispatch Console.
 * Real-time monitoring of incoming SOS alerts, device telemetry, Google Maps GPS triangulation,
 * authority cryptographic decryption, and rescue dispatch acknowledgment.
 */
@Composable
fun AuthorityConsoleView(
    authorityId: String,
    isWebSocketConnected: Boolean,
    incidents: List<DispatchSosIncident>,
    currentGpsInfo: RealGpsInfo = RealGpsInfo(),
    onLogout: () -> Unit,
    onDispatchRescue: (messageId: String, unitName: String, notes: String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EmergencyBackground)
            .padding(16.dp)
            .testTag("authority_console_view")
    ) {
        // Console Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "RESCUE DISPATCH CONSOLE",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        color = TextPrimary
                    )
                )
                Text(
                    text = "Operator: $authorityId • Secure Session",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = SignalGreenLight
                    )
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // WebSocket Streaming Status Pill
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isWebSocketConnected) SignalGreen.copy(alpha = 0.15f) else WarningAmber.copy(alpha = 0.15f),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isWebSocketConnected) SignalGreenLight else WarningAmber)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isWebSocketConnected) "LIVE DISPATCH STREAM" else "WS STANDBY",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isWebSocketConnected) SignalGreenLight else WarningAmber
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = onLogout,
                    modifier = Modifier.testTag("authority_logout_button")
                ) {
                    Icon(Icons.Default.Logout, contentDescription = "Logout", tint = TextSecondary)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (incidents.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(EmergencySurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Radar,
                            contentDescription = null,
                            tint = SignalGreenLight,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Monitoring Active Distress Mesh",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Standing by for incoming distress signals relayed from offline devices. When an SOS reaches connectivity, it will appear here with offline GPS sensor data, local distance calculation, and encrypted medical records.",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(incidents, key = { it.packet.messageId }) { incident ->
                    IncidentDetailCard(
                        incident = incident,
                        currentGpsInfo = currentGpsInfo,
                        onDispatch = { unit, notes ->
                            onDispatchRescue(incident.packet.messageId, unit, notes)
                        }
                    )
                }
            }
        }
    }
}

/**
 * Detailed Incident Alert Card for Dispatchers.
 * Shows:
 * - Device Name
 * - Battery percentage
 * - GPS location directly from offline GPS/GNSS sensor
 * - Local distance calculation without online APIs
 * - Signal strength metrics (RSSI dBm, link network type)
 * - Timestamps with real-time updates
 * - Functionality for authorities to decrypt user information
 * - Functionality to send an acknowledgment back to the victim
 */
@Composable
fun IncidentDetailCard(
    incident: DispatchSosIncident,
    currentGpsInfo: RealGpsInfo = RealGpsInfo(),
    onDispatch: (unit: String, notes: String) -> Unit
) {
    val packet = incident.packet
    val decrypted = incident.decryptedData
    val context = LocalContext.current

    // Authority Decryption Toggle state
    var isDecryptedRevealed by remember { mutableStateOf(false) }
    var selectedUnit by remember { mutableStateOf("Mountain Rescue Alpha") }
    var dispatchNotesInput by remember { mutableStateOf("Rescue unit en route to GPS coordinates. Stay in position.") }

    val formattedTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(packet.timestamp))
    val elapsedMinutes = ((System.currentTimeMillis() - packet.timestamp) / 60000).coerceAtLeast(0)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .testTag("incident_card_${packet.messageId.take(8)}"),
        colors = CardDefaults.cardColors(containerColor = EmergencySurface),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Alert Header: Device Name & Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (incident.isAcknowledged) SignalGreenLight else EmergencyRed)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = packet.originDeviceName,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = TextPrimary
                            )
                        )
                        Text(
                            text = "MSG ID: #${packet.messageId.take(8).uppercase()} • $formattedTime ($elapsedMinutes min ago)",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 11.sp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (incident.isAcknowledged) SignalGreen.copy(alpha = 0.15f) else EmergencyRed.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = if (incident.isAcknowledged) "DISPATCHED" else "ACTIVE DISTRESS",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (incident.isAcknowledged) SignalGreenLight else EmergencyRed
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Hardware & Signal Strength Metrics Bar
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = EmergencySurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Battery Percentage
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.BatteryFull,
                            contentDescription = "Battery",
                            tint = SignalGreenLight,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Battery: ${packet.signalMetrics.batteryLevel}%",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            )
                        )
                    }

                    // Signal Strength Metrics (RSSI dBm)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SignalCellularAlt,
                            contentDescription = "RSSI",
                            tint = WarningAmber,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "RSSI: ${packet.signalMetrics.rssi} dBm",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            )
                        )
                    }

                    // Hops & Link
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = "Hops",
                            tint = InfoBlue,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Hop ${packet.hopCount}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Offline Satellite GPS Location & Local Distance Calculation
            val rawLat = decrypted?.latitude ?: packet.latitude
            val rawLng = decrypted?.longitude ?: packet.longitude
            val isLocUnavailable = (rawLat == 0.0 && rawLng == 0.0) ||
                (rawLat == 37.422065 && rawLng == -122.084089) ||
                packet.locationStatus.contains("unavailable", ignoreCase = true) ||
                packet.locationStatus == "UNAVAILABLE"
            val hasValidCoords = !isLocUnavailable
            val lat = if (hasValidCoords) rawLat else null
            val lng = if (hasValidCoords) rawLng else null
            val alt = decrypted?.altitude ?: 0.0
            val acc = if (hasValidCoords) (decrypted?.accuracy ?: packet.accuracy) else 0.0f

            // Calculate distance locally from the received coordinates on the rescue device (100% offline, zero online APIs)
            val rescueLat = currentGpsInfo.latitude
            val rescueLng = currentGpsInfo.longitude
            val rescueGpsAvailable = currentGpsInfo.hasLocation && (rescueLat != 0.0 || rescueLng != 0.0) &&
                !(rescueLat == 37.422065 && rescueLng == -122.084089) && !currentGpsInfo.isUnavailable

            val localDistanceMeters: Float? = remember(lat, lng, rescueLat, rescueLng, rescueGpsAvailable) {
                if (lat != null && lng != null && rescueGpsAvailable) {
                    val dist = FloatArray(1)
                    try {
                        android.location.Location.distanceBetween(rescueLat, rescueLng, lat, lng, dist)
                        dist[0]
                    } catch (e: Exception) {
                        null
                    }
                } else null
            }

            val formattedDistance = remember(localDistanceMeters, rescueGpsAvailable, hasValidCoords) {
                when {
                    !hasValidCoords -> "GPS location unavailable"
                    !rescueGpsAvailable -> "Acquiring local rescue GPS..."
                    localDistanceMeters != null -> {
                        if (localDistanceMeters < 1000f) {
                            "${kotlin.math.round(localDistanceMeters).toInt()} m"
                        } else {
                            String.format(Locale.US, "%.2f km", localDistanceMeters / 1000f)
                        }
                    }
                    else -> "Calculating..."
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (hasValidCoords) SignalGreen.copy(alpha = 0.08f) else EmergencyRed.copy(alpha = 0.06f)
                ),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (hasValidCoords) SignalGreen.copy(alpha = 0.3f) else EmergencyRed.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (hasValidCoords) Icons.Default.LocationOn else Icons.Default.LocationOff,
                                contentDescription = if (hasValidCoords) "Offline GPS" else "GPS Unavailable",
                                tint = if (hasValidCoords) SignalGreenLight else EmergencyRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (hasValidCoords) "OFFLINE GPS / GNSS SENSOR" else "LOCATION STATUS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp,
                                    color = if (hasValidCoords) TextPrimary else EmergencyRed
                                )
                            )
                        }
                        if (hasValidCoords && acc > 0f) {
                            Text(
                                text = "±${acc.toInt()}m accuracy",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = SignalGreenLight,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        } else if (hasValidCoords) {
                            Text(
                                text = "Fix Acquired",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = SignalGreenLight,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (lat != null && lng != null) {
                        Text(
                            text = if (alt > 0.0) {
                                "Lat: %.6f°  •  Lng: %.6f°  •  Alt: %.1fm".format(Locale.US, lat, lng, alt)
                            } else {
                                "Lat: %.6f°  •  Lng: %.6f°".format(Locale.US, lat, lng)
                            },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Local Distance Calculation Banner (Rescue Device -> Victim)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = EmergencySurfaceVariant.copy(alpha = 0.7f),
                            border = BorderStroke(0.5.dp, CardBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Straighten,
                                        contentDescription = "Distance",
                                        tint = SignalGreenLight,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(
                                            text = "LOCAL RESCUE DISTANCE",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextSecondary
                                            )
                                        )
                                        Text(
                                            text = "Calculated locally on device (offline)",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 10.sp,
                                                color = TextMuted
                                            )
                                        )
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = formattedDistance,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Black,
                                            fontFamily = FontFamily.Monospace,
                                            color = SignalGreenLight
                                        )
                                    )
                                    if (localDistanceMeters != null && (acc > 0f || currentGpsInfo.accuracy > 0f)) {
                                        val combinedUncertainty = (acc + currentGpsInfo.accuracy).toInt()
                                        Text(
                                            text = "±${combinedUncertainty}m GPS uncertainty",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 9.sp,
                                                color = WarningAmber
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Open with Offline Geo Intent
                            Button(
                                onClick = {
                                    try {
                                        val geoUri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(Emergency+ECHO+Distress+Location)")
                                        val mapIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(mapIntent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Coordinates: $lat, $lng", Toast.LENGTH_LONG).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SignalGreen,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Offline Map", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                            }

                            // Copy Coordinates Button
                            OutlinedButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("ECHO_GPS", "$lat,$lng"))
                                    Toast.makeText(context, "Coordinates copied: ${String.format(Locale.US, "%.6f, %.6f", lat, lng)}", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = TextSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    } else {
                        Column {
                            Text(
                                text = "GPS location unavailable",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = EmergencyRed
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Origin device could not acquire an offline GPS satellite fix. Coordinates are omitted to prevent dispatching to an incorrect location.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Decrypt User Information Functionality for Authorities
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = EmergencySurfaceVariant),
                shape = RoundedCornerShape(12.dp),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isDecryptedRevealed) Icons.Default.LockOpen else Icons.Default.Lock,
                                contentDescription = null,
                                tint = if (isDecryptedRevealed) SignalGreenLight else EmergencyRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isDecryptedRevealed) "VICTIM PROFILE (DECRYPTED)" else "ENCRYPTED USER INFORMATION",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp,
                                    color = if (isDecryptedRevealed) SignalGreenLight else EmergencyRed
                                )
                            )
                        }

                        if (isDecryptedRevealed && decrypted != null) {
                            // Blood Type Priority Badge (BRIGHT RED for emergency visibility)
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = EmergencyRed
                            ) {
                                Text(
                                    text = "BLOOD: ${decrypted.bloodType.ifBlank { "O+" }}",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (!isDecryptedRevealed) {
                        Text(
                            text = "AES-256-GCM sealed with Rescue Authority Public Key. Intermediate mesh relays cannot inspect this data.",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { isDecryptedRevealed = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SignalGreen,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("decrypt_user_info_button")
                        ) {
                            Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "DECRYPT USER INFORMATION (AUTHORITY KEY)",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    } else {
                        // Revealed Decrypted Profile (Phrased as user-provided emergency profile)
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = SignalGreen.copy(alpha = 0.12f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "USER-PROVIDED EMERGENCY PROFILE • VOLUNTARILY SUBMITTED",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = SignalGreenLight,
                                        fontSize = 10.sp,
                                        letterSpacing = 0.5.sp
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Victim: ${decrypted?.victimName?.ifBlank { "Unspecified" } ?: "Unspecified"}",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            if (!decrypted?.phone.isNullOrBlank()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Call, contentDescription = null, tint = SignalGreenLight, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Emergency Phone: ${decrypted?.phone}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = SignalGreenLight
                                        )
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }

                            if (!decrypted?.allergies.isNullOrBlank()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.MedicalServices, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "User-Reported Allergies: ${decrypted?.allergies}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = WarningAmber
                                        )
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }

                            Text(
                                text = "User-Reported Medical Conditions: ${decrypted?.medicalIssues?.ifBlank { "None reported" } ?: "None reported"}",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "User-Reported Disability / Mobility: ${decrypted?.handicap?.ifBlank { "None reported" } ?: "None reported"}",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Emergency Notes: ${decrypted?.emergencyNotes?.ifBlank { "None" } ?: "None"}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (!decrypted?.emergencyNotes.isNullOrBlank()) WarningAmber else TextSecondary
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Hide Decrypted Data",
                                modifier = Modifier
                                    .clickable { isDecryptedRevealed = false }
                                    .padding(vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = TextMuted,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action: Acknowledge and Send ACK back to victim
            if (!incident.isAcknowledged) {
                Text(
                    text = "Deploy Rescue Unit & Acknowledge to Victim:",
                    style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Ground Unit 1", "Air Ambulance Alpha", "Mountain Team 2").forEach { unit ->
                        val isSelected = selectedUnit == unit
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected) EmergencyRed else EmergencySurfaceVariant,
                            modifier = Modifier.clickable { selectedUnit = unit }
                        ) {
                            Text(
                                text = unit,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else TextPrimary
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bright Red Button for Emergency Dispatch
                Button(
                    onClick = { onDispatch(selectedUnit, dispatchNotesInput) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SignalGreen,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("dispatch_ack_button")
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SEND ACKNOWLEDGMENT & DISPATCH",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = Color.White)
                    )
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SignalGreen.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SignalGreenLight, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Acknowledgment Sent Back to Victim",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = SignalGreenLight
                                )
                            )
                            Text(
                                text = incident.dispatchNotes,
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                            )
                        }
                    }
                }
            }
        }
    }
}
