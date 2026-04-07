package com.example.galya

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var editText: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var attachButton: ImageButton
    private lateinit var micButton: ImageButton
    private lateinit var adapter: ChatAdapter
    private val messages = mutableListOf<Message>()
    lateinit var voice: GalyaVoice
    private var pythonReady = false
    private var bridge: PythonBridge? = null

    private fun loadHistoryFromGalya() {
        try {
            val py = Python.getInstance()
            val module = py.getModule("galya")
            val history = module.callAttr("get_conversation_history").asList()
            messages.clear()
            adapter.notifyDataSetChanged()
            for (item in history) {
                val map = item as Map<*, *>
                val role = map["role"] as String
                val content = map["content"] as String
                if (role == "user") {
                    messages.add(Message(content, true))
                } else if (role == "assistant") {
                    messages.add(Message(content, false))
                }
            }
            adapter.notifyItemRangeInserted(0, messages.size)
            if (messages.isNotEmpty()) {
                recyclerView.scrollToPosition(messages.size - 1)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private val speechLauncher = registerForActivityResult(SpeechInputHelper()) { result ->
        try {
            if (result != null) {
                editText.setText(result)
                sendMessage()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка голосового ввода: ${e.message}", Toast.LENGTH_SHORT).show()
            voice.playError()
        }
    }

    private val fileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                val fileName = getFileName(it) ?: "file"
                val mimeType = contentResolver.getType(it)

                if (mimeType?.startsWith("image/") == true) {
                    Toast.makeText(this, "📷 Обрабатываю фото: $fileName", Toast.LENGTH_SHORT).show()
                    bridge?.sendImage(it.toString(), "")
                } else {
                    // Определяем, текстовый ли файл (по MIME или расширению)
                    val isText = mimeType?.startsWith("text/") == true ||
                            fileName.endsWith(".txt") || fileName.endsWith(".py") ||
                            fileName.endsWith(".json") || fileName.endsWith(".xml") ||
                            fileName.endsWith(".html") || fileName.endsWith(".css") ||
                            fileName.endsWith(".js") || fileName.endsWith(".md") ||
                            fileName.endsWith(".csv") || fileName.endsWith(".log")
                    if (isText) {
                        Toast.makeText(this, "📎 Читаю текстовый файл: $fileName", Toast.LENGTH_SHORT).show()
                        val inputStream = contentResolver.openInputStream(it)
                        if (inputStream != null) {
                            val content = inputStream.bufferedReader().use { reader -> reader.readText() }
                            bridge?.sendTextFile(fileName, content)
                            inputStream.close()
                        } else {
                            Toast.makeText(this, "Не удалось открыть файл", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        // Для бинарных файлов (PDF, DOCX, DOC, аудио, видео) отправляем байты
                        Toast.makeText(this, "📎 Обрабатываю бинарный файл: $fileName", Toast.LENGTH_SHORT).show()
                        val inputStream = contentResolver.openInputStream(it)
                        if (inputStream != null) {
                            val bytes = inputStream.readBytes()
                            bridge?.sendBinaryFile(fileName, bytes)
                            inputStream.close()
                        } else {
                            Toast.makeText(this, "Не удалось открыть файл", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                voice.playError()
                e.printStackTrace()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_main)

            requestPermissions()
            voice = GalyaVoice(this)
            initPython()
            initViews()
            voice.playGreeting()
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка запуска: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    private fun requestPermissions() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val permissions = mutableListOf(
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.MODIFY_AUDIO_SETTINGS,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
                // Для Android 13+ добавляем медиа-разрешения
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
                    permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
                    permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
                }
                val missing = permissions.filter {
                    ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
                }
                if (missing.isNotEmpty()) {
                    ActivityCompat.requestPermissions(this, missing.toTypedArray(), 1)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun initPython() {
        try {
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(this))
            }
            bridge = PythonBridge(this)
            val py = Python.getInstance()
            val module = py.getModule("galya")
            module?.callAttr("set_bridge", bridge)
            if (pythonReady) {
                loadHistoryFromGalya()
            }
            pythonReady = true
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка Python: ${e.message}", Toast.LENGTH_LONG).show()
            pythonReady = false
            e.printStackTrace()
        }
    }

    private fun initViews() {
        try {
            recyclerView = findViewById(R.id.recyclerView)
            editText = findViewById(R.id.editText)
            sendButton = findViewById(R.id.buttonSend)
            attachButton = findViewById(R.id.buttonAttach)
            micButton = findViewById(R.id.buttonMic)

            adapter = ChatAdapter(messages)
            recyclerView.layoutManager = LinearLayoutManager(this)
            recyclerView.adapter = adapter

            // Долгое нажатие на поле ввода для вставки из буфера
            editText.setOnLongClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                if (clipboard.hasPrimaryClip()) {
                    val item = clipboard.primaryClip?.getItemAt(0)
                    val text = item?.text
                    if (text != null && text.isNotEmpty()) {
                        editText.setText(text)
                        editText.setSelection(text.length)
                        Toast.makeText(this, "Вставлено из буфера", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Буфер пуст", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Буфер пуст", Toast.LENGTH_SHORT).show()
                }
                true
            }

            sendButton.setOnClickListener { 
                try { sendMessage() } 
                catch (e: Exception) { 
                    Toast.makeText(this, "Ошибка отправки: ${e.message}", Toast.LENGTH_SHORT).show()
                    voice.playError()
                }
            }
            
            attachButton.setOnClickListener { 
                try { fileLauncher.launch("*/*") } 
                catch (e: Exception) { 
                    Toast.makeText(this, "Ошибка файла: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            
            micButton.setOnClickListener { 
                try {
                    val speechIntent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    speechIntent.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, 
                        android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    speechIntent.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
                    speechIntent.putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Скажите команду...")
                    
                    if (speechIntent.resolveActivity(packageManager) != null) {
                        speechLauncher.launch(Unit)
                    } else {
                        Toast.makeText(this, "Голосовой ввод не поддерживается", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                    voice.playError()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка инициализации: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    fun addUserMessage(text: String) {
        try {
            runOnUiThread {
                messages.add(Message(text, true))
                adapter.notifyItemInserted(messages.size - 1)
                recyclerView.scrollToPosition(messages.size - 1)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addAssistantMessage(text: String) {
        try {
            runOnUiThread {
                messages.add(Message(text, false))
                adapter.notifyItemInserted(messages.size - 1)
                recyclerView.scrollToPosition(messages.size - 1)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addAssistantImage(url: String) {
        runOnUiThread {
            messages.add(Message("", false, imageUrl = url))
            adapter.notifyItemInserted(messages.size - 1)
            recyclerView.scrollToPosition(messages.size - 1)
        }
    }

    private fun sendMessage() {
        try {
            val text = editText.text.toString().trim()
            if (text.isEmpty()) return

            addUserMessage(text)
            editText.text.clear()

            if (pythonReady) {
                thread {
                    try {
                        val py = Python.getInstance()
                        val module = py.getModule("galya")
                        module?.callAttr("process_message", text, this)
                    } catch (e: Exception) {
                        runOnUiThread {
                            Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                            voice.playError()
                        }
                        e.printStackTrace()
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка сообщения: ${e.message}", Toast.LENGTH_SHORT).show()
            voice.playError()
            e.printStackTrace()
        }
    }

    fun onTaskCompleted() {
        try {
            voice.playDone()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun onError(message: String) {
        try {
            voice.playError()
            runOnUiThread {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        try {
            voice.shutdown()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        super.onDestroy()
    }

    // Вспомогательный метод для получения имени файла из URI
    private fun getFileName(uri: android.net.Uri): String? {
        var fileName: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    fileName = it.getString(nameIndex)
                }
            }
        }
        if (fileName == null) {
            fileName = uri.path?.let { path ->
                val lastSlash = path.lastIndexOf('/')
                if (lastSlash != -1) path.substring(lastSlash + 1) else path
            }
        }
        return fileName
    }
}