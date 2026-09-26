package com.armorlab.securedroid.ui

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.armorlab.securedroid.R
import com.armorlab.securedroid.databinding.ItemTrojanBinding
import com.armorlab.securedroid.scan.ThreatLevel

class TrojanAdapter : ListAdapter<TrojanAdapter.UiItem, TrojanAdapter.VH>(DIFF) {

    data class UiItem(
        val title: String,
        val sub: String,
        val detail: String,
        val level: ThreatLevel?,
        val suggestion: String?,
        val uninstallPkg: String?
    )

    class VH(val binding: ItemTrojanBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemTrojanBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val ctx = holder.binding.root.context
        holder.binding.tvTitle.text = item.title
        holder.binding.tvSub.text = item.sub
        holder.binding.tvDetail.text = item.detail
        holder.binding.tvDetail.visibility =
            if (item.detail.isEmpty()) View.GONE else View.VISIBLE

        val colorRes = when (item.level) {
            ThreatLevel.CRITICAL, ThreatLevel.HIGH -> R.color.status_malicious
            ThreatLevel.MEDIUM -> R.color.status_risky
            else -> R.color.status_safe
        }
        holder.binding.tvTitle.setTextColor(ContextCompat.getColor(ctx, colorRes))

        if (item.suggestion.isNullOrEmpty()) {
            holder.binding.tvSuggestion.visibility = View.GONE
        } else {
            holder.binding.tvSuggestion.visibility = View.VISIBLE
            holder.binding.tvSuggestion.text =
                ctx.getString(R.string.trojan_suggestion_label) + " " + item.suggestion
        }

        if (item.uninstallPkg.isNullOrEmpty()) {
            holder.binding.btnUninstall.visibility = View.GONE
        } else {
            holder.binding.btnUninstall.visibility = View.VISIBLE
            holder.binding.btnUninstall.setOnClickListener {
                try {
                    ctx.startActivity(
                        Intent(Intent.ACTION_DELETE, Uri.parse("package:" + item.uninstallPkg))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (e: Exception) {
                    Toast.makeText(ctx, e.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<UiItem>() {
            override fun areItemsTheSame(oldItem: UiItem, newItem: UiItem) =
                oldItem.title == newItem.title && oldItem.sub == newItem.sub

            override fun areContentsTheSame(oldItem: UiItem, newItem: UiItem) =
                oldItem == newItem
        }
    }
}
