package id.ichio.securevault.crypto

import android.util.Base64
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Utilitas kriptografi berbasis password (PBKDF2-HMAC-SHA256 + AES-256-GCM). */
object PasswordCrypto {

    private const val KDF = "PBKDF2WithHmacSHA256"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    const val BACKUP_ITERATIONS = 210_000
    const val BACKUP_FORMAT = "SECUREVAULT_BACKUP"
    const val BACKUP_VERSION = 1

    private val random = SecureRandom()

    fun randomBytes(size: Int): ByteArray = ByteArray(size).also { random.nextBytes(it) }

    fun pbkdf2(password: CharArray, salt: ByteArray, iterations: Int, keyBits: Int = 256): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, keyBits)
        try {
            return SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /** Enkripsi data backup, hasilnya JSON "amplop" berisi salt, iv dan ciphertext. */
    fun encryptBackup(plain: ByteArray, password: CharArray): String {
        val salt = randomBytes(16)
        val iv = randomBytes(12)
        val key = pbkdf2(password, salt, BACKUP_ITERATIONS)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD(BACKUP_FORMAT.toByteArray())
        val ct = cipher.doFinal(plain)
        key.fill(0)
        return JSONObject()
            .put("format", BACKUP_FORMAT)
            .put("version", BACKUP_VERSION)
            .put("kdf", KDF)
            .put("iterations", BACKUP_ITERATIONS)
            .put("salt", b64(salt))
            .put("iv", b64(iv))
            .put("data", b64(ct))
            .toString()
    }

    /** @throws IllegalArgumentException bila format tidak dikenali / password salah. */
    fun decryptBackup(envelopeJson: String, password: CharArray): ByteArray {
        val env = try {
            JSONObject(envelopeJson)
        } catch (e: Exception) {
            throw IllegalArgumentException("Not a valid SecureVault backup file")
        }
        if (env.optString("format") != BACKUP_FORMAT) {
            throw IllegalArgumentException("Not a valid SecureVault backup file")
        }
        if (env.optInt("version") > BACKUP_VERSION) {
            throw IllegalArgumentException("Backup version is newer than this app")
        }
        val salt = unb64(env.getString("salt"))
        val iv = unb64(env.getString("iv"))
        val data = unb64(env.getString("data"))
        val iterations = env.optInt("iterations", BACKUP_ITERATIONS)
        val key = pbkdf2(password, salt, iterations)
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            cipher.updateAAD(BACKUP_FORMAT.toByteArray())
            return cipher.doFinal(data)
        } catch (e: AEADBadTagException) {
            throw IllegalArgumentException("Wrong backup password or the file is corrupted")
        } finally {
            key.fill(0)
        }
    }

    fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    fun unb64(s: String): ByteArray = Base64.decode(s, Base64.NO_WRAP)
}
