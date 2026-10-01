package com.armorlab.securedroid.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.armorlab.securedroid.databinding.ItemToolBinding

class ToolsAdapter(
    private val onClick: (ToolEntry) -> Unit
) : ListAdapter<ToolsAdapter.ToolEntry, ToolsAdapter.VH>(DIFF) {

    enum class Kind {
        FULL_AUDIT, NETWORK, CLEANER
    }

    data class ToolEntry(val title: String, val sub: String, val kind: Kind)

    class VH(val binding: ItemToolBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemToolBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val entry = getItem(position)
        holder.binding.tvTitle.text = entry.title
        holder.binding.tvSub.text = entry.sub
        holder.binding.root.setOnClickListener { onClick(entry) }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ToolEntry>() {
            override fun areItemsTheSame(oldItem: ToolEntry, newItem: ToolEntry) =
                oldItem.kind == newItem.kind

            override fun areContentsTheSame(oldItem: ToolEntry, newItem: ToolEntry) =
                oldItem == newItem
        }
    }
}
