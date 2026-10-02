package com.armorlab.securedroid.trojan

import com.armorlab.securedroid.scan.MultiPatternMatcher
import com.armorlab.securedroid.scan.ThreatLevel
import com.armorlab.securedroid.scan.TokenMatch

/**
 * DEX 字符串级行为规则,思路源自开源 YARA 规则引擎:
 * 对从 classes.dex 提取的可读字符串做多模式组合匹配。
 *
 * 误报修复(v1.7.3,基于真实语料实测):
 * 1. 匹配改为"整词"匹配(scan/TokenMatch):exec 不再命中 execute/execSQL/executor,
 *    xposed 不再命中 xposedcheck;
 * 2. 加"强特征"门槛 [strong]:纯 API 组合(Socket + exec、Cipher + BTC、xposed 等)
 *    在正常应用里到处都是,必须同时命中真正的恶意落点(真实 shell 路径、.locked
 *    文件、resetPassword)才判该条;
 * 3. 分级下调:除三条有判别性证据的规则外,其余全部降为 LOW 提示级 ——
 *    LOW 不再计入"感染"(见 TrojanScanner.Report.isInfected),只在结果里作提示。
 *
 * 实测(5 个第三方 APK:一加备份恢复/OTA、Dute 等):修复前 7-10 条 MEDIUM+,
 * 修复后 0 条 MEDIUM+;合成恶意样本仍能命中 CRITICAL/HIGH。
 * 已知能力损失:单纯"短信扣费"样本降为 LOW 提示(与正常短信应用无法仅凭字符串区分)。
 */
object BehaviorRules {

    data class Rule(
        val name: String,
        val level: ThreatLevel,
        val description: String,
        val patterns: List<String>,
        val minHits: Int,
        /**
         * 判别性"强特征":命中集合里必须至少出现其中一个,否则不判该规则。
         * 留空表示该规则本身已足够特异(当前仅提示级规则使用)。
         */
        val strong: List<String> = emptyList()
    )

    val rules: List<Rule> = listOf(
        Rule(
            "Trojan.Sms.Premium", ThreatLevel.LOW,
            "短信发送 API 组合(提示级:正常短信/通讯类应用也会使用)",
            listOf("sendTextMessage", "Landroid/telephony/SmsManager;", "divideMessage"), 2
        ),
        Rule(
            "Trojan.PrivEsc", ThreatLevel.LOW,
            "出现 su 路径或 Superuser 库(提示级:ROM 工具、厂商组件常见)",
            listOf("/system/bin/su", "/system/xbin/su", "libsuperuser", "Chainfire"), 1
        ),
        Rule(
            "Backdoor.ReverseShell", ThreatLevel.CRITICAL,
            "疑似反向 Shell 后门:网络套接字 + 真实 Shell 路径",
            listOf("Ljava/net/Socket;", "/system/bin/sh", "/system/xbin/sh", "exec"), 2,
            strong = listOf("/system/bin/sh", "/system/xbin/sh")
        ),
        Rule(
            "Trojan.Ransom.Crypto", ThreatLevel.HIGH,
            "疑似勒索行为:加密文件并篡改文件名 / 索要赎金",
            listOf("Ljavax/crypto/Cipher;", ".locked", "bitcoin", "readme.txt"), 2,
            strong = listOf(".locked", "readme.txt", "bitcoin")
        ),
        Rule(
            "Trojan.Lock.Ransom", ThreatLevel.CRITICAL,
            "疑似锁机木马:设备管理员锁屏并重置锁屏密码",
            listOf("lockNow", "resetPassword", "Landroid/app/admin/DevicePolicyManager;"), 2,
            strong = listOf("resetPassword")
        ),
        Rule(
            "Trojan.Lock.Keyguard", ThreatLevel.LOW,
            "使用按键守卫 API(提示级:锁机 / 整蛊类软件常见)",
            listOf("disableKeyguard"), 1
        ),
        Rule(
            "Trojan.Header.DynaLoad", ThreatLevel.LOW,
            "动态加载 + 解密执行代码(提示级:加固/热修复方案同样如此)",
            listOf("Ldalvik/system/DexClassLoader;", "Ljavax/crypto/Cipher;", "Ljava/net/HttpURLConnection;"), 2
        ),
        Rule(
            "Trojan.Spy.Capture", ThreatLevel.LOW,
            "截屏 / 录屏 / 拍照 API 组合(提示级:相机、投屏类应用常见)",
            listOf("Landroid/media/projection/MediaProjection;", "MediaRecorder", "takePicture", "Landroid/hardware/Camera;"), 2
        ),
        Rule(
            "Trojan.Hider.Disguise", ThreatLevel.LOW,
            "动态隐藏图标或伪装桌面(提示级:启动器/桌面类应用常见)",
            listOf("setComponentEnabledSetting", "android.intent.category.HOME"), 2
        ),
        Rule(
            "Virus.WebViewRce", ThreatLevel.LOW,
            "WebView 混合开发面:addJavascriptInterface + JS 开启(提示级)",
            listOf("addJavascriptInterface", "setJavaScriptEnabled"), 2
        ),
        Rule(
            "Virus.DexStager", ThreatLevel.LOW,
            "DexClassLoader + loadDex 组合(提示级:插件化框架常见)",
            listOf("Ldalvik/system/DexClassLoader;", "loadDex"), 2
        ),
        Rule(
            "Virus.ClipSpy", ThreatLevel.LOW,
            "监听剪贴板(提示级:输入法、密码管理器常见)",
            listOf("OnPrimaryClipChangedListener", "getPrimaryClip"), 2
        ),
        Rule(
            "Virus.HiddenApi", ThreatLevel.LOW,
            "调用隐藏 / 受限 API(提示级:系统工具类应用常见)",
            listOf("Ldalvik/system/VMRuntime;", "Landroid/os/SystemProperties;", "Lsun/misc/Unsafe;"), 2
        ),
        Rule(
            "Virus.HookFramework", ThreatLevel.LOW,
            "携带 Hook 框架特征(提示级:查杀 / 加固应用自身也会带)",
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
                if (idx < 0 || !hits[idx]) continue
                // 位图只是"子串出现过"的预筛(性能);这里再用词边界复核(准确度),
                // 把 exec -> execute、xposed -> xposedcheck 这类子串命中剔掉。
                if (TokenMatch.occursIn(strings, p)) matched.add(p)
            }
            if (matched.size < rule.minHits) continue
            // 判别性门槛:没有命中任何"强特征"就不判 —— 消掉纯 API 组合类误报。
            if (rule.strong.isNotEmpty() && rule.strong.none { it in matched }) continue
            detections.add(
                TrojanScanner.Detection(
                    "行为规则", rule.name, rule.level,
                    rule.description + "(命中 " + matched.size + " 项: " + matched.joinToString(", ") + ")"
                )
            )
        }
        return detections
    }
}
