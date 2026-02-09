package aer.app.navhud

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

class BluetoothSppManager(private val context: Context) {

    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady = _isReady.asStateFlow()

    private var connectionJob: Job? = null
    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var ioThread: Thread? = null

    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    fun startConnectionLoop() {
        if (connectionJob?.isActive == true) return
        connectionJob = CoroutineScope(Dispatchers.Default).launch {
            while (true) {
                if (_isConnected.value) {
                    // If connected, check socket validity
                    if (socket?.isConnected == false) {
                        Log.e("BluetoothSpp", "Socket disconnected. Resetting connection.")
                        disconnect()
                    }
                } else {
                    // Try to connect
                    Log.d("BluetoothSpp", "Attempting to connect to device...")
                    attemptConnection()
                }
                delay(5000) // Wait 5 seconds before the next loop
            }
        }
    }

    fun stopConnectionLoop() {
        connectionJob?.cancel()
        disconnect()
    }

    private fun attemptConnection() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.w("BluetoothSpp", "Missing BLUETOOTH_CONNECT permission")
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            Log.w("BluetoothSpp", "Bluetooth adapter is not enabled")
            return
        }

        // Find bonded device by name match
        val bondedDevices = bluetoothAdapter.bondedDevices
        val targetDevice = bondedDevices.firstOrNull { device ->
            device.name?.contains(DEVICE_NAME_PATTERN, ignoreCase = true) == true
        }

        if (targetDevice == null) {
            Log.w("BluetoothSpp", "No bonded device found matching pattern: $DEVICE_NAME_PATTERN")
            return
        }

        Log.d("BluetoothSpp", "Found device: ${targetDevice.name} (${targetDevice.address})")

        try {
            // Create RFCOMM socket using SPP UUID
            val tmpSocket = targetDevice.createRfcommSocketToServiceRecord(SPP_UUID)
            
            Log.d("BluetoothSpp", "Connecting to ${targetDevice.name}...")
            tmpSocket.connect()

            socket = tmpSocket
            outputStream = tmpSocket.outputStream
            _isConnected.value = true
            _isReady.value = true

            Log.d("BluetoothSpp", "Connected successfully to ${targetDevice.name}")

            // Start I/O thread for reading (optional - currently we only write)
            startIoThread(tmpSocket)

        } catch (e: IOException) {
            Log.e("BluetoothSpp", "Connection failed: ${e.message}")
            disconnect()
        }
    }

    private fun startIoThread(socket: BluetoothSocket) {
        ioThread = Thread {
            val inputStream = socket.inputStream
            val buffer = ByteArray(1024)

            try {
                while (socket.isConnected) {
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead > 0) {
                        val received = String(buffer, 0, bytesRead)
                        Log.d("BluetoothSpp", "Received: $received")
                        // Handle incoming data if needed in the future
                    }
                }
            } catch (e: IOException) {
                Log.e("BluetoothSpp", "I/O thread error: ${e.message}")
                disconnect()
            }
        }
        ioThread?.start()
    }

    private fun disconnect() {
        _isConnected.value = false
        _isReady.value = false

        try {
            ioThread?.interrupt()
            ioThread = null
            outputStream?.close()
            outputStream = null
            socket?.close()
            socket = null
            Log.d("BluetoothSpp", "Disconnected and cleaned up resources")
        } catch (e: IOException) {
            Log.e("BluetoothSpp", "Error during disconnect: ${e.message}")
        }
    }

    fun sendData(data: ByteArray) {
        if (!_isReady.value) {
            Log.w("BluetoothSpp", "sendData called but Bluetooth is not ready. Data will not be sent.")
            return
        }

        val stream = outputStream
        if (stream == null) {
            Log.e("BluetoothSpp", "OutputStream is null, cannot send data")
            return
        }

        try {
            // Send data as-is (already includes newline terminator from serialization)
            stream.write(data)
            stream.flush()
            Log.d("BluetoothSpp", "Sent ${data.size} bytes successfully")
        } catch (e: IOException) {
            Log.e("BluetoothSpp", "Failed to send data: ${e.message}")
            disconnect()
        }
    }

    companion object {
        // SPP (Serial Port Profile) UUID
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        
        // Device name pattern to match (customize as needed)
        private const val DEVICE_NAME_PATTERN = "Buan-navHUD"
    }
}
