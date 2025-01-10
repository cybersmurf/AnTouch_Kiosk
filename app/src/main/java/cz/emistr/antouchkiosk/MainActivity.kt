// MainActivity.kt
package cz.emistr.antouchkiosk


import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_NONE
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    private lateinit var webView: WebView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var preferences: SharedPreferences
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var componentName: ComponentName

    @RequiresApi(Build.VERSION_CODES.P)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializace Device Policy Manageru
        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        componentName = ComponentName(this, MyDeviceAdminReceiver::class.java)

        // Ensure the app is the device owner before setting lock task packages
        if (devicePolicyManager.isDeviceOwnerApp(packageName)) {
            devicePolicyManager.setLockTaskPackages(componentName, arrayOf(packageName))
        } else {
            Toast.makeText(this, "App is not set as device owner", Toast.LENGTH_SHORT).show()
        }

        setContentView(R.layout.activity_main)

        preferences = getSharedPreferences("KioskPreferences", MODE_PRIVATE)

        setupWebView()
        setupDrawer()

        // Aktivace kioskového režimu, pokud je nastaven
        if (preferences.getBoolean("kiosk_mode", false)) {
            startLockTask()
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onResume() {
        super.onResume()
        // Kontrola a případné obnovení Lock Task Mode
        if (preferences.getBoolean("kiosk_mode", false) &&
            !isInLockTaskMode()) {
            startLockTask()
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun isInLockTaskMode(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return activityManager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    }

    private fun setupWebView() {
        webView = findViewById(R.id.webView)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            // Zakázání otevírání nových oken
            setSupportMultipleWindows(false)
            // Povolení zobrazení obsahu přes celou obrazovku
            loadWithOverviewMode = true
            useWideViewPort = true
            // Zakázání zoomu
            builtInZoomControls = false
            displayZoomControls = false
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                // Zajistí, že všechny URL se otevřou v aplikaci
                url?.let { view?.loadUrl(it) }
                return true
            }

            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                Toast.makeText(this@MainActivity, "Chyba načítání: $description", Toast.LENGTH_SHORT).show()
            }
        }

        // Načtení výchozí URL z preferences
        val defaultUrl = preferences.getString("default_url", "https://example.com")
        webView.loadUrl(defaultUrl ?: "https://example.com")
    }

    private fun setupDrawer() {
        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.nav_view)
        navigationView.setNavigationItemSelectedListener(this)

        // Nastavení hamburger menu ikony
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_menu) // Ujistěte se, že máte tuto ikonu v drawable
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                drawerLayout.openDrawer(GravityCompat.START)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_home -> {
                val homeUrl = preferences.getString("default_url", "https://example.com")
                webView.loadUrl(homeUrl ?: "https://example.com")
            }
            R.id.nav_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onBackPressed() {
        when {
            drawerLayout.isDrawerOpen(GravityCompat.START) -> {
                drawerLayout.closeDrawer(GravityCompat.START)
            }
            webView.canGoBack() && !isInLockTaskMode() -> {
                webView.goBack()
            }
            else -> {
                if (!isInLockTaskMode()) {
                    super.onBackPressed()
                }
            }
        }
    }

    // Metoda pro opuštění Lock Task Mode
    @RequiresApi(Build.VERSION_CODES.M)
    fun exitKioskMode() {
        if (isInLockTaskMode()) {
            stopLockTask()
        }
    }

    // Metoda pro kontrolu, zda je aplikace Device Owner
    private fun isDeviceOwner(): Boolean {
        return devicePolicyManager.isDeviceOwnerApp(packageName)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onDestroy() {
        // Ukončení Lock Task Mode při zavření aplikace
        if (isInLockTaskMode()) {
            stopLockTask()
        }
        super.onDestroy()
    }
}