package com.armorlab.securedroid.vscan

/**
 * 恶意链接 / 仿冒网址检测(纯本地启发式,不联网):
 * 对输入的 URL 做结构与文本分析,命中特征累加风险分 —— 只判断字符串形态,
 * 绝不主动访问目标地址(URL 不进任何网络栈),避免把用户带入危险站点。
 *
 * 判级:score >= 70 危险 / >= 40 可疑 / 其余未见异常;
 * 内网与 localhost 地址直接放行(不属于公网威胁面)。
 * 特征子集按"最小误报"选取:品牌伪装 / Punycode / @ 隐藏主机 / 黑名单命中为强特征,
 * 短链 / 高风险 TLD / http 明文 / 非标准端口等为弱特征(单独命中不足以判可疑)。
 */
object PhishingDetector {

    data class Verdict(
        val score: Int,
        val findings: List<String>,
        /** danger | warn | clean | invalid */
        val level: String
    )

    /** 短链服务:真实目标被跳转层隐藏 */
    private val shorteners = setOf(
        "bit.ly", "tinyurl.com", "t.co", "goo.gl", "is.gd", "cutt.ly",
        "rb.gy", "shorturl.at", "t.cn", "bit.do"
    )

    /** 高风险 TLD:零成本注册、钓鱼滥用率高的便宜域 */
    private val riskyTlds = setOf(
        "zip", "mov", "top", "xyz", "tk", "ml", "ga", "cf", "gq",
        "click", "link", "rest", "kim", "loan", "work", "country"
    )

    /** 知名品牌 → 官方域名后缀:host 含品牌词但后缀不符即视为仿冒 */
    private val brands = mapOf(
        "google" to "google.com", "facebook" to "facebook.com",
        "instagram" to "instagram.com", "whatsapp" to "whatsapp.com",
        "paypal" to "paypal.com", "apple" to "apple.com",
        "microsoft" to "microsoft.com", "amazon" to "amazon.com",
        "netflix" to "netflix.com", "twitter" to "twitter.com",
        "telegram" to "telegram.org", "alipay" to "alipay.com",
        "taobao" to "taobao.com", "baidu" to "baidu.com",
        "qq" to "qq.com", "wechat" to "wechat.com"
    )

    /** 路径中常见于骗取凭证 / 钱包的词 */
    private val credentialWords = listOf(
        "login", "signin", "verify", "secure", "account", "update",
        "wallet", "seed", "mnemonic", "recover"
    )

    fun check(context: android.content.Context, rawInput: String): Verdict {
        val input = rawInput.trim()
        if (input.isEmpty()) return Verdict(0, listOf("请输入网址"), "invalid")

        var score = 0
        val findings = mutableListOf<String>()

        val schemeEnd = input.indexOf("://")
        val hasScheme = schemeEnd > 0
        val rest = if (hasScheme) input.substring(schemeEnd + 3) else input
        val scheme = if (hasScheme) input.substring(0, schemeEnd).lowercase() else ""

        // authority = 第一个 / 之前;剥离 userinfo(@ 前半段)
        val authority = rest.substringBefore('/').substringBefore('?').substringBefore('#')
        val atIndex = authority.lastIndexOf('@')
        val hostPort = if (atIndex >= 0) {
            score += 25
            findings.add("使用@符号隐藏真实主机")
            authority.substring(atIndex + 1)
        } else {
            authority
        }

        // host:剥端口;IPv6 [..] 取括号内
        val bracket = hostPort.indexOf('[')
        val host = if (bracket >= 0) {
            hostPort.substringAfter('[').substringBefore(']')
        } else {
            hostPort.substringBefore(':')
        }
        val port = if (bracket < 0 && hostPort.contains(':')) hostPort.substringAfter(':') else ""
        val h = host.lowercase()

        if (h.isEmpty()) return Verdict(0, listOf("网址不是有效格式"), "invalid")

        if (h == "localhost" || isPrivateIpv4(h)) {
            return Verdict(0, listOf("内网地址无需检测"), "clean")
        }

        if (!hasScheme && !h.contains('.')) {
            return Verdict(0, listOf("网址不是有效格式"), "invalid")
        }

        // 黑名单(内置 + 用户扩展)命中为强特征:host 与路径一起比对
        val target = (host + rest.substringBefore('?')).lowercase()
        val blockHit = BlocklistEngine.patterns(context).firstOrNull { target.contains(it.lowercase()) }
        if (blockHit != null) {
            score += 60
            findings.add("命中黑名单模式: $blockHit")
        }

        if (isBareIp(h)) {
            score += 30
            findings.add("直接使用IP地址")
        }

        if (h.split(".").any { it.startsWith("xn--") }) {
            score += 40
            findings.add("含Punycode编码域名常见于同形异义字仿冒")
        }

        for ((brand, official) in brands) {
            if (!h.contains(brand)) continue
            val d = h.substringBefore(':')
            if (d == official || d.endsWith(".$official") || d.endsWith(official)) continue
            score += 45
            findings.add("域名伪装知名品牌")
            break
        }

        val hostNoPort = host.lowercase()
        if (hostNoPort in shorteners) {
            score += 30
            findings.add("短链接真实目标被隐藏")
        }

        val tld = h.substringAfterLast('.', "")
        if (tld in riskyTlds) {
            score += 20
            findings.add("高风险顶级域")
        }

        if (scheme == "http") {
            score += 15
            findings.add("未加密连接")
        }

        val portNum = port.toIntOrNull()
        if (portNum != null && portNum != 80 && portNum != 443) {
            score += 15
            findings.add("使用非标准端口")
        }

        val path = rest.substringAfter('/', "").substringBefore('?')
        val pathLower = path.lowercase()
        val credWord = credentialWords.firstOrNull { pathLower.contains(it) }
        if (credWord != null) {
            score += 10
            findings.add("路径含凭证或支付相关词")
        }

        val labels = h.split(".").filter { it.isNotEmpty() }
        if (labels.size >= 5) {
            score += 10
            findings.add("层级过深的子域名")
        }

        val level = when {
            score >= 70 -> "danger"
            score >= 40 -> "warn"
            else -> "clean"
        }
        if (findings.isEmpty()) findings.add("未发现异常")
        return Verdict(score, findings, level)
    }

    private fun isBareIp(host: String): Boolean {
        val parts = host.split('.')
        if (parts.size != 4) return false
        return parts.all { p -> p.isNotEmpty() && p.length <= 3 && p.all { it.isDigit() } }
    }

    private fun isPrivateIpv4(host: String): Boolean {
        if (!isBareIp(host)) return false
        val parts = host.split('.').map { it.toIntOrNull() ?: return false }
        return when {
            parts[0] == 10 || parts[0] == 127 || parts[0] == 0 -> true
            parts[0] == 192 && parts[1] == 168 -> true
            parts[0] == 172 && parts[1] in 16..31 -> true
            parts[0] == 169 && parts[1] == 254 -> true
            else -> false
        }
    }
}
