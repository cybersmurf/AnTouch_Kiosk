package cz.emistr.antouchkiosk

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.hardware.usb.UsbDevice
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.zkteco.android.biometric.core.device.TransportType
import com.zkteco.android.biometric.module.fingerprintreader.FingerprintCaptureListener
import com.zkteco.android.biometric.module.fingerprintreader.FingerprintFactory
import com.zkteco.android.biometric.module.fingerprintreader.FingerprintSensor
import com.zkteco.android.biometric.module.fingerprintreader.ZKFingerService
import com.zkteco.android.biometric.module.fingerprintreader.exception.FingerprintException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import android.util.Base64
import java.io.File

class FingerprintManager(
    private val context: Context,
    private val callback: FingerprintManagerCallback
) : DefaultLifecycleObserver {

    companion object {
        private const val ENROLL_COUNT = 3
    }

    interface FingerprintManagerCallback {
        fun onDeviceConnected(deviceInfo: String)
        fun onDeviceDisconnected()
        fun onFingerprintCaptured(bitmap: Bitmap?)
        fun onRegistrationProgress(step: Int, totalSteps: Int)
        fun onRegistrationComplete(userId: String)
        fun onRegistrationFailed(error: String)
        fun onIdentificationResult(userId: String?, score: Int)
        fun onError(error: String)
    }

    private var fingerprintSensor: FingerprintSensor? = null
    private val managerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isConnected = false
    private var isConnecting = false

    private val bufids = ByteArray(256)

    // Změna: isRegistering je nyní public
    var isRegistering = false
        private set

    private var currentWorkerId: String? = null
    private var currentName: String? = null
    private var enrollIndex = 0
    private val regTemplates = Array(ENROLL_COUNT) { ByteArray(2048) }

    private val dbManager = FingerprintDBManager()

    private val fingerprintCaptureListener = object : FingerprintCaptureListener {
        override fun captureOK(imageData: ByteArray) {
            val bitmap = createBitmapFromRawData(imageData, fingerprintSensor!!.imageWidth, fingerprintSensor!!.imageHeight)
            callback.onFingerprintCaptured(bitmap)
        }
        override fun extractOK(template: ByteArray) {
            if (isRegistering) {
                processRegistration(template)
            } else {
                // Identifikace není v novém designu přímo použita, ale necháváme pro budoucí potřeby
                processIdentification(template)
            }
        }
        override fun captureError(exception: FingerprintException?) {}
        override fun extractError(code: Int) {
            callback.onError("Chyba extrakce, kód: $code")
        }
    }

    fun initialize() {
        val ret = ZKFingerService.init()
        if (ret == 0) {
            Timber.d("ZKFingerService úspěšně inicializován.")
        } else {
            Timber.e("Inicializace ZKFingerService selhala s kódem: $ret")
        }
    }

    fun connect(device: UsbDevice) {
        if (isConnected || isConnecting) return
        isConnecting = true
        managerScope.launch {
            try {
                val parameters: MutableMap<String, Any> = mutableMapOf("vid" to device.vendorId, "pid" to device.productId)
                fingerprintSensor = FingerprintFactory.createFingerprintSensor(context, TransportType.USB, parameters)

                fingerprintSensor?.open(device)
                isConnected = true
                val dbPath = context.getDatabasePath("fingerprints.db").absolutePath
                dbManager.openDatabase(dbPath)

                // Synchronizace otisků do ZKService
                syncDbToService()

                withContext(Dispatchers.Main) {
                    callback.onDeviceConnected("Čtečka připojena.")
                }
                fingerprintSensor?.setFingerprintCaptureListener(0, fingerprintCaptureListener)
                fingerprintSensor?.startCapture(0)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback.onError("Výjimka při připojení: ${e.message}")
                }
                Timber.e(e, "Connection failed")
            } finally {
                isConnecting = false
            }
        }
    }

    fun disconnect() {
        managerScope.launch {
            try {
                fingerprintSensor?.stopCapture(0)
                fingerprintSensor?.close(0)
                ZKFingerService.free()
            } finally {
                isConnected = false
                fingerprintSensor = null
                withContext(Dispatchers.Main) {
                    callback.onDeviceDisconnected()
                }
            }
        }
    }

    private fun processIdentification(template: ByteArray) {
        val bufids = ByteArray(256)
        val ret = ZKFingerService.identify(template, bufids, 70, 1)
        if (ret > 0) {
            val firstNull = bufids.indexOf(0.toByte())
            val strRes = if (firstNull != -1) {
                String(bufids, 0, firstNull)
            } else {
                String(bufids)
            }
            val res = strRes.trim().split("\t".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (res.isNotEmpty()) {
                val recordId = res[0].toIntOrNull()
                val score = if (res.size > 1) res[1].toIntOrNull() ?: 0 else 0

                if (recordId != null) {
                    val user = dbManager.getUserById(recordId)
                    if (user != null) {
                        // Posíláme zpět jméno a kód zaměstnance
                        callback.onIdentificationResult("${user.name} (${user.workerId})", score)
                    } else {
                        callback.onIdentificationResult(null, 0)
                    }
                } else {
                    callback.onIdentificationResult(null, 0)
                }
            } else {
                callback.onIdentificationResult(null, 0)
            }
        } else {
            callback.onIdentificationResult(null, 0)
        }
    }

    // Změna: Metoda přijímá workerId a name
    fun startRegistration(workerId: String, name: String) {
        if (isRegistering) {
            callback.onError("Registrace již probíhá.")
            return
        }
        isRegistering = true
        currentWorkerId = workerId
        currentName = name
        enrollIndex = 0
        callback.onRegistrationProgress(1, ENROLL_COUNT)
    }

    private fun syncDbToService() {
        ZKFingerService.clear()
        val allUsers = dbManager.getAllUsers()
        for (user in allUsers) {
            val template = base64ToByteArray(user.feature)
            // ZKService používá pro identifikaci unikátní ID záznamu (otisku)
            ZKFingerService.save(template, user.id.toString())
        }
        Timber.d("${allUsers.size} otisků prstů nahráno z databáze do cache.")
    }

    private fun processRegistration(template: ByteArray) {
        val bufids = ByteArray(256)
        if (ZKFingerService.identify(template, bufids, 55, 1) > 0) {
            val strRes = String(bufids).trim().split("\t")[0]
            callback.onRegistrationFailed("Tento otisk je již registrován pod ID: $strRes")
            cancelRegistration()
            return
        }
        if (enrollIndex > 0 && ZKFingerService.verify(regTemplates[enrollIndex - 1], template) < 50) {
            callback.onRegistrationFailed("Přiložte prosím stejný prst.")
            return
        }
        System.arraycopy(template, 0, regTemplates[enrollIndex], 0, template.size)
        enrollIndex++

        if (enrollIndex < ENROLL_COUNT) {
            callback.onRegistrationProgress(enrollIndex + 1, ENROLL_COUNT)
        } else {
            val finalTemplate = ByteArray(2048)
            if (ZKFingerService.merge(regTemplates[0], regTemplates[1], regTemplates[2], finalTemplate) > 0) {
                val feature = byteArrayToBase64(finalTemplate)

                // **OPRAVA ZDE:** Kontrola návratové hodnoty `Long`.
                val newId = dbManager.insertUser(currentWorkerId!!, currentName!!, feature)
                if (newId) {
                    ZKFingerService.save(finalTemplate, newId.toString())
                    callback.onRegistrationComplete(currentWorkerId!!)
                } else {
                    callback.onRegistrationFailed("Nepodařilo se uložit do databáze.")
                }
            } else {
                callback.onRegistrationFailed("Nepodařilo se sloučit šablony.")
            }
            cancelRegistration()
        }
    }

    fun cancelRegistration() {
        isRegistering = false
        currentWorkerId = null
        currentName = null
        enrollIndex = 0
    }

    fun deleteUserById(id: Int): Boolean {
        val result = dbManager.deleteUserById(id)
        if (result) {
            // Po smazání z DB je nutné resynchronizovat ZKService
            syncDbToService()
        }
        return result
    }

    fun getAllUsers(): List<FingerprintUser> = dbManager.getAllUsers()

    // Nová metoda, která volá metodu z DBManageru
    fun getNextUserId(): Int = dbManager.getNextUserId()

    private fun createBitmapFromRawData(data: ByteArray, width: Int, height: Int): Bitmap {
        val imageSize = width * height
        if (imageSize == 0 || data.size < imageSize) {
            return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        }
        val pixels = IntArray(imageSize)
        for (i in 0 until imageSize) {
            val gray = data[i].toInt() and 0xFF
            pixels[i] = Color.rgb(gray, gray, gray)
        }
        return Bitmap.createBitmap(pixels, 0, width, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun byteArrayToBase64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun base64ToByteArray(base64: String): ByteArray = Base64.decode(base64, Base64.NO_WRAP)

    fun isDeviceConnected(): Boolean = isConnected

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        disconnect()
        dbManager.closeDatabase()
        managerScope.cancel()
    }
}