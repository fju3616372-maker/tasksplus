package com.example.notifications

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class AlarmSoundOption(
  val id: String,
  val name: String,
  val subtitle: String,
  val category: String,
  val isCustom: Boolean = false,
  val customFilePath: String = ""
)

/**
 * Manages the in-app library of synthesized alarm tones, zero-permission SAF custom audio uploads
 * copied into app-private storage, audio previews, and continuous looping alarm playback when the
 * Task Countdown Timer runs out. Compatible with Android 6.0 (API 23) through Android 15.
 */
object TimerAlarmSoundManager {

  private const val PREFS_NAME = "tasks_plus_custom_alarms_v1"
  private const val KEY_CUSTOM_ALARMS_JSON = "custom_alarms_json"
  private const val MAX_CUSTOM_AUDIO_BYTES = 15L * 1024L * 1024L // 15 MB cap
  private const val SAMPLE_RATE = 22_050

  const val DEFAULT_ALARM_ID = "classic_bell"
  const val DEFAULT_ALARM_NAME = "Classic Alarm Bell"
  const val DEVICE_DEFAULT_ID = "device_default"

  val BUILT_IN_LIBRARY: List<AlarmSoundOption> = listOf(
    AlarmSoundOption(
      id = "classic_bell",
      name = "Classic Alarm Bell",
      subtitle = "Crisp twin-bell metallic ring",
      category = "Classic"
    ),
    AlarmSoundOption(
      id = "digital_pulse",
      name = "Digital Pulse",
      subtitle = "Sharp 4-beep digital watch alarm",
      category = "Urgent"
    ),
    AlarmSoundOption(
      id = "sunrise_chime",
      name = "Sunrise Chime",
      subtitle = "Warm ascending harmonic chimes",
      category = "Gentle"
    ),
    AlarmSoundOption(
      id = "cyber_siren",
      name = "Cyber Siren",
      subtitle = "Energetic rising sci-fi sweep",
      category = "Urgent"
    ),
    AlarmSoundOption(
      id = "radar_ping",
      name = "Radar Alert",
      subtitle = "High-clarity sonar double ping",
      category = "Focus"
    ),
    AlarmSoundOption(
      id = "zen_gong",
      name = "Temple Gong",
      subtitle = "Deep resonant gong with shimmer",
      category = "Gentle"
    ),
    AlarmSoundOption(
      id = "arcade_rush",
      name = "Arcade Rush",
      subtitle = "Upbeat retro chiptune fanfare",
      category = "Fun"
    ),
    AlarmSoundOption(
      id = DEVICE_DEFAULT_ID,
      name = "Device Default Alarm",
      subtitle = "Your phone's built-in alarm tone",
      category = "System"
    )
  )

  private val mainHandler = Handler(Looper.getMainLooper())
  private var activeMediaPlayer: MediaPlayer? = null
  private var activeRingtone: Ringtone? = null
  private var autoStopRunnable: Runnable? = null

  private val _isAlarmRingingFlow = MutableStateFlow(false)
  val isAlarmRingingFlow: StateFlow<Boolean> = _isAlarmRingingFlow.asStateFlow()

  private val _previewingSoundIdFlow = MutableStateFlow<String?>(null)
  val previewingSoundIdFlow: StateFlow<String?> = _previewingSoundIdFlow.asStateFlow()

  fun isAlarmRinging(): Boolean = _isAlarmRingingFlow.value

  fun getCustomAlarms(context: Context): List<AlarmSoundOption> {
    val appContext = context.applicationContext
    val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val raw = prefs.getString(KEY_CUSTOM_ALARMS_JSON, "[]").orEmpty()
    val result = mutableListOf<AlarmSoundOption>()
    try {
      val array = JSONArray(raw)
      for (i in 0 until array.length()) {
        val obj = array.optJSONObject(i) ?: continue
        val id = obj.optString("id", "").trim()
        val name = obj.optString("name", "").trim()
        val path = obj.optString("path", "").trim()
        if (id.isNotEmpty() && path.isNotEmpty() && File(path).exists()) {
          result.add(
            AlarmSoundOption(
              id = id,
              name = name.ifBlank { "Custom Alarm" },
              subtitle = "Uploaded audio file",
              category = "Custom",
              isCustom = true,
              customFilePath = path
            )
          )
        }
      }
    } catch (_: Exception) {
    }
    return result
  }

  private fun saveCustomAlarms(appContext: Context, list: List<AlarmSoundOption>) {
    val array = JSONArray()
    list.forEach { item ->
      val obj = JSONObject().apply {
        put("id", item.id)
        put("name", item.name)
        put("path", item.customFilePath)
      }
      array.put(obj)
    }
    appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putString(KEY_CUSTOM_ALARMS_JSON, array.toString())
      .commit()
  }

  fun getAllAlarmOptions(context: Context): List<AlarmSoundOption> {
    return getCustomAlarms(context) + BUILT_IN_LIBRARY
  }

  fun resolveSound(context: Context, soundId: String?): AlarmSoundOption {
    val cleanId = soundId?.trim().orEmpty()
    if (cleanId.isNotEmpty()) {
      getCustomAlarms(context).firstOrNull { it.id == cleanId }?.let { return it }
      BUILT_IN_LIBRARY.firstOrNull { it.id == cleanId }?.let { return it }
    }
    return BUILT_IN_LIBRARY.first()
  }

  /**
   * Copies a user-selected audio document URI (from SAF [OpenDocument]) into app-private storage
   * so the alarm plays reliably even after reboot without requiring storage permissions.
   */
  fun importCustomAlarmFromUri(context: Context, uri: Uri): Result<AlarmSoundOption> {
    val appContext = context.applicationContext
    return try {
      val displayName = resolveDisplayName(appContext, uri)
      val rawExt = displayName.substringAfterLast('.', "").lowercase()
      val safeExt = if (rawExt in setOf("mp3", "wav", "ogg", "m4a", "aac", "flac", "opus", "amr")) {
        rawExt
      } else {
        "audio"
      }
      val cleanBaseName = displayName
        .substringBeforeLast('.')
        .trim()
        .ifBlank { "Custom Alarm" }
        .take(48)

      val dir = File(appContext.filesDir, "custom_alarms").apply { mkdirs() }
      val id = "custom_${UUID.randomUUID().toString().replace("-", "").take(12)}"
      val outFile = File(dir, "$id.$safeExt")

      val input = appContext.contentResolver.openInputStream(uri)
        ?: return Result.failure(IllegalArgumentException("Unable to read selected audio file."))

      var totalBytes = 0L
      input.use { src ->
        FileOutputStream(outFile).use { dst ->
          val buffer = ByteArray(8192)
          while (true) {
            val read = src.read(buffer)
            if (read <= 0) break
            totalBytes += read
            if (totalBytes > MAX_CUSTOM_AUDIO_BYTES) {
              outFile.delete()
              return Result.failure(IllegalArgumentException("Audio file is too large (max 15 MB)."))
            }
            dst.write(buffer, 0, read)
          }
        }
      }

      if (totalBytes <= 0L) {
        outFile.delete()
        return Result.failure(IllegalArgumentException("Selected audio file is empty."))
      }

      val option = AlarmSoundOption(
        id = id,
        name = cleanBaseName,
        subtitle = "Uploaded audio (${formatFileSize(totalBytes)})",
        category = "Custom",
        isCustom = true,
        customFilePath = outFile.absolutePath
      )
      val updatedList = listOf(option) + getCustomAlarms(appContext)
      saveCustomAlarms(appContext, updatedList)
      Result.success(option)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  fun deleteCustomAlarm(context: Context, alarmId: String): Boolean {
    val appContext = context.applicationContext
    val existing = getCustomAlarms(appContext)
    val target = existing.firstOrNull { it.id == alarmId } ?: return false
    if (_previewingSoundIdFlow.value == alarmId) {
      stopPreview()
    }
    try {
      File(target.customFilePath).delete()
    } catch (_: Exception) {
    }
    saveCustomAlarms(appContext, existing.filterNot { it.id == alarmId })
    return true
  }

  /**
   * Starts a continuous looping alarm on the `USAGE_ALARM` stream with vibration when the
   * countdown timer expires. Auto-stops after [durationMs] (default 60 seconds) if not dismissed.
   */
  fun startTimerAlarm(
    context: Context,
    soundId: String?,
    durationMs: Long = 60_000L
  ) {
    val appContext = context.applicationContext
    val sound = resolveSound(appContext, soundId)
    _isAlarmRingingFlow.value = true
    _previewingSoundIdFlow.value = null
    mainHandler.post {
      stopPlaybackInternal(appContext, clearRingingState = false)
      _isAlarmRingingFlow.value = true
      val started = startAudioPlayback(appContext, sound, loop = true, useAlarmUsage = true)
      if (!started) {
        // Fallback to classic built-in or device default alarm ringtone so alarm never fails silently
        val fallbackBell = BUILT_IN_LIBRARY.first()
        if (sound.id == fallbackBell.id || !startAudioPlayback(appContext, fallbackBell, loop = true, useAlarmUsage = true)) {
          startSystemAlarmRingtone(appContext, loop = true)
        }
      }
      startAlarmVibration(appContext)
      val stopTask = Runnable {
        stopPlaybackInternal(appContext, clearRingingState = true)
      }
      autoStopRunnable = stopTask
      mainHandler.postDelayed(stopTask, durationMs)
    }
  }

  /**
   * Plays a short audition preview (up to 6 seconds) of either a built-in library alarm or
   * an uploaded custom alarm. Calling preview again for the same sound stops the preview.
   */
  fun togglePreviewSound(context: Context, sound: AlarmSoundOption) {
    val appContext = context.applicationContext
    if (_previewingSoundIdFlow.value == sound.id) {
      stopPreview()
      return
    }
    _previewingSoundIdFlow.value = sound.id
    _isAlarmRingingFlow.value = false
    mainHandler.post {
      stopPlaybackInternal(appContext, clearRingingState = false)
      _previewingSoundIdFlow.value = sound.id
      val started = startAudioPlayback(appContext, sound, loop = false, useAlarmUsage = false)
      if (!started && sound.id != DEVICE_DEFAULT_ID) {
        startSystemAlarmRingtone(appContext, loop = false)
      }
      val stopTask = Runnable {
        stopPlaybackInternal(appContext, clearRingingState = true)
      }
      autoStopRunnable = stopTask
      mainHandler.postDelayed(stopTask, 6_000L)
    }
  }

  fun stopPreview() {
    if (_previewingSoundIdFlow.value == null) return
    _previewingSoundIdFlow.value = null
    mainHandler.post {
      stopPlaybackInternal(context = null, clearRingingState = false)
    }
  }

  fun stopAlarm(context: Context? = null) {
    _isAlarmRingingFlow.value = false
    _previewingSoundIdFlow.value = null
    val appContext = context?.applicationContext
    mainHandler.post {
      stopPlaybackInternal(appContext, clearRingingState = true)
    }
  }

  private fun stopPlaybackInternal(context: Context?, clearRingingState: Boolean) {
    autoStopRunnable?.let { mainHandler.removeCallbacks(it) }
    autoStopRunnable = null
    if (clearRingingState) {
      _isAlarmRingingFlow.value = false
      _previewingSoundIdFlow.value = null
    }
    try {
      activeMediaPlayer?.let { mp ->
        if (mp.isPlaying) {
          mp.stop()
        }
        mp.reset()
        mp.release()
      }
    } catch (_: Exception) {
    }
    activeMediaPlayer = null

    try {
      activeRingtone?.stop()
    } catch (_: Exception) {
    }
    activeRingtone = null

    if (context != null) {
      try {
        getVibrator(context)?.cancel()
      } catch (_: Exception) {
      }
    }
  }

  private fun startAudioPlayback(
    appContext: Context,
    sound: AlarmSoundOption,
    loop: Boolean,
    useAlarmUsage: Boolean
  ): Boolean {
    if (sound.id == DEVICE_DEFAULT_ID) {
      return startSystemAlarmRingtone(appContext, loop = loop)
    }

    val audioFile = if (sound.isCustom) {
      File(sound.customFilePath).takeIf { it.exists() && it.length() > 0L } ?: return false
    } else {
      ensureBuiltInWavFile(appContext, sound.id) ?: return false
    }

    return try {
      val player = MediaPlayer().apply {
        val attrs = AudioAttributes.Builder()
          .setUsage(
            if (useAlarmUsage) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_MEDIA
          )
          .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
          .build()
        setAudioAttributes(attrs)
        setDataSource(audioFile.absolutePath)
        isLooping = loop
        setOnCompletionListener { mp ->
          if (!loop) {
            try {
              mp.release()
            } catch (_: Exception) {
            }
            if (activeMediaPlayer === mp) {
              activeMediaPlayer = null
              _previewingSoundIdFlow.value = null
            }
          }
        }
        prepare()
        start()
      }
      activeMediaPlayer = player
      true
    } catch (_: Exception) {
      false
    }
  }

  private fun startSystemAlarmRingtone(appContext: Context, loop: Boolean): Boolean {
    return try {
      val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        ?: return false

      val ringtone = RingtoneManager.getRingtone(appContext, alarmUri) ?: return false
      val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
      ringtone.audioAttributes = attrs
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ringtone.isLooping = loop
      }
      ringtone.play()
      activeRingtone = ringtone
      true
    } catch (_: Exception) {
      false
    }
  }

  /**
   * Generates and caches a crisp 16-bit mono PCM WAV file for any built-in library alarm ID.
   */
  fun ensureBuiltInWavFile(context: Context, soundId: String): File? {
    val appContext = context.applicationContext
    val dir = File(appContext.filesDir, "alarm_library_v1").apply { mkdirs() }
    val outFile = File(dir, "$soundId.wav")
    if (outFile.exists() && outFile.length() > 1024L) {
      return outFile
    }
    return try {
      val samples = synthesizeBuiltInSamples(soundId)
      val wavBytes = encodePcm16Wav(samples, SAMPLE_RATE)
      FileOutputStream(outFile).use { it.write(wavBytes) }
      outFile
    } catch (_: Exception) {
      null
    }
  }

  private fun synthesizeBuiltInSamples(soundId: String): ShortArray {
    val durationSec = 2.0
    val totalSamples = (SAMPLE_RATE * durationSec).toInt()
    val out = ShortArray(totalSamples)

    for (i in 0 until totalSamples) {
      val t = i.toDouble() / SAMPLE_RATE
      val sample: Double = when (soundId) {
        "classic_bell" -> {
          // Twin-bell rapid hammer strikes in two 0.7s bursts
          val inBurst = (t < 0.72) || (t in 0.96..1.68)
          if (!inBurst) {
            0.0
          } else {
            val localT = if (t < 0.72) t else t - 0.96
            val hammer = 0.55 + 0.45 * sin(2.0 * PI * 18.0 * localT)
            val env = exp(-localT * 1.4)
            val bell = 0.52 * sin(2.0 * PI * 1046.5 * t) +
              0.30 * sin(2.0 * PI * 2093.0 * t) +
              0.18 * sin(2.0 * PI * 2793.8 * t)
            bell * hammer * env
          }
        }

        "digital_pulse" -> {
          // Classic digital watch 4 rapid pulses followed by short pause
          val beat = t % 1.0
          val pulseIndex = (beat / 0.14).toInt()
          val pulseLocal = beat % 0.14
          if (pulseIndex < 4 && pulseLocal < 0.08) {
            val env = sin(PI * (pulseLocal / 0.08))
            val tone = 0.65 * sin(2.0 * PI * 1760.0 * t) + 0.35 * sin(2.0 * PI * 3520.0 * t)
            tone * env
          } else {
            0.0
          }
        }

        "sunrise_chime" -> {
          // Warm ascending Cmaj9 arpeggio (C5, E5, G5, B5, D6)
          val notes = doubleArrayOf(523.25, 659.25, 783.99, 987.77, 1174.66)
          var mix = 0.0
          for (n in notes.indices) {
            val onset = n * 0.24
            if (t >= onset) {
              val dt = t - onset
              val env = exp(-dt * 2.8) * (1.0 - exp(-dt * 90.0))
              mix += 0.35 * env * (sin(2.0 * PI * notes[n] * dt) + 0.25 * sin(2.0 * PI * notes[n] * 2.0 * dt))
            }
          }
          mix.coerceIn(-1.0, 1.0)
        }

        "cyber_siren" -> {
          // Dual futuristic rising sweep
          val cycle = t % 1.0
          if (cycle < 0.82) {
            val progress = cycle / 0.82
            val freq = 520.0 + 760.0 * progress
            val phase = 2.0 * PI * (520.0 * cycle + 380.0 * progress * cycle)
            val env = sin(PI * progress)
            (0.65 * sin(phase) + 0.25 * sin(phase * 1.5)) * env
          } else {
            0.0
          }
        }

        "radar_ping" -> {
          // Sonar double ping at t=0.0 and t=0.34, repeating every 1.2s
          val local = t % 1.2
          val p1 = if (local < 0.45) {
            val env = exp(-local * 7.5) * (1.0 - exp(-local * 140.0))
            sin(2.0 * PI * 1318.5 * local) * env
          } else 0.0
          val p2 = if (local in 0.30..0.95) {
            val dt = local - 0.30
            val env = exp(-dt * 5.2) * (1.0 - exp(-dt * 140.0))
            sin(2.0 * PI * 1567.98 * dt) * env
          } else 0.0
          (0.75 * p1 + 0.75 * p2).coerceIn(-1.0, 1.0)
        }

        "zen_gong" -> {
          // Deep harmonic gong strike with gentle tremolo shimmer
          val env = exp(-t * 1.5) * (1.0 - exp(-t * 60.0))
          val shimmer = 0.85 + 0.15 * sin(2.0 * PI * 4.5 * t)
          val gong = 0.48 * sin(2.0 * PI * 220.0 * t) +
            0.28 * sin(2.0 * PI * 440.0 * t) +
            0.16 * sin(2.0 * PI * 659.25 * t) +
            0.08 * sin(2.0 * PI * 1108.73 * t)
          gong * env * shimmer
        }

        "arcade_rush" -> {
          // Energetic 8-bit chiptune fanfare pattern
          val freqs = doubleArrayOf(523.25, 659.25, 783.99, 1046.5, 783.99, 1046.5, 1318.5, 1046.5)
          val step = ((t / 0.20).toInt()).coerceIn(0, freqs.lastIndex)
          val stepT = t % 0.20
          if (t < 1.60 && stepT < 0.16) {
            val f = freqs[step]
            // Band-limited square-like waveform using odd harmonics
            val raw = sin(2.0 * PI * f * t) +
              0.33 * sin(2.0 * PI * f * 3.0 * t) +
              0.20 * sin(2.0 * PI * f * 5.0 * t)
            val env = (1.0 - exp(-stepT * 120.0)) * exp(-stepT * 4.0)
            (raw * 0.55 * env).coerceIn(-1.0, 1.0)
          } else {
            0.0
          }
        }

        else -> {
          val env = exp(-t * 2.0)
          sin(2.0 * PI * 880.0 * t) * env
        }
      }

      out[i] = (sample.coerceIn(-1.0, 1.0) * 28_000.0).toInt().toShort()
    }
    return out
  }

  private fun encodePcm16Wav(samples: ShortArray, sampleRate: Int): ByteArray {
    val dataSize = samples.size * 2
    val chunkSize = 36 + dataSize
    val byteRate = sampleRate * 2
    val out = ByteArrayOutputStream(44 + dataSize)

    fun writeAscii(text: String) {
      out.write(text.toByteArray(Charsets.US_ASCII))
    }

    fun writeIntLE(v: Int) {
      out.write(v and 0xFF)
      out.write((v shr 8) and 0xFF)
      out.write((v shr 16) and 0xFF)
      out.write((v shr 24) and 0xFF)
    }

    fun writeShortLE(v: Int) {
      out.write(v and 0xFF)
      out.write((v shr 8) and 0xFF)
    }

    writeAscii("RIFF")
    writeIntLE(chunkSize)
    writeAscii("WAVE")
    writeAscii("fmt ")
    writeIntLE(16) // PCM header size
    writeShortLE(1) // PCM format
    writeShortLE(1) // Mono
    writeIntLE(sampleRate)
    writeIntLE(byteRate)
    writeShortLE(2) // Block align
    writeShortLE(16) // Bits per sample
    writeAscii("data")
    writeIntLE(dataSize)

    for (s in samples) {
      writeShortLE(s.toInt())
    }
    return out.toByteArray()
  }

  private fun resolveDisplayName(appContext: Context, uri: Uri): String {
    try {
      appContext.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null
      )?.use { cursor ->
        if (cursor.moveToFirst()) {
          val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
          if (idx >= 0) {
            val name = cursor.getString(idx)
            if (!name.isNullOrBlank()) return name
          }
        }
      }
    } catch (_: Exception) {
    }
    return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "Custom Alarm.mp3"
  }

  private fun formatFileSize(bytes: Long): String {
    return when {
      bytes < 1024L -> "$bytes B"
      bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
      else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    }
  }

  private fun startAlarmVibration(context: Context) {
    try {
      val vibrator = getVibrator(context) ?: return
      val pattern = longArrayOf(0L, 500L, 300L, 500L, 300L, 700L)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
      } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(pattern, 0)
      }
    } catch (_: Exception) {
    }
  }

  private fun getVibrator(context: Context): Vibrator? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
      vm?.defaultVibrator
    } else {
      @Suppress("DEPRECATION")
      context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
  }
}
