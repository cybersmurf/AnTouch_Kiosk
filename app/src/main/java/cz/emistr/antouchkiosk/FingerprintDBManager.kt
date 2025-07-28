package cz.emistr.antouchkiosk

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import org.json.JSONObject
import java.io.File

class FingerprintDBManager {

    companion object {
        private const val TAG = "FingerprintDBManager"
        private const val DATABASE_VERSION = 1
        private const val TABLE_USERS = "users"
        private const val COLUMN_ID = "id"
        private const val COLUMN_FEATURE = "feature"

        private const val CREATE_TABLE_USERS = """
            CREATE TABLE IF NOT EXISTS $TABLE_USERS (
                $COLUMN_ID TEXT PRIMARY KEY,
                $COLUMN_FEATURE TEXT NOT NULL,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """
    }

    private var database: SQLiteDatabase? = null
    private var isOpen = false

    fun openDatabase(dbPath: String): Boolean {
        return try {
            // Ensure directory exists
            val dbFile = File(dbPath)
            dbFile.parentFile?.mkdirs()

            database = SQLiteDatabase.openOrCreateDatabase(dbPath, null)
            database?.execSQL(CREATE_TABLE_USERS)
            isOpen = true

            Log.d(TAG, "Database opened successfully: $dbPath")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error opening database: $dbPath", e)
            false
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

    fun insertUser(userId: String, feature: String): Boolean {
        if (!isOpen || database == null) {
            Log.w(TAG, "Database is not open")
            return false
        }

        return try {
            val values = ContentValues().apply {
                put(COLUMN_ID, userId)
                put(COLUMN_FEATURE, feature)
            }

            val result = database!!.insert(TABLE_USERS, null, values)
            val success = result != -1L

            if (success) {
                Log.d(TAG, "User inserted successfully: $userId")
            } else {
                Log.w(TAG, "Failed to insert user: $userId")
            }

            success
        } catch (e: Exception) {
            Log.e(TAG, "Error inserting user: $userId", e)
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

    fun getAllUsers(): List<FingerprintUser> {
        val users = mutableListOf<FingerprintUser>()

        if (!isOpen || database == null) {
            Log.w(TAG, "Database is not open")
            return users
        }

        return try {
            val cursor = database!!.query(
                TABLE_USERS,
                arrayOf(COLUMN_ID, COLUMN_FEATURE, "created_at"),
                null, null, null, null,
                "created_at DESC"
            )

            while (cursor.moveToNext()) {
                val id = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ID))
                val feature = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FEATURE))
                val createdAt = cursor.getString(cursor.getColumnIndexOrThrow("created_at"))

                users.add(FingerprintUser(id, feature, createdAt))
            }

            cursor.close()
            Log.d(TAG, "Retrieved ${users.size} users")
            users
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all users", e)
            users
        }
    }

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

            // Začátek transakce pro zrychlení a bezpečnost
            database!!.beginTransaction()

            // 1. Vymazání stávajících uživatelů
            clear()

            var importedCount = 0
            for (i in 0 until usersArray.length()) {
                val userObject = usersArray.getJSONObject(i)
                val id = userObject.getString("id")
                val feature = userObject.getString("feature")
                // created_at můžeme ignorovat, protože se nastaví automaticky

                if (insertUser(id, feature)) {
                    importedCount++
                }
            }

            database!!.setTransactionSuccessful()
            Log.d(TAG, "Database imported successfully, $importedCount users added.")
            importedCount
        } catch (e: Exception) {
            Log.e(TAG, "Error importing database", e)
            -1 // Vracíme -1 pro signalizaci chyby
        } finally {
            if (database!!.inTransaction()) {
                database!!.endTransaction() // Ukončení transakce
            }
        }
    }
}

data class FingerprintUser(
    val id: String,
    val feature: String,
    val createdAt: String
)