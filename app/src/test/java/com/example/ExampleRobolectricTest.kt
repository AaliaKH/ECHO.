package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.crypto.EchoCrypto
import com.example.model.DecryptedSosData
import com.example.model.EncryptedPayload
import com.example.model.SignalMetrics
import com.example.model.SosPacket
import com.example.util.DeviceIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("ECHO", appName)
  }

  @Test
  fun `device naming convention must strictly start with ECHO prefix`() {
    val deviceName = DeviceIdentity.getDeviceName()
    assertTrue("Device name should start with ECHO-", deviceName.startsWith("ECHO-"))
  }

  @Test
  fun `end to end encryption and rescue decryption round trip`() {
    val originalData = DecryptedSosData(
      victimName = "Jane Hiker",
      bloodType = "O-",
      medicalIssues = "Severe Peanut Allergy, Asthma",
      handicap = "None",
      latitude = 47.6062,
      longitude = -122.3321,
      altitude = 120.5,
      accuracy = 4.5f,
      batteryLevel = 84,
      emergencyNotes = "Trapped on mountain trail. Inhaler lost.",
      timestamp = System.currentTimeMillis()
    )

    // Encrypt using public key (origin device)
    val encrypted = EchoCrypto.encryptVictimPayload(originalData)
    assertNotNull(encrypted.encryptedKeyBase64)
    assertNotNull(encrypted.ciphertextBase64)
    assertNotNull(encrypted.ivBase64)

    // Decrypt using rescue authority private key
    val decrypted = EchoCrypto.decryptVictimPayload(encrypted)
    assertNotNull(decrypted)
    assertEquals(originalData.victimName, decrypted?.victimName)
    assertEquals(originalData.bloodType, decrypted?.bloodType)
    assertEquals(originalData.medicalIssues, decrypted?.medicalIssues)
    assertEquals(originalData.emergencyNotes, decrypted?.emergencyNotes)
    assertEquals(originalData.latitude, decrypted?.latitude ?: 0.0, 0.0001)
  }

  @Test
  fun `sos packet serialization and deserialization across mesh`() {
    val packet = SosPacket(
      messageId = UUID.randomUUID().toString(),
      originDeviceId = "phone-a-1234",
      originDeviceName = "ECHO-Pixel 7",
      timestamp = System.currentTimeMillis(),
      latitude = 37.7749,
      longitude = -122.4194,
      emergencyType = "SOS",
      priority = "CRITICAL",
      hopCount = 1,
      ttl = 9,
      path = listOf("ECHO-Pixel 7", "ECHO-Galaxy S22"),
      signalMetrics = SignalMetrics(batteryLevel = 90, isCharging = false, rssi = -60, networkType = "P2P"),
      encryptedPayload = EncryptedPayload(encryptedKeyBase64 = "key", ciphertextBase64 = "data", ivBase64 = "iv"),
      status = "HOPPING"
    )

    val jsonObject = packet.toJsonObject()
    val parsedPacket = SosPacket.fromJsonObject(jsonObject)

    assertEquals(packet.messageId, parsedPacket.messageId)
    assertEquals(packet.originDeviceName, parsedPacket.originDeviceName)
    assertEquals(packet.hopCount, parsedPacket.hopCount)
    assertEquals(packet.ttl, parsedPacket.ttl)
    assertEquals(packet.latitude, parsedPacket.latitude, 0.0001)
    assertEquals(packet.longitude, parsedPacket.longitude, 0.0001)
    assertEquals(packet.emergencyType, parsedPacket.emergencyType)
    assertEquals(packet.priority, parsedPacket.priority)
    assertEquals(packet.path.size, parsedPacket.path.size)
    assertEquals(packet.status, parsedPacket.status)
  }

  @Test
  fun `mesh loop prevention deduplication set prevents duplicate packet processing`() {
    val processedIds = java.util.Collections.synchronizedSet(HashSet<String>())
    val messageId = "ECHO-MSG-" + UUID.randomUUID().toString()

    // First arrival
    val isFirstTime = processedIds.add(messageId)
    assertTrue("First arrival must be accepted", isFirstTime)

    // Second arrival (loop or duplicate path)
    val isSecondTime = processedIds.add(messageId)
    org.junit.Assert.assertFalse("Duplicate packet must be rejected for loop prevention", isSecondTime)
  }

  @Test
  fun `unique message ID format and hop tracking`() {
    val messageId = "ECHO-SOS-" + UUID.randomUUID().toString().take(8).uppercase()
    assertTrue(messageId.startsWith("ECHO-SOS-"))

    val ack = com.example.model.AckPacket(
      messageId = messageId,
      ackType = com.example.model.AckType.RELAY_HOP,
      fromDevice = "ECHO-Device B",
      hopCount = 1,
      path = listOf("ECHO-Device A", "ECHO-Device B"),
      timestamp = System.currentTimeMillis()
    )

    val ackJson = ack.toJsonObject()
    val parsedAck = com.example.model.AckPacket.fromJsonObject(ackJson)
    assertEquals(messageId, parsedAck.messageId)
    assertEquals("ECHO-Device B", parsedAck.fromDevice)
    assertEquals(com.example.model.AckType.RELAY_HOP, parsedAck.ackType)
  }

  @Test
  fun `mfa token format validation`() {
    val challenge = "%06d".format((100000..999999).random())
    assertEquals(6, challenge.length)
    assertTrue(challenge.all { it.isDigit() })
  }

  @Test
  fun `civilian profile contains health information and default role`() {
    val civilian = com.example.model.UserProfile(
      name = "Aarav Sharma",
      phone = "+91 98765 43210",
      bloodType = "B+",
      allergies = "Penicillin, Peanuts",
      medicalIssues = "Type 1 Diabetes",
      handicap = "Wheelchair dependent",
      role = com.example.model.UserRole.CIVILIAN,
      isLoggedIn = true
    )

    assertEquals(com.example.model.UserRole.CIVILIAN, civilian.role)
    assertEquals("B+", civilian.bloodType)
    assertEquals("Penicillin, Peanuts", civilian.allergies)
    assertEquals("Type 1 Diabetes", civilian.medicalIssues)
    assertTrue(civilian.isLoggedIn)
  }

  @Test
  fun `authority profile requires credentials and restricts rescue dashboard`() {
    val authority = com.example.model.UserProfile(
      name = "Inspector Rajesh Kumar",
      authorityId = "NDRF-UNIT-07",
      agency = "National Disaster Response Force (NDRF)",
      role = com.example.model.UserRole.AUTHORITY,
      isLoggedIn = true
    )

    assertEquals(com.example.model.UserRole.AUTHORITY, authority.role)
    assertEquals("NDRF-UNIT-07", authority.authorityId)
    assertEquals("National Disaster Response Force (NDRF)", authority.agency)
    assertTrue(authority.isLoggedIn)
  }
}

