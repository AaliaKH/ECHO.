package com.example.mesh

import android.util.Log
import com.example.model.RoutingNodeInfo
import com.example.model.SosPacket
import com.example.util.RealGpsInfo
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class ScoredPeer(
    val endpointId: String,
    val peerName: String,
    val remoteDeviceId: String,
    val hasInternet: Boolean,
    val distanceMeters: Double,
    val hopsToBridge: Int,
    val score: Double,
    val reason: String
)

/**
 * Core Routing Engine for the ECHO mesh network.
 *
 * Implements autonomous chain-routing from origin Phone A -> nearest available Phone B ->
 * next closest Phone C -> Bridge Device with Internet.
 *
 * Prioritizes efficiency and reliability:
 * 1. Absolute priority is given to Bridge Devices with verified Internet (Cellular/Wi-Fi).
 * 2. Intermediate relays are chosen based on physical proximity (nearest available peer),
 *    device battery health, and reported hops to an internet bridge.
 * 3. Strict loop-prevention ensures messages never loop back to visited nodes.
 */
class MeshRoutingEngine {

    companion object {
        private const val TAG = "ECHO_MESH"
        const val MAX_DISTANCE_THRESHOLD_METERS = 5000.0
    }

    // EndpointId -> RoutingNodeInfo
    private val peerTable = ConcurrentHashMap<String, RoutingNodeInfo>()

    fun updatePeerInfo(endpointId: String, info: RoutingNodeInfo) {
        peerTable[endpointId] = info
        Log.d(TAG, "Routing table updated for $endpointId (${info.deviceName}): Net=${info.hasInternet}, Bat=${info.batteryLevel}%, HopsToBridge=${info.hopsToBridge}")
    }

    fun removePeer(endpointId: String) {
        peerTable.remove(endpointId)
    }

    fun getPeerInfo(endpointId: String): RoutingNodeInfo? = peerTable[endpointId]

    /**
     * Determines whether any connected or known peer currently possesses Internet access.
     */
    fun findConnectedBridgeEndpoint(availableEndpoints: Set<String>): String? {
        return availableEndpoints.firstOrNull { endpointId ->
            peerTable[endpointId]?.hasInternet == true
        }
    }

    /**
     * Evaluates all connected candidate peers for a given SOS packet,
     * sorting them from best to worst next-hop candidate.
     */
    fun rankCandidatePeers(
        currentGps: RealGpsInfo?,
        currentBattery: Int,
        packet: SosPacket,
        availablePeers: Map<String, String>, // endpointId -> peerName
        excludeEndpoint: String? = null
    ): List<ScoredPeer> {
        val candidates = mutableListOf<ScoredPeer>()

        for ((endpointId, peerName) in availablePeers) {
            // 1. Exclude the endpoint this packet just arrived from
            if (endpointId == excludeEndpoint) {
                Log.v(TAG, "Routing: Skipped $peerName ($endpointId) - source endpoint")
                continue
            }

            val peerInfo = peerTable[endpointId]
            val remoteDeviceId = peerInfo?.deviceId ?: ""

            // 2. Loop Prevention: Do not forward back to origin device
            if (remoteDeviceId.isNotEmpty() && remoteDeviceId == packet.originDeviceId) {
                Log.d(TAG, "Routing: Skipped $peerName - origin device")
                continue
            }

            // 3. Loop Prevention: Do not forward to any device already in the path
            val isAlreadyInPath = packet.path.any { visitedName ->
                visitedName.equals(peerName, ignoreCase = true) ||
                (remoteDeviceId.isNotEmpty() && visitedName.contains(remoteDeviceId, ignoreCase = true)) ||
                visitedName.contains(peerName, ignoreCase = true)
            }
            if (isAlreadyInPath) {
                Log.d(TAG, "Routing: Skipped $peerName - already in path [${packet.path.joinToString(" -> ")}]")
                continue
            }

            // 4. Calculate Distance (Proximity) using Haversine formula
            val distance = if (currentGps != null && currentGps.hasLocation && peerInfo != null && peerInfo.latitude != 0.0 && peerInfo.longitude != 0.0) {
                calculateDistanceMeters(currentGps.latitude, currentGps.longitude, peerInfo.latitude, peerInfo.longitude)
            } else {
                Double.MAX_VALUE
            }

            val hasInternet = peerInfo?.hasInternet ?: false
            val hopsToBridge = peerInfo?.hopsToBridge ?: (if (hasInternet) 0 else 99)
            val battery = peerInfo?.batteryLevel ?: 80

            // 5. Calculate Routing Score
            var score = 0.0
            val reason: String

            if (hasInternet) {
                // Tier 1: Direct Internet Bridge (Objective Target!)
                score = 1_000_000.0 + (if (distance != Double.MAX_VALUE) (1000.0 - distance.coerceAtMost(1000.0)) else 500.0)
                reason = "DIRECT_INTERNET_BRIDGE"
            } else if (hopsToBridge < 99) {
                // Tier 2: Path leading to Bridge
                val hopBonus = (10 - hopsToBridge).coerceAtLeast(1) * 20_000.0
                val distBonus = if (distance != Double.MAX_VALUE) (5000.0 - distance.coerceAtMost(5000.0)) else 0.0
                score = 100_000.0 + hopBonus + distBonus + (battery * 10.0)
                reason = "BRIDGE_PROXIMITY_HOP_${hopsToBridge}"
            } else {
                // Tier 3: Nearest Available Relay Peer
                val baseRelayScore = 10_000.0
                val proximityScore = if (distance != Double.MAX_VALUE) {
                    (MAX_DISTANCE_THRESHOLD_METERS - distance.coerceAtMost(MAX_DISTANCE_THRESHOLD_METERS))
                } else {
                    2000.0 // Default score if GPS coordinates are not yet available
                }
                val batteryScore = battery * 15.0 // Prefer devices with healthier battery
                score = baseRelayScore + proximityScore + batteryScore

                reason = if (distance != Double.MAX_VALUE) {
                    "NEAREST_RELAY_${distance.toInt()}M"
                } else {
                    "NEXT_AVAILABLE_RELAY"
                }
            }

            candidates.add(
                ScoredPeer(
                    endpointId = endpointId,
                    peerName = peerName,
                    remoteDeviceId = remoteDeviceId,
                    hasInternet = hasInternet,
                    distanceMeters = distance,
                    hopsToBridge = hopsToBridge,
                    score = score,
                    reason = reason
                )
            )
        }

        // Sort descending by score (highest scoring peer first)
        candidates.sortByDescending { it.score }

        if (candidates.isNotEmpty()) {
            val summary = candidates.joinToString(", ") { "${it.peerName} (${it.reason}, score=${it.score.toInt()})" }
            Log.i(TAG, "Routing: Evaluated ${candidates.size} candidate peer(s): $summary")
        } else {
            Log.w(TAG, "Routing: No candidate peers available (all peers visited or excluded)")
        }

        return candidates
    }

    /**
     * Selects the single best next hop peer for directed chain relaying.
     */
    fun selectBestNextHop(
        currentGps: RealGpsInfo?,
        currentBattery: Int,
        packet: SosPacket,
        availablePeers: Map<String, String>,
        excludeEndpoint: String? = null
    ): ScoredPeer? {
        return rankCandidatePeers(currentGps, currentBattery, packet, availablePeers, excludeEndpoint).firstOrNull()
    }

    /**
     * Calculates the great-circle distance between two points on the Earth's surface
     * using the Haversine formula in meters.
     */
    fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        if ((lat1 == 0.0 && lon1 == 0.0) || (lat2 == 0.0 && lon2 == 0.0)) {
            return Double.MAX_VALUE
        }
        val r = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
