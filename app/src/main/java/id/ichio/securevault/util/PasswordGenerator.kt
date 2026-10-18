package id.ichio.securevault.util

import java.security.SecureRandom
import kotlin.math.ln

data class GeneratorOptions(
    val length: Int = 16,
    val uppercase: Boolean = true,
    val lowercase: Boolean = true,
    val digits: Boolean = true,
    val symbols: Boolean = true,
    val excludeAmbiguous: Boolean = false
)

enum class Strength(val label: String, val level: Float) {
    VERY_WEAK("Very weak", 0.15f),
    WEAK("Weak", 0.35f),
    MEDIUM("Medium", 0.6f),
    STRONG("Strong", 0.85f),
    VERY_STRONG("Very strong", 1f)
}

object PasswordGenerator {
    private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    private const val DIGITS = "0123456789"
    private const val SYMBOLS = "!@#\$%^&*()-_=+[]{};:,.?/~"
    private const val AMBIGUOUS = "Il1O0o|`'\""

    private val random = SecureRandom()

    /** Menghasilkan password acak; dijamin memuat minimal 1 karakter dari setiap jenis yang dipilih. */
    fun generate(options: GeneratorOptions): String {
        val sets = buildList {
            if (options.uppercase) add(UPPER)
            if (options.lowercase) add(LOWER)
            if (options.digits) add(DIGITS)
            if (options.symbols) add(SYMBOLS)
        }.map { set ->
            if (options.excludeAmbiguous) set.filterNot { it in AMBIGUOUS } else set
        }.filter { it.isNotEmpty() }

        require(sets.isNotEmpty()) { "Select at least one character type" }
        val length = options.length.coerceAtLeast(sets.size)

        val chars = ArrayList<Char>(length)
        sets.forEach { chars += it[random.nextInt(it.length)] }
        val all = sets.joinToString("")
        while (chars.size < length) chars += all[random.nextInt(all.length)]

        // Fisher–Yates shuffle dengan SecureRandom
        for (i in chars.size - 1 downTo 1) {
            val j = random.nextInt(i + 1)
            val tmp = chars[i]; chars[i] = chars[j]; chars[j] = tmp
        }
        return chars.joinToString("")
    }

    /** Estimasi entropi sederhana berdasarkan jenis karakter dan panjang. */
    fun entropyBits(password: String): Double {
        if (password.isEmpty()) return 0.0
        var pool = 0
        if (password.any { it.isUpperCase() }) pool += 26
        if (password.any { it.isLowerCase() }) pool += 26
        if (password.any { it.isDigit() }) pool += 10
        if (password.any { !it.isLetterOrDigit() }) pool += 32
        val unique = password.toSet().size
        val effectiveLength = minOf(password.length.toDouble(), unique * 1.5)
        return effectiveLength * (ln(pool.coerceAtLeast(1).toDouble()) / ln(2.0))
    }

    fun strength(password: String): Strength {
        val bits = entropyBits(password)
        return when {
            bits < 28 -> Strength.VERY_WEAK
            bits < 45 -> Strength.WEAK
            bits < 65 -> Strength.MEDIUM
            bits < 90 -> Strength.STRONG
            else -> Strength.VERY_STRONG
        }
    }
}
