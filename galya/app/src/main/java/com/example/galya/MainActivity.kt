package com.example.galya

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import android.content.Intent
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
    private lateinit var sendButton: Button
    private lateinit var attachButton: Button
    private lateinit var micButton: ImageButton
    private lateinit var adapter: ChatAdapter
    private val messages = mutableListOf<Message>()
    lateinit var voice: GalyaVoice
    private var pythonReady = false
    private var bridge: PythonBridge? = null

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
                val fileName = it.path?.substringAfterLast('/') ?: "file"
                val mimeType = contentResolver.getType(it)
                
                if (mimeType?.startsWith("image/") == true) {
                    Toast.makeText(this, "📷 Фото: $fileName", Toast.LENGTH_SHORT).show()
                    bridge?.sendImage(it.toString(), "")
                } else {
                    // Документы и другие файлы
                    Toast.makeText(this, "📎 Файл: $fileName", Toast.LENGTH_SHORT).show()
                    // Отправляем как текст с путём
                    editText.setText("📎 Файл: $fileName")
                    sendMessage()                }
            } catch (e: Exception) {
                Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                voice.playError()
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
                    Manifest.permission.MODIFY_AUDIO_SETTINGS
                )
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
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
            bridge = PythonBridge(this)  // ← Сохраняем в свойство
            val py = Python.getInstance()
            val module = py.getModule("galya")
            module?.callAttr("set_bridge", bridge)
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

    private fun sendMessage() {
        try {
            val text = editText.text.toString().trim()
            if (text.isEmpty()) return

            when {
                text.contains("привет", ignoreCase = true) || text.contains("дорогая", ignoreCase = true) -> {
                    voice.playGreeting()
                }
                text.contains("найди", ignoreCase = true) || text.contains("поищи", ignoreCase = true) || text.contains("ищу", ignoreCase = true) -> {
                    voice.playSearch()
                }
                text.contains("открой", ignoreCase = true) || text.contains("открыть", ignoreCase = true) -> {
                    voice.playOpen()
                }
            }

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
}