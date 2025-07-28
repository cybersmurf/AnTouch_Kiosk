package cz.emistr.antouchkiosk

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class ViewPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> FingerprintControlFragment()
            1 -> UserListFragment()
            else -> throw IllegalStateException("Invalid position: $position")
        }
    }
}