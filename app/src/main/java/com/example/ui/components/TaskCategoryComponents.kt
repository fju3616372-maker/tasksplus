package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.TaskCategory
import com.example.data.model.TaskCategoryCatalog

/**
 * Compact color-coded category label displayed inside task item cards.
 */
@Composable
fun TaskCategoryLabelBadge(
  category: TaskCategory,
  modifier: Modifier = Modifier
) {
  val accent = Color(category.colorHex)
  Surface(
    shape = RoundedCornerShape(10.dp),
    color = accent.copy(alpha = 0.15f),
    border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
    modifier = modifier.testTag("task_category_badge_${category.id}")
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
      Box(
        modifier = Modifier
          .size(7.dp)
          .clip(CircleShape)
          .background(accent)
      )
      Text(
        text = category.name,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = accent,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

/**
 * Slim top bar of category filter pills displayed when task categories are enabled.
 */
@Composable
fun CompactCategoryFilterBar(
  categories: List<TaskCategory>,
  selectedCategoryId: String?,
  onSelectCategory: (String?) -> Unit,
  onManageCategories: () -> Unit,
  modifier: Modifier = Modifier
) {
  if (categories.isEmpty()) return

  LazyRow(
    contentPadding = PaddingValues(horizontal = 16.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
      .fillMaxWidth()
      .padding(top = 4.dp, bottom = 2.dp)
      .testTag("top_category_filter_bar")
  ) {
    item(key = "cat_filter_all") {
      val isAllSelected = selectedCategoryId == null
      Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (isAllSelected) {
          MaterialTheme.colorScheme.primary
        } else {
          MaterialTheme.colorScheme.surfaceContainerHigh
        },
        modifier = Modifier
          .heightIn(min = 34.dp)
          .clip(RoundedCornerShape(999.dp))
          .clickable { onSelectCategory(null) }
          .testTag("category_bar_chip_all")
      ) {
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
          Text(
            text = "All",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (isAllSelected) {
              MaterialTheme.colorScheme.onPrimary
            } else {
              MaterialTheme.colorScheme.onSurfaceVariant
            }
          )
        }
      }
    }

    items(items = categories, key = { it.id }) { category ->
      val isSelected = selectedCategoryId == category.id
      val accent = Color(category.colorHex)
      Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (isSelected) accent else accent.copy(alpha = 0.13f),
        border = if (isSelected) null else BorderStroke(1.dp, accent.copy(alpha = 0.32f)),
        modifier = Modifier
          .heightIn(min = 34.dp)
          .clip(RoundedCornerShape(999.dp))
          .clickable {
            onSelectCategory(if (isSelected) null else category.id)
          }
          .testTag("category_bar_chip_${category.id}")
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
        ) {
          Box(
            modifier = Modifier
              .size(7.dp)
              .clip(CircleShape)
              .background(if (isSelected) Color.White else accent)
          )
          Text(
            text = category.name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (isSelected) Color.White else accent,
            maxLines = 1
          )
        }
      }
    }

    item(key = "cat_filter_manage") {
      Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        modifier = Modifier
          .heightIn(min = 34.dp)
          .clip(RoundedCornerShape(999.dp))
          .clickable { onManageCategories() }
          .testTag("category_bar_manage_button")
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Tune,
            contentDescription = "Manage categories",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
          )
          Text(
            text = "Manage",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }
  }
}

/**
 * Category assignment selector inside TaskEditorSheet.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskCategoryPickerCard(
  categories: List<TaskCategory>,
  selectedCategoryId: String,
  onSelectCategoryId: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainer
    ),
    modifier = modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Icon(
          imageVector = Icons.Default.Label,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(18.dp)
        )
        Text(
          text = "Category (Optional)",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold
        )
      }

      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        val isNone = selectedCategoryId.isBlank()
        Surface(
          shape = RoundedCornerShape(999.dp),
          color = if (isNone) {
            MaterialTheme.colorScheme.primaryContainer
          } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
          },
          border = if (isNone) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
          } else null,
          modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable { onSelectCategoryId("") }
            .testTag("editor_category_none")
        ) {
          Text(
            text = "None",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (isNone) {
              MaterialTheme.colorScheme.onPrimaryContainer
            } else {
              MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
          )
        }

        categories.forEach { category ->
          val isSelected = selectedCategoryId == category.id
          val accent = Color(category.colorHex)
          Surface(
            shape = RoundedCornerShape(999.dp),
            color = if (isSelected) accent else accent.copy(alpha = 0.14f),
            border = if (isSelected) null else BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
            modifier = Modifier
              .clip(RoundedCornerShape(999.dp))
              .clickable {
                onSelectCategoryId(if (isSelected) "" else category.id)
              }
              .testTag("editor_category_${category.id}")
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(8.dp)
                  .clip(CircleShape)
                  .background(if (isSelected) Color.White else accent)
              )
              Text(
                text = category.name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isSelected) Color.White else accent
              )
            }
          }
        }
      }
    }
  }
}

/**
 * Full Settings manager for enabling optional categories, toggling premade categories,
 * and creating or removing custom color-coded categories.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskCategoriesSettingsSection(
  categoriesEnabled: Boolean,
  disabledPremadeIds: Set<String>,
  customCategories: List<TaskCategory>,
  onToggleCategoriesEnabled: (Boolean) -> Unit,
  onTogglePremadeCategory: (String, Boolean) -> Unit,
  onAddCustomCategory: (String, Long) -> Unit,
  onDeleteCustomCategory: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  var newCategoryName by rememberSaveable { mutableStateOf("") }
  var selectedColorHex by rememberSaveable { mutableLongStateOf(TaskCategoryCatalog.colorSwatches.first()) }

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    // 1. Optional Master Toggle
    Card(
      shape = RoundedCornerShape(22.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
      modifier = Modifier.fillMaxWidth()
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Enable Task Categories",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
          )
          Text(
            text = "Show color-coded category badges on tasks and a compact category filter bar at the top.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
          checked = categoriesEnabled,
          onCheckedChange = onToggleCategoriesEnabled,
          modifier = Modifier.testTag("settings_categories_enabled_switch")
        )
      }
    }

    // 2. Premade Categories
    Card(
      shape = RoundedCornerShape(22.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Text(
          text = "Premade Categories",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold
        )
        Text(
          text = "Turn built-in categories on or off to keep your top bar focused.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        TaskCategoryCatalog.premadeCategories.forEach { cat ->
          val enabled = cat.id !in disabledPremadeIds
          val accent = Color(cat.colorHex)
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(14.dp)
                  .clip(CircleShape)
                  .background(accent)
              )
              Text(
                text = cat.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
              )
            }
            Switch(
              checked = enabled,
              onCheckedChange = { onTogglePremadeCategory(cat.id, it) },
              modifier = Modifier.testTag("settings_premade_cat_switch_${cat.id}")
            )
          }
        }
      }
    }

    // 3. Custom Categories Creator & List
    Card(
      shape = RoundedCornerShape(22.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Text(
          text = "Custom Categories",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold
        )
        Text(
          text = "Create your own color-coded labels (e.g., Finance, Fitness, Errands).",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
          value = newCategoryName,
          onValueChange = { if (it.length <= 32) newCategoryName = it },
          label = { Text("Category Name") },
          placeholder = { Text("e.g., Finance") },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_custom_category_name_input")
        )

        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          TaskCategoryCatalog.colorSwatches.forEach { hex ->
            val swatchColor = Color(hex)
            val isPicked = selectedColorHex == hex
            Box(
              contentAlignment = Alignment.Center,
              modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(swatchColor)
                .then(
                  if (isPicked) {
                    Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                  } else Modifier
                )
                .clickable { selectedColorHex = hex }
                .testTag("settings_category_swatch_${hex.toString(16)}")
            ) {
              if (isPicked) {
                Icon(
                  imageVector = Icons.Default.Check,
                  contentDescription = "Selected color",
                  tint = Color.White,
                  modifier = Modifier.size(18.dp)
                )
              }
            }
          }
        }

        Button(
          onClick = {
            if (newCategoryName.isNotBlank()) {
              onAddCustomCategory(newCategoryName.trim(), selectedColorHex)
              newCategoryName = ""
            }
          },
          enabled = newCategoryName.isNotBlank(),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("settings_add_custom_category_button")
        ) {
          Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Add Custom Category")
        }

        if (customCategories.isNotEmpty()) {
          Spacer(modifier = Modifier.height(2.dp))
          customCategories.forEach { customCat ->
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = MaterialTheme.colorScheme.surfaceContainerLow,
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                TaskCategoryLabelBadge(category = customCat)
                IconButton(
                  onClick = { onDeleteCustomCategory(customCat.id) },
                  modifier = Modifier
                    .size(48.dp)
                    .testTag("settings_delete_custom_category_${customCat.id}")
                ) {
                  Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete ${customCat.name}",
                    tint = MaterialTheme.colorScheme.error
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}
