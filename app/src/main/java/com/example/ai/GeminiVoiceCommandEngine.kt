package com.example.ai

import android.util.Log
import com.example.audio.AcousticMatchResult
import com.example.audio.VoiceLockStrictness
import com.example.data.model.TaskEntity
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

enum class VoiceTaskAction {
  ADD_TASK,
  COMPLETE_TASK,
  REMOVE_TASK,
  LIST_TASKS,
  VERIFY_ONLY,
  UNKNOWN;

  companion object {
    fun fromName(raw: String?): VoiceTaskAction =
      entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: UNKNOWN
  }
}

data class HybridVerificationResult(
  val isVerifiedOwner: Boolean,
  val fusedScorePercent: Int,
  val acousticScorePercent: Int,
  val aiConfidencePercent: Int,
  val aiDeemedSameSpeaker: Boolean,
  val reason: String
)

data class GeminiVoiceCommandResult(
  val verification: HybridVerificationResult,
  val transcript: String,
  val action: VoiceTaskAction,
  val targetTaskId: String,
  val targetTaskTitle: String,
  val newTaskTitle: String,
  val newTaskNotes: String,
  val urgencyLevel: Int,
  val dueInMinutes: Int,
  val spokenReply: String
)

/**
 * Uses Firebase AI Logic (`gemini-3.8-flash`) with multimodal audio (`audio/wav`) and structured JSON output
 * to simultaneously:
 * 1. Compare the live speaker's voice against the enrolled owner's reference voice recording
 *    (fused with on-device DSP acoustic voiceprint matching).
 * 2. Transcribe and parse voice task commands (Add Task, Remove Task, Mark Completed, List Tasks).
 */
object GeminiVoiceCommandEngine {
  private const val TAG = "GeminiVoiceCmd"

  private val voiceCommandSchema = Schema.obj(
    properties = mapOf(
      "isSameSpeaker" to Schema.boolean(
        description = "True ONLY if the person speaking in Audio Clip 2 (live command) is the SAME person as Audio Clip 1 (enrolled owner reference) based on vocal timbre, pitch, resonance, cadence, and voice identity. False if it sounds like a different person."
      ),
      "voiceMatchConfidence" to Schema.integer(
        description = "Speaker identity match confidence from 0 to 100 comparing Audio Clip 2 against Audio Clip 1."
      ),
      "voiceMatchReason" to Schema.string(
        description = "Brief 1-sentence acoustic observation explaining why the voices match or differ."
      ),
      "transcript" to Schema.string(
        description = "Verbatim transcription of what was spoken in Audio Clip 2."
      ),
      "action" to Schema.enumeration(
        values = listOf("ADD_TASK", "COMPLETE_TASK", "REMOVE_TASK", "LIST_TASKS", "VERIFY_ONLY", "UNKNOWN"),
        description = "The task command requested by the speaker."
      ),
      "targetTaskId" to Schema.string(
        description = "Exact task id from the provided task list when action is COMPLETE_TASK or REMOVE_TASK, or empty string if none matched."
      ),
      "targetTaskTitle" to Schema.string(
        description = "Spoken title of the task to complete or remove, or empty string."
      ),
      "newTaskTitle" to Schema.string(
        description = "Concise, clean title for a newly created task when action is ADD_TASK (capitalize nicely, strip command words like 'add task')."
      ),
      "newTaskNotes" to Schema.string(
        description = "Optional extra details/notes mentioned for the new task, or empty string."
      ),
      "urgencyLevel" to Schema.integer(
        description = "Task urgency from 1 (low) to 5 (critical/urgent). Default 3 if not specified."
      ),
      "dueInMinutes" to Schema.integer(
        description = "Minutes from now if the user mentioned a relative deadline (e.g. 60 for 'in an hour', 1440 for 'tomorrow'), or 0 if not mentioned."
      ),
      "spokenReply" to Schema.string(
        description = "Natural, concise 1-sentence confirmation for Google Text-to-Speech to speak aloud."
      )
    )
  )

  private val config = generationConfig {
    responseMimeType = "application/json"
    responseSchema = voiceCommandSchema
    temperature = 0.15f
  }

  private val voiceModel by lazy {
    Firebase.ai.generativeModel(
      modelName = "gemini-3.8-flash",
      generationConfig = config
    )
  }

  /**
   * Pure deterministic fusion gate combining real on-device DSP acoustic voiceprint similarity
   * with Gemini multimodal audio speaker verification.
   */
  fun evaluateHybridVerification(
    acousticScorePercent: Int,
    aiIsSameSpeaker: Boolean,
    aiConfidencePercent: Int,
    aiReason: String,
    strictness: VoiceLockStrictness
  ): HybridVerificationResult {
    val safeAcoustic = acousticScorePercent.coerceIn(0, 100)
    val safeAi = aiConfidencePercent.coerceIn(0, 100)
    // If Gemini explicitly detects a different speaker, cap AI's effective contribution strictly
    val effectiveAi = if (aiIsSameSpeaker) safeAi else minOf(safeAi, 32)
    val fused = (safeAcoustic * 0.45 + effectiveAi * 0.55).roundToInt().coerceIn(0, 100)

    val passesAcousticFloor = safeAcoustic >= strictness.acousticFloorPercent
    val passesFused = fused >= strictness.fusedThresholdPercent
    val verified = aiIsSameSpeaker && passesAcousticFloor && passesFused

    val reason = when {
      verified ->
        aiReason.ifBlank { "Vocal timbre and pitch match the enrolled owner." }
      !aiIsSameSpeaker ->
        aiReason.ifBlank { "Voice sounds different from the enrolled owner's reference recording." }
      !passesAcousticFloor ->
        "On-device vocal acoustics ($safeAcoustic%) fell below the ${strictness.label} threshold (${strictness.acousticFloorPercent}%)."
      else ->
        "Combined voice verification score ($fused%) did not meet the ${strictness.label} requirement (${strictness.fusedThresholdPercent}%)."
    }

    return HybridVerificationResult(
      isVerifiedOwner = verified,
      fusedScorePercent = fused,
      acousticScorePercent = safeAcoustic,
      aiConfidencePercent = safeAi,
      aiDeemedSameSpeaker = aiIsSameSpeaker,
      reason = reason
    )
  }

  /**
   * Pure fuzzy task matcher for resolving spoken task titles when an exact ID isn't supplied.
   */
  fun findBestMatchingTask(
    spokenTitle: String,
    targetId: String,
    candidates: List<TaskEntity>
  ): TaskEntity? {
    if (candidates.isEmpty()) return null
    if (targetId.isNotBlank()) {
      candidates.firstOrNull { it.id == targetId }?.let { return it }
    }
    val needle = normalizeText(spokenTitle)
    if (needle.isBlank()) return null

    // 1. Exact normalized match
    candidates.firstOrNull { normalizeText(it.title) == needle }?.let { return it }
    // 2. Substring containment match
    candidates.firstOrNull {
      val hay = normalizeText(it.title)
      hay.contains(needle) || needle.contains(hay)
    }?.let { return it }

    // 3. Token Jaccard/overlap score
    val needleTokens = needle.split(" ").filter { it.length >= 2 }.toSet()
    if (needleTokens.isEmpty()) return null
    var bestTask: TaskEntity? = null
    var bestScore = 0.0
    for (task in candidates) {
      val taskTokens = normalizeText(task.title).split(" ").filter { it.length >= 2 }.toSet()
      if (taskTokens.isEmpty()) continue
      val intersection = needleTokens.intersect(taskTokens).size.toDouble()
      val union = needleTokens.union(taskTokens).size.toDouble()
      val overlap = intersection / union
      val containment = intersection / minOf(needleTokens.size, taskTokens.size).toDouble()
      val score = overlap * 0.5 + containment * 0.5
      if (score > bestScore && score >= 0.45) {
        bestScore = score
        bestTask = task
      }
    }
    return bestTask
  }

  private fun normalizeText(raw: String): String {
    return raw.lowercase()
      .replace(Regex("[^a-z0-9\\s]"), " ")
      .replace(Regex("\\s+"), " ")
      .trim()
  }

  suspend fun verifyAndParseCommand(
    enrolledReferenceWav: ByteArray,
    commandAudioWav: ByteArray,
    acousticMatch: AcousticMatchResult,
    strictness: VoiceLockStrictness,
    activeTasks: List<TaskEntity>,
    completedTasks: List<TaskEntity>,
    verifyOnlyMode: Boolean = false
  ): Result<GeminiVoiceCommandResult> = withContext(Dispatchers.IO) {
    if (enrolledReferenceWav.size <= 44 || commandAudioWav.size <= 44) {
      return@withContext Result.failure(
        IllegalArgumentException("Audio recording was too short. Please try speaking again.")
      )
    }
    try {
      val activeSummary = if (activeTasks.isEmpty()) {
        "(none)"
      } else {
        activeTasks.take(35).joinToString("\n") { task ->
          "- id=${task.id} | title=\"${task.title}\" | urgency=${task.resolvedUrgencyLevel}"
        }
      }
      val completedSummary = if (completedTasks.isEmpty()) {
        "(none)"
      } else {
        completedTasks.take(15).joinToString("\n") { task ->
          "- id=${task.id} | title=\"${task.title}\""
        }
      }

      val promptText = buildString {
        appendLine("You are the Voice Biometric Security & Task Command Engine for the Tasks+ Android app.")
        appendLine("You are given TWO real audio recordings:")
        appendLine("1) FIRST AUDIO CLIP: The ENROLLED OWNER's reference voice recording.")
        appendLine("2) SECOND AUDIO CLIP: A live voice command recorded right now.")
        appendLine()
        appendLine("STEP 1 — STRICT SPEAKER IDENTITY VERIFICATION:")
        appendLine("Carefully compare the speaker's actual voice in Clip 2 against Clip 1 (pitch, vocal tract timbre, resonance, cadence, gender/age vocal qualities, accent).")
        appendLine("On-device DSP telemetry (for additional context): ${acousticMatch.summary} (MFCC=${acousticMatch.mfccSimilarityPercent}%, PitchMatch=${acousticMatch.pitchMatchPercent}%).")
        appendLine("Set isSameSpeaker=false whenever Clip 2 sounds like a different human being than Clip 1, or if someone is whispering/masking their voice to bypass verification.")
        appendLine("Be honest and strict: protecting the user's tasks from other speakers is your top priority.")
        appendLine()
        appendLine("STEP 2 — TRANSCRIBE & PARSE VOICE TASK COMMAND FROM CLIP 2:")
        if (verifyOnlyMode) {
          appendLine("The user is currently in Speaker Lock Test mode. Set action=VERIFY_ONLY and transcribe what was said.")
        } else {
          appendLine("Determine whether the speaker wants to:")
          appendLine("- ADD_TASK: create/add/remind/schedule a new task (extract clean newTaskTitle, optional newTaskNotes, urgencyLevel 1..5, dueInMinutes if specified).")
          appendLine("- COMPLETE_TASK: finish/complete/done/check off an existing active task (match to the closest active task id & title below).")
          appendLine("- REMOVE_TASK: delete/remove/cancel/erase an existing active or completed task (match to the closest task id & title below).")
          appendLine("- LIST_TASKS: ask what tasks they have left or read their list.")
          appendLine("- UNKNOWN: anything else or unintelligible audio.")
        }
        appendLine()
        appendLine("CURRENT ACTIVE TASKS:")
        appendLine(activeSummary)
        appendLine()
        appendLine("RECENT COMPLETED TASKS:")
        appendLine(completedSummary)
      }

      val response = withTimeout(60_000L) {
        voiceModel.generateContent(
          content {
            text("AUDIO CLIP 1 — ENROLLED OWNER REFERENCE VOICE:")
            inlineData(enrolledReferenceWav, "audio/wav")
            text("AUDIO CLIP 2 — LIVE INCOMING VOICE COMMAND:")
            inlineData(commandAudioWav, "audio/wav")
            text(promptText)
          }
        )
      }

      val rawJson = response.text?.trim()
        ?: throw IllegalStateException("Gemini returned an empty voice verification response.")
      val json = JSONObject(rawJson)

      val aiSame = json.optBoolean("isSameSpeaker", false)
      val aiConfidence = json.optInt("voiceMatchConfidence", 0).coerceIn(0, 100)
      val aiReason = json.optString("voiceMatchReason").trim()
      val transcript = json.optString("transcript").trim()
      val action = if (verifyOnlyMode) {
        VoiceTaskAction.VERIFY_ONLY
      } else {
        VoiceTaskAction.fromName(json.optString("action"))
      }

      val verification = evaluateHybridVerification(
        acousticScorePercent = acousticMatch.scorePercent,
        aiIsSameSpeaker = aiSame,
        aiConfidencePercent = aiConfidence,
        aiReason = aiReason,
        strictness = strictness
      )

      Result.success(
        GeminiVoiceCommandResult(
          verification = verification,
          transcript = transcript,
          action = action,
          targetTaskId = json.optString("targetTaskId").trim(),
          targetTaskTitle = json.optString("targetTaskTitle").trim(),
          newTaskTitle = json.optString("newTaskTitle").trim(),
          newTaskNotes = json.optString("newTaskNotes").trim(),
          urgencyLevel = json.optInt("urgencyLevel", 3).coerceIn(1, 5),
          dueInMinutes = json.optInt("dueInMinutes", 0).coerceAtLeast(0),
          spokenReply = json.optString("spokenReply").trim()
        )
      )
    } catch (e: TimeoutCancellationException) {
      Log.e(TAG, "Voice verification timed out", e)
      Result.failure(IllegalStateException("Voice verification timed out (60s). Please try again."))
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Gemini voice verification failed", e)
      val msg = e.localizedMessage?.takeIf { it.isNotBlank() }
        ?: "Could not verify voice command right now. Check your internet connection."
      Result.failure(IllegalStateException(msg, e))
    }
  }
}
