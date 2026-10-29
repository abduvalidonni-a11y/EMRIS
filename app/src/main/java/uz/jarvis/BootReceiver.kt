package uz.jarvis

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        if (i.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!Prefs.sp(ctx).getBoolean("autostart", false)) return
        // Android 14+ da bu yerdan mikrofon servisini ishga tushirish cheklangan bo'lishi mumkin.
        try {
            ContextCompat.startForegroundService(ctx, Intent(ctx, JarvisService::class.java))
        } catch (_: Exception) {
        }
    }
}
