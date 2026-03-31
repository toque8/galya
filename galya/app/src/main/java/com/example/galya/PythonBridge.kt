package com.example.galya

import android.content.Context
import android.content.Intent
import android.content.ClipboardManager
import android.media.AudioManager
import android.net.Uri
import android.widget.Toast
import android.view.KeyEvent
import android.os.Environment
import java.io.File
import androidx.core.content.FileProvider

class PythonBridge(private val context: Context) {

    fun openApp(packageName: String) {
        try {
            var intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent == null) {
                val found = findAppPackage(packageName)
                if (found != null) {
                    intent = context.packageManager.getLaunchIntentForPackage(found)
                }
            }
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

    fun sendImage(uriString: String, description: String = "") {
        try {
            val uri = Uri.parse(uriString)
            val bitmap = android.provider.MediaStore.Images.Media.getBitmap(
                context.contentResolver,
                uri
            )
            val byteArrayOutputStream = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream)
            val imageBytes = byteArrayOutputStream.toByteArray()
            val base64Image = android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP)
            val py = com.chaquo.python.Python.getInstance()
            val module = py.getModule("galya")
            module?.callAttr("process_image", base64Image, description)
            addAssistantMessage("📷 Фото загружено, анализирую...")
        } catch (e: Exception) {
            addAssistantMessage("❌ Ошибка загрузки фото: ${e.message}")
            e.printStackTrace()
        }
    }

    fun saveImageAndShow(url: String) {
        try {
            val imageUrl = Uri.parse(url)
            val inputStream = context.contentResolver.openInputStream(imageUrl)
            if (inputStream != null) {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val galyaDir = File(picturesDir, "Galya")
                if (!galyaDir.exists()) galyaDir.mkdirs()
                val fileName = "galya_${System.currentTimeMillis()}.jpg"
                val file = File(galyaDir, fileName)
                val outputStream = java.io.FileOutputStream(file)
                inputStream.copyTo(outputStream)
                outputStream.close()
                inputStream.close()

                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Galya")
                }
                context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

                val fileUri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                (context as? MainActivity)?.addAssistantImage(fileUri.toString())
            } else {
                (context as? MainActivity)?.addAssistantMessage("🎨 Сгенерировано изображение: $url")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            (context as? MainActivity)?.addAssistantMessage("❌ Ошибка сохранения изображения: ${e.message}")
        }
    }

    fun sendTextFile(fileName: String, content: String) {
        try {
            val py = com.chaquo.python.Python.getInstance()
            val module = py.getModule("galya")
            module?.callAttr("process_uploaded_text", fileName, content)
            addAssistantMessage("📄 Файл '$fileName' загружен, анализирую...")
        } catch (e: Exception) {
            addAssistantMessage("❌ Ошибка загрузки файла: ${e.message}")
            e.printStackTrace()
        }
    }

    fun nextTrack() {
        sendMediaKeyCompat(KeyEvent.KEYCODE_MEDIA_NEXT)
    }

    fun prevTrack() {
        sendMediaKeyCompat(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    }

    fun playPause() {
        sendMediaKeyCompat(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    }

    private fun sendMediaKeyCompat(keyCode: Int) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
                val upEvent = KeyEvent(KeyEvent.ACTION_UP, keyCode)
                audioManager.dispatchMediaKeyEvent(downEvent)
                audioManager.dispatchMediaKeyEvent(upEvent)
            } else {
                val downIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                    putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
                }
                val upIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                    putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_UP, keyCode))
                }
                context.sendBroadcast(downIntent)
                context.sendBroadcast(upIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getDownloadsPath(): String {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
    }

    fun getDocumentsPath(): String {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).absolutePath
    }

    fun getPicturesPath(): String {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).absolutePath
    }

    fun openFile(path: String) {
        val file = File(path)
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val mime = getMimeType(path)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Открыть файл"))
    }

    private fun getMimeType(path: String): String {
        val extension = path.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "txt" -> "text/plain"
            "html", "htm" -> "text/html"
            "pdf" -> "application/pdf"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "mp3" -> "audio/mpeg"
            "mp4" -> "video/mp4"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            else -> "*/*"
        }
    }

    fun searchFiles(filename: String): List<String> {
        val result = mutableListOf<String>()
        val externalStorage = Environment.getExternalStorageDirectory().absolutePath
        val searchDir = File(externalStorage)
        if (!searchDir.exists()) return result

        searchDir.walkTopDown().forEach { file ->
            if (file.name.contains(filename, ignoreCase = true) && file.isFile) {
                result.add(file.absolutePath)
            }
        }
        return result
    }

    fun findAppPackage(appName: String): String? {
        try {
            val pm = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null)
            mainIntent.addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = pm.queryIntentActivities(mainIntent, 0)
            for (resolveInfo in apps) {
                val label = resolveInfo.loadLabel(pm).toString()
                if (label.contains(appName, ignoreCase = true)) {
                    return resolveInfo.activityInfo.packageName
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
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

    fun showImageFromUrl(url: String) {
        try {
            (context as? MainActivity)?.addAssistantImage(url)
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