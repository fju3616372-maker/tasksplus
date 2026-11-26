package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.ProjectDiaryPdfExporter
import kotlinx.coroutines.launch

private data class EditorialColors(
  val paper: Color,
  val insetCard: Color,
  val ink: Color,
  val mutedInk: Color,
  val hairline: Color,
  val indigo: Color,
  val emerald: Color,
  val amber: Color,
  val coral: Color,
  val violet: Color
)

@Composable
private fun rememberEditorialColors(): EditorialColors {
  val isDark = MaterialTheme.colorScheme.background.red < 0.25f || isSystemInDarkTheme() && MaterialTheme.colorScheme.surface.red < 0.25f
  return if (isDark) {
    EditorialColors(
      paper = Color(0xFF191714),
      insetCard = Color(0xFF23201B),
      ink = Color(0xFFF3ECE1),
      mutedInk = Color(0xFFAFA495),
      hairline = Color(0xFF363129),
      indigo = Color(0xFF8AA4FF),
      emerald = Color(0xFF4ADEA8),
      amber = Color(0xFFF5B84C),
      coral = Color(0xFFF07B68),
      violet = Color(0xFFB794F6)
    )
  } else {
    EditorialColors(
      paper = Color(0xFFFAF6EE),
      insetCard = Color(0xFFF1EAE0),
      ink = Color(0xFF1F1C18),
      mutedInk = Color(0xFF665E53),
      hairline = Color(0xFFDCD3C4),
      indigo = Color(0xFF3950A3),
      emerald = Color(0xFF177E5B),
      amber = Color(0xFFB87414),
      coral = Color(0xFFC24A38),
      violet = Color(0xFF6E48AA)
    )
  }
}

/**
 * Interactive, aesthetic 4-page editorial Project Diary Booklet showcasing Tasks+'s uses,
 * features, functions, build timeline ("how long it took"), architecture ("how it was made"),
 * and reflections, complete with 1-tap 4-page A4 PDF export.
 */
@Composable
fun ProjectDiaryBookletSection(
  onShowMessage: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val palette = rememberEditorialColors()
  val pagerState = rememberPagerState(initialPage = 0, pageCount = { 4 })

  val pageLabels = listOf(
    "01 • Cover & Uses",
    "02 • Features",
    "03 • Build Diary",
    "04 • Reflections"
  )

  fun triggerPdfExport() {
    ProjectDiaryPdfExporter.exportAndSharePdf(context)
      .onSuccess {
        onShowMessage("4-Page Project Diary PDF ready to save or share!")
      }
      .onFailure { err ->
        onShowMessage("Could not export PDF: ${err.localizedMessage ?: "unknown error"}")
      }
  }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .testTag("project_diary_booklet_root"),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    // Top booklet controls: page selector pills + PDF export action
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Icon(
          imageVector = Icons.Default.AutoStories,
          contentDescription = null,
          tint = palette.indigo,
          modifier = Modifier.size(20.dp)
        )
        Text(
          text = "4-Page Project Diary",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold
        )
      }

      FilledTonalButton(
        onClick = { triggerPdfExport() },
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.testTag("booklet_export_pdf_top_button")
      ) {
        Icon(
          imageVector = Icons.Default.PictureAsPdf,
          contentDescription = null,
          modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text("Export 4-Page PDF", style = MaterialTheme.typography.labelMedium)
      }
    }

    // Compact page jump strip (Pages 1–4)
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      pageLabels.forEachIndexed { idx, label ->
        val selected = pagerState.currentPage == idx
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = if (selected) palette.indigo else palette.insetCard,
          border = BorderStroke(1.dp, if (selected) palette.indigo else palette.hairline),
          modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(10.dp))
            .clickable {
              scope.launch { pagerState.animateScrollToPage(idx) }
            }
            .testTag("booklet_page_tab_${idx + 1}")
        ) {
          Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
          ) {
            Text(
              text = "P${idx + 1}",
              style = MaterialTheme.typography.labelMedium,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              color = if (selected) Color.White else palette.mutedInk,
              maxLines = 1
            )
          }
        }
      }
    }

    // Swipeable 4-Page Editorial Booklet Sheet
    HorizontalPager(
      state = pagerState,
      pageSpacing = 12.dp,
      modifier = Modifier
        .fillMaxWidth()
        .height(610.dp)
        .testTag("booklet_horizontal_pager")
    ) { pageIndex ->
      BookletPaperSheet(
        pageNumber = pageIndex + 1,
        sectionTag = pageLabels[pageIndex].uppercase(),
        palette = palette
      ) {
        when (pageIndex) {
          0 -> BookletPageOneCoverAndUses(palette = palette)
          1 -> BookletPageTwoFeatures(palette = palette)
          2 -> BookletPageThreeBuildDiary(palette = palette)
          else -> BookletPageFourReflections(
            palette = palette,
            onExportPdf = { triggerPdfExport() }
          )
        }
      }
    }

    // Bottom page navigation bar (Prev • Page Indicator • Next)
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      FilledTonalButton(
        onClick = {
          if (pagerState.currentPage > 0) {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
          }
        },
        enabled = pagerState.currentPage > 0,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.testTag("booklet_prev_page_button")
      ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous page", modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Prev")
      }

      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        repeat(4) { idx ->
          val active = pagerState.currentPage == idx
          Box(
            modifier = Modifier
              .size(width = if (active) 22.dp else 8.dp, height = 8.dp)
              .clip(CircleShape)
              .background(if (active) palette.indigo else palette.hairline)
          )
        }
      }

      FilledTonalButton(
        onClick = {
          if (pagerState.currentPage < 3) {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
          }
        },
        enabled = pagerState.currentPage < 3,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.testTag("booklet_next_page_button")
      ) {
        Text("Next")
        Spacer(modifier = Modifier.width(6.dp))
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next page", modifier = Modifier.size(16.dp))
      }
    }
  }
}

@Composable
private fun BookletPaperSheet(
  pageNumber: Int,
  sectionTag: String,
  palette: EditorialColors,
  content: @Composable () -> Unit
) {
  Surface(
    shape = RoundedCornerShape(20.dp),
    color = palette.paper,
    border = BorderStroke(1.2.dp, palette.hairline),
    shadowElevation = 4.dp,
    modifier = Modifier
      .fillMaxSize()
      .testTag("booklet_page_$pageNumber")
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
      // Running editorial folio header
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "TASKS+ DIARY",
          fontSize = 10.sp,
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.Bold,
          letterSpacing = 1.sp,
          color = palette.mutedInk
        )
        Text(
          text = sectionTag,
          fontSize = 10.sp,
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.SemiBold,
          color = palette.indigo,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }

      Spacer(modifier = Modifier.height(6.dp))
      HorizontalDivider(color = palette.hairline, thickness = 0.8.dp)
      Spacer(modifier = Modifier.height(10.dp))

      // Scrollable page body so every screen size reads comfortably without clipping
      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        content()
      }

      Spacer(modifier = Modifier.height(8.dp))
      HorizontalDivider(color = palette.hairline, thickness = 0.8.dp)
      Spacer(modifier = Modifier.height(6.dp))

      // Running editorial footer folio
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "ANDROID • KOTLIN & COMPOSE",
          fontSize = 9.5.sp,
          fontFamily = FontFamily.Monospace,
          color = palette.mutedInk
        )
        Text(
          text = "PAGE 0$pageNumber / 04",
          fontSize = 10.sp,
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.Bold,
          color = palette.ink
        )
      }
    }
  }
}

@Composable
private fun BookletPageOneCoverAndUses(palette: EditorialColors) {
  // Decorative editorial spectrum artwork header
  Canvas(
    modifier = Modifier
      .fillMaxWidth()
      .height(54.dp)
  ) {
    val colors = listOf(palette.emerald, Color(0xFF7CB342), palette.amber, palette.coral, palette.indigo)
    val gap = 5.dp.toPx()
    val segW = (size.width - gap * (colors.size - 1)) / colors.size
    colors.forEachIndexed { i, col ->
      drawRoundRect(
        color = col,
        topLeft = Offset(i * (segW + gap), 0f),
        size = Size(segW, 5.dp.toPx()),
        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
      )
    }

    // Subtle geometric horizon arcs on the right
    val arcCenter = Offset(size.width - 28.dp.toPx(), size.height * 0.65f)
    drawCircle(
      brush = Brush.radialGradient(
        colors = listOf(palette.indigo.copy(alpha = 0.24f), Color.Transparent),
        center = arcCenter,
        radius = 30.dp.toPx()
      ),
      radius = 30.dp.toPx(),
      center = arcCenter
    )
    drawCircle(
      color = palette.indigo.copy(alpha = 0.45f),
      radius = 18.dp.toPx(),
      center = arcCenter,
      style = Stroke(width = 1.2.dp.toPx())
    )
    drawCircle(
      color = palette.coral,
      radius = 5.dp.toPx(),
      center = arcCenter
    )
  }

  Text(
    text = "VOL. 01  •  PROJECT DIARY & FIELD GUIDE",
    fontSize = 10.sp,
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Bold,
    letterSpacing = 1.1.sp,
    color = palette.indigo
  )
  Text(
    text = "Tasks+",
    fontSize = 36.sp,
    lineHeight = 40.sp,
    fontFamily = FontFamily.Serif,
    fontWeight = FontWeight.Bold,
    color = palette.ink
  )
  Text(
    text = "Built for calm focus, daily momentum, family accountability, and mindful screen-time balance.",
    fontSize = 13.5.sp,
    lineHeight = 19.sp,
    fontFamily = FontFamily.Serif,
    fontStyle = FontStyle.Italic,
    color = palette.mutedInk
  )

  // Quick-facts editorial ribbon
  Surface(
    shape = RoundedCornerShape(14.dp),
    color = palette.insetCard,
    border = BorderStroke(1.dp, palette.hairline),
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      DiaryStatCell("~24 days", "Build Time", palette)
      DiaryStatCell("4 Phases", "Iterations", palette)
      DiaryStatCell("API 23–36", "Android", palette)
    }
  }

  Text(
    text = "Everyday Uses",
    fontSize = 16.sp,
    fontFamily = FontFamily.Serif,
    fontWeight = FontWeight.Bold,
    color = palette.ink
  )

  EditorialEntryCard(
    index = "01",
    title = "Personal Daily Routines",
    body = "Schedule one-time or recurring daily, weekly, and custom tasks with color categories and a clean Upcoming Recurring drawer.",
    accent = palette.emerald,
    palette = palette
  )
  EditorialEntryCard(
    index = "02",
    title = "Focused Countdown Sprints",
    body = "Run a live task timer visible right in your notifications and home-screen widgets, complete with a Time's Up alert.",
    accent = palette.indigo,
    palette = palette
  )
  EditorialEntryCard(
    index = "03",
    title = "Family & Team Accountability",
    body = "Assign shared group tasks, optionally review photo proofs with Gemini auto-approval, and supervise screen-time boundaries.",
    accent = palette.violet,
    palette = palette
  )
  EditorialEntryCard(
    index = "04",
    title = "Motivating Progress & Credits",
    body = "Track daily completed vs. pending bar charts, earn early-bird credits, unlock badges, and climb your Family or Workplace Group Leaderboard.",
    accent = palette.coral,
    palette = palette
  )
}

@Composable
private fun BookletPageTwoFeatures(palette: EditorialColors) {
  Text(
    text = "Features & Functions",
    fontSize = 22.sp,
    fontFamily = FontFamily.Serif,
    fontWeight = FontWeight.Bold,
    color = palette.ink
  )
  Text(
    text = "A concise catalog of every capability engineered into Tasks+.",
    fontSize = 12.5.sp,
    color = palette.mutedInk
  )

  EditorialEntryCard(
    index = "01",
    title = "Color Categories & Tactile Checklists",
    body = "Organize tasks with premade (Work, Personal, Shopping, Health, Study, Home) or custom color labels, filter via a slim top bar, and enjoy subtle radial checkmark animations.",
    accent = palette.indigo,
    palette = palette
  )
  EditorialEntryCard(
    index = "02",
    title = "1–5 Urgency Thermometer",
    body = "Continuous green-to-red thermometer slider with adaptive reminder cadence, due-date badges, and optional wake-up alarm bells.",
    accent = palette.coral,
    palette = palette
  )
  EditorialEntryCard(
    index = "03",
    title = "Live Task Countdown Timer",
    body = "Target selected tasks with a countdown synced across Settings, persistent notifications, and widgets—including +5m, pause/resume, and Time's Up alerts.",
    accent = palette.amber,
    palette = palette
  )
  EditorialEntryCard(
    index = "04",
    title = "Daily Productivity Dashboard",
    body = "Grouped Completed vs. Pending bar chart (7-Day & Today views) built in Compose Canvas with an optional 4×3 home-screen widget.",
    accent = palette.emerald,
    palette = palette
  )
  EditorialEntryCard(
    index = "05",
    title = "Groups, Photo Proof & Screen-Time Locks",
    body = "Multi-user group invites, role-based supervision, optional Gemini AI photo verification, usage tracking, and task-gated app locking.",
    accent = palette.violet,
    palette = palette
  )
  EditorialEntryCard(
    index = "06",
    title = "Credit System & Group Leaderboard",
    body = "Earn or customize points per second saved before deadlines, unlock collectible profile icons, and compare live rankings inside your Family or Workplace group.",
    accent = palette.indigo,
    palette = palette
  )
}

@Composable
private fun BookletPageThreeBuildDiary(palette: EditorialColors) {
  Text(
    text = "Build Diary & Timeline",
    fontSize = 22.sp,
    fontFamily = FontFamily.Serif,
    fontWeight = FontWeight.Bold,
    color = palette.ink
  )
  Text(
    text = "How Tasks+ was crafted from first commit to polished release (~24 days total).",
    fontSize = 12.5.sp,
    color = palette.mutedInk
  )

  val phases = listOf(
    Triple(
      "PHASE 01 • ~7 DAYS — Core Engine & Material 3 Foundation",
      palette.indigo,
      "Built the Jetpack Compose M3 interface, Room SQLite database, recurring occurrence engine, AlarmManager reminder pipeline, dynamic theming, and home-screen widgets."
    ),
    Triple(
      "PHASE 02 • ~6 DAYS — Cloud Sync, Groups & Screen Time",
      palette.violet,
      "Integrated Credential Manager Google Sign-In, real-time Firestore sync backed by zero-trust security rules, family/team groups, Gemini photo verification, and screen-time guards."
    ),
    Triple(
      "PHASE 03 • ~4 DAYS — Gamification, Timer & Productivity Chart",
      palette.emerald,
      "Added credit scoring rules, unlockable profile icons, the Family & Workplace Group Leaderboard, live Task Countdown Timer (notifications + widgets), and the Daily Productivity Bar Chart."
    ),
    Triple(
      "PHASE 04 • ~7 DAYS — Visual Polish, Categories & Hardening",
      palette.coral,
      "Refined the Urgency Thermometer, added radial ripple checkmark animations, built optional custom/premade Categories, fixed recurring cloud sync, and wrote Robolectric tests."
    )
  )

  phases.forEach { (heading, color, body) ->
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalAlignment = Alignment.Top
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(top = 6.dp)
      ) {
        Box(
          modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color)
        )
        Box(
          modifier = Modifier
            .width(2.dp)
            .height(64.dp)
            .background(palette.hairline)
        )
      }
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = palette.insetCard,
        border = BorderStroke(1.dp, palette.hairline),
        modifier = Modifier.weight(1f)
      ) {
        Column(
          modifier = Modifier.padding(11.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          Text(
            text = heading,
            fontSize = 10.5.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = color
          )
          Text(
            text = body,
            fontSize = 12.sp,
            lineHeight = 16.5.sp,
            color = palette.ink
          )
        }
      }
    }
  }

  // Tech stack summary card
  Surface(
    shape = RoundedCornerShape(14.dp),
    color = palette.insetCard,
    border = BorderStroke(1.dp, palette.hairline),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      Text(
        text = "HOW IT WAS MADE — TECH STACK",
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        color = palette.indigo
      )
      Text(
        text = "Kotlin • Jetpack Compose & Material 3 • Room SQLite (v8) • DataStore • Firebase Auth, Firestore & AI Logic • Android AppWidgets • AlarmManager • Robolectric",
        fontSize = 12.sp,
        lineHeight = 17.sp,
        color = palette.mutedInk
      )
    }
  }
}

@Composable
private fun BookletPageFourReflections(
  palette: EditorialColors,
  onExportPdf: () -> Unit
) {
  Text(
    text = "Reflections & Colophon",
    fontSize = 22.sp,
    fontFamily = FontFamily.Serif,
    fontWeight = FontWeight.Bold,
    color = palette.ink
  )
  Text(
    text = "Key engineering lessons recorded during development.",
    fontSize = 12.5.sp,
    color = palette.mutedInk
  )

  EditorialEntryCard(
    index = "01",
    title = "Taming Recurring Cloud Sync",
    body = "Catching Kotlin's `isCompleted` Firestore getter mismatch solved duplicate daily tasks—paired with explicit snapshot parsing and an Upcoming Recurring section.",
    accent = palette.emerald,
    palette = palette
  )
  EditorialEntryCard(
    index = "02",
    title = "Reliable Time's Up Alerts",
    body = "Combining AlarmManager triggers, synchronous broadcast wake locks, and dedicated alert notification channels ensures countdown alerts fire without getting deferred.",
    accent = palette.coral,
    palette = palette
  )
  EditorialEntryCard(
    index = "03",
    title = "API 23–36 Compatibility",
    body = "Custom Canvas widgets, charts, and animations were written using timeless APIs so older Android 6 phones and modern Android 16 flagships share the exact same polish.",
    accent = palette.indigo,
    palette = palette
  )

  // Editorial pull-quote
  Surface(
    shape = RoundedCornerShape(14.dp),
    color = palette.insetCard,
    border = BorderStroke(1.dp, palette.hairline),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Text(
        text = "“Great productivity software respects your attention—gentle when you're in flow, unmistakable when it matters.”",
        fontSize = 13.5.sp,
        lineHeight = 19.sp,
        fontFamily = FontFamily.Serif,
        fontStyle = FontStyle.Italic,
        color = palette.ink
      )
      Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        listOf(palette.indigo, palette.violet, palette.emerald, palette.amber, palette.coral).forEach { col ->
          Box(
            modifier = Modifier
              .size(9.dp)
              .clip(CircleShape)
              .background(col)
          )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = "Crafted with care by frosty-sys",
          fontSize = 11.sp,
          fontFamily = FontFamily.Monospace,
          color = palette.mutedInk
        )
      }
    }
  }

  Button(
    onClick = onExportPdf,
    shape = RoundedCornerShape(14.dp),
    colors = ButtonDefaults.buttonColors(
      containerColor = palette.indigo,
      contentColor = Color.White
    ),
    modifier = Modifier
      .fillMaxWidth()
      .height(48.dp)
      .testTag("booklet_export_pdf_bottom_button")
  ) {
    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
    Spacer(modifier = Modifier.width(8.dp))
    Text("Export & Share 4-Page PDF Booklet")
  }
}

@Composable
private fun DiaryStatCell(
  value: String,
  label: String,
  palette: EditorialColors
) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
      text = value,
      fontSize = 14.sp,
      fontFamily = FontFamily.Monospace,
      fontWeight = FontWeight.Bold,
      color = palette.indigo
    )
    Text(
      text = label,
      fontSize = 10.5.sp,
      color = palette.mutedInk
    )
  }
}

@Composable
private fun EditorialEntryCard(
  index: String,
  title: String,
  body: String,
  accent: Color,
  palette: EditorialColors
) {
  Surface(
    shape = RoundedCornerShape(12.dp),
    color = palette.insetCard,
    border = BorderStroke(1.dp, palette.hairline),
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(11.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalAlignment = Alignment.Top
    ) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .size(26.dp)
          .clip(RoundedCornerShape(8.dp))
          .background(accent.copy(alpha = 0.16f))
      ) {
        Text(
          text = index,
          fontSize = 10.sp,
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.Bold,
          color = accent
        )
      }
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(2.dp)
      ) {
        Text(
          text = title,
          fontSize = 13.sp,
          fontWeight = FontWeight.SemiBold,
          color = palette.ink
        )
        Text(
          text = body,
          fontSize = 12.sp,
          lineHeight = 16.5.sp,
          color = palette.mutedInk
        )
      }
    }
  }
}
