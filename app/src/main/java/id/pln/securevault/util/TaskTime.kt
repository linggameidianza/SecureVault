package id.pln.securevault.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Utilitas tanggal & jam untuk pengingat tugas. */
object TaskTime {
    private val locale = Locale("id", "ID")
    private val dateFmt = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", locale)
    private val shortDateFmt = DateTimeFormatter.ofPattern("EEE, d MMM", locale)
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", locale)

    private fun zone(): ZoneId = ZoneId.systemDefault()
    private fun toLocal(ms: Long): LocalDateTime = Instant.ofEpochMilli(ms).atZone(zone()).toLocalDateTime()
    private fun toMillis(dt: LocalDateTime): Long = dt.atZone(zone()).toInstant().toEpochMilli()

    fun formatDate(ms: Long): String = toLocal(ms).format(dateFmt)
    fun formatTime(ms: Long): String = toLocal(ms).format(timeFmt)
    fun hour(ms: Long): Int = toLocal(ms).hour
    fun minute(ms: Long): Int = toLocal(ms).minute

    /** "Hari ini 19:00", "Besok 08:00", "Kemarin 10:00", atau "Sen, 14 Okt 08:00". */
    fun relative(ms: Long): String {
        val dt = toLocal(ms)
        val today = LocalDate.now(zone())
        val day = when (dt.toLocalDate()) {
            today -> "Hari ini"
            today.plusDays(1) -> "Besok"
            today.minusDays(1) -> "Kemarin"
            else -> dt.format(if (dt.year == today.year) shortDateFmt else dateFmt)
        }
        return "$day ${dt.format(timeFmt)}"
    }

    /** Satu jam dari sekarang, dibulatkan ke menit 00. */
    fun nextHour(): Long {
        val now = LocalDateTime.now(zone())
        return toMillis(now.plusHours(1).withMinute(0).withSecond(0).withNano(0))
    }

    fun inOneHour(): Long = System.currentTimeMillis() + 60 * 60 * 1000L

    /** Hari ini jam [h]:[m], atau besok bila sudah lewat. */
    fun todayOrTomorrowAt(h: Int, m: Int = 0): Long {
        val now = LocalDateTime.now(zone())
        var dt = now.toLocalDate().atTime(LocalTime.of(h, m))
        if (!dt.isAfter(now)) dt = dt.plusDays(1)
        return toMillis(dt)
    }

    fun tomorrowAt(h: Int, m: Int = 0): Long =
        toMillis(LocalDate.now(zone()).plusDays(1).atTime(LocalTime.of(h, m)))

    /** Ganti tanggal (dari DatePicker, UTC tengah malam) dengan mempertahankan jam. */
    fun withPickedDate(base: Long, pickerUtcMillis: Long): Long {
        val date = Instant.ofEpochMilli(pickerUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()
        return toMillis(date.atTime(toLocal(base).toLocalTime().withSecond(0).withNano(0)))
    }

    /** Ganti jam & menit dengan mempertahankan tanggal. */
    fun withTime(base: Long, h: Int, m: Int): Long =
        toMillis(toLocal(base).toLocalDate().atTime(LocalTime.of(h, m)))

    /** Untuk nilai awal DatePicker (UTC tengah malam dari tanggal lokal). */
    fun toPickerMillis(ms: Long): Long =
        toLocal(ms).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun todayPickerMillis(): Long =
        LocalDate.now(zone()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}
