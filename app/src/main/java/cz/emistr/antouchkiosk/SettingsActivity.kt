package cz.emistr.antouchkiosk

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.SharedPreferences
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.os.Build
import android.text.InputType
import android.widget.*
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.compose.material.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader

import android.app.AlertDialog
import android.widget.EditText

class SettingsActivity : AppCompatActivity() {
    private lateinit var preferences: SharedPreferences
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var urlEditText: EditText
    private lateinit var barcodePortSpinner: Spinner
    private lateinit var barcodeSpeedSpinner: Spinner
    private lateinit var rfidPortSpinner: Spinner
    private lateinit var rfidSpeedSpinner: Spinner
    private lateinit var kioskModeCheckbox: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        preferences = getSharedPreferences("KioskPreferences", MODE_PRIVATE)
        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        setupViews()
        loadSettings()
        setupPortSpinners()
        setupSpeedSpinners()

        // Show password dialog when disabling kiosk mode
        kioskModeCheckbox.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked) {
                ShowPasswordDialog()
            }
        }
    }

    private fun setupViews() {
        urlEditText = findViewById(R.id.urlEditText)
        barcodePortSpinner = findViewById(R.id.barcodePortSpinner)
        barcodeSpeedSpinner = findViewById(R.id.barcodeSpeedSpinner)
        rfidPortSpinner = findViewById(R.id.rfidPortSpinner)
        rfidSpeedSpinner = findViewById(R.id.rfidSpeedSpinner)
        kioskModeCheckbox = findViewById(R.id.kioskModeCheckbox)

        findViewById<Button>(R.id.saveButton).setOnClickListener {
            saveSettings()


        }
    }

    private fun loadSettings() {
        urlEditText.setText(preferences.getString("default_url", ""))
        kioskModeCheckbox.isChecked = preferences.getBoolean("kiosk_mode", false)

        urlEditText.isEnabled = !kioskModeCheckbox.isChecked;
        //urlEditText.setEnabled(!kioskModeCheckbox.isChecked);
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

    private fun setupSpeedSpinners() {
        val speeds = listOf("9600", "19200", "57600", "115200")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, speeds)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        barcodeSpeedSpinner.adapter = adapter
        rfidSpeedSpinner.adapter = adapter

        val savedBarcodeSpeed = preferences.getString("barcode_speed", "9600")
        val savedRfidSpeed = preferences.getString("rfid_speed", "9600")

        speeds.indexOf(savedBarcodeSpeed).takeIf { it != -1 }?.let {
            barcodeSpeedSpinner.setSelection(it)
        }
        speeds.indexOf(savedRfidSpeed).takeIf { it != -1 }?.let {
            rfidSpeedSpinner.setSelection(it)
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
            putString("barcode_speed", barcodeSpeedSpinner.selectedItem?.toString())
            putString("rfid_port", rfidPortSpinner.selectedItem?.toString())
            putString("rfid_speed", rfidSpeedSpinner.selectedItem?.toString())
            putBoolean("kiosk_mode", kioskModeCheckbox.isChecked)
        }.apply()

        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
    }

    private fun ShowPasswordDialog() {
        // 1. Vytvoření dialogu pomocí standardního Builderu
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Zadejte heslo")

        // 2. Vytvoření a nastavení políčka pro zadání textu
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        builder.setView(input)

        // 3. Nastavení tlačítek a jejich logiky
        builder.setPositiveButton("OK") { dialog, _ ->
            val password = input.text.toString()
            if (password == "9009") { // Heslo si můžete změnit
                // Úspěšné zadání hesla
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    disableKioskMode()
                } else {
                    // Fallback pro starší verze Androidu
                    preferences.edit().putBoolean("kiosk_mode", false).apply()
                    Toast.makeText(this, "Kiosk mode disabled", Toast.LENGTH_SHORT).show()
                    urlEditText.isEnabled = true
                }
            } else {
                // Nesprávné heslo
                Toast.makeText(this, "Nesprávné heslo", Toast.LENGTH_SHORT).show()
                kioskModeCheckbox.isChecked = true // Vrátíme checkbox do původního stavu
            }
        }

        builder.setNegativeButton("Zrušit") { dialog, _ ->
            // Uživatel zrušil akci
            kioskModeCheckbox.isChecked = true // Vrátíme checkbox do původního stavu
            dialog.cancel()
        }

        // Zobrazení dialogu
        builder.show()
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun disableKioskMode() {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        if (activityManager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE) {
            stopLockTask()
            preferences.edit().putBoolean("kiosk_mode", false).apply()
            Toast.makeText(this, "Kiosk mode disabled", Toast.LENGTH_SHORT).show()
        }
        urlEditText.isEnabled = true;
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
                    label = { Text("Password") },
                    //keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.NumberPassword)
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