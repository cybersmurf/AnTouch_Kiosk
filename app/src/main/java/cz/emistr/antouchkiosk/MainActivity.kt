package cz.emistr.antouchkiosk

import BeaconScanner
import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.AlertDialog
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.MenuItem
import android.webkit.*
import android.widget.EditText
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import android_serialport_api.SerialPort
import tp.xmaihh.serialport.SerialHelper
import tp.xmaihh.serialport.bean.ComBean

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener, FingerprintManager.FingerprintManagerCallback {

    private lateinit var webView: WebView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var preferences: SharedPreferences
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var componentName: ComponentName
    private lateinit var fingerprintManager: FingerprintManager
    private var serialHelper: SerialHelper? = null

    private val ACTION_USB_PERMISSION = "cz.emistr.antouchkiosk.USB_PERMISSION"
    private var usbReceiver: BroadcastReceiver? = null
    private val CAMERA_PERMISSION_CODE = 100

    @RequiresApi(Build.VERSION_CODES.P)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        componentName = ComponentName(this, MyDeviceAdminReceiver::class.java)

        if (devicePolicyManager.isDeviceOwnerApp(packageName)) {
            devicePolicyManager.setLockTaskPackages(componentName, arrayOf(packageName))
        } else {
            Toast.makeText(this, "App is not set as device owner", Toast.LENGTH_SHORT).show()
        }

        setContentView(R.layout.activity_main)
        preferences = getSharedPreferences("KioskPreferences", MODE_PRIVATE)
        fingerprintManager = (application as AntouchKioskApp).fingerprintManager
        fingerprintManager.initialize()

        setupWebView()
        setupDrawer()
        setupUSBReceiver()
        startFingerprintReader()

        if (preferences.getBoolean("kiosk_mode", false)) {
            startLockTask()
        }
        startListeningToPorts()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView = findViewById(R.id.webView)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            setSupportMultipleWindows(false)
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            displayZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            allowFileAccess = true
            allowContentAccess = true
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                handler?.proceed()
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url.toString()
                if (url.startsWith("app://config")) {
                    showPasswordDialogForFingerprintActivity()
                    return true
                }
                return super.shouldOverrideUrlLoading(view, request)
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.let {
                    if (it.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
                        if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                            ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE)
                        } else {
                            it.grant(it.resources)
                        }
                    }
                }
            }
        }
        val defaultUrl = preferences.getString("default_url", "https://example.com")
        webView.loadUrl(defaultUrl ?: "https://example.com")
    }

    private fun setupDrawer() {
        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.nav_view)
        navigationView.setNavigationItemSelectedListener(this)
    }

    override fun onResume() {
        super.onResume()
        fingerprintManager.setCallback(this)
    }

    override fun onPause() {
        super.onPause()
        fingerprintManager.removeCallback()
    }

    override fun onDestroy() {
        usbReceiver?.let { unregisterReceiver(it) }
        serialHelper?.close()
        super.onDestroy()
    }

    private fun startFingerprintReader() {
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        val zktecoDevice = usbManager.deviceList.values.find { it.vendorId == 6997 }
        if (zktecoDevice == null) {
            onError("Čtečka otisků prstů ZKTeco nebyla nalezena.")
            return
        }
        if (!usbManager.hasPermission(zktecoDevice)) {
            val permissionIntent = PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE)
            usbManager.requestPermission(zktecoDevice, permissionIntent)
        } else {
            fingerprintManager.connect(zktecoDevice)
        }
    }

    private fun setupUSBReceiver() {
        usbReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == ACTION_USB_PERMISSION) {
                    val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) && device != null) {
                        fingerprintManager.connect(device)
                    } else {
                        onError("Oprávnění pro USB zařízení zamítnuto.")
                    }
                }
            }
        }
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        ContextCompat.registerReceiver(this, usbReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    // --- FingerprintManagerCallback Implementace ---
    override fun onDeviceConnected(deviceInfo: String) {
        runOnUiThread { Toast.makeText(this, "Čtečka otisků prstů připojena.", Toast.LENGTH_SHORT).show() }
    }

    override fun onDeviceDisconnected() {
        runOnUiThread { Toast.makeText(this, "Čtečka otisků prstů odpojena.", Toast.LENGTH_SHORT).show() }
    }

    override fun onIdentificationComplete(user: FingerprintUser?, score: Int) {
        if (user != null) {
            runOnUiThread {
                webView.evaluateJavascript("ProcessWorkerData('${user.workerId}')", null)
                Toast.makeText(this, "Identifikován: ${user.name}", Toast.LENGTH_SHORT).show()
            }
        } else {
            runOnUiThread {
                Toast.makeText(this, "Otisk nerozpoznán.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onError(error: String) {
        runOnUiThread { Toast.makeText(this, "Chyba čtečky: $error", Toast.LENGTH_LONG).show() }
    }

    override fun onFingerprintCaptured(bitmap: Bitmap?) {}
    override fun onRegistrationProgress(step: Int, totalSteps: Int) {}
    override fun onRegistrationComplete(userId: String) {}
    override fun onRegistrationFailed(error: String) {}

    private fun startListeningToPorts() {
        val barcodePort = preferences.getString("barcode_port", "")
        val barcodeSpeed = preferences.getString("barcode_speed", "9600")?.toInt() ?: 9600

        if (!barcodePort.isNullOrEmpty()) {
            configureAndReadSerialPort(barcodePort, barcodeSpeed, "processBarcode")
        }
    }

    private fun configureAndReadSerialPort(portName: String, portSpeed: Int, jsMethod: String) {
        try {
            serialHelper = object : SerialHelper(portName, portSpeed) {
                override fun onDataReceived(comBean: ComBean) {
                    val data = String(comBean.bRec).trim()
                    runOnUiThread {
                        webView.evaluateJavascript("$jsMethod('$data')", null)
                    }
                }
            }
            serialHelper?.open()
        } catch (e: Exception) {
            Log.e("SerialPort", "Error opening port $portName", e)
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_home -> {
                val homeUrl = preferences.getString("default_url", "https://example.com")
                webView.loadUrl(homeUrl ?: "https://example.com")
            }
            R.id.nav_settings -> startActivity(Intent(this, SettingsActivity::class.java))
            R.id.nav_fingerprint -> showPasswordDialogForFingerprintActivity()
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun showPasswordDialogForFingerprintActivity() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Zadejte heslo")

        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        builder.setView(input)

        builder.setPositiveButton("OK") { dialog, _ ->
            val password = input.text.toString()
            if (password == "9009") {
                startActivity(Intent(this, FingerprintActivity::class.java))
            } else {
                Toast.makeText(this, "Nesprávné heslo", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("Zrušit") { dialog, _ -> dialog.cancel() }

        builder.show()
    }
}