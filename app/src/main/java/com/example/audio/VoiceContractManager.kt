package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class VoiceRecorderUiState(
  val isRecording: Boolean = false,
  val isPlaying: Boolean = false,
  val elapsedSeconds: Int = 0,
  val maxDurationSeconds: Int = VoiceContractManager.MAX_CONTRACT_SECONDS,
  val amplitudeLevel: Float = 0f,
  val activePlayingPath: String? = null,
  val errorMessage: String? = null
)

/**
 * Real Android MediaRecorder + MediaPlayer manager for Future-Self Voice Contracts.
 * Lets users record a short (up to 15s) personal voice note saved privately inside
 * `filesDir/voice_contracts/` and automatically plays their own voice when the task reminder fires.
 */
object VoiceContractManager {
  private const val TAG = "VoiceContractManager"
  const val MAX_CONTRACT_SECONDS = 15

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var recorder: MediaRecorder? = null
  private var player: MediaPlayer? = null
  private var currentRecordingFile: File? = null
  private var meterJob: Job? = null

  private val _uiState = MutableStateFlow(VoiceRecorderUiState())
  val uiState: StateFlow<VoiceRecorderUiState> = _uiState.asStateFlow()

  private fun contractsDir(context: Context): File {
    return File(context.applicationContext.filesDir, "voice_contracts").apply {
      if (!exists()) mkdirs()
    }
  }

  fun startRecording(
    context: Context,
    onAutoStopped: (savedPath: String) -> Unit = {}
  ): Boolean {
    stopPlayback()
    stopRecordingInternal(discard = true)

    val outFile = File(
      contractsDir(context),
      "contract_${UUID.randomUUID().toString().replace("-", "_").take(12)}.m4a"
    )
    return try {
      val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        MediaRecorder(context.applicationContext)
      } else {
        @Suppress("DEPRECATION")
        MediaRecorder()
      }
      mediaRecorder.apply {
        setAudioSource(MediaRecorder.AudioSource.MIC)
        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        setAudioSamplingRate(44_100)
        setAudioEncodingBitRate(96_000)
        setMaxDuration(MAX_CONTRACT_SECONDS * 1_000)
        setOutputFile(outFile.absolutePath)
        prepare()
        start()
      }
      recorder = mediaRecorder
      currentRecordingFile = outFile
      _uiState.value = VoiceRecorderUiState(
        isRecording = true,
        elapsedSeconds = 0,
        amplitudeLevel = 0.08f
      )

      val startedAt = System.currentTimeMillis()
      meterJob?.cancel()
      meterJob = scope.launch {
        while (isActive && _uiState.value.isRecording) {
          delay(120L)
          val elapsed = ((System.currentTimeMillis() - startedAt) / 1_000L).toInt()
          val amp = try {
            val raw = recorder?.maxAmplitude ?: 0
            (raw / 24_000f).coerceIn(0.05f, 1f)
          } catch (_: Exception) {
            0.05f
          }
          _uiState.value = _uiState.value.copy(
            elapsedSeconds = elapsed.coerceAtMost(MAX_CONTRACT_SECONDS),
            amplitudeLevel = amp
          )
          if (elapsed >= MAX_CONTRACT_SECONDS) {
            val saved = stopRecording()
            if (saved != null) {
              onAutoStopped(saved)
            }
            break
          }
        }
      }
      true
    } catch (e: Exception) {
      Log.e(TAG, "Failed to start voice contract recording", e)
      runCatching { outFile.delete() }
      recorder = null
      currentRecordingFile = null
      _uiState.value = VoiceRecorderUiState(
        errorMessage = "Could not start microphone recording: ${e.localizedMessage ?: "Microphone busy"}"
      )
      false
    }
  }

  fun stopRecording(): String? {
    val saved = stopRecordingInternal(discard = false)
    _uiState.value = _uiState.value.copy(
      isRecording = false,
      amplitudeLevel = 0f
    )
    return saved
  }

  private fun stopRecordingInternal(discard: Boolean): String? {
    meterJob?.cancel()
    meterJob = null
    val activeRecorder = recorder
    val activeFile = currentRecordingFile
    recorder = null
    currentRecordingFile = null

    if (activeRecorder == null) return null
    return try {
      activeRecorder.stop()
      activeRecorder.reset()
      activeRecorder.release()
      if (discard) {
        runCatching { activeFile?.delete() }
        null
      } else {
        activeFile?.takeIf { it.exists() && it.length() > 0L }?.absolutePath
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error stopping MediaRecorder", e)
      runCatching { activeRecorder.reset() }
      runCatching { activeRecorder.release() }
      runCatching { activeFile?.delete() }
      null
    }
  }

  fun playContractFile(
    context: Context,
    filePath: String,
    isReminderAlarm: Boolean = false
  ): Boolean {
    if (filePath.isBlank()) return false
    val file = File(filePath)
    if (!file.exists() || file.length() <= 0L) return false

    stopPlayback()
    return try {
      val mp = MediaPlayer().apply {
        setAudioAttributes(
          AudioAttributes.Builder()
            .setUsage(
              if (isReminderAlarm) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_MEDIA
            )
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        )
        setDataSource(file.absolutePath)
        setOnCompletionListener {
          stopPlayback()
        }
        setOnErrorListener { _, _, _ ->
          stopPlayback()
          true
        }
        prepare()
        start()
      }
      player = mp
      _uiState.value = _uiState.value.copy(
        isPlaying = true,
        activePlayingPath = filePath,
        errorMessage = null
      )
      true
    } catch (e: Exception) {
      Log.e(TAG, "Failed to play voice contract $filePath", e)
      stopPlayback()
      _uiState.value = _uiState.value.copy(
        errorMessage = "Could not play voice recording."
      )
      false
    }
  }

  fun stopPlayback() {
    val active = player
    player = null
    if (active != null) {
      runCatching {
        if (active.isPlaying) active.stop()
      }
      runCatching { active.reset() }
      runCatching { active.release() }
    }
    _uiState.value = _uiState.value.copy(
      isPlaying = false,
      activePlayingPath = null
    )
  }

  fun deleteContractFile(filePath: String) {
    if (filePath.isBlank()) return
    if (_uiState.value.activePlayingPath == filePath) {
      stopPlayback()
    }
    runCatching { File(filePath).delete() }
  }

  fun hasValidRecording(filePath: String): Boolean {
    if (filePath.isBlank()) return false
    val f = File(filePath)
    return f.exists() && f.length() > 0L
  }
}
