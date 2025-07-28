package cz.emistr.antouchkiosk

import android.app.AlertDialog
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import timber.log.Timber

class FingerprintControlFragment : Fragment() {

    private lateinit var imageScan1: ImageView
    private lateinit var imageScan2: ImageView
    private lateinit var imageScan3: ImageView
    private lateinit var buttonNew: Button
    private lateinit var buttonCancelRegistration: Button
    private lateinit var textSensorStatus: TextView
    private lateinit var textInfo: TextView
    private lateinit var buttonClose: Button

    private lateinit var fingerprintManager: FingerprintManager
    private var registrationImages = mutableListOf<Bitmap>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_fingerprint_control, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fingerprintManager = (activity as FingerprintActivity).fingerprintManager
        initializeViews(view)
        setupClickListeners()
        updateUI()
    }

    private fun initializeViews(view: View) {
        imageScan1 = view.findViewById(R.id.imageScan1)
        imageScan2 = view.findViewById(R.id.imageScan2)
        imageScan3 = view.findViewById(R.id.imageScan3)
        buttonNew = view.findViewById(R.id.buttonNew)
        buttonCancelRegistration = view.findViewById(R.id.buttonCancelRegistration)
        textSensorStatus = view.findViewById(R.id.textSensorStatus)
        textInfo = view.findViewById(R.id.textInfo)
        buttonClose = view.findViewById(R.id.buttonClose)
    }

    private fun setupClickListeners() {
        buttonNew.setOnClickListener { showNewUserDialog() }
        buttonCancelRegistration.setOnClickListener {
            fingerprintManager.cancelRegistration()
            resetRegistrationUI()
            updateUI()
        }
        buttonClose.setOnClickListener { activity?.finish() }
    }

    private fun showNewUserDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_new_user, null)
        val builder = AlertDialog.Builder(requireContext()).setView(dialogView)
        val dialog = builder.create()

        val editTextId = dialogView.findViewById<EditText>(R.id.editTextId)
        val editTextWorkerId = dialogView.findViewById<EditText>(R.id.editTextWorkerId)
        val editTextName = dialogView.findViewById<EditText>(R.id.editTextName)
        val buttonOk = dialogView.findViewById<Button>(R.id.buttonOk)
        val buttonCancel = dialogView.findViewById<Button>(R.id.buttonCancel)

        // Získání dalšího ID z manažeru
        editTextId.setText(fingerprintManager.getNextUserId().toString())

        buttonOk.setOnClickListener {
            val workerId = editTextWorkerId.text.toString().trim()
            val name = editTextName.text.toString().trim()
            if (workerId.isEmpty()) {
                Toast.makeText(requireContext(), "Kód zaměstnance je povinný", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Opravené volání metody
            fingerprintManager.startRegistration(workerId, name)
            resetRegistrationUI()
            updateUI()
            dialog.dismiss()
        }
        buttonCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun resetRegistrationUI() {
        registrationImages.clear()
        imageScan1.setImageDrawable(null)
        imageScan2.setImageDrawable(null)
        imageScan3.setImageDrawable(null)
        textInfo.text = "Přiložte prst na čtečku..."
    }

    fun onDeviceConnected(deviceInfo: String) {
        textSensorStatus.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_circle_green, 0, 0, 0)
        textInfo.text = "Čtečka připojena. Pro zahájení stiskněte 'Nový'."
        updateUI()
    }

    fun onDeviceDisconnected() {
        textSensorStatus.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_circle_gray, 0, 0, 0)
        textInfo.text = "Čtečka odpojena."
        updateUI()
    }

    fun onFingerprintCaptured(bitmap: Bitmap?) {
        bitmap?.let {
            if (fingerprintManager.isRegistering) {
                // Původní chování při registraci
                if (registrationImages.size < 3) {
                    registrationImages.add(it)
                    when (registrationImages.size) {
                        1 -> imageScan1.setImageBitmap(it)
                        2 -> imageScan2.setImageBitmap(it)
                        3 -> imageScan3.setImageBitmap(it)
                    }
                }
            } else {
                // Nové chování mimo registraci
                resetRegistrationUI(clearText = false) // Vyčistí obrázky, ale ne text
                imageScan1.setImageBitmap(it)
                textInfo.text = "Identifikuji..."
            }
        }
    }

    fun onIdentificationResult(userInfo: String?, score: Int) {
        val resultText = if (userInfo != null) {
            "Identifikován: $userInfo [skóre: $score]"
        } else {
            "Otisk nerozpoznán."
        }
        textInfo.text = resultText
    }

    private fun resetRegistrationUI(clearText: Boolean = true) {
        registrationImages.clear()
        imageScan1.setImageDrawable(null)
        imageScan2.setImageDrawable(null)
        imageScan3.setImageDrawable(null)
        if (clearText) {
            textInfo.text = "Přiložte prst na čtečku..."
        }
    }

    fun onRegistrationProgress(step: Int, totalSteps: Int) {
        textInfo.text = "Přiložte prst znovu (${step}/${totalSteps})"
    }

    fun onRegistrationComplete(workerId: String) {
        textInfo.text = "Otisk pro zaměstnance $workerId úspěšně zaregistrován."
        updateUI()
    }

    fun onRegistrationFailed(error: String) {
        textInfo.text = "Registrace selhala: $error"
        updateUI()
    }

    fun onError(error: String) {
        textInfo.text = "Chyba: $error"
        Timber.e("Fingerprint error: $error")
    }

    fun updateUI() {
        val isConnected = fingerprintManager.isDeviceConnected()
        // Změna: isRegistering je nyní vlastnost
        val isRegistering = fingerprintManager.isRegistering

        buttonNew.isEnabled = isConnected && !isRegistering
        buttonCancelRegistration.isEnabled = isConnected && isRegistering
    }
}