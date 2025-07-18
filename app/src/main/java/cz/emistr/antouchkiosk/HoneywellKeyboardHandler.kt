import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileInputStream
import java.lang.ref.WeakReference

class HoneywellKeyboardHandler(context: Context, webView: WebView) {
    private val TAG = "HoneywellHandler"
    private val contextRef = WeakReference(context)
    private val webViewRef = WeakReference(webView)

    private val inputHandler = Handler(Looper.getMainLooper())
    private val barcodeBuffer = StringBuilder()
    private var lastInputTime = 0L
    private val BARCODE_TIMEOUT = 500L

    private val inputReaders = mutableListOf<Thread>()
    private var honeywellDevicePath: String? = null
    private var isHoneywellConnected = false

    // Honeywell vendor IDs
    private val HONEYWELL_VENDOR_IDS = listOf(
        0x0c2e, // Honeywell Scanning and Mobility
        0x0536, // Hand Held Products (Honeywell)
        0x04b4, // Cypress (used in some Honeywell devices)
        0x1eab, // Honeywell International (including MS5145)
        0x0504  // Another Honeywell ID
    )

    private val barcodeCompleteRunnable = Runnable {
        if (barcodeBuffer.isNotEmpty()) {
            val barcode = barcodeBuffer.toString()
            Log.d(TAG, "Processing completed barcode: $barcode")
            sendBarcodeToWebView(barcode)
            barcodeBuffer.clear()
        }
    }

    // USB connection/disconnection receiver
    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.action?.let { action ->
                when (action) {
                    UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                        val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                        if (isHoneywellDevice(device)) {
                            Log.d(TAG, "Honeywell scanner attached, searching for input device")
                            isHoneywellConnected = true
                            findAndOpenInputDevice()
                        }
                    }
                    UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                        val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                        if (isHoneywellDevice(device)) {
                            Log.d(TAG, "Honeywell scanner detached")
                            isHoneywellConnected = false
                            stopInputReaders()
                        }
                    }
                }
            }
        }
    }

    init {
        // Register for USB device events
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        contextRef.get()?.registerReceiver(usbReceiver, filter)

        // Check for already connected devices
        checkForHoneywellDevice()
        findAndOpenInputDevice()

        Log.d(TAG, "HoneywellKeyboardHandler initialized")
    }

    /**
     * Check if the device is a Honeywell scanner based on vendor ID
     */
    private fun isHoneywellDevice(device: UsbDevice?): Boolean {
        if (device == null) return false

        // Log all device info for debugging
        Log.d(TAG, "USB Device: ${device.deviceName}, VendorID: 0x${device.vendorId.toString(16)}, ProductID: 0x${device.productId.toString(16)}")

        return HONEYWELL_VENDOR_IDS.contains(device.vendorId)
    }

    private fun isHoneywellDevice(device: File): Boolean {
        try {
            // Získání jména zařízení z /proc/bus/input/devices
            // Předpokládáme, že soubor se jmenuje např. "eventX", kde X je číslo
            // a v souboru /proc/bus/input/devices najdeme blok informací pro každé zařízení.
            val process = ProcessBuilder("grep", "-B", "5", device.name, "/proc/bus/input/devices")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()

            // Hledáme klíčové slovo "Honeywell" v textu
            if (output.contains("Honeywell", ignoreCase = true)) {
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Chyba při čtení informací o zařízení: ${device.absolutePath}", e)
        }
        return false
    }


    /**
     * Check for already connected Honeywell device
     */
    private fun checkForHoneywellDevice() {
        val context = contextRef.get() ?: return
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

        for (device in usbManager.deviceList.values) {
            if (isHoneywellDevice(device)) {
                Log.d(TAG, "Found connected Honeywell device: ${device.deviceName}")
                isHoneywellConnected = true
                findAndOpenInputDevice()
                break
            }
        }
    }

    /**
     * Find and open Linux input device for Honeywell scanner
     */
    private fun findAndOpenInputDevice() {
        try {
            val inputDir = File("/dev/input")
            val inputDevices = inputDir.listFiles { file ->
                file.name.startsWith("event")
            } ?: emptyArray()

            Log.d(TAG, "Scanning ${inputDevices.size} input devices for Honeywell scanner")

            // Stop any existing readers
            stopInputReaders()

            // Start readers for all input devices - we'll filter scans later
            for (device in inputDevices) {
                if (isHoneywellDevice(device)) {
                    Log.d(TAG, "Starting reader for: ${device.absolutePath}")
                    startDeviceReader(device.absolutePath)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding input device", e)
        }
    }

    private fun startDeviceReader(devicePath: String) {
        val readerThread = Thread {
            try {
                val buffer = ByteArray(24) // Linux input_event structure size
                val inputStream = FileInputStream(devicePath)

                Log.d(TAG, "Reader started for $devicePath")

                while (!Thread.currentThread().isInterrupted) {
                    try {
                        // Read input event
                        val bytesRead = inputStream.read(buffer)
                        if (bytesRead == 24) { // Complete input_event structure
                            // Parse Linux input_event (simplified)
                            val type = ((buffer[16].toInt() and 0xFF) or ((buffer[17].toInt() and 0xFF) shl 8))
                            val code = ((buffer[18].toInt() and 0xFF) or ((buffer[19].toInt() and 0xFF) shl 8))
                            val value = ((buffer[20].toInt() and 0xFF)
                                    or ((buffer[21].toInt() and 0xFF) shl 8)
                                    or ((buffer[22].toInt() and 0xFF) shl 16)
                                    or ((buffer[23].toInt() and 0xFF) shl 24))

                            // Process keyboard events
                            // EV_KEY is 1, key press is 1, key release is 0
                            if (type == 1 && value == 1) { // Key press
                                Log.d(TAG, "Key press from $devicePath: $code")
                                honeywellDevicePath = devicePath // Remember this path if we're getting key events
                                handleKeyPress(code)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error reading from $devicePath", e)
                        break
                    }
                }

                inputStream.close()

            } catch (e: Exception) {
                Log.e(TAG, "Error opening $devicePath", e)
            }
        }

        readerThread.start()
        inputReaders.add(readerThread)
    }

    private fun stopInputReaders() {
        for (thread in inputReaders) {
            thread.interrupt()
        }
        inputReaders.clear()
    }

    // Convert Linux key codes to ASCII
    private fun handleKeyPress(keyCode: Int) {
        val currentTime = System.currentTimeMillis()

        // Convert Linux key codes to ASCII (simplified mapping)
        val char = when (keyCode) {
            // Numbers
            in 2..10 -> '1' + (keyCode - 2) // 1-9
            11 -> '0'  // 0

            // Letters
            in 16..25 -> 'q' + (keyCode - 16) // q-p
            in 30..38 -> 'a' + (keyCode - 30) // a-l
            in 44..50 -> 'z' + (keyCode - 44) // z-m

            // Special keys
            28 -> '\n'  // Enter
            57 -> ' '   // Space
            14 -> '\b'  // Backspace

            // Handle other keys as needed
            else -> null
        }

        // Process the character if it's valid
        if (char != null) {
            // Send to UI thread
            inputHandler.post {
                processCharacter(char, currentTime)
            }
        }
    }

    private fun processCharacter(char: Char, timestamp: Long) {
        // Reset any pending timeouts
        inputHandler.removeCallbacks(barcodeCompleteRunnable)

        // Handle special characters
        if (char == '\n') {
            // Enter key pressed, process buffer as barcode
            if (barcodeBuffer.isNotEmpty()) {
                val barcode = barcodeBuffer.toString()
                Log.d(TAG, "Processing barcode (Enter): $barcode")
                sendBarcodeToWebView(barcode)
                barcodeBuffer.clear()
            }
            return
        }

        // Handle backspace
        if (char == '\b' && barcodeBuffer.isNotEmpty()) {
            barcodeBuffer.deleteCharAt(barcodeBuffer.length - 1)
            return
        }

        // Add character to buffer
        barcodeBuffer.append(char)
        Log.d(TAG, "Buffer: $barcodeBuffer")

        // Schedule processing after timeout
        inputHandler.postDelayed(barcodeCompleteRunnable, BARCODE_TIMEOUT)

        lastInputTime = timestamp
    }

    /**
     * Send barcode data to WebView
     */
    private fun sendBarcodeToWebView(barcode: String) {
        val webView = webViewRef.get() ?: return
        val context = contextRef.get() ?: return
        val escapedBarcode = barcode.replace("\\", "\\\\").replace("'", "\\'")

        // Ensure execution on UI thread
        if (context is AppCompatActivity) {
            context.runOnUiThread {
                // Ensure WebView has focus
                webView.requestFocus()

                // Send to processBarcode function if it exists
                webView.evaluateJavascript(
                    "if(typeof processBarcode === 'function') { " +
                            "  processBarcode('$escapedBarcode'); " +
                            "  true; " +
                            "} else { " +
                            "  console.log('Received barcode: $escapedBarcode'); " +
                            "  false; " +
                            "}",
                    { result ->
                        Log.d(TAG, "Barcode processed by webpage: $result")
                    }
                )
            }
        }
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        inputHandler.removeCallbacksAndMessages(null)
        stopInputReaders()

        try {
            contextRef.get()?.unregisterReceiver(usbReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }
    }
}