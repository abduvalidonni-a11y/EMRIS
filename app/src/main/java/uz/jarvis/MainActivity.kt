package uz.jarvis

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import androidx.core.content.ContextCompat

class MainActivity : Activity() {
    private lateinit var log: TextView
    private val cyan = Color.parseColor("#00E5FF")

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val sp = Prefs.sp(this)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 80, 40, 60) }
        val root = ScrollView(this).apply { setBackgroundColor(Color.parseColor("#050A14")); addView(col) }

        fun label(t: String, size: Float = 14f) = TextView(this).apply {
            text = t; textSize = size; setTextColor(cyan); setPadding(0, 24, 0, 8)
        }
        fun field(hint: String, v: String) = EditText(this).apply {
            setHint(hint); setText(v); setTextColor(Color.WHITE); setHintTextColor(Color.GRAY); isSingleLine = true
        }
        fun button(t: String, f: () -> Unit) = Button(this).apply { text = t; setOnClickListener { f() } }

        col.addView(TextView(this).apply {
            text = "J.A.R.V.I.S"; textSize = 28f; setTextColor(cyan); gravity = Gravity.CENTER
        })

        // 1) Asosiy ekran: JARVIS animatsiyasi + mikrofon tugmasi
        val mic = TextView(this).apply {
            text = "\uD83C\uDF99"; textSize = 56f; gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setStroke(6, cyan); setColor(Color.parseColor("#0A1A2A")) }
            setOnClickListener {
                ContextCompat.startForegroundService(this@MainActivity,
                    Intent(this@MainActivity, JarvisService::class.java).setAction(JarvisService.ACTION_LISTEN))
            }
        }
        col.addView(mic, LinearLayout.LayoutParams(300, 300).apply { gravity = Gravity.CENTER; topMargin = 40; bottomMargin = 40 })
        ObjectAnimator.ofPropertyValuesHolder(mic,
            PropertyValuesHolder.ofFloat("scaleX", 1f, 1.1f), PropertyValuesHolder.ofFloat("scaleY", 1f, 1.1f)).apply {
            duration = 1200; repeatMode = ObjectAnimator.REVERSE; repeatCount = ObjectAnimator.INFINITE; start()
        }
        log = TextView(this).apply { setTextColor(Color.WHITE); textSize = 14f; text = History.text() }
        col.addView(label("Suhbat tarixi"))
        col.addView(log, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        // 2) Sozlamalar
        col.addView(label("Sozlamalar", 18f))
        col.addView(label("Gemini API kaliti (aistudio.google.com)"))
        val gm = field("API key", sp.getString("gemini", "").orEmpty()); col.addView(gm)
        val auto = CheckBox(this).apply {
            text = "Telefon yoqilganda avtomatik ishga tushsin"; setTextColor(Color.WHITE)
            isChecked = sp.getBoolean("autostart", true)
        }
        col.addView(auto)
        col.addView(button("Saqlash va ishga tushirish") {
            sp.edit().putString("gemini", gm.text.toString().trim())
                .putBoolean("autostart", auto.isChecked).apply()
            stopService(Intent(this, JarvisService::class.java))
            ContextCompat.startForegroundService(this, Intent(this, JarvisService::class.java))
            Toast.makeText(this, "JARVIS ishga tushdi", Toast.LENGTH_SHORT).show()
        })

        // 3) Ruxsatlar (bosqichma-bosqich)
        col.addView(label("Ruxsatlar (yuqoridan pastga bajaring)", 18f))
        col.addView(button("1. Asosiy ruxsatlar") {
            val p = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CONTACTS,
                Manifest.permission.CALL_PHONE, Manifest.permission.SEND_SMS, Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) p += Manifest.permission.POST_NOTIFICATIONS
            if (Build.VERSION.SDK_INT >= 31) p += Manifest.permission.BLUETOOTH_CONNECT
            requestPermissions(p.toTypedArray(), 1)
        })
        col.addView(button("2. Batareya optimizatsiyasidan chiqarish") {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        })
        col.addView(button("3. Boshqa ilovalar ustida ko'rsatish") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        col.addView(button("4. Maxsus imkoniyatlar (Accessibility)") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        History.listener = { t -> runOnUiThread { log.text = t } }
    }

    override fun onPause() {
        History.listener = null
        super.onPause()
    }
}
