package com.example.ui.screens

import java.util.Locale
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.data.MeshMessageEntity
import com.example.data.OfflineSosLogEntity
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.example.ui.components.LiveGoogleMapCard
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.location.Location
import com.example.model.SosPacket
import com.example.model.UserProfile
import com.example.ui.components.DeviceIdentityCard
import com.example.ui.components.RadarDeviceMarker
import com.example.ui.components.RelayerGlowOverlay
import com.example.util.DeviceRangingInfo
import com.example.ui.theme.CardBorder
import com.example.ui.theme.EmergencyBackground
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.EmergencyRedDark
import com.example.ui.theme.EmergencyRedGlow
import com.example.ui.theme.EmergencySurface
import com.example.ui.theme.EmergencySurfaceVariant
import com.example.ui.theme.SignalGreen
import com.example.ui.theme.SignalGreenLight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber
import com.example.util.LocationUtils
import com.example.util.RealBatteryInfo
import com.example.util.RealGpsInfo

@Composable
fun HomeScreen(
    deviceName: String,
    batteryInfo: RealBatteryInfo,
    gpsInfo: RealGpsInfo,
    hasInternet: Boolean,
    userProfile: UserProfile,
    connectedPeers: List<String>,
    isMeshActive: Boolean,
    isSosSending: Boolean,
    relayerGlowActive: Boolean,
    relayedPacketInfo: SosPacket?,
    onTriggerSos: (String) -> Unit,
    onDismissGlow: () -> Unit,
    onRequestBatteryOpt: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToJourney: () -> Unit,
    onRefreshLocation: () -> Unit = {},
    onSimulateRelayHop: () -> Unit = {},
    discoveredCount: Int = 0,
    activeOriginSos: SosPacket? = null,
    incomingSos: SosPacket? = null,
    onDismissIncomingSos: () -> Unit = {},
    cachedMeshMessages: List<MeshMessageEntity> = emptyList(),
    offlineSosLogs: List<OfflineSosLogEntity> = emptyList(),
    proximityUpdates: Map<String, DeviceRangingInfo> = emptyMap(),
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var selectedEmergencyType by remember { mutableStateOf("Medical") }

    // Pulse animation for the emergency button
    val infiniteTransition = rememberInfiniteTransition(label = "SosPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(EmergencyBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .testTag("home_screen"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Brand Title in Black
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(EmergencyRed.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Emergency,
                        contentDescription = null,
                        tint = EmergencyRed,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "ECHO",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 3.sp,
                        color = Color.Black
                    )
                )
            }

            // Top Device Hardware & Battery Card
            DeviceIdentityCard(
                deviceName = deviceName,
                batteryInfo = batteryInfo,
                gpsInfo = gpsInfo,
                hasInternet = hasInternet
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Mesh Network Status Card (Compact layout)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("mesh_network_card"),
                colors = CardDefaults.cardColors(containerColor = EmergencySurface),
                shape = RoundedCornerShape(12.dp),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Hub,
                                contentDescription = null,
                                tint = if (connectedPeers.isNotEmpty()) SignalGreenLight else WarningAmber,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Autonomous BLE Mesh",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (connectedPeers.isNotEmpty()) SignalGreen.copy(alpha = 0.2f) else EmergencySurfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (connectedPeers.isNotEmpty()) SignalGreenLight else WarningAmber)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (discoveredCount > connectedPeers.size) {
                                        "${connectedPeers.size} CONNECTED (${discoveredCount} NEARBY)"
                                    } else {
                                        "${connectedPeers.size} PEERS"
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        color = if (connectedPeers.isNotEmpty()) SignalGreenLight else TextSecondary
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Auto-relays distress hops offline via Nearby P2P & BLE.",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp),
                        maxLines = 1
                    )

                    if (connectedPeers.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            connectedPeers.take(3).forEach { peer ->
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = EmergencySurfaceVariant
                                ) {
                                    Text(
                                        text = peer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            color = SignalGreenLight
                                        )
                                    )
                                }
                            }
                            if (connectedPeers.size > 3) {
                                Text(
                                    text = "+${connectedPeers.size - 3} more",
                                    style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 10.sp)
                                )
                            }
                        }
                    }
                }
            }

            // Incoming SOS Card (Relay device display)
            if (incomingSos != null) {
                val isIncomingUnavailable = (incomingSos.latitude == 0.0 && incomingSos.longitude == 0.0) ||
                    (incomingSos.latitude == 37.422065 && incomingSos.longitude == -122.084089) ||
                    incomingSos.locationStatus.contains("unavailable", ignoreCase = true) ||
                    incomingSos.locationStatus == "UNAVAILABLE"

                // Exact distance calculated locally offline (Location.distanceBetween on WGS84 ellipsoid)
                val exactGpsDistanceMeters: Float? = remember(incomingSos, gpsInfo, isIncomingUnavailable) {
                    if (!isIncomingUnavailable &&
                        gpsInfo.hasLocation &&
                        !gpsInfo.isUnavailable &&
                        gpsInfo.latitude != 0.0 && gpsInfo.longitude != 0.0 &&
                        !(gpsInfo.latitude == 37.422065 && gpsInfo.longitude == -122.084089)
                    ) {
                        val results = FloatArray(1)
                        try {
                            Location.distanceBetween(
                                gpsInfo.latitude,
                                gpsInfo.longitude,
                                incomingSos.latitude,
                                incomingSos.longitude,
                                results
                            )
                            results[0]
                        } catch (e: Exception) {
                            null
                        }
                    } else null
                }

                val formattedExactDistance = remember(exactGpsDistanceMeters, isIncomingUnavailable) {
                    when {
                        isIncomingUnavailable -> "GPS location unavailable"
                        exactGpsDistanceMeters != null -> {
                            when {
                                exactGpsDistanceMeters < 10f -> String.format(Locale.US, "%.1f m", exactGpsDistanceMeters)
                                exactGpsDistanceMeters < 1000f -> "${kotlin.math.round(exactGpsDistanceMeters).toInt()} m"
                                else -> String.format(Locale.US, "%.2f km", exactGpsDistanceMeters / 1000f)
                            }
                        }
                        else -> "Acquiring local GPS..."
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("incoming_sos_card"),
                    colors = CardDefaults.cardColors(containerColor = EmergencyRedDark.copy(alpha = 0.95f)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(2.dp, EmergencyRed)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "🚨 DISTRESS SIGNAL",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        color = Color.White,
                                        letterSpacing = 1.sp
                                    )
                                )
                            }
                            IconButton(
                                onClick = onDismissIncomingSos,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Victim Name
                        Text(
                            text = "Victim: ${incomingSos.originDeviceName}",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Exact Distance (Offline local calculation)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.22f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "📏 Distance: $formattedExactDistance",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "🔄 Automatically relaying",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = SignalGreenLight
                            )
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Hop: ${incomingSos.hopCount + 1}",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Victim GPS Coordinates (strictly geographic info for Rescue authorities)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Victim Geographic Fix:",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.85f))
                            )
                            Text(
                                text = if (!isIncomingUnavailable) {
                                    "%.5f, %.5f (±%.0fm)".format(incomingSos.latitude, incomingSos.longitude, incomingSos.accuracy)
                                } else {
                                    "GPS location unavailable"
                                },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (!isIncomingUnavailable) Color.White else WarningAmber
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Privacy preservation: sensitive medical info hidden from relay users
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Victim medical & emergency profile is end-to-end encrypted for Rescue Authorities only.",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }
            }

            // Active SOS Status Card (Phone A display)
            if (activeOriginSos != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("active_sos_status_card"),
                    colors = CardDefaults.cardColors(containerColor = EmergencySurface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, EmergencyRed)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
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
                                        .background(EmergencyRed)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "SOS ACTIVE",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        color = EmergencyRed,
                                        letterSpacing = 1.sp
                                    )
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = EmergencyRed.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "Hop #${activeOriginSos.hopCount}",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = EmergencyRed
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Nearby ECHO devices", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
                                Text("$discoveredCount discovered", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary))
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Connected relays", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
                                Text("${connectedPeers.size} connected", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = if (connectedPeers.isNotEmpty()) SignalGreenLight else WarningAmber))
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Message ID: ${activeOriginSos.messageId.take(12)}...",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, color = TextMuted)
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(8.dp))

                        Text("Status:", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary))
                        Spacer(modifier = Modifier.height(4.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SignalGreenLight, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("SOS created", style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary))
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (connectedPeers.isNotEmpty()) Icons.Default.CheckCircle else Icons.Default.Radar,
                                contentDescription = null,
                                tint = if (connectedPeers.isNotEmpty()) SignalGreenLight else WarningAmber,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (connectedPeers.isNotEmpty()) "Sent to ${connectedPeers.joinToString(", ")}" else "Broadcasting to nearby mesh nodes...",
                                style = MaterialTheme.typography.bodySmall.copy(color = if (connectedPeers.isNotEmpty()) TextPrimary else WarningAmber)
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val isAcked = activeOriginSos.status.contains("acknowledged", ignoreCase = true) ||
                                          activeOriginSos.status.contains("HOPPING", ignoreCase = true) ||
                                          activeOriginSos.status.contains("BRIDGE", ignoreCase = true)
                            Icon(
                                imageVector = if (isAcked) Icons.Default.CheckCircle else Icons.Default.HourglassEmpty,
                                contentDescription = null,
                                tint = if (isAcked) SignalGreenLight else TextMuted,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isAcked) activeOriginSos.status else "Waiting for relay acknowledgment...",
                                style = MaterialTheme.typography.bodySmall.copy(color = if (isAcked) SignalGreenLight else TextMuted)
                            )
                        }

                        if (activeOriginSos.path.size > 1) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = EmergencySurface,
                                border = BorderStroke(0.5.dp, CardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = "AUTONOMOUS RELAY CHAIN (${activeOriginSos.path.size} NODES):",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp,
                                            color = TextSecondary
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = activeOriginSos.path.joinToString(" ➔ "),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = SignalGreenLight
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Tactical Radar Device Markers (Victim & Relay markers positioned by actual GPS calculated distance and BLE ranging)
            val radarDeviceMarkers = remember(incomingSos, connectedPeers, gpsInfo, proximityUpdates) {
                val list = mutableListOf<RadarDeviceMarker>()

                // 1. Victim Device Marker
                if (incomingSos != null) {
                    val isVictimLocUnavailable = (incomingSos.latitude == 0.0 && incomingSos.longitude == 0.0) ||
                        (incomingSos.latitude == 37.422065 && incomingSos.longitude == -122.084089) ||
                        incomingSos.locationStatus.contains("unavailable", ignoreCase = true) ||
                        incomingSos.locationStatus == "UNAVAILABLE"

                    val (dist, bearing) = if (!isVictimLocUnavailable &&
                        gpsInfo.hasLocation &&
                        !gpsInfo.isUnavailable &&
                        !(gpsInfo.latitude == 37.422065 && gpsInfo.longitude == -122.084089)
                    ) {
                        val results = FloatArray(2)
                        try {
                            Location.distanceBetween(
                                gpsInfo.latitude,
                                gpsInfo.longitude,
                                incomingSos.latitude,
                                incomingSos.longitude,
                                results
                            )
                            val actualDist = results[0]
                            val actualBearing = (results[1] + 360f) % 360f
                            Pair(actualDist, actualBearing)
                        } catch (e: Exception) {
                            Pair(0.0f, 0.0f)
                        }
                    } else {
                        Pair(0.0f, 0.0f)
                    }

                    list.add(
                        RadarDeviceMarker(
                            id = incomingSos.originDeviceId.ifEmpty { "victim-target" },
                            name = incomingSos.originDeviceName,
                            isVictim = true,
                            isRelay = false,
                            distanceMeters = dist,
                            bearingDegrees = bearing,
                            latitude = if (!isVictimLocUnavailable) incomingSos.latitude else 0.0,
                            longitude = if (!isVictimLocUnavailable) incomingSos.longitude else 0.0
                        )
                    )
                }

                // 2. Relay Devices (connected peers) - uses offline device-to-device BLE ranging when available
                connectedPeers.forEachIndexed { index, peerName ->
                    val rangingInfo = proximityUpdates[peerName] ?: proximityUpdates.values.find {
                        it.deviceName.equals(peerName, ignoreCase = true) || it.deviceId.equals(peerName, ignoreCase = true)
                    }
                    val dist = rangingInfo?.estimatedDistanceMeters ?: (1.5f + index * 2.0f)
                    val bearing = (115f * (index + 1)) % 360f

                    list.add(
                        RadarDeviceMarker(
                            id = "relay-peer-$index",
                            name = peerName,
                            isVictim = false,
                            isRelay = true,
                            distanceMeters = dist,
                            bearingDegrees = bearing
                        )
                    )
                }

                list
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Google Maps Live Location Card with 1-Meter Tactical Radar & Device Markers
            LiveGoogleMapCard(
                gpsInfo = gpsInfo,
                onRefreshLocation = onRefreshLocation,
                detectedDevices = radarDeviceMarkers
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Types of Emergencies Clickable Boxes
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "SELECT EMERGENCY TYPE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.2.sp,
                            color = TextSecondary
                        )
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = EmergencyRed.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = selectedEmergencyType.uppercase(),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Black,
                                color = EmergencyRed,
                                fontSize = 10.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 2x3 Grid of clickable emergency boxes
                val emergencyOptions = listOf(
                    Triple("Medical", "Health / Injury", Icons.Default.MedicalServices),
                    Triple("Fire", "Smoke / Burn", Icons.Default.LocalFireDepartment),
                    Triple("Disaster", "Flood / Quake", Icons.Default.Warning),
                    Triple("Trapped", "Debris / Lost", Icons.Default.AccessibilityNew),
                    Triple("Threat", "Security / Danger", Icons.Default.Shield),
                    Triple("General", "Urgent Distress", Icons.Default.Emergency)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    emergencyOptions.take(3).forEach { (type, desc, icon) ->
                        val isSelected = selectedEmergencyType.equals(type, ignoreCase = true)
                        EmergencyTypeBox(
                            title = type,
                            subtitle = desc,
                            icon = icon,
                            isSelected = isSelected,
                            modifier = Modifier.weight(1f),
                            onClick = { selectedEmergencyType = type }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    emergencyOptions.drop(3).forEach { (type, desc, icon) ->
                        val isSelected = selectedEmergencyType.equals(type, ignoreCase = true)
                        EmergencyTypeBox(
                            title = type,
                            subtitle = desc,
                            icon = icon,
                            isSelected = isSelected,
                            modifier = Modifier.weight(1f),
                            onClick = { selectedEmergencyType = type }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Primary Emergency SOS Trigger Area
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(240.dp)
                    .testTag("sos_beacon_container")
            ) {
                // Expanding ripple rings
                Box(
                    modifier = Modifier
                        .size(230.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(EmergencyRed.copy(alpha = ringAlpha * 0.4f))
                )
                Box(
                    modifier = Modifier
                        .size(195.dp)
                        .scale(pulseScale * 0.96f)
                        .clip(CircleShape)
                        .background(EmergencyRed.copy(alpha = 0.25f))
                )

                // Main Circular Button
                Box(
                    modifier = Modifier
                        .size(165.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(EmergencyRedGlow, EmergencyRed, EmergencyRedDark)
                            )
                        )
                        .border(4.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                        .clickable(enabled = !isSosSending) {
                            onTriggerSos(selectedEmergencyType)
                        }
                        .testTag("sos_trigger_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (isSosSending) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "BROADCASTING",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    letterSpacing = 1.sp
                                )
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Sensors,
                                contentDescription = "SOS",
                                tint = Color.White,
                                modifier = Modifier.size(38.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "SOS",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    letterSpacing = 2.sp
                                )
                            )
                            Text(
                                text = selectedEmergencyType.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    color = Color.White.copy(alpha = 0.95f),
                                    fontSize = 11.sp,
                                    letterSpacing = 1.sp
                                )
                            )
                            Text(
                                text = "TAP TO HOP",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontSize = 9.sp
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Hops automatically through nearest ECHO devices via Bluetooth/BLE without internet or Wi-Fi until finding an Internet Bridge",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Room Database Offline Cache Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("room_offline_cache_card"),
                colors = CardDefaults.cardColors(containerColor = EmergencySurface),
                shape = RoundedCornerShape(12.dp),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(SignalGreen.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Storage,
                                    contentDescription = null,
                                    tint = SignalGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Room DB Offline Cache",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = SignalGreen.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, SignalGreen.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(SignalGreen)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "OFFLINE PERSISTED",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Black,
                                        color = SignalGreen,
                                        fontSize = 9.sp
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Bluetooth/BLE P2P mesh operates completely without Internet and Wi-Fi. All mesh network packets, hop paths, and distress logs are cached in local SQLite Room DB for access during total blackouts.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            color = EmergencySurfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = "CACHED MESSAGES",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.sp,
                                        color = TextSecondary,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Text(
                                    text = "${cachedMeshMessages.size} in Room DB",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        color = TextPrimary
                                    )
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            color = EmergencySurfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = "OFFLINE SOS LOGS",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.sp,
                                        color = TextSecondary,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Text(
                                    text = "${offlineSosLogs.size} recorded",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        color = TextPrimary
                                    )
                                )
                            }
                        }
                    }

                    if (offlineSosLogs.isNotEmpty() || cachedMeshMessages.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = CardBorder.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "RECENT OFFLINE PERSISTED EVENTS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = TextSecondary
                            )
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        val recentItems = offlineSosLogs.take(3)
                        recentItems.forEach { log ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (log.eventType.contains("ACK")) SignalGreen else EmergencyRed)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = log.logMessage,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = TextPrimary
                                    ),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Background Power Optimization Button (Only button below SOS button)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onRequestBatteryOpt() }
                    .testTag("btn_power_optimization"),
                colors = CardDefaults.cardColors(containerColor = EmergencySurfaceVariant.copy(alpha = 0.75f)),
                shape = RoundedCornerShape(12.dp),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.BatteryAlert,
                        contentDescription = null,
                        tint = WarningAmber,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Background Power Optimization",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                        Text(
                            text = "Disable battery throttling so emergency mesh relays continuously in background without OS sleep.",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = WarningAmber.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, WarningAmber)
                    ) {
                        Text(
                            text = "CONFIG",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Black,
                                color = WarningAmber,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
fun EmergencyTypeBox(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.testTag("emergency_box_${title.lowercase()}"),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) EmergencyRed else EmergencySurface,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) EmergencyRed else CardBorder
        ),
        shadowElevation = if (isSelected) 3.dp else 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) Color.White.copy(alpha = 0.25f)
                        else EmergencyRed.copy(alpha = 0.1f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (isSelected) Color.White else EmergencyRed,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) Color.White else TextPrimary,
                    fontSize = 12.sp
                ),
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    color = if (isSelected) Color.White.copy(alpha = 0.85f) else TextSecondary
                ),
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}

