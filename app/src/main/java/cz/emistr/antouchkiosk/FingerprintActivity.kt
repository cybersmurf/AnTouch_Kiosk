package cz.emistr.antouchkiosk

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import timber.log.Timber

class FingerprintActivity : AppCompatActivity(), FingerprintManager.FingerprintManagerCallback {

    private val ACTION_USB_PERMISSION = "cz.emistr.antouchkiosk.USB_PERMISSION"

    lateinit var fingerprintManager: FingerprintManager
        private set

    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private var usbReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fingerprint)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        fingerprintManager = FingerprintManager(this, this)
        lifecycle.addObserver(fingerprintManager)
        fingerprintManager.initialize()

        viewPager = findViewById(R.id.viewPager)
        tabLayout = findViewById(R.id.tabLayout)
        viewPager.adapter = ViewPagerAdapter(this)

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Přiřazení otisků"
                1 -> "Seznam pracovníků"
                else -> null
            }
        }.attach()

        setupUSBReceiver()
        onStartClick() // Automaticky se pokusíme připojit při startu
    }

    fun onStartClick() {
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

    private fun getFragment(position: Int): Fragment? {
        return supportFragmentManager.findFragmentByTag("f$position")
    }

    // --- FingerprintManagerCallback ---
    override fun onDeviceConnected(deviceInfo: String) {
        runOnUiThread { (getFragment(0) as? FingerprintControlFragment)?.onDeviceConnected(deviceInfo) }
    }
    override fun onDeviceDisconnected() {
        runOnUiThread { (getFragment(0) as? FingerprintControlFragment)?.onDeviceDisconnected() }
    }
    override fun onFingerprintCaptured(bitmap: Bitmap?) {
        runOnUiThread { (getFragment(0) as? FingerprintControlFragment)?.onFingerprintCaptured(bitmap) }
    }
    override fun onRegistrationProgress(step: Int, totalSteps: Int) {
        runOnUiThread { (getFragment(0) as? FingerprintControlFragment)?.onRegistrationProgress(step, totalSteps) }
    }
    override fun onRegistrationComplete(userId: String) {
        runOnUiThread {
            (getFragment(0) as? FingerprintControlFragment)?.onRegistrationComplete(userId)
            (getFragment(1) as? UserListFragment)?.refreshUserList()
        }
    }
    override fun onRegistrationFailed(error: String) {
        runOnUiThread { (getFragment(0) as? FingerprintControlFragment)?.onRegistrationFailed(error) }
    }
    override fun onIdentificationResult(userId: String?, score: Int) {
        // Tuto funkci nyní v UI nevyužíváme, ale necháváme pro budoucí použití
        runOnUiThread {
            (getFragment(0) as? FingerprintControlFragment)?.onIdentificationResult(userId, score)
        }
    }
    override fun onError(error: String) {
        runOnUiThread { (getFragment(0) as? FingerprintControlFragment)?.onError(error) }
    }

    // --- Zbytek Activity ---
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
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

    override fun onDestroy() {
        super.onDestroy()
        usbReceiver?.let { unregisterReceiver(it) }
    }
}