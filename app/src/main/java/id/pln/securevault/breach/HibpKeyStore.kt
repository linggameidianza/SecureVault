package id.pln.securevault.breach

import android.content.Context
import id.pln.securevault.crypto.CryptoManager

/** Menyimpan API key HIBP secara terenkripsi (Android Keystore). */
class HibpKeyStore(context: Context, private val crypto: CryptoManager) {
    private val prefs = context.getSharedPreferences("securevault_hibp", Context.MODE_PRIVATE)

    fun get(): String? =
        prefs.getString(KEY, null)
            ?.let { runCatching { crypto.decrypt(it) }.getOrNull() }
            ?.takeIf { it.isNotBlank() }

    fun set(apiKey: String?) {
        if (apiKey.isNullOrBlank()) {
            prefs.edit().remove(KEY).apply()
        } else {
            prefs.edit().putString(KEY, crypto.encrypt(apiKey.trim())).apply()
        }
    }

    fun hasKey(): Boolean = prefs.contains(KEY)

    private companion object {
        const val KEY = "hibp_api_key"
    }
}
