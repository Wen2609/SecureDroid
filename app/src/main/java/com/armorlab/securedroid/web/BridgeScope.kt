package com.armorlab.securedroid.web

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 桥层协程作用域:应用级生命周期,与 Activity 解耦。
 *
 * 扫描等长任务跑在这里 —— 旋转/重建 Activity 后任务继续,
 * 新页面上线时经 getScanState 重放进度(见 [ScanSessions])。
 */
object BridgeScope {
    val default: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineName("bridge"))
}
