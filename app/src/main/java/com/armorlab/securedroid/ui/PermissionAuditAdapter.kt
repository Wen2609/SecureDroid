package com.armorlab.securedroid.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.armorlab.securedroid.R
import com.armorlab.securedroid.databinding.ItemAuditBinding
import com.armorlab.securedroid.permissions.PermissionAuditor
import com.armorlab.securedroid.scan.ThreatLevel

class PermissionAuditAdapter :
    ListAdapter<PermissionAuditor.AuditResult, PermissionAuditAdapter.VH>(DIFF) {

    class VH(val binding: ItemAuditBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemAuditBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val result = getItem(position)
        val ctx = holder.binding.root.context
        holder.binding.tvAppName.text = result.appName
        holder.binding.tvPerms.text = result.risky.joinToString(", ")
        holder.binding.tvScore.text = result.score.toString()
        // 上传稿的徽标:底色是语义色的低透明度版本,文字用语义色本身
        val (bg, color) = when (result.level) {
            ThreatLevel.CRITICAL, ThreatLevel.HIGH -> R.drawable.bg_badge_danger to R.color.status_malicious
            ThreatLevel.MEDIUM -> R.drawable.bg_badge_risky to R.color.status_risky
            ThreatLevel.LOW -> R.drawable.bg_badge_safe to R.color.status_safe
        }
        holder.binding.tvScore.setBackgroundResource(bg)
        holder.binding.tvScore.setTextColor(ContextCompat.getColor(ctx, color))
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<PermissionAuditor.AuditResult>() {
            override fun areItemsTheSame(
                oldItem: PermissionAuditor.AuditResult,
                newItem: PermissionAuditor.AuditResult
            ) = oldItem.packageName == newItem.packageName

            override fun areContentsTheSame(
                oldItem: PermissionAuditor.AuditResult,
                newItem: PermissionAuditor.AuditResult
            ) = oldItem == newItem
        }
    }
}
