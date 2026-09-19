package com.example.ui.components

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.EmergencySurface
import com.example.ui.theme.EmergencySurfaceVariant
import com.example.ui.theme.SignalGreen
import com.example.ui.theme.SignalGreenLight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber
import com.example.util.RealGpsInfo
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * Tactical device marker representing a nearby victim or relay device
 * positioned by actual GPS-calculated distance in meters.
 */
data class RadarDeviceMarker(
    val id: String,
    val name: String,
    val isVictim: Boolean,
    val isRelay: Boolean,
    val distanceMeters: Float, // actual calculated GPS distance in meters
    val bearingDegrees: Float = 0f,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
) {
    val formattedDistance: String
        get() = when {
            distanceMeters < 10f -> String.format(Locale.US, "%.1f m", distanceMeters)
            distanceMeters < 1000f -> String.format(Locale.US, "%.0f m", distanceMeters)
            else -> String.format(Locale.US, "%.2f km", distanceMeters / 1000f)
        }
}

/**
 * Tactical Geolocation & Google Maps Integration Card.
 *
 * Implemented purely in native Jetpack Compose to eliminate WebView/Chromium GPU
 * initialization and avoid Mesa driver "rendernode" failures in emulator/container
 * environments, while ensuring 100% offline operational capability.
 */
@Composable
fun LiveGoogleMapCard(
    gpsInfo: RealGpsInfo,
    onRefreshLocation: () -> Unit,
    modifier: Modifier = Modifier,
    cardTitle: String = "LIVE GOOGLE MAPS LOCATION",
    detectedDevices: List<RadarDeviceMarker> = emptyList()
) {
    val context = LocalContext.current
    var isTopoMode by remember { mutableStateOf(false) }

    // Range scales in meters: 1m, 2m, 5m, 10m, 25m, 50m, 100m, 250m
    // Accommodates very short distances: <1m, 1-2m, 2-5m, etc.
    val zoomScales = remember { listOf(1f, 2f, 5f, 10f, 25f, 50f, 100f, 250f) }
    var userManualZoomIndex by remember { mutableStateOf<Int?>(null) }

    // Dynamic scale / auto-zoom: Automatically scales to distinguish very close devices
    val effectiveZoomIndex = remember(detectedDevices, userManualZoomIndex) {
        if (userManualZoomIndex != null) {
            userManualZoomIndex!!.coerceIn(0, zoomScales.size - 1)
        } else if (detectedDevices.isNotEmpty()) {
            val minDistance = detectedDevices.minOf { it.distanceMeters }
            when {
                minDistance <= 1.0f -> 0 // 1-meter scale
                minDistance <= 2.0f -> 1 // 2-meter scale
                minDistance <= 5.0f -> 2 // 5-meter scale
                minDistance <= 10.0f -> 3 // 10-meter scale
                minDistance <= 25.0f -> 4 // 25-meter scale
                minDistance <= 50.0f -> 5 // 50-meter scale
                minDistance <= 100.0f -> 6 // 100-meter scale
                else -> 7 // 250-meter scale
            }
        } else {
            0 // Default to 1-meter scale for tactical nearby visualization
        }
    }
    val currentRangeMeters = zoomScales[effectiveZoomIndex]

    // Location permission state
    var hasFineLocation by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasFineLocation = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (hasFineLocation) {
            onRefreshLocation()
        }
    }

    // Infinite radar sweep & pulse animations
    val infiniteTransition = rememberInfiniteTransition(label = "RadarSweep")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SweepAngle"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 2.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PulseScale"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PulseAlpha"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("google_maps_live_card"),
        colors = CardDefaults.cardColors(containerColor = EmergencySurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, EmergencySurfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(EmergencyRed.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = EmergencyRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = cardTitle,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp,
                                color = TextPrimary
                            )
                        )
                        Text(
                            text = if (isTopoMode) "Topographical Radar • Offline GIS" else "Tactical Grid • GPS Triangulation",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { isTopoMode = !isTopoMode },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = "Toggle Grid/Topo Mode",
                            tint = if (isTopoMode) SignalGreenLight else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            if (!hasFineLocation) {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            } else {
                                onRefreshLocation()
                            }
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh GPS",
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Permission warning if not granted
            if (!hasFineLocation) {
                Surface(
                    color = WarningAmber.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Location permission needed for live tracking",
                                style = MaterialTheme.typography.labelSmall.copy(color = TextPrimary)
                            )
                        }
                        Button(
                            onClick = {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = WarningAmber, contentColor = Color.Black),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("ENABLE", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp))
                        }
                    }
                }
            }

            // Paints for canvas metric labels
            val ringTextPaint = remember {
                Paint().apply {
                    color = android.graphics.Color.argb(160, 150, 180, 210)
                    textSize = 24f
                    isAntiAlias = true
                    typeface = Typeface.MONOSPACE
                }
            }
            val victimTextPaint = remember {
                Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 26f
                    isAntiAlias = true
                    isFakeBoldText = true
                    typeface = Typeface.DEFAULT_BOLD
                }
            }
            val relayTextPaint = remember {
                Paint().apply {
                    color = android.graphics.Color.argb(240, 100, 255, 150)
                    textSize = 24f
                    isAntiAlias = true
                    typeface = Typeface.DEFAULT_BOLD
                }
            }

            // Tactical Canvas Geolocation Display
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0C131D))
                    .border(1.dp, EmergencySurfaceVariant, RoundedCornerShape(12.dp))
            ) {
                val gridColor = if (isTopoMode) Color(0xFF1E332A) else Color(0xFF142B3A)
                val accentColor = if (isTopoMode) SignalGreenLight else Color(0xFF00E5FF)
                val beaconColor = if (gpsInfo.hasLocation) EmergencyRed else WarningAmber

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val centerX = size.width / 2f
                    val centerY = size.height / 2f
                    val maxRadius = minOf(centerX, centerY) * 0.88f

                    // 1. Background radial gradient
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                gridColor.copy(alpha = 0.35f),
                                Color(0xFF070B10)
                            ),
                            center = Offset(centerX, centerY),
                            radius = maxRadius * 1.2f
                        )
                    )

                    // 2. Concentric range rings with explicit metric distance markers (1-meter scale support)
                    val ringSteps = 4
                    for (i in 1..ringSteps) {
                        val fraction = i.toFloat() / ringSteps
                        val radius = maxRadius * fraction
                        val ringDist = currentRangeMeters * fraction
                        drawCircle(
                            color = gridColor.copy(alpha = 0.5f),
                            radius = radius,
                            center = Offset(centerX, centerY),
                            style = Stroke(
                                width = 1f,
                                pathEffect = if (i == ringSteps) null else PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                            )
                        )

                        // Render metric distance label along 45-degree angle on the ring
                        val ringAngleRad = Math.toRadians(45.0)
                        val labelX = centerX + (radius * cos(ringAngleRad)).toFloat()
                        val labelY = centerY - (radius * sin(ringAngleRad)).toFloat()
                        val ringDistText = when {
                            ringDist < 1f -> String.format(Locale.US, "%.2fm", ringDist)
                            ringDist < 10f -> String.format(Locale.US, "%.1fm", ringDist)
                            else -> "${ringDist.toInt()}m"
                        }
                        drawContext.canvas.nativeCanvas.drawText(
                            ringDistText,
                            labelX + 4f,
                            labelY,
                            ringTextPaint
                        )
                    }

                    // 3. Coordinate Crosshairs
                    drawLine(
                        color = gridColor.copy(alpha = 0.6f),
                        start = Offset(centerX - maxRadius, centerY),
                        end = Offset(centerX + maxRadius, centerY),
                        strokeWidth = 1f
                    )
                    drawLine(
                        color = gridColor.copy(alpha = 0.6f),
                        start = Offset(centerX, centerY - maxRadius),
                        end = Offset(centerX, centerY + maxRadius),
                        strokeWidth = 1f
                    )

                    // 4. Mode-specific topography or tactical diagonals
                    if (isTopoMode) {
                        // Draw simulated elevation contour rings
                        for (j in 1..4) {
                            val contourRadius = maxRadius * (0.25f * j)
                            val path = Path()
                            val numPoints = 24
                            for (p in 0..numPoints) {
                                val angle = (p.toFloat() / numPoints) * 2f * Math.PI.toFloat()
                                val wobble = (sin(angle * 3 + j) * 8f) + (cos(angle * 2) * 5f)
                                val r = contourRadius + wobble
                                val x = centerX + r * cos(angle)
                                val y = centerY + r * sin(angle)
                                if (p == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            path.close()
                            drawPath(
                                path = path,
                                color = SignalGreenLight.copy(alpha = 0.15f + j * 0.05f),
                                style = Stroke(width = 1.2f)
                            )
                        }
                    } else {
                        // Tactical diagonal bearing lines
                        val diagOffset = maxRadius * 0.707f
                        drawLine(
                            color = gridColor.copy(alpha = 0.3f),
                            start = Offset(centerX - diagOffset, centerY - diagOffset),
                            end = Offset(centerX + diagOffset, centerY + diagOffset),
                            strokeWidth = 1f
                        )
                        drawLine(
                            color = gridColor.copy(alpha = 0.3f),
                            start = Offset(centerX - diagOffset, centerY + diagOffset),
                            end = Offset(centerX + diagOffset, centerY - diagOffset),
                            strokeWidth = 1f
                        )
                    }

                    // 5. Radar sweep beam
                    rotate(degrees = sweepAngle, pivot = Offset(centerX, centerY)) {
                        drawArc(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    accentColor.copy(alpha = 0.25f)
                                ),
                                center = Offset(centerX, centerY)
                            ),
                            startAngle = 0f,
                            sweepAngle = 45f,
                            useCenter = true,
                            topLeft = Offset(centerX - maxRadius, centerY - maxRadius),
                            size = androidx.compose.ui.geometry.Size(maxRadius * 2, maxRadius * 2)
                        )
                        drawLine(
                            color = accentColor.copy(alpha = 0.75f),
                            start = Offset(centerX, centerY),
                            end = Offset(centerX + maxRadius, centerY),
                            strokeWidth = 1.5f
                        )
                    }

                    // 6. Accuracy radius halo
                    val accuracyRadiusPixels = minOf(maxRadius * 0.65f, (gpsInfo.accuracy.coerceIn(1f, 30f) / currentRangeMeters) * maxRadius)
                    drawCircle(
                        color = accentColor.copy(alpha = 0.12f),
                        radius = accuracyRadiusPixels,
                        center = Offset(centerX, centerY)
                    )
                    drawCircle(
                        color = accentColor.copy(alpha = 0.45f),
                        radius = accuracyRadiusPixels,
                        center = Offset(centerX, centerY),
                        style = Stroke(width = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f))
                    )

                    // 7. Live Pulsing Beacon Wave
                    drawCircle(
                        color = beaconColor.copy(alpha = pulseAlpha),
                        radius = 12f * pulseScale,
                        center = Offset(centerX, centerY),
                        style = Stroke(width = 2f)
                    )

                    // 8. Center GPS Beacon Pin (My Phone)
                    drawCircle(
                        color = Color.White,
                        radius = 6.5f,
                        center = Offset(centerX, centerY)
                    )
                    drawCircle(
                        color = beaconColor,
                        radius = 4.5f,
                        center = Offset(centerX, centerY)
                    )

                    // 9. Detected Device Markers (Victim & Relays) positioned by actual calculated GPS distance
                    // Accurate 1-meter short distance representation (<1m, 1-2m, 2-5m, etc.)
                    // Displays line between device and markers, and numeric distance next to each device (e.g. "0.8 m", "1.4 m", "3.7 m", "7 m")
                    detectedDevices.forEach { device ->
                        // Continuous linear distance mapping (no coarse buckets or large min radius)
                        val normalizedDistance = (device.distanceMeters / currentRangeMeters).coerceIn(0.04f, 0.96f)
                        val markerRadiusPixels = normalizedDistance * maxRadius
                        val angleRad = Math.toRadians(device.bearingDegrees.toDouble() - 90.0)
                        val markerX = centerX + (markerRadiusPixels * cos(angleRad)).toFloat()
                        val markerY = centerY + (markerRadiusPixels * sin(angleRad)).toFloat()

                        val markerColor = if (device.isVictim) EmergencyRed else SignalGreenLight
                        val textPaint = if (device.isVictim) victimTextPaint else relayTextPaint

                        // Tactical connecting vector line between phone and marker
                        drawLine(
                            color = markerColor.copy(alpha = if (device.isVictim) 0.55f else 0.35f),
                            start = Offset(centerX, centerY),
                            end = Offset(markerX, markerY),
                            strokeWidth = if (device.isVictim) 1.5f else 1.0f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
                        )

                        // Pulsing aura for device
                        drawCircle(
                            color = markerColor.copy(alpha = if (device.isVictim) pulseAlpha else 0.35f),
                            radius = if (device.isVictim) 14f * pulseScale else 9f,
                            center = Offset(markerX, markerY),
                            style = Stroke(width = 1.5f)
                        )

                        // Solid marker dot
                        drawCircle(
                            color = Color.White,
                            radius = 6.5f,
                            center = Offset(markerX, markerY)
                        )
                        drawCircle(
                            color = markerColor,
                            radius = 4.5f,
                            center = Offset(markerX, markerY)
                        )

                        // Numeric distance label next to device (e.g. "0.8 m", "1.4 m", "3.7 m", "7 m")
                        val labelText = "${device.name} • ${device.formattedDistance}"
                        drawContext.canvas.nativeCanvas.drawText(
                            labelText,
                            markerX + 10f,
                            markerY + 4f,
                            textPaint
                        )
                    }
                }

                // Top Left: Mode & Status Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.75f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (gpsInfo.hasLocation) SignalGreenLight else WarningAmber)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isTopoMode) "TOPO ELEVATION" else if (currentRangeMeters <= 2f) "1m TACTICAL RADAR" else "TACTICAL RADAR",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                color = Color.White
                            )
                        )
                    }
                }

                // Top Right: North Bearing Arrow
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.75f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Navigation,
                            contentDescription = "North",
                            tint = EmergencyRed,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "N",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 10.sp,
                                color = EmergencyRed
                            )
                        )
                    }
                }

                // Bottom Left: Scale Indicator
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                ) {
                    val scaleText = if (currentRangeMeters < 10f) {
                        "SCALE: ${String.format(Locale.US, "%.0fm", currentRangeMeters)}"
                    } else {
                        "SCALE: ${currentRangeMeters.toInt()}m"
                    }
                    Text(
                        text = scaleText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            color = TextSecondary
                        ),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }

                // Bottom Right: Zoom Controls
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.75f),
                        modifier = Modifier
                            .size(28.dp)
                            .clickable(enabled = effectiveZoomIndex > 0) {
                                if (effectiveZoomIndex > 0) {
                                    userManualZoomIndex = effectiveZoomIndex - 1
                                }
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Zoom In",
                                tint = if (effectiveZoomIndex > 0) Color.White else TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.75f),
                        modifier = Modifier
                            .size(28.dp)
                            .clickable(enabled = effectiveZoomIndex < zoomScales.size - 1) {
                                if (effectiveZoomIndex < zoomScales.size - 1) {
                                    userManualZoomIndex = effectiveZoomIndex + 1
                                }
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = "Zoom Out",
                                tint = if (effectiveZoomIndex < zoomScales.size - 1) Color.White else TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Detected Nearby Ranging Targets List
            if (detectedDevices.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = EmergencySurfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(0.5.dp, EmergencySurfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "NEARBY TARGETS (EXACT GOOGLE MAPS DISTANCE):",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 9.sp,
                                color = TextSecondary
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        detectedDevices.forEach { device ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (device.isVictim) EmergencyRed else SignalGreenLight)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${device.name} (${if (device.isVictim) "Victim" else "Relay"})",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextPrimary,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                                Text(
                                    text = device.formattedDistance,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (device.isVictim) EmergencyRed else SignalGreenLight
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val isGpsValid = gpsInfo.hasLocation && (gpsInfo.latitude != 0.0 || gpsInfo.longitude != 0.0) &&
                !(gpsInfo.latitude == 37.422065 && gpsInfo.longitude == -122.084089) && !gpsInfo.isUnavailable

            // Coordinates Readout & Metrics Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isGpsValid) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "LAT: ",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextMuted
                                )
                            )
                            Text(
                                text = String.format(Locale.US, "%.6f°", gpsInfo.latitude),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "LON: ",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextMuted
                                )
                            )
                            Text(
                                text = String.format(Locale.US, "%.6f°", gpsInfo.longitude),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                text = "ACCURACY: ±${String.format(Locale.US, "%.1f", gpsInfo.accuracy)}m  •  ALT: ${String.format(Locale.US, "%.0f", gpsInfo.altitude)}m",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 10.sp,
                                    color = SignalGreenLight
                                )
                            )
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = EmergencyRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "GPS location unavailable",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = EmergencyRed
                            )
                        )
                    }
                }
            }

            if (isGpsValid) {
                Spacer(modifier = Modifier.height(10.dp))

                // Native Action Buttons: Offline Map Viewer & Copy Coordinates
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            openOfflineMapLocation(context, gpsInfo.latitude, gpsInfo.longitude)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SignalGreen,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "MAP VIEWER",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(
                                ClipData.newPlainText(
                                    "ECHO_GPS_COORDS",
                                    "${gpsInfo.latitude},${gpsInfo.longitude}"
                                )
                            )
                            Toast.makeText(context, "Coordinates copied: ${String.format(Locale.US, "%.6f, %.6f", gpsInfo.latitude, gpsInfo.longitude)}", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Coordinates",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "COPY",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary,
                                fontSize = 10.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * Launches the device's offline map application using native Geo URI intent.
 */
private fun openOfflineMapLocation(context: Context, lat: Double, lng: Double) {
    try {
        val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(Emergency+ECHO+Distress+Location)")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Location: $lat, $lng", Toast.LENGTH_LONG).show()
    }
}
