package com.example.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class TransformationProofRecord(
  val id: String,
  val taskId: String,
  val taskTitle: String,
  val beforeImagePath: String,
  val afterImagePath: String,
  val approved: Boolean,
  val confidencePercent: Int,
  val transformationScore: Int,
  val whatChanged: String,
  val feedback: String,
  val createdAtMillis: Long
)

/**
 * Private on-device store for Before & After Transformation Proof pairs verified by Gemini AI.
 * Stores scaled JPEG images and metadata JSON in app-private storage (`filesDir/transformation_proofs`).
 */
class TransformationProofStore(private val context: Context) {

  private val dir: File
    get() = File(context.applicationContext.filesDir, "transformation_proofs").apply {
      if (!exists()) mkdirs()
    }

  private val indexFile: File
    get() = File(dir, "proof_index.json")

  suspend fun loadAll(): List<TransformationProofRecord> = withContext(Dispatchers.IO) {
    if (!indexFile.exists()) return@withContext emptyList()
    try {
      val raw = indexFile.readText()
      if (raw.isBlank()) return@withContext emptyList()
      val arr = JSONArray(raw)
      buildList {
        for (i in 0 until arr.length()) {
          val o = arr.optJSONObject(i) ?: continue
          val beforePath = o.optString("beforeImagePath")
          val afterPath = o.optString("afterImagePath")
          add(
            TransformationProofRecord(
              id = o.optString("id"),
              taskId = o.optString("taskId"),
              taskTitle = o.optString("taskTitle"),
              beforeImagePath = beforePath,
              afterImagePath = afterPath,
              approved = o.optBoolean("approved", false),
              confidencePercent = o.optInt("confidencePercent", 0),
              transformationScore = o.optInt("transformationScore", 0),
              whatChanged = o.optString("whatChanged"),
              feedback = o.optString("feedback"),
              createdAtMillis = o.optLong("createdAtMillis", 0L)
            )
          )
        }
      }.sortedByDescending { it.createdAtMillis }
    } catch (_: Exception) {
      emptyList()
    }
  }

  suspend fun saveProof(
    taskId: String,
    taskTitle: String,
    beforeBitmap: Bitmap,
    afterBitmap: Bitmap,
    approved: Boolean,
    confidencePercent: Int,
    transformationScore: Int,
    whatChanged: String,
    feedback: String
  ): TransformationProofRecord = withContext(Dispatchers.IO) {
    val id = "tp_${UUID.randomUUID().toString().replace("-", "_").take(14)}"
    val beforeFile = File(dir, "${id}_before.jpg")
    val afterFile = File(dir, "${id}_after.jpg")
    writeScaledJpeg(beforeBitmap, beforeFile)
    writeScaledJpeg(afterBitmap, afterFile)

    val record = TransformationProofRecord(
      id = id,
      taskId = taskId,
      taskTitle = taskTitle.trim().take(120),
      beforeImagePath = beforeFile.absolutePath,
      afterImagePath = afterFile.absolutePath,
      approved = approved,
      confidencePercent = confidencePercent.coerceIn(0, 100),
      transformationScore = transformationScore.coerceIn(0, 100),
      whatChanged = whatChanged.trim().take(320),
      feedback = feedback.trim().take(400),
      createdAtMillis = System.currentTimeMillis()
    )
    val current = loadAll().toMutableList()
    current.add(0, record)
    val trimmed = current.take(40)
    // Clean up evicted images if list exceeded 40
    if (current.size > 40) {
      current.drop(40).forEach { evicted ->
        runCatching { File(evicted.beforeImagePath).delete() }
        runCatching { File(evicted.afterImagePath).delete() }
      }
    }
    writeIndex(trimmed)
    record
  }

  suspend fun deleteProof(proofId: String): List<TransformationProofRecord> = withContext(Dispatchers.IO) {
    val current = loadAll()
    val target = current.firstOrNull { it.id == proofId }
    if (target != null) {
      runCatching { File(target.beforeImagePath).delete() }
      runCatching { File(target.afterImagePath).delete() }
    }
    val remaining = current.filterNot { it.id == proofId }
    writeIndex(remaining)
    remaining
  }

  fun decodeThumb(path: String): Bitmap? {
    if (path.isBlank()) return null
    val file = File(path)
    if (!file.exists()) return null
    return try {
      BitmapFactory.decodeFile(file.absolutePath)
    } catch (_: Exception) {
      null
    }
  }

  private fun writeScaledJpeg(source: Bitmap, outFile: File) {
    val maxDim = 640
    val scaled = if (source.width <= maxDim && source.height <= maxDim) {
      source
    } else {
      val ratio = maxDim.toFloat() / maxOf(source.width, source.height).toFloat()
      Bitmap.createScaledBitmap(
        source,
        (source.width * ratio).toInt().coerceAtLeast(1),
        (source.height * ratio).toInt().coerceAtLeast(1),
        true
      )
    }
    FileOutputStream(outFile).use { fos ->
      scaled.compress(Bitmap.CompressFormat.JPEG, 78, fos)
    }
  }

  private fun writeIndex(records: List<TransformationProofRecord>) {
    val arr = JSONArray()
    records.forEach { r ->
      arr.put(
        JSONObject().apply {
          put("id", r.id)
          put("taskId", r.taskId)
          put("taskTitle", r.taskTitle)
          put("beforeImagePath", r.beforeImagePath)
          put("afterImagePath", r.afterImagePath)
          put("approved", r.approved)
          put("confidencePercent", r.confidencePercent)
          put("transformationScore", r.transformationScore)
          put("whatChanged", r.whatChanged)
          put("feedback", r.feedback)
          put("createdAtMillis", r.createdAtMillis)
        }
      )
    }
    indexFile.writeText(arr.toString())
  }
}
