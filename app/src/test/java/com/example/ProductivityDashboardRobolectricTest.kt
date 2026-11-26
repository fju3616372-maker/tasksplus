package com.example

import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.ProductivityStatsCalculator
import com.example.data.model.TaskEntity
import com.example.widget.ProductivityChartBitmapRenderer
import com.example.widget.TasksWidgetUpdater
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.M])
class ProductivityDashboardRobolectricTest {

  private fun localDayMillis(daysOffset: Int, hour: Int = 12): Long {
    val cal = Calendar.getInstance().apply {
      set(Calendar.HOUR_OF_DAY, hour)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
      add(Calendar.DAY_OF_YEAR, daysOffset)
    }
    return cal.timeInMillis
  }

  @Test
  fun computeDailySummary_bucketsCompletedPendingOverdueAndUpcomingAccurately() {
    val nowMillis = localDayMillis(daysOffset = 0, hour = 15)
    val todayNoon = localDayMillis(daysOffset = 0, hour = 12)
    val yesterdayNoon = localDayMillis(daysOffset = -1, hour = 12)
    val tenDaysAgo = localDayMillis(daysOffset = -10, hour = 10)
    val threeDaysFromNow = localDayMillis(daysOffset = 3, hour = 14)

    val completedTasks = listOf(
      TaskEntity(
        id = "c1",
        title = "Done today 1",
        scheduledAtMillis = todayNoon,
        maxCompletionMillis = todayNoon,
        isCompleted = true,
        completedAtMillis = todayNoon
      ),
      TaskEntity(
        id = "c2",
        title = "Done today 2",
        scheduledAtMillis = todayNoon,
        maxCompletionMillis = todayNoon,
        isCompleted = true,
        completedAtMillis = todayNoon
      ),
      TaskEntity(
        id = "c3",
        title = "Done yesterday",
        scheduledAtMillis = yesterdayNoon,
        maxCompletionMillis = yesterdayNoon,
        isCompleted = true,
        completedAtMillis = yesterdayNoon
      )
    )

    val activeTasks = listOf(
      TaskEntity(
        id = "a1",
        title = "Pending today",
        scheduledAtMillis = todayNoon,
        maxCompletionMillis = todayNoon,
        isCompleted = false
      ),
      TaskEntity(
        id = "a2",
        title = "Overdue from 10 days ago",
        scheduledAtMillis = tenDaysAgo,
        maxCompletionMillis = tenDaysAgo,
        isCompleted = false
      ),
      TaskEntity(
        id = "a3",
        title = "Pending yesterday",
        scheduledAtMillis = yesterdayNoon,
        maxCompletionMillis = yesterdayNoon,
        isCompleted = false
      ),
      TaskEntity(
        id = "a4",
        title = "Upcoming task",
        scheduledAtMillis = threeDaysFromNow,
        maxCompletionMillis = threeDaysFromNow,
        isCompleted = false
      )
    )

    val summary = ProductivityStatsCalculator.computeDailySummary(
      activeTasks = activeTasks,
      completedTasks = completedTasks,
      dayCount = 7,
      nowMillis = nowMillis
    )

    assertEquals(7, summary.days.size)
    // Today should have 2 completed and 2 pending (today's pending + overdue task rolled into today)
    assertEquals(2, summary.todayCompleted)
    assertEquals(2, summary.todayPending)
    assertEquals(50, summary.todayCompletionPercent)

    // Yesterday should have 1 completed and 1 pending
    val yesterday = summary.days[summary.days.lastIndex - 1]
    assertEquals(1, yesterday.completedCount)
    assertEquals(1, yesterday.pendingCount)

    // Window totals & upcoming count
    assertEquals(3, summary.totalCompletedInWindow)
    assertEquals(3, summary.totalPendingInWindow)
    assertEquals(1, summary.upcomingPendingCount)
  }

  @Test
  fun chartBitmapRendererAndWidgetUpdater_succeedOnApi23() {
    val nowMillis = localDayMillis(daysOffset = 0, hour = 14)
    val summary = ProductivityStatsCalculator.computeDailySummary(
      activeTasks = listOf(
        TaskEntity(
          id = "pending_1",
          title = "Prepare slides",
          scheduledAtMillis = nowMillis,
          maxCompletionMillis = nowMillis,
          isCompleted = false
        )
      ),
      completedTasks = listOf(
        TaskEntity(
          id = "done_1",
          title = "Morning run",
          scheduledAtMillis = nowMillis,
          maxCompletionMillis = nowMillis,
          isCompleted = true,
          completedAtMillis = nowMillis
        )
      ),
      dayCount = 7,
      nowMillis = nowMillis
    )

    val bitmap = ProductivityChartBitmapRenderer.renderChartBitmap(
      summary = summary,
      widthPx = 720,
      heightPx = 300
    )
    assertNotNull(bitmap)
    assertEquals(720, bitmap.width)
    assertEquals(300, bitmap.height)
    assertTrue(!bitmap.isRecycled)

    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    TasksWidgetUpdater.updateAllWidgets(context)
  }
}
