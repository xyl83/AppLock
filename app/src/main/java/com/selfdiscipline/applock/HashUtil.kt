package com.selfdiscipline.applock

import java.security.MessageDigest

/**
 * SHA-256 密码哈希工具
 * 禁止明文存储密码
 */
object HashUtil {
    fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun matches(raw: String, hashed: String?): Boolean {
        if (hashed.isNullOrBlank()) return false
        return sha256(raw) == hashed
    }
}
