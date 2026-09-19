package com.example.data

import com.example.model.AckPacket
import com.example.model.AckType
import com.example.model.SosPacket
import com.example.model.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EchoRepository(
    private val sosDao: SosDao,
    private val userProfileDao: UserProfileDao,
    private val meshMessageDao: MeshMessageDao? = null,
    private val offlineSosLogDao: OfflineSosLogDao? = null
) {
    val allSosHistory: Flow<List<SosEntity>> = sosDao.getAllSos()
    val mySosHistory: Flow<List<SosEntity>> = sosDao.getMySos()
    val cachedMeshMessages: Flow<List<MeshMessageEntity>> = meshMessageDao?.getRecentMessages(50) ?: kotlinx.coroutines.flow.flowOf(emptyList())
    val offlineSosLogs: Flow<List<OfflineSosLogEntity>> = offlineSosLogDao?.getRecentLogs(50) ?: kotlinx.coroutines.flow.flowOf(emptyList())

    val userProfile: Flow<UserProfile> = userProfileDao.getProfile().map { entity ->
        if (entity != null && entity.name.isNotBlank()) {
            UserProfile(
                name = entity.name,
                phone = entity.phone,
                bloodType = entity.bloodType,
                allergies = entity.allergies,
                medicalIssues = entity.medicalIssues,
                handicap = entity.handicap,
                emergencyNotes = entity.emergencyNotes,
                role = try { com.example.model.UserRole.valueOf(entity.role) } catch (e: Exception) { com.example.model.UserRole.CIVILIAN },
                authorityId = entity.authorityId,
                agency = entity.agency,
                isConfigured = true,
                isLoggedIn = entity.isLoggedIn
            )
        } else {
            UserProfile(isConfigured = false, isLoggedIn = false)
        }
    }

    suspend fun getUserProfileSync(): UserProfile = withContext(Dispatchers.IO) {
        val entity = userProfileDao.getProfileSync()
        if (entity != null && entity.name.isNotBlank()) {
            UserProfile(
                name = entity.name,
                phone = entity.phone,
                bloodType = entity.bloodType,
                allergies = entity.allergies,
                medicalIssues = entity.medicalIssues,
                handicap = entity.handicap,
                emergencyNotes = entity.emergencyNotes,
                role = try { com.example.model.UserRole.valueOf(entity.role) } catch (e: Exception) { com.example.model.UserRole.CIVILIAN },
                authorityId = entity.authorityId,
                agency = entity.agency,
                isConfigured = true,
                isLoggedIn = entity.isLoggedIn
            )
        } else {
            UserProfile(isConfigured = false, isLoggedIn = false)
        }
    }

    suspend fun saveUserProfile(profile: UserProfile) = withContext(Dispatchers.IO) {
        userProfileDao.insertProfile(
            UserProfileEntity(
                name = profile.name.trim(),
                phone = profile.phone.trim(),
                bloodType = profile.bloodType.trim(),
                allergies = profile.allergies.trim(),
                medicalIssues = profile.medicalIssues.trim(),
                handicap = profile.handicap.trim(),
                emergencyNotes = profile.emergencyNotes.trim(),
                role = profile.role.name,
                authorityId = profile.authorityId.trim(),
                agency = profile.agency.trim(),
                isLoggedIn = profile.isLoggedIn
            )
        )
    }

    suspend fun saveSosPacket(packet: SosPacket, isMySos: Boolean): Long = withContext(Dispatchers.IO) {
        val existing = sosDao.getSosByMessageId(packet.messageId)
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(packet.timestamp))
        val initialLog = if (isMySos) {
            "[$timeStr] SOS Created by origin node ${packet.originDeviceName}"
        } else {
            "[$timeStr] Received and relaying distress signal from ${packet.originDeviceName} (Hop ${packet.hopCount})"
        }

        val journeyLogs = if (existing != null) {
            parseJourneyLogs(existing.journeyLogJson)
        } else {
            mutableListOf(initialLog)
        }

        val entity = SosEntity(
            id = existing?.id ?: 0,
            messageId = packet.messageId,
            originDeviceId = packet.originDeviceId,
            originDeviceName = packet.originDeviceName,
            timestamp = packet.timestamp,
            latitude = packet.latitude,
            longitude = packet.longitude,
            accuracy = packet.accuracy,
            locationStatus = packet.locationStatus,
            emergencyType = packet.emergencyType,
            priority = packet.priority,
            hopCount = packet.hopCount,
            ttl = packet.ttl,
            pathJson = JSONArray(packet.path).toString(),
            status = packet.status,
            batteryLevel = packet.signalMetrics.batteryLevel,
            isCharging = packet.signalMetrics.isCharging,
            rssi = packet.signalMetrics.rssi,
            networkType = packet.signalMetrics.networkType,
            encryptedPayloadJson = packet.encryptedPayload?.toJsonObject()?.toString() ?: "{}",
            journeyLogJson = JSONArray(journeyLogs).toString(),
            isMySos = isMySos,
            isSyncedToBackend = existing?.isSyncedToBackend ?: false,
            sentToPeersJson = existing?.sentToPeersJson ?: "[]"
        )
        val rowId = sosDao.insertSos(entity)

        // Cache into Room Mesh Messages & Offline Logs tables for completely offline operation
        meshMessageDao?.insertMessage(
            MeshMessageEntity(
                messageId = packet.messageId,
                senderDeviceId = packet.originDeviceId,
                senderDeviceName = packet.originDeviceName,
                messageType = if (isMySos) "SOS_ORIGIN" else "SOS_RELAY_HOP",
                content = "${packet.emergencyType} distress from ${packet.originDeviceName} • Hop ${packet.hopCount}",
                emergencyType = packet.emergencyType,
                hopCount = packet.hopCount,
                timestamp = packet.timestamp,
                rssi = packet.signalMetrics.rssi,
                isRelayed = !isMySos,
                isOfflineCached = true
            )
        )

        offlineSosLogDao?.insertLog(
            OfflineSosLogEntity(
                messageId = packet.messageId,
                eventType = if (isMySos) "SOS_CREATED" else "HOP_RELAYED",
                logMessage = initialLog,
                deviceName = packet.originDeviceName,
                hopIndex = packet.hopCount,
                timestamp = packet.timestamp,
                isOfflineAvailable = true
            )
        )

        rowId
    }

    suspend fun getActivePacketsForRelay(): List<SosPacket> = withContext(Dispatchers.IO) {
        sosDao.getActivePacketsForRelay().map { entityToPacket(it) }
    }

    suspend fun getUnsyncedPackets(): List<SosPacket> = withContext(Dispatchers.IO) {
        sosDao.getUnsyncedSos().map { entityToPacket(it) }
    }

    suspend fun markPacketSynced(messageId: String) = withContext(Dispatchers.IO) {
        sosDao.markSynced(messageId)
    }

    suspend fun markSentToPeer(messageId: String, peerIdentifier: String) = withContext(Dispatchers.IO) {
        val existing = sosDao.getSosByMessageId(messageId) ?: return@withContext
        val peers = mutableListOf<String>()
        try {
            val arr = JSONArray(existing.sentToPeersJson)
            for (i in 0 until arr.length()) peers.add(arr.getString(i))
        } catch (_: Exception) {}
        if (!peers.contains(peerIdentifier)) {
            peers.add(peerIdentifier)
            sosDao.updateSos(existing.copy(sentToPeersJson = JSONArray(peers).toString()))
        }
    }

    fun entityToPacket(entity: SosEntity): SosPacket {
        val pathList = mutableListOf<String>()
        try {
            val arr = JSONArray(entity.pathJson)
            for (i in 0 until arr.length()) pathList.add(arr.getString(i))
        } catch (_: Exception) {}

        val encPayload = try {
            val obj = org.json.JSONObject(entity.encryptedPayloadJson)
            if (obj.has("cipherText")) com.example.model.EncryptedPayload.fromJsonObject(obj) else null
        } catch (_: Exception) {
            null
        }

        return SosPacket(
            messageId = entity.messageId,
            originDeviceId = entity.originDeviceId,
            originDeviceName = entity.originDeviceName,
            timestamp = entity.timestamp,
            latitude = entity.latitude,
            longitude = entity.longitude,
            accuracy = entity.accuracy,
            locationStatus = entity.locationStatus,
            emergencyType = entity.emergencyType,
            priority = entity.priority,
            hopCount = entity.hopCount,
            ttl = entity.ttl,
            status = entity.status,
            path = pathList,
            signalMetrics = com.example.model.SignalMetrics(
                batteryLevel = entity.batteryLevel,
                isCharging = entity.isCharging,
                rssi = entity.rssi,
                networkType = entity.networkType
            ),
            encryptedPayload = encPayload
        )
    }

    suspend fun recordAck(ack: AckPacket): Boolean = withContext(Dispatchers.IO) {
        val existing = sosDao.getSosByMessageId(ack.messageId) ?: return@withContext false
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ack.timestamp))
        val logs = parseJourneyLogs(existing.journeyLogJson)

        val logEntry = when (ack.ackType) {
            AckType.RELAY_HOP -> "[$timeStr] Sent to ${ack.fromDevice} • Relayed by ${ack.fromDevice} (Hop ${ack.hopCount})"
            AckType.BRIDGE_FOUND -> "[$timeStr] Sent to ${ack.fromDevice} • Bridge reached with Internet uplink"
            AckType.DISPATCH_CONFIRMED -> "[$timeStr] Rescue Dispatch Acknowledged by ${ack.fromDevice}: ${ack.dispatchNotes}"
        }
        logs.add(logEntry)

        val newStatus = when (ack.ackType) {
            AckType.RELAY_HOP -> if (existing.status == "BROADCASTING") "HOPPING" else existing.status
            AckType.BRIDGE_FOUND -> "BRIDGE_REACHED"
            AckType.DISPATCH_CONFIRMED -> "DISPATCH_ACKNOWLEDGED"
        }

        val updatedEntity = existing.copy(
            status = newStatus,
            hopCount = maxOf(existing.hopCount, ack.hopCount),
            journeyLogJson = JSONArray(logs).toString(),
            dispatchAcknowledgedTime = if (ack.ackType == AckType.DISPATCH_CONFIRMED) ack.timestamp else existing.dispatchAcknowledgedTime
        )
        sosDao.updateSos(updatedEntity)

        // Cache reverse acknowledgment in Room DB for offline review
        meshMessageDao?.insertMessage(
            MeshMessageEntity(
                messageId = ack.messageId,
                senderDeviceId = ack.fromDevice,
                senderDeviceName = ack.fromDevice,
                messageType = "REVERSE_ACK",
                content = "${ack.ackType.name} from ${ack.fromDevice} • Hop ${ack.hopCount}",
                emergencyType = existing.emergencyType,
                hopCount = ack.hopCount,
                timestamp = ack.timestamp,
                rssi = -50,
                isRelayed = true,
                isOfflineCached = true
            )
        )

        offlineSosLogDao?.insertLog(
            OfflineSosLogEntity(
                messageId = ack.messageId,
                eventType = "REVERSE_ACK",
                logMessage = logEntry,
                deviceName = ack.fromDevice,
                hopIndex = ack.hopCount,
                timestamp = ack.timestamp,
                isOfflineAvailable = true
            )
        )

        true
    }

    suspend fun cacheMeshMessage(
        messageId: String,
        senderDeviceId: String,
        senderDeviceName: String,
        messageType: String,
        content: String,
        emergencyType: String = "GENERAL",
        hopCount: Int = 0,
        rssi: Int = -60,
        isRelayed: Boolean = false
    ) = withContext(Dispatchers.IO) {
        meshMessageDao?.insertMessage(
            MeshMessageEntity(
                messageId = messageId,
                senderDeviceId = senderDeviceId,
                senderDeviceName = senderDeviceName,
                messageType = messageType,
                content = content,
                emergencyType = emergencyType,
                hopCount = hopCount,
                timestamp = System.currentTimeMillis(),
                rssi = rssi,
                isRelayed = isRelayed,
                isOfflineCached = true
            )
        )
    }

    suspend fun logOfflineEvent(
        messageId: String,
        eventType: String,
        logMessage: String,
        deviceName: String = "",
        hopIndex: Int = 0
    ) = withContext(Dispatchers.IO) {
        offlineSosLogDao?.insertLog(
            OfflineSosLogEntity(
                messageId = messageId,
                eventType = eventType,
                logMessage = logMessage,
                deviceName = deviceName,
                hopIndex = hopIndex,
                timestamp = System.currentTimeMillis(),
                isOfflineAvailable = true
            )
        )
    }

    suspend fun getSos(messageId: String): SosEntity? = withContext(Dispatchers.IO) {
        sosDao.getSosByMessageId(messageId)
    }

    private fun parseJourneyLogs(jsonStr: String): MutableList<String> {
        val list = mutableListOf<String>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                list.add(arr.getString(i))
            }
        } catch (e: Exception) {
            if (jsonStr.isNotBlank()) list.add(jsonStr)
        }
        return list
    }
}
