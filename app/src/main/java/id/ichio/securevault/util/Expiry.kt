package id.ichio.securevault.util

import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Date
import java.util.Locale

/** Utilitas masa berlaku (expired) password. */
object Expiry {
    const val DAY_MS = 86_400_000L

    /** Notifikasi dikirim bila sisa masa berlaku ≤ 2 hari. */
    const val WARN_DAYS = 2

    /** Pilihan interval (hari). 0 = tidak ada masa berlaku. */
    val PRESET_DAYS = listOf(30, 60, 90, 180, 365)

    enum class Status { NONE, OK, SOON, EXPIRED }

    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.US)

    fun status(expiresAt: Long?, now: Long = System.currentTimeMillis()): Status = when {
        expiresAt == null -> Status.NONE
        expiresAt <= now -> Status.EXPIRED
        expiresAt - now <= WARN_DAYS * DAY_MS -> Status.SOON
        else -> Status.OK
    }

    fun needsAttention(expiresAt: Long?): Boolean =
        status(expiresAt).let { it == Status.SOON || it == Status.EXPIRED }

    /** Sisa hari dibulatkan ke atas (mis. 1,2 hari → 2). */
    fun daysLeft(expiresAt: Long, now: Long = System.currentTimeMillis()): Long {
        val diff = expiresAt - now
        return if (diff <= 0) 0 else (diff + DAY_MS - 1) / DAY_MS
    }

    fun formatDate(millis: Long): String = dateFormat.format(Date(millis))

    /** Teks singkat untuk label di daftar / form. */
    fun describe(expiresAt: Long?, now: Long = System.currentTimeMillis()): String {
        if (expiresAt == null) return "No expiry date"
        val date = formatDate(expiresAt)
        return when (status(expiresAt, now)) {
            Status.EXPIRED -> {
                val ago = (now - expiresAt) / DAY_MS
                if (ago < 1) "Expired today ($date)" else "Expired $ago days ago ($date)"
            }
            Status.SOON, Status.OK -> {
                val left = daysLeft(expiresAt, now)
                if (expiresAt - now < DAY_MS) "Expires in < 1 day ($date)"
                else "Expires in $left days ($date)"
            }
            Status.NONE -> "No expiry date"
        }
    }

    /** Tanggal dari DatePicker (UTC tengah malam) → akhir hari itu di zona waktu lokal. */
    fun endOfLocalDayFromPicker(utcMidnightMillis: Long): Long =
        Instant.ofEpochMilli(utcMidnightMillis)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
            .atTime(23, 59, 59)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    /** Kebalikan dari [endOfLocalDayFromPicker], untuk nilai awal DatePicker. */
    fun toPickerMillis(localMillis: Long): Long =
        Instant.ofEpochMilli(localMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
}
