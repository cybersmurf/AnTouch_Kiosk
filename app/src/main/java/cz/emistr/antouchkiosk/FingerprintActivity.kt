package cz.emistr.antouchkiosk

import android.graphics.Bitmap
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class FingerprintActivity : AppCompatActivity(), FingerprintManager.FingerprintManagerCallback {

    lateinit var fingerprintManager: FingerprintManager
    private val sharedViewModel: SharedFingerprintViewModel by viewModels()

    private lateinit var customSelectionDialog: ConstraintLayout
    private lateinit var customConfirmDialog: ConstraintLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fingerprint)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        // ODSTRANĚNO: setSupportActionBar(toolbar) - Způsobovalo pád v NoActionBar tématu
        // ODSTRANĚNO: supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // PŘIDÁNO: Bezpečné nastavení navigace pro Toolbar
        toolbar.setNavigationOnClickListener {
            finish() // Ukončí aktivitu (stejné jako tlačítko zpět)
        }


        fingerprintManager = (application as AntouchKioskApp).fingerprintManager
        customSelectionDialog = findViewById(R.id.custom_selection_dialog_view)
        customConfirmDialog = findViewById(R.id.custom_confirm_dialog_view)

        val viewPager: ViewPager2 = findViewById(R.id.viewPager)
        val tabLayout: TabLayout = findViewById(R.id.tabLayout)
        viewPager.adapter = ViewPagerAdapter(this)

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Přiřazení otisků"
                1 -> "Seznam pracovníků"
                else -> null
            }
        }.attach()
    }

    override fun onResume() {
        super.onResume()
        fingerprintManager.setCallback(this)
        if (fingerprintManager.isDeviceConnected()) {
            sharedViewModel.setConnected(true)
            sharedViewModel.setInfoText("Čtečka připojena.")
        } else {
            sharedViewModel.setConnected(false)
            sharedViewModel.setInfoText("Čtečka odpojena. Zkuste se vrátit a znovu otevřít obrazovku.")
        }
    }

    override fun onPause() {
        super.onPause()
        fingerprintManager.removeCallback()
    }

    // --- Implementace callbacků a dialogů (zůstává beze změny) ---

    override fun onDeviceConnected(deviceInfo: String) {
        sharedViewModel.setConnected(true)
        sharedViewModel.setInfoText("Čtečka připojena: $deviceInfo")
    }

    override fun onDeviceDisconnected() {
        sharedViewModel.setConnected(false)
        sharedViewModel.setInfoText("Čtečka odpojena.")
    }

    override fun onFingerprintCaptured(bitmap: Bitmap?) {
        sharedViewModel.setCapturedImage(bitmap)
    }

    override fun onRegistrationProgress(step: Int, totalSteps: Int) {
        sharedViewModel.setRegistrationStep(step, totalSteps)
    }

    override fun onRegistrationComplete(userId: String) {
        sharedViewModel.setRegistrationComplete(userId)
    }

    override fun onRegistrationFailed(error: String) {
        sharedViewModel.setInfoText("Registrace selhala: $error")
    }

    override fun onIdentificationComplete(user: FingerprintUser?, score: Int) {
        sharedViewModel.setIdentificationResult(user, score)
    }

    override fun onError(error: String) {
        sharedViewModel.setInfoText("Chyba: $error")
    }

    fun showImportDialog(files: List<String>, onConfirm: (String) -> Unit) {
        customSelectionDialog.visibility = View.VISIBLE
        val dialogTitle: TextView = customSelectionDialog.findViewById(R.id.selection_dialog_title)
        val radioGroup: RadioGroup = customSelectionDialog.findViewById(R.id.selection_dialog_options)
        val confirmButton: Button = customSelectionDialog.findViewById(R.id.selection_dialog_button_confirm)
        val cancelButton: Button = customSelectionDialog.findViewById(R.id.selection_dialog_button_cancel)

        dialogTitle.text = "Vyberte zálohu pro import"
        radioGroup.removeAllViews()
        var selectedFile: String? = null
        confirmButton.isEnabled = false

        files.forEach { fileName ->
            val radioButton = RadioButton(this).apply {
                text = fileName
                textSize = 18f
                setPadding(8, 8, 8, 8)
            }
            radioGroup.addView(radioButton)
        }

        if (files.size == 1) {
            (radioGroup.getChildAt(0) as? RadioButton)?.isChecked = true
            selectedFile = files[0]
            confirmButton.isEnabled = true
        }

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val checkedRadioButton = findViewById<RadioButton>(checkedId)
            selectedFile = checkedRadioButton.text.toString()
            confirmButton.isEnabled = true
        }

        confirmButton.setOnClickListener {
            selectedFile?.let {
                onConfirm(it)
                customSelectionDialog.visibility = View.GONE
            }
        }

        cancelButton.setOnClickListener {
            customSelectionDialog.visibility = View.GONE
        }
    }

    fun showCustomConfirmDialog(title: String, message: String, positiveButtonText: String, onConfirm: () -> Unit) {
        customConfirmDialog.visibility = View.VISIBLE
        val dialogTitle: TextView = customConfirmDialog.findViewById(R.id.dialog_title)
        val dialogMessage: TextView = customConfirmDialog.findViewById(R.id.dialog_message)
        val confirmButton: Button = customConfirmDialog.findViewById(R.id.dialog_button_confirm)
        val cancelButton: Button = customConfirmDialog.findViewById(R.id.dialog_button_cancel)

        dialogTitle.text = title
        dialogMessage.text = message
        confirmButton.text = positiveButtonText

        confirmButton.setOnClickListener {
            onConfirm()
            customConfirmDialog.visibility = View.GONE
        }
        cancelButton.setOnClickListener {
            customConfirmDialog.visibility = View.GONE
        }
    }

    // Odstraněno onOptionsItemSelected, protože ho již spravuje toolbar.setNavigationOnClickListener
}