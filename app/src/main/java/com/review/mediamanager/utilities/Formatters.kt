package com.review.mediamanager.utilities

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

object Fmt {
    fun bytes(b: Long): String {
        if (b < 1024) return "$b B"
        val exp = (ln(b.toDouble()) / ln(1024.0)).toInt().coerceAtMost(4)
        val unit = "KMGT"[exp - 1]
        return String.format(Locale.US, "%.1f %sB", b / 1024.0.pow(exp), unit)
    }

    fun duration(ms: Long): String {
        val total = ms / 1000
        val h = total / 3600; val m = (total % 3600) / 60; val s = total % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
    }

    fun date(t: Long): String = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(t))

    /** Honest quality label derived from the real pixel size. */
    fun qualityLabel(w: Int, h: Int): String {
        val short = minOf(w, h); val long = maxOf(w, h)
        return when {
            short <= 0 -> "Unknown"
            short >= 2160 || long >= 3840 -> "4K UHD"
            short >= 1440 || long >= 2560 -> "2K QHD"
            short >= 1080 || long >= 1920 -> "Full HD"
            short >= 720 || long >= 1280 -> "HD"
            else -> "SD"
        }
    }

    fun fps(f: Float?): String = when {
        f == null -> "Unknown"
        f >= 100 -> String.format(Locale.US, "%.0f fps", f)
        else -> String.format(Locale.US, "%.2f fps", f).replace(".00", "")
    }

    fun bitrate(b: Int?): String = when {
        b == null || b <= 0 -> "Unknown"
        b >= 1_000_000 -> String.format(Locale.US, "%.1f Mbps", b / 1_000_000.0)
        else -> String.format(Locale.US, "%d kbps", b / 1000)
    }

    fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..20 -> "Good evening"
        else -> "Good night"
    }
}
