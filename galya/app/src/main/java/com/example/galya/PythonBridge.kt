package com.example.galya

import android.content.Context
import android.content.Intent
import android.content.ClipboardManager
import android.media.AudioManager
import android.net.Uri
import android.widget.Toast

class PythonBridge(private val context: Context) {

    fun openApp(packageName: String) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } else {
                Toast.makeText(context, "Приложение не найдено: $packageName", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setClipboard(text: String) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = android.content.ClipData.newPlainText("Galya", text)
            clipboard.setPrimaryClip(clip)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getClipboard(): String? {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            return if (clip != null && clip.itemCount > 0) clip.getItemAt(0).text.toString() else null
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun setVolume(percent: Int) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val target = (percent * max / 100).coerceIn(0, max)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getVolume(): Int {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            return (current * 100 / max)
        } catch (e: Exception) {
            e.printStackTrace()
            return 0
        }
    }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun openSettings() {
        try {
            val intent = Intent(android.provider.Settings.ACTION_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun speakPredefined(phraseKey: String) {
        try {
            (context as? MainActivity)?.voice?.speak(phraseKey)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun sendImage(imagePath: String, description: String = "") {
        try {
            val uri = android.net.Uri.parse(imagePath)
            
            // Читаем через ContentResolver (работает с content://)
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = android.provider.MediaStore.Images.Media.getBitmap(
                context.contentResolver, 
                uri
            )
            
            // Конвертируем в Base64
            val byteArrayOutputStream = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream)
            val imageBytes = byteArrayOutputStream.toByteArray()
            val base64Image = android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP)
            
            // Передаём в Python
            val py = com.chaquo.python.Python.getInstance()
            val module = py.getModule("galya")
            module?.callAttr("process_image", base64Image, description)
            
            inputStream?.close()
            
            addAssistantMessage("📷 Фото загружено, анализирую...")
        } catch (e: Exception) {
            addAssistantMessage("❌ Ошибка загрузки фото: ${e.message}")
            e.printStackTrace()
        }
    }
    
    fun playGreeting() {
        try {
            (context as? MainActivity)?.voice?.playGreeting()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playOpen() {
        try {
            (context as? MainActivity)?.voice?.playOpen()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playSearch() {
        try {
            (context as? MainActivity)?.voice?.playSearch()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playDone() {
        try {
            (context as? MainActivity)?.voice?.playDone()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playError() {
        try {
            (context as? MainActivity)?.voice?.playError()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addAssistantMessage(text: String) {
        try {
            (context as? MainActivity)?.addAssistantMessage(text)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun onTaskCompleted() {
        try {
            (context as? MainActivity)?.onTaskCompleted()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun onError(message: String) {
        try {
            (context as? MainActivity)?.onError(message)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}