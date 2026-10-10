package com.armorlab.securedroid.vscan

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 查杀取消控制:按扫描类型(kind)隔离的取消标志。
 *
 * 长任务(并行/深扫/差异)循环中轮询各自 kind,用户可取消对应类型的扫描。
 * 每种扫描启动时只清除自己的取消标记,互不影响 —— 并发扫描之间不再互相踩踏。
 */
object ScanControl {

    private val flags = ConcurrentHashMap<String, AtomicBoolean>()

    /** 该类型扫描是否已被请求取消 */
    fun isCancelled(kind: String): Boolean = flags[kind]?.get() ?: false

    fun requestCancel(kind: String) {
        flags.computeIfAbsent(kind) { AtomicBoolean(false) }.set(true)
    }

    /** 启动新扫描时清除该类型的取消标记(不影响其他类型) */
    fun reset(kind: String) {
        flags[kind]?.set(false)
    }

    /** 取消所有已知类型(UI 全局取消入口) */
    fun requestCancelAll() {
        flags.values.forEach { it.set(true) }
    }
}