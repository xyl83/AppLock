package com.selfdiscipline.applock

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * 无障碍服务：监听前台 App 切换，命中锁定策略时拉起密码拦截页
 */
class LockAccessibilityService : AccessibilityService() {

    private lateinit var prefs: PrefsManager

    // 节流：避免窗口事件抖动导致拦截页被反复拉起
    private var lastLaunchPkg: String? = null
    private var lastLaunchAt: Long = 0L

    companion object {
        private const val SAME_PKG_LAUNCH_INTERVAL_MS = 800L

        // 这些系统窗口短暂覆盖在前台时，不代表用户真的离开了被锁应用
        private const val PKG_SYSTEM_UI = "com.android.systemui"
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = PrefsManager(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString() ?: return
        if (pkg.isEmpty()) return

        handleForegroundPackage(pkg)
    }

    override fun onInterrupt() {
        // 系统临时中断服务（如其他无障碍请求），忽略
    }

    /**
     * 当前前台包名的拦截判断
     */
    private fun handleForegroundPackage(pkg: String) {
        if (!::prefs.isInitialized) prefs = PrefsManager(this)

        // 1. 本 App 自己的界面（含密码拦截页）不参与判定，也不会撤销放行状态
        if (pkg == packageName) return

        // 2. 该应用刚刚验证通过密码，直接放行（解决"解锁后立刻又被拦截"的循环）
        if (pkg == prefs.getUnlockedPackage()) return

        // 3. 前台确实切换到了别的应用 → 撤销此前的放行，下次进入需要重新输密码
        //    通知栏、音量面板等系统 UI 的短暂覆盖不算离开
        if (!isTransientSystemWindow(pkg)) {
            prefs.revokeUnlock()
        }

        // 4. 未设置密码则无法校验，不拦截
        if (!prefs.hasPassword()) return

        // 5. 锁定时间已到期 → 放行
        if (prefs.isDeadlineReached()) return

        // 6. 不在锁定名单
        if (!prefs.isPackageLocked(pkg)) return

        launchLockOverlay(pkg)
    }

    private fun isTransientSystemWindow(pkg: String): Boolean =
        pkg == PKG_SYSTEM_UI || pkg.contains("inputmethod")

    private fun launchLockOverlay(targetPkg: String) {
        val now = System.currentTimeMillis()
        if (targetPkg == lastLaunchPkg && now - lastLaunchAt < SAME_PKG_LAUNCH_INTERVAL_MS) return
        lastLaunchPkg = targetPkg
        lastLaunchAt = now

        val intent = Intent(this, LockOverlayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            putExtra(LockOverlayActivity.EXTRA_TARGET_PACKAGE, targetPkg)
        }
        startActivity(intent)
    }
}
