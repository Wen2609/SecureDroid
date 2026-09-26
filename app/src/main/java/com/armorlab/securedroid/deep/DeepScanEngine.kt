package com.armorlab.securedroid.deep

import android.content.Context
import com.armorlab.securedroid.ui.TrojanAdapter

/** 深度查杀编排:进程内存 → 全设备文件系统 → 底层分区 */
object DeepScanEngine {

    fun run(context: Context, onPhase: (String) -> Unit): List<TrojanAdapter.UiItem> {
        val items = mutableListOf<TrojanAdapter.UiItem>()

        onPhase("阶段 1/3:运行内存进程检测…")
        items.addAll(ProcessScanner.scan(context))

        onPhase("阶段 2/3:全设备文件系统查杀…")
        items.addAll(FilesystemScanner.deepScan(context, onPhase))

        onPhase("阶段 3/3:底层分区查杀…")
        items.addAll(PartitionScanner.scanAll(context))

        onPhase("完成")
        return items
    }
}
