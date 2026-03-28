package com.example.galya

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.OnInitListener
import java.util.Locale
import kotlin.random.Random

class GalyaVoice(private val context: Context) : OnInitListener {

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var currentMediaPlayer: MediaPlayer? = null

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("ru", "RU"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.US)
            }
            isTtsReady = true
        } else {
            isTtsReady = false
        }
    }

    fun playGreeting() {
        val randomNum = Random.nextInt(1, 5) // 1, 2, 3 или 4
        val phraseKey = "dear$randomNum"
        playSound(phraseKey)
    }

    fun playOpen() {
        playSound("open")
    }

    fun playSearch() {
        playSound("search")
    }

    fun playError() {
        playSound("error")
    }

    fun playDone() {
        playSound("done")
    }

    fun speak(phraseKey: String) {
        playSound(phraseKey)
    }

    private fun playSound(phraseKey: String) {
        val resId = context.resources.getIdentifier(phraseKey, "raw", context.packageName)
        if (resId != 0) {
            try {
                currentMediaPlayer?.release()
                currentMediaPlayer = MediaPlayer.create(context, resId).also {
                    it.setOnCompletionListener { mediaPlayer ->
                        mediaPlayer.release()
                        currentMediaPlayer = null
                    }
                    it.start()
                }
            } catch (e: Exception) {
                speakFallback(phraseKey)
            }
        } else {
            speakFallback(phraseKey)
        }
    }

    private fun speakFallback(text: String) {
        if (isTtsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, null)
        }
    }

    fun shutdown() {
        currentMediaPlayer?.release()
        tts?.stop()
        tts?.shutdown()
    }
}