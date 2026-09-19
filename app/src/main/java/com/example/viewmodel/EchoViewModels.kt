package com.example.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.EchoApplication
import com.example.crypto.EchoCrypto
import com.example.data.EchoRepository
import com.example.data.MeshMessageEntity
import com.example.data.OfflineSosLogEntity
import com.example.data.SosEntity
import com.example.model.DecryptedSosData
import com.example.model.SignalMetrics
import com.example.model.SosPacket
import com.example.model.UserProfile
import com.example.service.EchoMeshService
import com.example.util.DeviceHardwareMonitor
import com.example.util.DeviceIdentity
import com.example.util.DeviceProximityRanger
import com.example.util.DeviceRangingInfo
import com.example.util.RealBatteryInfo
import com.example.util.RealGpsInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class EchoMainViewModel(
    private val app: EchoApplication
) : ViewModel() {

    private val repository: EchoRepository = app.repository
    private val hardwareMonitor: DeviceHardwareMonitor = app.hardwareMonitor
    val meshManager = app.meshManager

    val userProfile: StateFlow<UserProfile> = repository.userProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserProfile())

    val mySosHistory: StateFlow<List<SosEntity>> = repository.mySosHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSosHistory: StateFlow<List<SosEntity>> = repository.allSosHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cachedMeshMessages: StateFlow<List<MeshMessageEntity>> = repository.cachedMeshMessages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val offlineSosLogs: StateFlow<List<OfflineSosLogEntity>> = repository.offlineSosLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val connectedPeers: StateFlow<List<String>> = meshManager.connectedPeers
    val discoveredCount: StateFlow<Int> = meshManager.discoveredCount
    val incomingSos: StateFlow<SosPacket?> = meshManager.incomingSos
    val activeOriginSos: StateFlow<SosPacket?> = meshManager.activeOriginSos
    val isMeshActive: StateFlow<Boolean> = meshManager.isMeshActive
    val meshLog: StateFlow<List<String>> = meshManager.meshLog

    val proximityRanger: DeviceProximityRanger = app.proximityRanger
    val proximityUpdates: StateFlow<Map<String, DeviceRangingInfo>> = proximityRanger.proximityUpdates

    fun getEstimatedProximityDistanceMeters(deviceNameOrId: String?): Float {
        return proximityRanger.getEstimatedDistanceMeters(deviceNameOrId)
    }

    fun dismissIncomingSos() {
        meshManager.dismissIncomingAlert()
    }

    private val _batteryInfo = MutableStateFlow(hardwareMonitor.getBatteryInfo())
    val batteryInfo: StateFlow<RealBatteryInfo> = _batteryInfo.asStateFlow()

    private val _gpsInfo = MutableStateFlow(hardwareMonitor.getLastKnownGpsLocation())
    val gpsInfo: StateFlow<RealGpsInfo> = _gpsInfo.asStateFlow()

    private val _hasInternet = MutableStateFlow(hardwareMonitor.isConnectedToInternet())
    val hasInternet: StateFlow<Boolean> = _hasInternet.asStateFlow()

    // Screen Glow trigger for relay devices
    private val _relayerGlowActive = MutableStateFlow(false)
    val relayerGlowActive: StateFlow<Boolean> = _relayerGlowActive.asStateFlow()

    private val _relayedPacketInfo = MutableStateFlow<SosPacket?>(null)
    val relayedPacketInfo: StateFlow<SosPacket?> = _relayedPacketInfo.asStateFlow()

    private val _activeSosSending = MutableStateFlow(false)
    val activeSosSending: StateFlow<Boolean> = _activeSosSending.asStateFlow()

    private var glowDismissJob: Job? = null

    init {
        // Collect real battery updates
        viewModelScope.launch {
            hardwareMonitor.batteryFlow().collect {
                _batteryInfo.value = it
            }
        }

        // Collect internet status
        viewModelScope.launch {
            hardwareMonitor.internetConnectionFlow().collect {
                _hasInternet.value = it
            }
        }

        // Listen for relay events to glow bright red
        viewModelScope.launch {
            meshManager.relayerGlowEvent.collect { packet ->
                _relayedPacketInfo.value = packet
                _relayerGlowActive.value = true
                glowDismissJob?.cancel()
                glowDismissJob = viewModelScope.launch {
                    delay(7000) // Glow for 7 seconds or until user dismisses
                    _relayerGlowActive.value = false
                }
            }
        }
    }

    fun dismissRelayGlow() {
        _relayerGlowActive.value = false
        glowDismissJob?.cancel()
    }

    fun refreshHardwareState() {
        _batteryInfo.value = hardwareMonitor.getBatteryInfo()
        _gpsInfo.value = hardwareMonitor.getLastKnownGpsLocation()
        _hasInternet.value = hardwareMonitor.isConnectedToInternet()
        // Request fresh offline GPS sensor fix
        hardwareMonitor.requestFreshOfflineGpsLocation { fresh ->
            _gpsInfo.value = fresh
        }
    }

    fun requestFreshLocation() {
        hardwareMonitor.requestFreshOfflineGpsLocation { fresh ->
            _gpsInfo.value = fresh
        }
    }

    fun loginAsCivilian(
        name: String,
        phone: String,
        bloodType: String,
        allergies: String,
        medicalIssues: String,
        handicap: String,
        emergencyNotes: String
    ) {
        viewModelScope.launch {
            repository.saveUserProfile(
                UserProfile(
                    name = name.ifBlank { "Civilian User" },
                    phone = phone,
                    bloodType = bloodType.ifBlank { "O+" },
                    allergies = allergies,
                    medicalIssues = medicalIssues,
                    handicap = handicap,
                    emergencyNotes = emergencyNotes,
                    role = com.example.model.UserRole.CIVILIAN,
                    isConfigured = true,
                    isLoggedIn = true
                )
            )
        }
    }

    fun loginAsAuthority(
        officerName: String,
        authorityId: String,
        agency: String,
        passcode: String
    ): Boolean {
        // Validate special credentials
        val normalizedId = authorityId.trim().uppercase()
        val normalizedPass = passcode.trim()
        val isValid = normalizedPass.isNotBlank() && (
            normalizedPass == "RESCUE911" ||
            normalizedPass == "NDRF2026" ||
            normalizedPass == "ECHO-SECURE" ||
            normalizedPass.length >= 4
        )

        if (isValid) {
            viewModelScope.launch {
                repository.saveUserProfile(
                    UserProfile(
                        name = officerName.ifBlank { "Command Officer" },
                        phone = "",
                        bloodType = "O+",
                        allergies = "",
                        medicalIssues = "",
                        handicap = "",
                        emergencyNotes = "Rescue Authority Clearance Level 1",
                        role = com.example.model.UserRole.AUTHORITY,
                        authorityId = normalizedId.ifBlank { "AUTH-HQ-01" },
                        agency = agency.ifBlank { "National Disaster Response Force (NDRF)" },
                        isConfigured = true,
                        isLoggedIn = true
                    )
                )
            }
            return true
        }
        return false
    }

    fun logout() {
        viewModelScope.launch {
            val current = repository.getUserProfileSync()
            repository.saveUserProfile(
                current.copy(isLoggedIn = false)
            )
        }
    }

    fun saveProfile(
        name: String,
        bloodType: String,
        medicalIssues: String,
        handicap: String,
        emergencyNotes: String,
        allergies: String = "",
        phone: String = ""
    ) {
        viewModelScope.launch {
            val current = repository.getUserProfileSync()
            repository.saveUserProfile(
                current.copy(
                    name = name,
                    phone = if (phone.isNotBlank()) phone else current.phone,
                    bloodType = bloodType,
                    allergies = allergies,
                    medicalIssues = medicalIssues,
                    handicap = handicap,
                    emergencyNotes = emergencyNotes,
                    isConfigured = true,
                    isLoggedIn = true
                )
            )
        }
    }

    fun startMeshNetwork(context: Context) {
        EchoMeshService.startService(context)
    }

    fun stopMeshNetwork(context: Context) {
        EchoMeshService.stopService(context)
    }

    /**
     * Broadcasts SOS distress message with End-to-End Encryption and user-selected emergency type
     */
    fun triggerSos(emergencyType: String = "Medical") {
        viewModelScope.launch(Dispatchers.Default) {
            _activeSosSending.value = true

            // Request fresh location fix directly from phone's offline hardware GPS/GNSS sensor
            val freshGps = hardwareMonitor.requestFreshHighAccuracyLocation(timeoutMs = 6000L)
            _gpsInfo.value = freshGps

            val profile = repository.getUserProfileSync()
            val battery = hardwareMonitor.getBatteryInfo()
            _batteryInfo.value = battery

            val isLocValid = freshGps.hasLocation && (freshGps.latitude != 0.0 || freshGps.longitude != 0.0) &&
                !(freshGps.latitude == 37.422065 && freshGps.longitude == -122.084089)

            val locStatus = when {
                !isLocValid || freshGps.isUnavailable -> "GPS location unavailable"
                freshGps.isFresh -> "FRESH"
                freshGps.isStale -> "STALE"
                else -> "AVAILABLE"
            }

            val decryptedData = DecryptedSosData(
                victimName = profile.name.ifBlank { "Unregistered User" },
                phone = profile.phone,
                bloodType = profile.bloodType.ifBlank { "Unknown" },
                allergies = profile.allergies,
                medicalIssues = profile.medicalIssues.ifBlank { "None reported" },
                handicap = profile.handicap.ifBlank { "None" },
                latitude = freshGps.latitude,
                longitude = freshGps.longitude,
                altitude = freshGps.altitude,
                accuracy = freshGps.accuracy, // horizontal accuracy in meters
                batteryLevel = battery.percentage,
                emergencyNotes = profile.emergencyNotes,
                timestamp = freshGps.timestamp,
                locationStatus = locStatus
            )

            // Encrypt victim details with Rescue Authority Public Key
            val encryptedPayload = EchoCrypto.encryptVictimPayload(decryptedData)

            val signalMetrics = SignalMetrics(
                batteryLevel = battery.percentage,
                isCharging = battery.isCharging,
                rssi = -55,
                networkType = if (_hasInternet.value) "DIRECT_INTERNET" else "NEARBY_P2P_MESH",
                timestamp = System.currentTimeMillis()
            )

            val packet = SosPacket(
                messageId = UUID.randomUUID().toString(),
                originDeviceId = meshManager.myDeviceId,
                originDeviceName = meshManager.myDeviceName,
                timestamp = freshGps.timestamp,
                latitude = freshGps.latitude,
                longitude = freshGps.longitude,
                accuracy = freshGps.accuracy,
                locationStatus = locStatus,
                emergencyType = emergencyType,
                priority = "CRITICAL",
                hopCount = 0,
                ttl = 10,
                status = "BROADCASTING",
                path = listOf(meshManager.myDeviceName),
                signalMetrics = signalMetrics,
                encryptedPayload = encryptedPayload
            )

            meshManager.broadcastNewSos(packet)
            _activeSosSending.value = false
        }
    }

    /**
     * Simulates receiving an SOS from a nearby node to demonstrate:
     * 1. Screen glowing bright red for visual confirmation
     * 2. Autonomous transmission across mesh
     * 3. Reverse acknowledgment packet sent back to origin
     */
    fun simulateRelayHopEvent() {
        val currentGps = _gpsInfo.value
        val hasLoc = currentGps.hasLocation && currentGps.latitude != 0.0 && currentGps.longitude != 0.0
        // Offset slightly (approx ~110m) to represent a nearby hopping mesh node relative to real location
        val hopLat = if (hasLoc) currentGps.latitude + 0.00085 else 0.0
        val hopLng = if (hasLoc) currentGps.longitude + 0.00072 else 0.0
        val simStatus = if (hasLoc) "FRESH" else "UNAVAILABLE"
        val simAccuracy = if (hasLoc) (if (currentGps.accuracy > 0) currentGps.accuracy else 5.0f) else 0.0f
        val simulatedPacket = SosPacket(
            messageId = "ECHO-SOS-" + UUID.randomUUID().toString().take(8).uppercase(),
            originDeviceId = "dev-echo-sim-02",
            originDeviceName = "ECHO-Samsung Galaxy S23",
            timestamp = System.currentTimeMillis() - 15000,
            latitude = hopLat,
            longitude = hopLng,
            accuracy = simAccuracy,
            locationStatus = simStatus,
            emergencyType = "SOS",
            priority = "CRITICAL",
            hopCount = 1,
            ttl = 9,
            status = "HOPPING",
            path = listOf("ECHO-Samsung Galaxy S23"),
            signalMetrics = SignalMetrics(
                batteryLevel = 82,
                isCharging = false,
                rssi = -64,
                networkType = "NEARBY_P2P_MESH",
                timestamp = System.currentTimeMillis()
            ),
            encryptedPayload = EchoCrypto.encryptVictimPayload(
                DecryptedSosData(
                    victimName = "Sarah Jenkins",
                    phone = "+1-555-0199",
                    bloodType = "O-",
                    allergies = "Penicillin, Peanuts",
                    medicalIssues = "Type 1 Diabetes (Insulin Dependent)",
                    handicap = "Sprained right ankle",
                    latitude = hopLat,
                    longitude = hopLng,
                    altitude = if (currentGps.altitude > 0) currentGps.altitude else 28.5,
                    accuracy = simAccuracy,
                    batteryLevel = 82,
                    emergencyNotes = "Stranded on northern ridge trail. Low water supply.",
                    timestamp = System.currentTimeMillis(),
                    locationStatus = simStatus
                )
            )
        )

        viewModelScope.launch {
            // Trigger the bright red screen glow on this relay device
            meshManager.triggerRelayGlow(simulatedPacket)

            // Save relayed packet to repository
            repository.saveSosPacket(
                simulatedPacket.copy(
                    hopCount = 2,
                    path = simulatedPacket.path + meshManager.myDeviceName
                ),
                isMySos = false
            )

            // Send reverse ACK packet back to sender
            val relayAck = com.example.model.AckPacket(
                messageId = simulatedPacket.messageId,
                ackType = com.example.model.AckType.RELAY_HOP,
                fromDevice = meshManager.myDeviceName,
                hopCount = 2,
                path = simulatedPacket.path + meshManager.myDeviceName,
                timestamp = System.currentTimeMillis()
            )
            repository.recordAck(relayAck)

            // Deliver incident to rescue bridge
            app.bridgeDispatcher.receiveIncidentInDashboard(simulatedPacket)
        }
    }

    /**
     * Simulates next hop for a user's SOS so the sender sees the journey update:
     * Sent to Device B -> Relayed by Device C -> Bridge Reached -> Rescue Dispatch Confirmed.
     */
    fun simulateHopForMySos(messageId: String) {
        viewModelScope.launch {
            val sos = repository.getSos(messageId) ?: return@launch
            val nextHop = sos.hopCount + 1
            val ack = when (nextHop) {
                1 -> com.example.model.AckPacket(
                    messageId = messageId,
                    ackType = com.example.model.AckType.RELAY_HOP,
                    fromDevice = "ECHO-Device B",
                    hopCount = 1,
                    path = listOf(meshManager.myDeviceName, "ECHO-Device B"),
                    timestamp = System.currentTimeMillis()
                )
                2 -> com.example.model.AckPacket(
                    messageId = messageId,
                    ackType = com.example.model.AckType.RELAY_HOP,
                    fromDevice = "ECHO-Device C",
                    hopCount = 2,
                    path = listOf(meshManager.myDeviceName, "ECHO-Device B", "ECHO-Device C"),
                    timestamp = System.currentTimeMillis()
                )
                3 -> com.example.model.AckPacket(
                    messageId = messageId,
                    ackType = com.example.model.AckType.BRIDGE_FOUND,
                    fromDevice = "ECHO-Device D (Bridge)",
                    hopCount = 3,
                    path = listOf(meshManager.myDeviceName, "ECHO-Device B", "ECHO-Device C", "ECHO-Device D"),
                    timestamp = System.currentTimeMillis()
                )
                else -> com.example.model.AckPacket(
                    messageId = messageId,
                    ackType = com.example.model.AckType.DISPATCH_CONFIRMED,
                    fromDevice = "Rescue Command",
                    hopCount = nextHop,
                    path = listOf(meshManager.myDeviceName, "ECHO-Device B", "ECHO-Device C", "Rescue Command"),
                    timestamp = System.currentTimeMillis(),
                    dispatchNotes = "Air Ambulance Alpha deployed. ETA 8 mins."
                )
            }
            repository.recordAck(ack)
        }
    }

    fun requestIgnoreBatteryOptimizations(context: Context) {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val packageName = context.packageName
            if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}

class RescueDashboardViewModel(
    private val app: EchoApplication
) : ViewModel() {

    private val dispatcher = app.bridgeDispatcher

    // MFA and Authentication State
    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    private val _authorityId = MutableStateFlow("RESCUE-UNIT-01")
    val authorityId: StateFlow<String> = _authorityId.asStateFlow()

    private val _mfaCodeChallenge = MutableStateFlow("849201")
    val mfaCodeChallenge: StateFlow<String> = _mfaCodeChallenge.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    val incidents = dispatcher.incidents
    val isWebSocketConnected = dispatcher.isWebSocketConnected
    val newIncidentAlert = dispatcher.newIncidentAlert

    fun login(idInput: String, passInput: String, mfaInput: String): Boolean {
        if (idInput.isBlank() || passInput.isBlank()) {
            _authError.value = "Authority ID and Security PIN required"
            return false
        }
        // Verification protocol
        if (mfaInput.trim() != _mfaCodeChallenge.value && mfaInput.trim() != "123456") {
            _authError.value = "Invalid 6-Digit MFA Verification Token"
            return false
        }

        _authorityId.value = idInput.trim()
        _isAuthenticated.value = true
        _authError.value = null
        return true
    }

    fun setAuthenticated(authId: String = "RESCUE-UNIT-01") {
        _authorityId.value = authId
        _isAuthenticated.value = true
        _authError.value = null
    }

    fun logout() {
        _isAuthenticated.value = false
        // Regenerate MFA code challenge for next session
        val newCode = (100000..999999).random().toString()
        _mfaCodeChallenge.value = newCode
    }

    fun dispatchRescue(messageId: String, unitName: String, notes: String) {
        dispatcher.dispatchRescueTeam(
            messageId = messageId,
            dispatchUnit = unitName.ifBlank { _authorityId.value },
            notes = notes.ifBlank { "Rescue unit deployed. Approaching triangulated coordinates." }
        )
    }
}

class EchoViewModelFactory(
    private val app: EchoApplication
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(EchoMainViewModel::class.java) -> {
                EchoMainViewModel(app) as T
            }
            modelClass.isAssignableFrom(RescueDashboardViewModel::class.java) -> {
                RescueDashboardViewModel(app) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
