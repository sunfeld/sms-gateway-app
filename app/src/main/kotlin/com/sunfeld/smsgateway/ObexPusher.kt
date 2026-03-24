package com.sunfeld.smsgateway

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Pushes data objects to Bluetooth devices via OBEX Object Push Profile (OPP).
 *
 * Implements the minimal OBEX protocol over RFCOMM:
 * 1. Connect to target's OPP service via RFCOMM
 * 2. Send OBEX CONNECT request
 * 3. Send OBEX PUT request with payload (vCard, vCalendar, etc.)
 * 4. Send OBEX DISCONNECT
 *
 * The target device will show an "Accept incoming file?" dialog.
 */
class ObexPusher {

    companion object {
        private const val TAG = "ObexPusher"
        // Standard UUID for OBEX Object Push Profile
        val OPP_UUID: UUID = UUID.fromString("00001105-0000-1000-8000-00805f9b34fb")
        private const val CONNECT_TIMEOUT_MS = 10000

        // OBEX opcodes
        private const val OBEX_CONNECT: Byte = 0x80.toByte()
        private const val OBEX_PUT_FINAL: Byte = 0x82.toByte()
        private const val OBEX_DISCONNECT: Byte = 0x81.toByte()

        // OBEX response codes
        private const val OBEX_SUCCESS: Byte = 0xA0.toByte()
        private const val OBEX_CONTINUE: Byte = 0x90.toByte()

        // OBEX headers
        private const val HEADER_NAME: Byte = 0x01  // Unicode string
        private const val HEADER_TYPE: Byte = 0x42   // Byte sequence (ASCII)
        private const val HEADER_LENGTH: Byte = 0xC3.toByte() // 4-byte uint
        private const val HEADER_BODY: Byte = 0x48   // Byte sequence
        private const val HEADER_END_OF_BODY: Byte = 0x49 // Byte sequence (final)
    }

    private val _pushCount = MutableStateFlow(0)
    val pushCount: StateFlow<Int> = _pushCount.asStateFlow()

    private val _lastResult = MutableStateFlow<String?>(null)
    val lastResult: StateFlow<String?> = _lastResult.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Push a payload to a target device via OBEX OPP.
     * Runs asynchronously. Check [lastResult] for outcome.
     */
    fun pushToDevice(device: BluetoothDevice, payload: BluetoothPayload) {
        scope.launch {
            try {
                val result = doPush(device, payload)
                _lastResult.value = result
                _pushCount.value++
                Log.d(TAG, "Push to ${device.address}: $result")
            } catch (e: Exception) {
                _lastResult.value = "Failed: ${e.message}"
                Log.e(TAG, "Push to ${device.address} failed", e)
            }
        }
    }

    /**
     * Push a payload to multiple devices.
     */
    fun pushToDevices(devices: List<BluetoothDevice>, payload: BluetoothPayload) {
        devices.forEach { device ->
            pushToDevice(device, payload)
        }
    }

    private suspend fun doPush(device: BluetoothDevice, payload: BluetoothPayload): String {
        return withContext(Dispatchers.IO) {
            var socket: BluetoothSocket? = null
            try {
                // Connect to OPP service
                socket = device.createRfcommSocketToServiceRecord(OPP_UUID)
                socket.connect()
                Log.d(TAG, "RFCOMM connected to ${device.address}")

                val input = socket.inputStream
                val output = socket.outputStream

                // OBEX Connect
                sendObexConnect(output)
                val connectResponse = readObexResponse(input)
                if (connectResponse != OBEX_SUCCESS) {
                    return@withContext "OBEX CONNECT rejected (0x${String.format("%02X", connectResponse)})"
                }
                Log.d(TAG, "OBEX CONNECT successful")

                // OBEX PUT
                val content = payload.toFileContent().toByteArray(Charsets.UTF_8)
                val fileName = payload.fileName()
                val mimeType = payload.mimeType()

                sendObexPut(output, fileName, mimeType, content)
                val putResponse = readObexResponse(input)
                Log.d(TAG, "OBEX PUT response: 0x${String.format("%02X", putResponse)}")

                // OBEX Disconnect
                sendObexDisconnect(output)
                readObexResponse(input) // Best-effort read

                when (putResponse) {
                    OBEX_SUCCESS -> "Sent $fileName (${content.size} bytes)"
                    OBEX_CONTINUE -> "Sent $fileName (accepted)"
                    else -> "PUT response: 0x${String.format("%02X", putResponse)}"
                }
            } catch (e: SecurityException) {
                "Permission denied: ${e.message}"
            } catch (e: java.io.IOException) {
                "Connection failed: ${e.message}"
            } finally {
                try { socket?.close() } catch (_: Exception) { }
            }
        }
    }

    // ---- OBEX protocol implementation ----

    private fun sendObexConnect(output: OutputStream) {
        // OBEX CONNECT: opcode(1) + length(2) + version(1) + flags(1) + maxPacket(2)
        val packet = byteArrayOf(
            OBEX_CONNECT,
            0x00, 0x07,  // Packet length: 7
            0x10,        // OBEX version 1.0
            0x00,        // Flags
            0x10, 0x00   // Max packet size: 4096
        )
        output.write(packet)
        output.flush()
    }

    private fun sendObexPut(output: OutputStream, name: String, type: String, body: ByteArray) {
        val nameBytes = encodeObexUnicodeName(name)
        val typeBytes = encodeObexType(type)
        val bodyHeader = encodeObexBody(body, final = true)
        val lengthHeader = encodeObexLength(body.size.toLong())

        // Total packet size
        val totalLen = 3 + nameBytes.size + typeBytes.size + lengthHeader.size + bodyHeader.size
        val packet = ByteArray(totalLen)
        var offset = 0

        // Opcode
        packet[offset++] = OBEX_PUT_FINAL
        // Packet length (2 bytes, big-endian)
        packet[offset++] = ((totalLen shr 8) and 0xFF).toByte()
        packet[offset++] = (totalLen and 0xFF).toByte()

        // Headers
        System.arraycopy(nameBytes, 0, packet, offset, nameBytes.size)
        offset += nameBytes.size
        System.arraycopy(typeBytes, 0, packet, offset, typeBytes.size)
        offset += typeBytes.size
        System.arraycopy(lengthHeader, 0, packet, offset, lengthHeader.size)
        offset += lengthHeader.size
        System.arraycopy(bodyHeader, 0, packet, offset, bodyHeader.size)

        output.write(packet)
        output.flush()
    }

    private fun sendObexDisconnect(output: OutputStream) {
        val packet = byteArrayOf(
            OBEX_DISCONNECT,
            0x00, 0x03  // Length: 3
        )
        output.write(packet)
        output.flush()
    }

    private fun readObexResponse(input: InputStream): Byte {
        val response = input.read()
        if (response == -1) return 0
        // Read the rest of the response packet (length field + data)
        val len1 = input.read()
        val len2 = input.read()
        val packetLen = ((len1 and 0xFF) shl 8) or (len2 and 0xFF)
        // Skip remaining bytes
        if (packetLen > 3) {
            val remaining = ByteArray(packetLen - 3)
            var read = 0
            while (read < remaining.size) {
                val n = input.read(remaining, read, remaining.size - read)
                if (n <= 0) break
                read += n
            }
        }
        return response.toByte()
    }

    // ---- OBEX header encoding ----

    private fun encodeObexUnicodeName(name: String): ByteArray {
        // Header ID (1) + Length (2) + UTF-16BE string + null terminator (2)
        val utf16 = name.toByteArray(Charsets.UTF_16BE)
        val totalLen = 3 + utf16.size + 2 // header + string + null term
        val header = ByteArray(totalLen)
        header[0] = HEADER_NAME
        header[1] = ((totalLen shr 8) and 0xFF).toByte()
        header[2] = (totalLen and 0xFF).toByte()
        System.arraycopy(utf16, 0, header, 3, utf16.size)
        // Null terminator already zeros
        return header
    }

    private fun encodeObexType(type: String): ByteArray {
        // Header ID (1) + Length (2) + ASCII string + null byte
        val ascii = type.toByteArray(Charsets.US_ASCII)
        val totalLen = 3 + ascii.size + 1
        val header = ByteArray(totalLen)
        header[0] = HEADER_TYPE
        header[1] = ((totalLen shr 8) and 0xFF).toByte()
        header[2] = (totalLen and 0xFF).toByte()
        System.arraycopy(ascii, 0, header, 3, ascii.size)
        return header
    }

    private fun encodeObexLength(length: Long): ByteArray {
        // Header ID (1) + 4-byte uint32
        return byteArrayOf(
            HEADER_LENGTH,
            ((length shr 24) and 0xFF).toByte(),
            ((length shr 16) and 0xFF).toByte(),
            ((length shr 8) and 0xFF).toByte(),
            (length and 0xFF).toByte()
        )
    }

    private fun encodeObexBody(body: ByteArray, final: Boolean): ByteArray {
        // Header ID (1) + Length (2) + body bytes
        val headerId = if (final) HEADER_END_OF_BODY else HEADER_BODY
        val totalLen = 3 + body.size
        val header = ByteArray(totalLen)
        header[0] = headerId
        header[1] = ((totalLen shr 8) and 0xFF).toByte()
        header[2] = (totalLen and 0xFF).toByte()
        System.arraycopy(body, 0, header, 3, body.size)
        return header
    }
}
