package com.armorlab.securedroid.trojan

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
        )
    )

    fun match(strings: Set<String>): List<TrojanScanner.Detection> {
        val detections = mutableListOf<TrojanScanner.Detection>()
        for (rule in rules) {
            val matched = mutableListOf<String>()
            for (p in rule.patterns) {
                if (strings.any { it.contains(p) }) matched.add(p)
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
