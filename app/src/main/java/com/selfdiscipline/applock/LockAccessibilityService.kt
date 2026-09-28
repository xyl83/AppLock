package com.selfdiscipline.applock

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.text.TextUtils
import android.view.accessibility.AccessibilityEvent

/**
 * 无障碍服务：监听前台 App 切换，命中锁定策略时拉起密码拦截页
 */
class LockAccessibilityService : AccessibilityService() {

    private lateinit var prefs: PrefsManager

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = PrefsManager(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString() ?: return
        if (TextUtils.isEmpty(pkg)) return

        handleForegroundPackage(pkg)
    }

    override fun onInterrupt() {
        // 系统临时中断服务（如其他无障碍请求），忽略
    }

    /**
     * 当前前台包名的拦截判断
     */
    private fun handleForegroundPackage(pkg: String) {
        val myPkg = packageName

        // 1. 本 App 自己的页面不拦截
        if (pkg == myPkg) return

        // 2. 未设置密码则不拦截（无法验证）
        if (!prefs.hasPassword()) return

        // 3. 未到达锁定截止时间？
        if (prefs.isDeadlineReached()) return

        // 4. 是否在锁定集合中
        if (!prefs.isPackageLocked(pkg)) return

        // 命中策略 → 拉起拦截页
        launchLockOverlay(pkg)
    }

    private fun launchLockOverlay(targetPkg: String) {
        val intent = Intent(this, LockOverlayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            putExtra(LockOverlayActivity.EXTRA_TARGET_PACKAGE, targetPkg)
        }
        startActivity(intent)
    }
}
