package com.example.mesh

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.example.data.EchoRepository
import com.example.model.AckPacket
import com.example.model.AckType
import com.example.model.MeshMessage
import com.example.model.RoutingNodeInfo
import com.example.model.SosPacket
import com.example.service.EchoMeshService
import com.example.util.DeviceHardwareMonitor
import com.example.util.DeviceIdentity
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

data class DiscoveredEndpoint(
    val endpointId: String,
    val cleanName: String,
    val remoteDeviceId: String,
    val discoveredTime: Long = System.currentTimeMillis()
)

/**
 * Manages Google Nearby Connections API peer-to-peer mesh networking.
 * Uses Strategy.P2P_CLUSTER for autonomous, completely offline M-to-N mesh routing.
 * Relaying happens automatically in background without requiring user intervention.
 */
class NearbyMeshManager(
    private val context: Context,
    private val repository: EchoRepository,
    private val hardwareMonitor: DeviceHardwareMonitor,
    val proximityRanger: com.example.util.DeviceProximityRanger? = null,
    private val scope: CoroutineScope
) {

    companion object {
        const val TAG = "ECHO_MESH"
        const val SERVICE_ID = "com.example.echo.mesh"
        val STRATEGY: Strategy = Strategy.P2P_CLUSTER
    }

    private val connectionsClient: ConnectionsClient = Nearby.getConnectionsClient(context)
    val myDeviceName: String = DeviceIdentity.getDeviceName()
    val myDeviceId: String = DeviceIdentity.getDeviceId(context)
    val advertisedName: String get() = getDynamicAdvertisedName()

    // Core Routing Engine for intelligent, reliable multi-hop relaying
    val routingEngine = MeshRoutingEngine()

    fun getDynamicAdvertisedName(): String {
        val hasNet = hardwareMonitor.isConnectedToInternet()
        return "${myDeviceName.take(18)}#${myDeviceId.take(8)}#${if (hasNet) "1" else "0"}"
    }

    // Connected endpoints: endpointId -> peerName
    private val connectedEndpoints = ConcurrentHashMap<String, String>()
    // Discovered endpoints: endpointId -> DiscoveredEndpoint
    private val discoveredEndpoints = ConcurrentHashMap<String, DiscoveredEndpoint>()
    // Endpoint names mapped during discovery / handshake
    private val endpointNames = ConcurrentHashMap<String, String>()
    // Endpoints currently in connection handshake
    private val connectingEndpoints = Collections.synchronizedSet(HashSet<String>())
    // Fallback connection jobs
    private val fallbackJobs = ConcurrentHashMap<String, Job>()

    // Deduplication set for processed message IDs
    private val processedMessageIds = Collections.synchronizedSet(HashSet<String>())
    // Deduplication set for processed ACK IDs
    private val processedAckIds = Collections.synchronizedSet(HashSet<String>())
    // In-memory store-and-forward pending packets
    private val pendingPackets = ConcurrentHashMap<String, SosPacket>()

    // Observable states
    private val _connectedPeers = MutableStateFlow<List<String>>(emptyList())
    val connectedPeers: StateFlow<List<String>> = _connectedPeers.asStateFlow()

    private val _discoveredCount = MutableStateFlow(0)
    val discoveredCount: StateFlow<Int> = _discoveredCount.asStateFlow()

    private val _isMeshActive = MutableStateFlow(false)
    val isMeshActive: StateFlow<Boolean> = _isMeshActive.asStateFlow()

    private val _meshLog = MutableStateFlow<List<String>>(emptyList())
    val meshLog: StateFlow<List<String>> = _meshLog.asStateFlow()

    // Screen Glow trigger when signal is received and relayed
    private val _relayerGlowEvent = MutableSharedFlow<SosPacket>(extraBufferCapacity = 10)
    val relayerGlowEvent: SharedFlow<SosPacket> = _relayerGlowEvent.asSharedFlow()

    // Latest incoming SOS received (for Phone B display)
    private val _incomingSos = MutableStateFlow<SosPacket?>(null)
    val incomingSos: StateFlow<SosPacket?> = _incomingSos.asStateFlow()

    // Latest originated SOS for detailed status display on Phone A
    private val _activeOriginSos = MutableStateFlow<SosPacket?>(null)
    val activeOriginSos: StateFlow<SosPacket?> = _activeOriginSos.asStateFlow()

    // Callback for bridge dispatch to rescue dashboard
    var onBridgeDispatchNeeded: ((SosPacket) -> Unit)? = null

    init {
        startInternetBridgeMonitor()
    }

    private fun addLog(entry: String) {
        _meshLog.value = (_meshLog.value + entry).takeLast(100)
    }

    fun triggerRelayGlow(packet: SosPacket) {
        _relayerGlowEvent.tryEmit(packet)
    }

    fun dismissIncomingAlert() {
        _incomingSos.value = null
    }

    private var isAdvertising = false
    private var isDiscovering = false

    /**
     * Starts continuous autonomous mesh networking:
     * simultaneously advertises and discovers to find all nearby ECHO devices.
     */
    fun startMesh() {
        if (_isMeshActive.value) {
            restartDiscoveryAndAdvertising()
            return
        }
        _isMeshActive.value = true
        Log.i(TAG, "Starting autonomous ECHO Mesh as $advertisedName on P2P_CLUSTER")
        addLog("Starting autonomous ECHO Mesh ($myDeviceName)...")
        startAdvertising()
        startDiscovery()
    }

    fun restartDiscoveryAndAdvertising() {
        if (!_isMeshActive.value) return
        Log.i(TAG, "Refreshing ECHO Mesh advertising and discovery...")
        try {
            connectionsClient.stopAdvertising()
            connectionsClient.stopDiscovery()
        } catch (e: Exception) {
            Log.w(TAG, "Notice while stopping previous mesh session: ${e.message}")
        }
        isAdvertising = false
        isDiscovering = false
        startAdvertising()
        startDiscovery()
    }

    fun stopMesh() {
        _isMeshActive.value = false
        isAdvertising = false
        isDiscovering = false
        try {
            connectionsClient.stopAdvertising()
            connectionsClient.stopDiscovery()
            connectionsClient.stopAllEndpoints()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping mesh: ${e.message}")
        }
        connectedEndpoints.clear()
        discoveredEndpoints.clear()
        connectingEndpoints.clear()
        fallbackJobs.values.forEach { it.cancel() }
        fallbackJobs.clear()
        _connectedPeers.value = emptyList()
        _discoveredCount.value = 0
        Log.i(TAG, "ECHO Mesh stopped.")
        addLog("ECHO Mesh stopped.")
    }

    private fun startAdvertising() {
        if (!_isMeshActive.value) return
        val advertisingOptions = AdvertisingOptions.Builder()
            .setStrategy(STRATEGY)
            .setLowPower(true)
            .setDisruptiveUpgrade(false)
            .build()

        connectionsClient.startAdvertising(
            advertisedName,
            SERVICE_ID,
            connectionLifecycleCallback,
            advertisingOptions
        ).addOnSuccessListener {
            isAdvertising = true
            Log.i(TAG, "Advertising started as $advertisedName on Bluetooth/BLE (P2P_CLUSTER)")
            addLog("Bluetooth/BLE Advertising active as $myDeviceName")
        }.addOnFailureListener { e ->
            isAdvertising = false
            Log.e(TAG, "Advertising failed: ${e.localizedMessage}")
            addLog("Advertising failed: ${e.localizedMessage}")
            // Auto-retry after brief delay if mesh remains active
            if (_isMeshActive.value) {
                scope.launch(Dispatchers.IO) {
                    delay(3500)
                    if (_isMeshActive.value && !isAdvertising) {
                        Log.i(TAG, "Retrying advertising...")
                        startAdvertising()
                    }
                }
            }
        }
    }

    private fun startDiscovery() {
        if (!_isMeshActive.value) return
        val discoveryOptions = DiscoveryOptions.Builder()
            .setStrategy(STRATEGY)
            .setLowPower(true)
            .build()

        connectionsClient.startDiscovery(
            SERVICE_ID,
            endpointDiscoveryCallback,
            discoveryOptions
        ).addOnSuccessListener {
            isDiscovering = true
            Log.i(TAG, "Discovery started for service $SERVICE_ID on Bluetooth/BLE (P2P_CLUSTER)")
            addLog("Bluetooth/BLE Discovery active for nearby ECHO nodes")
        }.addOnFailureListener { e ->
            isDiscovering = false
            Log.e(TAG, "Discovery failed: ${e.localizedMessage}")
            addLog("Discovery failed: ${e.localizedMessage}")
            // Auto-retry after brief delay if mesh remains active
            if (_isMeshActive.value) {
                scope.launch(Dispatchers.IO) {
                    delay(3500)
                    if (_isMeshActive.value && !isDiscovering) {
                        Log.i(TAG, "Retrying discovery...")
                        startDiscovery()
                    }
                }
            }
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val rawName = info.endpointName
            val parts = rawName.split("#")
            val cleanName = parts.getOrNull(0) ?: rawName
            val remoteDeviceId = parts.getOrNull(1) ?: rawName
            val hasInternetAdv = parts.getOrNull(2) == "1"

            val endpoint = DiscoveredEndpoint(endpointId, cleanName, remoteDeviceId)
            discoveredEndpoints[endpointId] = endpoint
            endpointNames[endpointId] = cleanName
            _discoveredCount.value = discoveredEndpoints.size
            proximityRanger?.registerPeerProximity(cleanName, remoteDeviceId, initialDistanceMeters = 1.0f)

            if (hasInternetAdv) {
                routingEngine.updatePeerInfo(
                    endpointId,
                    RoutingNodeInfo(
                        deviceId = remoteDeviceId,
                        deviceName = cleanName,
                        hasInternet = true,
                        hopsToBridge = 0
                    )
                )
                Log.i(TAG, "Device discovered [Internet Bridge!]: $cleanName ($endpointId)")
                addLog("Discovered Internet Bridge: $cleanName")
            } else {
                Log.i(TAG, "Device discovered: $cleanName ($endpointId)")
                addLog("Device discovered: $cleanName ($endpointId)")
            }

            // Symmetrical connection collision prevention:
            // Tie-break using deviceId. The device with lexicographically greater deviceId initiates.
            if (myDeviceId > remoteDeviceId) {
                initiateConnection(endpointId, cleanName, "Designated Initiator")
            } else {
                Log.d(TAG, "Waiting for designated initiator $cleanName to connect...")
                // Fallback: If not connected after 6 seconds, initiate connection anyway
                val job = scope.launch(Dispatchers.IO) {
                    delay(6000)
                    if (!connectedEndpoints.containsKey(endpointId) && !connectingEndpoints.contains(endpointId)) {
                        initiateConnection(endpointId, cleanName, "Fallback Initiator")
                    }
                }
                fallbackJobs[endpointId] = job
            }
        }

        override fun onEndpointLost(endpointId: String) {
            val endpoint = discoveredEndpoints.remove(endpointId)
            fallbackJobs.remove(endpointId)?.cancel()
            _discoveredCount.value = discoveredEndpoints.size
            val name = endpoint?.cleanName ?: endpointId
            Log.i(TAG, "Device left proximity: $name ($endpointId)")
            addLog("Device left proximity: $name")
        }
    }

    private fun initiateConnection(endpointId: String, cleanName: String, reason: String) {
        if (connectedEndpoints.containsKey(endpointId) || connectingEndpoints.contains(endpointId)) return

        connectingEndpoints.add(endpointId)
        Log.i(TAG, "Connection initiated with $cleanName ($endpointId) [$reason]")
        addLog("Connecting to $cleanName ($reason)...")

        connectionsClient.requestConnection(
            advertisedName,
            endpointId,
            connectionLifecycleCallback
        ).addOnFailureListener { e ->
            connectingEndpoints.remove(endpointId)
            Log.w(TAG, "Connection request to $cleanName ($endpointId) failed: ${e.message}")
            addLog("Connect to $cleanName failed: ${e.message}")
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            val rawName = info.endpointName
            val parts = rawName.split("#")
            val cleanName = parts.getOrNull(0) ?: rawName
            endpointNames[endpointId] = cleanName
            connectingEndpoints.add(endpointId)

            Log.i(TAG, "Connection initiated with $cleanName ($endpointId)")
            addLog("Handshake initiated with $cleanName, auto-accepting...")

            // Auto-accept without user prompt for seamless emergency mesh
            connectionsClient.acceptConnection(endpointId, payloadCallback)
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to accept connection with $cleanName: ${e.message}")
                }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            connectingEndpoints.remove(endpointId)
            fallbackJobs.remove(endpointId)?.cancel()
            val peerName = endpointNames[endpointId] ?: "ECHO-Peer-${endpointId.take(4)}"

            if (result.status.statusCode == ConnectionsStatusCodes.STATUS_OK) {
                connectedEndpoints[endpointId] = peerName
                proximityRanger?.registerPeerProximity(peerName, endpointId, initialDistanceMeters = 1.0f)
                Log.i(TAG, "Connection established with $peerName ($endpointId)")
                addLog("Connection established with $peerName ($endpointId)")
                updatePeerList()

                // Exchange routing node coordinates and internet bridge telemetry
                sendRoutingInfoToPeer(endpointId)

                // STORE-AND-FORWARD: Deliver any stored / pending SOS packets to this new peer
                flushStoredPacketsToPeer(endpointId, peerName)
            } else {
                Log.w(TAG, "Connection rejected/failed with $peerName ($endpointId) [code: ${result.status.statusCode}]")
                addLog("Connection failed with $peerName (code: ${result.status.statusCode})")
                connectedEndpoints.remove(endpointId)
                routingEngine.removePeer(endpointId)
                updatePeerList()
            }
        }

        override fun onDisconnected(endpointId: String) {
            val name = connectedEndpoints.remove(endpointId) ?: endpointNames[endpointId] ?: endpointId
            connectingEndpoints.remove(endpointId)
            routingEngine.removePeer(endpointId)
            Log.w(TAG, "Connection lost with $name ($endpointId)")
            addLog("Connection lost with $name")
            updatePeerList()

            // When a connection is lost, automatically try other reachable discovered ECHO devices
            scope.launch(Dispatchers.IO) {
                delay(2000)
                reconnectToAvailableEndpoints()
            }
        }
    }

    private fun reconnectToAvailableEndpoints() {
        Log.i(TAG, "Checking reconnection to available endpoints (${discoveredEndpoints.size} discovered, ${connectedEndpoints.size} connected)...")
        for ((epId, ep) in discoveredEndpoints) {
            if (!connectedEndpoints.containsKey(epId) && !connectingEndpoints.contains(epId)) {
                initiateConnection(epId, ep.cleanName, "Reconnection")
            }
        }
        if (connectedEndpoints.isEmpty() && _isMeshActive.value) {
            restartDiscoveryAndAdvertising()
        }
    }

    private fun updatePeerList() {
        _connectedPeers.value = connectedEndpoints.values.toList()
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                val bytes = payload.asBytes() ?: return
                val jsonStr = String(bytes, StandardCharsets.UTF_8)
                val message = MeshMessage.fromJson(jsonStr) ?: return
                handleIncomingMeshMessage(endpointId, message)
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // High-speed payload transfer progress
        }
    }

    /**
     * Originates a new SOS broadcast from Phone A.
     */
    fun broadcastNewSos(sosPacket: SosPacket) {
        Log.i(TAG, "SOS created [${sosPacket.messageId}] by $myDeviceName")
        addLog("SOS created [${sosPacket.messageId.take(8)}] by $myDeviceName")

        processedMessageIds.add(sosPacket.messageId)
        pendingPackets[sosPacket.messageId] = sosPacket
        _activeOriginSos.value = sosPacket

        scope.launch(Dispatchers.IO) {
            repository.saveSosPacket(sosPacket, isMySos = true)
        }

        // Direct Internet check: If origin device has Internet, bridge immediately!
        if (hardwareMonitor.isConnectedToInternet()) {
            Log.i(TAG, "Backend upload attempted for SOS [${sosPacket.messageId}] via Internet bridge")
            addLog("Local device has Internet! Acting as immediate bridge...")
            try {
                onBridgeDispatchNeeded?.invoke(sosPacket)
                Log.i(TAG, "Backend upload successful for SOS [${sosPacket.messageId}]")
                scope.launch(Dispatchers.IO) {
                    repository.markPacketSynced(sosPacket.messageId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Backend upload failed for SOS [${sosPacket.messageId}]: ${e.message}")
            }

            val ack = AckPacket(
                messageId = sosPacket.messageId,
                ackType = AckType.BRIDGE_FOUND,
                fromDevice = "$myDeviceName (Direct Internet)",
                hopCount = 0,
                path = listOf(myDeviceName)
            )
            handleIncomingAck(ack)
        }

        if (connectedEndpoints.isEmpty()) {
            Log.i(TAG, "No direct peers connected yet. Stored locally for store-and-forward.")
            addLog("No peers connected yet. SOS stored locally for store-and-forward.")
            return
        }

        val message = MeshMessage(type = "SOS", sosPacket = sosPacket)
        val data = message.toJson().toByteArray(StandardCharsets.UTF_8)

        // Select best next-hop (nearest available Phone B or direct Bridge) using Core Routing Engine
        val currentGps = hardwareMonitor.getLastKnownGpsLocation()
        val currentBattery = hardwareMonitor.getBatteryInfo().percentage
        val candidates = routingEngine.rankCandidatePeers(
            currentGps = currentGps,
            currentBattery = currentBattery,
            packet = sosPacket,
            availablePeers = connectedEndpoints
        )

        if (candidates.isEmpty()) {
            Log.w(TAG, "No candidate peers available for initial hop. Broadcasting to all connected.")
            broadcastBytes(data, excludeEndpoint = null, sosPacket = sosPacket)
            return
        }

        val primary = candidates.first()
        Log.i(TAG, "Chain routing: SOS hop directed to nearest peer ${primary.peerName} (${primary.reason})")
        addLog("Routing SOS to ${primary.peerName} (${primary.reason})")
        transmitPacketToEndpoint(primary.endpointId, primary.peerName, data, sosPacket)

        // If an Internet Bridge is available in candidates and is distinct from primary, route to it immediately!
        val bridgeCandidate = candidates.firstOrNull { it.hasInternet && it.endpointId != primary.endpointId }
        if (bridgeCandidate != null) {
            Log.i(TAG, "Priority routing: Transmitting directly to Internet Bridge ${bridgeCandidate.peerName}")
            addLog("Routing directly to Internet Bridge ${bridgeCandidate.peerName}!")
            transmitPacketToEndpoint(bridgeCandidate.endpointId, bridgeCandidate.peerName, data, sosPacket)
        }

        // Reliability Fallback: If primary does not acknowledge within 2800ms, forward to next candidate
        if (candidates.size > 1) {
            scope.launch(Dispatchers.IO) {
                delay(2800)
                val isAcked = _activeOriginSos.value?.status?.contains("acknowledged", ignoreCase = true) == true ||
                              _activeOriginSos.value?.status?.contains("BRIDGE", ignoreCase = true) == true ||
                              _activeOriginSos.value?.status?.contains("HOPPING", ignoreCase = true) == true
                if (!isAcked && _isMeshActive.value) {
                    val fallbackCandidate = candidates.getOrNull(1)
                    if (fallbackCandidate != null && connectedEndpoints.containsKey(fallbackCandidate.endpointId)) {
                        Log.w(TAG, "Reliability fallback: No ACK from ${primary.peerName}. Forwarding to next candidate ${fallbackCandidate.peerName}")
                        addLog("Fallback routing to ${fallbackCandidate.peerName}...")
                        transmitPacketToEndpoint(fallbackCandidate.endpointId, fallbackCandidate.peerName, data, sosPacket)
                    }
                }
            }
        }
    }

    /**
     * Store-and-forward: Transmit all active pending / stored SOS messages to a newly connected peer.
     */
    private fun flushStoredPacketsToPeer(endpointId: String, peerName: String) {
        scope.launch(Dispatchers.IO) {
            val activePackets = repository.getActivePacketsForRelay().toMutableList()
            // Add any memory-cached packets not yet flushed
            pendingPackets.values.forEach { p ->
                if (activePackets.none { it.messageId == p.messageId } && p.ttl > 0) {
                    activePackets.add(p)
                }
            }

            if (activePackets.isEmpty()) return@launch

            Log.i(TAG, "Store-and-forward: Transmitting ${activePackets.size} stored SOS packet(s) to new peer $peerName")
            addLog("Store-and-forward: Forwarding stored SOS to $peerName")

            for (packet in activePackets) {
                // Do not loop back if peer is origin or in path
                if (packet.originDeviceId == peerName || packet.path.contains(peerName)) continue

                val msg = MeshMessage(type = "SOS", sosPacket = packet)
                val bytes = msg.toJson().toByteArray(StandardCharsets.UTF_8)
                try {
                    connectionsClient.sendPayload(endpointId, Payload.fromBytes(bytes))
                        .addOnSuccessListener {
                            Log.i(TAG, "SOS sent [${packet.messageId}] to $peerName ($endpointId)")
                        }
                    repository.markSentToPeer(packet.messageId, peerName)
                } catch (e: Exception) {
                    Log.e(TAG, "Store-and-forward send failed to $peerName: ${e.message}")
                }
            }
        }
    }

    /**
     * Broadcasts an ACK packet across the mesh so the origin receives status updates.
     */
    fun broadcastAck(ackPacket: AckPacket) {
        val ackKey = "${ackPacket.messageId}_${ackPacket.ackType}_${ackPacket.fromDevice}_${ackPacket.hopCount}"
        if (!processedAckIds.add(ackKey)) return

        val message = MeshMessage(type = "ACK", ackPacket = ackPacket)
        val data = message.toJson().toByteArray(StandardCharsets.UTF_8)
        broadcastBytes(data, excludeEndpoint = null, sosPacket = null)
    }

    private fun broadcastBytes(bytes: ByteArray, excludeEndpoint: String?, sosPacket: SosPacket?) {
        val targets = connectedEndpoints.keys.filter { it != excludeEndpoint }
        if (targets.isEmpty()) {
            return
        }
        for (targetId in targets) {
            val peerName = connectedEndpoints[targetId] ?: targetId
            try {
                connectionsClient.sendPayload(targetId, Payload.fromBytes(bytes))
                    .addOnSuccessListener {
                        if (sosPacket != null) {
                            Log.i(TAG, "SOS sent [${sosPacket.messageId}] to $peerName ($targetId)")
                            scope.launch(Dispatchers.IO) {
                                repository.markSentToPeer(sosPacket.messageId, peerName)
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "Failed payload to $peerName: ${e.message}")
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send payload to $targetId: ${e.message}")
            }
        }
    }

    private fun handleIncomingMeshMessage(fromEndpointId: String, message: MeshMessage) {
        when (message.type) {
            "SOS" -> {
                val packet = message.sosPacket ?: return
                handleIncomingSos(fromEndpointId, packet)
            }
            "ACK" -> {
                val ack = message.ackPacket ?: return
                handleIncomingAck(ack, fromEndpointId)
            }
            "ROUTING_INFO" -> {
                val routing = message.routingInfo ?: return
                routingEngine.updatePeerInfo(fromEndpointId, routing)
                val bridgeTag = if (routing.hasInternet) " [Active Internet Bridge]" else ""
                Log.i(TAG, "Routing node updated from ${routing.deviceName}$bridgeTag (HopsToBridge=${routing.hopsToBridge}, Bat=${routing.batteryLevel}%)")
                addLog("Peer routing updated: ${routing.deviceName}$bridgeTag")
            }
        }
    }

    /**
     * Automatic relay logic executed autonomously by every receiving phone (Phone B -> Phone C -> Bridge).
     * Creates an autonomous chain without requiring user interaction on relay devices.
     */
    private fun handleIncomingSos(fromEndpointId: String, packet: SosPacket) {
        val fromPeerName = connectedEndpoints[fromEndpointId] ?: endpointNames[fromEndpointId] ?: fromEndpointId

        Log.i(TAG, "SOS received [${packet.messageId}] from $fromPeerName (Hop ${packet.hopCount}, TTL ${packet.ttl})")
        addLog("SOS received from $fromPeerName (Hop ${packet.hopCount}, TTL ${packet.ttl})")

        // 1. Loop Prevention: check if already processed
        if (!processedMessageIds.add(packet.messageId)) {
            Log.w(TAG, "Duplicate SOS ignored [${packet.messageId}] (Loop prevention)")
            addLog("Duplicate SOS [${packet.messageId.take(8)}] ignored (Loop Prevention).")
            return
        }

        // 2. Loop Prevention: check if I am the origin
        if (packet.originDeviceId == myDeviceId) {
            Log.w(TAG, "Duplicate SOS ignored [${packet.messageId}] (Looped back to origin)")
            addLog("SOS looped back to origin. Dropping.")
            return
        }

        // 3. TTL Loop Prevention: If TTL <= 1, stop forwarding
        if (packet.ttl <= 1) {
            Log.w(TAG, "TTL expired for SOS [${packet.messageId}], stopped forwarding")
            addLog("TTL expired for SOS [${packet.messageId.take(8)}]. Stored as terminal hop.")
            // Save locally without forwarding
            val terminalPacket = packet.copy(
                hopCount = packet.hopCount + 1,
                ttl = 0,
                path = packet.path + myDeviceName,
                status = "TTL_EXPIRED"
            )
            scope.launch(Dispatchers.IO) {
                repository.saveSosPacket(terminalPacket, isMySos = false)
            }
            return
        }

        // 4. Autonomous Relayer Feedback: Glow bright red, vibrate, update notification & UI state
        // Register device-to-device short-range proximity for the victim phone
        proximityRanger?.registerPeerProximity(packet.originDeviceName, packet.originDeviceId, initialDistanceMeters = 1.0f)
        if (packet.signalMetrics.rssi != 0) {
            proximityRanger?.recordPeerSignal(packet.originDeviceName, packet.originDeviceId, packet.signalMetrics.rssi)
        }
        _incomingSos.value = packet
        _relayerGlowEvent.tryEmit(packet)
        triggerEmergencyVibration()

        val updatedHopCount = packet.hopCount + 1
        val updatedTtl = packet.ttl - 1
        val updatedPath = packet.path + myDeviceName
        val hasInternet = hardwareMonitor.isConnectedToInternet()

        EchoMeshService.updateNotificationForRelay(context, packet.originDeviceName, updatedHopCount)

        // 5. Send immediate Reverse ACK to origin along reverse path
        val relayAck = AckPacket(
            messageId = packet.messageId,
            ackType = if (hasInternet) AckType.BRIDGE_FOUND else AckType.RELAY_HOP,
            fromDevice = if (hasInternet) "$myDeviceName (Internet Bridge)" else myDeviceName,
            hopCount = updatedHopCount,
            path = updatedPath,
            timestamp = System.currentTimeMillis()
        )
        broadcastAck(relayAck)

        val relayedPacket = packet.copy(
            hopCount = updatedHopCount,
            ttl = updatedTtl,
            path = updatedPath,
            status = if (hasInternet) "BRIDGE_REACHED" else "HOPPING"
        )

        pendingPackets[packet.messageId] = relayedPacket

        // 6. Save to local repository
        scope.launch(Dispatchers.IO) {
            repository.saveSosPacket(relayedPacket, isMySos = false)
        }

        // 7. If this device is a Bridge device with Internet (Wi-Fi or Cellular), upload to rescue dashboard!
        if (hasInternet) {
            Log.i(TAG, "Backend upload attempted for SOS [${packet.messageId}] via Internet bridge")
            addLog("Internet connection active! Bridging distress signal to Rescue Dashboard...")
            try {
                onBridgeDispatchNeeded?.invoke(relayedPacket)
                Log.i(TAG, "Backend upload successful for SOS [${packet.messageId}]")
                addLog("Backend upload successful for SOS [${packet.messageId.take(8)}]")
                scope.launch(Dispatchers.IO) {
                    repository.markPacketSynced(packet.messageId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Backend upload failed for SOS [${packet.messageId}]: ${e.message}")
            }
            // Distress signal has successfully reached rescue infrastructure; no further offline hopping needed
            return
        }

        // 8. Autonomous Chain Relay: Identify the next closest device ('Phone C') to forward the message
        val currentGps = hardwareMonitor.getLastKnownGpsLocation()
        val currentBattery = hardwareMonitor.getBatteryInfo().percentage
        val candidates = routingEngine.rankCandidatePeers(
            currentGps = currentGps,
            currentBattery = currentBattery,
            packet = relayedPacket,
            availablePeers = connectedEndpoints,
            excludeEndpoint = fromEndpointId
        )

        if (candidates.isEmpty()) {
            Log.i(TAG, "No other candidate peers connected right now. Stored locally for store-and-forward.")
            addLog("No further peers currently connected. Stored locally for store-and-forward.")
            return
        }

        val bestCandidate = candidates.first()
        Log.i(TAG, "SOS relayed [${relayedPacket.messageId}] -> Next closest peer ${bestCandidate.peerName} (${bestCandidate.reason}) [Hop $updatedHopCount, TTL $updatedTtl]")
        addLog("Chain relay: Forwarding to next hop ${bestCandidate.peerName} (${bestCandidate.reason})")

        val forwardedMsg = MeshMessage(type = "SOS", sosPacket = relayedPacket)
        val forwardedBytes = forwardedMsg.toJson().toByteArray(StandardCharsets.UTF_8)

        // Transmit to primary next-hop peer (Phone C)
        transmitPacketToEndpoint(bestCandidate.endpointId, bestCandidate.peerName, forwardedBytes, relayedPacket)

        // If an Internet Bridge is available among candidates and is distinct from bestCandidate, prioritize sending to it!
        val bridgeCandidate = candidates.firstOrNull { it.hasInternet && it.endpointId != bestCandidate.endpointId }
        if (bridgeCandidate != null) {
            Log.i(TAG, "Priority routing: Relaying directly to discovered Internet Bridge ${bridgeCandidate.peerName}")
            addLog("Routing directly to Internet Bridge ${bridgeCandidate.peerName}!")
            transmitPacketToEndpoint(bridgeCandidate.endpointId, bridgeCandidate.peerName, forwardedBytes, relayedPacket)
        }

        // Reliability Fallback: If bestCandidate fails to acknowledge or disconnects within 3000ms, forward to next candidate
        if (candidates.size > 1) {
            scope.launch(Dispatchers.IO) {
                delay(3000)
                val ackReceived = processedAckIds.any { it.startsWith(packet.messageId) && !it.contains(myDeviceName) }
                if (!ackReceived && _isMeshActive.value) {
                    val fallbackCandidate = candidates.getOrNull(1)
                    if (fallbackCandidate != null && connectedEndpoints.containsKey(fallbackCandidate.endpointId)) {
                        Log.w(TAG, "Reliability fallback on relay: Forwarding to alternative peer ${fallbackCandidate.peerName}")
                        addLog("Chain fallback: Forwarding to ${fallbackCandidate.peerName}...")
                        transmitPacketToEndpoint(fallbackCandidate.endpointId, fallbackCandidate.peerName, forwardedBytes, relayedPacket)
                    }
                }
            }
        }
    }

    private fun transmitPacketToEndpoint(endpointId: String, peerName: String, data: ByteArray, sosPacket: SosPacket) {
        try {
            connectionsClient.sendPayload(endpointId, Payload.fromBytes(data))
                .addOnSuccessListener {
                    Log.i(TAG, "SOS sent [${sosPacket.messageId}] to $peerName ($endpointId)")
                    scope.launch(Dispatchers.IO) {
                        repository.markSentToPeer(sosPacket.messageId, peerName)
                    }
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Failed sending SOS to $peerName: ${e.message}")
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed payload send to $endpointId: ${e.message}")
        }
    }

    private fun sendRoutingInfoToPeer(endpointId: String) {
        val gps = hardwareMonitor.getLastKnownGpsLocation()
        val battery = hardwareMonitor.getBatteryInfo().percentage
        val hasNet = hardwareMonitor.isConnectedToInternet()
        val hopsToBridge = if (hasNet) 0 else {
            val minKnown = connectedEndpoints.keys
                .filter { it != endpointId }
                .mapNotNull { routingEngine.getPeerInfo(it)?.hopsToBridge }
                .filter { it < 99 }
                .minOrNull() ?: 98
            (minKnown + 1).coerceAtMost(99)
        }

        val info = RoutingNodeInfo(
            deviceId = myDeviceId,
            deviceName = myDeviceName,
            hasInternet = hasNet,
            latitude = if (gps.hasLocation) gps.latitude else 0.0,
            longitude = if (gps.hasLocation) gps.longitude else 0.0,
            batteryLevel = battery,
            hopsToBridge = hopsToBridge
        )

        val msg = MeshMessage(type = "ROUTING_INFO", routingInfo = info)
        val bytes = msg.toJson().toByteArray(StandardCharsets.UTF_8)
        try {
            connectionsClient.sendPayload(endpointId, Payload.fromBytes(bytes))
                .addOnSuccessListener {
                    Log.d(TAG, "Sent routing info to $endpointId")
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Failed sending routing info to $endpointId: ${e.message}")
                }
        } catch (e: Exception) {
            Log.w(TAG, "Failed payload send to $endpointId: ${e.message}")
        }
    }

    private fun broadcastRoutingInfo() {
        for (endpointId in connectedEndpoints.keys) {
            sendRoutingInfoToPeer(endpointId)
        }
    }

    private fun handleIncomingAck(ack: AckPacket, fromEndpointId: String? = null) {
        val ackKey = "${ack.messageId}_${ack.ackType}_${ack.fromDevice}_${ack.hopCount}"
        if (!processedAckIds.add(ackKey)) return

        Log.i(TAG, "ACK received [${ack.ackType}] from ${ack.fromDevice} for SOS ${ack.messageId.take(8)}")
        addLog("ACK received [${ack.ackType}] from ${ack.fromDevice}")

        // Update active origin SOS status if this is my SOS
        _activeOriginSos.value?.let { active ->
            if (active.messageId == ack.messageId) {
                val newStatus = when (ack.ackType) {
                    AckType.RELAY_HOP -> "Relay acknowledged by ${ack.fromDevice} (Hop ${ack.hopCount})"
                    AckType.BRIDGE_FOUND -> "Bridge reached with Internet via ${ack.fromDevice}"
                    AckType.DISPATCH_CONFIRMED -> "Rescue Dispatch confirmed by ${ack.fromDevice}"
                }
                _activeOriginSos.value = active.copy(
                    status = newStatus,
                    hopCount = maxOf(active.hopCount, ack.hopCount),
                    path = ack.path
                )
            }
        }

        scope.launch(Dispatchers.IO) {
            repository.recordAck(ack)
        }

        // Propagate ACK across mesh so original sender receives it
        val ackMsg = MeshMessage(type = "ACK", ackPacket = ack)
        val ackBytes = ackMsg.toJson().toByteArray(StandardCharsets.UTF_8)
        broadcastBytes(ackBytes, excludeEndpoint = fromEndpointId, sosPacket = null)
    }

    /**
     * Monitors Internet connectivity changes.
     * When Internet becomes available, broadcasts updated routing info and uploads all unsynced SOS messages to backend bridge.
     */
    private fun startInternetBridgeMonitor() {
        scope.launch(Dispatchers.IO) {
            hardwareMonitor.internetConnectionFlow().collect { hasInternet ->
                Log.i(TAG, "Internet connectivity status changed: hasInternet=$hasInternet")
                broadcastRoutingInfo()
                if (hasInternet) {
                    syncStoredPacketsToBackend()
                }
            }
        }
    }

    suspend fun syncStoredPacketsToBackend() {
        val unsynced = repository.getUnsyncedPackets()
        if (unsynced.isEmpty()) return

        Log.i(TAG, "Internet connectivity detected. Syncing ${unsynced.size} stored SOS packet(s) to backend...")
        for (packet in unsynced) {
            Log.i(TAG, "Backend upload attempted for SOS [${packet.messageId}] via Internet bridge")
            try {
                onBridgeDispatchNeeded?.invoke(packet)
                repository.markPacketSynced(packet.messageId)
                Log.i(TAG, "Backend upload successful for SOS [${packet.messageId}]")
                addLog("Backend upload successful for SOS [${packet.messageId.take(8)}]")

                val bridgeAck = AckPacket(
                    messageId = packet.messageId,
                    ackType = AckType.BRIDGE_FOUND,
                    fromDevice = "$myDeviceName (Internet Bridge)",
                    hopCount = packet.hopCount + 1,
                    path = packet.path + "$myDeviceName (Internet Bridge)",
                    timestamp = System.currentTimeMillis()
                )
                broadcastAck(bridgeAck)
            } catch (e: Exception) {
                Log.e(TAG, "Backend upload failed for SOS [${packet.messageId}]: ${e.message}")
            }
        }
    }

    private fun triggerEmergencyVibration() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vibrator = vibratorManager?.defaultVibrator
                // Distinct emergency SOS pattern: 3 short, 3 long, 3 short
                val timings = longArrayOf(0, 150, 100, 150, 100, 150, 200, 400, 100, 400, 100, 400, 200, 150, 100, 150, 100, 150)
                val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255)
                val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
                vibrator?.vibrate(effect)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                val timings = longArrayOf(0, 150, 100, 150, 100, 150, 200, 400, 100, 400, 100, 400, 200, 150, 100, 150, 100, 150)
                val effect = VibrationEffect.createWaveform(timings, -1)
                vibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 150, 100, 150, 100, 150, 200, 400, 100, 400, 100, 400, 200, 150, 100, 150, 100, 150), -1)
            }
            Log.i(TAG, "Emergency SOS vibration triggered on relayed device")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to vibrate device: ${e.message}")
        }
    }
}
