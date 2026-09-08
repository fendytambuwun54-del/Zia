package com.zia.asisten

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import java.util.Locale

class ZiaService : Service(), TextToSpeech.OnInitListener {

    companion object {
        private const val CHANNEL_ID = "zia_service_channel"
        private const val NOTIFICATION_ID = 1001
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var recognizerIntent: Intent

    private lateinit var textToSpeech: TextToSpeech

    private var wakeLock: PowerManager.WakeLock? = null

    private var isListening = false
    private var isSpeaking = false

    override fun onCreate() {
        super.onCreate()

        // Membuat notifikasi agar Service tetap berjalan
        buatSaluranNotifikasi()

        val notification = buatPemberitahuan()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Menjaga CPU tetap aktif ketika layar mati
        val powerManager =
            getSystemService(POWER_SERVICE) as PowerManager

        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Zia::ListeningWakeLock"
        )

        wakeLock?.acquire()

        // Text To Speech
        textToSpeech = TextToSpeech(this, this)

        // Menyiapkan Speech Recognizer
        siapkanPengenalanSuara()

        // Mulai mendengarkan
        mulaiMendengarkan()
    }

    private fun siapkanPengenalanSuara() {

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        speechRecognizer?.setRecognitionListener(
            object : RecognitionListener {

                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                }

                override fun onBeginningOfSpeech() {
                    // Pengguna mulai berbicara
                }

                override fun onRmsChanged(rmsdB: Float) {
                    // Perubahan volume suara
                }

                override fun onBufferReceived(buffer: ByteArray?) {
                }

                override fun onEndOfSpeech() {
                    isListening = false
                }

                override fun onError(error: Int) {

                    isListening = false

                    // Coba mendengarkan lagi
                    if (!isSpeaking) {
                        mulaiLagi()
                    }
                }

                override fun onResults(results: Bundle?) {

                    isListening = false

                    val hasil =
                        results?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )

                    if (!hasil.isNullOrEmpty()) {

                        val teks = hasil[0].lowercase(Locale.getDefault())

                        // Mengecek apakah kata "Zia" terdengar
                        if (teks.contains("zia")) {

                            jawabZia()
                        } else {

                            // Tidak ada kata Zia
                            mulaiLagi()
                        }
                    } else {

                        mulaiLagi()
                    }
                }

                override fun onPartialResults(
                    partialResults: Bundle?
                ) {
                    // Hasil sementara
                }

                override fun onEvent(
                    eventType: Int,
                    params: Bundle?
                ) {
                }
            }
        )

        recognizerIntent = Intent(
            RecognizerIntent.ACTION_RECOGNIZE_SPEECH
        ).apply {

            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )

            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                "id-ID"
            )

            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                "id-ID"
            )

            putExtra(
                RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                true
            )

            putExtra(
                RecognizerIntent.EXTRA_MAX_RESULTS,
                3
            )
        }
    }

    private fun mulaiMendengarkan() {

        if (isSpeaking) return

        if (checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        if (speechRecognizer == null) {
            siapkanPengenalanSuara()
        }

        try {

            speechRecognizer?.startListening(
                recognizerIntent
            )

            isListening = true

        } catch (e: Exception) {

            isListening = false
            mulaiLagi()
        }
    }

    private fun mulaiLagi() {

        android.os.Handler(
            mainLooper
        ).postDelayed({

            if (!isSpeaking) {
                mulaiMendengarkan()
            }

        }, 1000)
    }

    private fun jawabZia() {

        if (isSpeaking) return

        isSpeaking = true

        // Hentikan sementara pendengaran
        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {
        }

        bicara(
            "Ya, saya di sini."
        )
    }

    private fun bicara(teks: String) {

        if (!::textToSpeech.isInitialized) {
            isSpeaking = false
            mulaiLagi()
            return
        }

        textToSpeech.speak(
            teks,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "ZIA_RESPONSE"
        )

        android.os.Handler(
            mainLooper
        ).postDelayed({

            isSpeaking = false
            mulaiMendengarkan()

        }, 1800)
    }

    override fun onInit(status: Int) {

        if (status == TextToSpeech.SUCCESS) {

            textToSpeech.language =
                Locale("id", "ID")

            textToSpeech.setSpeechRate(1.0f)
            textToSpeech.setPitch(1.0f)
        }
    }

    private fun buatSaluranNotifikasi() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel = NotificationChannel(
                CHANNEL_ID,
                "Asisten Zia",
                NotificationManager.IMPORTANCE_LOW
            )

            channel.description =
                "Menjalankan layanan pendengaran Zia"

            val manager =
                getSystemService(
                    NotificationManager::class.java
                )

            manager.createNotificationChannel(
                channel
            )
        }
    }

    private fun buatPemberitahuan(): Notification {

        return NotificationCompat.Builder(
            this,
            CHANNEL_ID
        )
            .setContentTitle("Zia aktif")
            .setContentText(
                "Zia sedang mendengarkan..."
            )
            .setSmallIcon(
                android.R.drawable.ic_btn_speak_now
            )
            .setOngoing(true)
            .setPriority(
                NotificationCompat.PRIORITY_LOW
            )
            .build()
    }

    override fun onDestroy() {

        isListening = false
        isSpeaking = false

        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }

        speechRecognizer = null

        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }

        try {
            wakeLock?.release()
        } catch (_: Exception) {
        }

        wakeLock = null

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}

