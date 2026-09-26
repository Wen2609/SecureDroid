package com.armorlab.securedroid.lock

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * 应用锁看门服务:监听窗口切换事件,发现受保护应用进入前台时弹出锁屏。
 */
class AppLockAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        if (!AppLockStore.isLocked(this, pkg)) return
        if (AppLockStore.isRecentlyUnlocked(this)) return
        if (!AppLockStore.hasPin(this)) return

        val intent = Intent(this, LockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
    }

    override fun onInterrupt() = Unit
}
