package cz.emistr.antouchkiosk

import android.app.AlertDialog
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
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
    private val sharedViewModel: SharedFingerprintViewModel by activityViewModels()
    private var registrationImages = mutableListOf<Bitmap>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_fingerprint_control, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fingerprintManager = (activity as FingerprintActivity).fingerprintManager
        initializeViews(view)
        setupClickListeners()
        observeViewModel() // Přesunuto sem pro jistotu
    }

    // Tato metoda byla přejmenována z updateUI na observeViewModel a upravena
    private fun observeViewModel() {
        sharedViewModel.isConnected.observe(viewLifecycleOwner) { isConnected ->
            updateConnectionStatus(isConnected)
            updateButtons(isConnected)
        }
        sharedViewModel.infoText.observe(viewLifecycleOwner) { text ->
            textInfo.text = text
        }
        sharedViewModel.capturedImage.observe(viewLifecycleOwner) { bitmap ->
            handleCapturedImage(bitmap)
        }
        sharedViewModel.identificationResult.observe(viewLifecycleOwner) { result ->
            val (user, score) = result
            val resultText = if (user != null) {
                "Identifikován: ${user.name} (${user.workerId}) [skóre: $score]"
            } else {
                "Otisk nerozpoznán."
            }
            textInfo.text = resultText
        }
        sharedViewModel.registrationStep.observe(viewLifecycleOwner) { (step, total) ->
            textInfo.text = "Přiložte prst znovu (${step}/${total})"
        }
        sharedViewModel.registrationComplete.observe(viewLifecycleOwner) { workerId ->
            textInfo.text = "Otisk pro zaměstnance $workerId úspěšně zaregistrován."
            updateButtons(fingerprintManager.isDeviceConnected())
        }
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
            updateButtons(fingerprintManager.isDeviceConnected())
        }
        buttonClose.setOnClickListener { activity?.finish() }
    }

    private fun handleCapturedImage(bitmap: Bitmap?) {
        bitmap ?: return
        if (fingerprintManager.isRegistering) {
            if (registrationImages.size < 3) {
                registrationImages.add(bitmap)
                when (registrationImages.size) {
                    1 -> imageScan1.setImageBitmap(bitmap)
                    2 -> imageScan2.setImageBitmap(bitmap)
                    3 -> imageScan3.setImageBitmap(bitmap)
                }
            }
        } else {
            resetRegistrationUI(clearText = false)
            imageScan1.setImageBitmap(bitmap)
            textInfo.text = "Identifikuji..."
        }
    }

    private fun updateConnectionStatus(isConnected: Boolean) {
        val drawableId = if (isConnected) R.drawable.ic_circle_green else R.drawable.ic_circle_gray
        textSensorStatus.setCompoundDrawablesWithIntrinsicBounds(drawableId, 0, 0, 0)
    }

    private fun updateButtons(isConnected: Boolean) {
        buttonNew.isEnabled = isConnected && !fingerprintManager.isRegistering
        buttonCancelRegistration.isEnabled = isConnected && fingerprintManager.isRegistering
    }

    // Ponechána pouze jedna verze této metody
    private fun resetRegistrationUI(clearText: Boolean = true) {
        registrationImages.clear()
        imageScan1.setImageDrawable(null)
        imageScan2.setImageDrawable(null)
        imageScan3.setImageDrawable(null)
        if (clearText) {
            textInfo.text = "Přiložte prst na čtečku..."
        }
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

        editTextId.setText(fingerprintManager.getNextUserId().toString())

        buttonOk.setOnClickListener {
            val workerId = editTextWorkerId.text.toString().trim()
            val name = editTextName.text.toString().trim()
            if (workerId.isEmpty()) {
                Toast.makeText(requireContext(), "Kód zaměstnance je povinný", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            fingerprintManager.startRegistration(workerId, name)
            resetRegistrationUI()
            updateButtons(fingerprintManager.isDeviceConnected())
            dialog.dismiss()
        }
        buttonCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
}