package id.pln.securevault.breach

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.security.MessageDigest

/**
 * Klien API Have I Been Pwned (https://haveibeenpwned.com).
 *
 * - Pwned Passwords: gratis, tanpa API key. Menggunakan k-anonymity — hanya 5 karakter pertama
 *   hash SHA-1 yang dikirim; password asli TIDAK PERNAH meninggalkan perangkat.
 *
 * Cek kebocoran email dipindahkan ke [XposedClient] (XposedOrNot, tanpa API key).
 */
object PwnedClient {
    private const val USER_AGENT = "SecureVault-Android"
    private const val PASSWORDS_URL = "https://api.pwnedpasswords.com/range/"

    class HibpException(message: String) : Exception(message)

    data class Breach(
        val name: String,
        val title: String,
        val domain: String,
        val breachDate: String,
        val pwnCount: Long,
        val dataClasses: List<String>,
        val isVerified: Boolean
    )

    fun sha1Hex(text: String): String =
        MessageDigest.getInstance("SHA-1")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02X".format(it.toInt() and 0xff) }

    /** @return berapa kali password muncul di kebocoran data (0 = tidak ditemukan). */
    suspend fun passwordPwnCount(password: String): Int = withContext(Dispatchers.IO) {
        val hash = sha1Hex(password)
        val prefix = hash.substring(0, 5)
        val suffix = hash.substring(5)
        val conn = open(PASSWORDS_URL + prefix)
        // Padding agar ukuran respons tidak membocorkan informasi
        conn.setRequestProperty("Add-Padding", "true")
        try {
            val code = conn.responseCode
            if (code != 200) throw HibpException("Gagal memeriksa password (HTTP $code)")
            conn.inputStream.bufferedReader().useLines { lines ->
                lines.map { it.trim() }
                    .firstOrNull { it.substringBefore(':').equals(suffix, ignoreCase = true) }
                    ?.substringAfter(':')
                    ?.trim()
                    ?.toIntOrNull()
                    ?: 0
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/json, text/plain")
        }

    /** Pesan error yang ramah pengguna. */
    fun friendlyError(e: Throwable): String = when (e) {
        is HibpException -> e.message ?: "Terjadi kesalahan"
        is UnknownHostException -> "Tidak ada koneksi internet"
        is SocketTimeoutException -> "Koneksi terlalu lama, coba lagi"
        is IOException -> "Gagal terhubung ke HIBP: ${e.message}"
        else -> e.message ?: "Terjadi kesalahan"
    }
}
