package com.example.galya

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.result.contract.ActivityResultContract
import java.util.Locale

class SpeechInputHelper : ActivityResultContract<Unit, String?>() {
    
    override fun createIntent(context: Context, input: Unit): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("ru", "RU"))
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Скажите команду...")
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): String? {
        if (resultCode == Activity.RESULT_OK && intent != null) {
            val results = intent.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!results.isNullOrEmpty()) {
                return results[0]
            }
        }
        return null
    }
}