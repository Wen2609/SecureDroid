package com.armorlab.securedroid.trojan

import com.armorlab.securedroid.scan.MultiPatternMatcher
import com.armorlab.securedroid.scan.ThreatLevel

/**
 * DEX 字符串级行为规则,思路源自开源 YARA 规则引擎:
 * 对从 classes.dex 提取的可读字符串做多模式组合匹配,
 * 命中数量达到 minHits 即判定为可疑行为(启发式,存在误报可能)。
 */
object BehaviorRules {

    data class Rule(
        val name: String,
        val level: ThreatLevel,
        val description: String,
        val patterns: List<String>,
        val minHits: Int
    )

    val rules: List<Rule> = listOf(
        Rule(
            "Trojan.Sms.Premium", ThreatLevel.CRITICAL,
            "疑似短信扣费木马:包含静默发送短信行为",
            listOf("sendTextMessage", "Landroid/telephony/SmsManager;", "divideMessage"), 2
        ),
        Rule(
            "Trojan.PrivEsc", ThreatLevel.HIGH,
            "疑似提权利用:尝试执行 su 获取 root 权限",
            listOf("/system/bin/su", "/system/xbin/su", "libsuperuser", "Chainfire"), 1
        ),
        Rule(
            "Backdoor.ReverseShell", ThreatLevel.CRITICAL,
            "疑似反向 Shell 后门:网络套接字 + Shell 执行组合",
            listOf("Ljava/net/Socket;", "/system/bin/sh", "exec"), 2
        ),
        Rule(
            "Trojan.Ransom.Crypto", ThreatLevel.HIGH,
            "疑似勒索行为:加密文件并索要赎金",
            listOf("Ljavax/crypto/Cipher;", ".locked", "bitcoin", "BTC", "readme.txt"), 2
        ),
        Rule(
            "Trojan.Lock.Ransom", ThreatLevel.CRITICAL,
            "疑似锁机木马:设备管理员锁屏 + 重置锁屏密码组合",
            listOf("lockNow", "resetPassword", "Landroid/app/admin/DevicePolicyManager;"), 2
        ),
        Rule(
            "Trojan.Lock.Keyguard", ThreatLevel.LOW,
            "使用按键守卫 API(常见于锁机 / 整蛊类软件)",
            listOf("disableKeyguard"), 1
        ),
        Rule(
            "Trojan.Header.DynaLoad", ThreatLevel.MEDIUM,
            "动态加载 + 解密执行代码(木马常用免杀手法)",
            listOf("Ldalvik/system/DexClassLoader;", "Ljavax/crypto/Cipher;", "Ljava/net/HttpURLConnection;"), 2
        ),
        Rule(
            "Trojan.Spy.Capture", ThreatLevel.MEDIUM,
            "疑似间谍行为:截屏 / 录屏 / 拍照监控",
            listOf("Landroid/media/projection/MediaProjection;", "MediaRecorder", "takePicture", "Landroid/hardware/Camera;"), 2
        ),
        Rule(
            "Trojan.Hider.Disguise", ThreatLevel.LOW,
            "疑似伪装隐藏:动态隐藏图标或伪装桌面",
            listOf("setComponentEnabledSetting", "android.intent.category.HOME"), 2
        ),
        Rule(
            "Virus.WebViewRce", ThreatLevel.HIGH,
            "WebView 远程代码执行面:addJavascriptInterface + JS 开启",
            listOf("addJavascriptInterface", "setJavaScriptEnabled"), 2
        ),
        Rule(
            "Virus.DexStager", ThreatLevel.HIGH,
            "DexClassLoader + loadDex 组合(载荷落地执行)",
            listOf("Ldalvik/system/DexClassLoader;", "loadDex"), 2
        ),
        Rule(
            "Virus.ClipSpy", ThreatLevel.MEDIUM,
            "监听剪贴板(密码/验证码窃取面)",
            listOf("OnPrimaryClipChangedListener", "getPrimaryClip"), 2
        ),
        Rule(
            "Virus.HiddenApi", ThreatLevel.MEDIUM,
            "调用隐藏/受限 API(VMRuntime/SystemProperties/Unsafe)",
            listOf("Ldalvik/system/VMRuntime;", "Landroid/os/SystemProperties;", "Lsun/misc/Unsafe;"), 2
        ),
        Rule(
            "Virus.HookFramework", ThreatLevel.MEDIUM,
            "携带 Hook 框架特征(Xposed/Substrate/Riru)",
            listOf("xposed", "substrate", "riru"), 1
        )
    )

    @Volatile private var matcherKey: List<List<String>>? = null
    @Volatile private var matcher: MultiPatternMatcher? = null

    /**
     * 按"规则模式集合"缓存多模式匹配器。
     * 关键性能:一整套规则的模式数在 40 个左右,集中到一个匹配器里只编译一次。
     */
    private fun matcherFor(all: List<Rule>): MultiPatternMatcher {
        val key = all.map { it.patterns }
        matcher?.let { m -> if (matcherKey == key) return m }
        synchronized(this) {
            val m = matcher
            if (m != null && matcherKey == key) return m
            val built = MultiPatternMatcher(key.flatten())
            matcherKey = key
            matcher = built
            return built
        }
    }

    fun match(
        strings: Set<String>,
        extra: List<Rule> = emptyList()
    ): List<TrojanScanner.Detection> {
        val detections = mutableListOf<TrojanScanner.Detection>()
        val all = rules + extra
        // 性能:单遍多模式匹配。原实现是「每条规则的每个模式都做一次 strings.any { it.contains(p) }」,
        // 即对整个 DEX 字符串集做 40 遍左右的全量扫描(深度扫描最热的 CPU 循环);
        // 现在一次扫描拿到全部模式的命中位图,再按规则聚合。
        val m = matcherFor(all)
        val hits = m.scan(strings)
        for (rule in all) {
            val matched = mutableListOf<String>()
            for (p in rule.patterns) {
                val idx = m.indexOf(p)
                if (idx >= 0 && hits[idx]) matched.add(p)
            }
            if (matched.size >= rule.minHits) {
                detections.add(
                    TrojanScanner.Detection(
                        "行为规则", rule.name, rule.level,
                        rule.description + "(命中 " + matched.size + " 项: " + matched.joinToString(", ") + ")"
                    )
                )
            }
        }
        return detections
    }
}
