package cz.emistr.antouchkiosk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import java.lang.ref.WeakReference

/**
 * Handler class for USB keyboard input in kiosk mode
 * With special handling for Honeywell barcode scanners
 */
class UsbKeyboardHandler(context: Context, webView: WebView) {
    private val TAG = "UsbKeyboardHandler"
    private val contextRef = WeakReference(context)
    private val webViewRef = WeakReference(webView)

    private val focusHandler = Handler(Looper.getMainLooper())
    private var isKeyboardAttached = false
    private var isHoneywellAttached = false

    // Regular keyboard input buffer
    private val inputBuffer = StringBuilder()
    private var lastInputTime = 0L
    private val BUFFER_TIMEOUT = 1000L

    // Honeywell scanner input buffer
    private val honeywellBuffer = StringBuilder()
    private var lastHoneywellKeyTime = 0L
    private val HONEYWELL_SCAN_TIMEOUT = 500L // Timeout to complete a scan

    // Honeywell vendor IDs
    private val HONEYWELL_VENDOR_IDS = listOf(
        0x0c2e, // Honeywell Scanning and Mobility
        0x0536, // Hand Held Products (Honeywell)
        0x04b4  // Cypress (used in some Honeywell devices)
    )

    // The runnable that periodically checks and maintains focus
    private val focusCheckRunnable = object : Runnable {
        override fun run() {
            ensureWebViewFocus()
            // Schedule the next focus check
            focusHandler.postDelayed(this, 2000) // Check every 2 seconds
        }
    }

    /**
     * Ensure the WebView has focus for keyboard input
     */
    private fun ensureWebViewFocus() {
        val webView = webViewRef.get() ?: return

        if (!webView.hasFocus()) {
            Log.d(TAG, "Requesting focus for WebView")
            webView.bringToFront()
            webView.requestFocus()
            webView.bringToFront()
        }
    }

    // The runnable that processes the regular buffer after a timeout
    private val bufferTimeoutRunnable = Runnable {
        processBufferedInput()
    }

    // The runnable that completes a Honeywell scan
    private val honeywellScanCompleteRunnable = Runnable {
        if (honeywellBuffer.isNotEmpty()) {
            val barcode = honeywellBuffer.toString()
            Log.d(TAG, "Processing completed Honeywell barcode: $barcode")
            sendBarcodeToWebView(barcode)
            honeywellBuffer.clear()
        }
    }

    // USB connection/disconnection receiver
    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.action?.let { action ->
                when (action) {
                    UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                        val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                        checkDeviceType(device)
                    }
                    UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                        val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                        if (isHoneywellDevice(device)) {
                            Log.d(TAG, "Honeywell scanner detached: ${device?.deviceName}")
                            isHoneywellAttached = false
                        } else if (isKeyboardDevice(device)) {
                            Log.d(TAG, "Keyboard detached: ${device?.deviceName}")
                            isKeyboardAttached = false
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
        checkForConnectedDevices()

        // Start periodic focus checking
        focusHandler.post(focusCheckRunnable)

        Log.d(TAG, "UsbKeyboardHandler initialized")
    }

    /**
     * Check what type of device this is (Honeywell scanner or regular keyboard)
     */
    private fun checkDeviceType(device: UsbDevice?) {
        if (isHoneywellDevice(device)) {
            Log.d(TAG, "Honeywell scanner attached: ${device?.deviceName}, VendorID: 0x${device?.vendorId?.toString(16)}")
            isHoneywellAttached = true
            ensureWebViewFocus()
        } else if (isKeyboardDevice(device)) {
            Log.d(TAG, "Keyboard attached: ${device?.deviceName}")
            isKeyboardAttached = true
            ensureWebViewFocus()
        }
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

    /**
     * Check if the device is a regular keyboard (excluding Honeywell scanners)
     */
    private fun isKeyboardDevice(device: UsbDevice?): Boolean {
        if (device == null) return false

        // Skip if it's a Honeywell device
        if (isHoneywellDevice(device)) return false

        // Check device class - HID devices (class 3) include keyboards
        if (device.deviceClass == 3) return true

        // Check interface class for composite devices
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            if (intf.interfaceClass == 3) {
                // For HID interfaces, check if it's likely a keyboard
                // Keyboards typically use interface subclass 1 and protocol 1
                if (intf.interfaceSubclass == 1 && intf.interfaceProtocol == 1) {
                    return true
                }
            }
        }

        // Check known keyboard manufacturers (VID)
        val vendorId = device.vendorId
        val knownKeyboardVendors = listOf(
            0x04d9, // Holtek (many keyboards)
            0x046d, // Logitech
            0x05ac, // Apple
            0x045e, // Microsoft
            0x0461, // Cherry
            0x04f2  // Chicony
        )

        return knownKeyboardVendors.contains(vendorId)
    }

    /**
     * Check for already connected USB devices
     */
    private fun checkForConnectedDevices() {
        val context = contextRef.get() ?: return
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

        for (device in usbManager.deviceList.values) {
            checkDeviceType(device)
        }
    }

    /**
     * Handle key events from the activity
     * @return true if the event was handled, false otherwise
     */
    fun handleKeyEvent(event: KeyEvent): Boolean {
        // Handle Honeywell scanner input specially
        if (isHoneywellAttached) {
            return handleHoneywellEvent(event)
        }

        // Handle regular keyboard input
        if (isKeyboardAttached) {
            return handleRegularKeyboardEvent(event)
        }

        return false
    }

    /**
     * Handle input from Honeywell scanner, collecting characters into complete barcodes
     */
    private fun handleHoneywellEvent(event: KeyEvent): Boolean {
        // Only process key down events to avoid duplicates
        if (event.action != KeyEvent.ACTION_DOWN) return false

        val currentTime = System.currentTimeMillis()

        // For Enter key, which typically signals the end of a barcode scan
        if (event.keyCode == KeyEvent.KEYCODE_ENTER) {
            if (honeywellBuffer.isNotEmpty()) {
                focusHandler.removeCallbacks(honeywellScanCompleteRunnable)
                val barcode = honeywellBuffer.toString()
                Log.d(TAG, "Processing Honeywell barcode on Enter: $barcode")
                sendBarcodeToWebView(barcode)
                honeywellBuffer.clear()
                return true
            }
            return false
        }

        // Handle character input
        val unicodeChar = event.unicodeChar
        if (unicodeChar > 0) {
            // Add character to buffer
            honeywellBuffer.append(unicodeChar.toChar())
            Log.d(TAG, "Added to Honeywell buffer: ${unicodeChar.toChar()}, current buffer: $honeywellBuffer")

            // Cancel any pending processing
            focusHandler.removeCallbacks(honeywellScanCompleteRunnable)

            // Schedule processing after timeout
            focusHandler.postDelayed(honeywellScanCompleteRunnable, HONEYWELL_SCAN_TIMEOUT)

            lastHoneywellKeyTime = currentTime
            return true
        }

        return false
    }

    /**
     * Handle regular keyboard input
     */
    private fun handleRegularKeyboardEvent(event: KeyEvent): Boolean {
        // Only process key down events to avoid duplicates
        if (event.action != KeyEvent.ACTION_DOWN) return false

        val currentTime = System.currentTimeMillis()

        // Reset the buffer timeout
        focusHandler.removeCallbacks(bufferTimeoutRunnable)

        when (event.keyCode) {
            KeyEvent.KEYCODE_ENTER -> {
                // Process the buffer when Enter is pressed
                inputBuffer.toString().trim().takeIf { it.isNotEmpty() }?.let { input ->
                    sendToWebView(input)
                    inputBuffer.clear()
                }
                return true
            }
            KeyEvent.KEYCODE_ESCAPE -> {
                // Clear the buffer when Escape is pressed
                inputBuffer.clear()
                return true
            }
            else -> {
                // If this is a character key, add it to the buffer
                val unicodeChar = event.unicodeChar
                if (unicodeChar > 0) {
                    inputBuffer.append(unicodeChar.toChar())

                    // Schedule buffer timeout
                    focusHandler.postDelayed(bufferTimeoutRunnable, BUFFER_TIMEOUT)
                }
            }
        }

        lastInputTime = currentTime
        return false
    }

    /**
     * Process the current contents of the regular input buffer
     */
    private fun processBufferedInput() {
        inputBuffer.toString().trim().takeIf { it.isNotEmpty() }?.let { input ->
            sendToWebView(input)
            inputBuffer.clear()
        }
    }

    /**
     * Send regular keyboard input to the WebView
     */
    private fun sendToWebView(input: String) {
        val webView = webViewRef.get() ?: return
        val escapedInput = input.replace("\\", "\\\\").replace("'", "\\'")

        // Ensure WebView has focus
        ensureWebViewFocus()

        // Send to regular input handling
        webView.evaluateJavascript(
            "if(typeof processKeyboardInput === 'function') { " +
                    "  processKeyboardInput('$escapedInput'); " +
                    "} else { " +
                    "  console.log('Received keyboard input: $escapedInput'); " +
                    "}",
            null
        )
    }

    /**
     * Send barcode data directly to WebView with focus verification
     */
    private fun sendBarcodeToWebView(barcode: String) {
        val webView = webViewRef.get() ?: return
        val escapedBarcode = barcode.replace("\\", "\\\\").replace("'", "\\'")

        // First ensure WebView has focus
        ensureWebViewFocusWithCallback {
            // Only send data once we confirm focus is active
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

    /**
     * Ensure the WebView has focus and is active before proceeding
     * @param callback Function to run once focus is confirmed
     */
    private fun ensureWebViewFocusWithCallback(callback: () -> Unit) {
        val webView = webViewRef.get() ?: return

        if (!webView.hasFocus()) {
            Log.d(TAG, "Requesting focus for WebView")

            // Request focus on UI thread
            val context = contextRef.get()
            if (context is AppCompatActivity) {
                context.runOnUiThread {
                    webView.requestFocus()

                    // Verify focus after a slight delay
                    focusHandler.postDelayed({
                        if (webView.hasFocus()) {
                            Log.d(TAG, "WebView focus confirmed, proceeding with callback")
                            callback()
                        } else {
                            Log.d(TAG, "WebView still not focused, trying again")
                            ensureWebViewFocusWithCallback(callback)
                        }
                    }, 100) // Short delay to ensure focus is processed
                }
            }
        } else {
            // WebView already has focus, proceed immediately
            callback()
        }
    }

    /**
     * Clean up resources when no longer needed
     */
    fun cleanup() {
        focusHandler.removeCallbacksAndMessages(null)
        try {
            contextRef.get()?.unregisterReceiver(usbReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }
    }
}