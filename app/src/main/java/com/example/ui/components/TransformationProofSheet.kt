package com.example.ui.components

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ai.GeminiTaskProofEvaluator
import com.example.ai.TransformationProofVerdict

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransformationProofSheet(
  initialTaskId: String,
  initialTaskTitle: String,
  initialTaskSubtext: String,
  isEvaluating: Boolean,
  verdict: TransformationProofVerdict?,
  onEvaluate: (
    taskId: String,
    taskTitle: String,
    taskSubtext: String,
    criteria: String,
    beforeBitmap: Bitmap,
    afterBitmap: Bitmap,
    note: String,
    completeTaskIfApproved: Boolean
  ) -> Unit,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var taskTitle by remember(initialTaskTitle) { mutableStateOf(initialTaskTitle) }
  var criteria by remember { mutableStateOf("") }
  var note by remember { mutableStateOf("") }
  var completeOnApprove by remember(initialTaskId) { mutableStateOf(initialTaskId.isNotBlank()) }
  var beforeBitmap by remember { mutableStateOf<Bitmap?>(null) }
  var afterBitmap by remember { mutableStateOf<Bitmap?>(null) }

  val beforeCameraLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.TakePicturePreview()
  ) { bmp -> if (bmp != null) beforeBitmap = bmp }

  val afterCameraLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.TakePicturePreview()
  ) { bmp -> if (bmp != null) afterBitmap = bmp }

  val beforeGalleryLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri ->
    if (uri != null) {
      beforeBitmap = GeminiTaskProofEvaluator.loadBitmapFromUri(context, uri)
    }
  }

  val afterGalleryLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri ->
    if (uri != null) {
      afterBitmap = GeminiTaskProofEvaluator.loadBitmapFromUri(context, uri)
    }
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp)
        .padding(bottom = 28.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Icon(
          imageVector = Icons.Default.Compare,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(26.dp)
        )
        Column {
          Text(
            text = stringResource(R.string.transformation_proof_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = "Gemini AI compares your real Before & After photos to verify transformation.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      OutlinedTextField(
        value = taskTitle,
        onValueChange = { taskTitle = it },
        label = { Text("Task / Project Name") },
        singleLine = true,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("transform_task_title_input")
      )

      // Side-by-side Before & After photo pickers
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        PhotoSlotCard(
          label = "1. BEFORE",
          bitmap = beforeBitmap,
          contentDescription = stringResource(R.string.cd_before_photo),
          onCamera = { beforeCameraLauncher.launch(null) },
          onGallery = {
            beforeGalleryLauncher.launch(
              PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
          },
          cameraTag = "transform_before_camera",
          galleryTag = "transform_before_gallery",
          modifier = Modifier.weight(1f)
        )
        PhotoSlotCard(
          label = "2. AFTER",
          bitmap = afterBitmap,
          contentDescription = stringResource(R.string.cd_after_photo),
          onCamera = { afterCameraLauncher.launch(null) },
          onGallery = {
            afterGalleryLauncher.launch(
              PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
          },
          cameraTag = "transform_after_camera",
          galleryTag = "transform_after_gallery",
          modifier = Modifier.weight(1f)
        )
      }

      OutlinedTextField(
        value = criteria,
        onValueChange = { criteria = it },
        label = { Text("What should Gemini verify? (optional)") },
        placeholder = { Text("e.g. Desk is cleared and books are stacked neatly") },
        modifier = Modifier.fillMaxWidth()
      )

      OutlinedTextField(
        value = note,
        onValueChange = { note = it },
        label = { Text("Your note (optional)") },
        modifier = Modifier.fillMaxWidth()
      )

      if (initialTaskId.isNotBlank()) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.fillMaxWidth()
        ) {
          Checkbox(
            checked = completeOnApprove,
            onCheckedChange = { completeOnApprove = it }
          )
          Text(
            text = "Auto-complete task if Gemini verifies the transformation",
            style = MaterialTheme.typography.bodyMedium
          )
        }
      }

      if (verdict != null) {
        Card(
          shape = RoundedCornerShape(18.dp),
          colors = CardDefaults.cardColors(
            containerColor = if (verdict.approved) {
              MaterialTheme.colorScheme.primaryContainer
            } else {
              MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
            }
          ),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(
                imageVector = if (verdict.approved) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                contentDescription = null,
                tint = if (verdict.approved) {
                  MaterialTheme.colorScheme.primary
                } else {
                  MaterialTheme.colorScheme.error
                }
              )
              Text(
                text = if (verdict.approved) {
                  "Transformation Verified • ${verdict.transformationScore}/100"
                } else {
                  "Needs More Progress • ${verdict.transformationScore}/100"
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
              )
            }
            Text(
              text = "What Changed: ${verdict.whatChanged}",
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.Medium
            )
            Text(
              text = verdict.feedback,
              style = MaterialTheme.typography.bodySmall
            )
            Text(
              text = "Gemini Confidence: ${verdict.confidencePercent}% • Saved to your private Proof Vault",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }

      Button(
        onClick = {
          val b = beforeBitmap ?: return@Button
          val a = afterBitmap ?: return@Button
          onEvaluate(
            initialTaskId,
            taskTitle.trim().ifBlank { "My Transformation" },
            initialTaskSubtext,
            criteria,
            b,
            a,
            note,
            completeOnApprove
          )
        },
        enabled = !isEvaluating && beforeBitmap != null && afterBitmap != null && taskTitle.isNotBlank(),
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("transform_verify_button")
      ) {
        if (isEvaluating) {
          CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onPrimary
          )
          Spacer(Modifier.width(10.dp))
          Text("Gemini AI is comparing photos…")
        } else {
          Icon(Icons.Default.AutoAwesome, contentDescription = null)
          Spacer(Modifier.width(8.dp))
          Text("Verify Transformation with Gemini AI")
        }
      }
    }
  }
}

@Composable
private fun PhotoSlotCard(
  label: String,
  bitmap: Bitmap?,
  contentDescription: String,
  onCamera: () -> Unit,
  onGallery: () -> Unit,
  cameraTag: String,
  galleryTag: String,
  modifier: Modifier = Modifier
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
  ) {
    Column(
      modifier = Modifier.padding(10.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
      )
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(124.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(MaterialTheme.colorScheme.surface)
          .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
      ) {
        if (bitmap != null) {
          Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
          )
        } else {
          Text(
            text = "No photo yet",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        FilledTonalButton(
          onClick = onCamera,
          modifier = Modifier
            .weight(1f)
            .height(44.dp)
            .testTag(cameraTag),
          contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
        ) {
          Icon(Icons.Default.CameraAlt, contentDescription = "Take $label photo", modifier = Modifier.size(18.dp))
        }
        OutlinedButton(
          onClick = onGallery,
          modifier = Modifier
            .weight(1f)
            .height(44.dp)
            .testTag(galleryTag),
          contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
        ) {
          Icon(Icons.Default.PhotoLibrary, contentDescription = "Pick $label photo", modifier = Modifier.size(18.dp))
        }
      }
    }
  }
}
