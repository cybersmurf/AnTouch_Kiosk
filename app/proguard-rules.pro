# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
-keep class com.zkteco.** { *; }
-keep interface com.zkteco.** { *; }

# Ponechá všechny třídy, které implementují ZKFPCallBack, aby se zabránilo problémům s zpětným voláním
-keep class * implements com.zkteco.android.biometric.module.fingerprintreader.FingerprintSensor$ZKFPCallBack { *; }

# Ponechá třídy související s USB komunikací
-keep class com.zkteco.android.biometric.ZKUSBDeviceCtl { *; }

# Pokud používáte jakékoli další vlastní třídy, které komunikují s nativními knihovnami, přidejte je také
# Například:
-keep class cz.emistr.antouchkiosk.FingerprintBridge { *; }
-keep class cz.emistr.antouchkiosk.FingerprintManager { *; }