plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "cz.emistr.antouchkiosk"
    compileSdk = 35// Přidání namespace

    defaultConfig {
        applicationId = "cz.emistr.antouchkiosk"
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx.v1120)
    implementation(libs.androidx.appcompat)
    implementation(libs.material.v1110)
    implementation(libs.androidx.constraintlayout)
    implementation("com.github.mik3y:usb-serial-for-android:3.8.1")
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.drawerlayout)
    implementation("androidx.compose.ui:ui:1.7.6")
    implementation("androidx.compose.material:material:1.7.6")
    implementation("androidx.compose.ui:ui-tooling-preview:1.7.6")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation(libs.androidx.ui.android)
    testImplementation(libs.junit.junit)
    androidTestImplementation(libs.junit.junit)

}
