package com.armorlab.securedroid.lock

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.armorlab.securedroid.databinding.ItemLockAppBinding

class AppLockAdapter(
    private val onToggle: (Item, Boolean) -> Unit
) : ListAdapter<AppLockAdapter.Item, AppLockAdapter.VH>(DIFF) {

    data class Item(
        val appName: String,
        val packageName: String,
        var locked: Boolean
    )

    class VH(val binding: ItemLockAppBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemLockAppBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.binding.tvName.text = item.appName
        holder.binding.tvPkg.text = item.packageName
        holder.binding.swLock.setOnCheckedChangeListener(null)
        holder.binding.swLock.isChecked = item.locked
        holder.binding.swLock.setOnCheckedChangeListener { _, checked ->
            item.locked = checked
            onToggle(item, checked)
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Item>() {
            override fun areItemsTheSame(oldItem: Item, newItem: Item) =
                oldItem.packageName == newItem.packageName

            override fun areContentsTheSame(oldItem: Item, newItem: Item) =
                oldItem == newItem
        }
    }
}
