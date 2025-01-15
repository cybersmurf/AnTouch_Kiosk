package cz.emistr.antouchkiosk

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.SharedPreferences
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.os.Build
import android.widget.*
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.compose.material.*
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader

class SettingsActivity : AppCompatActivity() {
    private lateinit var preferences: SharedPreferences
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var urlEditText: EditText
    private lateinit var barcodePortSpinner: Spinner
    private lateinit var rfidPortSpinner: Spinner
    private lateinit var kioskModeCheckbox: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        preferences = getSharedPreferences("KioskPreferences", MODE_PRIVATE)
        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        setupViews()
        loadSettings()
        setupPortSpinners()

        // Show password dialog when disabling kiosk mode
        kioskModeCheckbox.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked) {
                showPasswordDialog()
            }
        }
    }

    private fun setupViews() {
        urlEditText = findViewById(R.id.urlEditText)
        barcodePortSpinner = findViewById(R.id.barcodePortSpinner)
        rfidPortSpinner = findViewById(R.id.rfidPortSpinner)
        kioskModeCheckbox = findViewById(R.id.kioskModeCheckbox)

        findViewById<Button>(R.id.saveButton).setOnClickListener {
            saveSettings()
        }
    }

    private fun loadSettings() {
        urlEditText.setText(preferences.getString("default_url", ""))
        kioskModeCheckbox.isChecked = preferences.getBoolean("kiosk_mode", false)
    }

    private fun setupPortSpinners() {
        val ports = detectAvailablePorts()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, ports)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        barcodePortSpinner.adapter = adapter
        rfidPortSpinner.adapter = adapter

        val savedBarcodePort = preferences.getString("barcode_port", "")
        val savedRfidPort = preferences.getString("rfid_port", "")

        ports.indexOf(savedBarcodePort).takeIf { it != -1 }?.let {
            barcodePortSpinner.setSelection(it)
        }
        ports.indexOf(savedRfidPort).takeIf { it != -1 }?.let {
            rfidPortSpinner.setSelection(it)
        }
    }

    private fun detectAvailablePorts(): List<String> {
        val ports = mutableListOf<String>()

        File("/dev").list()?.forEach { fileName ->
            if (fileName.startsWith("ttyS") || fileName.startsWith("ttyUSB")) {
                ports.add("/dev/$fileName")
            }
        }

        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        val deviceList = usbManager.deviceList
        deviceList.values.forEach { device ->
            ports.add("USB: ${device.deviceName}")
        }

        return ports
    }

    private fun saveSettings() {
        preferences.edit().apply {
            putString("default_url", urlEditText.text.toString())
            putString("barcode_port", barcodePortSpinner.selectedItem?.toString())
            putString("rfid_port", rfidPortSpinner.selectedItem?.toString())
            putBoolean("kiosk_mode", kioskModeCheckbox.isChecked)
        }.apply()

        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
    }

    private fun showPasswordDialog() {
        setContent {
            var showDialog by remember { mutableStateOf(true) }

            if (showDialog) {
                PasswordDialog(
                    onDismiss = { showDialog = false; kioskModeCheckbox.isChecked = true },
                    onConfirm = { password ->
                        if (password == "9009") {
                            disableKioskMode()
                        } else {
                            Toast.makeText(this, "Incorrect password", Toast.LENGTH_SHORT).show()
                            kioskModeCheckbox.isChecked = true
                        }
                        showDialog = false
                    }
                )
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun disableKioskMode() {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        if (activityManager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE) {
            stopLockTask()
            preferences.edit().putBoolean("kiosk_mode", false).apply()
            Toast.makeText(this, "Kiosk mode disabled", Toast.LENGTH_SHORT).show()
        }
    }

    @Composable
    fun PasswordDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
        var password by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { /* Do nothing to make it modal */ },
            title = { Text(text = "Enter Password") },
            text = {
                TextField(
                    value = password,
                    onValueChange = { password = it },
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text("Password") }
                )
            },
            confirmButton = {
                Button(onClick = { onConfirm(password) }) {
                    Text("OK")
                }
            },
            dismissButton = {
                Button(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    }

    @Preview
    @Composable
    fun PreviewPasswordDialog() {
        PasswordDialog(onDismiss = {}, onConfirm = {})
    }
}