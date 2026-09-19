package com.example.mesh

import com.example.model.RoutingNodeInfo
import com.example.model.SosPacket
import com.example.util.RealGpsInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MeshRoutingEngineTest {

    private val routingEngine = MeshRoutingEngine()

    @Test
    fun testHaversineDistanceCalculation() {
        // Known distance between two points in San Francisco (~1.1 km)
        // Lat: 37.7749, Lon: -122.4194 to Lat: 37.7849, Lon: -122.4194
        val dist = routingEngine.calculateDistanceMeters(37.7749, -122.4194, 37.7849, -122.4194)
        assertTrue("Distance should be approximately 1111 meters", dist in 1100.0..1125.0)
    }

    @Test
    fun testBridgeDevicePrioritizedOverOfflinePeers() {
        val originGps = RealGpsInfo(hasLocation = true, latitude = 37.7749, longitude = -122.4194)
        val packet = SosPacket(
            messageId = "test-sos-1",
            originDeviceId = "dev-phone-a",
            originDeviceName = "ECHO-Phone A",
            timestamp = System.currentTimeMillis(),
            latitude = 37.7749,
            longitude = -122.4194,
            hopCount = 0,
            ttl = 5,
            path = listOf("ECHO-Phone A")
        )

        // Peer 1: Very close offline phone (50m away)
        routingEngine.updatePeerInfo(
            "ep-b",
            RoutingNodeInfo(
                deviceId = "dev-phone-b",
                deviceName = "ECHO-Phone B",
                hasInternet = false,
                latitude = 37.7753,
                longitude = -122.4194,
                batteryLevel = 90,
                hopsToBridge = 99
            )
        )

        // Peer 2: Farther device with active Internet connection (Bridge device, 300m away)
        routingEngine.updatePeerInfo(
            "ep-bridge",
            RoutingNodeInfo(
                deviceId = "dev-phone-bridge",
                deviceName = "ECHO-Bridge",
                hasInternet = true,
                latitude = 37.7770,
                longitude = -122.4194,
                batteryLevel = 80,
                hopsToBridge = 0
            )
        )

        val peers = mapOf("ep-b" to "ECHO-Phone B", "ep-bridge" to "ECHO-Bridge")
        val candidates = routingEngine.rankCandidatePeers(originGps, 100, packet, peers)

        assertEquals(2, candidates.size)
        // Bridge device MUST be ranked first because it has internet for immediate rescue dispatch!
        assertEquals("ep-bridge", candidates.first().endpointId)
        assertTrue("Top candidate must have internet", candidates.first().hasInternet)
    }

    @Test
    fun testNearestAvailableRelayChosenWhenAllOffline() {
        // Phone B evaluates Phone C (closer, 100m) vs Phone D (farther, 800m)
        val phoneBGps = RealGpsInfo(hasLocation = true, latitude = 37.7750, longitude = -122.4194)
        val relayedPacket = SosPacket(
            messageId = "test-sos-chain",
            originDeviceId = "dev-phone-a",
            originDeviceName = "ECHO-Phone A",
            timestamp = System.currentTimeMillis(),
            latitude = 37.7740,
            longitude = -122.4194,
            hopCount = 1,
            ttl = 4,
            path = listOf("ECHO-Phone A", "ECHO-Phone B")
        )

        // Phone C: 100m away
        routingEngine.updatePeerInfo(
            "ep-c",
            RoutingNodeInfo(
                deviceId = "dev-phone-c",
                deviceName = "ECHO-Phone C",
                hasInternet = false,
                latitude = 37.7759,
                longitude = -122.4194,
                batteryLevel = 85,
                hopsToBridge = 99
            )
        )

        // Phone D: 800m away
        routingEngine.updatePeerInfo(
            "ep-d",
            RoutingNodeInfo(
                deviceId = "dev-phone-d",
                deviceName = "ECHO-Phone D",
                hasInternet = false,
                latitude = 37.7820,
                longitude = -122.4194,
                batteryLevel = 85,
                hopsToBridge = 99
            )
        )

        val peers = mapOf("ep-c" to "ECHO-Phone C", "ep-d" to "ECHO-Phone D")
        val nextHop = routingEngine.selectBestNextHop(phoneBGps, 90, relayedPacket, peers)

        assertNotNull(nextHop)
        // Phone C is closer than Phone D, so Phone C must be chosen as next hop!
        assertEquals("ep-c", nextHop?.endpointId)
        assertEquals("ECHO-Phone C", nextHop?.peerName)
    }

    @Test
    fun testLoopPreventionFiltersVisitedNodesAndOrigin() {
        val phoneCGps = RealGpsInfo(hasLocation = true, latitude = 37.7760, longitude = -122.4194)
        val relayedPacket = SosPacket(
            messageId = "test-sos-loop",
            originDeviceId = "dev-phone-a",
            originDeviceName = "ECHO-Phone A",
            timestamp = System.currentTimeMillis(),
            latitude = 37.7740,
            longitude = -122.4194,
            hopCount = 2,
            ttl = 3,
            path = listOf("ECHO-Phone A", "ECHO-Phone B", "ECHO-Phone C")
        )

        routingEngine.updatePeerInfo(
            "ep-a",
            RoutingNodeInfo(deviceId = "dev-phone-a", deviceName = "ECHO-Phone A", hasInternet = false)
        )
        routingEngine.updatePeerInfo(
            "ep-b",
            RoutingNodeInfo(deviceId = "dev-phone-b", deviceName = "ECHO-Phone B", hasInternet = false)
        )
        routingEngine.updatePeerInfo(
            "ep-d",
            RoutingNodeInfo(deviceId = "dev-phone-d", deviceName = "ECHO-Phone D", hasInternet = false)
        )

        val peers = mapOf(
            "ep-a" to "ECHO-Phone A",
            "ep-b" to "ECHO-Phone B",
            "ep-d" to "ECHO-Phone D"
        )

        // Exclude ep-b from which the packet arrived
        val candidates = routingEngine.rankCandidatePeers(
            currentGps = phoneCGps,
            currentBattery = 80,
            packet = relayedPacket,
            availablePeers = peers,
            excludeEndpoint = "ep-b"
        )

        // ep-a is origin, ep-b is source & in path -> ONLY ep-d must remain!
        assertEquals(1, candidates.size)
        assertEquals("ep-d", candidates.first().endpointId)
    }
}
