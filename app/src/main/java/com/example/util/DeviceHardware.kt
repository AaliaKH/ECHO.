package com.example.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

object DeviceIdentity {

    private const val PREFS_NAME = "echo_device_prefs"
    private const val KEY_DEVICE_ID = "device_hardware_uuid"

    /**
     * Requirement: "the device's name shouldnt be coustomizable. it should be ECHO-"The device's name" simple.
     * the user should have no option to choose the device for itslef like phone a phone b or phone c"
     */
    fun getDeviceName(): String {
        val manufacturer = Build.MANUFACTURER.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
        }
        val model = Build.MODEL
        val cleanModel = if (model.startsWith(manufacturer, ignoreCase = true)) {
            model
        } else {
            "$manufacturer $model"
        }
        return "ECHO-$cleanModel".trim()
    }

    fun getDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var id = prefs.getString(KEY_DEVICE_ID, null)
        if (id == null) {
            id = UUID.randomUUID().toString().substring(0, 8)
            prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        }
        return id
    }
}

data class RealBatteryInfo(
    val percentage: Int,
    val isCharging: Boolean
)

data class RealGpsInfo(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitude: Double = 0.0,
    val accuracy: Float = 0.0f, // horizontal accuracy radius in meters
    val timestamp: Long = System.currentTimeMillis(),
    val hasLocation: Boolean = true,
    val isFresh: Boolean = false,
    val isStale: Boolean = false,
    val isUnavailable: Boolean = false,
    val statusDescription: String = "Location Unavailable"
)

object LocationUtils {
    /**
     * Calculates distance in meters between two geographical points using Android Location.distanceBetween.
     * Returns null if coordinates are unset/invalid (0.0, 0.0).
     */
    fun calculateDistanceMeters(
        lat1: Double,
        lng1: Double,
        lat2: Double,
        lng2: Double
    ): Float? {
        if ((lat1 == 0.0 && lng1 == 0.0) || (lat2 == 0.0 && lng2 == 0.0)) return null
        val results = FloatArray(1)
        return try {
            Location.distanceBetween(lat1, lng1, lat2, lng2, results)
            results[0]
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Formats distance in meters clearly for relayer pop-ups and rescue consoles:
     * e.g. "120 meters away" or "1,450 meters away (1.45 km)"
     */
    fun formatDistance(meters: Float?): String {
        if (meters == null) return "Distance Unavailable"
        val m = meters.toInt()
        return if (m < 1000) {
            "$m meters away"
        } else {
            String.format(Locale.US, "%d meters away (%.2f km)", m, meters / 1000f)
        }
    }
}

class DeviceHardwareMonitor(private val context: Context) {

    /**
     * Legit real battery info from Android BatteryManager
     */
    fun getBatteryInfo(): RealBatteryInfo {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val batteryPctFromBm = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1

        val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus: Intent? = context.registerReceiver(null, ifilter)

        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1

        val isCharging: Boolean = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val percentage = if (batteryPctFromBm in 0..100) {
            batteryPctFromBm
        } else if (level >= 0 && scale > 0) {
            ((level / scale.toFloat()) * 100).toInt()
        } else {
            100 // Safe standard default
        }

        return RealBatteryInfo(
            percentage = percentage.coerceIn(0, 100),
            isCharging = isCharging
        )
    }

    /**
     * Stream battery changes reactively
     */
    fun batteryFlow(): Flow<RealBatteryInfo> = callbackFlow {
        trySend(getBatteryInfo())

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                trySend(getBatteryInfo())
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        context.registerReceiver(receiver, filter)

        awaitClose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    @Volatile
    private var cachedGpsInfo: RealGpsInfo? = null

    fun hasLocationPermission(): Boolean {
        // Real hardware GPS/GNSS location requires ACCESS_FINE_LOCATION
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Obtains GPS location directly from the phone's hardware GPS/GNSS location sensor.
     * Works 100% offline without internet, mobile data, Google Maps API, or IP/network location.
     * If GPS location cannot be obtained, returns "GPS location unavailable".
     */
    @SuppressLint("MissingPermission")
    fun getLastKnownGpsLocation(): RealGpsInfo {
        if (!hasLocationPermission()) {
            val unavail = RealGpsInfo(
                latitude = 0.0,
                longitude = 0.0,
                altitude = 0.0,
                accuracy = 0.0f,
                timestamp = System.currentTimeMillis(),
                hasLocation = false,
                isFresh = false,
                isStale = false,
                isUnavailable = true,
                statusDescription = "GPS location unavailable"
            )
            cachedGpsInfo = unavail
            return unavail
        }

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (lm == null || !lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            val unavail = RealGpsInfo(
                latitude = 0.0,
                longitude = 0.0,
                altitude = 0.0,
                accuracy = 0.0f,
                timestamp = System.currentTimeMillis(),
                hasLocation = false,
                isFresh = false,
                isStale = false,
                isUnavailable = true,
                statusDescription = "GPS location unavailable"
            )
            cachedGpsInfo = unavail
            return unavail
        }

        val lastGps = try {
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        } catch (e: SecurityException) {
            null
        }

        if (lastGps != null && (lastGps.latitude != 0.0 || lastGps.longitude != 0.0)) {
            val isFresh = (System.currentTimeMillis() - lastGps.time) < 60_000L
            val result = RealGpsInfo(
                latitude = lastGps.latitude,
                longitude = lastGps.longitude,
                altitude = lastGps.altitude,
                accuracy = lastGps.accuracy, // horizontal accuracy in metres
                timestamp = lastGps.time.takeIf { it > 0 } ?: System.currentTimeMillis(),
                hasLocation = true,
                isFresh = isFresh,
                isStale = !isFresh,
                isUnavailable = false,
                statusDescription = "GPS Fix (±${lastGps.accuracy.toInt()}m)"
            )
            cachedGpsInfo = result
            return result
        }

        val fallback = cachedGpsInfo
        if (fallback != null && fallback.hasLocation && (fallback.latitude != 0.0 || fallback.longitude != 0.0)) {
            return fallback.copy(
                isFresh = false,
                isStale = true,
                isUnavailable = false,
                statusDescription = "Previous GPS Fix"
            )
        }

        val unavail = RealGpsInfo(
            latitude = 0.0,
            longitude = 0.0,
            altitude = 0.0,
            accuracy = 0.0f,
            timestamp = System.currentTimeMillis(),
            hasLocation = false,
            isFresh = false,
            isStale = false,
            isUnavailable = true,
            statusDescription = "GPS location unavailable"
        )
        cachedGpsInfo = unavail
        return unavail
    }

    /**
     * When SOS is pressed, obtain the user's REAL current location directly from the phone's GPS/GNSS location sensor.
     * Do NOT use IP address, internet location services, Google Maps API, online geocoding, Wi-Fi location, or a hard-coded location.
     * The app must work without internet or mobile data.
     * Captures:
     * - Latitude
     * - Longitude
     * - GPS accuracy in metres
     * - Timestamp
     * If GPS location cannot be obtained, returns "GPS location unavailable".
     */
    @SuppressLint("MissingPermission")
    suspend fun requestFreshHighAccuracyLocation(timeoutMs: Long = 6000L): RealGpsInfo = withContext(Dispatchers.Main) {
        if (!hasLocationPermission()) {
            val unavail = RealGpsInfo(
                latitude = 0.0,
                longitude = 0.0,
                altitude = 0.0,
                accuracy = 0.0f,
                timestamp = System.currentTimeMillis(),
                hasLocation = false,
                isFresh = false,
                isStale = false,
                isUnavailable = true,
                statusDescription = "GPS location unavailable"
            )
            cachedGpsInfo = unavail
            return@withContext unavail
        }

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (lm == null || !lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            val unavail = RealGpsInfo(
                latitude = 0.0,
                longitude = 0.0,
                altitude = 0.0,
                accuracy = 0.0f,
                timestamp = System.currentTimeMillis(),
                hasLocation = false,
                isFresh = false,
                isStale = false,
                isUnavailable = true,
                statusDescription = "GPS location unavailable"
            )
            cachedGpsInfo = unavail
            return@withContext unavail
        }

        var freshGpsLoc: Location? = null
        try {
            freshGpsLoc = withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine { continuation ->
                    val listener = object : LocationListener {
                        override fun onLocationChanged(loc: Location) {
                            if (loc.provider == LocationManager.GPS_PROVIDER || loc.provider == "gps" || loc.provider == null) {
                                if (continuation.isActive) {
                                    try {
                                        lm.removeUpdates(this)
                                    } catch (_: Exception) {}
                                    continuation.resume(loc)
                                }
                            }
                        }
                        @Deprecated("Deprecated in Java")
                        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                        override fun onProviderEnabled(provider: String) {}
                        override fun onProviderDisabled(provider: String) {
                            if (continuation.isActive) {
                                continuation.resume(null)
                            }
                        }
                    }

                    try {
                        lm.requestLocationUpdates(
                            LocationManager.GPS_PROVIDER,
                            0L,
                            0f,
                            listener,
                            Looper.getMainLooper()
                        )
                    } catch (e: Exception) {
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                    }

                    continuation.invokeOnCancellation {
                        try {
                            lm.removeUpdates(listener)
                        } catch (_: Exception) {}
                    }
                }
            }
        } catch (e: Exception) {
            freshGpsLoc = null
        }

        if (freshGpsLoc != null && (freshGpsLoc.latitude != 0.0 || freshGpsLoc.longitude != 0.0)) {
            val freshInfo = RealGpsInfo(
                latitude = freshGpsLoc.latitude,
                longitude = freshGpsLoc.longitude,
                altitude = freshGpsLoc.altitude,
                accuracy = freshGpsLoc.accuracy, // GPS accuracy in metres
                timestamp = freshGpsLoc.time.takeIf { it > 0 } ?: System.currentTimeMillis(),
                hasLocation = true,
                isFresh = true,
                isStale = false,
                isUnavailable = false,
                statusDescription = "GPS Fix (±${freshGpsLoc.accuracy.toInt()}m)"
            )
            cachedGpsInfo = freshInfo
            return@withContext freshInfo
        }

        // If direct real-time satellite fix timed out, check last known hardware GPS location
        val lastGps = try {
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        } catch (_: Exception) {
            null
        }

        if (lastGps != null && (lastGps.latitude != 0.0 || lastGps.longitude != 0.0)) {
            val isFresh = (System.currentTimeMillis() - lastGps.time) < 60_000L
            val fallback = RealGpsInfo(
                latitude = lastGps.latitude,
                longitude = lastGps.longitude,
                altitude = lastGps.altitude,
                accuracy = lastGps.accuracy, // GPS accuracy in metres
                timestamp = lastGps.time.takeIf { it > 0 } ?: System.currentTimeMillis(),
                hasLocation = true,
                isFresh = isFresh,
                isStale = !isFresh,
                isUnavailable = false,
                statusDescription = if (isFresh) "GPS Fix (±${lastGps.accuracy.toInt()}m)" else "Last Known GPS Fix (±${lastGps.accuracy.toInt()}m)"
            )
            cachedGpsInfo = fallback
            return@withContext fallback
        }

        val existing = cachedGpsInfo
        if (existing != null && existing.hasLocation && (existing.latitude != 0.0 || existing.longitude != 0.0)) {
            return@withContext existing.copy(
                isFresh = false,
                isStale = true,
                isUnavailable = false,
                statusDescription = "Previous GPS Fix"
            )
        }

        val unavail = RealGpsInfo(
            latitude = 0.0,
            longitude = 0.0,
            altitude = 0.0,
            accuracy = 0.0f,
            timestamp = System.currentTimeMillis(),
            hasLocation = false,
            isFresh = false,
            isStale = false,
            isUnavailable = true,
            statusDescription = "GPS location unavailable"
        )
        cachedGpsInfo = unavail
        return@withContext unavail
    }

    /**
     * Offline GPS location query directly querying the GPS/GNSS sensor.
     */
    @SuppressLint("MissingPermission")
    fun requestFreshOfflineGpsLocation(onResult: (RealGpsInfo) -> Unit) {
        if (!hasLocationPermission()) {
            onResult(getLastKnownGpsLocation())
            return
        }
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (lm == null || !lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            onResult(getLastKnownGpsLocation())
            return
        }
        try {
            val listener = object : LocationListener {
                override fun onLocationChanged(loc: Location) {
                    try {
                        lm.removeUpdates(this)
                    } catch (_: Exception) {}
                    val fresh = RealGpsInfo(
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        altitude = loc.altitude,
                        accuracy = loc.accuracy, // GPS accuracy in metres
                        timestamp = loc.time.takeIf { it > 0 } ?: System.currentTimeMillis(),
                        hasLocation = true,
                        isFresh = true,
                        isStale = false,
                        isUnavailable = false,
                        statusDescription = "GPS Fix (±${loc.accuracy.toInt()}m)"
                    )
                    cachedGpsInfo = fresh
                    onResult(fresh)
                }
                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {
                    onResult(getLastKnownGpsLocation())
                }
            }
            lm.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                0L,
                0f,
                listener,
                Looper.getMainLooper()
            )
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    lm.removeUpdates(listener)
                } catch (_: Exception) {}
                onResult(getLastKnownGpsLocation())
            }, 4000L)
        } catch (e: Exception) {
            onResult(getLastKnownGpsLocation())
        }
    }

    /**
     * Checks if this device currently has an active Internet connection (Wi-Fi or Cellular).
     * If true, this device can act as an Emergency Bridge to dispatch center!
     */
    fun isConnectedToInternet(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun internetConnectionFlow(): Flow<Boolean> = callbackFlow {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm == null) {
            trySend(false)
            close()
            return@callbackFlow
        }

        trySend(isConnectedToInternet())

        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(isConnectedToInternet())
            }

            override fun onLost(network: Network) {
                trySend(isConnectedToInternet())
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                trySend(isConnectedToInternet())
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, networkCallback)

        awaitClose {
            try {
                cm.unregisterNetworkCallback(networkCallback)
            } catch (e: Exception) {
                // ignore
            }
        }
    }
}
