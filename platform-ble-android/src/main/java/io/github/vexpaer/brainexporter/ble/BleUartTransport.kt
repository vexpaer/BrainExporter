package io.github.vexpaer.brainexporter.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import java.util.ArrayDeque
import java.util.UUID

data class UartGattProfile(
    val label: String,
    val serviceUuid: UUID,
    val writeUuid: UUID,
    val notifyUuid: UUID,
)

data class BlePeripheral(
    val address: String,
    val name: String,
    val rssi: Int,
    val serviceUuids: List<String>,
)

interface BleUartListener {
    fun onScanResults(devices: List<BlePeripheral>)
    fun onReady(profile: UartGattProfile)
    fun onNotification(data: ByteArray)
    fun onDisconnected(reason: String?)
    fun onError(message: String, cause: Throwable? = null)
}

/**
 * Generic BLE-to-UART transport. Hardware-specific UUIDs and packet parsing stay
 * in a device plug-in, so another headset can reuse or replace this layer.
 */
@SuppressLint("MissingPermission")
class AndroidBleUartTransport(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val bluetoothManager =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter?
        get() = bluetoothManager.adapter

    private var listener: BleUartListener? = null
    private var scanCallback: ScanCallback? = null
    private var scanStop: Runnable? = null
    private val scanResults = linkedMapOf<String, BlePeripheral>()

    private var activeGatt: BluetoothGatt? = null
    private var profiles: List<UartGattProfile> = emptyList()
    private var selectedProfile: UartGattProfile? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private val writeQueue = ArrayDeque<ByteArray>()
    private var writePumpScheduled = false
    private var intentionalDisconnect = false

    fun setListener(value: BleUartListener?) {
        listener = value
    }

    fun scan(durationMillis: Long) {
        mainHandler.post {
            stopScanInternal(notify = false)
            val currentAdapter = adapter
            if (currentAdapter == null) {
                listener?.onError("Bluetooth is not supported on this device.")
                return@post
            }
            if (!currentAdapter.isEnabled) {
                listener?.onError("Turn on Bluetooth first.")
                return@post
            }
            val scanner = currentAdapter.bluetoothLeScanner
            if (scanner == null) {
                listener?.onError("Unable to start BLE scan.")
                return@post
            }

            scanResults.clear()
            val callback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    acceptScanResult(result)
                }

                override fun onBatchScanResults(results: MutableList<ScanResult>) {
                    results.forEach(::acceptScanResult)
                }

                override fun onScanFailed(errorCode: Int) {
                    stopScanInternal(notify = false)
                    listener?.onError("BLE scan failed (code $errorCode)。")
                }
            }
            scanCallback = callback
            try {
                scanner.startScan(callback)
            } catch (error: SecurityException) {
                scanCallback = null
                listener?.onError("Nearby Devices / Bluetooth scan permission is missing.", error)
                return@post
            } catch (error: IllegalStateException) {
                scanCallback = null
                listener?.onError("BLE scanner is unavailable.", error)
                return@post
            }

            val stop = Runnable { stopScanInternal(notify = true) }
            scanStop = stop
            mainHandler.postDelayed(stop, durationMillis.coerceIn(1_000, 20_000))
        }
    }

    private fun acceptScanResult(result: ScanResult) {
        val record = result.scanRecord
        val services = (record?.serviceUuids ?: emptyList<ParcelUuid>())
            .map { it.uuid.toString().lowercase() }
        val name = record?.deviceName
            ?: runCatching { result.device.name }.getOrNull()
            ?: "Unnamed BLE device"
        scanResults[result.device.address] = BlePeripheral(
            address = result.device.address,
            name = name,
            rssi = result.rssi,
            serviceUuids = services,
        )
        listener?.onScanResults(scanResults.values.sortedByDescending { it.rssi })
    }

    private fun stopScanInternal(notify: Boolean) {
        scanStop?.let(mainHandler::removeCallbacks)
        scanStop = null
        val callback = scanCallback ?: return
        scanCallback = null
        runCatching { adapter?.bluetoothLeScanner?.stopScan(callback) }
        if (notify) {
            listener?.onScanResults(scanResults.values.sortedByDescending { it.rssi })
        }
    }

    fun connect(address: String, candidates: List<UartGattProfile>) {
        mainHandler.post {
            stopScanInternal(notify = false)
            closeGattImmediately()
            val currentAdapter = adapter
            if (currentAdapter == null || !currentAdapter.isEnabled) {
                listener?.onError("Turn on Bluetooth first.")
                return@post
            }
            profiles = candidates
            selectedProfile = null
            writeCharacteristic = null
            intentionalDisconnect = false
            val device = try {
                currentAdapter.getRemoteDevice(address)
            } catch (error: IllegalArgumentException) {
                listener?.onError("Invalid BLE device address.", error)
                return@post
            }
            try {
                activeGatt = device.connectGatt(
                    appContext,
                    false,
                    gattCallback,
                    BluetoothDevice.TRANSPORT_LE,
                )
            } catch (error: SecurityException) {
                listener?.onError("Bluetooth connect permission is missing.", error)
            } catch (error: RuntimeException) {
                listener?.onError("Could not connect to this BLE device.", error)
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (gatt !== activeGatt) {
                runCatching { gatt.close() }
                return
            }
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        fail("Unexpected BLE connection state ($status)。")
                        return
                    }
                    runCatching {
                        gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                    }
                    if (!gatt.requestMtu(247)) discoverServices(gatt)
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    val wasIntentional = intentionalDisconnect
                    closeGattImmediately()
                    listener?.onDisconnected(
                        if (wasIntentional) null else "BLE disconnected (status $status)。",
                    )
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (gatt === activeGatt) discoverServices(gatt)
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (gatt !== activeGatt) return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                fail("GATT service discovery failed ($status)。")
                return
            }
            val match = profiles.firstNotNullOfOrNull { profile ->
                val service = gatt.getService(profile.serviceUuid) ?: return@firstNotNullOfOrNull null
                val write = service.getCharacteristic(profile.writeUuid)
                val notify = service.getCharacteristic(profile.notifyUuid)
                if (write != null && notify != null) Triple(profile, write, notify) else null
            }
            if (match == null) {
                val available = gatt.services
                    .flatMap { it.characteristics }
                    .joinToString { it.uuid.toString() }
                fail("No recognized UART characteristic. Found: $available")
                return
            }

            selectedProfile = match.first
            writeCharacteristic = match.second
            val notify = match.third
            if (!gatt.setCharacteristicNotification(notify, true)) {
                fail("Could not enable BLE notifications.")
                return
            }
            val descriptor = notify.getDescriptor(CLIENT_CONFIGURATION_UUID)
            if (descriptor == null) {
                fail("Notification characteristic lacks CCCD.")
                return
            }
            val accepted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(
                    descriptor,
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE,
                ) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                run {
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(descriptor)
                }
            }
            if (!accepted) fail("Could not write BLE notification configuration.")
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            if (gatt !== activeGatt || descriptor.uuid != CLIENT_CONFIGURATION_UUID) return
            if (status == BluetoothGatt.GATT_SUCCESS) {
                selectedProfile?.let { listener?.onReady(it) }
            } else {
                fail("Could not enable BLE notifications ($status)。")
            }
        }

        @Deprecated("Deprecated in Android 13")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && gatt === activeGatt) {
                @Suppress("DEPRECATION")
                listener?.onNotification(characteristic.value?.clone() ?: byteArrayOf())
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            if (gatt === activeGatt) listener?.onNotification(value.clone())
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (gatt === activeGatt && status != BluetoothGatt.GATT_SUCCESS) {
                listener?.onError("BLE command write failed ($status)。")
            }
        }
    }

    private fun discoverServices(gatt: BluetoothGatt) {
        if (!gatt.discoverServices()) fail("Could not discover BLE services.")
    }

    fun write(payload: ByteArray) {
        if (payload.isEmpty()) return
        mainHandler.post {
            writeQueue.addLast(payload.clone())
            scheduleWritePump(0)
        }
    }

    private fun scheduleWritePump(delayMillis: Long) {
        if (writePumpScheduled) return
        writePumpScheduled = true
        mainHandler.postDelayed({
            writePumpScheduled = false
            val gatt = activeGatt
            val characteristic = writeCharacteristic
            if (gatt == null || characteristic == null || writeQueue.isEmpty()) return@postDelayed
            val payload = writeQueue.removeFirst()
            val accepted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(
                    characteristic,
                    payload,
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE,
                ) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                run {
                    characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    characteristic.value = payload
                    gatt.writeCharacteristic(characteristic)
                }
            }
            if (!accepted) listener?.onError("BLE command queue write failed.")
            if (writeQueue.isNotEmpty()) scheduleWritePump(28)
        }, delayMillis)
    }

    fun disconnect() {
        mainHandler.post {
            intentionalDisconnect = true
            writeQueue.clear()
            activeGatt?.disconnect()
            mainHandler.postDelayed({
                if (intentionalDisconnect) closeGattImmediately()
            }, 500)
        }
    }

    private fun fail(message: String) {
        listener?.onError(message)
        intentionalDisconnect = true
        activeGatt?.disconnect()
    }

    private fun closeGattImmediately() {
        writeQueue.clear()
        writePumpScheduled = false
        val old = activeGatt
        activeGatt = null
        writeCharacteristic = null
        selectedProfile = null
        runCatching { old?.close() }
    }

    override fun close() {
        mainHandler.post {
            stopScanInternal(notify = false)
            intentionalDisconnect = true
            closeGattImmediately()
            listener = null
        }
    }

    private companion object {
        val CLIENT_CONFIGURATION_UUID: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
