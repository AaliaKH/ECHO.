package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.EchoApplication
import com.example.MainActivity
import com.example.mesh.NearbyMeshManager
import com.example.model.SosPacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Android Foreground Service managing Google Nearby Connections API
 * for peer-to-peer discovery and message relaying in the emergency mesh network
 * offline exclusively over Bluetooth and BLE.
 *
 * Holds a partial wake-lock so the CPU does not sleep when the screen is turned off,
 * ensuring continuous multi-hop SOS packet forwarding and reverse ACK delivery.
 */
class EchoMeshService : Service() {

    companion object {
        const val TAG = "EchoMeshService"
        const val CHANNEL_ID = "echo_mesh_foreground_channel"
        const val RELAY_ALERT_CHANNEL_ID = "echo_mesh_relay_alert_channel"
        const val NOTIFICATION_ID = 101
        const val RELAY_NOTIFICATION_ID = 102

        const val ACTION_START = "ACTION_START_MESH"
        const val ACTION_STOP = "ACTION_STOP_MESH"
        const val ACTION_REFRESH = "ACTION_REFRESH_MESH"
        const val ACTION_BROADCAST_SOS = "ACTION_BROADCAST_SOS"

        fun startService(context: Context) {
            val intent = Intent(context, EchoMeshService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, EchoMeshService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun refreshMesh(context: Context) {
            val intent = Intent(context, EchoMeshService::class.java).apply {
                action = ACTION_REFRESH
            }
            context.startService(intent)
        }

        fun updateNotificationForRelay(context: Context, originDevice: String, hopCount: Int) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification = NotificationCompat.Builder(context, RELAY_ALERT_CHANNEL_ID)
                .setContentTitle("ECHO: SOS RELAYED OVER BLE")
                .setContentText("Emergency distress signal from $originDevice (Hop #$hopCount) relayed via Bluetooth/BLE mesh")
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setOngoing(false)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .build()

            manager.notify(RELAY_NOTIFICATION_ID, notification)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val binder = LocalBinder()
    private var wakeLock: PowerManager.WakeLock? = null
    private var observerJob: Job? = null

    inner class LocalBinder : Binder() {
        fun getService(): EchoMeshService = this@EchoMeshService
        fun getMeshManager(): NearbyMeshManager? = (application as? EchoApplication)?.meshManager
        fun isMeshActive(): Boolean = (application as? EchoApplication)?.meshManager?.isMeshActive?.value == true
        fun broadcastSos(packet: SosPacket) {
            (application as? EchoApplication)?.meshManager?.broadcastNewSos(packet)
        }
        fun refreshMesh() {
            (application as? EchoApplication)?.meshManager?.restartDiscoveryAndAdvertising()
        }
        fun stopMesh() {
            (application as? EchoApplication)?.meshManager?.stopMesh()
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        acquireWakeLock()
        startMeshStatusObserver()
        Log.i(TAG, "EchoMeshService created. Google Nearby Connections Bluetooth/BLE mesh service ready.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as? EchoApplication
        val meshManager = app?.meshManager

        when (intent?.action) {
            ACTION_START -> {
                Log.i(TAG, "ACTION_START received: Starting Bluetooth & BLE Nearby Connections mesh")
                val notification = buildForegroundNotification("Bluetooth/BLE Mesh Active - Initializing P2P discovery...")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }

                if (meshManager?.isMeshActive?.value == true) {
                    meshManager.restartDiscoveryAndAdvertising()
                } else {
                    meshManager?.startMesh()
                }
            }

            ACTION_REFRESH -> {
                Log.i(TAG, "ACTION_REFRESH received: Refreshing Bluetooth/BLE discovery and advertising")
                meshManager?.restartDiscoveryAndAdvertising()
            }

            ACTION_STOP -> {
                Log.i(TAG, "ACTION_STOP received: Stopping Bluetooth/BLE mesh and service")
                meshManager?.stopMesh()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        observerJob?.cancel()
        releaseWakeLock()
        serviceScope.cancel()
        Log.i(TAG, "EchoMeshService destroyed.")
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ECHO:MeshServiceWakeLock")?.apply {
                    setReferenceCounted(false)
                    acquire(24 * 60 * 60 * 1000L) // Safe 24-hour limit
                }
                Log.i(TAG, "Partial wake lock acquired for continuous offline Bluetooth/BLE mesh relaying.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire WakeLock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                try {
                    it.release()
                    Log.i(TAG, "Partial wake lock released.")
                } catch (e: Exception) {
                    Log.w(TAG, "Error releasing WakeLock: ${e.message}")
                }
            }
        }
        wakeLock = null
    }

    /**
     * Observes mesh connection state and automatically updates the foreground notification
     * with live peer count and discovery stats so the user sees real-time mesh activity.
     */
    private fun startMeshStatusObserver() {
        observerJob?.cancel()
        observerJob = serviceScope.launch {
            val app = application as? EchoApplication ?: return@launch
            val meshManager = app.meshManager

            launch {
                meshManager.connectedPeers.collect { peers ->
                    val discovered = meshManager.discoveredCount.value
                    updateForegroundNotification(peers.size, discovered)
                }
            }

            launch {
                meshManager.discoveredCount.collect { discovered ->
                    val connected = meshManager.connectedPeers.value.size
                    updateForegroundNotification(connected, discovered)
                }
            }

            launch {
                meshManager.relayerGlowEvent.collect { packet ->
                    updateNotificationForRelay(
                        context = applicationContext,
                        originDevice = packet.originDeviceName,
                        hopCount = packet.hopCount + 1
                    )
                }
            }
        }
    }

    private fun updateForegroundNotification(connectedCount: Int, discoveredCount: Int) {
        val statusText = when {
            connectedCount > 0 -> "Bluetooth/BLE Active: $connectedCount connected peer(s) (${discoveredCount} nearby)"
            discoveredCount > 0 -> "Discovered $discoveredCount node(s) - establishing Bluetooth/BLE mesh..."
            else -> "Bluetooth/BLE Mesh Active - Monitoring for nearby distress signals"
        }
        val notification = buildForegroundNotification(statusText)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Low importance channel for continuous foreground status
            val meshChannel = NotificationChannel(
                CHANNEL_ID,
                "ECHO Mesh Relay Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors offline Bluetooth and BLE peer-to-peer mesh networking"
                setShowBadge(false)
            }
            manager.createNotificationChannel(meshChannel)

            // High importance channel for emergency SOS relay alerts
            val relayChannel = NotificationChannel(
                RELAY_ALERT_CHANNEL_ID,
                "ECHO Emergency Relays",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when an emergency SOS distress signal is relayed through this device"
                enableVibration(true)
                setShowBadge(true)
            }
            manager.createNotificationChannel(relayChannel)
        }
    }

    private fun buildForegroundNotification(statusText: String): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, EchoMeshService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ECHO Bluetooth/BLE Mesh")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(android.R.drawable.ic_menu_view, "Open ECHO", openAppIntent)
            .addAction(android.R.drawable.ic_delete, "Stop Mesh", stopIntent)
            .build()
    }
}
