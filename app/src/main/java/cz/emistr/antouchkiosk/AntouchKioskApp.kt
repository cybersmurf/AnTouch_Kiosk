package cz.emistr.antouchkiosk

import android.app.Application

class AntouchKioskApp : Application() {
    // Táto inštancia bude zdieľaná v celej aplikácii
    lateinit var fingerprintManager: FingerprintManager
        private set
    lateinit var dbManager: FingerprintDBManager // PŘIDÁNO
        private set

    override fun onCreate() {
        super.onCreate()
        // Inicializace obou manažerů při startu aplikace
        dbManager = FingerprintDBManager().apply {
            openDatabase(applicationContext.getDatabasePath("fingerprints.db").absolutePath)
        }
        // Předání sdílené instance dbManager do FingerprintManageru
        fingerprintManager = FingerprintManager(applicationContext, dbManager)
    }

    override fun onTerminate() {
        dbManager.closeDatabase() // Bezpečné uzavření databáze při ukončení aplikace
        super.onTerminate()
    }
}