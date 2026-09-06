package com.paizi.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import com.paizi.core.logging.LoggingManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class VoiceManager(private val context: Context) {
    private val TAG = "VoiceManager"

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _speechText = MutableStateFlow("")
    val speechText: StateFlow<String> = _speechText.asStateFlow()

    private val _voiceStatus = MutableStateFlow("Idle")
    val voiceStatus: StateFlow<String> = _voiceStatus.asStateFlow()

    init {
        initTts()
    }

    private fun initTts() {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.getDefault()
                isTtsInitialized = true
                LoggingManager.i(TAG, "TextToSpeech engine initialized")
            } else {
                LoggingManager.w(TAG, "TextToSpeech failed to initialize")
            }
        }
    }

    fun isSpeechRecognitionSupported(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun startListening(onResult: (String) -> Unit) {
        if (!isSpeechRecognitionSupported()) {
            _voiceStatus.value = "Speech recognition not available on this device"
            return
        }

        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _isListening.value = true
                        _voiceStatus.value = "Listening..."
                    }
                    override fun onBeginningOfSpeech() {
                        _voiceStatus.value = "Recording speech..."
                    }
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        _isListening.value = false
                        _voiceStatus.value = "Processing speech..."
                    }
                    override fun onError(error: Int) {
                        _isListening.value = false
                        _voiceStatus.value = "Speech recognition error ($error)"
                        LoggingManager.w(TAG, "SpeechRecognizer error: $error")
                    }
                    override fun onResults(results: Bundle?) {
                        _isListening.value = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: ""
                        _speechText.value = text
                        _voiceStatus.value = "Speech recognized"
                        onResult(text)
                    }
                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        matches?.firstOrNull()?.let { _speechText.value = it }
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            }
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            _isListening.value = false
            _voiceStatus.value = "Failed to start speech: ${e.message}"
            LoggingManager.e(TAG, "Failed starting speech recognition", e)
        }
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        _isListening.value = false
        _voiceStatus.value = "Stopped"
    }

    fun speak(text: String) {
        if (isTtsInitialized && textToSpeech != null) {
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "PAIZI_TTS_${System.currentTimeMillis()}")
            _voiceStatus.value = "Speaking..."
        } else {
            LoggingManager.w(TAG, "TTS not ready for speech output")
        }
    }

    fun stopSpeaking() {
        textToSpeech?.stop()
        _voiceStatus.value = "Idle"
    }

    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
    }
}
