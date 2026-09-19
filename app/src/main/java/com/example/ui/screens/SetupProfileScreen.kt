package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Accessible
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.example.model.UserProfile
import com.example.ui.theme.EmergencyBackground
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.EmergencySurface
import com.example.ui.theme.EmergencySurfaceVariant
import com.example.ui.theme.SignalGreen
import com.example.ui.theme.SignalGreenLight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber

private val BLOOD_TYPES = listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetupProfileScreen(
    currentProfile: UserProfile,
    fixedDeviceName: String,
    onSaveProfile: (name: String, phone: String, bloodType: String, allergies: String, medicalIssues: String, handicap: String, notes: String) -> Unit,
    onLogout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var name by remember(currentProfile) { mutableStateOf(currentProfile.name) }
    var phone by remember(currentProfile) { mutableStateOf(currentProfile.phone) }
    var selectedBloodType by remember(currentProfile) { mutableStateOf(currentProfile.bloodType.ifBlank { "O+" }) }
    var allergies by remember(currentProfile) { mutableStateOf(currentProfile.allergies) }
    var medicalIssues by remember(currentProfile) { mutableStateOf(currentProfile.medicalIssues) }
    var handicap by remember(currentProfile) { mutableStateOf(currentProfile.handicap) }
    var emergencyNotes by remember(currentProfile) { mutableStateOf(currentProfile.emergencyNotes) }
    var savedFeedback by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EmergencyBackground)
            .verticalScroll(scrollState)
            .padding(20.dp)
            .testTag("setup_profile_screen")
    ) {
        // Header with clear user-provided phrasing
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(EmergencyRed.copy(alpha = 0.15f))
                    .border(1.dp, EmergencyRed, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MedicalServices,
                    contentDescription = null,
                    tint = EmergencyRed,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "User-Provided Emergency Profile",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
                Text(
                    text = "Voluntarily provided by user. ECHO does not infer or diagnose conditions.",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Fixed Device Identity Card (Non-customizable as requested)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = EmergencySurface),
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
                    imageVector = Icons.Default.DeviceHub,
                    contentDescription = null,
                    tint = SignalGreenLight,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "HARDWARE MESH IDENTIFIER (FIXED)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = TextMuted,
                            letterSpacing = 1.sp
                        )
                    )
                    Text(
                        text = fixedDeviceName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = TextPrimary
                        )
                    )
                }
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Locked Identifier",
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Full Name Field
        Text(
            text = "Full Name",
            style = MaterialTheme.typography.labelMedium.copy(color = TextSecondary, fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = { Text("Enter your full legal name", color = TextMuted) },
            leadingIcon = {
                Icon(Icons.Default.Person, contentDescription = null, tint = TextSecondary)
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_name"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = EmergencySurface,
                unfocusedContainerColor = EmergencySurface,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = EmergencyRed,
                unfocusedBorderColor = Color(0xFF334155)
            ),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Phone / Emergency Contact
        Text(
            text = "Emergency Phone / Contact",
            style = MaterialTheme.typography.labelMedium.copy(color = TextSecondary, fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            placeholder = { Text("+1 (555) 019-2834", color = TextMuted) },
            leadingIcon = {
                Icon(Icons.Default.Call, contentDescription = null, tint = TextSecondary)
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_phone"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = EmergencySurface,
                unfocusedContainerColor = EmergencySurface,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = EmergencyRed,
                unfocusedBorderColor = Color(0xFF334155)
            ),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Blood Type Selector
        Text(
            text = "Blood Group",
            style = MaterialTheme.typography.labelMedium.copy(color = TextSecondary, fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BLOOD_TYPES.forEach { bType ->
                val isSelected = selectedBloodType.equals(bType, ignoreCase = true)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) EmergencyRed else EmergencySurface,
                    border = CardDefaults.outlinedCardBorder(),
                    modifier = Modifier
                        .clickable { selectedBloodType = bType }
                        .testTag("blood_type_$bType")
                ) {
                    Text(
                        text = bType,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color.White else TextPrimary
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Voluntarily Provided Allergies
        Text(
            text = "User-Provided Allergies",
            style = MaterialTheme.typography.labelMedium.copy(color = TextSecondary, fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = allergies,
            onValueChange = { allergies = it },
            placeholder = { Text("e.g., Penicillin, Peanuts, Aspirin, Latex, None", color = TextMuted) },
            leadingIcon = {
                Icon(Icons.Default.MedicalServices, contentDescription = null, tint = TextSecondary)
            },
            maxLines = 2,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_allergies"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = EmergencySurface,
                unfocusedContainerColor = EmergencySurface,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = EmergencyRed,
                unfocusedBorderColor = Color(0xFF334155)
            ),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // User-Provided Medical Conditions & Diseases
        Text(
            text = "User-Provided Medical Conditions & Diseases",
            style = MaterialTheme.typography.labelMedium.copy(color = TextSecondary, fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = medicalIssues,
            onValueChange = { medicalIssues = it },
            placeholder = { Text("e.g., Asthma, Diabetes Type 1, Cardiac condition, Epilepsy", color = TextMuted) },
            leadingIcon = {
                Icon(Icons.Default.HealthAndSafety, contentDescription = null, tint = TextSecondary)
            },
            maxLines = 3,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_medical_issues"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = EmergencySurface,
                unfocusedContainerColor = EmergencySurface,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = EmergencyRed,
                unfocusedBorderColor = Color(0xFF334155)
            ),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // User-Provided Handicap & Mobility Limitations
        Text(
            text = "User-Provided Disability & Mobility Limitations",
            style = MaterialTheme.typography.labelMedium.copy(color = TextSecondary, fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = handicap,
            onValueChange = { handicap = it },
            placeholder = { Text("e.g., Wheelchair user, Hearing impairment, Visual impairment, None", color = TextMuted) },
            leadingIcon = {
                Icon(Icons.Default.Accessible, contentDescription = null, tint = TextSecondary)
            },
            maxLines = 2,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_handicap"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = EmergencySurface,
                unfocusedContainerColor = EmergencySurface,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = EmergencyRed,
                unfocusedBorderColor = Color(0xFF334155)
            ),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Emergency Notes
        Text(
            text = "Emergency Notes & Instructions",
            style = MaterialTheme.typography.labelMedium.copy(color = TextSecondary, fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = emergencyNotes,
            onValueChange = { emergencyNotes = it },
            placeholder = { Text("e.g., Next of kin: Jane Doe, Carrying insulin in blue pouch", color = TextMuted) },
            maxLines = 3,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_emergency_notes"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = EmergencySurface,
                unfocusedContainerColor = EmergencySurface,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = EmergencyRed,
                unfocusedBorderColor = Color(0xFF334155)
            ),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Privacy Guarantee Card
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = EmergencySurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = SignalGreenLight,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Voluntarily provided information. Ordinary relay devices cannot access your emergency profile; it is end-to-end encrypted with the Rescue Authority public key.",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 12.sp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                onSaveProfile(name, phone, selectedBloodType, allergies, medicalIssues, handicap, emergencyNotes)
                savedFeedback = true
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = EmergencyRed,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("save_profile_button")
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (savedFeedback) Icons.Default.Check else Icons.Default.MedicalServices,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (savedFeedback) "PROFILE SAVED" else "SAVE PROFILE DATA",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        androidx.compose.material3.OutlinedButton(
            onClick = onLogout,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = EmergencyRed),
            border = androidx.compose.foundation.BorderStroke(1.dp, EmergencyRed.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("logout_button")
        ) {
            Text(
                text = "LOGOUT / SWITCH ACCOUNT",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
