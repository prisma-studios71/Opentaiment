package app.opentaiment.feature.vehicle.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.UUID

/**
 * Collects the bytes an ELM327 prints and cuts them into responses at the '>' prompt.
 * BLE adapters send replies in 20-byte notification chunks, so a response can arrive in many pieces.
 */
class ElmResponseBuffer {
    private val current = StringBuilder()
    private val ready = ArrayDeque<String>()

    @Synchronized
    fun append(bytes: ByteArray) {
        for (b in bytes) {
            val c = (b.toInt() and 0xFF).toChar()
            if (c == '>') {
                ready.addLast(current.toString())
                current.clear()
            } else {
                current.append(c)
            }
        }
    }

    /** The oldest complete response, or null if the adapter is still talking. */
    @Synchronized
    fun poll(): String? = ready.removeFirstOrNull()

    @Synchronized
    fun clear() {
        current.clear()
        ready.clear()
    }
}

/** GATT service layouts used by BLE ELM327 clones and the better-known brands. */
internal data class BleObdProfile(val service: UUID, val notify: UUID, val write: UUID) {
    companion object {
        private fun uuid16(short: String) = UUID.fromString("0000$short-0000-1000-8000-00805f9b34fb")

        val KNOWN = listOf(
            // Most cheap "ELM327 BLE" / Vgate / Konnwei adapters.
            BleObdProfile(uuid16("fff0"), uuid16("fff1"), uuid16("fff2")),
            // HM-10 style modules: one characteristic for both directions.
            BleObdProfile(uuid16("ffe0"), uuid16("ffe1"), uuid16("ffe1")),
            // Vgate iCar Pro BLE / OBDLink CX and others.
            BleObdProfile(uuid16("18f0"), uuid16("2af0"), uuid16("2af1")),
            // Some Veepeak / Carista units.
            BleObdProfile(UUID.fromString("e7810a71-73ae-499d-8c15-faa9aef0c3f2"),
                UUID.fromString("bef8d6c9-9c21-4c9e-b632-bd58c1009f9f"),
                UUID.fromString("bef8d6c9-9c21-4c9e-b632-bd58c1009f9f")),
        )

        private val CCCD: UUID = uuid16("2902")
        fun cccd(): UUID = CCCD
    }
}

/** ELM327 over Bluetooth Low Energy (GATT). */
@SuppressLint("MissingPermission") // checked by VehicleRepository before connecting
class BleObdTransport private constructor(
    private val gatt: BluetoothGatt,
    private val writeChar: BluetoothGattCharacteristic,
    private val buffer: ElmResponseBuffer,
    private val callback: Callback,
) : ObdTransport {

    private val mutex = Mutex()

    override suspend fun send(command: String, timeoutMs: Long): String = mutex.withLock {
        if (callback.disconnected) throw IOException("Adapter OBD rozłączył się")
        buffer.clear()
        val bytes = "$command\r".toByteArray(Charsets.US_ASCII)
        // BLE writes are limited to 20 bytes by default; OBD commands are always shorter.
        val written = write(bytes)
        if (!written) throw IOException("Nie udało się wysłać $command do adaptera BLE")
        try {
            withTimeout(timeoutMs) {
                var response = buffer.poll()
                while (response == null) {
                    if (callback.disconnected) throw IOException("Adapter OBD rozłączył się")
                    kotlinx.coroutines.delay(5)
                    response = buffer.poll()
                }
                response
            }
        } catch (e: TimeoutCancellationException) {
            throw IOException("Adapter OBD nie odpowiada na $command")
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun write(bytes: ByteArray): Boolean = withContext(Dispatchers.Main) {
        val type = if (writeChar.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }
        if (Build.VERSION.SDK_INT >= 33) {
            gatt.writeCharacteristic(writeChar, bytes, type) == android.bluetooth.BluetoothStatusCodes.SUCCESS
        } else {
            writeChar.writeType = type
            writeChar.value = bytes
            gatt.writeCharacteristic(writeChar)
        }
    }

    override fun close() {
        try {
            gatt.disconnect()
            gatt.close()
        } catch (_: SecurityException) {
        }
    }

    internal class Callback(private val buffer: ElmResponseBuffer) : BluetoothGattCallback() {
        val connected = CompletableDeferred<Unit>()
        val servicesDiscovered = CompletableDeferred<Unit>()
        val descriptorWritten = CompletableDeferred<Unit>()

        @Volatile
        var disconnected = false

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> connected.complete(Unit)
                BluetoothProfile.STATE_DISCONNECTED -> {
                    disconnected = true
                    val error = IOException("Adapter BLE rozłączył się")
                    connected.completeExceptionally(error)
                    servicesDiscovered.completeExceptionally(error)
                    descriptorWritten.completeExceptionally(error)
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) servicesDiscovered.complete(Unit)
            else servicesDiscovered.completeExceptionally(IOException("Adapter BLE nie pokazał usług ($status)"))
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            descriptorWritten.complete(Unit)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            characteristic.value?.let(buffer::append)
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            buffer.append(value)
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000L

        suspend fun connect(context: Context, address: String): BleObdTransport {
            val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
                ?: throw IOException("To urządzenie nie ma Bluetooth")
            if (!adapter.isEnabled) throw IOException("Bluetooth jest wyłączony")
            val device = adapter.getRemoteDevice(address)
            val buffer = ElmResponseBuffer()
            val callback = Callback(buffer)
            val gatt = withContext(Dispatchers.Main) {
                device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            } ?: throw IOException("Nie udało się otworzyć połączenia BLE")
            try {
                withTimeout(CONNECT_TIMEOUT_MS) {
                    callback.connected.await()
                    withContext(Dispatchers.Main) { gatt.discoverServices() }
                    callback.servicesDiscovered.await()
                }
                val (notifyChar, writeChar) = BleObdProfile.KNOWN.firstNotNullOfOrNull { p ->
                    val service = gatt.getService(p.service) ?: return@firstNotNullOfOrNull null
                    val n = service.getCharacteristic(p.notify) ?: return@firstNotNullOfOrNull null
                    val w = service.getCharacteristic(p.write) ?: return@firstNotNullOfOrNull null
                    n to w
                } ?: throw IOException("To nie wygląda na adapter OBD (nieznane usługi BLE)")
                enableNotifications(gatt, notifyChar, callback)
                return BleObdTransport(gatt, writeChar, buffer, callback)
            } catch (e: TimeoutCancellationException) {
                gatt.close()
                throw IOException("Adapter BLE nie odpowiada – jest w zasięgu i ma prąd?")
            } catch (e: Exception) {
                gatt.close()
                throw e
            }
        }

        @Suppress("DEPRECATION")
        private suspend fun enableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, callback: Callback) {
            withContext(Dispatchers.Main) { gatt.setCharacteristicNotification(characteristic, true) }
            val descriptor = characteristic.getDescriptor(BleObdProfile.cccd()) ?: return
            val value = if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) {
                BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            } else {
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            }
            withContext(Dispatchers.Main) {
                if (Build.VERSION.SDK_INT >= 33) {
                    gatt.writeDescriptor(descriptor, value)
                } else {
                    descriptor.value = value
                    gatt.writeDescriptor(descriptor)
                }
            }
            // Some adapters never confirm the write; carry on after a short wait.
            try {
                withTimeout(2_000) { callback.descriptorWritten.await() }
            } catch (_: TimeoutCancellationException) {
            }
        }
    }
}
