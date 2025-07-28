package cz.emistr.antouchkiosk;

// soubor: FingerprintBridge.java
import android.util.Log;
import com.zkteco.android.biometric.module.fingerprintreader.FingerprintCaptureListener;
import com.zkteco.android.biometric.module.fingerprintreader.FingerprintFactory;
import com.zkteco.android.biometric.module.fingerprintreader.FingerprintSensor;
import com.zkteco.android.biometric.module.fingerprintreader.ZKFingerService;
import com.zkteco.android.biometric.module.fingerprintreader.exception.FingerprintException;


public class FingerprintBridge {
    public static int performIdentify(byte[] template, byte[] buffer) {
        if (template == null) {
            Log.e("FingerprintBridge", "Template is null!");
            return -1001;
        }
        if (buffer == null) {
            Log.e("FingerprintBridge", "Buffer is null!");
            return -1002;
        }
        Log.d("FingerprintBridge", "Calling ZKFingerService.identify from JAVA bridge...");
        // Přímé volání nativní funkce
        int result = ZKFingerService.identify(template, buffer, 70, 1);
        Log.d("FingerprintBridge", "Call finished. Result: " + result);
        return result;
    }
}