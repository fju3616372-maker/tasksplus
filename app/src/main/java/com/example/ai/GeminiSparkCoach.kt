package com.example.ai

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class FiveMinuteSparkPlan(
  val fiveMinuteSparkTitle: String,
  val fiveMinuteSparkNote: String,
  val followUpSteps: List<String>,
  val coachTip: String
)

/**
 * Uses Firebase AI Logic (`gemini-3.8-flash`) with structured JSON output (`Schema.obj`)
 * to break any intimidating or procrastinated task into an immediate, low-friction 5-minute starter spark.
 */
object GeminiSparkCoach {
  private const val TAG = "GeminiSparkCoach"

  private val sparkSchema = Schema.obj(
    properties = mapOf(
      "fiveMinuteSparkTitle" to Schema.string(
        description = "Short, concrete, non-intimidating 5-minute starter action title (under 65 characters)."
      ),
      "fiveMinuteSparkNote" to Schema.string(
        description = "Clear 1-2 sentence micro-instruction explaining exactly what to do during those first 5 minutes."
      ),
      "followUpSteps" to Schema.array(
        items = Schema.string(description = "One concrete next milestone step."),
        description = "3 short sequential follow-up steps after the 5-minute spark."
      ),
      "coachTip" to Schema.string(
        description = "Warm, encouraging 1-sentence anti-procrastination coaching tip."
      )
    )
  )

  private val config = generationConfig {
    responseMimeType = "application/json"
    responseSchema = sparkSchema
    temperature = 0.45f
  }

  private val sparkModel by lazy {
    Firebase.ai.generativeModel(
      modelName = "gemini-3.8-flash",
      generationConfig = config
    )
  }

  suspend fun generateSparkPlan(
    taskTitle: String,
    taskSubtext: String = "",
    urgencyLevel: Int = 3,
    radarContextHint: String = ""
  ): Result<FiveMinuteSparkPlan> = withContext(Dispatchers.IO) {
    val cleanTitle = taskTitle.trim()
    if (cleanTitle.isBlank()) {
      return@withContext Result.failure(
        IllegalArgumentException("Enter or select a task title first so Gemini can craft your 5-minute spark.")
      )
    }
    try {
      val promptText = buildString {
        appendLine("You are a warm, practical behavioral productivity coach inside a personal task app.")
        appendLine("The user is facing friction or procrastination on the following task and needs an effortless 5-minute starter action to build momentum.")
        appendLine("Task Title: $cleanTitle")
        if (taskSubtext.isNotBlank()) {
          appendLine("Task Notes: ${taskSubtext.trim()}")
        }
        appendLine("Urgency Level: ${urgencyLevel.coerceIn(1, 5)}/5")
        if (radarContextHint.isNotBlank()) {
          appendLine("User's Personal Rhythm Insight: $radarContextHint")
        }
        appendLine("Return a tiny, concrete, immediately doable 5-minute starter spark title, short starter note, 3 follow-up steps, and a wholesome encouraging coach tip.")
      }

      val response = withTimeout(60_000L) {
        sparkModel.generateContent(
          content {
            text(promptText)
          }
        )
      }

      val rawJson = response.text?.trim()
        ?: throw IllegalStateException("Gemini returned an empty spark plan.")
      val json = JSONObject(rawJson)
      val sparkTitle = json.optString("fiveMinuteSparkTitle").trim()
        .ifBlank { "5-Min Start: ${cleanTitle.take(48)}" }
      val sparkNote = json.optString("fiveMinuteSparkNote").trim()
        .ifBlank { "Set a 5-minute timer and complete just the very first small step." }
      val stepsArray = json.optJSONArray("followUpSteps")
      val steps = buildList {
        if (stepsArray != null) {
          for (i in 0 until stepsArray.length()) {
            val item = stepsArray.optString(i).trim()
            if (item.isNotBlank()) add(item)
          }
        }
      }.take(5)
      val coachTip = json.optString("coachTip").trim()
        .ifBlank { "Starting small breaks resistance — five focused minutes changes everything." }

      Result.success(
        FiveMinuteSparkPlan(
          fiveMinuteSparkTitle = sparkTitle,
          fiveMinuteSparkNote = sparkNote,
          followUpSteps = steps,
          coachTip = coachTip
        )
      )
    } catch (e: TimeoutCancellationException) {
      Log.e(TAG, "Gemini spark plan timed out", e)
      Result.failure(IllegalStateException("Gemini took too long to respond (60s timeout). Please try again."))
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Gemini spark plan failed", e)
      val msg = e.localizedMessage?.takeIf { it.isNotBlank() }
        ?: "Could not reach Gemini AI right now. Check your connection and try again."
      Result.failure(IllegalStateException(msg, e))
    }
  }
}
