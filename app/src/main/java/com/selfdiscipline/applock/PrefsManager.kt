package com.selfdiscipline.applock

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

/**
 * SharedPreferences 持久化封装
 * 所有数据存储在此类，统一管理键名
 */
class PrefsManager(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    companion object {
        private const val NAME = "applock_prefs"

        // 密码：SHA256 哈希值，禁止明文
        private const val KEY_PASSWORD_HASH = "password_hash"

        // 锁定时长（分钟）
        private const val KEY_LOCK_DURATION_MINUTES = "lock_duration_minutes"

        // 锁定截止时间戳（毫秒）
        private const val KEY_DEADLINE_TIMESTAMP = "deadline_ts"

        // 锁定的 App 包名集合（JSON数组字符串）
        private const val KEY_LOCKED_PACKAGES = "locked_packages"

        // 忠告列表（JSON数组字符串）
        private const val KEY_ADVICES = "advices"

        // 尝试计数（0~9，10次自动重置）
        private const val KEY_ATTEMPT_COUNT = "attempt_count"

        // 忠告轮询索引
        private const val KEY_ADVICE_INDEX = "advice_index"

        const val MAX_ATTEMPTS = 10

        private val DEFAULT_ADVICES = listOf(
            "自律给我自由，再坚持一下！",
            "你现在的专注，决定了你未来的高度。",
            "放下手机，去做真正重要的事。",
            "时间不会辜负每一个努力的人。",
            "目标在前方，别被眼前的诱惑带走。"
        )
    }

    // ==================== 密码 ====================

    fun hasPassword(): Boolean = sp.getString(KEY_PASSWORD_HASH, null) != null

    fun savePassword(raw: String) {
        sp.edit().putString(KEY_PASSWORD_HASH, HashUtil.sha256(raw)).apply()
    }

    fun clearPassword() {
        sp.edit().remove(KEY_PASSWORD_HASH).apply()
    }

    fun checkPassword(raw: String): Boolean =
        HashUtil.matches(raw, sp.getString(KEY_PASSWORD_HASH, null))

    // ==================== 锁定时长 & 截止时间 ====================

    var lockDurationMinutes: Int
        get() = sp.getInt(KEY_LOCK_DURATION_MINUTES, 0)
        set(value) = sp.edit().putInt(KEY_LOCK_DURATION_MINUTES, value).apply()

    /**
     * 设置锁定截止时间戳（毫秒）
     * @param minutes 从现在起多少分钟后截止
     */
    fun setDeadlineFromNow(minutes: Int) {
        val deadline = System.currentTimeMillis() + minutes * 60_000L
        sp.edit().putLong(KEY_DEADLINE_TIMESTAMP, deadline).apply()
    }

    var deadlineTimestamp: Long
        get() = sp.getLong(KEY_DEADLINE_TIMESTAMP, 0L)
        set(value) = sp.edit().putLong(KEY_DEADLINE_TIMESTAMP, value).apply()

    /** 是否已到达截止时间（锁定已到期，放行） */
    fun isDeadlineReached(): Boolean {
        val dl = deadlineTimestamp
        if (dl <= 0L) return true // 未设置过截止时间视为已到期
        return System.currentTimeMillis() >= dl
    }

    // ==================== 锁定的 App 包名集合 ====================

    fun getLockedPackages(): Set<String> {
        val json = sp.getString(KEY_LOCKED_PACKAGES, "[]") ?: "[]"
        val arr = JSONArray(json)
        val set = LinkedHashSet<String>()
        for (i in 0 until arr.length()) {
            set.add(arr.getString(i))
        }
        return set
    }

    fun setLockedPackages(packages: Set<String>) {
        val arr = JSONArray()
        packages.forEach { arr.put(it) }
        sp.edit().putString(KEY_LOCKED_PACKAGES, arr.toString()).apply()
    }

    fun toggleLockedPackage(pkg: String, locked: Boolean) {
        val set = getLockedPackages().toMutableSet()
        if (locked) set.add(pkg) else set.remove(pkg)
        setLockedPackages(set)
    }

    fun isPackageLocked(pkg: String): Boolean = getLockedPackages().contains(pkg)

    // ==================== 忠告列表 ====================

    fun getAdvices(): List<String> {
        val json = sp.getString(KEY_ADVICES, null)
        if (json.isNullOrBlank()) {
            // 首次启动，写入默认忠告
            setAdvices(DEFAULT_ADVICES)
            return DEFAULT_ADVICES
        }
        val arr = JSONArray(json)
        val list = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) {
            list.add(arr.getString(i))
        }
        return list
    }

    fun setAdvices(advices: List<String>) {
        val arr = JSONArray()
        advices.forEach { arr.put(it) }
        sp.edit().putString(KEY_ADVICES, arr.toString()).apply()
    }

    /**
     * 获取下一条忠告（轮询）
     */
    fun nextAdvice(): String {
        val list = getAdvices()
        if (list.isEmpty()) return "坚持就是胜利！"
        val idx = sp.getInt(KEY_ADVICE_INDEX, 0) % list.size
        sp.edit().putInt(KEY_ADVICE_INDEX, (idx + 1) % list.size).apply()
        return list[idx]
    }

    // ==================== 尝试计数 ====================

    /**
     * 每次提交密码（无论对错）都调用一次
     * @return 提交后的计数值（1~MAX_ATTEMPTS）
     *         到达 MAX_ATTEMPTS 后自动重置为 0，下一次返回 1
     */
    fun incrementAttemptCount(): Int {
        var count = sp.getInt(KEY_ATTEMPT_COUNT, 0) + 1
        if (count >= MAX_ATTEMPTS) {
            count = 0 // 10次用完归零
        }
        sp.edit().putInt(KEY_ATTEMPT_COUNT, count).apply()
        return if (count == 0) MAX_ATTEMPTS else count
    }

    /**
     * 当前计数值（0 表示还没提交过；1~MAX_ATTEMPTS 表示已提交次数）
     */
    fun getAttemptCount(): Int {
        val raw = sp.getInt(KEY_ATTEMPT_COUNT, 0)
        return if (raw == 0) 0 else raw
    }

    fun resetAttemptCount() {
        sp.edit().putInt(KEY_ATTEMPT_COUNT, 0).apply()
    }
}
