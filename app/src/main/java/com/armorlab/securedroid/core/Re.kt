package com.armorlab.securedroid.core

/**
 * 共享正则常量。
 *
 * Regex 的编译(prepareMatch / 生成 Pattern)是纯开销;原实现把它们写在逐行、逐进程、
 * 逐应用的循环体内,等于对每一行输入都重新编译一次同样的模式。这里统一提到文件级常量。
 */
object Re {

    /** /proc 与命令行输出的空白分隔 */
    val WS: Regex = Regex("\\s+")

    /** 文件名安全字符过滤 */
    val UNSAFE_FILENAME: Regex = Regex("[^A-Za-z0-9._-]")

    /** iptables 输出中的 --uid-owner 编号 */
    val UID_OWNER: Regex = Regex("--uid-owner (\\d+)")

    /** dnsmasq 日志中的 IPv4 地址记录 */
    val DNS_A_RECORD: Regex = Regex("\"data\"\\s*:\\s*\"([0-9]{1,3}(?:\\.[0-9]{1,3}){3})\"")

    /** 挂载表:overlay 覆盖 /system(疑似 Magisk 类注入) */
    val MOUNT_OVERLAY: Regex = Regex("(^|\\s)/system\\s+.*\\boverlay\\b")

    /** 挂载表:/system 被以 rw 重新挂载 */
    val MOUNT_RW: Regex = Regex("\\s/system\\s+\\S+\\s+\\S*rw[\\s,]")
}
