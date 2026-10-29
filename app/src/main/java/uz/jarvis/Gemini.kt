package uz.jarvis

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Gemini {
    // Model nomi o'zgarsa, shu yerda yangilang.
    const val MODEL = "gemini-2.5-flash"

    private val SYSTEM = """
Sen JARVIS - telefonni boshqaradigan ovozli yordamchisan. Foydalanuvchini doim "janob" deb chaqir.
Faqat o'zbek tilida, lotin yozuvida, qisqa va aniq (1-2 gap) javob ber.
Javobing FAQAT shu JSON bo'lsin: {"reply": "...", "action": {...}}  (action kerak bo'lmasa null).
action turlari:
{"type":"call","name":"Onam"}            - qo'ng'iroq (kontakt nomi BOSH KELISHIKDA: "Onamga"->"Onam")
{"type":"sms","name":"Akam","text":"kech qolaman"}
{"type":"youtube","query":"Shahzoda musiqasi"}
{"type":"wifi","on":true}
{"type":"bluetooth","on":true}
{"type":"mobile_data"}
{"type":"open_app","name":"Telegram"}
{"type":"flashlight","on":true}
{"type":"volume","dir":"up"}             - "up" yoki "down"
{"type":"time"}                          - reply bo'sh ("") bo'lsin
{"type":"weather"}                       - reply bo'sh ("") bo'lsin
{"type":"click","text":"Yuborish"}       - ekrandagi tugmani bosish
{"type":"global","kind":"back"}          - "back", "home" yoki "recents"
Oddiy savol bo'lsa action = null va reply ga o'zing javob yoz.
""".trimIndent()

    fun ask(key: String, userText: String): JSONObject {
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SYSTEM))))
            .put("contents", JSONArray().put(JSONObject().put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", userText)))))
            .put("generationConfig", JSONObject().put("responseMimeType", "application/json"))

        val con = URL("https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent")
            .openConnection() as HttpURLConnection
        con.requestMethod = "POST"
        con.connectTimeout = 15000
        con.readTimeout = 30000
        con.doOutput = true
        con.setRequestProperty("Content-Type", "application/json")
        con.setRequestProperty("x-goog-api-key", key)
        con.outputStream.use { it.write(body.toString().toByteArray()) }

        val code = con.responseCode
        val resp = (if (code in 200..299) con.inputStream else con.errorStream).bufferedReader().readText()
        if (code !in 200..299) throw RuntimeException("AI xatosi $code")

        val txt = JSONObject(resp).getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
        return JSONObject(txt.trim().removePrefix("```json").removeSuffix("```").trim())
    }
}
