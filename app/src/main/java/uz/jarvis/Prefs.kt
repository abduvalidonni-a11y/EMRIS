package uz.jarvis

import android.content.Context

object Prefs {
    fun sp(c: Context) = c.getSharedPreferences("jarvis", Context.MODE_PRIVATE)
}

/** Suhbat tarixi: servis yozadi, ekran o'qiydi. */
object History {
    private val sb = StringBuilder()
    var listener: ((String) -> Unit)? = null
    @Synchronized fun add(line: String) {
        sb.append(line).append("\n\n")
        if (sb.length > 8000) sb.delete(0, sb.length - 6000)
        listener?.invoke(sb.toString())
    }
    @Synchronized fun text() = sb.toString()
}
