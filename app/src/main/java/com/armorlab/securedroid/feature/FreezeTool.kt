package com.armorlab.securedroid.feature

import com.armorlab.securedroid.root.ShellBridge

/** 应用冻结:pm disable-user / enable(root),桌面隐藏且不可运行 */
object FreezeTool {

    fun frozenPackages(): Set<String> {
        val out = ShellBridge.runSu("pm list packages -d 2>/dev/null") ?: return emptySet()
        return out.lines()
            .map { it.removePrefix("package:").trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    fun freeze(pkg: String): Boolean =
        ShellBridge.runSu("pm disable-user --user 0 '" + pkg + "'") != null

    fun unfreeze(pkg: String): Boolean =
        ShellBridge.runSu("pm enable '" + pkg + "'") != null
}
