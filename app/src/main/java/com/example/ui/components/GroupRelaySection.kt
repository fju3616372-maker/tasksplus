package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.CloudGroupMember
import com.example.data.model.CloudGroupRelay
import com.example.data.model.CloudRelayStep

@Composable
fun GroupRelaySection(
  relays: List<CloudGroupRelay>,
  members: List<CloudGroupMember>,
  currentUid: String,
  isCurrentUserAdmin: Boolean,
  onCreateRelay: (title: String, description: String, steps: List<CloudRelayStep>) -> Unit,
  onAdvanceRelay: (CloudGroupRelay) -> Unit,
  onDeleteRelay: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  var showCreateDialog by remember { mutableStateOf(false) }

  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.42f)
    )
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.weight(1f)
        ) {
          Box(
            modifier = Modifier
              .size(40.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.tertiary),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.DirectionsRun,
              contentDescription = stringResource(R.string.group_relay_title),
              tint = MaterialTheme.colorScheme.onTertiary,
              modifier = Modifier.size(22.dp)
            )
          }
          Column {
            Text(
              text = "Pass-the-Baton Relays",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "Chain tasks across teammates — finish your leg to pass the baton!",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
        FilledTonalButton(
          onClick = { showCreateDialog = true },
          enabled = members.isNotEmpty(),
          modifier = Modifier
            .height(44.dp)
            .testTag("group_relay_new_button")
        ) {
          Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(4.dp))
          Text("Relay")
        }
      }

      if (relays.isEmpty()) {
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(
            text = "No relay chains yet. Start a Sunday Dinner, House Reset, or Release Sprint relay so everyone runs their leg in order!",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(14.dp)
          )
        }
      } else {
        relays.forEach { relay ->
          RelayChainCard(
            relay = relay,
            currentUid = currentUid,
            isCurrentUserAdmin = isCurrentUserAdmin,
            onAdvance = { onAdvanceRelay(relay) },
            onDelete = { onDeleteRelay(relay.id) }
          )
        }
      }
    }
  }

  if (showCreateDialog) {
    CreateGroupRelayDialog(
      members = members,
      onDismiss = { showCreateDialog = false },
      onCreate = { title, desc, steps ->
        onCreateRelay(title, desc, steps)
        showCreateDialog = false
      }
    )
  }
}

@Composable
private fun RelayChainCard(
  relay: CloudGroupRelay,
  currentUid: String,
  isCurrentUserAdmin: Boolean,
  onAdvance: () -> Unit,
  onDelete: () -> Unit
) {
  val activeStep = relay.activeStep
  val isMyTurn = !relay.isRelayCompleted && activeStep?.assigneeUid == currentUid
  val canAdvance = !relay.isRelayCompleted && (isMyTurn || isCurrentUserAdmin)
  val canDelete = isCurrentUserAdmin || relay.createdByUid == currentUid

  Surface(
    shape = RoundedCornerShape(18.dp),
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = 2.dp,
    modifier = Modifier
      .fillMaxWidth()
      .testTag("group_relay_card_${relay.id}")
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Icon(
              imageVector = if (relay.isRelayCompleted) Icons.Default.EmojiEvents else Icons.Default.Flag,
              contentDescription = null,
              tint = if (relay.isRelayCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
              modifier = Modifier.size(18.dp)
            )
            Text(
              text = relay.title,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold
            )
          }
          if (relay.description.isNotBlank()) {
            Text(
              text = relay.description,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
        if (canDelete) {
          IconButton(
            onClick = onDelete,
            modifier = Modifier
              .size(40.dp)
              .testTag("delete_relay_${relay.id}")
          ) {
            Icon(
              imageVector = Icons.Default.DeleteOutline,
              contentDescription = "Delete relay chain",
              tint = MaterialTheme.colorScheme.error
            )
          }
        }
      }

      LinearProgressIndicator(
        progress = { relay.progressFraction },
        modifier = Modifier
          .fillMaxWidth()
          .height(8.dp)
          .clip(RoundedCornerShape(4.dp))
      )

      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        relay.steps.forEachIndexed { idx, step ->
          val isActive = !relay.isRelayCompleted && idx == relay.currentStepIndex.toInt()
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .background(
                when {
                  isActive -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                  step.done -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                  else -> MaterialTheme.colorScheme.surface
                }
              )
              .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              imageVector = when {
                step.done -> Icons.Default.CheckCircle
                isActive -> Icons.Default.DirectionsRun
                else -> Icons.Default.RadioButtonUnchecked
              },
              contentDescription = null,
              tint = when {
                step.done -> MaterialTheme.colorScheme.primary
                isActive -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.outline
              },
              modifier = Modifier.size(18.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Leg ${idx + 1}: ${step.stepTitle}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
              )
              val handoffLabel = if (step.done && step.handoffSpeedSeconds > 0L) {
                " • Done in ${formatRelaySpeed(step.handoffSpeedSeconds)}"
              } else if (isActive) {
                " • Holding the Baton!"
              } else {
                " • Waiting"
              }
              Text(
                text = "${step.assigneeName}$handoffLabel",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }
      }

      if (canAdvance && activeStep != null) {
        val nextRunner = relay.steps.getOrNull(relay.currentStepIndex.toInt() + 1)?.assigneeName
        Button(
          onClick = onAdvance,
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("advance_relay_${relay.id}")
        ) {
          Icon(Icons.Default.DirectionsRun, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text(
            text = if (nextRunner != null) {
              if (isMyTurn) "Complete My Leg & Pass Baton to $nextRunner" else "Advance Baton to $nextRunner"
            } else {
              "Finish Final Leg & Complete Relay!"
            }
          )
        }
      } else if (relay.isRelayCompleted) {
        Text(
          text = "Relay Complete! ${relay.steps.size}/${relay.steps.size} baton legs finished.",
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.primary
        )
      }
    }
  }
}

private data class DraftRelayLeg(
  val title: String,
  val memberUid: String,
  val memberName: String
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateGroupRelayDialog(
  members: List<CloudGroupMember>,
  onDismiss: () -> Unit,
  onCreate: (title: String, description: String, steps: List<CloudRelayStep>) -> Unit
) {
  var relayTitle by remember { mutableStateOf("") }
  var relayDesc by remember { mutableStateOf("") }
  val firstMember = members.firstOrNull()
  val secondMember = members.getOrNull(1) ?: firstMember
  val draftLegs = remember {
    mutableStateListOf(
      DraftRelayLeg(
        title = "",
        memberUid = firstMember?.uid.orEmpty(),
        memberName = firstMember?.displayName?.ifBlank { firstMember.email.substringBefore("@") } ?: "Member 1"
      ),
      DraftRelayLeg(
        title = "",
        memberUid = secondMember?.uid.orEmpty(),
        memberName = secondMember?.displayName?.ifBlank { secondMember.email.substringBefore("@") } ?: "Member 2"
      )
    )
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Launch Pass-the-Baton Relay") },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        OutlinedTextField(
          value = relayTitle,
          onValueChange = { relayTitle = it },
          label = { Text("Relay Title") },
          placeholder = { Text("e.g. Sunday Dinner Prep") },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("relay_dialog_title_input")
        )
        OutlinedTextField(
          value = relayDesc,
          onValueChange = { relayDesc = it },
          label = { Text("Goal (optional)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
        Text(
          text = "Sequential Baton Legs (2 to 6 steps):",
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.SemiBold
        )
        draftLegs.forEachIndexed { idx, leg ->
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(
              modifier = Modifier.padding(10.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = "Leg ${idx + 1}",
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.primary
                )
                if (draftLegs.size > 2) {
                  TextButton(onClick = { draftLegs.removeAt(idx) }) {
                    Text("Remove")
                  }
                }
              }
              OutlinedTextField(
                value = leg.title,
                onValueChange = { newVal -> draftLegs[idx] = leg.copy(title = newVal) },
                label = { Text("What needs to be done?") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
              )
              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                members.forEach { member ->
                  val name = member.displayName.ifBlank { member.email.substringBefore("@") }
                  FilterChip(
                    selected = leg.memberUid == member.uid,
                    onClick = {
                      draftLegs[idx] = leg.copy(memberUid = member.uid, memberName = name)
                    },
                    label = { Text(name) }
                  )
                }
              }
            }
          }
        }
        if (draftLegs.size < 6) {
          OutlinedButton(
            onClick = {
              val nextMem = members[draftLegs.size % members.size]
              val name = nextMem.displayName.ifBlank { nextMem.email.substringBefore("@") }
              draftLegs.add(DraftRelayLeg("", nextMem.uid, name))
            },
            modifier = Modifier.fillMaxWidth()
          ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Add Another Relay Leg")
          }
        }
      }
    },
    confirmButton = {
      val valid = relayTitle.isNotBlank() &&
        draftLegs.size >= 2 &&
        draftLegs.all { it.title.isNotBlank() && it.memberUid.isNotBlank() }
      Button(
        onClick = {
          val steps = draftLegs.map { leg ->
            CloudRelayStep(
              stepTitle = leg.title.trim(),
              assigneeUid = leg.memberUid,
              assigneeName = leg.memberName,
              durationMinutes = 15L
            )
          }
          onCreate(relayTitle.trim(), relayDesc.trim(), steps)
        },
        enabled = valid,
        modifier = Modifier.testTag("relay_dialog_confirm_button")
      ) {
        Text("Launch Relay")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    }
  )
}

private fun formatRelaySpeed(seconds: Long): String {
  val mins = seconds / 60L
  val remSecs = seconds % 60L
  return if (mins > 0L) "${mins}m ${remSecs}s" else "${remSecs}s"
}
