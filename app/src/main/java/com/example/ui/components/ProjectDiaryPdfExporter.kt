package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Generates an aesthetic, print-ready 4-page A4 Project Diary PDF booklet for Tasks+
 * using Android's native PdfDocument API (API 23+ compatible) and shares it via FileProvider.
 */
object ProjectDiaryPdfExporter {

  private const val PAGE_WIDTH = 595   // A4 at 72 dpi
  private const val PAGE_HEIGHT = 842
  private const val MARGIN = 44f

  private val paperBg = Color.parseColor("#FAF6EE")
  private val cardBg = Color.parseColor("#F2ECE0")
  private val inkPrimary = Color.parseColor("#1F1C18")
  private val inkMuted = Color.parseColor("#635B50")
  private val hairline = Color.parseColor("#D8CFC0")
  private val accentIndigo = Color.parseColor("#3B52A4")
  private val accentCoral = Color.parseColor("#C24A38")
  private val accentEmerald = Color.parseColor("#1E7F5C")
  private val accentAmber = Color.parseColor("#B87414")
  private val accentViolet = Color.parseColor("#6E48AA")

  fun generateFourPagePdf(context: Context): File {
    val outDir = File(context.cacheDir, "booklet").apply { mkdirs() }
    val outFile = File(outDir, "TasksPlus_Project_Diary_Booklet.pdf")

    val pdf = PdfDocument()
    try {
      drawPage1CoverAndUses(pdf)
      drawPage2FeaturesAndFunctions(pdf)
      drawPage3BuildDiaryAndTimeline(pdf)
      drawPage4ReflectionsAndColophon(pdf)

      FileOutputStream(outFile).use { fos ->
        pdf.writeTo(fos)
      }
    } finally {
      pdf.close()
    }
    return outFile
  }

  fun exportAndSharePdf(context: Context): Result<File> {
    return try {
      val pdfFile = generateFourPagePdf(context)
      val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        pdfFile
      )
      val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "Tasks+ — 4-Page Project Diary Booklet")
        putExtra(Intent.EXTRA_TEXT, "Aesthetic 4-page Project Diary Booklet for the Tasks+ Android app.")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
      val chooser = Intent.createChooser(shareIntent, "Share 4-Page Project Diary PDF").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(chooser)
      Result.success(pdfFile)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  private fun startEditorialPage(pdf: PdfDocument, pageNum: Int, sectionTag: String): Pair<PdfDocument.Page, Canvas> {
    val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
    val page = pdf.startPage(info)
    val canvas = page.canvas

    // Warm archival paper background
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = paperBg }
    canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat(), bgPaint)

    // Editorial outer hairline frame
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = hairline
      style = Paint.Style.STROKE
      strokeWidth = 1f
    }
    canvas.drawRoundRect(
      RectF(22f, 22f, PAGE_WIDTH - 22f, PAGE_HEIGHT - 22f),
      10f,
      10f,
      borderPaint
    )

    // Running header & footer folio
    val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = inkMuted
      textSize = 9f
      typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
    }
    canvas.drawText("TASKS+  •  PROJECT DIARY BOOKLET", MARGIN, 46f, metaPaint)
    val rightTagWidth = metaPaint.measureText(sectionTag)
    canvas.drawText(sectionTag, PAGE_WIDTH - MARGIN - rightTagWidth, 46f, metaPaint)

    val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = hairline
      strokeWidth = 0.8f
    }
    canvas.drawLine(MARGIN, 54f, PAGE_WIDTH - MARGIN, 54f, rulePaint)
    canvas.drawLine(MARGIN, PAGE_HEIGHT - 48f, PAGE_WIDTH - MARGIN, PAGE_HEIGHT - 48f, rulePaint)

    val folio = "PAGE 0$pageNum / 04"
    canvas.drawText("CRAFTED FOR ANDROID  •  KOTLIN & JETPACK COMPOSE", MARGIN, PAGE_HEIGHT - 32f, metaPaint)
    val folioW = metaPaint.measureText(folio)
    canvas.drawText(folio, PAGE_WIDTH - MARGIN - folioW, PAGE_HEIGHT - 32f, metaPaint)

    return page to canvas
  }

  private fun drawPage1CoverAndUses(pdf: PdfDocument) {
    val (page, c) = startEditorialPage(pdf, 1, "01 / COVER & USES")

    // Editorial top color ribbon
    val ribbonColors = intArrayOf(accentEmerald, Color.parseColor("#7CB342"), accentAmber, accentCoral, accentIndigo)
    val segW = (PAGE_WIDTH - MARGIN * 2f) / ribbonColors.size
    val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    ribbonColors.forEachIndexed { idx, color ->
      barPaint.color = color
      c.drawRoundRect(
        RectF(MARGIN + idx * segW, 72f, MARGIN + (idx + 1) * segW - 4f, 78f),
        3f,
        3f,
        barPaint
      )
    }

    // Cover Typography
    val eyebrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = accentIndigo
      textSize = 10.5f
      typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    c.drawText("VOL. 01  •  PRODUCT FIELD GUIDE & BUILD JOURNAL", MARGIN, 108f, eyebrowPaint)

    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = inkPrimary
      textSize = 42f
      typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }
    c.drawText("Tasks+", MARGIN, 156f, titlePaint)

    val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = inkMuted
      textSize = 14.5f
      typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
    }
    c.drawText("A calm, tactile workspace for daily momentum, focus sprints,", MARGIN, 184f, subtitlePaint)
    c.drawText("family accountability, and mindful screen-time habits.", MARGIN, 204f, subtitlePaint)

    // At-a-glance stats strip
    val statsRect = RectF(MARGIN, 226f, PAGE_WIDTH - MARGIN, 294f)
    val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cardBg }
    c.drawRoundRect(statsRect, 12f, 12f, cardPaint)

    val stats = listOf(
      "~24 DAYS" to "Iterative Build",
      "4 PHASES" to "Design to Release",
      "API 23–36" to "Android Reach",
      "CLOUD + LOCAL" to "Room & Firestore"
    )
    val statW = (PAGE_WIDTH - MARGIN * 2f) / stats.size
    val statNumPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = accentIndigo
      textSize = 14f
      typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    val statSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = inkMuted
      textSize = 9.5f
      typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }
    stats.forEachIndexed { idx, (big, small) ->
      val x = MARGIN + 14f + idx * statW
      c.drawText(big, x, 256f, statNumPaint)
      c.drawText(small, x, 274f, statSubPaint)
    }

    // Section Heading: Everyday Uses
    var y = 336f
    val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = inkPrimary
      textSize = 18f
      typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }
    c.drawText("Everyday Uses & Purpose", MARGIN, y, sectionPaint)
    y += 22f

    val uses = listOf(
      Triple(
        "01  Personal Daily Planning & Habits",
        accentEmerald,
        "Organize one-time errands and repeating daily, weekly, or custom routines with color-coded categories, urgency thermometers, and distraction-free upcoming drawers."
      ),
      Triple(
        "02  Time-Boxed Focus Sprints",
        accentIndigo,
        "Pair specific tasks with a live countdown timer visible in notifications and home-screen widgets, backed by a high-priority Time's Up alert."
      ),
      Triple(
        "03  Family & Team Accountability",
        accentViolet,
        "Create shared groups, assign tasks to members or supervised children, and optionally verify completion with photo proof or Gemini auto-approval."
      ),
      Triple(
        "04  Mindful Screen-Time & Positive Motivation",
        accentCoral,
        "Set daily/hourly app limits or task-gated locks, earn early-completion credit points, unlock profile badges, and track standings on your Family or Workplace Group Leaderboard."
      )
    )

    for ((heading, accent, body) in uses) {
      val box = RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 92f)
      c.drawRoundRect(box, 10f, 10f, cardPaint)
      barPaint.color = accent
      c.drawRoundRect(RectF(MARGIN, y + 10f, MARGIN + 4.5f, y + 82f), 2f, 2f, barPaint)

      val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = inkPrimary
        textSize = 12.5f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
      }
      c.drawText(heading, MARGIN + 16f, y + 26f, headPaint)
      drawWrappedText(c, body, MARGIN + 16f, y + 46f, PAGE_WIDTH - MARGIN * 2f - 28f, 10.5f, 15f, inkMuted)
      y += 104f
    }

    pdf.finishPage(page)
  }

  private fun drawPage2FeaturesAndFunctions(pdf: PdfDocument) {
    val (page, c) = startEditorialPage(pdf, 2, "02 / FEATURES & FUNCTIONS")

    val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = inkPrimary
      textSize = 22f
      typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }
    c.drawText("Features & Core Functions", MARGIN, 96f, sectionPaint)

    val introPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = inkMuted
      textSize = 11f
      typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }
    c.drawText("Every module inside Tasks+ was crafted to work offline-first while syncing seamlessly.", MARGIN, 118f, introPaint)

    val features = listOf(
      Triple(
        "01  Color Categories & Tactile Checklists",
        accentIndigo,
        "Assign premade (Work, Personal, Shopping, Health, Study, Home) or custom color categories, filter from a slim top bar, and enjoy ripple checkmark animations on completion."
      ),
      Triple(
        "02  1–5 Urgency Thermometer & Priority Alerts",
        accentCoral,
        "Slide smoothly from Calm (1) to Critical (5) on a full-width green-to-red thermometer with adaptive reminder intervals and optional wake-up alarm bells."
      ),
      Triple(
        "03  Task Countdown Timer & Time's Up Alert",
        accentAmber,
        "Select one or more tasks, set a sprint duration, monitor live countdowns in notifications and home-screen widgets, and receive a guaranteed alert when time expires."
      ),
      Triple(
        "04  Daily Productivity Bar Chart Dashboard",
        accentEmerald,
        "Inspect Completed vs. Pending tasks across 7 days or Today via an interactive grouped bar chart widget available both inside the app and on the home screen."
      ),
      Triple(
        "05  Groups, Photo Proof & App Screen-Time Locks",
        accentViolet,
        "Manage family or team groups with role permissions, optional Gemini photo verification, real-time usage tracking, and scheduled or task-gated app locks."
      ),
      Triple(
        "06  Credit Economy, Badges & Group Leaderboard",
        accentIndigo,
        "Earn points for beating deadlines, customize scoring rules, unlock profile icons, and compare live member scores on your private Family or Workplace Group Leaderboard."
      )
    )

    val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cardBg }
    val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    var y = 140f
    for ((title, accentColor, desc) in features) {
      c.drawRoundRect(RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 96f), 10f, 10f, cardPaint)
      barPaint.color = accentColor
      c.drawCircle(MARGIN + 18f, y + 24f, 5f, barPaint)

      val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = inkPrimary
        textSize = 12.5f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
      }
      c.drawText(title, MARGIN + 30f, y + 28f, headPaint)
      drawWrappedText(c, desc, MARGIN + 18f, y + 48f, PAGE_WIDTH - MARGIN * 2f - 32f, 10.2f, 14.5f, inkMuted)
      y += 108f
    }

    pdf.finishPage(page)
  }

  private fun drawPage3BuildDiaryAndTimeline(pdf: PdfDocument) {
    val (page, c) = startEditorialPage(pdf, 3, "03 / BUILD DIARY & TIMELINE")

    val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = inkPrimary
      textSize = 22f
      typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }
    c.drawText("The Build Diary: How It Was Made", MARGIN, 96f, sectionPaint)

    val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = inkMuted
      textSize = 11f
    }
    c.drawText("Total craft time: ~24 days across four focused engineering & design phases.", MARGIN, 118f, subPaint)

    val timelineEntries = listOf(
      Triple(
        "PHASE 01  •  ~7 DAYS  —  Core Engine & Material 3 Foundation",
        accentIndigo,
        "Built the Jetpack Compose M3 interface, Room SQLite database, recurring occurrence engine, AlarmManager reminder pipeline, dynamic theming, and home-screen widgets."
      ),
      Triple(
        "PHASE 02  •  ~6 DAYS  —  Cloud Sync, Groups & Screen Time",
        accentViolet,
        "Integrated Credential Manager Google Sign-In, real-time Firestore sync backed by zero-trust security rules, family/team groups, Gemini photo verification, and screen-time guards."
      ),
      Triple(
        "PHASE 03  •  ~4 DAYS  —  Gamification, Timer & Productivity Chart",
        accentEmerald,
        "Added credit scoring rules, unlockable profile icons, the Family & Workplace Group Leaderboard, live Task Countdown Timer (notifications + widgets), and the Daily Productivity Bar Chart."
      ),
      Triple(
        "PHASE 04  •  ~7 DAYS  —  Visual Polish, Categories & Hardening",
        accentCoral,
        "Refined the Urgency Thermometer, added radial ripple checkmark animations, built optional custom/premade Categories, fixed recurring cloud sync, and wrote Robolectric tests."
      )
    )

    val spineX = MARGIN + 14f
    val spinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = hairline
      strokeWidth = 2f
    }
    c.drawLine(spineX, 152f, spineX, 558f, spinePaint)

    val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cardBg }
    var y = 140f

    for ((label, color, detail) in timelineEntries) {
      dotPaint.color = color
      c.drawCircle(spineX, y + 20f, 6f, dotPaint)
      c.drawRoundRect(RectF(MARGIN + 32f, y, PAGE_WIDTH - MARGIN, y + 96f), 10f, 10f, cardPaint)

      val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = 10f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
      }
      c.drawText(label, MARGIN + 46f, y + 24f, labelPaint)
      drawWrappedText(c, detail, MARGIN + 46f, y + 44f, PAGE_WIDTH - MARGIN * 2f - 60f, 10.2f, 14.5f, inkPrimary)
      y += 110f
    }

    // Tech Stack Blueprint Card
    val stackTop = 594f
    c.drawRoundRect(RectF(MARGIN, stackTop, PAGE_WIDTH - MARGIN, 764f), 12f, 12f, cardPaint)
    val stackHead = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = inkPrimary
      textSize = 13.5f
      typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }
    c.drawText("Technical Architecture & Stack", MARGIN + 16f, stackTop + 28f, stackHead)

    val stackRows = listOf(
      "UI & Motion:" to "Kotlin, Jetpack Compose, Material 3, Custom Canvas & Spring Animations",
      "Local Persistence:" to "Room SQLite (v8 with safe migrations) + Jetpack DataStore Preferences",
      "Cloud & Auth:" to "Firebase Firestore (named DB + zero-trust rules) & Credential Manager",
      "AI & Background:" to "Firebase AI Logic (Gemini proof review), AlarmManager & AppWidgets",
      "Quality & Tests:" to "Robolectric API 23–36 regression suites & Firestore Rules unit tests"
    )
    var sy = stackTop + 52f
    val kPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = accentIndigo
      textSize = 10f
      typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    val vPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = inkMuted
      textSize = 10f
      typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }
    for ((k, v) in stackRows) {
      c.drawText(k, MARGIN + 16f, sy, kPaint)
      c.drawText(v, MARGIN + 126f, sy, vPaint)
      sy += 22f
    }

    pdf.finishPage(page)
  }

  private fun drawPage4ReflectionsAndColophon(pdf: PdfDocument) {
    val (page, c) = startEditorialPage(pdf, 4, "04 / REFLECTIONS & COLOPHON")

    val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = inkPrimary
      textSize = 22f
      typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }
    c.drawText("Engineering Notes & Reflections", MARGIN, 96f, sectionPaint)

    val challenges = listOf(
      Triple(
        "Challenge 01 — Recurring Tasks & Cloud Sync Parity",
        accentEmerald,
        "Kotlin's `isCompleted` boolean getter caused Firestore reflection to miss completed snapshots, duplicating daily tasks. Switching to explicit snapshot parsing, @PropertyName annotations, and an Upcoming Recurring drawer made daily routines rock-solid."
      ),
      Triple(
        "Challenge 02 — Guaranteed Timer Alerts Under OS Limits",
        accentCoral,
        "Background broadcast delays can swallow timer expirations. Combining AlarmManager exact triggers, synchronous receiver execution with wake locks, and a dedicated high-priority alert channel ensures Time's Up notifications fire reliably."
      ),
      Triple(
        "Challenge 03 — Tactile Polish From Android 6 to Android 16",
        accentIndigo,
        "Supporting API 23 through API 36 meant writing custom Canvas visuals—like the Urgency Thermometer, grouped Productivity Bar Chart, and radial checkmark animation—without relying on modern-only platform APIs."
      )
    )

    val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cardBg }
    val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    var y = 122f
    for ((heading, color, body) in challenges) {
      c.drawRoundRect(RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 114f), 10f, 10f, cardPaint)
      accentPaint.color = color
      c.drawRoundRect(RectF(MARGIN, y + 12f, MARGIN + 4.5f, y + 102f), 2f, 2f, accentPaint)

      val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = inkPrimary
        textSize = 12f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
      }
      c.drawText(heading, MARGIN + 16f, y + 26f, headPaint)
      drawWrappedText(c, body, MARGIN + 16f, y + 46f, PAGE_WIDTH - MARGIN * 2f - 28f, 10.2f, 15f, inkMuted)
      y += 126f
    }

    // Editorial Quote Block
    val quoteY = 516f
    val quotePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = inkPrimary
      textSize = 14f
      typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
    }
    c.drawText("\"Great productivity software respects your attention—", MARGIN + 12f, quoteY, quotePaint)
    c.drawText("gentle when you're in flow, unmistakable when it matters.\"", MARGIN + 12f, quoteY + 22f, quotePaint)

    // Colophon Box
    val colTop = 580f
    c.drawRoundRect(RectF(MARGIN, colTop, PAGE_WIDTH - MARGIN, 756f), 12f, 12f, cardPaint)
    val colTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = accentIndigo
      textSize = 10.5f
      typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    c.drawText("COLOPHON  •  TASKS+ PROJECT DIARY", MARGIN + 18f, colTop + 28f, colTitle)

    val colBody = "Designed and built iteratively as a full-featured Android productivity suite combining calm aesthetics, tactile feedback, cross-device cloud backup, family/team accountability, and thoughtful focus tooling."
    drawWrappedText(c, colBody, MARGIN + 18f, colTop + 50f, PAGE_WIDTH - MARGIN * 2f - 36f, 10.5f, 15.5f, inkMuted)

    val creditPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = inkPrimary
      textSize = 11f
      typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }
    c.drawText("Crafted with care by frosty-sys  •  Tasks+ Android Edition", MARGIN + 18f, colTop + 122f, creditPaint)

    // Palette dots
    val palette = intArrayOf(accentIndigo, accentViolet, accentEmerald, accentAmber, accentCoral)
    palette.forEachIndexed { idx, col ->
      accentPaint.color = col
      c.drawCircle(MARGIN + 24f + idx * 22f, colTop + 148f, 6f, accentPaint)
    }

    pdf.finishPage(page)
  }

  private fun drawWrappedText(
    canvas: Canvas,
    text: String,
    startX: Float,
    startY: Float,
    maxWidth: Float,
    textSize: Float,
    lineHeight: Float,
    color: Int
  ) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      this.color = color
      this.textSize = textSize
      typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }
    val words = text.split(" ")
    var line = StringBuilder()
    var y = startY
    for (word in words) {
      val candidate = if (line.isEmpty()) word else "$line $word"
      if (paint.measureText(candidate) <= maxWidth) {
        line = StringBuilder(candidate)
      } else {
        canvas.drawText(line.toString(), startX, y, paint)
        y += lineHeight
        line = StringBuilder(word)
      }
    }
    if (line.isNotEmpty()) {
      canvas.drawText(line.toString(), startX, y, paint)
    }
  }
}
