package com.example.bridge

import android.util.Log
import com.example.crypto.EchoCrypto
import com.example.model.AckPacket
import com.example.model.AckType
import com.example.model.DecryptedSosData
import com.example.model.SosPacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class DispatchSosIncident(
    val packet: SosPacket,
    val decryptedData: DecryptedSosData?,
    val receivedTimestamp: Long = System.currentTimeMillis(),
    val isAcknowledged: Boolean = false,
    val dispatchNotes: String = ""
)

/**
 * Handles low-latency streaming between Bridge Devices and the Rescue Team Dispatch Center via WebSockets.
 * Also houses the in-app Dispatch Station so authorities can operate the console directly on device.
 */
class RescueBridgeDispatcher(
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "RescueBridge"
        // Standard WebSocket endpoint for emergency streaming test or customizable endpoint
        const val DEFAULT_WS_URL = "wss://echo.websocket.events"
    }

    private val okHttpClient = OkHttpClient.Builder()
        .readTimeout(10, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    private var activeWebSocket: WebSocket? = null

    private val _isWebSocketConnected = MutableStateFlow(false)
    val isWebSocketConnected: StateFlow<Boolean> = _isWebSocketConnected.asStateFlow()

    // Incident queue for the Rescue Dashboard
    private val _incidents = MutableStateFlow<List<DispatchSosIncident>>(emptyList())
    val incidents: StateFlow<List<DispatchSosIncident>> = _incidents.asStateFlow()

    // Real-time incoming alert event for dashboard audio/visual alarm
    private val _newIncidentAlert = MutableSharedFlow<DispatchSosIncident>(extraBufferCapacity = 5)
    val newIncidentAlert: SharedFlow<DispatchSosIncident> = _newIncidentAlert.asSharedFlow()

    // Outgoing ACK callback to route back into Mesh
    var onDispatchAckGenerated: ((AckPacket) -> Unit)? = null

    init {
        connectWebSocket(DEFAULT_WS_URL)
    }

    fun connectWebSocket(url: String) {
        try {
            val request = Request.Builder().url(url).build()
            activeWebSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.i(TAG, "WebSocket Dispatch Stream Connected: $url")
                    _isWebSocketConnected.value = true
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleWebSocketPayload(text)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    _isWebSocketConnected.value = false
                    Log.i(TAG, "WebSocket Closed: $reason")
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    _isWebSocketConnected.value = false
                    Log.w(TAG, "WebSocket connection failed: ${t.message}")
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize WebSocket: ${e.message}")
            _isWebSocketConnected.value = false
        }
    }

    /**
     * Bridge Device transmits the SOS packet to Rescue Team Dispatch via WebSocket.
     */
    fun transmitSosToRescueDispatch(packet: SosPacket) {
        val payloadJson = JSONObject().apply {
            put("action", "SOS_ALERT")
            put("sosPacket", packet.toJsonObject())
            put("bridgeTimestamp", System.currentTimeMillis())
        }

        val jsonStr = payloadJson.toString()

        // 1. Send via WebSocket if connected
        activeWebSocket?.send(jsonStr)

        // 2. Deliver directly into the local Dispatch Incident registry so the Rescue Dashboard
        // on this or linked device displays it immediately with low latency!
        receiveIncidentInDashboard(packet)
    }

    private fun handleWebSocketPayload(text: String) {
        try {
            val json = JSONObject(text)
            val action = json.optString("action")
            if (action == "SOS_ALERT" && json.has("sosPacket")) {
                val packet = SosPacket.fromJsonObject(json.getJSONObject("sosPacket"))
                receiveIncidentInDashboard(packet)
            } else if (action == "DISPATCH_ACK" && json.has("ackPacket")) {
                val ack = AckPacket.fromJsonObject(json.getJSONObject("ackPacket"))
                onDispatchAckGenerated?.invoke(ack)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling incoming WebSocket payload: ${e.message}")
        }
    }

    fun receiveIncidentInDashboard(packet: SosPacket) {
        // Automatically attempt authority decryption with Rescue Authority Private Key
        val decrypted = EchoCrypto.decryptVictimPayload(packet.encryptedPayload)

        val incident = DispatchSosIncident(
            packet = packet,
            decryptedData = decrypted,
            receivedTimestamp = System.currentTimeMillis()
        )

        val current = _incidents.value.toMutableList()
        val index = current.indexOfFirst { it.packet.messageId == packet.messageId }
        if (index >= 0) {
            current[index] = incident
        } else {
            current.add(0, incident)
        }
        _incidents.value = current
        _newIncidentAlert.tryEmit(incident)
    }

    /**
     * Authority marks incident as dispatched and sends official verification ACK back to victim.
     */
    fun dispatchRescueTeam(messageId: String, dispatchUnit: String, notes: String) {
        val current = _incidents.value.toMutableList()
        val index = current.indexOfFirst { it.packet.messageId == messageId }
        if (index >= 0) {
            val updated = current[index].copy(
                isAcknowledged = true,
                dispatchNotes = "Unit $dispatchUnit deployed: $notes"
            )
            current[index] = updated
            _incidents.value = current

            val ack = AckPacket(
                messageId = messageId,
                ackType = AckType.DISPATCH_CONFIRMED,
                fromDevice = "Rescue Command ($dispatchUnit)",
                hopCount = updated.packet.hopCount,
                path = updated.packet.path + "Rescue Command",
                timestamp = System.currentTimeMillis(),
                dispatchNotes = "Rescue team $dispatchUnit is en route. Stay in place."
            )

            // Send via WebSocket
            val payload = JSONObject().apply {
                put("action", "DISPATCH_ACK")
                put("ackPacket", ack.toJsonObject())
            }
            activeWebSocket?.send(payload.toString())

            // Route back into mesh
            onDispatchAckGenerated?.invoke(ack)
        }
    }
}
