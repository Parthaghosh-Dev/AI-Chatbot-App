package com.example.testaichatbot

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private val groqApiKey = "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"

    private val chatList = mutableListOf<ChatModel>()
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var rcvResponseView: RecyclerView
    private var tts: TextToSpeech? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tts = TextToSpeech(this, this)

        rcvResponseView = findViewById(R.id.rcvResponseView)
        val etMessage = findViewById<EditText>(R.id.etMessage)
        val btnSend = findViewById<ImageView>(R.id.btnSend)
        val btnMic = findViewById<ImageView>(R.id.btnMic)

        chatAdapter = ChatAdapter(chatList) { textToSpeak ->
            tts?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, null)
        }

        rcvResponseView.layoutManager = LinearLayoutManager(this)
        rcvResponseView.adapter = chatAdapter

        chatList.add(ChatModel("Hello! How can I help you today?", true))
        chatAdapter.notifyItemInserted(0)

        val voiceLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                if (!matches.isNullOrEmpty()) {
                    etMessage.setText(matches[0])
                }
            }
        }

        btnMic.setOnClickListener {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            }
            try {
                voiceLauncher.launch(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Voice input not supported", Toast.LENGTH_SHORT).show()
            }
        }

        btnSend.setOnClickListener {
            val userText = etMessage.text.toString().trim()
            if (userText.isNotEmpty()) {
                chatList.add(ChatModel(userText, false))
                chatAdapter.notifyItemInserted(chatList.size - 1)
                etMessage.text.clear()

                val botIndex = chatList.size
                chatList.add(ChatModel("Thinking...", true))
                chatAdapter.notifyItemInserted(botIndex)
                rcvResponseView.scrollToPosition(botIndex)

                callGroqApi(userText, botIndex)
            }
        }
    }

    private fun callGroqApi(prompt: String, botIndex: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val jsonBody = JSONObject().apply {
                    put("model", "openai/gpt-oss-120b")
                    val messagesArray = JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        })
                    }
                    put("messages", messagesArray)
                    put("temperature", 0.7)
                }

                val mediaType = "application/json; charset=utf-8".toMediaType()
                val requestBody = jsonBody.toString().toRequestBody(mediaType)

                val request = Request.Builder()
                    .url("https://api.groq.com/openai/v1/chat/completions")
                    .header("Authorization", "Bearer ${groqApiKey.trim()}")
                    .header("Content-Type", "application/json")
                    .post(requestBody)
                    .build()

                val response = httpClient.newCall(request).execute()
                val responseData = response.body?.string()

                if (response.isSuccessful && responseData != null) {
                    val rootJson = JSONObject(responseData)
                    val choices = rootJson.getJSONArray("choices")
                    val botReply = choices.getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")

                    withContext(Dispatchers.Main) {
                        chatList[botIndex].message = botReply.trim()
                        chatAdapter.notifyItemChanged(botIndex)
                        rcvResponseView.scrollToPosition(botIndex)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        chatList[botIndex].message = "Error ${response.code}: ${responseData ?: response.message}"
                        chatAdapter.notifyItemChanged(botIndex)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    chatList[botIndex].message = "Exception: ${e.localizedMessage}"
                    chatAdapter.notifyItemChanged(botIndex)
                }
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}