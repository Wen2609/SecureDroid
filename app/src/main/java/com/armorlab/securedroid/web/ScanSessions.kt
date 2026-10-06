package com.armorlab.securedroid.web

import java.util.concurrent.ConcurrentHashMap

/**
 * 扫描会话注册表(进程级单例)。
 *
 * 旧实现把 `running` 集合放在 NativeBridge 实例里 —— Activity 重建后新实例
 * 的集合是空的,旧扫描(应用级协程)还在跑,页面却可以再点一次开始。
 * 会话状态提到进程级,handler 换了几茬,进行中的扫描只有一份真相;
 * getScanState 据此向新页面重放进度。
 */
object ScanSessions {

    /** 进行中的扫描类型(virus/trojan/rootkit/modules/locker) */
    val running: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** 各类型最近一次进度 {done,total} */
    private val progress = ConcurrentHashMap<String, IntArray>()

    /** @return false 表示该类型已有扫描在跑(幂等防重入) */
    fun start(kind: String): Boolean = running.add(kind)

    fun updateProgress(kind: String, done: Int, total: Int) {
        progress[kind] = intArrayOf(done, total)
    }

    fun finish(kind: String) {
        running.remove(kind)
        progress.remove(kind)
    }

    /** 正在进行的扫描快照(供 getScanAction 重放) */
    fun snapshot(): List<Triple<String, Int, Int>> = running.map { kind ->
        val p = progress[kind]
        Triple(kind, p?.get(0) ?: 0, p?.get(1) ?: 0)
    }
}
