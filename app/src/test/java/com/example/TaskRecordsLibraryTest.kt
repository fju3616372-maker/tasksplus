package com.example

import com.example.data.model.CloudTaskRecord
import com.example.data.model.GroupType
import com.example.data.model.TaskRecordFilter
import com.example.data.model.TaskRecordScope
import com.example.data.model.TaskRecordType
import com.example.data.model.TaskRecordsLibraryHelper
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskRecordsLibraryTest {

  private val sampleRecords = listOf(
    CloudTaskRecord(
      id = "entered_personal_t1",
      userId = "uid_1",
      recordType = TaskRecordType.ENTERED.name,
      sourceScope = TaskRecordScope.PERSONAL.name,
      taskId = "t1",
      taskTitle = "Write weekly plan",
      taskSubtext = "Outline top 3 milestones",
      eventTimeMillis = 1_762_900_000_000L
    ),
    CloudTaskRecord(
      id = "done_group_g1",
      userId = "uid_1",
      recordType = TaskRecordType.DONE.name,
      sourceScope = TaskRecordScope.FAMILY.name,
      taskId = "g1",
      taskTitle = "Clean kitchen",
      groupId = "grp_fam",
      groupName = "Smith Family",
      counterpartyName = "Mom",
      pointsReceived = 120L,
      hasPoints = true,
      pointsReason = "Finished 2m early",
      eventTimeMillis = 1_762_902_000_000L
    ),
    // Duplicate standalone POINTS_RECEIVED for the same group task g1 — should be deduplicated
    CloudTaskRecord(
      id = "points_group_tx1",
      userId = "uid_1",
      recordType = TaskRecordType.POINTS_RECEIVED.name,
      sourceScope = TaskRecordScope.FAMILY.name,
      taskId = "g1",
      taskTitle = "Clean kitchen",
      groupId = "grp_fam",
      groupName = "Smith Family",
      pointsReceived = 120L,
      hasPoints = true,
      pointsReason = "Finished 2m early",
      eventTimeMillis = 1_762_902_005_000L
    ),
    CloudTaskRecord(
      id = "given_group_g2",
      userId = "uid_1",
      recordType = TaskRecordType.GIVEN.name,
      sourceScope = TaskRecordScope.WORKPLACE.name,
      taskId = "g2",
      taskTitle = "Review PR #42",
      groupId = "grp_work",
      groupName = "Design Studio",
      counterpartyName = "Bob",
      eventTimeMillis = 1_762_903_000_000L
    ),
    // Standalone POINTS_RECEIVED for a task whose group task doc was deleted later
    CloudTaskRecord(
      id = "points_group_tx2",
      userId = "uid_1",
      recordType = TaskRecordType.POINTS_RECEIVED.name,
      sourceScope = TaskRecordScope.WORKPLACE.name,
      taskId = "g_old",
      taskTitle = "Deploy staging build",
      groupId = "grp_work",
      groupName = "Design Studio",
      pointsReceived = -30L,
      hasPoints = true,
      pointsReason = "Finished 30s late",
      eventTimeMillis = 1_762_904_000_000L
    )
  )

  @Test
  fun summarizeAndDeduplicate_countsAllFourCategoriesAccurately() {
    val summary = TaskRecordsLibraryHelper.summarize(sampleRecords)
    assertEquals(4, summary.totalCount)
    assertEquals(1, summary.enteredCount)
    assertEquals(1, summary.doneCount)
    assertEquals(1, summary.givenCount)
    assertEquals(2, summary.pointsEventCount)
    assertEquals(90L, summary.netTaskPoints)
  }

  @Test
  fun filterRecords_filtersByTypeAndSearchQueryAndOrdersNewestFirst() {
    val all = TaskRecordsLibraryHelper.filterRecords(sampleRecords, TaskRecordFilter.ALL)
    assertEquals(
      listOf("points_group_tx2", "given_group_g2", "done_group_g1", "entered_personal_t1"),
      all.map { it.id }
    )

    val given = TaskRecordsLibraryHelper.filterRecords(sampleRecords, TaskRecordFilter.GIVEN)
    assertEquals(listOf("given_group_g2"), given.map { it.id })

    val points = TaskRecordsLibraryHelper.filterRecords(sampleRecords, TaskRecordFilter.POINTS)
    assertEquals(listOf("points_group_tx2", "done_group_g1"), points.map { it.id })

    val searchWorkplace = TaskRecordsLibraryHelper.filterRecords(
      sampleRecords,
      TaskRecordFilter.ALL,
      query = "Bob"
    )
    assertEquals(listOf("given_group_g2"), searchWorkplace.map { it.id })
  }

  @Test
  fun scopeMappingAndTimestampFormatting_workForFamilyAndWorkplaceGroups() {
    assertEquals(TaskRecordScope.FAMILY, TaskRecordScope.fromGroupType(GroupType.FAMILY))
    assertEquals(TaskRecordScope.WORKPLACE, TaskRecordScope.fromGroupType(GroupType.ORGANIZATION))

    val formatted = TaskRecordsLibraryHelper.formatRecordTimestamp(
      epochMillis = 1_762_902_000_000L,
      locale = Locale.US
    )
    assertTrue("Expected formatted date & time to include separator", formatted.contains("•"))
    assertEquals("Time recorded", TaskRecordsLibraryHelper.formatRecordTimestamp(0L, Locale.US))
  }
}
