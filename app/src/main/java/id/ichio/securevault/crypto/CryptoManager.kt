package id.ichio.securevault.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Dilempar bila kunci vault tidak bisa dipakai karena pengguna belum terautentikasi
 * (atau jendela validitas autentikasi sudah habis). UI harus memanggil
 * [BiometricHelper.authenticate] lalu mengulang operasi.
 */
class VaultLockedException(cause: Throwable? = null) :
    Exception("Vault terkunci: perlu autentikasi ulang", cause)

/**
 * Dilempar bila kunci vault permanen tidak berlaku lagi (mis. biometrik baru didaftarkan saat
 * [KeyGenParameterSpec.Builder.setInvalidatedByBiometricEnrollment] true, atau kredensial layar
 * perangkat dihapus). Data lama tidak bisa didekripsi lagi — pulihkan dari backup.
 */
class VaultKeyInvalidatedException(cause: Throwable? = null) :
    Exception("Kunci vault tidak berlaku lagi", cause)

/**
 * Enkripsi field sensitif (password & isi catatan) menggunakan kunci AES-256-GCM di Android
 * Keystore. BERBEDA dari versi lama: kunci ini TERIKAT autentikasi pengguna
 * (setUserAuthenticationRequired). Kunci hanya dapat dipakai selama [AUTH_VALIDITY_SECONDS] detik
 * setelah pengguna lolos biometrik kuat / kredensial perangkat. Ini membuat kunci layar
 * benar-benar menggerbangi dekripsi, bukan sekadar UI.
 */
class CryptoManager {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    @Synchronized
    private fun getKey(): SecretKey {
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            // === Inti perbaikan: kunci hanya bisa dipakai setelah autentikasi pengguna ===
            .setUserAuthenticationRequired(true)
            // Tambah fingerprint baru akan membatalkan kunci. Karena DEVICE_CREDENTIAL diizinkan,
            // selalu ada jalur autentikasi pemulihan. Set false bila tidak ingin invalidasi ini
            // (lebih ramah, sedikit kurang ketat).
            .setInvalidatedByBiometricEnrollment(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // API 30+: jendela validitas + jenis autentikator yang diterima.
            builder.setUserAuthenticationParameters(
                AUTH_VALIDITY_SECONDS,
                KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
            )
        } else {
            // API 26–29: hanya durasi validitas (detik). >0 => autentikasi berbasis waktu.
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(AUTH_VALIDITY_SECONDS)
        }

        generator.init(builder.build())
        return generator.generateKey()
    }

    fun encrypt(plain: String): String = guarded {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getKey())
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(iv + cipherText, Base64.NO_WRAP)
    }

    fun decrypt(encoded: String): String = guarded {
        if (encoded.isEmpty()) return@guarded ""
        val all = Base64.decode(encoded, Base64.NO_WRAP)
        // Perbaikan ketahanan (temuan LOW): validasi panjang sebelum copyOfRange.
        require(all.size > IV_SIZE) { "Ciphertext tidak valid" }
        val iv = all.copyOfRange(0, IV_SIZE)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(TAG_BITS, iv))
        String(cipher.doFinal(all, IV_SIZE, all.size - IV_SIZE), Charsets.UTF_8)
    }

    /**
     * True bila kunci vault bisa dipakai saat ini (pengguna terautentikasi dalam jendela validitas).
     * Dipakai UI untuk memutuskan perlu-tidaknya memunculkan prompt biometrik sebelum masuk vault.
     */
    fun isUnlocked(): Boolean = try {
        // init ENCRYPT murah & tidak berefek samping; melempar UserNotAuthenticatedException bila terkunci.
        Cipher.getInstance(TRANSFORMATION).init(Cipher.ENCRYPT_MODE, getKey())
        true
    } catch (e: UserNotAuthenticatedException) {
        false
    } catch (e: KeyPermanentlyInvalidatedException) {
        false
    }

    /** True bila kunci sudah pernah dibuat (mis. setelah onboarding/unlock pertama). */
    fun keyExists(): Boolean = keyStore.containsAlias(KEY_ALIAS)

    private inline fun <T> guarded(block: () -> T): T = try {
        block()
    } catch (e: UserNotAuthenticatedException) {
        throw VaultLockedException(e)
    } catch (e: KeyPermanentlyInvalidatedException) {
        throw VaultKeyInvalidatedException(e)
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        // Alias BARU: install lama memakai "securevault_master_key" (tanpa ikatan auth).
        // Migrasi data: dekripsi dengan alias lama -> enkripsi ulang dengan alias ini -> hapus alias lama.
        private const val KEY_ALIAS = "securevault_master_key_v2"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
        private const val TAG_BITS = 128

        /**
         * Lama (detik) kunci tetap bisa dipakai setelah satu kali autentikasi.
         * Lebih kecil = lebih aman, tetapi perlu prompt ulang lebih sering.
         * Catatan: ini jam-dinding sejak autentikasi terakhir, BUKAN berbasis aktivitas —
         * operasi di pertengahan sesi bisa melempar VaultLockedException; UI harus menangani & re-prompt.
         */
        const val AUTH_VALIDITY_SECONDS = 300
    }
}
