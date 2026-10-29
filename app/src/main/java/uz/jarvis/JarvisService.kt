package uz.jarvis

import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.os.Bundle
import androidx.core.app.ServiceCompat
import java.util.Locale

class JarvisService : Service() {

    companion object { const val ACTION_LISTEN = "uz.jarvis.LISTEN" }

    private var porcupine: PorcupineManager? = null
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val callbacks = HashMap<String, () -> Unit>()
    private var recognizer: SpeechRecognizer? = null
    @Volatile private var busy = false

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("jarvis", "JARVIS", NotificationManager.IMPORTANCE_LOW))
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, "jarvis")
            .setContentTitle("JARVIS tinglamoqda")
            .setContentText("\"JARVIS\" deb chaqiring")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pi).setOngoing(true).build()
        ServiceCompat.startForeground(this, 1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)

        tts = TextToSpeech(this) { st ->
            if (st == TextToSpeech.SUCCESS) {
                val r = tts!!.setLanguage(Locale("uz", "UZ"))
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED)
                    tts!!.setLanguage(Locale("tr", "TR")) // o'zbek ovozi yo'q bo'lsa, lotin imlosiga yaqin tur tili
                tts!!.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) { main.post { callbacks.remove(id)?.invoke() } }
                    @Deprecated("") override fun onError(id: String?) { main.post { callbacks.remove(id)?.invoke() } }
                })
                ttsReady = true
            }
        }
    }

    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        if (i?.action == ACTION_LISTEN) onWake() else startWake()
        return START_STICKY
    }

    private fun startWake() {
        try {
            if (porcupine == null) {
                val sp = Prefs.sp(this)
                val key = sp.getString("picovoice", "").orEmpty()
                if (key.isBlank()) { History.add("! Picovoice AccessKey kiritilmagan (Sozlamalar)."); return }
                porcupine = PorcupineManager.Builder()
                    .setAccessKey(key)
                    .setKeyword(Porcupine.BuiltInKeyword.JARVIS)
                    .setSensitivity(sp.getInt("sens", 50) / 100f)
                    .build(this, PorcupineManagerCallback { onWake() })
            }
            porcupine?.start()
        } catch (e: Exception) {
            History.add("! Uyg'otish so'zi xatosi: ${e.message}")
        }
    }

    private fun onWake() {
        if (busy) return
        busy = true
        try { porcupine?.stop() } catch (_: Exception) {}
        main.post { speak("Labbay, janob.") { listen() } }
    }

    private fun finish() {
        busy = false
        main.post { startWake() }
    }

    private fun speak(text: String, done: () -> Unit) {
        History.add("JARVIS: $text")
        if (!ttsReady) { main.post(done); return }
        val id = System.nanoTime().toString()
        callbacks[id] = done
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    private fun listen() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            speak("Nutqni tanish xizmati topilmadi, janob.") { finish() }; return
        }
        recognizer?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(b: Bundle?) {
                val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (t.isNullOrBlank()) finish() else process(t)
            }
            override fun onError(e: Int) { finish() }
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(b: Bundle?) {}
            override fun onEvent(t: Int, b: Bundle?) {}
        })
        r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "uz-UZ")
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "uz-UZ"))
    }

    private fun process(text: String) {
        History.add("Siz: $text")
        val key = Prefs.sp(this).getString("gemini", "").orEmpty()
        Thread {
            val say = try {
                if (key.isBlank()) throw RuntimeException("Gemini kaliti kiritilmagan")
                val j = Gemini.ask(key, text)
                val extra = Actions.run(this, j.optJSONObject("action"))
                if (!extra.isNullOrBlank()) extra else j.optString("reply")
            } catch (e: Exception) {
                "Xatolik: ${e.message}"
            }
            main.post { if (say.isBlank()) finish() else speak(say) { finish() } }
        }.start()
    }

    override fun onDestroy() {
        try { porcupine?.stop(); porcupine?.delete() } catch (_: Exception) {}
        recognizer?.destroy()
        tts?.shutdown()
        super.onDestroy()
    }
}
