package com.armorlab.securedroid.scan

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.armorlab.securedroid.R
import com.armorlab.securedroid.databinding.ItemScanResultBinding
import com.armorlab.securedroid.trojan.TrojanScanner

class ScanAdapter :
    ListAdapter<TrojanScanner.Report, ScanAdapter.VH>(DIFF) {

    class VH(val binding: ItemScanResultBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemScanResultBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val result = getItem(position)
        val ctx = holder.binding.root.context
        holder.binding.tvAppName.text = result.appName
        holder.binding.tvPackage.text = result.packageName

        val worst = result.worstLevel
        when {
            result.isInfected -> {
                holder.binding.tvStatus.text =
                    ctx.getString(R.string.status_malicious) + " · " +
                    (result.detections.maxByOrNull { it.level.ordinal }?.name ?: "")
                holder.binding.tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.status_malicious))
            }
            worst != null -> {
                holder.binding.tvStatus.text =
                    ctx.getString(R.string.status_risky) + " (" + worst.name + ")"
                holder.binding.tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.status_risky))
            }
            else -> {
                holder.binding.tvStatus.setText(R.string.status_safe)
                holder.binding.tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.status_safe))
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<TrojanScanner.Report>() {
            override fun areItemsTheSame(
                oldItem: TrojanScanner.Report,
                newItem: TrojanScanner.Report
            ) = oldItem.packageName == newItem.packageName

            override fun areContentsTheSame(
                oldItem: TrojanScanner.Report,
                newItem: TrojanScanner.Report
            ) = oldItem == newItem
        }
    }
}
