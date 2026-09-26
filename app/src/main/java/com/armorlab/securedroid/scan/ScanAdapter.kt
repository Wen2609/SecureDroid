package com.armorlab.securedroid.scan

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.armorlab.securedroid.R
import com.armorlab.securedroid.databinding.ItemScanResultBinding

class ScanAdapter :
    ListAdapter<ScannerEngine.ScanResult, ScanAdapter.VH>(DIFF) {

    class VH(val binding: ItemScanResultBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemScanResultBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val result = getItem(position)
        val ctx = holder.binding.root.context
        holder.binding.tvAppName.text = result.appName
        holder.binding.tvPackage.text = result.packageName

        when {
            result.isMalicious -> {
                holder.binding.tvStatus.text =
                    ctx.getString(R.string.status_malicious) + " · " + (result.threat?.name ?: "")
                holder.binding.tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.status_malicious))
            }
            result.isRisky -> {
                holder.binding.tvStatus.text =
                    ctx.getString(R.string.status_risky) + " (" + result.permissionRiskScore + ")"
                holder.binding.tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.status_risky))
            }
            else -> {
                holder.binding.tvStatus.setText(R.string.status_safe)
                holder.binding.tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.status_safe))
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ScannerEngine.ScanResult>() {
            override fun areItemsTheSame(
                oldItem: ScannerEngine.ScanResult,
                newItem: ScannerEngine.ScanResult
            ) = oldItem.packageName == newItem.packageName

            override fun areContentsTheSame(
                oldItem: ScannerEngine.ScanResult,
                newItem: ScannerEngine.ScanResult
            ) = oldItem == newItem
        }
    }
}
