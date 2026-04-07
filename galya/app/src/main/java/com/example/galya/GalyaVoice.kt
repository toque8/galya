package com.example.galya

import android.content.Context
import android.media.MediaPlayer
import kotlin.random.Random

class GalyaVoice(private val context: Context) {

    private var currentMediaPlayer: MediaPlayer? = null

    fun playGreeting() {
        val randomNum = Random.nextInt(1, 4)
        val resId = context.resources.getIdentifier("dear$randomNum", "raw", context.packageName)
        if (resId != 0) {
            playSoundById(resId)
        } else {
            android.util.Log.e("GalyaVoice", "Файл dear$randomNum не найден")
        }
    }

    fun playOpen() {
        playSoundById(context.resources.getIdentifier("open", "raw", context.packageName))
    }

    fun playSearch() {
        playSoundById(context.resources.getIdentifier("search", "raw", context.packageName))
    }

    fun playError() {
        playSoundById(context.resources.getIdentifier("error", "raw", context.packageName))
    }

    fun playDone() {
        playSoundById(context.resources.getIdentifier("done", "raw", context.packageName))
    }

    fun speak(phraseKey: String) {
        // Если нужно проиграть конкретный ключ (например, "open", "search" и т.д.)
        val resId = context.resources.getIdentifier(phraseKey, "raw", context.packageName)
        if (resId != 0) {
            playSoundById(resId)
        }
    }

    private fun playSoundById(resId: Int) {
        if (resId == 0) return
        try {
            currentMediaPlayer?.release()
            currentMediaPlayer = MediaPlayer.create(context, resId)
            currentMediaPlayer?.setOnCompletionListener { mp ->
                mp.release()
                currentMediaPlayer = null
            }
            currentMediaPlayer?.start()
        } catch (e: Exception) {
            android.util.Log.e("GalyaVoice", "Ошибка воспроизведения", e)
        }
    }

    fun shutdown() {
        currentMediaPlayer?.release()
        currentMediaPlayer = null
    }
}