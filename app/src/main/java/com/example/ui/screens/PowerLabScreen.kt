package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddTask
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ai.FiveMinuteSparkPlan
import com.example.ai.TransformationProofVerdict
import com.example.analytics.ProcrastinationRadarReport
import com.example.audio.GoogleTtsState
import com.example.data.local.TransformationProofRecord
import com.example.data.local.TransformationProofStore
import com.example.data.model.TaskEntity
import com.example.data.model.UserPreferencesState
import com.example.sensors.FocusAuraSensorState
import com.example.ui.components.EarnYourScrollCard
import com.example.ui.components.TransformationProofSheet
import com.example.ui.components.VoiceContractRecorderCard
import com.example.viewmodel.SparkPlanUiState
import com.example.viewmodel.VoiceAssistantUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class PowerLabTab(val label: String) {
  FOCUS_AURA("Aura"),
  RADAR_SPARK("AI Radar"),
  BEFORE_AFTER("Proof"),
  VOICE("Voice")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PowerLabScreen(
  preferences: UserPreferencesState,
  activeTasks: List<TaskEntity>,
  focusAuraState: FocusAuraSensorState,
  radarReport: ProcrastinationRadarReport,
  sparkPlanState: SparkPlanUiState,
  transformationProofs: List<TransformationProofRecord>,
  isEvaluatingTransformation: Boolean,
  lastTransformationVerdict: TransformationProofVerdict?,
  initialTab: PowerLabTab = PowerLabTab.FOCUS_AURA,
  initialSparkTask: TaskEntity? = null,
  initialProofTask: TaskEntity? = null,
  onToggleFocusAura: () -> Unit,
  onToggleEarnYourScroll: (Boolean) -> Unit,
  onChangeEarnBaseMinutes: (Int) -> Unit,
  onGenerateFiveMinuteSpark: (title: String, subtext: String, urgency: Int, categoryId: String) -> Unit,
  onAddSparkTask: (plan: FiveMinuteSparkPlan, urgency: Int, categoryId: String) -> Unit,
  onClearSparkPlan: () -> Unit,
  onEvaluateTransformation: (
    taskId: String,
    taskTitle: String,
    taskSubtext: String,
    criteria: String,
    beforeBitmap: Bitmap,
    afterBitmap: Bitmap,
    note: String,
    completeTaskIfApproved: Boolean
  ) -> Unit,
  onClearTransformationVerdict: () -> Unit,
  onDeleteTransformationProof: (String) -> Unit,
  onAttachVoiceContract: (taskId: String, path: String) -> Unit,
  voiceAssistantState: VoiceAssistantUiState = VoiceAssistantUiState(),
  ttsState: GoogleTtsState = GoogleTtsState(),
  onOpenVoiceCommands: () -> Unit = {},
  onTestGoogleTts: () -> Unit = {},
  onNavigateBack: () -> Unit
) {
  BackHandler(onBack = onNavigateBack)

  val context = LocalContext.current
  val proofStore = remember(context) { TransformationProofStore(context) }
  var selectedTabIndex by remember(initialTab) { mutableIntStateOf(initialTab.ordinal) }
  var showProofSheetForTask by remember(initialProofTask) {
    mutableStateOf<TaskEntity?>(initialProofTask)
  }
  var showCustomProofSheet by remember { mutableStateOf(false) }

  var customSparkInput by remember(initialSparkTask) {
    mutableStateOf(initialSparkTask?.title.orEmpty())
  }
  var selectedSparkTaskId by remember(initialSparkTask) {
    mutableStateOf(initialSparkTask?.id.orEmpty())
  }
  var selectedVoiceTaskId by remember(activeTasks) {
    mutableStateOf(activeTasks.firstOrNull()?.id.orEmpty())
  }

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              text = stringResource(R.string.power_lab_title),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "Real Hardware Sensors • Gemini AI • Voice & Wallet",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        },
        navigationIcon = {
          IconButton(
            onClick = onNavigateBack,
            modifier = Modifier
              .size(48.dp)
              .testTag("power_lab_back_button")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(R.string.cd_navigate_back)
            )
          }
        }
      )
    }
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
    ) {
      SingleChoiceSegmentedButtonRow(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 8.dp)
      ) {
        PowerLabTab.entries.forEachIndexed { index, tab ->
          SegmentedButton(
            selected = selectedTabIndex == index,
            onClick = { selectedTabIndex = index },
            shape = SegmentedButtonDefaults.itemShape(index, PowerLabTab.entries.size),
            modifier = Modifier.testTag("power_lab_tab_${tab.name.lowercase()}")
          ) {
            Text(
              text = tab.label,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              style = MaterialTheme.typography.labelMedium
            )
          }
        }
      }

      when (PowerLabTab.entries[selectedTabIndex]) {
        PowerLabTab.FOCUS_AURA -> {
          LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
          ) {
            item {
              FocusAuraSensorCard(
                sensorState = focusAuraState,
                streakCount = preferences.focusAuraStreakCount,
                onToggleArm = onToggleFocusAura
              )
            }
            item {
              EarnYourScrollCard(
                preferences = preferences,
                onToggleEnabled = onToggleEarnYourScroll,
                onChangeBaseMinutes = onChangeEarnBaseMinutes
              )
            }
          }
        }

        PowerLabTab.RADAR_SPARK -> {
          LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
          ) {
            item {
              ProcrastinationRadarCard(report = radarReport)
            }
            item {
              Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                  containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                ),
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(
                  modifier = Modifier.padding(18.dp),
                  verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                  ) {
                    Box(
                      modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                      contentAlignment = Alignment.Center
                    ) {
                      Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary
                      )
                    }
                    Column {
                      Text(
                        text = "Gemini 5-Minute Spark Splitter",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                      )
                      Text(
                        text = "Turn any overwhelming task into a 5-minute friction-free first step.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }
                  }

                  if (activeTasks.isNotEmpty()) {
                    Text(
                      text = "Tap an active task or type your own:",
                      style = MaterialTheme.typography.labelMedium,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                      verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                      activeTasks.take(8).forEach { task ->
                        FilterChip(
                          selected = selectedSparkTaskId == task.id,
                          onClick = {
                            selectedSparkTaskId = task.id
                            customSparkInput = task.title
                          },
                          label = {
                            Text(task.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                          }
                        )
                      }
                    }
                  }

                  OutlinedTextField(
                    value = customSparkInput,
                    onValueChange = {
                      customSparkInput = it
                      selectedSparkTaskId = ""
                    },
                    label = { Text("Task you're putting off") },
                    placeholder = { Text("e.g. Clean entire garage or write quarterly report") },
                    singleLine = true,
                    modifier = Modifier
                      .fillMaxWidth()
                      .testTag("spark_task_input")
                  )

                  Button(
                    onClick = {
                      val matched = activeTasks.firstOrNull { it.id == selectedSparkTaskId }
                      onGenerateFiveMinuteSpark(
                        customSparkInput.trim(),
                        matched?.subtext.orEmpty(),
                        matched?.resolvedUrgencyLevel ?: 3,
                        matched?.categoryId.orEmpty()
                      )
                    },
                    enabled = customSparkInput.isNotBlank() && sparkPlanState !is SparkPlanUiState.Loading,
                    modifier = Modifier
                      .fillMaxWidth()
                      .height(50.dp)
                      .testTag("spark_generate_button")
                  ) {
                    if (sparkPlanState is SparkPlanUiState.Loading) {
                      CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                      )
                      Spacer(Modifier.width(10.dp))
                      Text("Gemini is crafting your 5-Min Spark…")
                    } else {
                      Icon(Icons.Default.AutoAwesome, contentDescription = null)
                      Spacer(Modifier.width(8.dp))
                      Text("Generate 5-Minute Spark with Gemini AI")
                    }
                  }

                  when (sparkPlanState) {
                    is SparkPlanUiState.Error -> {
                      Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                      ) {
                        Text(
                          text = sparkPlanState.message,
                          style = MaterialTheme.typography.bodySmall,
                          color = MaterialTheme.colorScheme.onErrorContainer,
                          modifier = Modifier.padding(12.dp)
                        )
                      }
                    }
                    is SparkPlanUiState.Success -> {
                      Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                      ) {
                        Column(
                          modifier = Modifier.padding(16.dp),
                          verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                          Text(
                            text = "Your 5-Minute Spark",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                          )
                          Text(
                            text = sparkPlanState.plan.fiveMinuteSparkTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                          )
                          Text(
                            text = sparkPlanState.plan.fiveMinuteSparkNote,
                            style = MaterialTheme.typography.bodyMedium
                          )
                          if (sparkPlanState.plan.followUpSteps.isNotEmpty()) {
                            Text(
                              text = "Next Milestones:",
                              style = MaterialTheme.typography.labelMedium,
                              fontWeight = FontWeight.SemiBold
                            )
                            sparkPlanState.plan.followUpSteps.forEachIndexed { idx, step ->
                              Text(
                                text = "${idx + 1}. $step",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                              )
                            }
                          }
                          Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                          ) {
                            Text(
                              text = "Coach Tip: ${sparkPlanState.plan.coachTip}",
                              style = MaterialTheme.typography.bodySmall,
                              fontWeight = FontWeight.Medium,
                              modifier = Modifier.padding(10.dp)
                            )
                          }
                          Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                          ) {
                            Button(
                              onClick = {
                                onAddSparkTask(
                                  sparkPlanState.plan,
                                  sparkPlanState.sourceUrgencyLevel,
                                  sparkPlanState.sourceCategoryId
                                )
                              },
                              modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("spark_add_task_button")
                            ) {
                              Icon(Icons.Default.AddTask, contentDescription = null, modifier = Modifier.size(18.dp))
                              Spacer(Modifier.width(6.dp))
                              Text("Add 5-Min Task")
                            }
                            FilledTonalButton(
                              onClick = onClearSparkPlan,
                              modifier = Modifier.height(48.dp)
                            ) {
                              Text("Clear")
                            }
                          }
                        }
                      }
                    }
                    else -> Unit
                  }
                }
              }
            }
          }
        }

        PowerLabTab.BEFORE_AFTER -> {
          LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
          ) {
            item {
              Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                  containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(
                  modifier = Modifier.padding(18.dp),
                  verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                  ) {
                    Icon(
                      imageVector = Icons.Default.Compare,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(28.dp)
                    )
                    Column {
                      Text(
                        text = "Before & After Transformation Proof",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                      )
                      Text(
                        text = "Snap Before & After photos and let Gemini AI score your real-world progress.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }
                  }
                  if (activeTasks.isNotEmpty()) {
                    FlowRow(
                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                      verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                      activeTasks.take(6).forEach { task ->
                        FilledTonalButton(
                          onClick = {
                            onClearTransformationVerdict()
                            showProofSheetForTask = task
                          },
                          modifier = Modifier.height(44.dp)
                        ) {
                          Text(task.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                      }
                    }
                  }
                  Button(
                    onClick = {
                      onClearTransformationVerdict()
                      showCustomProofSheet = true
                    },
                    modifier = Modifier
                      .fillMaxWidth()
                      .height(48.dp)
                      .testTag("power_lab_new_transformation_button")
                  ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Compare New Before & After Photos")
                  }
                }
              }
            }

            if (transformationProofs.isEmpty()) {
              item {
                Surface(
                  shape = RoundedCornerShape(18.dp),
                  color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Text(
                    text = "Your private Before & After Proof Vault is empty. Verify your first desk cleanup, workout, or project milestone to build a visual progress gallery!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(18.dp)
                  )
                }
              }
            } else {
              item {
                Text(
                  text = "Private Proof Vault (${transformationProofs.size})",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.padding(top = 4.dp)
                )
              }
              items(transformationProofs, key = { it.id }) { proof ->
                TransformationProofHistoryCard(
                  record = proof,
                  proofStore = proofStore,
                  onDelete = { onDeleteTransformationProof(proof.id) }
                )
              }
            }
          }
        }

        PowerLabTab.VOICE -> {
          LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
          ) {
            item {
              val isEnrolled = voiceAssistantState.enrolledProfile != null
              Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                  containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                ),
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(
                  modifier = Modifier.padding(18.dp),
                  verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                  ) {
                    Box(
                      modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                      contentAlignment = Alignment.Center
                    ) {
                      Icon(
                        imageVector = if (isEnrolled) Icons.Default.VerifiedUser else Icons.Default.Mic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary
                      )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = stringResource(R.string.voice_command_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                      )
                      Text(
                        text = if (isEnrolled) {
                          "Voice Lock active for ${voiceAssistantState.enrolledProfile?.ownerName} • ${ttsState.engineLabel}"
                        } else {
                          "Enroll your Voice ID so only YOUR voice can add, complete, or remove tasks."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }
                  }
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                  ) {
                    Button(
                      onClick = onOpenVoiceCommands,
                      modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("power_lab_open_voice_commands")
                    ) {
                      Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                      Spacer(Modifier.width(6.dp))
                      Text(if (isEnrolled) "Speak Command" else "Enroll Voice ID")
                    }
                    FilledTonalButton(
                      onClick = onTestGoogleTts,
                      modifier = Modifier
                        .height(48.dp)
                        .testTag("power_lab_test_google_tts")
                    ) {
                      Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                      Spacer(Modifier.width(6.dp))
                      Text("Test TTS")
                    }
                  }
                }
              }
            }

            item {
              Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                  containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
                ),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier.padding(16.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                  Icon(
                    imageVector = Icons.Default.RecordVoiceOver,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(28.dp)
                  )
                  Column {
                    Text(
                      text = "Future-Self Voice Contracts",
                      style = MaterialTheme.typography.titleMedium,
                      fontWeight = FontWeight.Bold
                    )
                    Text(
                      text = "When a reminder fires, Tasks+ plays YOUR own recorded voice explaining why you set this commitment.",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }
              }
            }

            if (activeTasks.isEmpty()) {
              item {
                Surface(
                  shape = RoundedCornerShape(18.dp),
                  color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Text(
                    text = "Add an active task first, or record a Voice Contract right inside the Task Editor when creating a task.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(18.dp)
                  )
                }
              }
            } else {
              item {
                Text(
                  text = "Choose an active task to attach a voice note:",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  activeTasks.forEach { task ->
                    FilterChip(
                      selected = selectedVoiceTaskId == task.id,
                      onClick = { selectedVoiceTaskId = task.id },
                      label = {
                        Text(
                          text = if (task.voiceContractPath.isNotBlank()) "🎙 ${task.title}" else task.title,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis
                        )
                      }
                    )
                  }
                }
              }

              val chosenTask = activeTasks.firstOrNull { it.id == selectedVoiceTaskId } ?: activeTasks.first()
              item(key = chosenTask.id) {
                VoiceContractRecorderCard(
                  voiceContractPath = chosenTask.voiceContractPath,
                  onVoiceContractChanged = { path ->
                    onAttachVoiceContract(chosenTask.id, path)
                  }
                )
              }
            }
          }
        }
      }
    }
  }

  val activeProofTask = showProofSheetForTask
  if (activeProofTask != null || showCustomProofSheet) {
    TransformationProofSheet(
      initialTaskId = activeProofTask?.id.orEmpty(),
      initialTaskTitle = activeProofTask?.title.orEmpty(),
      initialTaskSubtext = activeProofTask?.subtext.orEmpty(),
      isEvaluating = isEvaluatingTransformation,
      verdict = lastTransformationVerdict,
      onEvaluate = onEvaluateTransformation,
      onDismiss = {
        showProofSheetForTask = null
        showCustomProofSheet = false
      }
    )
  }
}

@Composable
private fun FocusAuraSensorCard(
  sensorState: FocusAuraSensorState,
  streakCount: Int,
  onToggleArm: () -> Unit
) {
  val activeColor = if (sensorState.isFocusSessionActive) {
    MaterialTheme.colorScheme.primary
  } else if (sensorState.isArmed) {
    MaterialTheme.colorScheme.tertiary
  } else {
    MaterialTheme.colorScheme.surfaceVariant
  }

  Card(
    shape = RoundedCornerShape(26.dp),
    colors = CardDefaults.cardColors(
      containerColor = if (sensorState.isFocusSessionActive) {
        MaterialTheme.colorScheme.primaryContainer
      } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
      }
    ),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier.padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Box(
          modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(activeColor),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = if (sensorState.isFocusSessionActive) Icons.Default.Shield else Icons.Default.ScreenLockPortrait,
            contentDescription = stringResource(R.string.focus_aura_title),
            tint = if (sensorState.isArmed) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
          )
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(R.string.focus_aura_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = sensorState.statusMessage,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      // Live Hardware Telemetry Row
      Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier.padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Sensors,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(18.dp)
            )
            Text(
              text = "Live Android Hardware Sensors",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold
            )
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            TelemetryStat(
              label = "Accelerometer Z",
              value = String.format(Locale.US, "%.1f m/s²", sensorState.accelZ)
            )
            TelemetryStat(
              label = "Orientation",
              value = if (sensorState.isFaceDown) "Face-Down" else "Upright"
            )
            TelemetryStat(
              label = "Proximity",
              value = when {
                !sensorState.hasProximitySensor -> "Accel Only"
                sensorState.isProximityCovered -> "Covered"
                else -> "Open"
              }
            )
          }
        }
      }

      // Session Metrics Strip
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.surface,
          modifier = Modifier.weight(1f)
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Text("Session Focus", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val mins = sensorState.focusElapsedSeconds / 60
            val secs = sensorState.focusElapsedSeconds % 60
            Text(
              text = String.format(Locale.US, "%02d:%02d", mins, secs),
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.ExtraBold,
              color = MaterialTheme.colorScheme.primary
            )
          }
        }
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.surface,
          modifier = Modifier.weight(1f)
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Text("Pickups", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
              text = "${sensorState.pickupInterruptions}",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold
            )
          }
        }
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.surface,
          modifier = Modifier.weight(1f)
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Text("Aura Streak", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
              Icon(
                Icons.Default.LocalFireDepartment,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(18.dp)
              )
              Text(
                text = "$streakCount",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
              )
            }
          }
        }
      }

      Button(
        onClick = onToggleArm,
        enabled = sensorState.hasAccelerometer,
        colors = if (sensorState.isArmed) {
          ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        } else {
          ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        },
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("focus_aura_arm_button")
      ) {
        Icon(
          imageVector = Icons.Default.Sensors,
          contentDescription = null,
          modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
          text = if (sensorState.isArmed) "Disarm Focus Aura" else "Arm Phone-Flip Focus Aura"
        )
      }
    }
  }
}

@Composable
private fun TelemetryStat(label: String, value: String) {
  Column {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodySmall,
      fontWeight = FontWeight.Bold
    )
  }
}

@Composable
private fun ProcrastinationRadarCard(report: ProcrastinationRadarReport) {
  Card(
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier.padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Icon(
          imageVector = Icons.Default.Insights,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(26.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Private Procrastination Radar",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = "100% on-device rhythm insights • ${report.sampleSize} tasks analyzed",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      Text(
        text = report.summaryNarrative,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface
      )

      if (report.peakWindow != null || report.dangerWindow != null) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          report.peakWindow?.let { peak ->
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
              modifier = Modifier.weight(1f)
            ) {
              Column(modifier = Modifier.padding(12.dp)) {
                Text("Peak Window", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text(peak.window.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("${peak.onTimeRatePercent}% on time", style = MaterialTheme.typography.bodySmall)
              }
            }
          }
          report.dangerWindow?.let { danger ->
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
              modifier = Modifier.weight(1f)
            ) {
              Column(modifier = Modifier.padding(12.dp)) {
                Text("Danger Zone", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text(danger.window.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("${danger.onTimeRatePercent}% on time", style = MaterialTheme.typography.bodySmall)
              }
            }
          }
        }
      }

      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        report.windowStats.forEach { stat ->
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text(
                text = "${stat.window.label} (${stat.window.shortLabel})",
                style = MaterialTheme.typography.labelMedium
              )
              Text(
                text = if (stat.totalTasks == 0) "No data" else "${stat.onTimeRatePercent}% on-time (${stat.totalTasks})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            LinearProgressIndicator(
              progress = { if (stat.totalTasks == 0) 0f else stat.onTimeRatePercent / 100f },
              modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
            )
          }
        }
      }

      report.highestFrictionCategoryName?.let { catName ->
        Text(
          text = "Highest friction category: $catName — try splitting these with a 5-Min Spark below.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.Medium
        )
      }
    }
  }
}

@Composable
private fun TransformationProofHistoryCard(
  record: TransformationProofRecord,
  proofStore: TransformationProofStore,
  onDelete: () -> Unit
) {
  val beforeBmp = remember(record.beforeImagePath) { proofStore.decodeThumb(record.beforeImagePath) }
  val afterBmp = remember(record.afterImagePath) { proofStore.decodeThumb(record.afterImagePath) }
  val formattedDate = remember(record.createdAtMillis) {
    SimpleDateFormat("MMM d • h:mm a", Locale.getDefault()).format(Date(record.createdAtMillis))
  }

  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    modifier = Modifier.fillMaxWidth()
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
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.weight(1f)
        ) {
          Icon(
            imageVector = if (record.approved) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
            contentDescription = null,
            tint = if (record.approved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp)
          )
          Column {
            Text(
              text = record.taskTitle,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "$formattedDate • Score ${record.transformationScore}/100",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
          Icon(
            imageVector = Icons.Default.DeleteOutline,
            contentDescription = "Delete transformation proof",
            tint = MaterialTheme.colorScheme.error
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        if (beforeBmp != null) {
          Image(
            bitmap = beforeBmp.asImageBitmap(),
            contentDescription = stringResource(R.string.cd_before_photo),
            contentScale = ContentScale.Crop,
            modifier = Modifier
              .weight(1f)
              .height(104.dp)
              .clip(RoundedCornerShape(12.dp))
          )
        }
        if (afterBmp != null) {
          Image(
            bitmap = afterBmp.asImageBitmap(),
            contentDescription = stringResource(R.string.cd_after_photo),
            contentScale = ContentScale.Crop,
            modifier = Modifier
              .weight(1f)
              .height(104.dp)
              .clip(RoundedCornerShape(12.dp))
          )
        }
      }

      Text(
        text = record.whatChanged,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Medium
      )
      Text(
        text = record.feedback,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}
