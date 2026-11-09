package app.opentaiment.feature.vehicle.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.IOException
import java.util.UUID

/** A line-based link to an ELM327-compatible adapter. */
interface ObdTransport : Closeable {
    /** Sends [command] and returns everything the adapter printed before its '>' prompt. */
    suspend fun send(command: String, timeoutMs: Long = 2_000): String
}

/** Classic Bluetooth (SPP/RFCOMM) – what the vast majority of cheap ELM327 dongles use. */
class BluetoothObdTransport private constructor(private val socket: BluetoothSocket) : ObdTransport {

    private val input = socket.inputStream
    private val output = socket.outputStream

    override suspend fun send(command: String, timeoutMs: Long): String = withContext(Dispatchers.IO) {
        while (input.available() > 0) input.read() // drop leftovers from a previous timed-out command
        output.write("$command\r".toByteArray(Charsets.US_ASCII))
        output.flush()

        val response = StringBuilder()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (input.available() > 0) {
                val c = input.read()
                if (c == -1) throw IOException("Adapter OBD rozłączył się")
                if (c.toChar() == '>') return@withContext response.toString()
                response.append(c.toChar())
            } else {
                delay(5)
            }
        }
        throw IOException("Adapter OBD nie odpowiada na $command")
    }

    override fun close() {
        try {
            socket.close()
        } catch (_: IOException) {
        }
    }

    companion object {
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        @SuppressLint("MissingPermission") // checked by VehicleRepository before connecting
        suspend fun connect(context: Context, address: String): BluetoothObdTransport = withContext(Dispatchers.IO) {
            val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
                ?: throw IOException("To urządzenie nie ma Bluetooth")
            if (!adapter.isEnabled) throw IOException("Bluetooth jest wyłączony")
            try {
                adapter.cancelDiscovery() // discovery slows RFCOMM connects down a lot
            } catch (_: SecurityException) {
            }
            val device = adapter.getRemoteDevice(address)
            val socket = try {
                device.createRfcommSocketToServiceRecord(SPP_UUID).also { it.connect() }
            } catch (e: IOException) {
                // Many clones only accept insecure connections.
                device.createInsecureRfcommSocketToServiceRecord(SPP_UUID).also { it.connect() }
            }
            BluetoothObdTransport(socket)
        }
    }
}
