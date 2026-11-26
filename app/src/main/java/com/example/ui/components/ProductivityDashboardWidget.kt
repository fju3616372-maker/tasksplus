package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.DailyProductivityPoint
import com.example.data.model.DailyProductivitySummary
import com.example.data.model.ProductivityStatsCalculator
import com.example.data.model.TaskEntity
import kotlin.math.ceil

private val CompletedBarColor = Color(0xFF10B981) // Emerald green
private val PendingBarColor = Color(0xFFF59E0B)   // Amber gold

private enum class ProductivityChartRange {
  SEVEN_DAYS,
  TODAY
}

/**
 * Optional Daily Productivity Dashboard Widget featuring a Recharts-style grouped Bar Chart
 * comparing Completed vs. Pending tasks with dashed Cartesian gridlines, Y/X axes, legend,
 * and interactive day-selection tooltips.
 */
@Composable
fun ProductivityDashboardWidget(
  activeTasks: List<TaskEntity>,
  completedTasks: List<TaskEntity>,
  onHideWidget: () -> Unit,
  modifier: Modifier = Modifier
) {
  val summary = remember(activeTasks, completedTasks) {
    ProductivityStatsCalculator.computeDailySummary(
      activeTasks = activeTasks,
      completedTasks = completedTasks,
      dayCount = 7
    )
  }

  var isExpanded by rememberSaveable { mutableStateOf(false) }
  var selectedRangeName by rememberSaveable { mutableStateOf(ProductivityChartRange.SEVEN_DAYS.name) }
  val selectedRange = remember(selectedRangeName) {
    ProductivityChartRange.entries.firstOrNull { it.name == selectedRangeName }
      ?: ProductivityChartRange.SEVEN_DAYS
  }
  var selectedDayIndex by rememberSaveable { mutableIntStateOf(summary.days.lastIndex.coerceAtLeast(0)) }
  val safeSelectedIndex = selectedDayIndex.coerceIn(0, summary.days.lastIndex.coerceAtLeast(0))
  val focusedPoint = summary.days.getOrNull(safeSelectedIndex)

  Card(
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ),
    border = BorderStroke(
      width = 1.dp,
      color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    ),
    modifier = modifier
      .fillMaxWidth()
      .testTag("productivity_dashboard_widget")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      // Header row with title, Today's rate badge, collapse, and hide controls
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(14.dp))
          .clickable { isExpanded = !isExpanded },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
        ) {
          Icon(
            imageVector = Icons.Default.BarChart,
            contentDescription = stringResource(R.string.cd_productivity_chart),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
          )
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(R.string.productivity_dashboard_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = "Today: ${summary.todayCompleted} done • ${summary.todayPending} pending (${summary.todayCompletionPercent}%)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }

        IconButton(
          onClick = { isExpanded = !isExpanded },
          modifier = Modifier
            .size(48.dp)
            .testTag("productivity_widget_collapse_btn")
        ) {
          Icon(
            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = if (isExpanded) "Collapse productivity chart" else "Expand productivity chart"
          )
        }
        IconButton(
          onClick = onHideWidget,
          modifier = Modifier
            .size(48.dp)
            .testTag("productivity_widget_hide_btn")
        ) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Hide productivity widget"
          )
        }
      }

      AnimatedVisibility(visible = isExpanded) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          // Range Selector + Legend row
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              FilterChip(
                selected = selectedRange == ProductivityChartRange.SEVEN_DAYS,
                onClick = { selectedRangeName = ProductivityChartRange.SEVEN_DAYS.name },
                label = { Text("7 Days") },
                modifier = Modifier.testTag("productivity_range_7d")
              )
              FilterChip(
                selected = selectedRange == ProductivityChartRange.TODAY,
                onClick = { selectedRangeName = ProductivityChartRange.TODAY.name },
                label = { Text("Today") },
                modifier = Modifier.testTag("productivity_range_today")
              )
            }

            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              ChartLegendDot(color = CompletedBarColor, label = "Done")
              ChartLegendDot(color = PendingBarColor, label = "Pending")
            }
          }

          // Recharts-style Bar Chart body
          if (selectedRange == ProductivityChartRange.SEVEN_DAYS) {
            SevenDayGroupedBarChart(
              summary = summary,
              selectedIndex = safeSelectedIndex,
              onSelectIndex = { selectedDayIndex = it }
            )

            // Recharts-style interactive Tooltip summary card for the tapped day
            if (focusedPoint != null) {
              Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(
                  1.dp,
                  MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("productivity_tooltip_card")
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = focusedPoint.fullDateLabel,
                      style = MaterialTheme.typography.labelLarge,
                      fontWeight = FontWeight.Bold
                    )
                    Text(
                      text = "Completion rate: ${focusedPoint.completionPercent}%",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CountMetricPill(
                      label = "Done",
                      count = focusedPoint.completedCount,
                      accent = CompletedBarColor
                    )
                    CountMetricPill(
                      label = "Pending",
                      count = focusedPoint.pendingCount,
                      accent = PendingBarColor
                    )
                  }
                }
              }
            }
          } else {
            TodayComparisonBarChart(summary = summary)
          }

          if (summary.upcomingPendingCount > 0) {
            Text(
              text = "+${summary.upcomingPendingCount} upcoming pending task${if (summary.upcomingPendingCount == 1) "" else "s"} scheduled after today",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }
    }
  }
}

@Composable
private fun SevenDayGroupedBarChart(
  summary: DailyProductivitySummary,
  selectedIndex: Int,
  onSelectIndex: (Int) -> Unit
) {
  val yMax = remember(summary.maxBarValue) { computeNiceYAxisMax(summary.maxBarValue) }
  val yTicks = remember(yMax) { listOf(yMax, yMax / 2, 0) }
  val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
  val highlightBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
  val axisTextColor = MaterialTheme.colorScheme.onSurfaceVariant

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("productivity_7day_barchart"),
    verticalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(148.dp),
      verticalAlignment = Alignment.Bottom
    ) {
      // Y-Axis Labels
      Column(
        modifier = Modifier
          .width(28.dp)
          .fillMaxHeight()
          .padding(bottom = 2.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.End
      ) {
        yTicks.forEach { tick ->
          Text(
            text = tick.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = axisTextColor
          )
        }
      }

      Spacer(modifier = Modifier.width(6.dp))

      // Chart Plot Area: Dashed CartesianGrid + Grouped Bars
      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
      ) {
        // Horizontal dashed Cartesian gridlines
        Canvas(modifier = Modifier.fillMaxSize()) {
          val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
          val lineYs = listOf(4.dp.toPx(), size.height / 2f, size.height - 1.dp.toPx())
          lineYs.forEachIndexed { idx, y ->
            drawLine(
              color = gridColor,
              start = Offset(0f, y),
              end = Offset(size.width, y),
              strokeWidth = if (idx == 2) 1.5.dp.toPx() else 1.dp.toPx(),
              pathEffect = if (idx == 2) null else dashEffect
            )
          }
        }

        // Interactive day bar groups
        Row(
          modifier = Modifier.fillMaxSize(),
          horizontalArrangement = Arrangement.SpaceEvenly,
          verticalAlignment = Alignment.Bottom
        ) {
          summary.days.forEachIndexed { index, point ->
            val isSelected = index == selectedIndex
            val completedFrac by animateFloatAsState(
              targetValue = (point.completedCount.toFloat() / yMax.toFloat()).coerceIn(0f, 1f),
              animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
              label = "completed_bar_$index"
            )
            val pendingFrac by animateFloatAsState(
              targetValue = (point.pendingCount.toFloat() / yMax.toFloat()).coerceIn(0f, 1f),
              animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
              label = "pending_bar_$index"
            )

            Box(
              modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .background(if (isSelected) highlightBg else Color.Transparent)
                .clickable { onSelectIndex(index) }
                .padding(horizontal = 3.dp, vertical = 2.dp)
                .testTag("productivity_bar_day_$index"),
              contentAlignment = Alignment.BottomCenter
            ) {
              Canvas(modifier = Modifier.fillMaxSize()) {
                val usableHeight = (size.height - 6.dp.toPx()).coerceAtLeast(1f)
                val gap = 3.dp.toPx()
                val barWidth = ((size.width - gap) / 2f).coerceIn(4.dp.toPx(), 16.dp.toPx())
                val groupWidth = barWidth * 2f + gap
                val startX = (size.width - groupWidth) / 2f
                val corner = CornerRadius(4.dp.toPx(), 4.dp.toPx())

                val compHeight = if (point.completedCount > 0) {
                  (usableHeight * completedFrac).coerceAtLeast(4.dp.toPx())
                } else 0f
                val pendHeight = if (point.pendingCount > 0) {
                  (usableHeight * pendingFrac).coerceAtLeast(4.dp.toPx())
                } else 0f

                if (compHeight > 0f) {
                  drawRoundRect(
                    color = CompletedBarColor,
                    topLeft = Offset(startX, size.height - compHeight),
                    size = Size(barWidth, compHeight),
                    cornerRadius = corner
                  )
                }
                if (pendHeight > 0f) {
                  drawRoundRect(
                    color = PendingBarColor,
                    topLeft = Offset(startX + barWidth + gap, size.height - pendHeight),
                    size = Size(barWidth, pendHeight),
                    cornerRadius = corner
                  )
                }
              }
            }
          }
        }
      }
    }

    // X-Axis Day Labels aligned under bars
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 34.dp),
      horizontalArrangement = Arrangement.SpaceEvenly
    ) {
      summary.days.forEachIndexed { index, point ->
        val isSelected = index == selectedIndex
        Text(
          text = point.shortLabel,
          style = MaterialTheme.typography.labelSmall,
          fontWeight = if (isSelected || point.isToday) FontWeight.Bold else FontWeight.Normal,
          color = if (isSelected) MaterialTheme.colorScheme.primary else axisTextColor,
          textAlign = TextAlign.Center,
          maxLines = 1,
          overflow = TextOverflow.Clip,
          modifier = Modifier
            .weight(1f)
            .clickable { onSelectIndex(index) }
        )
      }
    }
  }
}

@Composable
private fun TodayComparisonBarChart(summary: DailyProductivitySummary) {
  val yMax = remember(summary.todayCompleted, summary.todayPending) {
    computeNiceYAxisMax(maxOf(summary.todayCompleted, summary.todayPending, 1))
  }
  val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
  val completedFrac by animateFloatAsState(
    targetValue = (summary.todayCompleted.toFloat() / yMax.toFloat()).coerceIn(0f, 1f),
    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
    label = "today_completed_bar"
  )
  val pendingFrac by animateFloatAsState(
    targetValue = (summary.todayPending.toFloat() / yMax.toFloat()).coerceIn(0f, 1f),
    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
    label = "today_pending_bar"
  )

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("productivity_today_barchart"),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(136.dp),
      verticalAlignment = Alignment.Bottom
    ) {
      Column(
        modifier = Modifier
          .width(28.dp)
          .fillMaxHeight(),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.End
      ) {
        Text(yMax.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text((yMax / 2).toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      Spacer(modifier = Modifier.width(8.dp))
      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
      ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
          val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
          val ys = listOf(4.dp.toPx(), size.height / 2f, size.height - 1.dp.toPx())
          ys.forEachIndexed { idx, y ->
            drawLine(
              color = gridColor,
              start = Offset(0f, y),
              end = Offset(size.width, y),
              strokeWidth = if (idx == 2) 1.5.dp.toPx() else 1.dp.toPx(),
              pathEffect = if (idx == 2) null else dashEffect
            )
          }

          val usableHeight = (size.height - 10.dp.toPx()).coerceAtLeast(1f)
          val barWidth = (size.width * 0.24f).coerceIn(28.dp.toPx(), 64.dp.toPx())
          val corner = CornerRadius(8.dp.toPx(), 8.dp.toPx())

          val leftCenterX = size.width * 0.30f
          val rightCenterX = size.width * 0.70f

          val compHeight = if (summary.todayCompleted > 0) (usableHeight * completedFrac).coerceAtLeast(6.dp.toPx()) else 0f
          val pendHeight = if (summary.todayPending > 0) (usableHeight * pendingFrac).coerceAtLeast(6.dp.toPx()) else 0f

          if (compHeight > 0f) {
            drawRoundRect(
              color = CompletedBarColor,
              topLeft = Offset(leftCenterX - barWidth / 2f, size.height - compHeight),
              size = Size(barWidth, compHeight),
              cornerRadius = corner
            )
          }
          if (pendHeight > 0f) {
            drawRoundRect(
              color = PendingBarColor,
              topLeft = Offset(rightCenterX - barWidth / 2f, size.height - pendHeight),
              size = Size(barWidth, pendHeight),
              cornerRadius = corner
            )
          }
        }
      }
    }

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 36.dp),
      horizontalArrangement = Arrangement.SpaceAround
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          text = "${summary.todayCompleted}",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.ExtraBold,
          color = CompletedBarColor
        )
        Text(
          text = "Completed",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          text = "${summary.todayPending}",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.ExtraBold,
          color = PendingBarColor
        )
        Text(
          text = "Pending",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

@Composable
private fun ChartLegendDot(color: Color, label: String) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    Box(
      modifier = Modifier
        .size(9.dp)
        .clip(CircleShape)
        .background(color)
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

@Composable
private fun CountMetricPill(label: String, count: Int, accent: Color) {
  Surface(
    color = accent.copy(alpha = 0.16f),
    shape = RoundedCornerShape(10.dp)
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      Box(
        modifier = Modifier
          .size(7.dp)
          .clip(CircleShape)
          .background(accent)
      )
      Text(
        text = "$count $label",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
      )
    }
  }
}

private fun computeNiceYAxisMax(rawMax: Int): Int {
  val safe = rawMax.coerceAtLeast(2)
  return if (safe <= 4) 4 else ceil(safe / 2.0).toInt() * 2
}
