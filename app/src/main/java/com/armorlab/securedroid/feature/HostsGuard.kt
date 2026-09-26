package com.armorlab.securedroid.feature

import com.armorlab.securedroid.root.ShellBridge
import java.io.File

/** hosts 文件篡改检测(劫持银行/支付类域名是常见木马手法) */
object HostsGuard {

    private val sensitive = listOf("bank", "alipay", "tenpay", "unionpay", "apple", "google", "paypal")

    data class Finding(val line: String, val sensitiveHit: Boolean)

    fun readHosts(): String {
        return try {
            val t = File("/etc/hosts").readText()
            if (t.isBlank()) ShellBridge.runSu("cat /etc/hosts") ?: t else t
        } catch (_: Exception) {
            ShellBridge.runSu("cat /etc/hosts") ?: ""
        }
    }

    fun analyze(content: String): List<Finding> {
        val findings = mutableListOf<Finding>()
        for (raw in content.lines()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            // 放行标准回环映射
            if (line == "127.0.0.1\tlocalhost" || line == "127.0.0.1 localhost" ||
                line == "::1\tlocalhost ip6-localhost ip6-loopback" || line.startsWith("::1")) continue
            val hit = sensitive.any { line.lowercase().contains(it) }
            findings.add(Finding(line, hit))
        }
        return findings
    }
}
