package id.ichio.securevault.crypto

import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

/**
 * HANYA untuk migrasi. Mengakses kunci LAMA "securevault_master_key" yang TIDAK terikat
 * autentikasi, sehingga data lama bisa didekripsi tanpa prompt, lalu dienkripsi ulang oleh
 * [CryptoManager] (alias v2, auth-bound).
 *
 * Hapus kelas ini setelah periode migrasi dianggap selesai (semua instalasi aktif sudah lewat
 * sekali unlock). Kelas ini sengaja hanya bisa mendekripsi — tidak membuat kunci baru.
 */
class LegacyCryptoManager {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    fun hasLegacyKey(): Boolean = keyStore.containsAlias(LEGACY_ALIAS)

    private fun legacyKey() =
        (keyStore.getEntry(LEGACY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey

    fun decrypt(encoded: String): String {
        if (encoded.isEmpty()) return ""
        val all = Base64.decode(encoded, Base64.NO_WRAP)
        require(all.size > IV_SIZE) { "Ciphertext tidak valid" }
        val iv = all.copyOfRange(0, IV_SIZE)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, legacyKey(), GCMParameterSpec(TAG_BITS, iv))
        return String(cipher.doFinal(all, IV_SIZE, all.size - IV_SIZE), Charsets.UTF_8)
    }

    /** Dipanggil setelah migrasi sukses, agar kunci lama tidak tertinggal. */
    fun deleteLegacyKey() {
        if (keyStore.containsAlias(LEGACY_ALIAS)) keyStore.deleteEntry(LEGACY_ALIAS)
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val LEGACY_ALIAS = "securevault_master_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
        private const val TAG_BITS = 128
    }
}
