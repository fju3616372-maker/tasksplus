package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class GoogleTtsState(
  val isReady: Boolean = false,
  val isSpeaking: Boolean = false,
  val isGoogleEngine: Boolean = false,
  val engineLabel: String = "Initializing Google Text-to-Speech…",
  val isMuted: Boolean = false,
  val speechRate: Float = 1.0f,
  val lastSpokenText: String = "",
  val errorMessage: String? = null
)

/**
 * Real Google Text-to-Speech controller using Android's TextToSpeech API.
 * Explicitly binds to Google's Speech Services engine (`com.google.android.tts`) first,
 * falling back cleanly to the device default TTS engine if Google TTS is unavailable.
 */
class GoogleTtsSpeaker(context: Context) {
  companion object {
    private const val TAG = "GoogleTtsSpeaker"
    const val GOOGLE_TTS_PACKAGE = "com.google.android.tts"
  }

  private val appContext = context.applicationContext
  private var tts: TextToSpeech? = null
  private var isReleased = false
  private var pendingUtterance: String? = null

  private val _state = MutableStateFlow(GoogleTtsState())
  val state: StateFlow<GoogleTtsState> = _state.asStateFlow()

  init {
    initializeEngine(preferGoogleEngine = true)
  }

  private fun initializeEngine(preferGoogleEngine: Boolean) {
    if (isReleased) return
    val listener = TextToSpeech.OnInitListener { status ->
      if (isReleased) return@OnInitListener
      if (status == TextToSpeech.SUCCESS) {
        val activeTts = tts ?: return@OnInitListener
        configureTtsInstance(activeTts, preferGoogleEngine)
      } else if (preferGoogleEngine) {
        // Fallback to the device's default TTS engine if com.google.android.tts isn't installed
        Log.w(TAG, "Google TTS package unavailable; falling back to default Android TTS engine")
        runCatching { tts?.shutdown() }
        tts = null
        initializeEngine(preferGoogleEngine = false)
      } else {
        _state.update {
          it.copy(
            isReady = false,
            isGoogleEngine = false,
            engineLabel = "Text-to-Speech unavailable",
            errorMessage = "No Text-to-Speech engine could be initialized on this device."
          )
        }
      }
    }

    try {
      tts = if (preferGoogleEngine) {
        TextToSpeech(appContext, listener, GOOGLE_TTS_PACKAGE)
      } else {
        TextToSpeech(appContext, listener)
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed creating TextToSpeech instance (preferGoogle=$preferGoogleEngine)", e)
      if (preferGoogleEngine) {
        initializeEngine(preferGoogleEngine = false)
      } else {
        _state.update {
          it.copy(
            isReady = false,
            engineLabel = "Text-to-Speech error",
            errorMessage = e.localizedMessage ?: "Failed to start Text-to-Speech."
          )
        }
      }
    }
  }

  private fun configureTtsInstance(engine: TextToSpeech, triedGoogle: Boolean) {
    val defaultLocale = Locale.getDefault()
    val langResult = engine.setLanguage(defaultLocale)
    if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
      engine.setLanguage(Locale.US)
    }

    engine.setAudioAttributes(
      AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    )
    engine.setSpeechRate(_state.value.speechRate)
    engine.setPitch(1.0f)

    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
      override fun onStart(utteranceId: String?) {
        _state.update { it.copy(isSpeaking = true) }
      }

      override fun onDone(utteranceId: String?) {
        _state.update { it.copy(isSpeaking = false) }
      }

      @Deprecated("Deprecated in Java")
      override fun onError(utteranceId: String?) {
        _state.update { it.copy(isSpeaking = false) }
      }

      override fun onError(utteranceId: String?, errorCode: Int) {
        _state.update { it.copy(isSpeaking = false) }
      }
    })

    val actualEnginePkg = runCatching { engine.defaultEngine }.getOrNull().orEmpty()
    val usingGoogle = triedGoogle || actualEnginePkg.contains("google", ignoreCase = true)
    val label = if (usingGoogle) "Google Text-to-Speech Ready" else "Android Text-to-Speech Ready"

    _state.update {
      it.copy(
        isReady = true,
        isGoogleEngine = usingGoogle,
        engineLabel = label,
        errorMessage = null
      )
    }

    val queued = pendingUtterance
    pendingUtterance = null
    if (!queued.isNullOrBlank()) {
      speak(queued, flush = true)
    }
  }

  fun speak(text: String, flush: Boolean = true) {
    val clean = text.trim()
    if (clean.isBlank() || isReleased) return
    _state.update { it.copy(lastSpokenText = clean) }
    if (_state.value.isMuted) return

    val activeTts = tts
    if (activeTts == null || !_state.value.isReady) {
      pendingUtterance = clean
      return
    }
    try {
      activeTts.setSpeechRate(_state.value.speechRate)
      val queueMode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
      val utteranceId = "tasksplus_tts_${UUID.randomUUID().toString().take(8)}"
      activeTts.speak(clean, queueMode, null, utteranceId)
    } catch (e: Exception) {
      Log.e(TAG, "TTS speak failed", e)
      _state.update { it.copy(isSpeaking = false, errorMessage = "Could not play speech output.") }
    }
  }

  fun stop() {
    runCatching { tts?.stop() }
    _state.update { it.copy(isSpeaking = false) }
  }

  fun setMuted(muted: Boolean) {
    if (muted) stop()
    _state.update { it.copy(isMuted = muted) }
  }

  fun setSpeechRate(rate: Float) {
    val clamped = rate.coerceIn(0.75f, 1.35f)
    runCatching { tts?.setSpeechRate(clamped) }
    _state.update { it.copy(speechRate = clamped) }
  }

  fun release() {
    isReleased = true
    pendingUtterance = null
    runCatching { tts?.stop() }
    runCatching { tts?.shutdown() }
    tts = null
    _state.update { it.copy(isReady = false, isSpeaking = false) }
  }
}
