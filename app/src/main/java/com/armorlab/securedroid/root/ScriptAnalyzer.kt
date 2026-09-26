package com.armorlab.securedroid.root

import com.armorlab.securedroid.scan.ThreatLevel

/**
 * Shell 脚本风险评分引擎(模块 service.sh / post-fs-data.sh / service.d 脚本):
 * 对脚本内容做多模式加权评分,判定 恶意 / 可疑 / 轻微 三档。
 * 规则侧重 Root 环境下模块脚本真正的高危行为,而非通用风格问题。
 */
object ScriptAnalyzer {

    data class Hit(val label: String, val weight: Int)

    data class Verdict(val score: Int, val hits: List<Hit>) {
        val hasFindings: Boolean get() = score >= 8
        val isMalicious: Boolean get() = score >= 45
        val isSuspicious: Boolean get() = score in 20..44
        val level: ThreatLevel
            get() = when {
                score >= 45 -> ThreatLevel.CRITICAL
                score >= 20 -> ThreatLevel.HIGH
                else -> ThreatLevel.MEDIUM
            }
    }

    private class Pattern(val weight: Int, val label: String, val needles: List<String>) {
        fun match(content: String): Boolean = needles.any { content.contains(it) }
    }

    private val patterns: List<Pattern> = listOf(
        // 下载并执行:模块脚本最常见的恶意载荷投递方式
        Pattern(40, "下载并执行远程脚本(curl/wget 管道 sh)",
            listOf("| sh", "|sh ", "| bash", "|bash ", "curl -o /data/local/tmp", "wget -O /data/local/tmp")),
        // 对抗安全软件:卸载 / 停用 / 针对安全应用包名
        Pattern(40, "对抗安全软件(pm 卸载/停用/针对安全应用)", listOf(
            "pm uninstall", "pm disable", "pm hide", "pm suspend", "pm clear", "armorlab")),
        // 挖矿
        Pattern(40, "疑似挖矿程序(xmrig/矿池协议)", listOf("xmrig", "stratum+tcp", "minerd", "cryptonight")),
        // 破坏
        Pattern(40, "破坏性操作(删除系统数据/写块设备/格式化)",
            listOf("rm -rf /", "rm -rf /data", ">/dev/block", "dd if=/dev/zero", "mkfs")),
        // 免杀载荷
        Pattern(25, "Base64 解码执行(载荷隐藏)", listOf("base64 -d", "base64 --decode")),
        // 数据外传
        Pattern(25, "数据外传(POST/上传/nc 外联)",
            listOf("curl --data", "curl -d ", "curl -t ", "-d @", "nc -", "ncat ", "curl -f ")),
        // 窃密
        Pattern(25, "窃取短信/通讯录数据库", listOf("mmssms.db", "contacts2.db", "telephony/databases")),
        // 持久化
        Pattern(20, "持久化驻留(service.d/计划任务/持久属性)", listOf(
            "/data/adb/service.d", "/data/adb/post-fs-data.d", "crontab", "setprop persist", "/system/etc/init")),
        // 注入/调试
        Pattern(20, "注入/调试框架(frida/ptrace)", listOf("frida", "ptrace", "gdb", "ld_preload")),
        // 系统篡改
        Pattern(15, "系统分区篡改(remount/写入系统目录/build.prop)", listOf(
            "remount", ">/system/bin", ">/system/etc", ">/system/lib", ">/system/xbin", "dd if=", "build.prop")),
        // 对抗取证
        Pattern(15, "对抗取证(锁定文件/挂载遮蔽/清日志)", listOf(
            "chattr +i", "mount --bind", "dmesg -c", "logcat -c", "history -c")),
        // SELinux
        Pattern(10, "放宽 SELinux 策略", listOf("sepolicy", "setenforce 0", "supolicy", "magiskpolicy")),
        // 网络工具
        Pattern(10, "可疑网络工具(tcpdump/抓包)", listOf("tcpdump"))
    )

    fun analyze(script: String): Verdict {
        val c = script.lowercase()
        val hits = mutableListOf<Hit>()
        var score = 0
        for (p in patterns) {
            if (p.match(c)) {
                hits.add(Hit(p.label, p.weight))
                score += p.weight
            }
        }
        if (score > 100) score = 100
        return Verdict(score, hits)
    }
}
