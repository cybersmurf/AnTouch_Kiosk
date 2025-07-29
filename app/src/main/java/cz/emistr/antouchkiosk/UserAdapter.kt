package cz.emistr.antouchkiosk

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class UserAdapter(
    private var users: List<FingerprintUser>,
    private val onItemClicked: (FingerprintUser) -> Unit
) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

    private var selectedPosition = RecyclerView.NO_POSITION

    inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val idTextView: TextView = itemView.findViewById(R.id.textViewColId)
        val workerIdTextView: TextView = itemView.findViewById(R.id.textViewColWorkerId)
        val nameTextView: TextView = itemView.findViewById(R.id.textViewColName)

        init {
            itemView.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onItemClicked(users[adapterPosition])
                    notifyItemChanged(selectedPosition) // Obarvení předchozího výběru
                    selectedPosition = adapterPosition
                    notifyItemChanged(selectedPosition) // Zvýraznění nového výběru
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_user, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = users[position]
        holder.idTextView.text = user.id.toString()
        holder.workerIdTextView.text = user.workerId
        holder.nameTextView.text = user.name
        holder.itemView.isActivated = (selectedPosition == position)
    }

    override fun getItemCount(): Int = users.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateUsers(newUsers: List<FingerprintUser>) {
        this.users = newUsers
        selectedPosition = RecyclerView.NO_POSITION
        notifyDataSetChanged()
    }

    fun getSelectedUser(): FingerprintUser? {
        return if (selectedPosition != RecyclerView.NO_POSITION) {
            users[selectedPosition]
        } else {
            null
        }
    }
}