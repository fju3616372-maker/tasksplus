package com.example.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

enum class VoiceLockStrictness(
  val label: String,
  val fusedThresholdPercent: Int,
  val acousticFloorPercent: Int,
  val description: String
) {
  BALANCED(
    label = "Balanced",
    fusedThresholdPercent = 68,
    acousticFloorPercent = 52,
    description = "Reliable daily recognition while blocking other voices"
  ),
  STRICT(
    label = "Strict",
    fusedThresholdPercent = 78,
    acousticFloorPercent = 64,
    description = "High security — demands close vocal tract & pitch match"
  );

  companion object {
    fun fromName(name: String?): VoiceLockStrictness =
      entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: BALANCED
  }
}

data class AcousticVoiceprint(
  val mfccMean: List<Double>,
  val mfccStd: List<Double>,
  val melEnvelope: List<Double>,
  val pitchHz: Double,
  val spectralCentroidHz: Double,
  val zeroCrossingRate: Double,
  val voicedFrameCount: Int
) {
  val hasEnoughSpeech: Boolean
    get() = voicedFrameCount >= 18 && mfccMean.isNotEmpty() && melEnvelope.isNotEmpty()

  fun toJson(): JSONObject = JSONObject().apply {
    put("mfccMean", JSONArray(mfccMean))
    put("mfccStd", JSONArray(mfccStd))
    put("melEnvelope", JSONArray(melEnvelope))
    put("pitchHz", pitchHz)
    put("spectralCentroidHz", spectralCentroidHz)
    put("zeroCrossingRate", zeroCrossingRate)
    put("voicedFrameCount", voicedFrameCount)
  }

  companion object {
    fun fromJson(json: JSONObject): AcousticVoiceprint {
      fun parseDoubles(key: String): List<Double> {
        val arr = json.optJSONArray(key) ?: return emptyList()
        return (0 until arr.length()).map { arr.optDouble(it, 0.0) }
      }
      return AcousticVoiceprint(
        mfccMean = parseDoubles("mfccMean"),
        mfccStd = parseDoubles("mfccStd"),
        melEnvelope = parseDoubles("melEnvelope"),
        pitchHz = json.optDouble("pitchHz", 0.0),
        spectralCentroidHz = json.optDouble("spectralCentroidHz", 0.0),
        zeroCrossingRate = json.optDouble("zeroCrossingRate", 0.0),
        voicedFrameCount = json.optInt("voicedFrameCount", 0)
      )
    }
  }
}

data class AcousticMatchResult(
  val scorePercent: Int,
  val mfccSimilarityPercent: Int,
  val melSimilarityPercent: Int,
  val pitchMatchPercent: Int,
  val enrolledPitchHz: Double,
  val candidatePitchHz: Double,
  val hasEnoughSpeech: Boolean,
  val summary: String
)

data class EnrolledVoiceProfile(
  val ownerName: String,
  val enrolledAtMillis: Long,
  val sampleCount: Int,
  val referenceWavPath: String,
  val strictness: VoiceLockStrictness = VoiceLockStrictness.BALANCED,
  val voiceprint: AcousticVoiceprint
)

data class PcmRecordingCapture(
  val pcmSamples: ShortArray,
  val wavFile: File,
  val durationMillis: Long
)

data class PcmRecorderLiveState(
  val isRecording: Boolean = false,
  val elapsedSeconds: Int = 0,
  val maxDurationSeconds: Int = 8,
  val amplitudeLevel: Float = 0f,
  val recentBars: List<Float> = List(18) { 0.08f },
  val errorMessage: String? = null
)

/**
 * Real Android PCM AudioRecord engine + deterministic DSP acoustic speaker voiceprint extractor.
 * Extracts vocal tract resonance (16-band Log-Mel + 10 MFCC cepstral coefficients), fundamental pitch F0
 * via autocorrelation, spectral centroid, and zero-crossing rate from 16kHz mono speech recordings.
 */
object VoiceBiometricEngine {
  private const val TAG = "VoiceBiometricEngine"
  const val SAMPLE_RATE = 16_000
  private const val FRAME_SIZE = 400 // 25ms @ 16kHz
  private const val HOP_SIZE = 160   // 10ms @ 16kHz
  private const val NUM_MEL_BANDS = 16
  private const val NUM_MFCC = 10

  // 16 Mel-spaced center frequencies spanning vocal formants F1–F4 (140 Hz .. 3700 Hz)
  private val MEL_CENTER_FREQS_HZ: DoubleArray = run {
    val lowMel = hzToMel(140.0)
    val highMel = hzToMel(3700.0)
    DoubleArray(NUM_MEL_BANDS) { i ->
      val mel = lowMel + (highMel - lowMel) * i / (NUM_MEL_BANDS - 1)
      melToHz(mel)
    }
  }

  private val HANN_WINDOW: DoubleArray = DoubleArray(FRAME_SIZE) { n ->
    0.5 * (1.0 - cos(2.0 * PI * n / (FRAME_SIZE - 1)))
  }

  private fun hzToMel(hz: Double): Double = 2595.0 * ln(1.0 + hz / 700.0) / ln(10.0)
  private fun melToHz(mel: Double): Double = 700.0 * (10.0.pow(mel / 2595.0) - 1.0)

  /**
   * Pure deterministic DSP acoustic voiceprint extraction from 16kHz 16-bit PCM samples.
   */
  fun extractVoiceprint(pcmSamples: ShortArray, sampleRate: Int = SAMPLE_RATE): AcousticVoiceprint {
    if (pcmSamples.size < FRAME_SIZE * 4) {
      return AcousticVoiceprint(
        mfccMean = emptyList(),
        mfccStd = emptyList(),
        melEnvelope = emptyList(),
        pitchHz = 0.0,
        spectralCentroidHz = 0.0,
        zeroCrossingRate = 0.0,
        voicedFrameCount = 0
      )
    }

    // Normalize PCM to [-1, 1] and compute overall RMS to set adaptive Voice Activity Detection (VAD) floor
    val normalized = DoubleArray(pcmSamples.size) { pcmSamples[it] / 32768.0 }
    val frameRmsList = ArrayList<Double>()
    val frameOffsets = ArrayList<Int>()
    var offset = 0
    while (offset + FRAME_SIZE <= normalized.size) {
      var sumSq = 0.0
      for (i in 0 until FRAME_SIZE) {
        val v = normalized[offset + i]
        sumSq += v * v
      }
      frameRmsList.add(sqrt(sumSq / FRAME_SIZE))
      frameOffsets.add(offset)
      offset += HOP_SIZE
    }

    if (frameRmsList.isEmpty()) {
      return AcousticVoiceprint(emptyList(), emptyList(), emptyList(), 0.0, 0.0, 0.0, 0)
    }

    val sortedRms = frameRmsList.sorted()
    val noiseFloor = sortedRms[(sortedRms.size * 0.2).toInt().coerceIn(0, sortedRms.lastIndex)]
    val peakRms = sortedRms[(sortedRms.size * 0.92).toInt().coerceIn(0, sortedRms.lastIndex)]
    val vadThreshold = max(0.012, noiseFloor + (peakRms - noiseFloor) * 0.24)

    val frameMfccs = ArrayList<DoubleArray>()
    val frameMels = ArrayList<DoubleArray>()
    val pitchValues = ArrayList<Double>()
    val centroidValues = ArrayList<Double>()
    val zcrValues = ArrayList<Double>()

    val preEmphFrame = DoubleArray(FRAME_SIZE)
    val rawFrame = DoubleArray(FRAME_SIZE)

    for (idx in frameOffsets.indices) {
      if (frameRmsList[idx] < vadThreshold) continue
      val start = frameOffsets[idx]

      // Zero-crossing rate + raw frame for pitch autocorrelation
      var zc = 0
      for (i in 0 until FRAME_SIZE) {
        val s = normalized[start + i]
        rawFrame[i] = s
        if (i > 0 && ((s >= 0.0 && rawFrame[i - 1] < 0.0) || (s < 0.0 && rawFrame[i - 1] >= 0.0))) {
          zc++
        }
      }
      val zcr = zc.toDouble() / FRAME_SIZE
      // Skip pure high-frequency hiss/breath frames (unvoiced fricatives)
      if (zcr > 0.34) continue

      // Pre-emphasis (boosts vocal formants) + Hann window
      preEmphFrame[0] = rawFrame[0] * HANN_WINDOW[0]
      for (i in 1 until FRAME_SIZE) {
        preEmphFrame[i] = (rawFrame[i] - 0.97 * rawFrame[i - 1]) * HANN_WINDOW[i]
      }

      // 16-band Log-Mel energies via Goertzel algorithm (exact DFT magnitude at Mel center frequencies)
      val logMel = DoubleArray(NUM_MEL_BANDS)
      var weightedFreqSum = 0.0
      var magSum = 0.0
      for (b in 0 until NUM_MEL_BANDS) {
        val freq = MEL_CENTER_FREQS_HZ[b]
        val power = goertzelPower(preEmphFrame, freq, sampleRate)
        val mag = sqrt(power)
        weightedFreqSum += freq * mag
        magSum += mag
        logMel[b] = ln(power + 1e-9)
      }

      // Normalize per-frame log-mel by subtracting frame mean (channel/volume invariant!)
      val melFrameMean = logMel.average()
      val normMel = DoubleArray(NUM_MEL_BANDS) { logMel[it] - melFrameMean }
      frameMels.add(normMel)

      // DCT-II on log-Mel energies -> MFCC 1..NUM_MFCC (skip c0 energy coefficient for loudness invariance)
      val mfcc = DoubleArray(NUM_MFCC) { c ->
        val k = c + 1
        var sum = 0.0
        for (m in 0 until NUM_MEL_BANDS) {
          sum += logMel[m] * cos(PI * k * (m + 0.5) / NUM_MEL_BANDS)
        }
        sum * sqrt(2.0 / NUM_MEL_BANDS)
      }
      frameMfccs.add(mfcc)

      if (magSum > 1e-9) {
        centroidValues.add(weightedFreqSum / magSum)
      }
      zcrValues.add(zcr)

      // Pitch F0 estimation via normalized autocorrelation (75 Hz .. 350 Hz)
      val f0 = estimatePitchAutocorrelation(rawFrame, sampleRate)
      if (f0 in 75.0..350.0) {
        pitchValues.add(f0)
      }
    }

    val count = frameMfccs.size
    if (count < 8) {
      return AcousticVoiceprint(emptyList(), emptyList(), emptyList(), 0.0, 0.0, 0.0, count)
    }

    val mfccMean = DoubleArray(NUM_MFCC)
    for (v in frameMfccs) {
      for (i in 0 until NUM_MFCC) mfccMean[i] += v[i]
    }
    for (i in 0 until NUM_MFCC) mfccMean[i] /= count

    val mfccStd = DoubleArray(NUM_MFCC)
    for (v in frameMfccs) {
      for (i in 0 until NUM_MFCC) {
        val d = v[i] - mfccMean[i]
        mfccStd[i] += d * d
      }
    }
    for (i in 0 until NUM_MFCC) mfccStd[i] = sqrt(mfccStd[i] / count)

    val melMean = DoubleArray(NUM_MEL_BANDS)
    for (m in frameMels) {
      for (i in 0 until NUM_MEL_BANDS) melMean[i] += m[i]
    }
    for (i in 0 until NUM_MEL_BANDS) melMean[i] /= count

    val medianPitch = if (pitchValues.isNotEmpty()) {
      val sorted = pitchValues.sorted()
      sorted[sorted.size / 2]
    } else {
      0.0
    }

    return AcousticVoiceprint(
      mfccMean = mfccMean.toList(),
      mfccStd = mfccStd.toList(),
      melEnvelope = melMean.toList(),
      pitchHz = medianPitch,
      spectralCentroidHz = centroidValues.averageOrZero(),
      zeroCrossingRate = zcrValues.averageOrZero(),
      voicedFrameCount = count
    )
  }

  /**
   * Combines multiple enrollment recordings into one robust Master Voiceprint.
   */
  fun combineVoiceprints(samples: List<AcousticVoiceprint>): AcousticVoiceprint {
    val valid = samples.filter { it.hasEnoughSpeech }
    if (valid.isEmpty()) {
      return samples.firstOrNull()
        ?: AcousticVoiceprint(emptyList(), emptyList(), emptyList(), 0.0, 0.0, 0.0, 0)
    }
    val mfccSize = valid.first().mfccMean.size
    val melSize = valid.first().melEnvelope.size
    val avgMfcc = List(mfccSize) { i -> valid.map { it.mfccMean.getOrElse(i) { 0.0 } }.average() }
    val avgStd = List(mfccSize) { i -> valid.map { it.mfccStd.getOrElse(i) { 0.0 } }.average() }
    val avgMel = List(melSize) { i -> valid.map { it.melEnvelope.getOrElse(i) { 0.0 } }.average() }
    val pitches = valid.map { it.pitchHz }.filter { it > 0.0 }.sorted()
    val medianPitch = if (pitches.isNotEmpty()) pitches[pitches.size / 2] else 0.0
    return AcousticVoiceprint(
      mfccMean = avgMfcc,
      mfccStd = avgStd,
      melEnvelope = avgMel,
      pitchHz = medianPitch,
      spectralCentroidHz = valid.map { it.spectralCentroidHz }.average(),
      zeroCrossingRate = valid.map { it.zeroCrossingRate }.average(),
      voicedFrameCount = valid.sumOf { it.voicedFrameCount }
    )
  }

  /**
   * Deterministic on-device speaker similarity comparison (0..100%).
   * Measures vocal tract MFCC cosine similarity, Mel-envelope contour match, Pitch F0 proximity,
   * and Spectral Centroid consistency.
   */
  fun compareVoiceprints(
    enrolled: AcousticVoiceprint,
    candidate: AcousticVoiceprint
  ): AcousticMatchResult {
    if (!enrolled.hasEnoughSpeech || !candidate.hasEnoughSpeech) {
      return AcousticMatchResult(
        scorePercent = 0,
        mfccSimilarityPercent = 0,
        melSimilarityPercent = 0,
        pitchMatchPercent = 0,
        enrolledPitchHz = enrolled.pitchHz,
        candidatePitchHz = candidate.pitchHz,
        hasEnoughSpeech = false,
        summary = "Not enough clear speech detected to analyze vocal timbre."
      )
    }

    // Cosine similarity in [-1, 1] mapped sharply so orthogonal/opposite timbres score low
    val mfccCos = cosineSimilarity(enrolled.mfccMean, candidate.mfccMean)
    val mfccScore = ((mfccCos.coerceIn(0.0, 1.0)).pow(1.35) * 100.0)

    val melCos = cosineSimilarity(enrolled.melEnvelope, candidate.melEnvelope)
    val melScore = ((melCos.coerceIn(0.0, 1.0)).pow(1.45) * 100.0)

    // Pitch proximity using Gaussian kernel with sigma = 42 Hz (same speaker typically varies +/- 18 Hz;
    // different gender or noticeably different voice typically differs 45–130 Hz)
    val pitchScore = if (enrolled.pitchHz > 60.0 && candidate.pitchHz > 60.0) {
      val diffHz = kotlin.math.abs(enrolled.pitchHz - candidate.pitchHz)
      exp(-(diffHz / 44.0).pow(2.0)) * 100.0
    } else {
      60.0
    }

    // Spectral centroid proximity (brightness of vocal tract, sigma = 420 Hz)
    val centroidScore = if (enrolled.spectralCentroidHz > 100.0 && candidate.spectralCentroidHz > 100.0) {
      val diffCentroid = kotlin.math.abs(enrolled.spectralCentroidHz - candidate.spectralCentroidHz)
      exp(-(diffCentroid / 420.0).pow(2.0)) * 100.0
    } else {
      65.0
    }

    val combined = (
      mfccScore * 0.44 +
        melScore * 0.24 +
        pitchScore * 0.22 +
        centroidScore * 0.10
      ).roundToInt().coerceIn(0, 100)

    val pitchNote = if (enrolled.pitchHz > 60.0 && candidate.pitchHz > 60.0) {
      "Pitch ${candidate.pitchHz.roundToInt()}Hz vs enrolled ${enrolled.pitchHz.roundToInt()}Hz"
    } else {
      "Timbre profile compared"
    }

    return AcousticMatchResult(
      scorePercent = combined,
      mfccSimilarityPercent = mfccScore.roundToInt().coerceIn(0, 100),
      melSimilarityPercent = melScore.roundToInt().coerceIn(0, 100),
      pitchMatchPercent = pitchScore.roundToInt().coerceIn(0, 100),
      enrolledPitchHz = enrolled.pitchHz,
      candidatePitchHz = candidate.pitchHz,
      hasEnoughSpeech = true,
      summary = "Acoustic match $combined% • $pitchNote"
    )
  }

  private fun goertzelPower(frame: DoubleArray, targetFreqHz: Double, sampleRate: Int): Double {
    val omega = 2.0 * PI * targetFreqHz / sampleRate
    val coeff = 2.0 * cos(omega)
    var q0: Double
    var q1 = 0.0
    var q2 = 0.0
    for (sample in frame) {
      q0 = coeff * q1 - q2 + sample
      q2 = q1
      q1 = q0
    }
    val power = q1 * q1 + q2 * q2 - q1 * q2 * coeff
    return max(0.0, power) / frame.size
  }

  private fun estimatePitchAutocorrelation(frame: DoubleArray, sampleRate: Int): Double {
    val minLag = (sampleRate / 350.0).toInt().coerceAtLeast(20)
    val maxLag = (sampleRate / 75.0).toInt().coerceAtMost(frame.size / 2)
    var energy0 = 0.0
    for (v in frame) energy0 += v * v
    if (energy0 < 1e-6) return 0.0

    var bestLag = -1
    var bestCorr = 0.38 // require real periodicity to reject unvoiced noise
    for (lag in minLag..maxLag) {
      var dot = 0.0
      var e1 = 0.0
      var e2 = 0.0
      val limit = frame.size - lag
      for (i in 0 until limit) {
        val a = frame[i]
        val b = frame[i + lag]
        dot += a * b
        e1 += a * a
        e2 += b * b
      }
      val denom = sqrt(e1 * e2)
      if (denom > 1e-8) {
        val corr = dot / denom
        if (corr > bestCorr) {
          bestCorr = corr
          bestLag = lag
        }
      }
    }
    return if (bestLag > 0) sampleRate.toDouble() / bestLag else 0.0
  }

  private fun cosineSimilarity(a: List<Double>, b: List<Double>): Double {
    val len = minOf(a.size, b.size)
    if (len == 0) return 0.0
    var dot = 0.0
    var normA = 0.0
    var normB = 0.0
    for (i in 0 until len) {
      dot += a[i] * b[i]
      normA += a[i] * a[i]
      normB += b[i] * b[i]
    }
    val denom = sqrt(normA) * sqrt(normB)
    return if (denom > 1e-9) dot / denom else 0.0
  }

  private fun List<Double>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()

  /**
   * Writes 16kHz 16-bit mono PCM samples into a standard RIFF WAV file playable by MediaPlayer
   * and accepted natively by Gemini audio understanding (`audio/wav`).
   */
  fun writePcmToWav(samples: ShortArray, outFile: File, sampleRate: Int = SAMPLE_RATE): File {
    outFile.parentFile?.mkdirs()
    val dataSize = samples.size * 2
    val totalChunkSize = 36 + dataSize
    val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
      put("RIFF".toByteArray(Charsets.US_ASCII))
      putInt(totalChunkSize)
      put("WAVE".toByteArray(Charsets.US_ASCII))
      put("fmt ".toByteArray(Charsets.US_ASCII))
      putInt(16) // PCM subchunk size
      putShort(1) // AudioFormat = 1 (PCM)
      putShort(1) // NumChannels = 1 (Mono)
      putInt(sampleRate)
      putInt(sampleRate * 2) // ByteRate
      putShort(2) // BlockAlign
      putShort(16) // BitsPerSample
      put("data".toByteArray(Charsets.US_ASCII))
      putInt(dataSize)
    }.array()

    val pcmBytes = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN).apply {
      for (s in samples) putShort(s)
    }.array()

    FileOutputStream(outFile).use { fos ->
      fos.write(header)
      fos.write(pcmBytes)
    }
    return outFile
  }

  /**
   * Reads 16-bit PCM samples back from a RIFF WAV file (skips 44-byte header).
   */
  fun readPcmFromWav(wavFile: File): ShortArray {
    if (!wavFile.exists() || wavFile.length() <= 44L) return ShortArray(0)
    val bytes = wavFile.readBytes()
    if (bytes.size <= 44) return ShortArray(0)
    val dataLen = (bytes.size - 44) / 2
    val buf = ByteBuffer.wrap(bytes, 44, dataLen * 2).order(ByteOrder.LITTLE_ENDIAN)
    return ShortArray(dataLen) { buf.short }
  }
}

/**
 * Live 16kHz PCM AudioRecord capture controller with real-time waveform bars.
 */
class PcmVoiceRecorder(context: Context) {
  companion object {
    private const val TAG = "PcmVoiceRecorder"
  }

  private val appContext = context.applicationContext
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private val _state = MutableStateFlow(PcmRecorderLiveState())
  val state: StateFlow<PcmRecorderLiveState> = _state.asStateFlow()

  @Volatile private var recordingActive = false
  private var recordJob: Job? = null
  private var activeAudioRecord: AudioRecord? = null
  private var capturedBuffer = ByteArrayOutputStream()
  private var activeOutputFile: File? = null
  private var startedAtMillis: Long = 0L

  @SuppressLint("MissingPermission")
  fun startRecording(
    outputFile: File,
    maxSeconds: Int = 8,
    onAutoCompleted: (PcmRecordingCapture) -> Unit = {}
  ): Boolean {
    cancelRecording()
    val minBuf = AudioRecord.getMinBufferSize(
      VoiceBiometricEngine.SAMPLE_RATE,
      AudioFormat.CHANNEL_IN_MONO,
      AudioFormat.ENCODING_PCM_16BIT
    ).coerceAtLeast(3200)

    val audioRecord = createAudioRecord(minBuf * 2) ?: run {
      _state.value = PcmRecorderLiveState(
        errorMessage = "Could not open microphone. Ensure microphone permission is granted."
      )
      return false
    }

    return try {
      capturedBuffer = ByteArrayOutputStream()
      activeOutputFile = outputFile
      startedAtMillis = System.currentTimeMillis()
      activeAudioRecord = audioRecord
      audioRecord.startRecording()
      recordingActive = true

      _state.value = PcmRecorderLiveState(
        isRecording = true,
        elapsedSeconds = 0,
        maxDurationSeconds = maxSeconds,
        amplitudeLevel = 0.08f,
        recentBars = List(18) { 0.08f },
        errorMessage = null
      )

      recordJob = scope.launch {
        val readBuf = ShortArray(1600) // 100ms chunks @ 16kHz
        val byteChunk = ByteBuffer.allocate(readBuf.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        while (isActive && recordingActive) {
          val count = audioRecord.read(readBuf, 0, readBuf.size)
          if (count > 0) {
            byteChunk.clear()
            var sumSq = 0.0
            for (i in 0 until count) {
              val s = readBuf[i]
              byteChunk.putShort(s)
              val norm = s / 32768.0
              sumSq += norm * norm
            }
            synchronized(capturedBuffer) {
              capturedBuffer.write(byteChunk.array(), 0, count * 2)
            }
            val rms = sqrt(sumSq / count).toFloat()
            val visualLevel = (rms * 5.2f).coerceIn(0.06f, 1.0f)
            val elapsedSec = ((System.currentTimeMillis() - startedAtMillis) / 1000L).toInt()
            val prevBars = _state.value.recentBars
            val nextBars = prevBars.drop(1) + visualLevel
            _state.value = _state.value.copy(
              isRecording = true,
              elapsedSeconds = elapsedSec.coerceAtMost(maxSeconds),
              amplitudeLevel = visualLevel,
              recentBars = nextBars
            )
            if (elapsedSec >= maxSeconds) {
              val finished = finishInternal(discard = false)
              if (finished != null) {
                onAutoCompleted(finished)
              }
              break
            }
          }
        }
      }
      true
    } catch (e: Exception) {
      Log.e(TAG, "Failed to start PCM AudioRecord", e)
      cancelRecording()
      _state.value = PcmRecorderLiveState(
        errorMessage = "Microphone error: ${e.localizedMessage ?: "Audio device busy"}"
      )
      false
    }
  }

  @SuppressLint("MissingPermission")
  private fun createAudioRecord(bufferSize: Int): AudioRecord? {
    val sources = listOf(
      MediaRecorder.AudioSource.VOICE_RECOGNITION,
      MediaRecorder.AudioSource.MIC
    )
    for (src in sources) {
      try {
        val rec = AudioRecord(
          src,
          VoiceBiometricEngine.SAMPLE_RATE,
          AudioFormat.CHANNEL_IN_MONO,
          AudioFormat.ENCODING_PCM_16BIT,
          bufferSize
        )
        if (rec.state == AudioRecord.STATE_INITIALIZED) {
          return rec
        }
        rec.release()
      } catch (e: Exception) {
        Log.w(TAG, "AudioRecord source $src failed: ${e.localizedMessage}")
      }
    }
    return null
  }

  fun stopAndCapture(): PcmRecordingCapture? {
    return finishInternal(discard = false)
  }

  fun cancelRecording() {
    finishInternal(discard = true)
  }

  private fun finishInternal(discard: Boolean): PcmRecordingCapture? {
    recordingActive = false
    val job = recordJob
    recordJob = null
    if (job != null && job.isActive) {
      job.cancel()
    }
    val rec = activeAudioRecord
    activeAudioRecord = null
    if (rec != null) {
      runCatching { rec.stop() }
      runCatching { rec.release() }
    }

    val durationMs = (System.currentTimeMillis() - startedAtMillis).coerceAtLeast(0L)
    val outFile = activeOutputFile
    activeOutputFile = null

    val rawBytes = synchronized(capturedBuffer) { capturedBuffer.toByteArray() }
    capturedBuffer = ByteArrayOutputStream()

    _state.value = _state.value.copy(
      isRecording = false,
      amplitudeLevel = 0f
    )

    if (discard || outFile == null || rawBytes.size < 3200) {
      return null
    }

    val sampleCount = rawBytes.size / 2
    val bb = ByteBuffer.wrap(rawBytes, 0, sampleCount * 2).order(ByteOrder.LITTLE_ENDIAN)
    val shorts = ShortArray(sampleCount) { bb.short }
    VoiceBiometricEngine.writePcmToWav(shorts, outFile)
    return PcmRecordingCapture(
      pcmSamples = shorts,
      wavFile = outFile,
      durationMillis = durationMs
    )
  }
}

/**
 * Private on-device storage for the user's enrolled Voice ID profile & reference WAV recordings
 * inside `context.filesDir/voice_id/`.
 */
class VoiceprintStore(context: Context) {
  companion object {
    private const val TAG = "VoiceprintStore"
    private const val DIR_NAME = "voice_id"
    private const val PROFILE_FILE = "voiceprint_profile.json"
    private const val REF_WAV_FILE = "owner_voiceprint_reference.wav"
  }

  private val dir: File = File(context.applicationContext.filesDir, DIR_NAME).apply {
    if (!exists()) mkdirs()
  }

  fun enrollmentStepFile(stepIndex: Int): File {
    if (!dir.exists()) dir.mkdirs()
    return File(dir, "enroll_step_$stepIndex.wav")
  }

  fun commandTempFile(): File {
    if (!dir.exists()) dir.mkdirs()
    return File(dir, "command_latest.wav")
  }

  fun masterReferenceWavFile(): File {
    if (!dir.exists()) dir.mkdirs()
    return File(dir, REF_WAV_FILE)
  }

  fun loadProfile(): EnrolledVoiceProfile? {
    val file = File(dir, PROFILE_FILE)
    if (!file.exists()) return null
    return try {
      val json = JSONObject(file.readText())
      val refPath = json.optString("referenceWavPath").trim()
      if (refPath.isBlank() || !File(refPath).exists()) return null
      val vpJson = json.optJSONObject("voiceprint") ?: return null
      EnrolledVoiceProfile(
        ownerName = json.optString("ownerName", "Owner").ifBlank { "Owner" },
        enrolledAtMillis = json.optLong("enrolledAtMillis", 0L),
        sampleCount = json.optInt("sampleCount", 3),
        referenceWavPath = refPath,
        strictness = VoiceLockStrictness.fromName(json.optString("strictness")),
        voiceprint = AcousticVoiceprint.fromJson(vpJson)
      )
    } catch (e: Exception) {
      Log.e(TAG, "Failed to load voiceprint profile", e)
      null
    }
  }

  fun saveMasterProfile(
    ownerName: String,
    stepSamples: List<ShortArray>,
    stepVoiceprints: List<AcousticVoiceprint>,
    strictness: VoiceLockStrictness = VoiceLockStrictness.BALANCED
  ): EnrolledVoiceProfile {
    if (!dir.exists()) dir.mkdirs()
    val combinedVoiceprint = VoiceBiometricEngine.combineVoiceprints(stepVoiceprints)

    // Concatenate the enrolled PCM samples (with a short 150ms silence gap between phrases, capped to 10s)
    val gapSamples = ShortArray((VoiceBiometricEngine.SAMPLE_RATE * 0.15).toInt())
    val maxTotalSamples = VoiceBiometricEngine.SAMPLE_RATE * 10
    val merged = ArrayList<Short>()
    for ((idx, chunk) in stepSamples.withIndex()) {
      for (s in chunk) {
        if (merged.size >= maxTotalSamples) break
        merged.add(s)
      }
      if (idx < stepSamples.lastIndex && merged.size + gapSamples.size < maxTotalSamples) {
        for (g in gapSamples) merged.add(g)
      }
    }
    val refFile = masterReferenceWavFile()
    VoiceBiometricEngine.writePcmToWav(merged.toShortArray(), refFile)

    val profile = EnrolledVoiceProfile(
      ownerName = ownerName.ifBlank { "Owner" },
      enrolledAtMillis = System.currentTimeMillis(),
      sampleCount = stepVoiceprints.size,
      referenceWavPath = refFile.absolutePath,
      strictness = strictness,
      voiceprint = combinedVoiceprint
    )
    writeProfileJson(profile)
    return profile
  }

  fun updateStrictness(strictness: VoiceLockStrictness): EnrolledVoiceProfile? {
    val current = loadProfile() ?: return null
    val updated = current.copy(strictness = strictness)
    writeProfileJson(updated)
    return updated
  }

  private fun writeProfileJson(profile: EnrolledVoiceProfile) {
    val json = JSONObject().apply {
      put("ownerName", profile.ownerName)
      put("enrolledAtMillis", profile.enrolledAtMillis)
      put("sampleCount", profile.sampleCount)
      put("referenceWavPath", profile.referenceWavPath)
      put("strictness", profile.strictness.name)
      put("voiceprint", profile.voiceprint.toJson())
    }
    File(dir, PROFILE_FILE).writeText(json.toString())
  }

  fun loadReferenceWavBytes(): ByteArray? {
    val profile = loadProfile() ?: return null
    val ref = File(profile.referenceWavPath)
    return if (ref.exists() && ref.length() > 44L) ref.readBytes() else null
  }

  fun deleteProfile() {
    runCatching {
      dir.listFiles()?.forEach { it.delete() }
    }
  }
}
