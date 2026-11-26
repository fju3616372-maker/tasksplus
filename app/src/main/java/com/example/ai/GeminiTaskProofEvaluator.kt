package com.example.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class GeminiProofVerdict(
  val approved: Boolean,
  val confidencePercent: Int,
  val feedback: String
)

data class TransformationProofVerdict(
  val approved: Boolean,
  val confidencePercent: Int,
  val transformationScore: Int,
  val whatChanged: String,
  val feedback: String
)

/**
 * Evaluates uploaded task completion proof photos using Firebase AI Logic (`gemini-3.8-flash`)
 * with structured JSON output (`Schema.obj`) against the Group Manager's verification instructions.
 */
object GeminiTaskProofEvaluator {
  private const val TAG = "GeminiProofEval"

  private val proofEvaluationSchema = Schema.obj(
    properties = mapOf(
      "approved" to Schema.boolean(
        description = "True if the photo clearly satisfies the manager's task completion criteria, false otherwise."
      ),
      "confidencePercent" to Schema.integer(
        description = "Confidence score from 0 to 100."
      ),
      "feedback" to Schema.string(
        description = "Concise explanation of why the photo proof was approved or what is missing."
      )
    )
  )

  private val config = generationConfig {
    responseMimeType = "application/json"
    responseSchema = proofEvaluationSchema
    temperature = 0.2f
  }

  private val evaluatorModel by lazy {
    Firebase.ai.generativeModel(
      modelName = "gemini-3.8-flash",
      generationConfig = config
    )
  }

  private val transformationSchema = Schema.obj(
    properties = mapOf(
      "approved" to Schema.boolean(
        description = "True if the AFTER photo shows real, genuine progress or completion compared to the BEFORE photo."
      ),
      "confidencePercent" to Schema.integer(
        description = "Confidence score from 0 to 100 in this comparison."
      ),
      "transformationScore" to Schema.integer(
        description = "Visual progress score from 0 to 100 comparing BEFORE vs AFTER."
      ),
      "whatChanged" to Schema.string(
        description = "Concrete 1-2 sentence description of the specific visual differences spotted between BEFORE and AFTER."
      ),
      "feedback" to Schema.string(
        description = "Warm, constructive 1-2 sentence summary approving the transformation or noting what still needs work."
      )
    )
  )

  private val transformationConfig = generationConfig {
    responseMimeType = "application/json"
    responseSchema = transformationSchema
    temperature = 0.25f
  }

  private val transformationModel by lazy {
    Firebase.ai.generativeModel(
      modelName = "gemini-3.8-flash",
      generationConfig = transformationConfig
    )
  }

  suspend fun evaluateProofPhoto(
    taskTitle: String,
    taskSubtext: String,
    managerCriteriaPrompt: String,
    proofBitmap: Bitmap,
    assigneeNote: String = ""
  ): Result<GeminiProofVerdict> = withContext(Dispatchers.IO) {
    try {
      if (proofBitmap.isRecycled || proofBitmap.width <= 0 || proofBitmap.height <= 0) {
        return@withContext Result.failure(
          IllegalArgumentException("The selected photo could not be read. Please retake or pick another photo.")
        )
      }
      val safeBitmap = if (proofBitmap.config == Bitmap.Config.ARGB_8888) {
        proofBitmap
      } else {
        proofBitmap.copy(Bitmap.Config.ARGB_8888, false) ?: proofBitmap
      }
      val scaledBitmap = scaleBitmapToMaxDimension(safeBitmap, 1024)
      val promptText = buildString {
        appendLine("You are an objective task completion verifier for a family or workplace group.")
        appendLine("Task Title: $taskTitle")
        if (taskSubtext.isNotBlank()) {
          appendLine("Task Details: $taskSubtext")
        }
        val criteria = managerCriteriaPrompt.ifBlank {
          "Verify that the photo shows genuine completion of the task: $taskTitle"
        }
        appendLine("Manager's Required Conditions to Approve: $criteria")
        if (assigneeNote.isNotBlank()) {
          appendLine("Member's Note: $assigneeNote")
        }
        appendLine("Carefully inspect the attached photo and decide if it satisfies the manager's conditions.")
      }

      val response = withTimeout(60_000L) {
        evaluatorModel.generateContent(
          content {
            image(scaledBitmap)
            text(promptText)
          }
        )
      }

      val rawJson = response.text?.trim()
        ?: throw IllegalStateException("Gemini returned an empty evaluation response.")
      val json = JSONObject(rawJson)
      val approved = json.optBoolean("approved", false)
      val confidence = json.optInt("confidencePercent", 80).coerceIn(0, 100)
      val feedback = json.optString("feedback", if (approved) "Verified by Gemini AI." else "Did not meet verification criteria.")
        .ifBlank { if (approved) "Verified by Gemini AI." else "Did not meet verification criteria." }

      Result.success(
        GeminiProofVerdict(
          approved = approved,
          confidencePercent = confidence,
          feedback = feedback
        )
      )
    } catch (e: TimeoutCancellationException) {
      Log.e(TAG, "Gemini proof evaluation timed out", e)
      Result.failure(IllegalStateException("Gemini evaluation timed out after 60 seconds. Please try again."))
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Gemini proof evaluation failed", e)
      val readable = e.localizedMessage?.takeIf { it.isNotBlank() }
        ?: "Gemini AI could not process the photo proof."
      Result.failure(IllegalStateException(readable, e))
    }
  }

  suspend fun evaluateBeforeAfterTransformation(
    taskTitle: String,
    taskSubtext: String = "",
    criteriaPrompt: String = "",
    beforeBitmap: Bitmap,
    afterBitmap: Bitmap,
    userNote: String = ""
  ): Result<TransformationProofVerdict> = withContext(Dispatchers.IO) {
    try {
      if (beforeBitmap.isRecycled || beforeBitmap.width <= 0 || beforeBitmap.height <= 0 ||
        afterBitmap.isRecycled || afterBitmap.width <= 0 || afterBitmap.height <= 0
      ) {
        return@withContext Result.failure(
          IllegalArgumentException("Both Before and After photos must be readable images.")
        )
      }
      val safeBefore = if (beforeBitmap.config == Bitmap.Config.ARGB_8888) {
        beforeBitmap
      } else {
        beforeBitmap.copy(Bitmap.Config.ARGB_8888, false) ?: beforeBitmap
      }
      val safeAfter = if (afterBitmap.config == Bitmap.Config.ARGB_8888) {
        afterBitmap
      } else {
        afterBitmap.copy(Bitmap.Config.ARGB_8888, false) ?: afterBitmap
      }
      val scaledBefore = scaleBitmapToMaxDimension(safeBefore, 896)
      val scaledAfter = scaleBitmapToMaxDimension(safeAfter, 896)

      val promptText = buildString {
        appendLine("You are an objective, encouraging visual transformation verifier.")
        appendLine("Compare Image 1 (BEFORE) and Image 2 (AFTER) for the task below.")
        appendLine("Task Title: $taskTitle")
        if (taskSubtext.isNotBlank()) {
          appendLine("Task Details: $taskSubtext")
        }
        val criteria = criteriaPrompt.ifBlank {
          "Verify that the AFTER photo demonstrates genuine, real-world completion or clear visual progress compared to the BEFORE photo."
        }
        appendLine("Verification Criteria: $criteria")
        if (userNote.isNotBlank()) {
          appendLine("User's Note: $userNote")
        }
        appendLine("Spot real visual changes between BEFORE and AFTER, score the transformation from 0 to 100, and decide whether it is approved.")
      }

      val response = withTimeout(60_000L) {
        transformationModel.generateContent(
          content {
            text("Image 1 — BEFORE photo:")
            image(scaledBefore)
            text("Image 2 — AFTER photo:")
            image(scaledAfter)
            text(promptText)
          }
        )
      }

      val rawJson = response.text?.trim()
        ?: throw IllegalStateException("Gemini returned an empty transformation evaluation.")
      val json = JSONObject(rawJson)
      val approved = json.optBoolean("approved", false)
      val confidence = json.optInt("confidencePercent", 80).coerceIn(0, 100)
      val score = json.optInt("transformationScore", if (approved) 85 else 30).coerceIn(0, 100)
      val whatChanged = json.optString("whatChanged").trim()
        .ifBlank { if (approved) "Visible progress detected between before and after." else "Limited visual difference detected." }
      val feedback = json.optString("feedback").trim()
        .ifBlank { if (approved) "Awesome transformation verified by Gemini AI!" else "The after photo does not yet show full completion." }

      Result.success(
        TransformationProofVerdict(
          approved = approved,
          confidencePercent = confidence,
          transformationScore = score,
          whatChanged = whatChanged,
          feedback = feedback
        )
      )
    } catch (e: TimeoutCancellationException) {
      Log.e(TAG, "Gemini transformation evaluation timed out", e)
      Result.failure(IllegalStateException("Gemini comparison timed out after 60 seconds. Please try again."))
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Gemini transformation evaluation failed", e)
      val readable = e.localizedMessage?.takeIf { it.isNotBlank() }
        ?: "Gemini AI could not compare the before and after photos."
      Result.failure(IllegalStateException(readable, e))
    }
  }

  fun encodeBitmapToCompactBase64(bitmap: Bitmap, maxDimension: Int = 520, quality: Int = 68): String {
    val scaled = scaleBitmapToMaxDimension(bitmap, maxDimension)
    val output = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, quality, output)
    return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
  }

  fun decodeBase64ToBitmap(base64String: String): Bitmap? {
    if (base64String.isBlank()) return null
    return try {
      val bytes = Base64.decode(base64String, Base64.DEFAULT)
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (_: Exception) {
      null
    }
  }

  fun loadBitmapFromUri(context: Context, uri: Uri, maxDimension: Int = 1024): Bitmap? {
    return try {
      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, bounds)
      }
      var sampleSize = 1
      if (bounds.outWidth > maxDimension || bounds.outHeight > maxDimension) {
        val halfW = bounds.outWidth / 2
        val halfH = bounds.outHeight / 2
        while (halfW / sampleSize >= maxDimension && halfH / sampleSize >= maxDimension) {
          sampleSize *= 2
        }
      }
      val decodeOpts = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
      }
      val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, decodeOpts)
      } ?: return null
      scaleBitmapToMaxDimension(decoded, maxDimension)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to decode bitmap from URI", e)
      null
    }
  }

  private fun scaleBitmapToMaxDimension(source: Bitmap, maxDim: Int): Bitmap {
    val w = source.width
    val h = source.height
    if (w <= maxDim && h <= maxDim) return source
    val ratio = maxDim.toFloat() / maxOf(w, h).toFloat()
    val targetW = (w * ratio).toInt().coerceAtLeast(1)
    val targetH = (h * ratio).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(source, targetW, targetH, true)
  }
}
