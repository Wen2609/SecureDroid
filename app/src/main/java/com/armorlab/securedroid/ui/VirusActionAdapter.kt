package com.armorlab.securedroid.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.armorlab.securedroid.databinding.ItemToolBinding

class VirusActionAdapter(
    private val onClick: (Action) -> Unit
) : ListAdapter<VirusActionAdapter.Action, VirusActionAdapter.VH>(DIFF) {

    data class Action(val id: String, val title: String, val sub: String)

    class VH(val binding: ItemToolBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemToolBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val a = getItem(position)
        holder.binding.tvTitle.text = a.title
        holder.binding.tvSub.text = a.sub
        holder.binding.root.setOnClickListener { onClick(a) }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Action>() {
            override fun areItemsTheSame(oldItem: Action, newItem: Action) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Action, newItem: Action) =
                oldItem == newItem
        }
    }
}
