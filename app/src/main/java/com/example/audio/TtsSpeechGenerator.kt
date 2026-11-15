package com.example.audio

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import java.util.UUID

class TtsSpeechGenerator(private val context: Context) {
    suspend fun synthesizeToWav(
        text: String,
        pitch: Float = 1.0f,
        rate: Float = 1.0f,
        languageTag: String = Locale.getDefault().toLanguageTag()
    ): File = withContext(Dispatchers.Main) {
        val cleanText = text.trim()
        require(cleanText.isNotEmpty()) { "Enter a script line to synthesize." }

        val locale = Locale.forLanguageTag(languageTag.trim())
        require(locale.language.isNotBlank() && locale.language != "und") {
            "Choose a valid speech language."
        }

        val initDeferred = CompletableDeferred<Int>()
        val tts = TextToSpeech(context.applicationContext) { status ->
            if (!initDeferred.isCompleted) initDeferred.complete(status)
        }

        try {
            val status = withTimeoutOrNull(5000L) { initDeferred.await() }
            require(status == TextToSpeech.SUCCESS) {
                "On-device speech engine is unavailable on this device."
            }

            val languageStatus = tts.isLanguageAvailable(locale)
            require(languageStatus >= TextToSpeech.LANG_AVAILABLE) {
                "The installed TTS engine does not support ${locale.displayName}. Install its voice data or choose another language."
            }
            val setLanguageStatus = tts.setLanguage(locale)
            require(setLanguageStatus >= TextToSpeech.LANG_AVAILABLE) {
                "The voice for ${locale.displayName} could not be activated."
            }

            tts.setPitch(pitch.coerceIn(0.6f, 1.5f))
            tts.setSpeechRate(rate.coerceIn(0.6f, 1.5f))

            val audioDir = File(context.filesDir, "audio").apply { mkdirs() }
            val outFile = File(audioDir, "tts_${UUID.randomUUID()}.wav")
            val utteranceId = UUID.randomUUID().toString()
            val synthDeferred = CompletableDeferred<Boolean>()

            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) {
                    if (id == utteranceId) synthDeferred.complete(true)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) {
                    if (id == utteranceId) synthDeferred.complete(false)
                }

                override fun onError(id: String?, errorCode: Int) {
                    if (id == utteranceId) synthDeferred.complete(false)
                }
            })

            val result = tts.synthesizeToFile(cleanText, Bundle(), outFile, utteranceId)
            require(result == TextToSpeech.SUCCESS) { "Speech synthesis could not start." }

            val completed = withTimeoutOrNull(30_000L) { synthDeferred.await() } == true
            require(completed && outFile.exists() && outFile.length() > 44) {
                "Speech synthesis failed to produce a complete audio file."
            }
            outFile
        } finally {
            tts.stop()
            tts.shutdown()
        }
    }
}
