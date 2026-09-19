package com.example.model

import org.json.JSONArray
import org.json.JSONObject

enum class UserRole {
    CIVILIAN,
    AUTHORITY
}

data class UserProfile(
    val name: String = "",
    val phone: String = "",
    val bloodType: String = "",
    val allergies: String = "",
    val medicalIssues: String = "",
    val handicap: String = "",
    val emergencyNotes: String = "",
    val role: UserRole = UserRole.CIVILIAN,
    val authorityId: String = "",
    val agency: String = "",
    val isConfigured: Boolean = false,
    val isLoggedIn: Boolean = false
)

data class SignalMetrics(
    val batteryLevel: Int = 0,
    val isCharging: Boolean = false,
    val rssi: Int = -60, // dBm approximation or reported signal metric
    val networkType: String = "P2P_MESH", // P2P_MESH, BLUETOOTH, WIFI_DIRECT, INTERNET_BRIDGE
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("batteryLevel", batteryLevel)
        put("isCharging", isCharging)
        put("rssi", rssi)
        put("networkType", networkType)
        put("timestamp", timestamp)
    }

    companion object {
        fun fromJsonObject(json: JSONObject): SignalMetrics {
            return SignalMetrics(
                batteryLevel = json.optInt("batteryLevel", 0),
                isCharging = json.optBoolean("isCharging", false),
                rssi = json.optInt("rssi", -60),
                networkType = json.optString("networkType", "P2P_MESH"),
                timestamp = json.optLong("timestamp", System.currentTimeMillis())
            )
        }
    }
}

data class EncryptedPayload(
    val ciphertextBase64: String,
    val ivBase64: String,
    val encryptedKeyBase64: String
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("ciphertextBase64", ciphertextBase64)
        put("ivBase64", ivBase64)
        put("encryptedKeyBase64", encryptedKeyBase64)
    }

    companion object {
        fun fromJsonObject(json: JSONObject): EncryptedPayload {
            return EncryptedPayload(
                ciphertextBase64 = json.getString("ciphertextBase64"),
                ivBase64 = json.getString("ivBase64"),
                encryptedKeyBase64 = json.getString("encryptedKeyBase64")
            )
        }
    }
}

data class DecryptedSosData(
    val victimName: String,
    val phone: String = "",
    val bloodType: String,
    val allergies: String = "",
    val medicalIssues: String,
    val handicap: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val accuracy: Float,
    val batteryLevel: Int,
    val emergencyNotes: String,
    val timestamp: Long,
    val locationStatus: String = "FRESH"
)

enum class AckType {
    RELAY_HOP,
    BRIDGE_FOUND,
    DISPATCH_CONFIRMED
}

data class AckPacket(
    val messageId: String,
    val ackType: AckType,
    val fromDevice: String,
    val hopCount: Int,
    val path: List<String>,
    val timestamp: Long = System.currentTimeMillis(),
    val dispatchNotes: String = ""
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("messageId", messageId)
        put("ackType", ackType.name)
        put("fromDevice", fromDevice)
        put("hopCount", hopCount)
        val pathArr = JSONArray()
        path.forEach { pathArr.put(it) }
        put("path", pathArr)
        put("timestamp", timestamp)
        put("dispatchNotes", dispatchNotes)
    }

    companion object {
        fun fromJsonObject(json: JSONObject): AckPacket {
            val pathList = mutableListOf<String>()
            val pathArr = json.optJSONArray("path")
            if (pathArr != null) {
                for (i in 0 until pathArr.length()) {
                    pathList.add(pathArr.getString(i))
                }
            }
            return AckPacket(
                messageId = json.getString("messageId"),
                ackType = AckType.valueOf(json.optString("ackType", AckType.RELAY_HOP.name)),
                fromDevice = json.getString("fromDevice"),
                hopCount = json.optInt("hopCount", 1),
                path = pathList,
                timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                dispatchNotes = json.optString("dispatchNotes", "")
            )
        }
    }
}

data class SosPacket(
    val messageId: String,
    val originDeviceId: String,
    val originDeviceName: String,
    val timestamp: Long,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracy: Float = 0.0f,
    val locationStatus: String = "AVAILABLE", // FRESH, STALE, UNAVAILABLE
    val emergencyType: String = "SOS",
    val priority: String = "CRITICAL",
    val hopCount: Int = 0,
    val ttl: Int = 10,
    val status: String = "BROADCASTING", // BROADCASTING, HOPPING, BRIDGE_REACHED, DISPATCH_ACKNOWLEDGED
    val path: List<String> = emptyList(),
    val signalMetrics: SignalMetrics = SignalMetrics(),
    val encryptedPayload: EncryptedPayload? = null
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("messageId", messageId)
        put("originDeviceId", originDeviceId)
        put("originDeviceName", originDeviceName)
        put("timestamp", timestamp)
        put("latitude", latitude)
        put("longitude", longitude)
        put("accuracy", accuracy.toDouble())
        put("locationStatus", locationStatus)
        put("emergencyType", emergencyType)
        put("priority", priority)
        put("hopCount", hopCount)
        put("ttl", ttl)
        put("status", status)
        val pathArr = JSONArray()
        path.forEach { pathArr.put(it) }
        put("path", pathArr)
        put("signalMetrics", signalMetrics.toJsonObject())
        encryptedPayload?.let { put("encryptedPayload", it.toJsonObject()) }
    }

    companion object {
        fun fromJsonObject(json: JSONObject): SosPacket {
            val pathList = mutableListOf<String>()
            val pathArr = json.optJSONArray("path")
            if (pathArr != null) {
                for (i in 0 until pathArr.length()) {
                    pathList.add(pathArr.getString(i))
                }
            }
            val lat = json.optDouble("latitude", 0.0)
            val lng = json.optDouble("longitude", 0.0)
            val defaultStatus = if (lat != 0.0 || lng != 0.0) "AVAILABLE" else "UNAVAILABLE"
            return SosPacket(
                messageId = json.getString("messageId"),
                originDeviceId = json.getString("originDeviceId"),
                originDeviceName = json.getString("originDeviceName"),
                timestamp = json.getLong("timestamp"),
                latitude = lat,
                longitude = lng,
                accuracy = json.optDouble("accuracy", 0.0).toFloat(),
                locationStatus = json.optString("locationStatus", defaultStatus),
                emergencyType = json.optString("emergencyType", "SOS"),
                priority = json.optString("priority", "CRITICAL"),
                hopCount = json.optInt("hopCount", 0),
                ttl = json.optInt("ttl", 10),
                status = json.optString("status", "BROADCASTING"),
                path = pathList,
                signalMetrics = if (json.has("signalMetrics")) {
                    SignalMetrics.fromJsonObject(json.getJSONObject("signalMetrics"))
                } else {
                    SignalMetrics()
                },
                encryptedPayload = if (json.has("encryptedPayload")) {
                    EncryptedPayload.fromJsonObject(json.getJSONObject("encryptedPayload"))
                } else null
            )
        }
    }
}

data class RoutingNodeInfo(
    val deviceId: String,
    val deviceName: String,
    val hasInternet: Boolean,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val batteryLevel: Int = 100,
    val hopsToBridge: Int = if (hasInternet) 0 else 99,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("deviceId", deviceId)
        put("deviceName", deviceName)
        put("hasInternet", hasInternet)
        put("latitude", latitude)
        put("longitude", longitude)
        put("batteryLevel", batteryLevel)
        put("hopsToBridge", hopsToBridge)
        put("timestamp", timestamp)
    }

    companion object {
        fun fromJsonObject(json: JSONObject): RoutingNodeInfo {
            return RoutingNodeInfo(
                deviceId = json.optString("deviceId", ""),
                deviceName = json.optString("deviceName", ""),
                hasInternet = json.optBoolean("hasInternet", false),
                latitude = json.optDouble("latitude", 0.0),
                longitude = json.optDouble("longitude", 0.0),
                batteryLevel = json.optInt("batteryLevel", 100),
                hopsToBridge = json.optInt("hopsToBridge", 99),
                timestamp = json.optLong("timestamp", System.currentTimeMillis())
            )
        }
    }
}

data class MeshMessage(
    val type: String, // "SOS", "ACK", "ROUTING_INFO"
    val sosPacket: SosPacket? = null,
    val ackPacket: AckPacket? = null,
    val routingInfo: RoutingNodeInfo? = null
) {
    fun toJson(): String {
        val json = JSONObject()
        json.put("type", type)
        sosPacket?.let { json.put("sosPacket", it.toJsonObject()) }
        ackPacket?.let { json.put("ackPacket", it.toJsonObject()) }
        routingInfo?.let { json.put("routingInfo", it.toJsonObject()) }
        return json.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): MeshMessage? {
            return try {
                val json = JSONObject(jsonStr)
                val type = json.getString("type")
                val sos = if (json.has("sosPacket")) SosPacket.fromJsonObject(json.getJSONObject("sosPacket")) else null
                val ack = if (json.has("ackPacket")) AckPacket.fromJsonObject(json.getJSONObject("ackPacket")) else null
                val routing = if (json.has("routingInfo")) RoutingNodeInfo.fromJsonObject(json.getJSONObject("routingInfo")) else null
                MeshMessage(type = type, sosPacket = sos, ackPacket = ack, routingInfo = routing)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}
