package cz.emistr.antouchkiosk

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UserListFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var userAdapter: UserAdapter
    private lateinit var fingerprintManager: FingerprintManager
    private lateinit var dbManager: FingerprintDBManager

    private lateinit var buttonImport: Button
    private lateinit var buttonExport: Button
    private lateinit var buttonDelete: Button
    private lateinit var buttonClose: Button

    private val sharedViewModel: SharedFingerprintViewModel by activityViewModels()

    private val requestLegacyPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Toast.makeText(requireContext(), "Oprávnění uděleno.", Toast.LENGTH_SHORT).show()
                handleImport()
            } else {
                Toast.makeText(requireContext(), "Oprávnění k úložišti je pro import vyžadováno.", Toast.LENGTH_SHORT).show()
            }
        }

    private val requestManageStorageLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (checkStoragePermission()) {
                Toast.makeText(requireContext(), "Oprávnění uděleno.", Toast.LENGTH_SHORT).show()
                handleImport()
            } else {
                Toast.makeText(requireContext(), "Oprávnění k úložišti je pro import vyžadováno.", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_user_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val app = requireActivity().application as AntouchKioskApp
        fingerprintManager = app.fingerprintManager
        dbManager = app.dbManager

        recyclerView = view.findViewById(R.id.recyclerViewUsers)
        recyclerView.layoutManager = LinearLayoutManager(context)

        buttonImport = view.findViewById(R.id.buttonImport)
        buttonExport = view.findViewById(R.id.buttonExport)
        buttonDelete = view.findViewById(R.id.buttonDelete)
        buttonClose = view.findViewById(R.id.buttonClose)

        buttonImport.setOnClickListener { handleImport() }
        buttonExport.setOnClickListener { handleExport() }
        buttonDelete.setOnClickListener { handleDelete() }
        buttonClose.setOnClickListener { activity?.finish() }

        view.findViewById<Button>(R.id.buttonEdit).isEnabled = false
        buttonDelete.isEnabled = false

        loadUsers()
        observeViewModel()
    }

    private fun observeViewModel() {
        sharedViewModel.refreshUserListEvent.observe(viewLifecycleOwner) { shouldRefresh ->
            if (shouldRefresh == true) {
                refreshUserList()
                sharedViewModel.onRefreshUserListComplete()
            }
        }
    }

    private fun handleDelete() {
        val selectedUser = userAdapter.getSelectedUser() ?: return

        (activity as? FingerprintActivity)?.showCustomConfirmDialog(
            title = "Smazat uživatele",
            message = "Opravdu chcete smazat záznam pro uživatele '${selectedUser.name}' (ID: ${selectedUser.workerId})?",
            positiveButtonText = "Smazat"
        ) {
            val success = fingerprintManager.deleteUser(selectedUser.id)
            if (success) {
                Toast.makeText(requireContext(), "Uživatel smazán.", Toast.LENGTH_SHORT).show()
                refreshUserList()
            } else {
                Toast.makeText(requireContext(), "Smazání se nezdařilo.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleImport() {
        if (!checkStoragePermission()) {
            requestStoragePermission()
            return
        }

        val importDir = File(Environment.getExternalStorageDirectory(), "AnTouchKiosk/import/")
        if (!importDir.exists() || !importDir.isDirectory) {
            importDir.mkdirs()
            Toast.makeText(requireContext(), "Složka pro import byla vytvořena. Vložte do ní soubory (.json).\nCesta: ${importDir.path}", Toast.LENGTH_LONG).show()
            return
        }

        val backupFiles = importDir.listFiles { _, name -> name.endsWith(".json", ignoreCase = true) }?.map { it.name } ?: emptyList()

        if (backupFiles.isEmpty()) {
            Toast.makeText(requireContext(), "Ve složce '${importDir.name}' nebyly nalezeny žádné zálohy (.json).", Toast.LENGTH_LONG).show()
        } else {
            (activity as? FingerprintActivity)?.showImportDialog(backupFiles) { selectedFileName ->
                val importFile = File(importDir, selectedFileName)
                importDatabaseFromFile(importFile)
            }
        }
    }

    private fun handleExport() {
        if (!checkStoragePermission()) {
            requestStoragePermission()
            return
        }
        exportDatabaseToFile()
    }

    private fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse(String.format("package:%s", requireContext().packageName))
                requestManageStorageLauncher.launch(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                requestManageStorageLauncher.launch(intent)
            }
        } else {
            requestLegacyPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    private fun importDatabaseFromFile(file: File) {
        try {
            val jsonString = file.readText()
            val importedCount = dbManager.importDatabase(jsonString)
            if (importedCount >= 0) {
                Toast.makeText(requireContext(), "$importedCount uživatelů úspěšně naimportováno.", Toast.LENGTH_SHORT).show()
                refreshUserList()
            } else {
                Toast.makeText(requireContext(), "Chyba při importu databáze.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Nastala chyba při čtení souboru: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportDatabaseToFile() {
        val jsonString = dbManager.exportDatabase()
        if (jsonString != null) {
            try {
                val exportDir = File(Environment.getExternalStorageDirectory(), "AnTouchKiosk/export")
                if (!exportDir.exists()) {
                    exportDir.mkdirs()
                }
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val file = File(exportDir, "fingerprints_$timestamp.json")
                file.writeText(jsonString)
                Toast.makeText(requireContext(), "Databáze úspěšně exportována do:\n${file.path}", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Chyba při exportu: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadUsers() {
        val users = dbManager.getAllUsers()
        userAdapter = UserAdapter(users) {
            buttonDelete.isEnabled = true
        }
        recyclerView.adapter = userAdapter
    }

    fun refreshUserList() {
        val users = dbManager.getAllUsers()
        if (::userAdapter.isInitialized) {
            userAdapter.updateUsers(users)
        } else {
            loadUsers()
        }
        buttonDelete.isEnabled = false
    }

    override fun onDestroyView() {
        // Databáze se již nezavírá zde
        super.onDestroyView()
    }
}