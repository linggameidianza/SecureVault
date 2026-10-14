package id.pln.securevault.security

import android.content.Context
import id.pln.securevault.crypto.PasswordCrypto
import java.security.MessageDigest

sealed interface PinResult {
    data object Success : PinResult
    data class Wrong(val attemptsLeft: Int) : PinResult
    data class LockedOut(val seconds: Long) : PinResult
}

/**
 * Menyimpan hash PIN (PBKDF2 + salt), bukan PIN aslinya.
 * Setelah 5x salah, aplikasi dikunci sementara (30 detik, berlipat tiap kegagalan berikutnya).
 */
class PinManager(context: Context) {

    private val prefs = context.getSharedPreferences("securevault_security", Context.MODE_PRIVATE)

    fun isPinSet(): Boolean = prefs.contains(KEY_HASH)

    fun setPin(pin: String) {
        val salt = PasswordCrypto.randomBytes(16)
        val hash = PasswordCrypto.pbkdf2(pin.toCharArray(), salt, ITERATIONS)
        prefs.edit()
            .putString(KEY_SALT, PasswordCrypto.b64(salt))
            .putString(KEY_HASH, PasswordCrypto.b64(hash))
            .putInt(KEY_FAILED, 0)
            .putLong(KEY_LOCK_UNTIL, 0)
            .putInt(KEY_LOCK_ROUND, 0)
            .apply()
    }

    fun lockoutRemainingSeconds(): Long {
        val remaining = prefs.getLong(KEY_LOCK_UNTIL, 0) - System.currentTimeMillis()
        return if (remaining > 0) (remaining + 999) / 1000 else 0
    }

    fun verify(pin: String): PinResult {
        val lockLeft = lockoutRemainingSeconds()
        if (lockLeft > 0) return PinResult.LockedOut(lockLeft)

        val salt = PasswordCrypto.unb64(prefs.getString(KEY_SALT, null) ?: return PinResult.Wrong(0))
        val stored = PasswordCrypto.unb64(prefs.getString(KEY_HASH, null) ?: return PinResult.Wrong(0))
        val hash = PasswordCrypto.pbkdf2(pin.toCharArray(), salt, ITERATIONS)

        if (MessageDigest.isEqual(hash, stored)) {
            prefs.edit().putInt(KEY_FAILED, 0).putInt(KEY_LOCK_ROUND, 0).putLong(KEY_LOCK_UNTIL, 0).apply()
            return PinResult.Success
        }

        val failed = prefs.getInt(KEY_FAILED, 0) + 1
        return if (failed >= MAX_ATTEMPTS) {
            val round = prefs.getInt(KEY_LOCK_ROUND, 0) + 1
            val seconds = BASE_LOCK_SECONDS * (1L shl (round - 1).coerceAtMost(6))
            prefs.edit()
                .putInt(KEY_FAILED, 0)
                .putInt(KEY_LOCK_ROUND, round)
                .putLong(KEY_LOCK_UNTIL, System.currentTimeMillis() + seconds * 1000)
                .apply()
            PinResult.LockedOut(seconds)
        } else {
            prefs.edit().putInt(KEY_FAILED, failed).apply()
            PinResult.Wrong(MAX_ATTEMPTS - failed)
        }
    }

    var biometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC, false)
        set(value) = prefs.edit().putBoolean(KEY_BIOMETRIC, value).apply()

    companion object {
        const val MIN_PIN = 4
        const val MAX_PIN = 12
        private const val ITERATIONS = 100_000
        private const val MAX_ATTEMPTS = 5
        private const val BASE_LOCK_SECONDS = 30L
        private const val KEY_SALT = "pin_salt"
        private const val KEY_HASH = "pin_hash"
        private const val KEY_FAILED = "pin_failed"
        private const val KEY_LOCK_UNTIL = "pin_lock_until"
        private const val KEY_LOCK_ROUND = "pin_lock_round"
        private const val KEY_BIOMETRIC = "biometric_enabled"

        fun isValidFormat(pin: String) = pin.length in MIN_PIN..MAX_PIN && pin.all { it.isDigit() }
    }
}
