package id.pln.securevault.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Menyediakan passphrase SQLCipher untuk enkripsi SELURUH file database.
 *
 * Passphrase acak dibungkus (AES-GCM) oleh kunci Keystore khusus DB yang **TIDAK terikat
 * autentikasi**, supaya database tetap bisa dibuka di latar belakang (pengingat tugas saat
 * boot/alarm) tanpa kehadiran pengguna.
 *
 * Pembagian tugas:
 *  - Lapisan SQLCipher (kunci ini) melindungi METADATA (judul, username, url, tugas) dari pembacaan
 *    file mentah / pencurian file.
 *  - Kerahasiaan isi sensitif (password & catatan) tetap dijaga kolom *Enc lewat [id.pln.securevault
 *    .crypto.CryptoManager] yang auth-bound, sehingga rahasia hanya terbuka setelah verifikasi.
 */
object DatabaseKey {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val DB_KEY_ALIAS = "securevault_db_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_SIZE = 12
    private const val TAG_BITS = 128
    private const val PREFS = "securevault_db"
    private const val PREF_WRAPPED = "wrapped_passphrase"
    private const val PASSPHRASE_BYTES = 32

    /** Passphrase sebagai String ASCII (Base64) agar konsisten dipakai SupportFactory maupun ATTACH KEY. */
    fun getPassphrase(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(PREF_WRAPPED, null)?.let { return String(unwrap(it), Charsets.US_ASCII) }

        val raw = ByteArray(PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }
        val passphrase = Base64.encodeToString(raw, Base64.NO_WRAP or Base64.NO_PADDING)
        val bytes = passphrase.toByteArray(Charsets.US_ASCII)
        prefs.edit().putString(PREF_WRAPPED, wrap(bytes)).apply()
        return passphrase
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(DB_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(
                DB_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                // Sengaja TIDAK setUserAuthenticationRequired: DB harus bisa dibuka di latar belakang.
                .build()
        )
        return gen.generateKey()
    }

    private fun wrap(data: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val ct = cipher.doFinal(data)
        return Base64.encodeToString(iv + ct, Base64.NO_WRAP)
    }

    private fun unwrap(encoded: String): ByteArray {
        val all = Base64.decode(encoded, Base64.NO_WRAP)
        val iv = all.copyOfRange(0, IV_SIZE)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(all, IV_SIZE, all.size - IV_SIZE)
    }
}
