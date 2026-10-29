package uz.jarvis

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.ContactsContract
import android.provider.Settings
import android.telephony.SmsManager
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Actions {

    private fun start(ctx: Context, i: Intent) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    /** Harakatni bajaradi. Ovoz bilan aytiladigan qo'shimcha matn qaytarsa, u reply o'rniga aytiladi. */
    @Suppress("DEPRECATION", "MissingPermission")
    fun run(ctx: Context, a: JSONObject?): String? {
        if (a == null) return null
        return try {
            when (a.optString("type")) {
                "call" -> {
                    val n = a.optString("name")
                    val num = number(ctx, n) ?: return "$n kontaktlar orasida topilmadi, janob."
                    start(ctx, Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(num))))
                    null
                }
                "sms" -> {
                    val n = a.optString("name")
                    val num = number(ctx, n) ?: return "$n kontaktlar orasida topilmadi, janob."
                    val sm = if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java)
                    else SmsManager.getDefault()
                    val parts = sm.divideMessage(a.optString("text"))
                    sm.sendMultipartTextMessage(num, null, parts, null, null)
                    null
                }
                "youtube" -> {
                    val q = a.optString("query")
                    val i = Intent(Intent.ACTION_SEARCH).setPackage("com.google.android.youtube")
                        .putExtra("query", q)
                    try { start(ctx, i) } catch (_: Exception) {
                        start(ctx, Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q))))
                    }
                    null
                }
                "wifi" -> {
                    if (Build.VERSION.SDK_INT >= 29) start(ctx, Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
                    else (ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
                        .isWifiEnabled = a.optBoolean("on", true)
                    null
                }
                "bluetooth" -> {
                    val on = a.optBoolean("on", true)
                    if (Build.VERSION.SDK_INT < 33) {
                        val ad = ctx.getSystemService(BluetoothManager::class.java).adapter
                        if (on) ad.enable() else ad.disable()
                    } else start(ctx, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    null
                }
                "mobile_data" -> {
                    if (Build.VERSION.SDK_INT >= 29) start(ctx, Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
                    else start(ctx, Intent(Settings.ACTION_WIRELESS_SETTINGS))
                    null
                }
                "open_app" -> {
                    val name = a.optString("name")
                    val pm = ctx.packageManager
                    val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    val hit = pm.queryIntentActivities(q, 0)
                        .firstOrNull { it.loadLabel(pm).toString().contains(name, true) }
                    val li = hit?.let { pm.getLaunchIntentForPackage(it.activityInfo.packageName) }
                    if (li == null) "$name ilovasi topilmadi, janob." else { start(ctx, li); null }
                }
                "flashlight" -> {
                    val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                    val id = cm.cameraIdList.firstOrNull {
                        cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                    } ?: return "Fonar topilmadi, janob."
                    cm.setTorchMode(id, a.optBoolean("on", true))
                    null
                }
                "volume" -> {
                    val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    val d = if (a.optString("dir") == "down") AudioManager.ADJUST_LOWER else AudioManager.ADJUST_RAISE
                    repeat(2) { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, d, AudioManager.FLAG_SHOW_UI) }
                    null
                }
                "time" -> "Soat " + SimpleDateFormat("HH:mm", Locale.US).format(Date()) + ", janob."
                "weather" -> weather(ctx)
                "click" -> {
                    val s = JarvisAccessibilityService.instance
                        ?: return "Maxsus imkoniyatlar xizmati yoqilmagan, janob."
                    if (s.clickText(a.optString("text"))) null else "Bunday tugma topilmadi, janob."
                }
                "global" -> {
                    val s = JarvisAccessibilityService.instance
                        ?: return "Maxsus imkoniyatlar xizmati yoqilmagan, janob."
                    s.global(a.optString("kind")); null
                }
                else -> null
            }
        } catch (e: SecurityException) {
            "Bu uchun ruxsat berilmagan, janob."
        } catch (e: Exception) {
            "Bajarib bo'lmadi: ${e.message}"
        }
    }

    private fun number(ctx: Context, name: String): String? {
        if (name.filter { it.isDigit() }.length >= 7) return name
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val numCol = ContactsContract.CommonDataKinds.Phone.NUMBER
        val nameCol = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
        ctx.contentResolver.query(uri, arrayOf(numCol), "$nameCol LIKE ?", arrayOf("%$name%"), null)?.use {
            if (it.moveToFirst()) return it.getString(0)
        }
        return null
    }

    @Suppress("MissingPermission")
    private fun weather(ctx: Context): String {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val loc = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { try { lm.getLastKnownLocation(it) } catch (_: Exception) { null } }
            .maxByOrNull { it.time } ?: return "Joylashuv aniqlanmadi, janob."
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${loc.latitude}&longitude=${loc.longitude}" +
                "&current=temperature_2m,weather_code"
        val cur = JSONObject(URL(url).readText()).getJSONObject("current")
        val t = Math.round(cur.getDouble("temperature_2m"))
        val w = when (cur.getInt("weather_code")) {
            0 -> "ochiq"
            1, 2, 3 -> "bulutli"
            45, 48 -> "tumanli"
            in 51..67, in 80..82 -> "yomg'irli"
            in 71..77, 85, 86 -> "qorli"
            in 95..99 -> "momaqaldiroqli"
            else -> "o'zgaruvchan"
        }
        return "Hozir havo $w, harorat $t daraja, janob."
    }
}
