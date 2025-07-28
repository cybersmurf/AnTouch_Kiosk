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
import android.util.Base64 // Přidejte tento import
//import java.io.File // Přidejte tento import

class FingerprintManager(
    private val context: Context,
    private val callback: FingerprintManagerCallback
) : DefaultLifecycleObserver {


    // ... (companion object a interface zůstávají stejné) ...
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
/*
    private val byteArray = byteArrayOf(
        75, 77, 83, 83, 50, 49, 0, 0, 2, 14, 15, 4, 8, 5, 7, 9, -50, -48, 0, 0, 46, 15, -123, 1, 0, 0, 55,
        -127, 51, 13, 82, 14, 4, 1, 4, 61, -124, 0, 20, 0, -109, 81, 91, 0, -75, 0, -122, 77, -18, 0, 91,
        15, 104, 57, -100, 0, 122, 1, 88, 96, 64, 14, 1, 1, 121, 85, 88, 0, -8, 1, -118, 59, 75, 0, -78, 0,
        6, 88, 82, 0, -90, 14, -107, 100, 75, 0, -51, 0, -49, 56, 60, 14, -65, 0, -128, 65, -81, 0, -31, 1,
        -101, 86, 61, 0, 107, 1, 101, 67, -56, -116, 57, -16, -70, 126, -61, -4, -117, 13, -74, -118, -125,
        -114, 80, 127, 109, 11, 57, 6, -58, -31, -23, 105, -82, 7, -125, 23, 107, 59, -48, 116, -47, 127,
        58, -13, 99, 33, 83, 38, 114, -122, -125, -114, -36, -120, -71, -1, 63, 4, -90, 19, 101, -99, 24,
        -115, 53, 7, 77, -121, 36, 124, 39, 119, -12, -1, 51, -1, 95, 19, 82, 11, -65, -16, -14, -34, 35,
        -53, -76, 6, -27, 31, 3, 15, 83, 17, 42, 5, 0, 118, -72, 22, 89, 4, 1, 93, 124, 23, -63, 83, -125,
        -62, 4, 14, 88, -123, 22, 123, -2, 12, -59, 66, -110, 8, -63, 56, -1, -64, -64, -1, -105, 15, 2, 62,
        -103, 6, -63, 56, 62, 74, 59, -128, 19, 14, 37, -92, -3, 62, -2, 84, -5, 115, 87, 10, 1, 67, -78,
        -122, 117, 12, -59, 96, -76, 30, 64, 86, -64, -64, 96, 3, -59, 26, -70, 13, -63, 6, 0, 68, -65, 6,
        4, 43, 6, 14, 61, -63, 125, -122, 13, 0, -117, -53, 1, 59, -63, -2, -1, 77, 63, 5, -59, 70, -50,
        115, -2, -107, 14, 0, 76, -47, -54, -63, 58, -15, -63, -2, 107, 106, 19, 0, -53, -31, -11, -15,
        -64, -4, -64, 60, 71, -64, 5, 69, 86, 0, 1, 13, -17, -12, -2, -63, 57, -62, 43, -50, 62, -63, 5,
        0, 64, -2, 69, -62, 124, 29, 1, 9, -1, -16, 59, -64, 59, -1, 52, 92, -64, 49, -63, 14, 16, 72,
        -59, 3, -7, 85, -63, 93, 63, -63, 5, 16, -123, 4, 117, 118, 18, 16, 17, 31, -21, 56, 57, -2, 58,
        -49, 69, 85, 91, 19, 16, 10, -20, -15, -61, -50, -1, -5, -64, -2, -1, -1, 4, 67, 64, 88, 6, 16, 85,
        61, -112, -97, 1, -2, 16, 30, 12, 54, -25, -64, -2, -2, 57, -3, -61, -16, -63, -1, -64, -64, -1,
        -64, 5, 91, 19, 30, 14, 70, -34, 44, -1, -4, -119, 76, 72, -50, 12, 16, 19, 88, -25, -63, 56, -3,
        -4, -50, -3, -64, -1, -2, -64, -64, -55, 16, 20, 110, -27, 49, -3, -3, -64, -2, -121, 9, 18, 123,
        120, -105, 117, -61, -61, -60, 5, 12, 18, 25, 114, -30, 65, -5, -2, -4, -88, -64, 1, 30, 87, 125,
        -119, -57, 7, 16, -75, -128, -107, -49, -61, -60, -93, 82, 66, 0, -50, 67, 3, 14, 1, 11, 69, 82, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1
    )
*/

    private var fpWidth = 256
    private var fpHeight = 360

    private var isRegistering = false
    private var currentUserId: String? = null
    private var enrollIndex = 0
    private val regTemplates = Array(ENROLL_COUNT) { ByteArray(2048) }

    private val dbManager = FingerprintDBManager()

    private fun byteArrayToBase64(bytes: ByteArray): String {
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun base64ToByteArray(base64: String): ByteArray {
        return Base64.decode(base64, Base64.NO_WRAP)
    }



    private val fingerprintCaptureListener = object : FingerprintCaptureListener {
        override fun captureOK(imageData: ByteArray) {
            //val bitmap = createBitmapFromRawData(imageData, fpWidth, fpHeight)
            val bitmap = createBitmapFromRawData(imageData, fingerprintSensor!!.getImageWidth(), fingerprintSensor!!.getImageHeight())
            callback.onFingerprintCaptured(bitmap)
        }

        override fun extractOK(template: ByteArray) {
            if (isRegistering) {
                processRegistration(template)
            } else {
                processIdentification(template)
            }
        }

        override fun captureError(exception: FingerprintException?) {
            //callback.onError("Chyba snímání: ${exception?.message}")
        }

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

    fun isUserAlreadyRegistered(userId: String): Boolean {
        return dbManager.isUserExisted(userId)
    }

    fun getUserCount(): Int {
        return dbManager.getCount()
    }

    fun connect(device: UsbDevice) {
        if (isConnected || isConnecting) {
            return
        }
        isConnecting = true
        managerScope.launch {
            try {
                val parameters: MutableMap<String, Any> = mutableMapOf(
                    "vid" to device.vendorId,
                    "pid" to device.productId
                )
                fingerprintSensor = FingerprintFactory.createFingerprintSensor(context, TransportType.USB, parameters)

                if (fingerprintSensor == null) {
                    withContext(Dispatchers.Main) {
                        callback.onError("Nepodařilo se vytvořit instanci čtečky otisků prstů.")
                    }
                    return@launch
                }
                var ret = 0;
                try {
                  fingerprintSensor?.open(device)
                } catch (e: FingerprintException) {
                    withContext(Dispatchers.Main) {
                        callback.onError("Chyba při otevírání čtečky: ${e.message}")
                    }
                    ret = -1
                    //return@launch
                }
                if (ret == 0) {
                    isConnected = true

                    // Otevření databáze
                    val dbPath = context.getDatabasePath("fingerprints.db").absolutePath
                    dbManager.openDatabase(dbPath)

                    // Načtení uživatelů z DB a jejich nahrání do ZKFingerService
                    val allUsers = dbManager.queryUserList()
                    if (allUsers.isNotEmpty()) {
                        ZKFingerService.clear() // Pro jistotu vyčistíme cache před nahráním
                        for ((userId, feature) in allUsers) {
                            val template = base64ToByteArray(feature)
                            ZKFingerService.save(template, userId)
                        }
                        Timber.d("${allUsers.size} otisků prstů nahráno z databáze do cache.")
                    }

                    withContext(Dispatchers.Main) {
                        callback.onDeviceConnected("Čtečka připojena ($fpWidth x $fpHeight).")
                    }
                    fingerprintSensor?.setFingerprintCaptureListener(0, fingerprintCaptureListener)
                    fingerprintSensor?.startCapture(0)
                } else {
                    withContext(Dispatchers.Main) {
                        callback.onError("Připojení selhalo, kód: $ret")
                    }
                }
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

    fun startRegistration(userId: String) {
        if (isRegistering) {
            callback.onError("Registrace již probíhá.")
            return
        }
        isRegistering = true
        currentUserId = userId
        enrollIndex = 0
        callback.onRegistrationProgress(1, ENROLL_COUNT)
    }

    private fun processRegistration(template: ByteArray) {
        val bufids = ByteArray(256)
        if (ZKFingerService.identify(template, bufids, 55, 1) > 0) {
            val strRes = String(bufids).trim()
            val res = strRes.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            callback.onRegistrationFailed("Tento otisk je již registrován pod ID: ${res[0]}")
            cancelRegistration()
            return
        }

        if (enrollIndex > 0) {
            val score = ZKFingerService.verify(regTemplates[enrollIndex - 1], template)
            if (score < 50) {
                callback.onRegistrationFailed("Přiložte prosím stejný prst.")
                return
            }
        }

        System.arraycopy(template, 0, regTemplates[enrollIndex], 0, template.size)
        enrollIndex++

        if (enrollIndex < ENROLL_COUNT) {
            callback.onRegistrationProgress(enrollIndex + 1, ENROLL_COUNT)
        } else {
            val finalTemplate = ByteArray(2048)
            if (ZKFingerService.merge(regTemplates[0], regTemplates[1], regTemplates[2], finalTemplate) > 0) {
                ZKFingerService.save(finalTemplate, currentUserId!!)

                val feature = byteArrayToBase64(finalTemplate)
                dbManager.insertUser(currentUserId!!, feature)

                callback.onRegistrationComplete(currentUserId!!)
            } else {
                callback.onRegistrationFailed("Nepodařilo se sloučit šablony.")
            }
            cancelRegistration()
        }
    }

    private fun processIdentification2(template: ByteArray) {
        bufids.fill(0)

        //val ret = ZKFingerService.identify(template, bufids, 70, 1)
        //val ret= FingerprintBridge.performIdentify(byteArray, bufids)
        val ret = ZKFingerService.identify(template, bufids, 70, 1)
        if (ret > 0) {
            val strRes = String(bufids).trim()
            val res = strRes.split("\t".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val userId = res[0]
            val score = 100 //Integer.parseInt(res[1])
            callback.onIdentificationResult(userId, score)
        } else {
            callback.onIdentificationResult(null, 0)
        }
    }

    private fun processIdentification(template: ByteArray) {
        val bufids = ByteArray(256)
        val ret = ZKFingerService.identify(template, bufids, 70, 1)
        if (ret > 0) {
            // KROK 1: Najdeme konec skutečného textu (první nulový znak)
            val firstNull = bufids.indexOf(0.toByte())
            val strRes = if (firstNull != -1) {
                String(bufids, 0, firstNull)
            } else {
                String(bufids)
            }

            // Tímto získáme čistý řetězec, např. "123,95"
            val res = strRes.trim().split("\t".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

            if (res.size >= 2) {
                val userId = res[0]
                // KROK 2: Použijeme bezpečnější metodu toIntOrNull(), která nespadne
                val score = res[1].toIntOrNull() ?: 0 // Pokud se převod nepovede, použije se 0
                callback.onIdentificationResult(userId, score)
            } else {
                // Pokud odpověď neměla očekávaný formát "id,skore"
                callback.onIdentificationResult(null, 0)
                Timber.w("Neočekávaný formát odpovědi při identifikaci: $strRes")
            }
        } else {
            callback.onIdentificationResult(null, 0)
        }
    }

    fun cancelRegistration() {
        isRegistering = false
        currentUserId = null
        enrollIndex = 0
    }

    fun deleteUser(userId: String): Boolean {
        val result = ZKFingerService.del(userId) > 0
        if (result) {
            dbManager.deleteUser(userId) // Smazání z DB
        }
        return result
    }

    fun clearAllUsers() {
        ZKFingerService.clear()
        dbManager.clear() // Vyčištění DB
    }

    /*
    fun getUserCount2(): Int {
        return ZKFingerService.count()
    }

     */

    fun getDeviceInfo(): String? {
        return if (isConnected) "ZKTeco Sensor ($fpWidth x $fpHeight)" else null
    }

    /*
    private fun createBitmapFromRawData2(data: ByteArray, width: Int, height: Int): Bitmap {
        val pixels = IntArray(width * height)
        for (i in data.indices) {
            val gray = data[i].toInt() and 0xFF
            pixels[i] = Color.rgb(gray, gray, gray)
        }
        return Bitmap.createBitmap(pixels, 0, width, width, height, Bitmap.Config.ARGB_8888)
    }

     */

    private fun createBitmapFromRawData(data: ByteArray, width: Int, height: Int): Bitmap {
        // Pojistka proti pádu, i kdyby přišla data špatné velikosti
        val imageSize = width * height
        if (imageSize == 0 || data.size < imageSize) {
            Timber.e("Chybná velikost dat obrázku. Očekáváno: $imageSize, Dorazilo: ${data.size}")
            // Vytvoříme prázdný (černý) bitmap, aby aplikace nespadla
            return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        }

        val pixels = IntArray(imageSize)
        for (i in 0 until imageSize) {
            val gray = data[i].toInt() and 0xFF
            pixels[i] = Color.rgb(gray, gray, gray)
        }
        return Bitmap.createBitmap(pixels, 0, width, width, height, Bitmap.Config.ARGB_8888)
    }

    fun isDeviceConnected(): Boolean = isConnected
    fun getCurrentRegistrationUser(): String? = if (isRegistering) currentUserId else null
    fun getRegistrationProgress(): Pair<Int, Int> = Pair(enrollIndex + 1, ENROLL_COUNT)

    /**
     * Provede import otisků z JSON souboru, nahraje je do databáze
     * a následně synchronizuje se ZKFingerService.
     * @param jsonString Obsah JSON souboru.
     * @return Počet naimportovaných otisků, nebo -1 při chybě.
     */
    fun importFingerprints(jsonString: String): Int {
        val result = dbManager.importDatabase(jsonString)

        if (result > -1) {
            // Po úspěšném importu do DB musíme znovu načíst všechny otisky do paměti čtečky
            ZKFingerService.clear() // Vyčistíme stávající cache
            val allUsers = dbManager.queryUserList()
            for ((userId, feature) in allUsers) {
                val template = base64ToByteArray(feature)
                ZKFingerService.save(template, userId)
            }
            Timber.d("${allUsers.size} otisků prstů znovu nahráno do cache po importu.")
        }

        return result
    }
    /**
     * Exportuje databázi otisků prstů do JSON formátu.
     * @return JSON řetězec s otisky prstů, nebo null při chybě.
     */
    fun exportDatabase(): String? {
        return dbManager.exportDatabase()
    }
    /**
     * Načte všechny uživatele z databáze.
     * @return Seznam uživatelů.
     */
    fun getAllUsers(): List<FingerprintUser> {
        return dbManager.getAllUsers()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        disconnect()
        dbManager.closeDatabase()
        managerScope.cancel()
        Timber.d("FingerprintManager destroyed.")
    }
}