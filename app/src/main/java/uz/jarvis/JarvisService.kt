package uz.jarvis

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
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.zip.ZipInputStream
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener as VoskListener
import org.vosk.android.SpeechService

class JarvisService : Service() {

    companion object { const val ACTION_LISTEN = "uz.jarvis.LISTEN" }

    private var speech: SpeechService? = null
    private var model: Model? = null
    @Volatile private var loading = false
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

    private val modelName = "vosk-model-small-en-us-0.15"

    private fun startWake() {
        if (loading) return
        loading = true
        Thread {
            try {
                if (model == null) {
                    val root = filesDir
                    val dir = File(root, modelName)
                    val done = File(root, "$modelName.done")
                    if (!done.exists()) {
                        History.add("Uyg'otish modeli yuklanmoqda (~40 MB), kuting...")
                        dir.deleteRecursively()
                        download(root)
                        done.createNewFile()
                    }
                    model = Model(dir.absolutePath)
                }
                main.post { beginWake() }
            } catch (e: Exception) {
                History.add("! Uyg'otish so'zi xatosi: ${e.message}")
            } finally {
                loading = false
            }
        }.start()
    }

    private fun download(target: File) {
        val con = URL("https://alphacephei.com/vosk/models/$modelName.zip").openConnection() as HttpURLConnection
        con.connectTimeout = 20000
        con.readTimeout = 60000
        ZipInputStream(con.inputStream.buffered()).use { z ->
            var e = z.nextEntry
            while (e != null) {
                val f = File(target, e.name)
                if (!f.canonicalPath.startsWith(target.canonicalPath)) throw SecurityException("zip")
                if (e.isDirectory) f.mkdirs() else {
                    f.parentFile?.mkdirs()
                    f.outputStream().use { z.copyTo(it) }
                }
                e = z.nextEntry
            }
        }
    }

    private fun beginWake() {
        try {
            speech?.shutdown()
            val rec = Recognizer(model, 16000f, "[\"jarvis\", \"[unk]\"]")
            val sp = SpeechService(rec, 16000f)
            speech = sp
            sp.startListening(object : VoskListener {
                override fun onPartialResult(h: String?) { if (h?.contains("jarvis") == true) onWake() }
                override fun onResult(h: String?) { if (h?.contains("jarvis") == true) onWake() }
                override fun onFinalResult(h: String?) {}
                override fun onError(e: Exception?) { History.add("! Mikrofon xatosi: ${e?.message}") }
                override fun onTimeout() {}
            })
        } catch (e: Exception) {
            History.add("! Uyg'otish so'zi xatosi: ${e.message}")
        }
    }

    private fun onWake() {
        if (busy) return
        busy = true
        try { speech?.shutdown() } catch (_: Exception) {}
        speech = null
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
        try { speech?.shutdown(); model?.close() } catch (_: Exception) {}
        recognizer?.destroy()
        tts?.shutdown()
        super.onDestroy()
    }
}
