package id.pln.securevault.breach

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException

/**
 * Klien cek kebocoran email via XposedOrNot (https://xposedornot.com) — GRATIS, tanpa API key.
 *
 * Memakai REST API publik `/v1/breach-analytics`. Catatan: endpoint `/mcp` milik XposedOrNot adalah
 * server MCP (JSON-RPC, khusus agen AI), bukan untuk klien HTTP biasa, sehingga tidak dipakai di
 * aplikasi. Hasilnya dipetakan ke [PwnedClient.Breach] agar UI tidak berubah.
 */
object XposedClient {

    private const val USER_AGENT = "SecureVault-Android"
    private const val ANALYTICS_URL = "https://api.xposedornot.com/v1/breach-analytics?email="

    class XposedException(message: String) : Exception(message)

    /** @return daftar kebocoran yang memuat email; kosong bila email tidak ditemukan. */
    suspend fun breachedAccount(email: String): List<PwnedClient.Breach> = withContext(Dispatchers.IO) {
        val conn = open(ANALYTICS_URL + URLEncoder.encode(email.trim(), "UTF-8"))
        try {
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            when (code) {
                in 200..299 -> parse(body)
                400 -> throw XposedException("Format email tidak valid")
                404 -> emptyList()
                429 -> throw XposedException("Batas permintaan tercapai. Coba lagi nanti")
                else -> throw XposedException("Gagal memeriksa email (HTTP $code)")
            }
        } finally {
            conn.disconnect()
        }
    }

    /** Email bersih mengembalikan ExposedBreaches null → daftar kosong (kabar baik). */
    private fun parse(json: String): List<PwnedClient.Breach> {
        val eb = JSONObject(json).optJSONObject("ExposedBreaches") ?: return emptyList()
        val arr = eb.optJSONArray("breaches_details") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val name = o.optString("breach")
            val classes = o.optString("xposed_data")
                .split(";")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            PwnedClient.Breach(
                name = name,
                title = name,
                domain = o.optString("domain"),
                breachDate = o.optString("xposed_date"),
                pwnCount = o.optLong("xposed_records"),
                dataClasses = classes,
                isVerified = o.optString("verified").equals("Yes", ignoreCase = true)
            )
        }.sortedByDescending { it.breachDate }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/json")
        }

    fun friendlyError(e: Throwable): String = when (e) {
        is XposedException -> e.message ?: "Terjadi kesalahan"
        is UnknownHostException -> "Tidak ada koneksi internet"
        is SocketTimeoutException -> "Koneksi terlalu lama, coba lagi"
        is IOException -> "Gagal terhubung ke XposedOrNot: ${e.message}"
        else -> e.message ?: "Terjadi kesalahan"
    }
}
