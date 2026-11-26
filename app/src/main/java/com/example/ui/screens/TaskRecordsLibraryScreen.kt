package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddTask
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.CloudTaskRecord
import com.example.data.model.CreditCalculator
import com.example.data.model.TaskRecordFilter
import com.example.data.model.TaskRecordScope
import com.example.data.model.TaskRecordType
import com.example.data.model.TaskRecordsLibraryHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskRecordsLibraryScreen(
  isSignedIn: Boolean,
  userDisplayName: String,
  records: List<CloudTaskRecord>,
  onNavigateBack: () -> Unit,
  onDeleteRecord: (String) -> Unit,
  onClearAllRecords: () -> Unit
) {
  BackHandler { onNavigateBack() }

  var selectedFilterName by rememberSaveable { mutableStateOf(TaskRecordFilter.ALL.name) }
  var searchQuery by rememberSaveable { mutableStateOf("") }
  var showClearConfirmDialog by rememberSaveable { mutableStateOf(false) }

  val selectedFilter = remember(selectedFilterName) {
    TaskRecordFilter.entries.firstOrNull { it.name == selectedFilterName } ?: TaskRecordFilter.ALL
  }

  val summary = remember(records) { TaskRecordsLibraryHelper.summarize(records) }
  val filteredRecords = remember(records, selectedFilter, searchQuery) {
    TaskRecordsLibraryHelper.filterRecords(
      records = records,
      filter = selectedFilter,
      query = searchQuery
    )
  }

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              text = stringResource(R.string.records_library_title),
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            if (isSignedIn) {
              Text(
                text = "Private to ${userDisplayName.ifBlank { "you" }}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          }
        },
        navigationIcon = {
          IconButton(
            onClick = onNavigateBack,
            modifier = Modifier.testTag("records_library_back_button")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(R.string.cd_navigate_back)
            )
          }
        },
        actions = {
          if (isSignedIn && records.isNotEmpty()) {
            IconButton(
              onClick = { showClearConfirmDialog = true },
              modifier = Modifier.testTag("records_library_clear_all_button")
            ) {
              Icon(
                imageVector = Icons.Default.DeleteSweep,
                contentDescription = stringResource(R.string.cd_clear_all_task_records),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.background
        )
      )
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
      contentAlignment = Alignment.TopCenter
    ) {
      if (!isSignedIn) {
        // Strict signed-in-only lock state (never renders records when signed out)
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp)
            .padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          Spacer(modifier = Modifier.height(32.dp))
          Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = CircleShape,
            modifier = Modifier.size(72.dp)
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(34.dp)
              )
            }
          }
          Text(
            text = "Sign In Required",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = "Your Task Records Library is private and only available while signed in with your account.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          FilledTonalButton(
            onClick = onNavigateBack,
            modifier = Modifier.testTag("records_library_signed_out_back_button")
          ) {
            Text("Go Back")
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 640.dp)
            .navigationBarsPadding(),
          contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          item(key = "privacy_banner") {
            Card(
              shape = RoundedCornerShape(20.dp),
              colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
              )
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
              ) {
                Surface(
                  color = MaterialTheme.colorScheme.primaryContainer,
                  shape = CircleShape,
                  modifier = Modifier.size(40.dp)
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Icon(
                      imageVector = Icons.Default.Lock,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.onPrimaryContainer,
                      modifier = Modifier.size(20.dp)
                    )
                  }
                }
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "Only Accessible to You",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                  Text(
                    text = "Private timestamped log of tasks you entered, finished, gave in Family & Workplace groups, and points earned.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            }
          }

          item(key = "summary_metrics") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                RecordStatTile(
                  label = "Entered",
                  value = summary.enteredCount.toString(),
                  icon = Icons.Default.AddTask,
                  containerColor = MaterialTheme.colorScheme.primaryContainer,
                  contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                  modifier = Modifier.weight(1f)
                )
                RecordStatTile(
                  label = "Done",
                  value = summary.doneCount.toString(),
                  icon = Icons.Default.TaskAlt,
                  containerColor = MaterialTheme.colorScheme.secondaryContainer,
                  contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                  modifier = Modifier.weight(1f)
                )
              }
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                RecordStatTile(
                  label = "Given (Groups)",
                  value = summary.givenCount.toString(),
                  icon = Icons.AutoMirrored.Filled.Send,
                  containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                  contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                  modifier = Modifier.weight(1f)
                )
                RecordStatTile(
                  label = "Task Points",
                  value = if (summary.pointsEventCount == 0) {
                    "0 pts"
                  } else {
                    CreditCalculator.formatSignedPoints(summary.netTaskPoints)
                  },
                  icon = Icons.Default.Stars,
                  containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                  contentColor = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.weight(1f)
                )
              }
            }
          }

          item(key = "search_bar") {
            OutlinedTextField(
              value = searchQuery,
              onValueChange = { searchQuery = it },
              placeholder = { Text("Search tasks, groups, or recipients…") },
              singleLine = true,
              leadingIcon = {
                Icon(
                  imageVector = Icons.Default.Search,
                  contentDescription = null
                )
              },
              trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                  IconButton(onClick = { searchQuery = "" }) {
                    Icon(
                      imageVector = Icons.Default.Close,
                      contentDescription = "Clear search"
                    )
                  }
                }
              },
              shape = RoundedCornerShape(16.dp),
              colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
              ),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("records_library_search_input")
            )
          }

          item(key = "filter_chips") {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              TaskRecordFilter.entries.forEach { filter ->
                val count = when (filter) {
                  TaskRecordFilter.ALL -> summary.totalCount
                  TaskRecordFilter.ENTERED -> summary.enteredCount
                  TaskRecordFilter.DONE -> summary.doneCount
                  TaskRecordFilter.GIVEN -> summary.givenCount
                  TaskRecordFilter.POINTS -> summary.pointsEventCount
                }
                FilterChip(
                  selected = selectedFilter == filter,
                  onClick = { selectedFilterName = filter.name },
                  label = {
                    Text("${filter.label} ($count)")
                  },
                  modifier = Modifier.testTag(filter.tag)
                )
              }
            }
          }

          if (filteredRecords.isEmpty()) {
            item(key = "empty_state") {
              Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                  containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(top = 8.dp)
              ) {
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Icon(
                    imageVector = Icons.Default.LibraryBooks,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                  )
                  Text(
                    text = if (searchQuery.isNotBlank()) {
                      "No matching records"
                    } else {
                      when (selectedFilter) {
                        TaskRecordFilter.ALL -> "Your Task Records Library is ready"
                        TaskRecordFilter.ENTERED -> "No entered tasks recorded yet"
                        TaskRecordFilter.DONE -> "No completed tasks recorded yet"
                        TaskRecordFilter.GIVEN -> "No tasks given in groups yet"
                        TaskRecordFilter.POINTS -> "No task points earned yet"
                      }
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                  )
                  Text(
                    text = "Tasks you enter, finish, or assign in Family & Workplace groups—along with completion points and timestamps—will automatically appear here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            }
          } else {
            items(
              items = filteredRecords,
              key = { it.id.ifBlank { "${it.recordType}_${it.taskId}_${it.eventTimeMillis}" } }
            ) { record ->
              TaskRecordCard(
                record = record,
                onDelete = { onDeleteRecord(record.id) }
              )
            }
          }

          item(key = "bottom_spacer") {
            Spacer(modifier = Modifier.height(24.dp))
          }
        }
      }
    }
  }

  if (showClearConfirmDialog) {
    AlertDialog(
      onDismissRequest = { showClearConfirmDialog = false },
      title = { Text("Clear Task Records Library?") },
      text = {
        Text("This permanently removes all saved records from your private library. Your active tasks and group memberships will not be affected.")
      },
      confirmButton = {
        TextButton(
          onClick = {
            showClearConfirmDialog = false
            onClearAllRecords()
          },
          modifier = Modifier.testTag("confirm_clear_records_button")
        ) {
          Text("Clear All", color = MaterialTheme.colorScheme.error)
        }
      },
      dismissButton = {
        TextButton(onClick = { showClearConfirmDialog = false }) {
          Text("Cancel")
        }
      }
    )
  }
}

@Composable
private fun RecordStatTile(
  label: String,
  value: String,
  icon: ImageVector,
  containerColor: Color,
  contentColor: Color,
  modifier: Modifier = Modifier
) {
  Surface(
    color = containerColor,
    shape = RoundedCornerShape(18.dp),
    modifier = modifier
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = contentColor,
        modifier = Modifier.size(20.dp)
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = value,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = contentColor,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = label,
          style = MaterialTheme.typography.labelSmall,
          color = contentColor.copy(alpha = 0.85f),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}

@Composable
private fun TaskRecordCard(
  record: CloudTaskRecord,
  onDelete: () -> Unit
) {
  val type = record.parsedType
  val scope = record.parsedScope

  val (typeIcon, badgeBg, badgeFg) = when (type) {
    TaskRecordType.ENTERED -> Triple(
      Icons.Default.AddTask,
      MaterialTheme.colorScheme.primaryContainer,
      MaterialTheme.colorScheme.onPrimaryContainer
    )
    TaskRecordType.DONE -> Triple(
      Icons.Default.TaskAlt,
      MaterialTheme.colorScheme.secondaryContainer,
      MaterialTheme.colorScheme.onSecondaryContainer
    )
    TaskRecordType.GIVEN -> Triple(
      Icons.AutoMirrored.Filled.Send,
      MaterialTheme.colorScheme.tertiaryContainer,
      MaterialTheme.colorScheme.onTertiaryContainer
    )
    TaskRecordType.POINTS_RECEIVED -> Triple(
      Icons.Default.Stars,
      MaterialTheme.colorScheme.primaryContainer,
      MaterialTheme.colorScheme.onPrimaryContainer
    )
  }

  val scopeIcon = when (scope) {
    TaskRecordScope.PERSONAL -> Icons.Default.Person
    TaskRecordScope.FAMILY -> Icons.Default.FamilyRestroom
    TaskRecordScope.WORKPLACE -> Icons.Default.Business
  }

  val timestampText = remember(record.eventTimeMillis, record.createdAt, record.updatedAt) {
    TaskRecordsLibraryHelper.formatRecordTimestamp(record.resolvedEventTimeMillis())
  }
  val dueTimeText = remember(record.dueAtMillis) {
    if (record.dueAtMillis > 0L) {
      TaskRecordsLibraryHelper.formatRecordTimestamp(record.dueAtMillis)
    } else {
      ""
    }
  }

  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainer
    ),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("task_record_card_${record.id}")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Box(
          modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(badgeBg),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = typeIcon,
            contentDescription = type.label,
            tint = badgeFg,
            modifier = Modifier.size(20.dp)
          )
        }

        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Surface(
              color = badgeBg,
              shape = RoundedCornerShape(8.dp)
            ) {
              Text(
                text = type.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = badgeFg,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
              )
            }
            Surface(
              color = MaterialTheme.colorScheme.surfaceContainerHighest,
              shape = RoundedCornerShape(8.dp)
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                Icon(
                  imageVector = scopeIcon,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(12.dp)
                )
                Text(
                  text = record.formattedScopeLabel(),
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
            }
          }

          Text(
            text = record.taskTitle.ifBlank { "Untitled task" },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
          )

          if (record.taskSubtext.isNotBlank()) {
            Text(
              text = record.taskSubtext,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis
            )
          }

          if (record.counterpartyName.isNotBlank()) {
            val relationLabel = when (type) {
              TaskRecordType.GIVEN -> "Given to: ${record.counterpartyName}"
              TaskRecordType.DONE -> "Assigned by: ${record.counterpartyName}"
              TaskRecordType.POINTS_RECEIVED -> "Recorded with: ${record.counterpartyName}"
              TaskRecordType.ENTERED -> record.counterpartyName
            }
            Text(
              text = relationLabel,
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary
            )
          }
        }

        if (record.id.isNotBlank()) {
          IconButton(
            onClick = onDelete,
            modifier = Modifier.testTag("delete_record_${record.id}")
          ) {
            Icon(
              imageVector = Icons.Default.DeleteOutline,
              contentDescription = stringResource(R.string.cd_delete_task_record),
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(20.dp)
            )
          }
        }
      }

      if (record.hasPoints || type == TaskRecordType.POINTS_RECEIVED) {
        val isPositive = record.pointsReceived >= 0L
        val pillBg = if (isPositive) {
          MaterialTheme.colorScheme.primaryContainer
        } else {
          MaterialTheme.colorScheme.errorContainer
        }
        val pillFg = if (isPositive) {
          MaterialTheme.colorScheme.onPrimaryContainer
        } else {
          MaterialTheme.colorScheme.onErrorContainer
        }
        Surface(
          color = pillBg,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Stars,
              contentDescription = null,
              tint = pillFg,
              modifier = Modifier.size(16.dp)
            )
            Text(
              text = CreditCalculator.formatSignedPoints(record.pointsReceived),
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold,
              color = pillFg
            )
            if (record.pointsReason.isNotBlank()) {
              Text(
                text = "• ${record.pointsReason}",
                style = MaterialTheme.typography.labelSmall,
                color = pillFg.copy(alpha = 0.9f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
              )
            }
          }
        }
      }

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(5.dp),
          modifier = Modifier.weight(1f)
        ) {
          Icon(
            imageVector = Icons.Default.Schedule,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
          )
          Text(
            text = "${type.timePrefix}: $timestampText",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        if (dueTimeText.isNotBlank() && type != TaskRecordType.POINTS_RECEIVED) {
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Due: $dueTimeText",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }
    }
  }
}
