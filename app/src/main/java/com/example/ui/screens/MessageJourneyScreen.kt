package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SosEntity
import com.example.ui.theme.EmergencyBackground
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.EmergencySurface
import com.example.ui.theme.EmergencySurfaceVariant
import com.example.ui.theme.InfoBlue
import com.example.ui.theme.SignalGreen
import com.example.ui.theme.SignalGreenLight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MessageJourneyScreen(
    sosList: List<SosEntity>,
    onSimulateHop: (messageId: String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EmergencyBackground)
            .padding(16.dp)
            .testTag("message_journey_screen")
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = SignalGreenLight,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Distress Hop Journey",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = EmergencySurfaceVariant,
                border = CardDefaults.outlinedCardBorder()
            ) {
                Text(
                    text = "${sosList.size} SIGNALS LOGGED",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Track your SOS message journey hop-by-hop across offline devices until connectivity and rescue response are established.",
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (sosList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(EmergencySurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = null,
                            tint = SignalGreenLight,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Distress Signals Logged Yet",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "When an SOS is triggered, each device hop (e.g. 'Sent to Device B', 'Relayed by Device C') and reverse acknowledgment will be logged here with unique IDs to prevent looping.",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(sosList, key = { it.messageId }) { sos ->
                    SosJourneyCard(
                        sos = sos,
                        onSimulateNextHop = { onSimulateHop(sos.messageId) }
                    )
                }
            }
        }
    }
}

/**
 * Detailed Journey Card for an SOS signal showing:
 * - Unique Message ID (prevents message loops)
 * - Status indicator for progress towards connectivity
 * - 4-stage visual progress stepper
 * - Detailed hop log showing 'Sent to Device B', 'Relayed by Device C', etc.
 * - Hardware metrics at transmission
 */
@Composable
fun SosJourneyCard(
    sos: SosEntity,
    onSimulateNextHop: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(true) }
    val formattedDate = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault()).format(Date(sos.timestamp))

    val journeySteps = remember(sos.journeyLogJson) {
        val list = mutableListOf<String>()
        try {
            val arr = JSONArray(sos.journeyLogJson)
            for (i in 0 until arr.length()) {
                list.add(arr.getString(i))
            }
        } catch (e: Exception) {
            if (sos.journeyLogJson.isNotBlank()) list.add(sos.journeyLogJson)
        }
        list
    }

    // Determine current progress stage (1 to 4)
    val stage = when (sos.status) {
        "DISPATCH_ACKNOWLEDGED" -> 4
        "BRIDGE_REACHED" -> 3
        "HOPPING" -> 2
        else -> 1
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { isExpanded = !isExpanded }
            .testTag("sos_journey_card_${sos.messageId.take(8)}"),
        colors = CardDefaults.cardColors(containerColor = EmergencySurface),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Unique Message ID + Status Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "MSG ID: #${sos.messageId.take(12).uppercase()}",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                color = TextPrimary
                            )
                        )
                        if (sos.isMySos) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = EmergencyRed.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "ORIGINATOR",
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp,
                                        color = EmergencyRed
                                    )
                                )
                            }
                        }
                    }
                    Text(
                        text = "$formattedDate • Hop Count: ${sos.hopCount}",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 11.sp)
                    )
                }

                // Status Indicator keeping sender informed about progress
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when (sos.status) {
                        "DISPATCH_ACKNOWLEDGED" -> SignalGreen.copy(alpha = 0.15f)
                        "BRIDGE_REACHED" -> InfoBlue.copy(alpha = 0.15f)
                        "HOPPING" -> WarningAmber.copy(alpha = 0.15f)
                        else -> EmergencyRed.copy(alpha = 0.15f)
                    }
                ) {
                    Text(
                        text = when (sos.status) {
                            "DISPATCH_ACKNOWLEDGED" -> "DISPATCH CONFIRMED"
                            "BRIDGE_REACHED" -> "BRIDGE REACHED"
                            "HOPPING" -> "HOPPING (${sos.hopCount} HOPS)"
                            else -> "BROADCASTING"
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = when (sos.status) {
                                "DISPATCH_ACKNOWLEDGED" -> SignalGreenLight
                                "BRIDGE_REACHED" -> InfoBlue
                                "HOPPING" -> WarningAmber
                                else -> EmergencyRed
                            }
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4-Stage Visual Progress Towards Connectivity
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val stages = listOf("Broadcast", "Mesh Hop", "Bridge", "Rescue")
                stages.forEachIndexed { index, name ->
                    val stepNum = index + 1
                    val isCompleted = stepNum <= stage
                    val isCurrent = stepNum == stage

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        stepNum == 4 && isCompleted -> SignalGreenLight
                                        isCompleted -> SignalGreenLight
                                        else -> EmergencySurfaceVariant
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isCompleted) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            } else {
                                Text(
                                    text = "$stepNum",
                                    style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCompleted) TextPrimary else TextMuted
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Metrics row: Battery + Expand Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.BatteryFull,
                        contentDescription = "Battery",
                        tint = SignalGreenLight,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Battery: ${sos.batteryLevel}%",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Icon(
                        imageVector = Icons.Default.Hub,
                        contentDescription = "Hops",
                        tint = WarningAmber,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Path: ${sos.pathJson}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextSecondary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                    )
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Expand",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = EmergencySurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "MESSAGE JOURNEY & REVERSE ACKS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = TextSecondary
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            journeySteps.forEachIndexed { index, step ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 4.dp)
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when {
                                                    step.contains("Dispatch", ignoreCase = true) -> SignalGreenLight
                                                    step.contains("Bridge", ignoreCase = true) -> InfoBlue
                                                    step.contains("Relayed by", ignoreCase = true) -> WarningAmber
                                                    else -> SignalGreen
                                                }
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = step,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = TextPrimary
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Interactive simulation helper for demonstrating multi-hop message progression
                    if (stage < 4) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = onSimulateNextHop,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("simulate_hop_button_${sos.messageId.take(8)}")
                        ) {
                            Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp), tint = SignalGreenLight)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when (stage) {
                                    1 -> "Simulate Relay by Peer (Device B)"
                                    2 -> "Simulate Next Hop (Device C -> Bridge)"
                                    else -> "Simulate Rescue Command Acknowledgment"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = SignalGreenLight
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
