package cz.emistr.antouchkiosk

import android.Manifest
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.zkteco.android.biometric.core.utils.LogHelper
import kotlinx.coroutines.launch
import timber.log.Timber

import androidx.activity.result.contract.ActivityResultContracts // Přidejte tento import
import java.io.BufferedReader // Přidejte tento import
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader // Přidejte tento import

class FingerprintActivity : AppCompatActivity(), FingerprintManager.FingerprintManagerCallback {
    private val ACTION_USB_PERMISSION = "cz.emistr.antouchkiosk.USB_PERMISSION"
    // UI Components
    private lateinit var toolbar: Toolbar
    private lateinit var statusText: TextView
    private lateinit var statusIcon: ImageView
    private lateinit var userIdEditText: TextInputEditText
    private lateinit var fingerprintImageView: ImageView
    private lateinit var imageHintText: TextView
    private lateinit var resultTextView: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var registerButton: Button
    private lateinit var identifyButton: Button
    private lateinit var deleteButton: Button
    private lateinit var clearAllButton: Button
    private lateinit var userCountTextView: TextView
    private lateinit var importButton: Button
    private lateinit var exportButton: Button
    private lateinit var backButton : Button

    private lateinit var fingerprintManager: FingerprintManager
    private var bStarted = false

    // USB broadcast receiver
    private var usbReceiver: BroadcastReceiver? = null

    // Launcher pro výběr souboru
    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val reader = BufferedReader(InputStreamReader(inputStream))
                val jsonString = reader.readText()
                inputStream?.close()

                val result = fingerprintManager.importFingerprints(jsonString)
                if (result > -1) {
                    setResult("Import proběhl úspěšně. Naimportováno $result uživatelů.")
                    updateUI()
                } else {
                    setResult("Chyba: Import se nezdařil. Zkontrolujte formát souboru.")
                }
            } catch (e: Exception) {
                setResult("Chyba při čtení souboru: ${e.message}")
                Timber.e(e, "File read error")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fingerprint)

        Timber.d("FingerprintActivity created")

        initializeViews()
        setupToolbar()
        setupBackPressedCallback()
        checkPermissions()
        setupClickListeners()
        setupUSBReceiver()

        // Initialize fingerprint manager
        initializeFingerprintManager()
    }



    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        statusText = findViewById(R.id.statusText)
        statusIcon = findViewById(R.id.statusIcon)
        userIdEditText = findViewById(R.id.userIdEditText)
        fingerprintImageView = findViewById(R.id.fingerprintImageView)
        imageHintText = findViewById(R.id.imageHintText)
        resultTextView = findViewById(R.id.resultTextView)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        registerButton = findViewById(R.id.registerButton)
        identifyButton = findViewById(R.id.identifyButton)
        deleteButton = findViewById(R.id.deleteButton)
        clearAllButton = findViewById(R.id.clearAllButton)
        userCountTextView = findViewById(R.id.userCountTextView)
        importButton = findViewById(R.id.importButton)
        exportButton = findViewById(R.id.exportButton)
        backButton = findViewById(R.id.backButton)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
    }

    private fun setupBackPressedCallback() {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (fingerprintManager.getCurrentRegistrationUser() != null) {
                    showCancelRegistrationDialog()
                } else {
                    finish()
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)
    }

    private fun showCancelRegistrationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Zrušit registraci?")
            .setMessage("Opravdu chcete zrušit probíhající registraci?")
            .setPositiveButton("Ano") { _, _ ->
                fingerprintManager.cancelRegistration()
                finish()
            }
            .setNegativeButton("Ne", null)
            .show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun checkPermissions() {
        // Seznam oprávnění, která budeme kontrolovat
        val permissionsToRequest = mutableListOf<String>()

        // Pro Android 10 a starší stále potřebujeme klasická oprávnění
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        // Pro Android 11 a novější potřebujeme speciální přístup, pokud SDK cílí na starší API
        // V tomto případě je nejjednodušší ověřit, zda je oprávnění uděleno, a pokud ne, informovat uživatele.
        // Jelikož vaše aplikace již má v manifestu MANAGE_EXTERNAL_STORAGE, systém by měl oprávnění udělit.

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                9
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 9) {
            val granted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            val message = if (granted) {
                getString(R.string.permission_granted)
            } else {
                getString(R.string.permission_denied)
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupUSBReceiver() {
        usbReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.action?.let { action ->
                    when (action) {
                        ACTION_USB_PERMISSION -> {
                            synchronized(this) {
                                // Získáme zařízení z intentu, který přišel s výsledkem oprávnění
                                val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                                } else {
                                    @Suppress("DEPRECATION")
                                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                                }

                                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                                    if (device != null) {
                                        // Oprávnění uděleno. Počkáme chvíli a PŘEDÁME ZAŘÍZENÍ.
                                        setResult("Oprávnění uděleno, připojuji za okamžik...")
                                        Handler(Looper.getMainLooper()).postDelayed({
                                            updateStatus(getString(R.string.fingerprint_connecting), true)
                                            fingerprintManager.connect(device) // <-- ZDE JE KLÍČOVÁ OPRAVA

                                            if (fingerprintManager.isDeviceConnected()) {
                                                setResult("Zařízení připojeno: ${device.deviceName}")
                                                updateUI()
                                            } else {
                                                setResult("Chyba: Zařízení nebylo připojeno.")
                                            }

                                        }, 500)
                                    } else {
                                        setResult("Chyba: Zařízení nebylo nalezeno po udělení oprávnění.")
                                    }
                                } else {
                                    setResult("Oprávnění pro USB zařízení zamítnuto.")
                                }
                            }
                        }
                        "cz.emistr.antouchkiosk.FINGERPRINT_DEVICE_ATTACHED" -> {
                            val deviceName = intent.getStringExtra("device_name")
                            setResult("ZKTeco čtečka připojena: $deviceName")
                            updateUI()
                        }
                        "cz.emistr.antouchkiosk.FINGERPRINT_DEVICE_DETACHED" -> {
                            val deviceName = intent.getStringExtra("device_name")
                            setResult("ZKTeco čtečka odpojena: $deviceName")
                            updateUI()
                        }
                        else -> {
                            // Nic neděláme
                        }
                    }
                }
            }
        }

        // Zbytek metody zůstává stejný...
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction("cz.emistr.antouchkiosk.FINGERPRINT_DEVICE_ATTACHED")
            addAction("cz.emistr.antouchkiosk.FINGERPRINT_DEVICE_DETACHED")
        }

        ContextCompat.registerReceiver(
            this,
            usbReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun initializeFingerprintManager() {
        try {
            fingerprintManager = FingerprintManager(this, this)
            lifecycle.addObserver(fingerprintManager)
            fingerprintManager.initialize()
            setResult("FingerprintManager se inicializuje...")
            updateUI()
        } catch (t: Throwable) {
            Timber.e(t, "FATAL ERROR during initialization")
            AlertDialog.Builder(this)
                .setTitle("Kritická chyba inicializace")
                .setMessage("Aplikace nemohla být spuštěna. Důvod:\n\n${t.javaClass.name}\n\n${t.message}")
                .setPositiveButton("OK") { _, _ -> finish() }
                .setCancelable(false)
                .show()
        }
    }

    private fun setupClickListeners() {
        startButton.setOnClickListener { onStartClick() }
        stopButton.setOnClickListener { onStopClick() }
        registerButton.setOnClickListener { onRegisterClick() }
        identifyButton.setOnClickListener { onIdentifyClick() }
        deleteButton.setOnClickListener { onDeleteClick() }
        clearAllButton.setOnClickListener { onClearAllClick() }
        importButton.setOnClickListener { onImportClick() }
        exportButton.setOnClickListener { onExportClick() }
        backButton.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun onExportClick() {
        if (!fingerprintManager.isDeviceConnected()) {
            setResult(getString(R.string.error_device_not_connected))
            return
        }

        val exportedData = fingerprintManager.exportDatabase()
        if (exportedData != null) {
            try {
                val downloadsDir = getExternalFilesDir(null)
                val file = File(downloadsDir, "fingerprints_export.json")
                FileOutputStream(file).use {
                    it.write(exportedData.toByteArray())
                }
                setResult("Databáze úspěšně exportována do: ${file.absolutePath}")
            } catch (e: Exception) {
                setResult("Chyba při ukládání souboru: ${e.message}")
                Timber.e(e, "File write error")
            }
        } else {
            setResult("Chyba: Export se nezdařil.")
        }
    }

    private fun onImportClick() {
        if (!fingerprintManager.isDeviceConnected()) {
            setResult(getString(R.string.error_device_not_connected))
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Potvrdit import")
            .setMessage("Import dat vymaže všechny stávající otisky v tomto zařízení. Opravdu chcete pokračovat?")
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton("Ano, importovat") { _, _ ->
                filePickerLauncher.launch("application/json")
            }
            .setNegativeButton("Zrušit", null)
            .show()
    }

    private fun onStartClick() {
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        var zktecoDevice: UsbDevice? = null
        for (device in usbManager.deviceList.values) {
            if (device.vendorId == 6997) { // Vendor ID pro ZKTeco
                zktecoDevice = device
                break
            }
        }

        if (zktecoDevice == null) {
            setResult("Čtečka otisků prstů ZKTeco nebyla nalezena.")
            return
        }

        if (!usbManager.hasPermission(zktecoDevice)) {
            val permissionIntent = PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE)
            usbManager.requestPermission(zktecoDevice, permissionIntent)
            setResult("Žádám o oprávnění pro USB zařízení...")
        } else {
            // Oprávnění již máme, rovnou se připojíme a PŘEDÁME ZAŘÍZENÍ
            updateStatus(getString(R.string.fingerprint_connected), true)
            updateUI()
            fingerprintManager.connect(zktecoDevice)
        }
    }

    private fun onStopClick() {
        if (!fingerprintManager.isDeviceConnected()) {
            setResult("Zařízení není připojeno!")
            return
        }
        fingerprintManager.disconnect()
    }

    private fun onRegisterClick() {
        if (!fingerprintManager.isDeviceConnected()) {
            setResult(getString(R.string.error_device_not_connected))
            return
        }

        val userId = userIdEditText.text.toString().trim()
        if (userId.isEmpty()) {
            setResult(getString(R.string.error_no_user_id))
            return
        }

        if (fingerprintManager.isUserAlreadyRegistered(userId)) {
            AlertDialog.Builder(this)
                .setTitle("Uživatel již existuje")
                .setMessage("Uživatel s ID '$userId' je již zaregistrován. Přejete si přepsat jeho otisk?")
                .setPositiveButton("Ano, přepsat") { _, _ ->
                    // Smažeme starý záznam a zahájíme novou registraci
                    fingerprintManager.deleteUser(userId)
                    fingerprintManager.startRegistration(userId)
                    updateUI()
                }
                .setNegativeButton("Ne, zrušit", null)
                .show()
        } else {
            // Pokud uživatel neexistuje, rovnou zahájíme registraci
            fingerprintManager.startRegistration(userId)
            updateUI()
        }
    }

    private fun onIdentifyClick() {
        if (!fingerprintManager.isDeviceConnected()) {
            setResult(getString(R.string.error_device_not_connected))
            return
        }
        setResult(getString(R.string.identification_start))
    }

    private fun onDeleteClick() {
        if (!fingerprintManager.isDeviceConnected()) {
            setResult(getString(R.string.error_device_not_connected))
            return
        }

        val userId = userIdEditText.text.toString().trim()
        if (userId.isEmpty()) {
            setResult(getString(R.string.error_no_user_id))
            return
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_delete_user_title))
            .setMessage(getString(R.string.dialog_delete_user_message, userId))
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton(getString(R.string.dialog_yes)) { _, _ ->
                lifecycleScope.launch {
                    if (fingerprintManager.deleteUser(userId)) {
                        setResult(getString(R.string.success_user_deleted))
                    } else {
                        setResult("Chyba při mazání uživatele")
                    }
                    updateUI()
                }
            }
            .setNegativeButton(getString(R.string.dialog_no), null)
            .show()
    }

    private fun onClearAllClick() {
        if (!fingerprintManager.isDeviceConnected()) {
            setResult(getString(R.string.error_device_not_connected))
            return
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_clear_all_title))
            .setMessage(getString(R.string.dialog_clear_all_message))
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton(getString(R.string.dialog_yes)) { _, _ ->
                fingerprintManager.clearAllUsers()
                setResult(getString(R.string.success_all_deleted))
                updateUI()
            }
            .setNegativeButton(getString(R.string.dialog_no), null)
            .show()
    }

    // FingerprintManager.FingerprintManagerCallback implementation
    override fun onDeviceConnected(deviceInfo: String) {
        runOnUiThread {
            updateStatus(getString(R.string.fingerprint_connected), false)
            setResult(deviceInfo)
            imageHintText.text = getString(R.string.fingerprint_place_finger)
            updateUI()
        }
    }

    override fun onDeviceDisconnected() {
        runOnUiThread {
            updateStatus(getString(R.string.fingerprint_disconnected), false)
            setResult("Zařízení odpojeno")
            imageHintText.text = getString(R.string.fingerprint_disconnected)
            fingerprintImageView.setImageResource(R.drawable.ic_fingerprint_placeholder)
            updateUI()
        }
    }

    override fun onFingerprintCaptured(bitmap: Bitmap?) {
        runOnUiThread {
            bitmap?.let {
                fingerprintImageView.setImageBitmap(it)
                imageHintText.visibility = View.GONE
            }
        }
    }

    override fun onRegistrationProgress(step: Int, totalSteps: Int) {
        runOnUiThread {
            setResult(getString(R.string.registration_step2, totalSteps - step))
            updateUI()
        }
    }

    override fun onRegistrationComplete(userId: String) {
        runOnUiThread {
            setResult(getString(R.string.success_user_registered))
            imageHintText.visibility = View.VISIBLE
            imageHintText.text = getString(R.string.fingerprint_place_finger)
            updateUI()
        }
    }

    override fun onRegistrationFailed(error: String) {
        runOnUiThread {
            setResult("Registrace selhala: $error")
            updateUI()
        }
    }

    override fun onIdentificationResult(userId: String?, score: Int) {
        runOnUiThread {
            val result = if (userId != null) {
                getString(R.string.identification_success, "$userId (skóre: $score%)")
            } else {
                getString(R.string.identification_failed)
            }
            setResult(result)
        }
    }

    override fun onError(error: String) {
        runOnUiThread {
            setResult(error)
            updateStatus(getString(R.string.fingerprint_error), false)
            Timber.e("Fingerprint error: $error")
        }
    }

    private fun updateStatus(message: String, isLoading: Boolean) {
        statusText.text = message
        if (isLoading) {
            statusIcon.setImageResource(android.R.drawable.ic_popup_sync)
        } else {
            statusIcon.setImageResource(R.drawable.ic_fingerprint_colored)
        }
    }

    private fun setResult(result: String) {
        resultTextView.text = result
        Timber.d("Result: $result")
    }

    private fun updateUI() {
        val isConnected = fingerprintManager.isDeviceConnected()
        val isRegistering = fingerprintManager.getCurrentRegistrationUser() != null
        //val userCount = fingerprintManager.getUserCount()
        val userCount = if (isConnected) fingerprintManager.getUserCount() else 0
        userCountTextView.text = "Počet uložených otisků: $userCount"

        importButton.isEnabled = isConnected && !isRegistering

        // Update button states
        startButton.isEnabled = !isConnected
        stopButton.isEnabled = isConnected
        registerButton.isEnabled = isConnected && !isRegistering
        identifyButton.isEnabled = isConnected && !isRegistering
        deleteButton.isEnabled = isConnected && !isRegistering
        clearAllButton.isEnabled = isConnected && !isRegistering && userCount > 0

        // Update registration status
        if (isRegistering) {
            val (current, total) = fingerprintManager.getRegistrationProgress()
            userIdEditText.isEnabled = false
            setResult("Registrace probíhá: krok $current z $total")
        } else {
            userIdEditText.isEnabled = true
        }

        // Update device info
        fingerprintManager.getDeviceInfo()?.let {
            // Could display in status or result
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.d("FingerprintActivity destroyed")

        // Unregister USB receiver
        usbReceiver?.let {
            unregisterReceiver(it)
        }
    }
}
