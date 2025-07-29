// You can define these at the top of build.gradle.kts or even in a separate file.
val majorVersion = 1
val minorVersion = 0
val patchVersion = 0
val buildNumber = 2  // This can be incremented as needed.

plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "cz.emistr.antouchkiosk"
    compileSdk = 35

    defaultConfig {
        applicationId = "cz.emistr.antouchkiosk"
        minSdk = 28
        targetSdk = 35
        versionCode = buildNumber
        versionName = "$majorVersion.$minorVersion.$patchVersion"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters.add("armeabi-v7a")
            abiFilters.add("arm64-v8a")
            // Pro přidání další architektury, např. pro emulátor
            // abiFilters.add("x86")
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            // Debug verze bude mít jiný applicationId
            manifestPlaceholders["fileProviderAuthority"] = "cz.emistr.antouchkiosk.debug.fileprovider"
        }
        release {
            manifestPlaceholders += mapOf()
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
            manifestPlaceholders["fileProviderAuthority"] = "cz.emistr.antouchkiosk.release.fileprovider"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core Android libraries
    implementation(libs.androidx.core.ktx.v1120)
    implementation(libs.androidx.appcompat)
    implementation(libs.material.v1110)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.drawerlayout)

    // Serial Port Communication
    implementation("io.github.xmaihh:serialport:2.1.2")
    //implementation(libs.usb.serial.for1.android.v381)
    // V souboru app/build.gradle.kts
    //implementation(libs.usbserial)
    //implementation(libs.usb.serial.for1.android)

    // Jetpack Compose
    implementation("androidx.compose.ui:ui:1.7.6")
    implementation("androidx.compose.material:material:1.7.6")
    implementation("androidx.compose.ui:ui-tooling-preview:1.7.6")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Coroutines for async operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Lifecycle components
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")

    // Security for encrypted preferences
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Logging
    implementation("com.jakewharton.timber:timber:5.0.1")

    // JSON processing (pro budoucí konfigurace)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    // Network monitoring (pro detekci připojení)
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // ZKTeco Fingerprint SDK (pokud máš JAR soubory)
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    // Card views pro UI
    implementation("androidx.cardview:cardview:1.0.0")

    // Material Text Input Layout
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.fragment:fragment-ktx:1.5.7")

    implementation(libs.androidx.ui.android)
    implementation(fileTree(mapOf(
        "dir" to "C:\\Develop\\projects_flutter\\AnTouchKiosk\\app\\libs",
        "include" to listOf("*.aar", "*.jar")
    )))

    // Testing
    testImplementation(libs.junit.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("androidx.arch.core:core-testing:2.2.0")

    androidTestImplementation(libs.junit.junit)
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.7.6")

    // Debug dependencies
    debugImplementation("androidx.compose.ui:ui-tooling:1.7.6")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.7.6")
}