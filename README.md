# JARVIS - Android ovozli yordamchi

## 1. Kerakli kalitlar (bepul)
1. **Picovoice AccessKey** - https://console.picovoice.ai ro'yxatdan o'ting, AccessKey ni nusxalang.
   ("Jarvis" - Porcupine'ning tayyor so'zi, alohida model kerak emas.)
2. **Gemini API kaliti** - https://aistudio.google.com/apikey

## 2. APK yig'ish (ikki yo'l)
**A) Android Studio:** papkani oching -> Sync -> Build > Build APK(s) -> `app/build/outputs/apk/debug/app-debug.apk`
**B) Kompyutersiz (GitHub):** loyihani GitHub'ga yuklang -> Actions > "Build APK" -> tugagach `jarvis-apk` artefaktini yuklab oling.

## 3. O'rnatish va ruxsatlar
1. APK ni telefonga o'rnating ("Noma'lum manbalar"ga ruxsat bering).
2. Ilovani oching, ikkala kalitni kiriting, "Saqlash va ishga tushirish" ni bosing.
3. "Ruxsatlar" bo'limidagi 4 tugmani tartib bilan bosing va hammasiga ruxsat bering.
4. Telefonda Google ilovasi / Ovozli kiritish uchun **o'zbek tili** yuklab olingan bo'lsin.
5. "JARVIS" deng, "Labbay, janob" javobidan keyin buyruq bering.

## 4. Cheklovlar
- Android 10+ da Wi-Fi va mobil internet faqat tizim paneli orqali (ilova o'zi yoqolmaydi); Android 13+ da Bluetooth sozlamalari ochiladi.
- YouTube: qidiruv natijalari ochiladi (birinchi videoni o'zi ochishi uchun YouTube Data API kerak).
- Android 14+ da qayta yoqilgandan keyin mikrofon xizmatini tizim ishga tushirishga ruxsat bermasligi mumkin - bunda ilovani bir marta oching.
- O'zbek TTS ovozi bo'lmasa, turk ovozi ishlatiladi (lotin imlosi yaqin).
- Google Play uchun SMS/qo'ng'iroq ruxsatlariga alohida asoslash kerak; shaxsiy foydalanishda APK yetarli.
