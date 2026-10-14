package id.pln.securevault.breach

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException
import java.security.MessageDigest

/**
 * Klien API Have I Been Pwned (https://haveibeenpwned.com).
 *
 * - Pwned Passwords: gratis, tanpa API key. Menggunakan k-anonymity — hanya 5 karakter pertama
 *   hash SHA-1 yang dikirim; password asli TIDAK PERNAH meninggalkan perangkat.
 * - Breached Account (email): butuh API key HIBP berlangganan (header `hibp-api-key`).
 */
object PwnedClient {
    private const val USER_AGENT = "SecureVault-Android"
    private const val PASSWORDS_URL = "https://api.pwnedpasswords.com/range/"
    private const val ACCOUNT_URL = "https://haveibeenpwned.com/api/v3/breachedaccount/"

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

    /** @return daftar kebocoran yang memuat email; kosong bila tidak ditemukan. */
    suspend fun breachedAccount(email: String, apiKey: String): List<Breach> = withContext(Dispatchers.IO) {
        val url = ACCOUNT_URL + URLEncoder.encode(email.trim(), "UTF-8") + "?truncateResponse=false"
        val conn = open(url)
        conn.setRequestProperty("hibp-api-key", apiKey.trim())
        try {
            when (val code = conn.responseCode) {
                200 -> parseBreaches(conn.inputStream.bufferedReader().use { it.readText() })
                404 -> emptyList()
                400 -> throw HibpException("Format email tidak valid")
                401 -> throw HibpException("API key HIBP tidak valid atau langganan sudah berakhir")
                403 -> throw HibpException("Akses ditolak oleh HIBP (403)")
                429 -> {
                    val wait = conn.getHeaderField("retry-after") ?: "beberapa"
                    throw HibpException("Batas permintaan tercapai. Coba lagi dalam $wait detik")
                }
                503 -> throw HibpException("Layanan HIBP sedang tidak tersedia, coba lagi nanti")
                else -> throw HibpException("Gagal memeriksa email (HTTP $code)")
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun parseBreaches(json: String): List<Breach> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val classes = o.optJSONArray("DataClasses")
            Breach(
                name = o.optString("Name"),
                title = o.optString("Title").ifBlank { o.optString("Name") },
                domain = o.optString("Domain"),
                breachDate = o.optString("BreachDate"),
                pwnCount = o.optLong("PwnCount"),
                dataClasses = if (classes == null) emptyList() else (0 until classes.length()).map { classes.optString(it) },
                isVerified = o.optBoolean("IsVerified", true)
            )
        }.sortedByDescending { it.breachDate }
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
