package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sos_history",
    indices = [Index(value = ["messageId"], unique = true)]
)
data class SosEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val messageId: String,
    val originDeviceId: String,
    val originDeviceName: String,
    val timestamp: Long,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracy: Float = 0.0f,
    val locationStatus: String = "AVAILABLE",
    val emergencyType: String = "SOS",
    val priority: String = "CRITICAL",
    val hopCount: Int = 0,
    val ttl: Int = 10,
    val pathJson: String, // serialized JSON list of device names
    val status: String,
    val batteryLevel: Int,
    val isCharging: Boolean,
    val rssi: Int,
    val networkType: String,
    val encryptedPayloadJson: String,
    val journeyLogJson: String, // JSON array of journey updates
    val isMySos: Boolean,
    val isSyncedToBackend: Boolean = false,
    val sentToPeersJson: String = "[]",
    val dispatchAcknowledgedTime: Long = 0L
)

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 1,
    val name: String,
    val phone: String = "",
    val bloodType: String = "",
    val allergies: String = "",
    val medicalIssues: String = "",
    val handicap: String = "",
    val emergencyNotes: String = "",
    val role: String = "CIVILIAN",
    val authorityId: String = "",
    val agency: String = "",
    val isLoggedIn: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Room Entity caching mesh network messages, pings, and hop relays
 * for offline persistence when internet and WiFi are unavailable.
 */
@Entity(
    tableName = "mesh_messages",
    indices = [Index(value = ["messageId"])]
)
data class MeshMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messageId: String,
    val senderDeviceId: String,
    val senderDeviceName: String,
    val messageType: String, // "SOS_DISTRESS", "RELAY_HOP", "REVERSE_ACK", "PEER_DISCOVERY"
    val content: String,
    val emergencyType: String = "GENERAL",
    val hopCount: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val rssi: Int = -60,
    val isRelayed: Boolean = false,
    val isOfflineCached: Boolean = true
)

/**
 * Room Entity caching detailed chronological SOS activity logs for
 * full diagnostic review during total network outages.
 */
@Entity(
    tableName = "offline_sos_logs",
    indices = [Index(value = ["messageId"])]
)
data class OfflineSosLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messageId: String,
    val eventType: String, // "SOS_TRIGGERED", "HOP_RELAYED", "PEER_CONNECTED", "REVERSE_ACK", "ROOM_CACHE_STORED"
    val logMessage: String,
    val deviceName: String = "",
    val hopIndex: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val isOfflineAvailable: Boolean = true
)
