package com.armorlab.securedroid.vscan

/** 查杀取消控制:长任务(并行/深扫/差异)循环中轮询,用户可随时取消 */
object ScanControl {

    @Volatile
    var cancelled: Boolean = false
        private set

    fun requestCancel() { cancelled = true }

    fun reset() { cancelled = false }
}
