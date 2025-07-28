package cz.emistr.antouchkiosk

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class UserListFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var userAdapter: UserAdapter
    private lateinit var fingerprintManager: FingerprintManager

    private lateinit var buttonEdit: Button
    private lateinit var buttonDelete: Button
    private lateinit var buttonClose: Button

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_user_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fingerprintManager = (activity as FingerprintActivity).fingerprintManager

        recyclerView = view.findViewById(R.id.recyclerViewUsers)
        recyclerView.layoutManager = LinearLayoutManager(context)

        buttonEdit = view.findViewById(R.id.buttonEdit)
        buttonDelete = view.findViewById(R.id.buttonDelete)
        buttonClose = view.findViewById(R.id.buttonClose)

        buttonClose.setOnClickListener { activity?.finish() }
        // Funkce pro Edit a Delete budou potřebovat logiku pro výběr položky v seznamu,
        // což je pokročilejší a prozatím ji můžeme nechat neaktivní.
        buttonEdit.isEnabled = false
        buttonDelete.isEnabled = false

        loadUsers()
    }

    override fun onResume() {
        super.onResume()
        refreshUserList()
    }

    private fun loadUsers() {
        val users = fingerprintManager.getAllUsers()
        userAdapter = UserAdapter(users)
        recyclerView.adapter = userAdapter
    }

    fun refreshUserList() {
        if (::userAdapter.isInitialized) {
            userAdapter.updateUsers(fingerprintManager.getAllUsers())
        } else {
            loadUsers()
        }
    }
}