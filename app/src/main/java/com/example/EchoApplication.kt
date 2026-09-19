package com.example

import android.app.Application
import com.example.bridge.RescueBridgeDispatcher
import com.example.data.EchoDatabase
import com.example.data.EchoRepository
import com.example.mesh.NearbyMeshManager
import com.example.util.DeviceHardwareMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class EchoApplication : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var database: EchoDatabase
        private set

    lateinit var repository: EchoRepository
        private set

    lateinit var hardwareMonitor: DeviceHardwareMonitor
        private set

    lateinit var proximityRanger: com.example.util.DeviceProximityRanger
        private set

    lateinit var meshManager: NearbyMeshManager
        private set

    lateinit var bridgeDispatcher: RescueBridgeDispatcher
        private set

    override fun onCreate() {
        super.onCreate()

        database = EchoDatabase.getDatabase(this)
        repository = EchoRepository(
            sosDao = database.sosDao(),
            userProfileDao = database.userProfileDao(),
            meshMessageDao = database.meshMessageDao(),
            offlineSosLogDao = database.offlineSosLogDao()
        )
        hardwareMonitor = DeviceHardwareMonitor(this)
        proximityRanger = com.example.util.DeviceProximityRanger(this, applicationScope)
        bridgeDispatcher = RescueBridgeDispatcher(applicationScope)

        meshManager = NearbyMeshManager(
            context = this,
            repository = repository,
            hardwareMonitor = hardwareMonitor,
            proximityRanger = proximityRanger,
            scope = applicationScope
        )

        // Interlink Bridge and Mesh
        meshManager.onBridgeDispatchNeeded = { packet ->
            bridgeDispatcher.transmitSosToRescueDispatch(packet)
        }

        bridgeDispatcher.onDispatchAckGenerated = { ack ->
            meshManager.broadcastAck(ack)
        }
    }
}
