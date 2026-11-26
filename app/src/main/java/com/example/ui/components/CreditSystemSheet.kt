package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.auth.GoogleAuthHelper
import com.example.data.model.CloudCreditTransaction
import com.example.data.model.CloudGroupMember
import com.example.data.model.CloudGroupPerk
import com.example.data.model.CreditCalculator
import com.example.data.model.CreditRules
import com.example.data.model.PerkStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class CreditSheetTab(val label: String) {
  POINTS("Points & Perks"),
  HISTORY("History")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreditSystemSheet(
  isSignedIn: Boolean = true,
  currentUserUid: String = "",
  userDisplayName: String = "",
  userEmail: String = "",
  hasActiveGroup: Boolean = false,
  groupName: String,
  currentBalance: Long,
  totalEarnedCredits: Long = 0L,
  totalDeductedCredits: Long = 0L,
  creditRules: CreditRules,
  canEditRules: Boolean,
  groupMembers: List<CloudGroupMember>,
  groupPerks: List<CloudGroupPerk> = emptyList(),
  transactions: List<CloudCreditTransaction>,
  userFallbackInitial: String = "U",
  initialTab: CreditSheetTab = CreditSheetTab.POINTS,
  onSignInSuccess: () -> Unit = {},
  onSignInError: (String) -> Unit = {},
  onOpenAccountsSettings: () -> Unit = {},
  onOpenGroupPerks: () -> Unit = {},
  onDismiss: () -> Unit,
  onSaveRules: (CreditRules) -> Unit,
  onAdjustCredits: (targetMember: CloudGroupMember?, pointsDelta: Long, reason: String) -> Unit,
  onRedeemPerk: (CloudGroupPerk) -> Unit = {},
  onUpdatePerkStatus: (CloudGroupPerk, PerkStatus) -> Unit = { _, _ -> }
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val dateFmt = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }
  var isSigningIn by remember { mutableStateOf(false) }

  var selectedSheetTab by rememberSaveable(initialTab) { mutableStateOf(initialTab) }

  var enabled by rememberSaveable(creditRules.enabled, hasActiveGroup) {
    mutableStateOf(creditRules.enabled)
  }
  var earlyRateText by rememberSaveable(creditRules.earlyPointsPerSecond, hasActiveGroup) {
    mutableStateOf(CreditCalculator.formatRate(creditRules.earlyPointsPerSecond))
  }
  var lateRateText by rememberSaveable(creditRules.latePenaltyPerSecond, hasActiveGroup) {
    mutableStateOf(CreditCalculator.formatRate(creditRules.latePenaltyPerSecond))
  }
  var graceText by rememberSaveable(creditRules.gracePeriodSeconds, hasActiveGroup) {
    mutableStateOf(creditRules.gracePeriodSeconds.toString())
  }
  var maxBonusText by rememberSaveable(creditRules.maxCreditPerTask, hasActiveGroup) {
    mutableStateOf(creditRules.maxCreditPerTask.toString())
  }
  var maxPenaltyText by rememberSaveable(creditRules.maxPenaltyPerTask, hasActiveGroup) {
    mutableStateOf(creditRules.maxPenaltyPerTask.toString())
  }
  var allowNegative by rememberSaveable(creditRules.allowNegativeBalance, hasActiveGroup) {
    mutableStateOf(creditRules.allowNegativeBalance)
  }

  var selectedMemberUid by rememberSaveable {
    mutableStateOf(groupMembers.firstOrNull()?.uid.orEmpty())
  }
  LaunchedEffect(groupMembers) {
    if (groupMembers.isNotEmpty() && groupMembers.none { it.uid == selectedMemberUid }) {
      selectedMemberUid = groupMembers.first().uid
    }
  }
  var adjustAmountText by rememberSaveable { mutableStateOf("60") }
  var adjustReasonText by rememberSaveable { mutableStateOf("") }

  val draftRules = remember(
    enabled,
    earlyRateText,
    lateRateText,
    graceText,
    maxBonusText,
    maxPenaltyText,
    allowNegative
  ) {
    CreditRules(
      enabled = enabled,
      earlyPointsPerSecond = earlyRateText.toDoubleOrNull()?.coerceIn(0.0, 1000.0) ?: 1.0,
      latePenaltyPerSecond = lateRateText.toDoubleOrNull()?.coerceIn(0.0, 1000.0) ?: 1.0,
      gracePeriodSeconds = graceText.toLongOrNull()?.coerceIn(0L, 86_400L) ?: 0L,
      maxCreditPerTask = maxBonusText.toLongOrNull()?.coerceIn(0L, 1_000_000_000L) ?: 0L,
      maxPenaltyPerTask = maxPenaltyText.toLongOrNull()?.coerceIn(0L, 1_000_000_000L) ?: 0L,
      allowNegativeBalance = allowNegative
    ).normalized()
  }

  val earlyPreview = remember(draftRules) {
    CreditCalculator.calculateTaskCredit(
      deadlineMillis = 120_000L + 1_000_000L,
      completedAtMillis = 1_000_000L,
      rules = draftRules
    )
  }
  val latePreview = remember(draftRules) {
    CreditCalculator.calculateTaskCredit(
      deadlineMillis = 1_000_000L,
      completedAtMillis = 1_060_000L,
      rules = draftRules
    )
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 10.dp)
        .navigationBarsPadding(),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      // Account & Group Summary Header
      val resolvedName = if (isSignedIn) {
        userDisplayName.trim().ifBlank {
          userEmail.substringBefore("@").ifBlank { "Tasks+ User" }
        }
      } else {
        "Tasks+ Local Account"
      }

      Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("profile_identity_header_card")
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
          ) {
            ProfileAvatarBadge(
              fallbackInitial = userFallbackInitial,
              size = 56.dp,
              modifier = Modifier.size(56.dp)
            )
            Column(
              modifier = Modifier.weight(1f),
              verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
              Text(
                text = resolvedName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Text(
                text = if (isSignedIn && userEmail.isNotBlank()) {
                  userEmail
                } else {
                  "Sign in for cloud sync & Groups"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              if (isSignedIn && hasActiveGroup) {
                Text(
                  text = "Group: $groupName",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.primary,
                  fontWeight = FontWeight.SemiBold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
            }
            if (isSignedIn && hasActiveGroup) {
              Surface(
                color = if (!creditRules.enabled) {
                  MaterialTheme.colorScheme.surfaceContainerHighest
                } else if (currentBalance >= 0L) {
                  MaterialTheme.colorScheme.primaryContainer
                } else {
                  MaterialTheme.colorScheme.errorContainer
                },
                shape = CircleShape
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                  Icon(
                    imageVector = Icons.Default.Stars,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (!creditRules.enabled) {
                      MaterialTheme.colorScheme.onSurfaceVariant
                    } else if (currentBalance >= 0L) {
                      MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                      MaterialTheme.colorScheme.onErrorContainer
                    }
                  )
                  Text(
                    text = if (creditRules.enabled) {
                      CreditCalculator.formatSignedPoints(currentBalance)
                    } else {
                      "Pts Off"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (!creditRules.enabled) {
                      MaterialTheme.colorScheme.onSurfaceVariant
                    } else if (currentBalance >= 0L) {
                      MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                      MaterialTheme.colorScheme.onErrorContainer
                    }
                  )
                }
              }
            }
          }

          if (!isSignedIn) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Button(
                onClick = {
                  isSigningIn = true
                  GoogleAuthHelper.onGoogleSignInClicked(
                    context = context,
                    credentialManager = credentialManager,
                    onAuthSuccess = {
                      isSigningIn = false
                      onSignInSuccess()
                    },
                    onAuthError = { err ->
                      isSigningIn = false
                      onSignInError(err)
                    },
                    scope = scope,
                    onAuthCancelled = { isSigningIn = false }
                  )
                },
                enabled = !isSigningIn,
                modifier = Modifier
                  .weight(1f)
                  .height(48.dp)
                  .testTag("profile_google_sign_in_button")
              ) {
                if (isSigningIn) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Sign in")
              }
              OutlinedButton(
                onClick = {
                  onDismiss()
                  onOpenAccountsSettings()
                },
                modifier = Modifier
                  .weight(1f)
                  .height(48.dp)
                  .testTag("profile_open_settings_accounts_button")
              ) {
                Text("Accounts")
              }
            }
          }
        }
      }

      // Sheet Navigation Tabs (Points & Perks | History)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .testTag("credit_sheet_tabs_row"),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        CreditSheetTab.entries.forEach { tab ->
          FilterChip(
            selected = selectedSheetTab == tab,
            onClick = { selectedSheetTab = tab },
            label = {
              Text(
                text = when (tab) {
                  CreditSheetTab.POINTS -> "Points & Perks"
                  CreditSheetTab.HISTORY -> "History (${transactions.size})"
                },
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
              )
            },
            leadingIcon = {
              Icon(
                imageVector = when (tab) {
                  CreditSheetTab.POINTS -> Icons.Default.Stars
                  CreditSheetTab.HISTORY -> Icons.Default.History
                },
                contentDescription = null,
                modifier = Modifier.size(16.dp)
              )
            },
            modifier = Modifier
              .weight(1f)
              .testTag("credit_sheet_tab_${tab.name.lowercase()}")
          )
        }
      }

      if (selectedSheetTab == CreditSheetTab.POINTS) {
        if (!isSignedIn || !hasActiveGroup) {
          GroupOnlyPointsEmptyCard(
            isSignedIn = isSignedIn,
            isSigningIn = isSigningIn,
            onSignIn = {
              isSigningIn = true
              GoogleAuthHelper.onGoogleSignInClicked(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {
                  isSigningIn = false
                  onSignInSuccess()
                },
                onAuthError = { err ->
                  isSigningIn = false
                  onSignInError(err)
                },
                scope = scope,
                onAuthCancelled = { isSigningIn = false }
              )
            },
            onOpenGroups = {
              onDismiss()
              onOpenGroupPerks()
            }
          )
        } else {
          // Group Points Balance Card
          Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
              containerColor = if (!creditRules.enabled) {
                MaterialTheme.colorScheme.surfaceContainerHigh
              } else if (currentBalance >= 0L) {
                MaterialTheme.colorScheme.primaryContainer
              } else {
                MaterialTheme.colorScheme.errorContainer
              }
            ),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("credit_balance_hero_card")
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              val heroContentColor = if (!creditRules.enabled) {
                MaterialTheme.colorScheme.onSurface
              } else if (currentBalance >= 0L) {
                MaterialTheme.colorScheme.onPrimaryContainer
              } else {
                MaterialTheme.colorScheme.onErrorContainer
              }
              Text(
                text = "$groupName • Your Group Points",
                style = MaterialTheme.typography.labelLarge,
                color = heroContentColor.copy(alpha = 0.85f)
              )
              Text(
                text = if (creditRules.enabled) {
                  CreditCalculator.formatSignedPoints(currentBalance)
                } else {
                  "Points Off (Default)"
                },
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                color = heroContentColor,
                modifier = Modifier.testTag("credit_balance_value")
              )
              if (creditRules.enabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                    shape = CircleShape
                  ) {
                    Text(
                      text = "Earned +$totalEarnedCredits",
                      style = MaterialTheme.typography.labelMedium,
                      fontWeight = FontWeight.SemiBold,
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                  }
                  Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                    shape = CircleShape
                  ) {
                    Text(
                      text = "Spent/Deducted -$totalDeductedCredits",
                      style = MaterialTheme.typography.labelMedium,
                      fontWeight = FontWeight.SemiBold,
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                  }
                }
              } else {
                Text(
                  text = "Group Points are optional and turned off by default. Only the main Group Manager can turn them on.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }

          // Manager-Assigned Perks Preview Card
          Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("credit_sheet_perks_card")
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
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
                    imageVector = Icons.Default.CardGiftcard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                  )
                  Text(
                    text = "Group Perks (${groupPerks.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                  )
                }
                FilledTonalButton(
                  onClick = {
                    onDismiss()
                    onOpenGroupPerks()
                  },
                  modifier = Modifier
                    .height(48.dp)
                    .testTag("open_group_perks_from_sheet_button")
                ) {
                  Text(if (canEditRules) "Assign & Manage" else "View All")
                }
              }

              if (groupPerks.isEmpty()) {
                Text(
                  text = if (canEditRules) {
                    "No perks assigned yet. Open the Group Perks tab to assign specific perks to other members."
                  } else {
                    "No perks assigned yet. The main Group Manager can assign specific perks to members."
                  },
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              } else {
                groupPerks.take(4).forEach { perk ->
                  Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                  ) {
                    Row(
                      modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Column(
                        modifier = Modifier
                          .weight(1f)
                          .padding(end = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                      ) {
                        Text(
                          text = perk.title,
                          style = MaterialTheme.typography.titleSmall,
                          fontWeight = FontWeight.SemiBold,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis
                        )
                        val costLabel = if (perk.pointsCost > 0L) "${perk.pointsCost} pts" else "Direct Grant"
                        Text(
                          text = "For ${perk.assignedToName} • $costLabel • ${perk.parsedStatus.label}",
                          style = MaterialTheme.typography.labelSmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                      }
                      if (perk.assignedToUid == currentUserUid &&
                        perk.parsedStatus == PerkStatus.ASSIGNED &&
                        perk.pointsCost > 0L &&
                        creditRules.enabled
                      ) {
                        FilledTonalButton(
                          onClick = { onRedeemPerk(perk) },
                          enabled = currentBalance >= perk.pointsCost,
                          modifier = Modifier.height(48.dp)
                        ) {
                          Text("Redeem")
                        }
                      } else if (canEditRules && perk.parsedStatus == PerkStatus.ASSIGNED) {
                        FilledTonalButton(
                          onClick = { onUpdatePerkStatus(perk, PerkStatus.GRANTED) },
                          modifier = Modifier.height(48.dp)
                        ) {
                          Text("Grant")
                        }
                      }
                    }
                  }
                }
              }
            }
          }

          // Group Member Balances (when points enabled)
          if (creditRules.enabled && groupMembers.isNotEmpty()) {
            val sortedMembers = remember(groupMembers) {
              groupMembers.sortedByDescending { it.creditBalance }
            }
            Card(
              shape = RoundedCornerShape(22.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Text(
                  text = "Group Member Points",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold
                )
                sortedMembers.forEach { member ->
                  val memberName = member.displayName.ifBlank { member.email.substringBefore("@") }
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    ProfileAvatarBadge(
                      fallbackInitial = memberName.firstOrNull()?.uppercase() ?: "M",
                      size = 34.dp,
                      modifier = Modifier.size(34.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = memberName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                      )
                      Text(
                        text = member.parsedRole.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }
                    Surface(
                      color = if (member.creditBalance >= 0L) {
                        MaterialTheme.colorScheme.secondaryContainer
                      } else {
                        MaterialTheme.colorScheme.errorContainer
                      },
                      shape = CircleShape
                    ) {
                      Text(
                        text = CreditCalculator.formatSignedPoints(member.creditBalance),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (member.creditBalance >= 0L) {
                          MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                          MaterialTheme.colorScheme.onErrorContainer
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                      )
                    }
                  }
                }
              }
            }
          }

          // Configurable Rules Card (Main Group Manager Only)
          Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("credit_rules_card")
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Icon(
                  imageVector = if (canEditRules) Icons.Default.Tune else Icons.Default.Lock,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary
                )
                Text(
                  text = "Group Points Rules",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold
                )
              }

              if (!canEditRules) {
                Surface(
                  color = MaterialTheme.colorScheme.secondaryContainer,
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Text(
                    text = "Main Manager Only — Only the primary Group Manager can enable Group Points, change point rules, adjust balances, or assign perks.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(12.dp)
                  )
                }
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "Enable Group Points (Optional)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                  )
                  Text(
                    text = "Off by default. Turn on if your group wants deadline points for redeeming perks.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
                Switch(
                  checked = enabled,
                  onCheckedChange = { if (canEditRules) enabled = it },
                  enabled = canEditRules,
                  modifier = Modifier.testTag("credit_system_enabled_switch")
                )
              }

              AnimatedVisibility(visible = enabled) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                  ) {
                    OutlinedTextField(
                      value = earlyRateText,
                      onValueChange = { input ->
                        if (canEditRules) {
                          earlyRateText = input.filter { it.isDigit() || it == '.' }.take(6)
                        }
                      },
                      enabled = canEditRules,
                      label = { Text("Early (pts/sec)") },
                      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                      singleLine = true,
                      shape = RoundedCornerShape(14.dp),
                      modifier = Modifier
                        .weight(1f)
                        .testTag("credit_early_rate_input")
                    )
                    OutlinedTextField(
                      value = lateRateText,
                      onValueChange = { input ->
                        if (canEditRules) {
                          lateRateText = input.filter { it.isDigit() || it == '.' }.take(6)
                        }
                      },
                      enabled = canEditRules,
                      label = { Text("Late (-pts/sec)") },
                      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                      singleLine = true,
                      shape = RoundedCornerShape(14.dp),
                      modifier = Modifier
                        .weight(1f)
                        .testTag("credit_late_rate_input")
                    )
                  }

                  if (canEditRules) {
                    FlowRow(
                      horizontalArrangement = Arrangement.spacedBy(6.dp),
                      verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                      listOf("0.5", "1", "2", "5").forEach { preset ->
                        FilterChip(
                          selected = earlyRateText == preset && lateRateText == preset,
                          onClick = {
                            earlyRateText = preset
                            lateRateText = preset
                          },
                          label = { Text("±$preset/sec") }
                        )
                      }
                      FilterChip(
                        selected = earlyRateText == "1" && lateRateText == "0",
                        onClick = {
                          earlyRateText = "1"
                          lateRateText = "0"
                        },
                        label = { Text("Bonus Only (+1/s)") }
                      )
                    }
                  }

                  OutlinedTextField(
                    value = graceText,
                    onValueChange = { input ->
                      if (canEditRules) graceText = input.filter { it.isDigit() }.take(5)
                    },
                    enabled = canEditRules,
                    label = { Text("Grace Period (seconds around deadline)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                      .fillMaxWidth()
                      .testTag("credit_grace_input")
                  )

                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                  ) {
                    OutlinedTextField(
                      value = maxBonusText,
                      onValueChange = { input ->
                        if (canEditRules) maxBonusText = input.filter { it.isDigit() }.take(8)
                      },
                      enabled = canEditRules,
                      label = { Text("Max Bonus (0 = none)") },
                      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                      singleLine = true,
                      shape = RoundedCornerShape(14.dp),
                      modifier = Modifier
                        .weight(1f)
                        .testTag("credit_max_bonus_input")
                    )
                    OutlinedTextField(
                      value = maxPenaltyText,
                      onValueChange = { input ->
                        if (canEditRules) maxPenaltyText = input.filter { it.isDigit() }.take(8)
                      },
                      enabled = canEditRules,
                      label = { Text("Max Penalty (0 = none)") },
                      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                      singleLine = true,
                      shape = RoundedCornerShape(14.dp),
                      modifier = Modifier
                        .weight(1f)
                        .testTag("credit_max_penalty_input")
                    )
                  }

                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = "Allow Negative Balance",
                        style = MaterialTheme.typography.titleSmall
                      )
                      Text(
                        text = "If disabled, balance stops at 0 pts instead of going negative.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }
                    Switch(
                      checked = allowNegative,
                      onCheckedChange = { if (canEditRules) allowNegative = it },
                      enabled = canEditRules,
                      modifier = Modifier.testTag("credit_allow_negative_switch")
                    )
                  }

                  Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                  ) {
                    Text(
                      text = "Preview: Finish 2m early = ${CreditCalculator.formatSignedPoints(earlyPreview.pointsDelta)} • Finish 1m late = ${CreditCalculator.formatSignedPoints(latePreview.pointsDelta)}",
                      style = MaterialTheme.typography.labelMedium,
                      color = MaterialTheme.colorScheme.primary,
                      fontWeight = FontWeight.SemiBold,
                      modifier = Modifier.padding(12.dp)
                    )
                  }
                }
              }

              if (canEditRules) {
                Button(
                  onClick = { onSaveRules(draftRules) },
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("save_credit_rules_button")
                ) {
                  Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Save Group Points Rules")
                }
              }
            }
          }

          // Manual Bonus / Deduction Card (Main Manager Only)
          if (canEditRules && groupMembers.isNotEmpty()) {
            Card(
              shape = RoundedCornerShape(22.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("credit_manual_adjust_card")
            ) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                Text(
                  text = "Award or Deduct Member Points",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold
                )

                FlowRow(
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                  verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  groupMembers.forEach { member ->
                    FilterChip(
                      selected = selectedMemberUid == member.uid,
                      onClick = { selectedMemberUid = member.uid },
                      label = {
                        Text("${member.displayName.ifBlank { member.email.substringBefore("@") }} (${member.creditBalance})")
                      },
                      modifier = Modifier.testTag("credit_adjust_member_${member.uid}")
                    )
                  }
                }

                OutlinedTextField(
                  value = adjustAmountText,
                  onValueChange = { adjustAmountText = it.filter { ch -> ch.isDigit() }.take(7) },
                  label = { Text("Points Amount") },
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                  singleLine = true,
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("credit_adjust_amount_input")
                )

                OutlinedTextField(
                  value = adjustReasonText,
                  onValueChange = { adjustReasonText = it },
                  label = { Text("Reason (optional)") },
                  placeholder = { Text("e.g., Extra chores bonus or perk adjustment") },
                  singleLine = true,
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("credit_adjust_reason_input")
                )

                val parsedAmount = adjustAmountText.toLongOrNull()?.coerceAtLeast(1L) ?: 0L
                val targetMember = groupMembers.firstOrNull { it.uid == selectedMemberUid }

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                  FilledTonalButton(
                    onClick = {
                      if (parsedAmount > 0L && targetMember != null) {
                        onAdjustCredits(targetMember, parsedAmount, adjustReasonText.trim())
                        adjustReasonText = ""
                      }
                    },
                    enabled = parsedAmount > 0L && targetMember != null,
                    modifier = Modifier
                      .weight(1f)
                      .height(48.dp)
                      .testTag("credit_award_button")
                  ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Award +$parsedAmount")
                  }
                  OutlinedButton(
                    onClick = {
                      if (parsedAmount > 0L && targetMember != null) {
                        onAdjustCredits(targetMember, -parsedAmount, adjustReasonText.trim())
                        adjustReasonText = ""
                      }
                    },
                    enabled = parsedAmount > 0L && targetMember != null,
                    colors = ButtonDefaults.outlinedButtonColors(
                      contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier
                      .weight(1f)
                      .height(48.dp)
                      .testTag("credit_deduct_button")
                  ) {
                    Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Deduct -$parsedAmount")
                  }
                }
              }
            }
          }
        }
      }

      if (selectedSheetTab == CreditSheetTab.HISTORY) {
        if (!isSignedIn || !hasActiveGroup) {
          GroupOnlyPointsEmptyCard(
            isSignedIn = isSignedIn,
            isSigningIn = isSigningIn,
            onSignIn = {
              isSigningIn = true
              GoogleAuthHelper.onGoogleSignInClicked(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {
                  isSigningIn = false
                  onSignInSuccess()
                },
                onAuthError = { err ->
                  isSigningIn = false
                  onSignInError(err)
                },
                scope = scope,
                onAuthCancelled = { isSigningIn = false }
              )
            },
            onOpenGroups = {
              onDismiss()
              onOpenGroupPerks()
            }
          )
        } else {
          Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("credit_ledger_section")
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.History,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary
                )
                Text(
                  text = "Group Points Activity (${transactions.size})",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold
                )
              }

              if (transactions.isEmpty()) {
                Text(
                  text = "No group point transactions yet. When the Group Manager enables Group Points, task timing awards and perk redemptions appear here.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              } else {
                transactions.take(30).forEachIndexed { index, tx ->
                  if (index > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                  }
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Column(
                      modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                      verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                      Text(
                        text = tx.taskTitle.ifBlank { "Points Update" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                      )
                      Text(
                        text = tx.reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                      )
                      val tsMillis = tx.createdAt?.toDate()?.time ?: 0L
                      val metaParts = buildList {
                        if (tx.userName.isNotBlank()) add("For ${tx.userName}")
                        if (tx.awardedByName.isNotBlank() && tx.awardedByUid != tx.userId) {
                          add("By ${tx.awardedByName}")
                        }
                        if (tsMillis > 0L) add(dateFmt.format(Date(tsMillis)))
                      }
                      if (metaParts.isNotEmpty()) {
                        Text(
                          text = metaParts.joinToString(" • "),
                          style = MaterialTheme.typography.labelSmall,
                          color = MaterialTheme.colorScheme.outline
                        )
                      }
                    }

                    val positive = tx.pointsDelta >= 0L
                    Surface(
                      color = if (positive) {
                        MaterialTheme.colorScheme.primaryContainer
                      } else {
                        MaterialTheme.colorScheme.errorContainer
                      },
                      shape = CircleShape
                    ) {
                      Text(
                        text = CreditCalculator.formatSignedPoints(tx.pointsDelta),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (positive) {
                          MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                          MaterialTheme.colorScheme.onErrorContainer
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(6.dp))
    }
  }
}

@Composable
private fun GroupOnlyPointsEmptyCard(
  isSignedIn: Boolean,
  isSigningIn: Boolean,
  onSignIn: () -> Unit,
  onOpenGroups: () -> Unit
) {
  Card(
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("group_only_points_empty_card")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = CircleShape,
        modifier = Modifier.size(56.dp)
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Default.Groups,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(30.dp)
          )
        }
      }
      Text(
        text = "Points & Perks Are for Groups",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold
      )
      Text(
        text = "Optional points (disabled by default) and manager-assigned perks are exclusive to Family and Workplace Groups.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      if (!isSignedIn) {
        Button(
          onClick = onSignIn,
          enabled = !isSigningIn,
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("group_points_sign_in_button")
        ) {
          if (isSigningIn) {
            CircularProgressIndicator(
              modifier = Modifier.size(18.dp),
              color = MaterialTheme.colorScheme.onPrimary,
              strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
          }
          Text("Sign in with Google")
        }
      } else {
        FilledTonalButton(
          onClick = onOpenGroups,
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("group_points_open_group_button")
        ) {
          Icon(Icons.Default.Groups, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("Open Group Tab")
        }
      }
    }
  }
}
