package cz.emistr.antouchkiosk

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class UserAdapter(
    private var users: List<FingerprintUser>
) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

    class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val idTextView: TextView = itemView.findViewById(R.id.textViewColId)
        val workerIdTextView: TextView = itemView.findViewById(R.id.textViewColWorkerId)
        val nameTextView: TextView = itemView.findViewById(R.id.textViewColName)
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
    }

    override fun getItemCount(): Int = users.size

    fun updateUsers(newUsers: List<FingerprintUser>) {
        this.users = newUsers
        notifyDataSetChanged()
    }
}