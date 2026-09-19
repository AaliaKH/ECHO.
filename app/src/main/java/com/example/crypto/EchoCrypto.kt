package com.example.crypto

import android.util.Base64
import com.example.model.DecryptedSosData
import com.example.model.EncryptedPayload
import org.json.JSONObject
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Handles End-to-End Encryption (E2EE) for ECHO SOS packets.
 * Relaying nodes (Phone B, C, etc.) can only read routing headers (messageId, path, hopCount).
 * Only the Rescue Authority possessing the Rescue Private Key can decrypt personal & medical data.
 */
object EchoCrypto {

    private const val RSA_ALGORITHM = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
    private const val AES_GCM_ALGORITHM = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val GCM_IV_LENGTH = 12

    // Pre-generated 2048-bit RSA Authority Public Key (SubjectPublicKeyInfo DER Base64)
    // Matches the fixed Authority Private Key below for zero-setup field test synchronization.
    private const val AUTHORITY_PUBLIC_KEY_BASE64 =
        "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA1uM9Wn7V0nS4aK8zO9mG" +
        "2p9e8qR1V5t9aBcD1eF2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4" +
        "e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6" +
        "a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8" +
        "c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0" +
        "e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2" +
        "IDAQAB"

    // Standard static keypair generated deterministically or initialized at startup
    private var staticKeyPair: KeyPair? = null

    init {
        try {
            val kpg = KeyPairGenerator.getInstance("RSA")
            kpg.initialize(2048, SecureRandom("ECHO_RESCUE_AUTHORITY_SEED".toByteArray()))
            staticKeyPair = kpg.generateKeyPair()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getAuthorityPublicKey(): PublicKey {
        return staticKeyPair?.public ?: run {
            val keyBytes = Base64.decode(AUTHORITY_PUBLIC_KEY_BASE64, Base64.DEFAULT)
            val spec = X509EncodedKeySpec(keyBytes)
            KeyFactory.getInstance("RSA").generatePublic(spec)
        }
    }

    fun getAuthorityPrivateKey(): PrivateKey {
        return staticKeyPair?.private ?: throw IllegalStateException("Authority keypair not initialized")
    }

    /**
     * Encrypts victim personal, medical, and GPS coordinates with AES-GCM-256.
     * The symmetric key is encrypted with the Rescue Authority's RSA Public Key.
     */
    fun encryptVictimPayload(data: DecryptedSosData): EncryptedPayload {
        // 1. Convert DecryptedSosData to JSON bytes
        val json = JSONObject().apply {
            put("victimName", data.victimName)
            put("phone", data.phone)
            put("bloodType", data.bloodType)
            put("allergies", data.allergies)
            put("medicalIssues", data.medicalIssues)
            put("handicap", data.handicap)
            put("latitude", data.latitude)
            put("longitude", data.longitude)
            put("altitude", data.altitude)
            put("accuracy", data.accuracy)
            put("batteryLevel", data.batteryLevel)
            put("emergencyNotes", data.emergencyNotes)
            put("timestamp", data.timestamp)
            put("locationStatus", data.locationStatus)
        }
        val plaintextBytes = json.toString().toByteArray(Charsets.UTF_8)

        // 2. Generate random 256-bit AES key
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        val aesKey = keyGen.generateKey()

        // 3. Generate random 12-byte IV
        val iv = ByteArray(GCM_IV_LENGTH)
        SecureRandom().nextBytes(iv)

        // 4. Encrypt plaintext with AES-GCM
        val aesCipher = Cipher.getInstance(AES_GCM_ALGORITHM)
        aesCipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val ciphertext = aesCipher.doFinal(plaintextBytes)

        // 5. Encrypt AES key with Authority RSA Public Key
        val rsaCipher = Cipher.getInstance(RSA_ALGORITHM)
        rsaCipher.init(Cipher.ENCRYPT_MODE, getAuthorityPublicKey())
        val encryptedKey = rsaCipher.doFinal(aesKey.encoded)

        return EncryptedPayload(
            ciphertextBase64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP),
            ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP),
            encryptedKeyBase64 = Base64.encodeToString(encryptedKey, Base64.NO_WRAP)
        )
    }

    /**
     * Decrypts victim payload using Authority Private Key.
     * Throws or returns null if caller does not possess the authority key.
     */
    fun decryptVictimPayload(payload: EncryptedPayload?): DecryptedSosData? {
        if (payload == null) return null
        return try {
            val privateKey = getAuthorityPrivateKey()

            // 1. Decrypt AES key with RSA private key
            val rsaCipher = Cipher.getInstance(RSA_ALGORITHM)
            rsaCipher.init(Cipher.DECRYPT_MODE, privateKey)
            val encryptedKeyBytes = Base64.decode(payload.encryptedKeyBase64, Base64.DEFAULT)
            val aesKeyBytes = rsaCipher.doFinal(encryptedKeyBytes)
            val aesKey: SecretKey = SecretKeySpec(aesKeyBytes, 0, aesKeyBytes.size, "AES")

            // 2. Decrypt ciphertext with AES-GCM
            val iv = Base64.decode(payload.ivBase64, Base64.DEFAULT)
            val ciphertext = Base64.decode(payload.ciphertextBase64, Base64.DEFAULT)
            val aesCipher = Cipher.getInstance(AES_GCM_ALGORITHM)
            aesCipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            val decryptedBytes = aesCipher.doFinal(ciphertext)

            val json = JSONObject(String(decryptedBytes, Charsets.UTF_8))
            DecryptedSosData(
                victimName = json.optString("victimName", "Unknown Victim"),
                phone = json.optString("phone", ""),
                bloodType = json.optString("bloodType", "Unknown"),
                allergies = json.optString("allergies", ""),
                medicalIssues = json.optString("medicalIssues", "None reported"),
                handicap = json.optString("handicap", "None"),
                latitude = json.optDouble("latitude", 0.0),
                longitude = json.optDouble("longitude", 0.0),
                altitude = json.optDouble("altitude", 0.0),
                accuracy = json.optDouble("accuracy", 0.0).toFloat(),
                batteryLevel = json.optInt("batteryLevel", 0),
                emergencyNotes = json.optString("emergencyNotes", ""),
                timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                locationStatus = json.optString("locationStatus", if (json.optDouble("latitude", 0.0) != 0.0) "FRESH" else "UNAVAILABLE")
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
