package cz.emistr.antouchkiosk

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
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
        // Zvýšení verze databáze kvůli změně schématu
        private const val DATABASE_VERSION = 2
        private const val TABLE_USERS = "users"
        private const val COLUMN_ID = "id"
        private const val COLUMN_WORKER_ID = "worker_id"
        private const val COLUMN_NAME = "name"
        private const val COLUMN_FEATURE = "feature"
        private const val COLUMN_CREATED_AT = "created_at"

        // Aktualizovaný SQL - odstraněno UNIQUE u worker_id
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

            // Upgrade schématu, pokud je potřeba
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
        return try {
            val cursor = database!!.query(
                TABLE_USERS, null, "$COLUMN_ID = ?", arrayOf(id.toString()), null, null, null
            )
            val user = if (cursor.moveToFirst()) {
                FingerprintUser(
                    id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                    workerId = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_WORKER_ID)),
                    name = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NAME)),
                    feature = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FEATURE)),
                    createdAt = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CREATED_AT))
                )
            } else {
                null
            }
            cursor.close()
            user
        } catch (e: Exception) {
            null
        }
    }

    fun getNextUserId(): Int {
        if (!isOpen) return 1
        return try {
            val cursor = database!!.rawQuery("SELECT MAX($COLUMN_ID) FROM $TABLE_USERS", null)
            val nextId = if (cursor.moveToFirst()) cursor.getInt(0) + 1 else 1
            cursor.close()
            nextId
        } catch (e: Exception) {
            1
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

    fun deleteUserById(id: Int): Boolean {
        if (!isOpen) return false
        return try {
            database!!.delete(TABLE_USERS, "$COLUMN_ID = ?", arrayOf(id.toString())) > 0
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting user by ID: $id", e)
            false
        }
    }

    fun deleteUser(userId: String): Boolean {
        if (!isOpen || database == null) {
            Log.w(TAG, "Database is not open")
            return false
        }

        return try {
            val deletedRows = database!!.delete(
                TABLE_USERS,
                "$COLUMN_ID = ?",
                arrayOf(userId)
            )

            val success = deletedRows > 0
            if (success) {
                Log.d(TAG, "User deleted successfully: $userId")
            } else {
                Log.w(TAG, "No user found to delete: $userId")
            }

            success
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting user: $userId", e)
            false
        }
    }

    fun isUserExisted(userId: String): Boolean {
        if (!isOpen || database == null) {
            Log.w(TAG, "Database is not open")
            return false
        }

        return try {
            val cursor = database!!.query(
                TABLE_USERS,
                arrayOf(COLUMN_ID),
                "$COLUMN_ID = ?",
                arrayOf(userId),
                null, null, null
            )

            val exists = cursor.count > 0
            cursor.close()

            Log.d(TAG, "User existence check for $userId: $exists")
            exists
        } catch (e: Exception) {
            Log.e(TAG, "Error checking user existence: $userId", e)
            false
        }
    }
    fun isWorkerIdRegistered(workerId: String): Boolean {
        if (!isOpen) return false
        return try {
            val cursor = database!!.query(TABLE_USERS, arrayOf(COLUMN_ID), "$COLUMN_WORKER_ID = ?", arrayOf(workerId), null, null, null)
            val exists = cursor.count > 0
            cursor.close()
            exists
        } catch (e: Exception) { false }
    }

    fun getAllUsers(): List<FingerprintUser> {
        val users = mutableListOf<FingerprintUser>()
        if (!isOpen) return users
        try {
            val cursor = database!!.query(TABLE_USERS, null, null, null, null, null, "$COLUMN_NAME ASC")
            while (cursor.moveToNext()) {
                users.add(
                    FingerprintUser(
                        id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                        workerId = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_WORKER_ID)),
                        name = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NAME)),
                        feature = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FEATURE)),
                        createdAt = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CREATED_AT))
                    )
                )
            }
            cursor.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all users", e)
        }
        return users
    }
    fun queryUserList(): HashMap<String, String> {
        val userList = HashMap<String, String>()

        if (!isOpen || database == null) {
            Log.w(TAG, "Database is not open")
            return userList
        }

        return try {
            val cursor = database!!.query(
                TABLE_USERS,
                arrayOf(COLUMN_ID, COLUMN_FEATURE),
                null, null, null, null,
                "$COLUMN_ID ASC"
            )

            while (cursor.moveToNext()) {
                val id = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ID))
                val feature = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FEATURE))
                userList[id] = feature
            }

            cursor.close()
            Log.d(TAG, "Queried ${userList.size} users from database")
            userList
        } catch (e: Exception) {
            Log.e(TAG, "Error querying user list", e)
            userList
        }
    }

    fun getCount(): Int {
        if (!isOpen || database == null) {
            Log.w(TAG, "Database is not open")
            return 0
        }

        return try {
            val cursor = database!!.rawQuery("SELECT COUNT(*) FROM $TABLE_USERS", null)
            cursor.moveToFirst()
            val count = cursor.getInt(0)
            cursor.close()

            Log.d(TAG, "Database contains $count users")
            count
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user count", e)
            0
        }
    }

    fun clear(): Boolean {
        if (!isOpen || database == null) {
            Log.w(TAG, "Database is not open")
            return false
        }

        return try {
            val deletedRows = database!!.delete(TABLE_USERS, null, null)
            Log.d(TAG, "Cleared $deletedRows users from database")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing database", e)
            false
        }
    }

    fun getUserFeature(userId: String): String? {
        if (!isOpen || database == null) {
            Log.w(TAG, "Database is not open")
            return null
        }

        return try {
            val cursor = database!!.query(
                TABLE_USERS,
                arrayOf(COLUMN_FEATURE),
                "$COLUMN_ID = ?",
                arrayOf(userId),
                null, null, null
            )

            val feature = if (cursor.moveToFirst()) {
                cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FEATURE))
            } else {
                null
            }

            cursor.close()
            Log.d(TAG, "Retrieved feature for user $userId: ${feature != null}")
            feature
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user feature: $userId", e)
            null
        }
    }

    fun updateUserFeature(userId: String, feature: String): Boolean {
        if (!isOpen || database == null) {
            Log.w(TAG, "Database is not open")
            return false
        }

        return try {
            val values = ContentValues().apply {
                put(COLUMN_FEATURE, feature)
            }

            val updatedRows = database!!.update(
                TABLE_USERS,
                values,
                "$COLUMN_ID = ?",
                arrayOf(userId)
            )

            val success = updatedRows > 0
            if (success) {
                Log.d(TAG, "User feature updated successfully: $userId")
            } else {
                Log.w(TAG, "No user found to update: $userId")
            }

            success
        } catch (e: Exception) {
            Log.e(TAG, "Error updating user feature: $userId", e)
            false
        }
    }

    /**
     * Exportuje databázi do JSON formátu.
     * @return JSON řetězec s uživateli, nebo null v případě chyby.
     */

    fun exportDatabase(): String? {
        if (!isOpen || database == null) {
            Log.w(TAG, "Database is not open")
            return null
        }

        return try {
            val users = getAllUsers()
            val json = StringBuilder()
            json.append("{\"users\":[")

            users.forEachIndexed { index, user ->
                if (index > 0) json.append(",")
                json.append("{")
                json.append("\"id\":\"${user.id}\",")
                json.append("\"feature\":\"${user.feature}\",")
                json.append("\"created_at\":\"${user.createdAt}\"")
                json.append("}")
            }

            json.append("]}")
            Log.d(TAG, "Database exported successfully")
            json.toString()
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
        if (!isOpen || database == null) {
            Log.w(TAG, "Database is not open for import")
            return -1
        }

        return try {
            val jsonObject = JSONObject(jsonString)
            val usersArray = jsonObject.getJSONArray("users")

            database!!.beginTransaction()
            clear()

            var importedCount = 0
            for (i in 0 until usersArray.length()) {
                val userObject = usersArray.getJSONObject(i)
                // Očekáváme, že JSON bude obsahovat workerId a name
                val workerId = userObject.getString("workerId")
                val name = userObject.getString("name")
                val feature = userObject.getString("feature")

                // Opravené volání metody insertUser
                if (insertUser(workerId, name, feature)) {
                    importedCount++
                }
            }

            database!!.setTransactionSuccessful()
            Log.d(TAG, "Database imported successfully, $importedCount users added.")
            importedCount
        } catch (e: Exception) {
            Log.e(TAG, "Error importing database", e)
            -1
        } finally {
            if (database!!.inTransaction()) {
                database!!.endTransaction()
            }
        }
    }
}

