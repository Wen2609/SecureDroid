package com.armorlab.securedroid.web.handlers

import android.content.Context
import com.armorlab.securedroid.root.RootGuard
import com.armorlab.securedroid.web.AppMode
import com.armorlab.securedroid.web.AppModeStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 初始化模式选择:页面启动时先 [getMode] 判断是否已强制选择;
 * [setMode] 持久化所选模式,并在超级用户模式下联动探测 su 与 Root 模式开关。
 */
class ModeHandler(private val app: Context) {

    fun getMode(): String = JSONObject()
        .put("selected", AppModeStore.isSelected(app))
        .put("mode", AppModeStore.current(app).id)
        .put("rootMode", RootGuard.isRootMode(app))
        .toString()

    suspend fun setMode(json: JSONObject): String = withContext(Dispatchers.Default) {
        val mode = AppMode.fromId(json.optString("mode"))
            ?: return@withContext JSONObject().put("ok", false).toString()
        AppModeStore.save(app, mode)
        when (mode) {
            AppMode.SUPERUSER -> {
                if (RootGuard.probeRoot(app)) RootGuard.setRootMode(app, true)
            }
            else -> RootGuard.setRootMode(app, false)
        }
        JSONObject().put("ok", true).put("mode", mode.id).toString()
    }
}