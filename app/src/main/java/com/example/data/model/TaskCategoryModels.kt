package com.example.data.model

import org.json.JSONArray
import org.json.JSONObject

data class TaskCategory(
  val id: String,
  val name: String,
  val colorHex: Long,
  val isPremade: Boolean = false
)

object TaskCategoryCatalog {

  val premadeCategories: List<TaskCategory> = listOf(
    TaskCategory(id = "cat_work", name = "Work", colorHex = 0xFF2563EBL, isPremade = true),
    TaskCategory(id = "cat_personal", name = "Personal", colorHex = 0xFF7C3AEDL, isPremade = true),
    TaskCategory(id = "cat_shopping", name = "Shopping", colorHex = 0xFF059669L, isPremade = true),
    TaskCategory(id = "cat_health", name = "Health", colorHex = 0xFFDB2777L, isPremade = true),
    TaskCategory(id = "cat_study", name = "Study", colorHex = 0xFFD97706L, isPremade = true),
    TaskCategory(id = "cat_home", name = "Home", colorHex = 0xFF0D9488L, isPremade = true)
  )

  val colorSwatches: List<Long> = listOf(
    0xFF2563EBL, // Royal Blue
    0xFF7C3AEDL, // Violet
    0xFF059669L, // Emerald
    0xFFDB2777L, // Rose
    0xFFD97706L, // Amber
    0xFF0D9488L, // Teal
    0xFFDC2626L, // Crimson
    0xFF4F46E5L, // Indigo
    0xFF65A30DL, // Lime
    0xFFEA580CL  // Vivid Orange
  )

  fun activeCategories(
    disabledPremadeIds: Set<String>,
    customCategories: List<TaskCategory>
  ): List<TaskCategory> {
    val enabledPremade = premadeCategories.filter { it.id !in disabledPremadeIds }
    return enabledPremade + customCategories
  }

  fun resolveById(
    categoryId: String,
    customCategories: List<TaskCategory>
  ): TaskCategory? {
    if (categoryId.isBlank()) return null
    return customCategories.firstOrNull { it.id == categoryId }
      ?: premadeCategories.firstOrNull { it.id == categoryId }
  }

  fun serializeCustomCategories(categories: List<TaskCategory>): String {
    val array = JSONArray()
    categories.take(30).forEach { cat ->
      val cleanName = cat.name.trim().take(32)
      if (cleanName.isNotEmpty()) {
        val obj = JSONObject()
        obj.put("id", cat.id.take(64))
        obj.put("name", cleanName)
        obj.put("colorHex", cat.colorHex)
        array.put(obj)
      }
    }
    return array.toString()
  }

  fun parseCustomCategories(rawJson: String?): List<TaskCategory> {
    if (rawJson.isNullOrBlank()) return emptyList()
    return try {
      val array = JSONArray(rawJson)
      val out = mutableListOf<TaskCategory>()
      val seenIds = mutableSetOf<String>()
      for (i in 0 until array.length()) {
        val obj = array.optJSONObject(i) ?: continue
        val id = obj.optString("id").trim()
        val name = obj.optString("name").trim().take(32)
        val colorHex = obj.optLong("colorHex", colorSwatches.first())
        if (id.isNotEmpty() && name.isNotEmpty() && seenIds.add(id)) {
          out.add(
            TaskCategory(
              id = id,
              name = name,
              colorHex = colorHex,
              isPremade = false
            )
          )
        }
      }
      out
    } catch (_: Exception) {
      emptyList()
    }
  }

  fun parseIdCsv(csv: String?): Set<String> {
    if (csv.isNullOrBlank()) return emptySet()
    return csv.split(",")
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .toSet()
  }

  fun toIdCsv(ids: Set<String>): String {
    return ids.map { it.trim() }
      .filter { it.isNotEmpty() }
      .sorted()
      .joinToString(",")
  }
}
