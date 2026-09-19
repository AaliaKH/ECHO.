package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.UserRole
import com.example.ui.theme.CardBorder
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginCivilian: (name: String, phone: String, blood: String, allergies: String, issues: String, handicap: String, notes: String) -> Unit,
    onLoginAuthority: (name: String, authId: String, agency: String, passcode: String) -> Boolean
) {
    var selectedRole by remember { mutableStateOf(UserRole.CIVILIAN) }

    // Civilian form state
    var civilianName by remember { mutableStateOf("") }
    var civilianPhone by remember { mutableStateOf("") }
    var civilianBloodType by remember { mutableStateOf("O+") }
    var civilianAllergies by remember { mutableStateOf("") }
    var civilianMedicalIssues by remember { mutableStateOf("") }
    var civilianHandicap by remember { mutableStateOf("None") }
    var civilianNotes by remember { mutableStateOf("") }
    var civilianError by remember { mutableStateOf<String?>(null) }

    // Authority form state
    var officerName by remember { mutableStateOf("") }
    var authorityId by remember { mutableStateOf("NDRF-HQ-742") }
    var selectedAgency by remember { mutableStateOf("NDRF (National Disaster Response Force)") }
    var agencyExpanded by remember { mutableStateOf(false) }
    var passcode by remember { mutableStateOf("") }
    var passcodeVisible by remember { mutableStateOf(false) }
    var authorityError by remember { mutableStateOf<String?>(null) }

    val bloodTypes = listOf("O+", "A+", "B+", "AB+", "O-", "A-", "B-", "AB-")
    val handicapOptions = listOf("None", "Wheelchair", "Walking Cane", "Visual Impairment", "Hearing Impairment")
    val agencies = listOf(
        "NDRF (National Disaster Response Force)",
        "State Disaster Management Authority (SDMA)",
        "Coast Guard & Marine Rescue",
        "Fire & Emergency Services",
        "Emergency Medical Services (EMS)",
        "Police Command & Dispatch"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EmergencyBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // App Brand Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(EmergencyRed.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Emergency,
                        contentDescription = null,
                        tint = EmergencyRed,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "ECHO",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp,
                            color = Color.Black
                        )
                    )
                    Text(
                        text = "OFFLINE EMERGENCY DISASTER NETWORK",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            color = EmergencyRed,
                            letterSpacing = 1.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Role Selector Tab (Civilian vs Authority)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = EmergencySurface,
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                ) {
                    // Civilian Tab
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (selectedRole == UserRole.CIVILIAN) EmergencyRed else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedRole = UserRole.CIVILIAN }
                            .testTag("tab_login_civilian")
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = if (selectedRole == UserRole.CIVILIAN) Color.White else TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "CIVILIAN",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedRole == UserRole.CIVILIAN) Color.White else TextMuted
                                )
                            )
                        }
                    }

                    // Authority Tab
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (selectedRole == UserRole.AUTHORITY) WarningAmber else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedRole = UserRole.AUTHORITY }
                            .testTag("tab_login_authority")
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AdminPanelSettings,
                                contentDescription = null,
                                tint = if (selectedRole == UserRole.AUTHORITY) Color.Black else TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "AUTHORITY",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedRole == UserRole.AUTHORITY) Color.Black else TextMuted
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (selectedRole == UserRole.CIVILIAN) {
                // ==================== CIVILIAN LOGIN FORM ====================
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = EmergencySurface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.MedicalServices, contentDescription = null, tint = EmergencyRed, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Civilian Registration & Health Profile",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                            )
                        }
                        Text(
                            text = "This critical medical data will be encrypted and transmitted to rescuers during an SOS beacon broadcast.",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp),
                            modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
                        )

                        if (civilianError != null) {
                            Surface(
                                color = EmergencyRedDark.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, EmergencyRed),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                            ) {
                                Text(
                                    text = civilianError!!,
                                    style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        // Full Name
                        OutlinedTextField(
                            value = civilianName,
                            onValueChange = { civilianName = it; civilianError = null },
                            label = { Text("Full Name *") },
                            placeholder = { Text("e.g. Aishwarya Sharma") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("civilian_name_input"),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmergencyRed,
                                unfocusedBorderColor = EmergencySurfaceVariant,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Phone Number
                        OutlinedTextField(
                            value = civilianPhone,
                            onValueChange = { civilianPhone = it },
                            label = { Text("Phone / Emergency Contact *") },
                            placeholder = { Text("+91 98765 43210") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("civilian_phone_input"),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = TextMuted) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmergencyRed,
                                unfocusedBorderColor = EmergencySurfaceVariant,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Blood Group Selection
                        Text(
                            text = "BLOOD GROUP *",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TextSecondary)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            bloodTypes.forEach { type ->
                                val isSelected = civilianBloodType == type
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) EmergencyRed else EmergencySurfaceVariant,
                                    border = BorderStroke(1.dp, if (isSelected) EmergencyRed else CardBorder),
                                    modifier = Modifier.clickable { civilianBloodType = type }
                                ) {
                                    Text(
                                        text = type,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else TextPrimary
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Allergies
                        OutlinedTextField(
                            value = civilianAllergies,
                            onValueChange = { civilianAllergies = it },
                            label = { Text("Allergies") },
                            placeholder = { Text("e.g. Penicillin, Peanuts, Sulfa drugs, None") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("civilian_allergies_input"),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmergencyRed,
                                unfocusedBorderColor = EmergencySurfaceVariant,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Prior or Current Health Issues
                        OutlinedTextField(
                            value = civilianMedicalIssues,
                            onValueChange = { civilianMedicalIssues = it },
                            label = { Text("Prior or Current Health Issues") },
                            placeholder = { Text("e.g. Asthma, Type 1 Diabetes, Cardiac Stent, None") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("civilian_issues_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmergencyRed,
                                unfocusedBorderColor = EmergencySurfaceVariant,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Handicap / Mobility Assistance
                        Text(
                            text = "MOBILITY / HANDICAP ASSISTANCE",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TextSecondary)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            handicapOptions.forEach { opt ->
                                val isSelected = civilianHandicap == opt
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) EmergencyRed.copy(alpha = 0.2f) else EmergencySurfaceVariant,
                                    border = BorderStroke(1.dp, if (isSelected) EmergencyRed else CardBorder),
                                    modifier = Modifier.clickable { civilianHandicap = opt }
                                ) {
                                    Text(
                                        text = opt,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) EmergencyRed else TextSecondary,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Emergency Notes / Contact Person
                        OutlinedTextField(
                            value = civilianNotes,
                            onValueChange = { civilianNotes = it },
                            label = { Text("Emergency Notes / Relative Contact") },
                            placeholder = { Text("e.g. In case of emergency notify brother (+91 9123456789)") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmergencyRed,
                                unfocusedBorderColor = EmergencySurfaceVariant,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // Submit Button
                        Button(
                            onClick = {
                                if (civilianName.isBlank()) {
                                    civilianError = "Please enter your full name"
                                    return@Button
                                }
                                onLoginCivilian(
                                    civilianName,
                                    civilianPhone,
                                    civilianBloodType,
                                    civilianAllergies,
                                    civilianMedicalIssues,
                                    civilianHandicap,
                                    civilianNotes
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed, contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("btn_enter_civilian")
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ENTER ECHO AS CIVILIAN",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp
                                )
                            )
                        }
                    }
                }
            } else {
                // ==================== AUTHORITY LOGIN FORM ====================
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = EmergencySurface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Rescue Authority Incident Command",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                            )
                        }
                        Text(
                            text = "Requires verified emergency agency credentials to unlock Central Rescue HQ dashboard and dispatch controls.",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp),
                            modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
                        )

                        if (authorityError != null) {
                            Surface(
                                color = EmergencyRedDark.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, EmergencyRed),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                            ) {
                                Text(
                                    text = authorityError!!,
                                    style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        // Authority Agency Dropdown
                        ExposedDropdownMenuBox(
                            expanded = agencyExpanded,
                            onExpandedChange = { agencyExpanded = !agencyExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = selectedAgency,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Emergency Agency *") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = agencyExpanded) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = WarningAmber,
                                    unfocusedBorderColor = EmergencySurfaceVariant,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )
                            ExposedDropdownMenu(
                                expanded = agencyExpanded,
                                onDismissRequest = { agencyExpanded = false },
                                modifier = Modifier.background(EmergencySurface)
                            ) {
                                agencies.forEach { agency ->
                                    DropdownMenuItem(
                                        text = { Text(agency, color = TextPrimary, fontSize = 13.sp) },
                                        onClick = {
                                            selectedAgency = agency
                                            agencyExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Officer Name
                        OutlinedTextField(
                            value = officerName,
                            onValueChange = { officerName = it; authorityError = null },
                            label = { Text("Officer / Commander Name *") },
                            placeholder = { Text("e.g. Cmdr. Rajesh Kumar") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("authority_name_input"),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = WarningAmber,
                                unfocusedBorderColor = EmergencySurfaceVariant,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Authority ID
                        OutlinedTextField(
                            value = authorityId,
                            onValueChange = { authorityId = it },
                            label = { Text("Authority ID / Call Sign *") },
                            placeholder = { Text("e.g. NDRF-HQ-742") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("authority_id_input"),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = WarningAmber) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = WarningAmber,
                                unfocusedBorderColor = EmergencySurfaceVariant,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Special Credentials Passcode
                        OutlinedTextField(
                            value = passcode,
                            onValueChange = { passcode = it; authorityError = null },
                            label = { Text("Security Passcode / Special Credentials *") },
                            placeholder = { Text("Enter authorized security key") },
                            visualTransformation = if (passcodeVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = { passcodeVisible = !passcodeVisible }) {
                                    Icon(
                                        imageVector = if (passcodeVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle Passcode",
                                        tint = TextMuted
                                    )
                                }
                            },
                            leadingIcon = { Icon(Icons.Default.Key, contentDescription = null, tint = WarningAmber) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("authority_passcode_input"),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = WarningAmber,
                                unfocusedBorderColor = EmergencySurfaceVariant,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Demo Passcode hint
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = WarningAmber.copy(alpha = 0.1f),
                            border = BorderStroke(0.5.dp, WarningAmber.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Demo Credentials: Key RESCUE911 or PIN 1234",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = WarningAmber
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Submit Button
                        Button(
                            onClick = {
                                if (officerName.isBlank()) {
                                    authorityError = "Please enter officer name"
                                    return@Button
                                }
                                if (passcode.isBlank()) {
                                    authorityError = "Special credentials passcode is required"
                                    return@Button
                                }
                                val success = onLoginAuthority(officerName, authorityId, selectedAgency, passcode)
                                if (!success) {
                                    authorityError = "Invalid security credentials. Use demo key: RESCUE911"
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = WarningAmber, contentColor = Color.Black),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("btn_enter_authority")
                        ) {
                            Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "AUTHENTICATE RESCUE HQ",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Footer note
            Text(
                text = "End-to-End Encrypted via Curve25519 & AES-GCM. Autonomous mesh operates offline without active cellular or Internet.",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 10.sp,
                    color = TextMuted,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
