package com.armorlab.securedroid.vscan

import android.content.Context
import com.armorlab.securedroid.scan.SignatureDatabase
import com.armorlab.securedroid.scan.ThreatLevel
import com.armorlab.securedroid.trojan.ClamAvSignatures
import com.armorlab.securedroid.ui.TrojanAdapter

/** 签名库状态与统计:内置库 + ClamAV 兼容库的规模与来源 */
object SignatureStats {

    fun items(context: Context): List<TrojanAdapter.UiItem> {
        ClamAvSignatures.ensureLoaded(context)
        return listOf(
            TrojanAdapter.UiItem(
                "Sig.Builtin", "内置哈希特征库",
                "当前 " + SignatureDatabase.size() + " 条(含演示特征,可经 signatures.txt 扩充)",
                ThreatLevel.LOW, null, null
            ),
            TrojanAdapter.UiItem(
                "Sig.ClamAvHash", "ClamAV 整文件哈希签名(.hsb/.hdb)",
                "已加载 " + (ClamAvSignatures.hashCount() + ClamAvSignatures.md5Count()) + " 条",
                ThreatLevel.LOW, null, null
            ),
            TrojanAdapter.UiItem(
                "Sig.ClamAvBytes", "ClamAV 字节码签名(.ndb)",
                "已加载 " + ClamAvSignatures.byteCount() + " 条(支持 ? 半字节通配)",
                ThreatLevel.LOW, null, null
            ),
            TrojanAdapter.UiItem(
                "Sig.ClamAvLogical", "ClamAV 逻辑签名(.ldb)",
                "已加载 " + ClamAvSignatures.logicalCount() + " 条(子签名组合判定)",
                ThreatLevel.LOW, null, null
            ),
            TrojanAdapter.UiItem(
                "Sig.Source", "特征来源",
                "assets/signatures(内置)+ files/clamav(外部,可经 adb push 或在线更新写入)",
                ThreatLevel.LOW,
                "在\"特征库在线更新\"中配置 URL 可自动拉取 daily.cvd 解包出的 .hsb/.hdb/.ndb",
                null
            )
        )
    }
}
