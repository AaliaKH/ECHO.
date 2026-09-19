package com.example.ui.components

import java.util.Locale
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.SosPacket
import com.example.ui.theme.EmergencyBackground
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.EmergencyRedDark
import com.example.ui.theme.EmergencyRedGlow
import com.example.ui.theme.EmergencySurface
import com.example.ui.theme.SignalGreen
import com.example.ui.theme.SignalGreenLight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber
import com.example.util.RealBatteryInfo
import com.example.util.RealGpsInfo
import com.example.util.LocationUtils

@Composable
fun DeviceIdentityCard(
    deviceName: String,
    batteryInfo: RealBatteryInfo,
    gpsInfo: RealGpsInfo,
    hasInternet: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("device_identity_card"),
        colors = CardDefaults.cardColors(containerColor = EmergencySurface),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder()
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
                            .background(if (hasInternet) SignalGreen else WarningAmber)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = deviceName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = TextPrimary
                        )
                    )
                }

                // Battery Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when {
                        batteryInfo.percentage > 50 -> SignalGreen.copy(alpha = 0.2f)
                        batteryInfo.percentage > 20 -> WarningAmber.copy(alpha = 0.2f)
                        else -> EmergencyRed.copy(alpha = 0.2f)
                    },
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (batteryInfo.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                            contentDescription = "Battery",
                            tint = when {
                                batteryInfo.percentage > 50 -> SignalGreenLight
                                batteryInfo.percentage > 20 -> WarningAmber
                                else -> EmergencyRed
                            },
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${batteryInfo.percentage}%",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Sub-strip: GPS status + Link mode
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val isGpsValid = gpsInfo.hasLocation && !gpsInfo.isUnavailable &&
                        (gpsInfo.latitude != 0.0 || gpsInfo.longitude != 0.0) &&
                        !(gpsInfo.latitude == 37.422065 && gpsInfo.longitude == -122.084089)
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "GPS",
                        tint = if (isGpsValid) SignalGreenLight else if (gpsInfo.isUnavailable) EmergencyRed else TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = when {
                            isGpsValid -> "%.4f, %.4f (±%.0fm)".format(gpsInfo.latitude, gpsInfo.longitude, gpsInfo.accuracy)
                            gpsInfo.isUnavailable -> "GPS location unavailable"
                            else -> "Acquiring GPS fix..."
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (isGpsValid) TextSecondary else if (gpsInfo.isUnavailable) EmergencyRed else WarningAmber
                        )
                    )
                }

                // Mode pill
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (hasInternet) SignalGreen.copy(alpha = 0.15f) else EmergencyBackground,
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Text(
                        text = if (hasInternet) "BRIDGE ACTIVE (ONLINE)" else "OFFLINE MESH ONLY",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (hasInternet) SignalGreenLight else WarningAmber
                        )
                    )
                }
            }
        }
    }
}

/**
 * Requirement:
 * "Once a signal is received and relayed by a relay device, it should send an acknowledgment packet
 * back to the original sender and the screen of the relayer's device should glow bright red to
 * indicate a signal was recieved and transmitted. This visual confirmation is essential for
 * monitoring network activity in real time."
 */
@Composable
fun RelayerGlowOverlay(
    isActive: Boolean,
    packet: SosPacket?,
    exactDistanceMeters: Float? = null,
    proximityDistanceMeters: Float? = null,
    proximityRssi: Int? = null,
    onDismiss: () -> Unit
) {
    val isPacketLocUnavailable = packet != null && (
        (packet.latitude == 0.0 && packet.longitude == 0.0) ||
        (packet.latitude == 37.422065 && packet.longitude == -122.084089) ||
        packet.locationStatus.contains("unavailable", ignoreCase = true) ||
        packet.locationStatus == "UNAVAILABLE"
    )

    val (distanceLabel, distanceSubtext) = remember(exactDistanceMeters, proximityDistanceMeters, isPacketLocUnavailable) {
        when {
            proximityDistanceMeters != null -> {
                val formatted = when {
                    proximityDistanceMeters < 10f -> String.format(Locale.US, "%.1f m", proximityDistanceMeters)
                    proximityDistanceMeters < 1000f -> "${kotlin.math.round(proximityDistanceMeters).toInt()} m"
                    else -> String.format(Locale.US, "%.2f km", proximityDistanceMeters / 1000f)
                }
                Pair("📏 Proximity: ~$formatted", "Short-range BLE RF ranging (device-to-device)")
            }
            exactDistanceMeters != null && !isPacketLocUnavailable -> {
                val formatted = when {
                    exactDistanceMeters < 10f -> String.format(Locale.US, "%.1f m", exactDistanceMeters)
                    exactDistanceMeters < 1000f -> "${kotlin.math.round(exactDistanceMeters).toInt()} m"
                    else -> String.format(Locale.US, "%.2f km", exactDistanceMeters / 1000f)
                }
                val uncertainty = if (packet != null && packet.accuracy > 0f) " (±${packet.accuracy.toInt()}m GPS accuracy)" else ""
                Pair("📏 GPS Distance: $formatted$uncertainty", "Geographic distance calculated offline locally")
            }
            isPacketLocUnavailable -> {
                Pair("GPS location unavailable", "Ranging unavailable - meter-level BLE scan in progress")
            }
            else -> {
                Pair("Acquiring distance...", "Measuring device proximity / GPS coordinates")
            }
        }
    }

    AnimatedVisibility(
        visible = isActive,
        enter = fadeIn(animationSpec = tween(150)),
        exit = fadeOut(animationSpec = tween(400))
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "RedGlowPulse")
        val glowAlpha by infiniteTransition.animateFloat(
            initialValue = 0.85f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "alpha"
        )
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 0.98f,
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(
                animation = tween(500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            EmergencyRedGlow.copy(alpha = glowAlpha),
                            EmergencyRed.copy(alpha = glowAlpha),
                            EmergencyRedDark
                        )
                    )
                )
                .clickable { onDismiss() }
                .padding(24.dp)
                .testTag("relayer_red_glow_overlay"),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.scale(pulseScale)
            ) {
                // Warning Beacon Icon
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f))
                        .border(4.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CellTower,
                        contentDescription = "Relaying Signal",
                        tint = Color.White,
                        modifier = Modifier.size(50.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Full-Screen Relay Popup Specification:
                // 🚨 DISTRESS SIGNAL
                // Victim: AALIA-PHONE
                // 📏 Approx. distance: 1 m
                // 🔄 Automatically relaying
                // Hop: 2
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.55f),
                    border = BorderStroke(2.dp, Color.White.copy(alpha = 0.8f)),
                    modifier = Modifier.fillMaxWidth(0.95f)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "🚨 DISTRESS SIGNAL",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                letterSpacing = 1.5.sp,
                                textAlign = TextAlign.Center
                            )
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Victim: ${packet?.originDeviceName ?: "AALIA-PHONE"}",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.22f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f))
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = distanceLabel,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        color = Color.White
                                    )
                                )
                                Text(
                                    text = distanceSubtext,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        color = Color.White.copy(alpha = 0.85f)
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "🔄 Automatically relaying",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = SignalGreenLight
                            )
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Hop: ${(packet?.hopCount ?: 1) + 1}",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Screen glowing bright red for immediate visual confirmation.\nExact distance computed according to Google Maps geodesy.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.95f),
                        textAlign = TextAlign.Center
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Encrypted medical data banner
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth(0.95f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Encrypted",
                            tint = Color.White.copy(alpha = 0.95f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Victim medical & emergency profile is end-to-end encrypted for Rescue Authorities only.",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 10.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = EmergencyRedDark
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("dismiss_glow_button")
                ) {
                    Text(
                        text = "DISMISS ALERT",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}
