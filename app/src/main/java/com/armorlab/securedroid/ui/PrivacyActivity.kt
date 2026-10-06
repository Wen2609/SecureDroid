package com.armorlab.securedroid.ui

import android.content.Context
import com.armorlab.securedroid.R
import com.armorlab.securedroid.permissions.PermissionAuditor
import com.armorlab.securedroid.scan.ThreatLevel

/**
 * 隐私检测:枚举持有敏感权限的应用,按风险权重累加评分排序。
 *
 * 对应上传稿(deepseek_html_20261003_008012.html)工具箱里的「隐私检测」:
 * 与"权限审计"的差异是这里聚焦敏感权限本身(短信 / 通讯录 / 定位 / 录音 / 相机等),
 * 用中文列出具体敏感项,并给高风险应用提供系统卸载入口。
 */
class PrivacyActivity : BaseListToolActivity() {

    override fun titleRes() = R.string.tool_privacy

    override fun subtitleRes(): Int? = R.string.tool_privacy_sub

    override fun load(): List<TrojanAdapter.UiItem> {
        val ctx = this
        val self = ctx.packageName
        val results = PermissionAuditor.audit(ctx).filter { it.packageName != self }
        if (results.isEmpty()) {
            return listOf(
                TrojanAdapter.UiItem(
                    "Privacy.Clean", ctx.getString(R.string.privacy_clean), "",
                    ThreatLevel.LOW, null, null
                )
            )
        }
        val highest = results.maxByOrNull { it.score }?.level ?: ThreatLevel.LOW
        val items = mutableListOf(
            TrojanAdapter.UiItem(
                "Privacy.Summary",
                ctx.getString(
                    R.string.privacy_summary_fmt,
                    results.size,
                    levelLabel(ctx, highest)
                ),
                "敏感权限按权重累加评分,分数越高越危险",
                if (highest == ThreatLevel.CRITICAL || highest == ThreatLevel.HIGH) {
                    ThreatLevel.MEDIUM
                } else {
                    ThreatLevel.LOW
                },
                null, null
            )
        )
        for (r in results) {
            val perms = r.risky.map { permLabel(ctx, it) }.distinct()
            items.add(
                TrojanAdapter.UiItem(
                    r.appName,
                    r.packageName,
                    perms.joinToString(" · "),
                    r.level,
                    if (r.level.ordinal >= ThreatLevel.HIGH.ordinal) {
                        "在系统设置中撤销其敏感权限,或卸载"
                    } else {
                        null
                    },
                    r.packageName
                )
            )
        }
        return items
    }

    private fun levelLabel(ctx: Context, level: ThreatLevel): String = ctx.getString(
        when (level) {
            ThreatLevel.CRITICAL -> R.string.threat_critical
            ThreatLevel.HIGH -> R.string.threat_high
            ThreatLevel.MEDIUM -> R.string.threat_medium
            else -> R.string.threat_low
        }
    )

    /** 敏感权限 basename → 中文名(与 PermissionAuditor 的权重表对应) */
    private fun permLabel(ctx: Context, basename: String): String {
        val res = when (basename) {
            "READ_SMS" -> R.string.perm_read_sms
            "SEND_SMS" -> R.string.perm_send_sms
            "RECEIVE_SMS" -> R.string.perm_receive_sms
            "RECEIVE_MMS" -> R.string.perm_receive_mms
            "READ_CONTACTS" -> R.string.perm_read_contacts
            "WRITE_CONTACTS" -> R.string.perm_write_contacts
            "READ_CALL_LOG" -> R.string.perm_read_call_log
            "WRITE_CALL_LOG" -> R.string.perm_write_call_log
            "CALL_PHONE" -> R.string.perm_call_phone
            "RECORD_AUDIO" -> R.string.perm_record_audio
            "CAMERA" -> R.string.perm_camera
            "ACCESS_FINE_LOCATION" -> R.string.perm_fine_location
            "ACCESS_COARSE_LOCATION" -> R.string.perm_coarse_location
            "ACCESS_BACKGROUND_LOCATION" -> R.string.perm_bg_location
            "READ_EXTERNAL_STORAGE" -> R.string.perm_read_storage
            "WRITE_EXTERNAL_STORAGE" -> R.string.perm_write_storage
            "READ_MEDIA_IMAGES" -> R.string.perm_read_media_images
            "READ_MEDIA_VIDEO" -> R.string.perm_read_media_video
            "READ_PHONE_STATE" -> R.string.perm_read_phone_state
            "READ_PHONE_NUMBERS" -> R.string.perm_read_phone_numbers
            "BODY_SENSORS" -> R.string.perm_body_sensors
            "SYSTEM_ALERT_WINDOW" -> R.string.perm_system_alert
            "GET_ACCOUNTS" -> R.string.perm_get_accounts
            "REQUEST_INSTALL_PACKAGES" -> R.string.perm_request_install
            "QUERY_ALL_PACKAGES" -> R.string.perm_query_all
            "PACKAGE_USAGE_STATS" -> R.string.perm_usage_stats
            else -> return basename
        }
        return ctx.getString(res)
    }
}
