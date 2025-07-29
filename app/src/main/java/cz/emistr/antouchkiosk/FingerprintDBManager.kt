package cz.emistr.antouchkiosk

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class FingerprintUser(
    val id: Int,
    val workerId: String,
    val name: String,
    val feature: String,
    val createdAt: String
)

class FingerprintDBManager {

    companion object {
        private const val TAG = "FingerprintDBManager"
        private const val DATABASE_VERSION = 2
        private const val TABLE_USERS = "users"
        private const val COLUMN_ID = "id"
        private const val COLUMN_WORKER_ID = "worker_id"
        private const val COLUMN_NAME = "name"
        private const val COLUMN_FEATURE = "feature"
        private const val COLUMN_CREATED_AT = "created_at"

        private const val CREATE_TABLE_USERS = """
            CREATE TABLE IF NOT EXISTS $TABLE_USERS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_WORKER_ID TEXT NOT NULL,
                $COLUMN_NAME TEXT,
                $COLUMN_FEATURE TEXT NOT NULL,
                $COLUMN_CREATED_AT DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """
    }

    private var database: SQLiteDatabase? = null
    private var isOpen = false

    fun openDatabase(dbPath: String): Boolean {
        return try {
            val dbFile = File(dbPath)
            dbFile.parentFile?.mkdirs()
            database = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY)
            if (database!!.version < DATABASE_VERSION) {
                database!!.execSQL("DROP TABLE IF EXISTS $TABLE_USERS")
                database!!.version = DATABASE_VERSION
            }
            database?.execSQL(CREATE_TABLE_USERS)
            isOpen = true
            Log.d(TAG, "Database opened successfully: $dbPath")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error opening database: $dbPath", e)
            false
        }
    }

    fun getUserById(id: Int): FingerprintUser? {
        if (!isOpen) return null
        try {
            val cursor = database!!.query(
                TABLE_USERS, null, "$COLUMN_ID = ?", arrayOf(id.toString()), null, null, null
            )
            cursor.use {
                if (it.moveToFirst()) {
                    return FingerprintUser(
                        id = it.getInt(it.getColumnIndexOrThrow(COLUMN_ID)),
                        workerId = it.getString(it.getColumnIndexOrThrow(COLUMN_WORKER_ID)),
                        name = it.getString(it.getColumnIndexOrThrow(COLUMN_NAME)),
                        feature = it.getString(it.getColumnIndexOrThrow(COLUMN_FEATURE)),
                        createdAt = it.getString(it.getColumnIndexOrThrow(COLUMN_CREATED_AT))
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user by ID: $id", e)
        }
        return null
    }

    fun getNextUserId(): Int {
        if (!isOpen) return 1
        return try {
            val cursor = database!!.rawQuery("SELECT MAX($COLUMN_ID) FROM $TABLE_USERS", null)
            cursor.use {
                if (it.moveToFirst() && !it.isNull(0)) it.getInt(0) + 1 else 1
            }
        } catch (e: Exception) {
            1
        }
    }

    fun getLastUserId(): Int {
        if (!isOpen) return 0
        return try {
            val cursor = database!!.rawQuery("SELECT MAX($COLUMN_ID) FROM $TABLE_USERS", null)
            cursor.use {
                if (it.moveToFirst() && !it.isNull(0)) it.getInt(0) else 0
            }
        } catch (e: Exception) {
            0
        }
    }

    fun closeDatabase() {
        try {
            database?.close()
            isOpen = false
            Log.d(TAG, "Database closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing database", e)
        }
    }

    fun insertUser(workerId: String, name: String, feature: String): Boolean {
        if (!isOpen) return false
        return try {
            val values = ContentValues().apply {
                put(COLUMN_WORKER_ID, workerId)
                put(COLUMN_NAME, name)
                put(COLUMN_FEATURE, feature)
            }
            database!!.insert(TABLE_USERS, null, values) != -1L
        } catch (e: Exception) {
            Log.e(TAG, "Error inserting user: $workerId", e)
            false
        }
    }

    fun getAllUsers(): List<FingerprintUser> {
        val users = mutableListOf<FingerprintUser>()
        if (!isOpen) return users
        try {
            val cursor = database!!.query(TABLE_USERS, null, null, null, null, null, "$COLUMN_NAME ASC")
            cursor.use {
                while (it.moveToNext()) {
                    users.add(
                        FingerprintUser(
                            id = it.getInt(it.getColumnIndexOrThrow(COLUMN_ID)),
                            workerId = it.getString(it.getColumnIndexOrThrow(COLUMN_WORKER_ID)),
                            name = it.getString(it.getColumnIndexOrThrow(COLUMN_NAME)),
                            feature = it.getString(it.getColumnIndexOrThrow(COLUMN_FEATURE)),
                            createdAt = it.getString(it.getColumnIndexOrThrow(COLUMN_CREATED_AT))
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all users", e)
        }
        return users
    }

    /**
     * Exportuje databázi do JSON formátu.
     * @return JSON řetězec s uživateli, nebo null v případě chyby.
     */
    fun exportDatabase(): String? {
        if (!isOpen) return null
        return try {
            val users = getAllUsers()
            val usersJsonArray = JSONArray()
            users.forEach { user ->
                val userJson = JSONObject().apply {
                    put("id", user.id)
                    put("workerId", user.workerId)
                    put("name", user.name)
                    put("feature", user.feature)
                    put("createdAt", user.createdAt)
                }
                usersJsonArray.put(userJson)
            }
            val finalJson = JSONObject().put("users", usersJsonArray)
            finalJson.toString(4) // Pretty print s odsazením 4 mezer
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting database", e)
            null
        }
    }

    /**
     * Importuje uživatele z JSON řetězce. Vymaže stávající databázi
     * a nahradí ji novými daty.
     * @param jsonString JSON data s uživateli.
     * @return Počet úspěšně naimportovaných uživatelů, nebo -1 v případě chyby.
     */
    fun importDatabase(jsonString: String): Int {
        if (!isOpen) return -1
        var importedCount = 0
        try {
            database?.beginTransaction()
            database?.delete(TABLE_USERS, null, null) // Vymazání stávajících dat
            val jsonObject = JSONObject(jsonString)
            val usersArray = jsonObject.getJSONArray("users")

            for (i in 0 until usersArray.length()) {
                val userObject = usersArray.getJSONObject(i)
                val values = ContentValues().apply {
                    put(COLUMN_ID, userObject.getInt("id"))
                    put(COLUMN_WORKER_ID, userObject.getString("workerId"))
                    put(COLUMN_NAME, userObject.getString("name"))
                    put(COLUMN_FEATURE, userObject.getString("feature"))
                    put(COLUMN_CREATED_AT, userObject.getString("createdAt"))
                }
                if (database!!.insertWithOnConflict(TABLE_USERS, null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1L) {
                    importedCount++
                }
            }
            database?.setTransactionSuccessful()
        } catch (e: Exception) {
            Log.e(TAG, "Error importing database", e)
            return -1
        } finally {
            database?.endTransaction()
        }
        return importedCount
    }

    /**
     * Smaže uživatele z databáze podle jeho unikátního ID.
     * @param id ID uživatele, který má být smazán.
     * @return True v případě úspěchu, jinak false.
     */
    fun deleteUserById(id: Int): Boolean {
        if (!isOpen) return false
        return try {
            val deletedRows = database!!.delete(TABLE_USERS, "$COLUMN_ID = ?", arrayOf(id.toString()))
            deletedRows > 0
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting user by ID: $id", e)
            false
        }
    }

}