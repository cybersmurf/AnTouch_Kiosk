import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import java.util.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BeaconScanner(private val context: Context, private val callback: BeaconScanCallback) {

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var scanning = false
    private val handler = Handler(Looper.getMainLooper())



    // Scan timeout
    //private val SCAN_PERIOD: Long = 10000
    private val SCAN_PERIOD: Long = 10000  // 30 sekund místo 10

    interface BeaconScanCallback {
        fun onBeaconFound(beacon: Beacon)
    }

    data class Beacon(
        val uuid: String,
        val major: Int,
        val minor: Int,
        val rssi: Int
    )

    fun startScanning() {
        if (!hasPermissions()) {
            Log.e("BeaconScanner", "Chybí oprávnění pro Bluetooth skenování")
            return
        }

        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            Log.e("BeaconScanner", "Bluetooth není k dispozici nebo je vypnutý")
            return
        }

        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner

        if (bluetoothLeScanner == null) {
            Log.e("BeaconScanner", "BluetoothLeScanner není k dispozici")
            return
        }

        if (scanning) {
            return
        }

        // Zastavit skenování po SCAN_PERIOD
        handler.postDelayed({
            if (scanning) {
                scanning = false
                bluetoothLeScanner?.stopScan(leScanCallback)
                Log.d("BeaconScanner", "BLE skenování zastaveno po timeoutu")
            }
        }, SCAN_PERIOD)

        scanning = true

        // Nastavení filtru pro iBeacon data
        // iBeacon používá proprietární formát Applu, takže budeme muset filtrovat v callback
        bluetoothLeScanner?.startScan(null, scanSettings, leScanCallback)
        Log.d("BeaconScanner", "BLE skenování zahájeno")
    }

    fun stopScanning() {
        if (scanning && bluetoothLeScanner != null) {
            scanning = false
            bluetoothLeScanner?.stopScan(leScanCallback)
            Log.d("BeaconScanner", "BLE skenování zastaveno")
        }
    }
/*
    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()
*/
    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .setReportDelay(0)  // Okamžité oznámení výsledků
        .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)  // Vyhledat všechny odpovídající reklamy
        .build()

    private val leScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)

            // Základní info o nalezeném zařízení
            Log.d("BeaconScanner", "Nalezeno BLE zařízení: ${result.device.address}")

            val scanRecord = result.scanRecord ?: return
            val manufacturerSpecificData = scanRecord.manufacturerSpecificData ?: return

            // Projít všechna manufacturer data
            for (i in 0 until manufacturerSpecificData.size()) {
                val manufacturerId = manufacturerSpecificData.keyAt(i)
                val data = manufacturerSpecificData.get(manufacturerId) ?: continue

                // Log pro každé manufacturer data
                Log.d("BeaconScanner", "Manufacturer ID: 0x${manufacturerId.toString(16).uppercase()}, Data size: ${data.size}")

                // Kontrola, zda data vypadají jako beacon data (mají dostatečnou délku)
                if (data.size >= 20) {
                    // Zkusit interpretovat jako iBeacon (Apple)
                    if (data.size >= 23 && data[0] == 0x02.toByte() && data[1] == 0x15.toByte()) {
                        // Extrakce UUID (16 bajtů)
                        val uuidBytes = data.copyOfRange(2, 18)
                        val uuid = parseUuidFromBytes(uuidBytes)

                        // Extrakce Major (2 bajty)
                        val major = ((data[18].toInt() and 0xff) shl 8) or (data[19].toInt() and 0xff)

                        // Extrakce Minor (2 bajty)
                        val minor = ((data[20].toInt() and 0xff) shl 8) or (data[21].toInt() and 0xff)

                        Log.d("BeaconScanner", "Detekován iBeacon (Apple format): UUID=$uuid, Major=$major, Minor=$minor, RSSI=${result.rssi}")
                        callback.onBeaconFound(Beacon(uuid, major, minor, result.rssi))
                    }
                    // Microsoft beacon formát (může mít jiný formát než Apple)
                    else if (manufacturerId == 0x0006) { // Microsoft Company ID
                        Log.d("BeaconScanner", "Detekován Microsoft beacon, zpracovávám...")
                        // Pokusit se interpretovat data jako Microsoft beacon
                        // Formát se může lišit, proto logujeme všechna data pro analýzu
                        Log.d("BeaconScanner", "Raw data: ${data.joinToString(" ") { "%02X".format(it) }}")

                        // Pokud známe přesný formát Microsoft beaconů, můžeme je zde zpracovat
                        // Pro obecné účely můžeme vytvořit univerzální UUID
                        val uuid = "MSFT" + data.take(12).joinToString("") { "%02X".format(it) }
                        // Odhadnout major/minor z dostupných dat, pokud neznáme přesný formát
                        val major = if (data.size >= 14) ((data[12].toInt() and 0xff) shl 8) or (data[13].toInt() and 0xff) else 0
                        val minor = if (data.size >= 16) ((data[14].toInt() and 0xff) shl 8) or (data[15].toInt() and 0xff) else 0

                        Log.d("BeaconScanner", "Interpretovaný Microsoft beacon: UUID=$uuid, Major=$major, Minor=$minor, RSSI=${result.rssi}")
                        callback.onBeaconFound(Beacon(uuid, major, minor, result.rssi))
                    }
                    // Jiní výrobci - obecný přístup
                    else {
                        Log.d("BeaconScanner", "Neznámý beacon formát od výrobce 0x${manufacturerId.toString(16).uppercase()}")
                        Log.d("BeaconScanner", "Raw data: ${data.joinToString(" ") { "%02X".format(it) }}")

                        // Pokusíme se extrahovat nějaké smysluplné údaje, ale nepředáme je callbacku
                        if (data.size >= 16) {
                            // Vytvoříme generický UUID pro identifikaci
                            val uuid = "GENERIC-" + manufacturerId.toString(16).uppercase() + "-" +
                                    data.take(8).joinToString("") { "%02X".format(it) }

                            // Použijeme nějaké bajty jako major/minor
                            val major = if (data.size >= 10) ((data[8].toInt() and 0xff) shl 8) or (data[9].toInt() and 0xff) else 0
                            val minor = if (data.size >= 12) ((data[10].toInt() and 0xff) shl 8) or (data[11].toInt() and 0xff) else 0

                            Log.d("BeaconScanner", "Interpretován generický beacon: UUID=$uuid, Major=$major, Minor=$minor, RSSI=${result.rssi}")

                            // POUZE LOGUJEME, ale nepředáváme do callbacku, protože UUID začíná "GENERIC"
                            // callback.onBeaconFound(Beacon(uuid, major, minor, result.rssi))
                        }
                    }
                }
            }
        }
        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e("BeaconScanner", "BLE skenování selhalo s kódem: $errorCode")
            scanning = false
        }
    }

    private fun parseUuidFromBytes(bytes: ByteArray): String {
        val bb = ByteBuffer.wrap(bytes)
        val mostSigBits = bb.long
        val leastSigBits = bb.long
        return UUID(mostSigBits, leastSigBits).toString().uppercase(Locale.ROOT)
    }

    private fun hasPermissions(): Boolean {
        // Od Androidu 12 (API 31) jsou potřeba jiná oprávnění
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                    context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            context.checkSelfPermission(android.Manifest.permission.BLUETOOTH) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                    context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_ADMIN) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                    context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }
}