package com.armorlab.securedroid.scan

/**
 * 带"词边界"的字面量匹配。
 *
 * 背景(实测):行为规则在 DEX 字符串碎片上匹配(trojan/DexScanner 按可打印 ASCII 滑窗切串),
 * 纯 contains 会把检测器自己的关键字匹配成恶意特征 ——
 * exec 命中 execute / execSQL / executor,su 路径命中正常应用的字符串表,
 * xposed 命中 xposedcheck 这类"查杀应用自带"的字符串。
 * 用真实第三方 APK 语料复现时,一加官方"备份与恢复"、Dute 等正常应用被判成
 * 短信扣费 / 提权 / 反向 Shell 木马,误报率 100%。
 *
 * 判定规则:
 * - 模式首字符是标识符字符([A-Za-z0-9_$])时,命中处左边的字符不能也是标识符字符;
 * - 模式末字符是标识符字符时,命中处右边的字符不能也是标识符字符。
 *
 * 以非标识符字符开头/结尾的模式(如 ".locked"、"/system/bin/sh"、"Landroid/...;")
 * 只在标识符那一侧检查边界,另一端按原样匹配。
 */
object TokenMatch {

    fun isIdentifierChar(c: Char): Boolean =
        c == '_' || c == '$' || c in '0'..'9' || c in 'a'..'z' || c in 'A'..'Z'

    /** 模式在文本中是否"整词"出现(即至少有一处命中满足两侧边界) */
    fun occurs(text: String, pattern: String): Boolean {
        if (pattern.isEmpty()) return text.isNotEmpty()
        var from = 0
        while (true) {
            val at = text.indexOf(pattern, from)
            if (at < 0) return false
            if (boundaryOk(text, at, pattern)) return true
            from = at + 1
        }
    }

    fun occursIn(texts: Iterable<String>, pattern: String): Boolean {
        for (t in texts) if (occurs(t, pattern)) return true
        return false
    }

    private fun boundaryOk(text: String, at: Int, pattern: String): Boolean {
        if (isIdentifierChar(pattern[0]) && at > 0 && isIdentifierChar(text[at - 1])) return false
        val end = at + pattern.length
        if (isIdentifierChar(pattern[pattern.length - 1]) && end < text.length && isIdentifierChar(text[end])) {
            return false
        }
        return true
    }
}
