package cz.emistr.antouchkiosk

import BeaconScanner
import tp.xmaihh.serialport.SerialHelper

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager

import android.Manifest
import android.content.pm.PackageManager
import android.webkit.PermissionRequest
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.OutputStream
import android.webkit.WebChromeClient
import android.webkit.WebSettings

import android_serialport_api.SerialPort
import tp.xmaihh.serialport.bean.ComBean
import tp.xmaihh.serialport.utils.ByteUtil
import android_serialport_api.SerialPortFinder
import java.io.InputStream
import java.nio.ByteBuffer

object AppConfig {
    const val VERSION_NAME = "1.0.0"
}

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    private lateinit var webView: WebView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var preferences: SharedPreferences
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var componentName: ComponentName

    // USB Keyboard Handler

    private lateinit var serialPortFinder: SerialPortFinder
    private lateinit var serialHelper: SerialHelper
    private lateinit var serialHelper3: SerialHelper

    private val CAMERA_PERMISSION_CODE = 100
    private val REQUEST_IMAGE_CAPTURE = 102
    private val BLUETOOTH_PERMISSIONS_REQUEST_CODE = 2001

    private var beaconScanner: BeaconScanner? = null
    private val SCAN_INTERVAL_MS: Long = 15000 // 30 sekund mezi skeny
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())



    @RequiresApi(Build.VERSION_CODES.P)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializace Device Policy Manageru
        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        componentName = ComponentName(this, MyDeviceAdminReceiver::class.java)

        // Ensure the app is the device owner before setting lock task packages
        if (devicePolicyManager.isDeviceOwnerApp(packageName)) {
            devicePolicyManager.setLockTaskPackages(componentName, arrayOf(packageName))
        } else {
            Toast.makeText(this, "App is not set as device owner", Toast.LENGTH_SHORT).show()
        }

        setContentView(R.layout.activity_main)

        preferences = getSharedPreferences("KioskPreferences", MODE_PRIVATE)

        setupWebView()
        setupDrawer()
        //setupUsbKeyboardHandler() // Add USB keyboard support
        //setupLinuxInputDevices()  // Add this line
        //setupHoneywellHandler();
        checkAndRequestBluetoothPermissions()

        // Aktivace kioskového režimu, pokud je nastaven
        if (preferences.getBoolean("kiosk_mode", false)) {
            startLockTask()
        }

        // Start listening to barcode and RFID ports
        startListeningToPorts()

        // Set the application version in the navigation drawer header
        //setAppVersion()
    }

    private fun setupHoneywellHandler() {
        //honeywellHandler = HoneywellKeyboardHandler(this, webView)
    }

    private fun setupUsbKeyboardHandler() {
        //usbKeyboardHandler = UsbKeyboardHandler(this, webView)

        // Add keyboard JavaScript interface to the WebView
        webView.evaluateJavascript(
            """
            if (!window.keyboardHandlerInitialized) {
                window.keyboardHandlerInitialized = true;
                
                // Add a global function to handle keyboard input
                window.processKeyboardInput = function(input, isBarcode) {
                    console.log('Input received: ' + input + (isBarcode ? ' (barcode)' : ''));
                    
                    // If this is detected as a barcode scan, prioritize the barcode handler
                    if (isBarcode) {
                        if (typeof processBarcode === 'function') {
                            processBarcode(input);
                            return;
                        }
                    }
                    
                    // Find any focused input element
                    var activeElement = document.activeElement;
                    if (activeElement && 
                        (activeElement.tagName === 'INPUT' || 
                         activeElement.tagName === 'TEXTAREA')) {
                        // Insert text at cursor position
                        var start = activeElement.selectionStart || 0;
                        var end = activeElement.selectionEnd || 0;
                        var value = activeElement.value || '';
                        
                        activeElement.value = value.substring(0, start) + 
                                              input + 
                                              value.substring(end);
                        
                        // Move cursor to end of inserted text
                        activeElement.selectionStart = activeElement.selectionEnd = 
                            start + input.length;
                        
                        // Trigger input event for frameworks that listen to it
                        var event = new Event('input', { bubbles: true });
                        activeElement.dispatchEvent(event);
                    } else {
                        // No input element focused, try to find one
                        var inputs = document.querySelectorAll(
                            'input[type="text"], input[type="search"], input:not([type])');
                        if (inputs.length > 0) {
                            inputs[0].focus();
                            inputs[0].value = input;
                            var event = new Event('input', { bubbles: true });
                            inputs[0].dispatchEvent(event);
                        }
                    }
                };
            }
            """, null
        )
    }

    private fun setAppVersion() {
        val versionTextView: TextView = findViewById(R.id.versionTextView)
        val versionName = AppConfig.VERSION_NAME
        versionTextView.text = getString(R.string.version_format, versionName)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onResume() {
        super.onResume()
        // Kontrola a případné obnovení Lock Task Mode
        if (preferences.getBoolean("kiosk_mode", false) &&
            !isInLockTaskMode()) {
            startLockTask()
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun isInLockTaskMode(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return activityManager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun checkAndRequestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12 a novější - potřebujeme BLUETOOTH_CONNECT a BLUETOOTH_SCAN
            val permissions = arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )

            val permissionsToRequest = permissions.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }.toTypedArray()

            if (permissionsToRequest.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, permissionsToRequest, BLUETOOTH_PERMISSIONS_REQUEST_CODE)
            } else {
                // Už máme oprávnění, inicializujeme beacon skenování
                initializeBeaconScanning()
            }
        } else {
            // Android 6-11 - potřebujeme BLUETOOTH, BLUETOOTH_ADMIN a možná lokaci
            val permissions = mutableListOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION  // Vždy přidat pro BLE skenování
            )

            // Pro vyhledávání zařízení na Android 6-11 potřebujeme oprávnění pro lokaci
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }

            val permissionsToRequest = permissions.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }.toTypedArray()

            if (permissionsToRequest.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, permissionsToRequest, BLUETOOTH_PERMISSIONS_REQUEST_CODE)
            } else {
                // Už máme oprávnění, inicializujeme beacon skenování
                initializeBeaconScanning()
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun setupWebView() {
        webView = findViewById(R.id.webView)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true

            // DŮLEŽITÉ: explicitní nastavení pro kameru
            mediaPlaybackRequiresUserGesture = false

            // Zakázání otevírání nových oken
            setSupportMultipleWindows(false)
            // Povolení zobrazení obsahu přes celou obrazovku
            loadWithOverviewMode = true
            useWideViewPort = true
            // Zakázání zoomu
            builtInZoomControls = false
            displayZoomControls = false
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true
        webView.settings.setPluginState(WebSettings.PluginState.ON)

        // Povolení WebRTC (nutné pro přístup ke kameře)
        with(webView.settings) {
            setWebRtcEnabled(true)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                // Zajistí, že všechny URL se otevřou v aplikaci
                url?.let { view?.loadUrl(it) }
                return true
            }

            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                Toast.makeText(this@MainActivity, "Chyba načítání: $description", Toast.LENGTH_SHORT).show()
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                handler?.proceed() // Ignore SSL certificate errors
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Test JavaScript after page load
                webView.evaluateJavascript("typeof processBarcode === 'function'", ValueCallback { result ->
                    Log.d("WebView", "processBarcode function exists: $result")
                    //webView.evaluateJavascript("processBarcode('test')", null)
                })
            }

        }

        // Načtení výchozí URL z preferences
        val defaultUrl = preferences.getString("default_url", "https://example.com")
        webView.addJavascriptInterface(WebAppInterface(this), "Android")
        webView.getSettings().setJavaScriptEnabled(true)
        webView.getSettings().setDomStorageEnabled(true)
        webView.getSettings().setAllowFileAccess(true)
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.let {
                    val resources = it.resources
                    if (resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
                        if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA)
                            != PackageManager.PERMISSION_GRANTED) {
                            ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE)
                        } else {
                            //it.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
                            it.grant(it.resources)
                        }
                    }
                }
            }

        }
        webView.loadUrl(defaultUrl ?: "https://example.com")
    }

    private fun setupDrawer() {
        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.nav_view)
        navigationView.setNavigationItemSelectedListener(this)

        // Nastavení hamburger menu ikony
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_menu) // Ujistěte se, že máte tuto ikonu v drawable
        }
    }

    // Override onKeyDown method to handle keyboard events
    @RequiresApi(Build.VERSION_CODES.M)
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Zamezit změně hlasitosti v kioskovém režimu
        if (isInLockTaskMode() && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            return true // Ignorovat stisk
        }

        // Let the UsbKeyboardHandler process the event first
        /*
        if (usbKeyboardHandler.handleKeyEvent(event)) {
            return true
        }
        */

        // If not handled by keyboard handler, use default behavior
        return super.onKeyDown(keyCode, event)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (isInLockTaskMode() && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    // Pokus o explicitní povolení WebRTC
    private fun WebSettings.setWebRtcEnabled(enabled: Boolean) {
        try {
            val field = this.javaClass.getDeclaredMethod("setWebRtcEnabled", Boolean::class.java)
            field.invoke(this, enabled)
        } catch (e: Exception) {
            Log.w(TAG, "Nelze nastavit WebRTC explicitně", e)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                drawerLayout.openDrawer(GravityCompat.START)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_home -> {
                val homeUrl = preferences.getString("default_url", "https://example.com")
                webView.loadUrl(homeUrl ?: "https://example.com")
            }
            R.id.nav_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
            }

            R.id.nav_fingerprint -> {
                startActivity(Intent(this, FingerprintActivity::class.java))
            }
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onBackPressed() {
        when {
            drawerLayout.isDrawerOpen(GravityCompat.START) -> {
                drawerLayout.closeDrawer(GravityCompat.START)
            }
            webView.canGoBack() && !isInLockTaskMode() -> {
                webView.goBack()
            }
            else -> {
                if (!isInLockTaskMode()) {
                    super.onBackPressed()
                }
            }
        }
    }

    // Sem přidejte onRequestPermissionsResult
    @RequiresApi(Build.VERSION_CODES.M)
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            CAMERA_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Kód pro oprávnění kamery
                }
            }
            BLUETOOTH_PERMISSIONS_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    Log.d("Bluetooth", "Všechna Bluetooth oprávnění byla udělena")
                    // Inicializace skenování beaconů, když jsou udělena všechna oprávnění
                    initializeBeaconScanning()
                } else {
                    Log.d("Bluetooth", "Některá Bluetooth oprávnění byla zamítnuta")
                    Toast.makeText(this, "Pro využití Bluetooth funkcí jsou potřeba příslušná oprávnění", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Metoda pro opuštění Lock Task Mode
    @RequiresApi(Build.VERSION_CODES.M)
    fun exitKioskMode() {
        if (isInLockTaskMode()) {
            stopLockTask()
        }
    }

    // Metoda pro kontrolu, zda je aplikace Device Owner
    private fun isDeviceOwner(): Boolean {
        return devicePolicyManager.isDeviceOwnerApp(packageName)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onDestroy() {
        // Cleanup the USB keyboard handler
        //usbKeyboardHandler.cleanup()
        //honeywellHandler.cleanup()

        // Ukončení Lock Task Mode při zavření aplikace
        if (isInLockTaskMode()) {
            stopLockTask()
        }

        // Zastavit skenování beaconů
        stopBeaconScanning()

        serialHelper.close();
        super.onDestroy()
    }

    private fun startListeningToPorts() {
        val barcodePort = preferences.getString("barcode_port", "")
        val barcodeSpeed = preferences.getString("barcode_speed", "9600")?.toInt() ?: 9600
        val rfidPort = preferences.getString("rfid_port", "")
        val rfidSpeed = preferences.getString("rfid_speed", "9600")?.toInt() ?: 9600

        barcodePort?.let {
            //configureComPort(it)
            //startListeningToPort(it, "processBarcode")
            //startListeningToPort(it, barcodeSpeed, "processBarcode")
            configureAndReadSerialPort(it, barcodeSpeed, "processBarcode")
        }

        rfidPort?.let {
            //configureComPort(it)
            //startListeningToPort(it, rfidSpeed, "processRfidCode")
            configureAndReadSerialPort(it, rfidSpeed, "processRfidCode")
        }

    }

    private fun startListeningToPort(port: String, speed: Int, jsMethod: String) {
        Thread {
            try {
                serialHelper = object : SerialHelper(port, speed) {
                    override fun onDataReceived(comBean: ComBean?) {
                        comBean?.let {
                            val data = if (jsMethod == "processRfidCode") {
                                it.bRec.joinToString("") { byte -> "%02X".format(byte) }
                            } else {
                                String(it.bRec)
                            }
                            runOnUiThread {
                                webView.evaluateJavascript(
                                    "$jsMethod('$data')",
                                    null
                                )
                            }
                        }
                    }
                }
                serialHelper.open()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                serialHelper.close()
            }
        }.start()
    }

    private fun startListeningToPort3(port: String, speed: Int, jsMethod: String) {
        Thread {
            try {
                serialHelper3 = object : SerialHelper(port, speed) {
                    override fun onDataReceived(comBean: ComBean?) {
                        comBean?.let {
                            val data = if (jsMethod == "processRfidCode") {
                                it.bRec.joinToString("") { byte -> "%02X".format(byte) }
                            } else {
                                String(it.bRec)
                            }
                            runOnUiThread {
                                webView.evaluateJavascript(
                                    "$jsMethod('$data')",
                                    null
                                )
                            }
                        }
                    }
                }
                serialHelper3.open()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                serialHelper3.close()
            }
        }.start()
    }

    private fun startListeningToPort2(port: String, jsMethod: String) {
        Thread {
            try {
                val fileInputStream = FileInputStream(File(port))
                val reader = InputStreamReader(fileInputStream)
                val buffer = CharArray(1024)
                var bytesRead: Int

                while (true) {
                    bytesRead = reader.read(buffer)
                    if (bytesRead > 0) {
                        val data = String(buffer, 0, bytesRead).trim()

                        runOnUiThread {
                            // Escape special characters in the data
                            val escapedData = data.replace("\\", "\\\\")
                                .replace("'", "\\'")
                            webView.evaluateJavascript(
                                "$jsMethod('$escapedData')",
                                null
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun configureAndReadSerialPort(portName: String, portSpeed: Int, jsMethod: String) {
        try {
            val serialPort = SerialPort(File(portName), portSpeed, 0, 8, 0, 0, 0)
            val inputStream: InputStream = serialPort.inputStream
            val outputStream: OutputStream = serialPort.outputStream

            Thread {
                val buffer = ByteArray(1024)
                try {
                    while (true) {
                        val bytesRead = inputStream.read(buffer)
                        if (bytesRead > 0) {
                            //val data = String(buffer, 0, bytesRead).trim()
                            val data = if (jsMethod == "processRfidCode") {
                                buffer.take(bytesRead).joinToString("") { byte -> "%02X".format(byte) }
                            } else {
                                String(buffer, 0, bytesRead).trim()
                            }
                            runOnUiThread {
                                webView.evaluateJavascript(
                                    "$jsMethod('$data')",
                                    null
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SerialPort", "Read error", e)
                } finally {
                    serialPort.close()
                }
            }.start()
        } catch (e: Exception) {
            Log.e("SerialPort", "Exception details:", e)
            e.printStackTrace()
        }
    }

    // Metoda pro inicializaci a spuštění skenování beaconů - přidejte do MainActivity
    @RequiresApi(Build.VERSION_CODES.M)
    private fun initializeBeaconScanning() {
        // Kontrola, zda máme oprávnění pro Bluetooth
        if (!hasBluetoothPermissions()) {
            Log.e("MainActivity", "Nemáme oprávnění pro Bluetooth")
            return
        }

        // Kontrola, zda je Bluetooth zapnutý
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e("MainActivity", "Bluetooth není k dispozici nebo je vypnutý")
            return
        }

        // Inicializace skeneru beaconů
        beaconScanner = BeaconScanner(this, object : BeaconScanner.BeaconScanCallback {
            override fun onBeaconFound(beacon: BeaconScanner.Beacon) {
                // Volání JavaScript funkce ve WebView
                val jsCode = "if(typeof emistrBeaconReached === 'function') { " +
                        "emistrBeaconReached('${beacon.uuid}', ${beacon.major}, ${beacon.minor}, ${beacon.rssi}); }"

                runOnUiThread {
                    webView.evaluateJavascript(jsCode, null)
                    Log.d("Beacon", "JavaScript volán s parametry: UUID=${beacon.uuid}, Major=${beacon.major}, Minor=${beacon.minor}, RSSI=${beacon.rssi}")
                }
            }
        })

        // Spustit periodické skenování beaconů
        //startPeriodicBeaconScanning()
    }

    // Metoda pro kontrolu Bluetooth oprávnění - přidejte do MainActivity
    @RequiresApi(Build.VERSION_CODES.M)
    private fun hasBluetoothPermissions(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            checkSelfPermission(android.Manifest.permission.BLUETOOTH) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(android.Manifest.permission.BLUETOOTH_ADMIN) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    // Metoda pro periodické skenování beaconů - přidejte do MainActivity
    private fun startPeriodicBeaconScanning() {
        Log.d("MainActivity", "Spouštím periodické skenování")

        // Spustit skenování
        beaconScanner?.startScanning()

        // Naplánovat další skenování po intervalu
        handler.postDelayed({
            Log.d("MainActivity", "Čas pro další skenování")

            // Zastavit aktuální skenování
            beaconScanner?.stopScanning()

            // Spustit další cyklus
            startPeriodicBeaconScanning()
        }, SCAN_INTERVAL_MS)

        Log.d("MainActivity", "Spuštěno periodické skenování beaconů")
    }

    // Metoda pro zastavení skenování - přidejte do MainActivity
    private fun stopBeaconScanning() {
        beaconScanner?.stopScanning()
        handler.removeCallbacksAndMessages(null) // Zrušit naplánované skeny
        Log.d("MainActivity", "Skenování beaconů zastaveno")
    }

    private fun setupLinuxInputDevices() {
        // Detect and monitor input devices
        val inputDir = File("/dev/input")
        val inputDevices = inputDir.listFiles { file ->
            file.name.startsWith("event")
        } ?: emptyArray()

        for (device in inputDevices) {
            Log.d("InputDevice", "Found: ${device.absolutePath}")
            startListeningToInputDevice(device.absolutePath)
        }
    }

    private fun startListeningToInputDevice(devicePath: String) {
        Thread {
            try {
                Log.d("InputDevice", "Starting to listen to $devicePath")
                val buffer = ByteArray(24)  // Standard Linux input_event structure size
                val inputStream = FileInputStream(devicePath)

                // Try to get device information
                try {
                    val deviceNameFile = File("/sys/class/input/${devicePath.substringAfterLast("/")}/device/name")
                    if (deviceNameFile.exists()) {
                        val deviceName = deviceNameFile.readText().trim()
                        Log.d("InputDevice", "Device Name: $deviceName")
                    }
                } catch (e: Exception) {
                    Log.e("InputDevice", "Error reading device name", e)
                }

                while (!Thread.currentThread().isInterrupted) {
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead == 24) { // Complete input_event structure
                        // Parse Linux input_event structure
                        val seconds = ByteBuffer.wrap(buffer, 0, 4).int
                        val microseconds = ByteBuffer.wrap(buffer, 4, 4).int
                        val type = ByteBuffer.wrap(buffer, 16, 2).short.toInt()
                        val code = ByteBuffer.wrap(buffer, 18, 2).short.toInt()
                        val value = ByteBuffer.wrap(buffer, 20, 4).int

                        // Log detailed event information
                        Log.d("InputDevice", """
                        Device: $devicePath
                        Timestamp: $seconds.${microseconds}s
                        Type: $type
                        Code: $code
                        Value: $value
                        Raw Data: ${buffer.joinToString("") { "%02X".format(it) }}
                    """.trimIndent())

                        // Event type constants
                        // EV_SYN = 0, EV_KEY = 1, EV_REL = 2, EV_ABS = 3
                        if (type == 1) { // Keyboard event
                            // Key press (value = 1), key release (value = 0)
                            val action = if (value == 1) "PRESSED" else "RELEASED"
                            Log.d("InputDevice", "Key $action: $code")

                            // Attempt to map key code to character
                            val mappedChar = mapKeyCode(code)
                            if (mappedChar != null) {
                                Log.d("InputDevice", "Mapped Character: $mappedChar")
                                runOnUiThread {
                                    processKeyboardInput(mappedChar)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("InputDevice", "Error reading from $devicePath", e)
            }
        }.start()
    }

    // Helper method to map key codes to characters
    private fun mapKeyCode(keyCode: Int): Char? {
        return when (keyCode) {
            // Number row
            in 2..10 -> '1' + (keyCode - 2)
            11 -> '0'

            // Letter rows (QWERTY layout)
            in 16..25 -> 'q' + (keyCode - 16)  // q-p
            in 30..38 -> 'a' + (keyCode - 30)  // a-l
            in 44..50 -> 'z' + (keyCode - 44)  // z-m

            // Special keys
            28 -> '\n'  // Enter
            57 -> ' '   // Space
            14 -> '\b'  // Backspace

            else -> null
        }
    }

    // Process keyboard input
    private fun processKeyboardInput(char: Char) {
        webView.evaluateJavascript(
            """
        if (typeof window.processKeyboardInput === 'function') {
            window.processKeyboardInput('$char', false);
        } else {
            console.log('Received character: $char');
        }
        """.trimIndent(),
            null
        )
    }

    /*
        private fun configureComPort(portName: String) {
            try {
                if (portName.isEmpty()) {
                    Log.e("MainActivity", "Port name is empty.")
                    return
                }

                Log.d("MainActivity", "Configuring port: $portName")

                val comPort = SerialPort.getCommPort(portName)
                comPort.baudRate = 9600
                comPort.numDataBits = 8
                comPort.numStopBits = SerialPort.ONE_STOP_BIT
                comPort.parity = SerialPort.NO_PARITY

                if (comPort.openPort()) {
                    Log.d("MainActivity", "Port $portName opened successfully.")
                } else {
                    Log.e("MainActivity", "Failed to open port $portName.")
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error configuring port $portName: ${e.message}", e)
            }
        }

     */

    inner class WebAppInterface(private val context: Context) {
        @JavascriptInterface
        fun showToast(message: String) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }

        @JavascriptInterface
        fun logMessage(message: String) {
            Log.d("WebView-JS", message)
        }

        /*
         @JavascriptInterface
         fun processBarcode(data: String) {
             webView.evaluateJavascript("javascript:processBarcode('$data');", null)
         }

         @JavascriptInterface
         fun processRfidCode(data: String) {
             webView.evaluateJavascript("javascript:processRfidCode('$data');", null)
         }

         */
    }
}