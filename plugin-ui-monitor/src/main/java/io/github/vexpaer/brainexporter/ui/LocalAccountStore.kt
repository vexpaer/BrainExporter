package io.github.vexpaer.brainexporter.ui

import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class AuthResult(val success: Boolean, val message: String, val username: String? = null)

/** Device-local accounts. Passwords are stored only as salted PBKDF2 hashes. */
class LocalAccountStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun currentUser(): String? = preferences.getString(KEY_CURRENT_USER, null)

    fun register(username: String, password: String): AuthResult {
        val cleanName = username.trim()
        validate(cleanName, password)?.let { return AuthResult(false, it) }
        val key = accountKey(cleanName)
        if (preferences.contains(key)) return AuthResult(false, "该本地账号已存在。")

        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        val hash = derive(password, salt)
        val record = listOf(
            encode(cleanName.toByteArray(Charsets.UTF_8)),
            encode(salt),
            encode(hash),
        ).joinToString(".")
        preferences.edit {
            putString(key, record)
            putString(KEY_CURRENT_USER, cleanName)
        }
        return AuthResult(true, "注册成功，账号仅保存在这台设备。", cleanName)
    }

    fun login(username: String, password: String): AuthResult {
        val cleanName = username.trim()
        if (cleanName.isBlank() || password.isBlank()) return AuthResult(false, "请输入账号和密码。")
        val parts = preferences.getString(accountKey(cleanName), null)?.split('.')
            ?: return AuthResult(false, "本机没有这个账号。")
        if (parts.size != 3) return AuthResult(false, "本地账号数据已损坏。")
        return try {
            val savedName = String(decode(parts[0]), Charsets.UTF_8)
            val salt = decode(parts[1])
            val expected = decode(parts[2])
            val actual = derive(password, salt)
            if (!MessageDigest.isEqual(expected, actual)) {
                AuthResult(false, "密码不正确。")
            } else {
                preferences.edit { putString(KEY_CURRENT_USER, savedName) }
                AuthResult(true, "已登录本地账号。", savedName)
            }
        } catch (_: RuntimeException) {
            AuthResult(false, "本地账号数据已损坏。")
        }
    }

    fun logout() {
        preferences.edit { remove(KEY_CURRENT_USER) }
    }

    private fun validate(username: String, password: String): String? = when {
        username.length !in 3..32 -> "账号长度需要为 3–32 个字符。"
        username.any { it.isISOControl() || it == '|' } -> "账号包含不支持的字符。"
        password.length < 6 -> "密码至少需要 6 个字符。"
        else -> null
    }

    private fun accountKey(username: String): String {
        val normalized = username.lowercase(Locale.ROOT).toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(normalized)
        return "account.${digest.joinToString("") { "%02x".format(it) }}"
    }

    private fun derive(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, HASH_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun encode(value: ByteArray): String = Base64.encodeToString(value, Base64.NO_WRAP or Base64.URL_SAFE)
    private fun decode(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP or Base64.URL_SAFE)

    private companion object {
        const val PREFERENCES = "brainexporter_accounts"
        const val KEY_CURRENT_USER = "current_user"
        const val SALT_BYTES = 16
        const val PBKDF2_ITERATIONS = 120_000
        const val HASH_BITS = 256
    }
}
