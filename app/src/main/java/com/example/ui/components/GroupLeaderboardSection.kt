package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Diversity3
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.CloudGroup
import com.example.data.model.CloudGroupMember
import com.example.data.model.CloudGroupTask
import com.example.data.model.GroupType
import java.text.NumberFormat
import java.util.Locale

private val GoldMedalColor = Color(0xFFFFC107)
private val SilverMedalColor = Color(0xFFB0BEC5)
private val BronzeMedalColor = Color(0xFFCD7F32)

data class GroupLeaderboardStanding(
  val rank: Int,
  val member: CloudGroupMember,
  val completedTasksCount: Int
)

fun rankGroupMembers(
  members: List<CloudGroupMember>,
  groupTasks: List<CloudGroupTask> = emptyList(),
  pointsEnabled: Boolean = true
): List<GroupLeaderboardStanding> {
  val completedByMember = groupTasks
    .filter { it.isCompleted && it.completedByUid.isNotBlank() }
    .groupingBy { it.completedByUid }
    .eachCount()

  val comparator = if (pointsEnabled) {
    compareByDescending<CloudGroupMember> { it.creditBalance }
      .thenByDescending { completedByMember[it.uid] ?: 0 }
      .thenByDescending { it.totalEarnedCredits }
      .thenBy { it.displayName.lowercase(Locale.US) }
      .thenBy { it.uid }
  } else {
    compareByDescending<CloudGroupMember> { completedByMember[it.uid] ?: 0 }
      .thenByDescending { it.creditBalance }
      .thenBy { it.displayName.lowercase(Locale.US) }
      .thenBy { it.uid }
  }

  return members
    .sortedWith(comparator)
    .mapIndexed { idx, member ->
      GroupLeaderboardStanding(
        rank = idx + 1,
        member = member,
        completedTasksCount = completedByMember[member.uid] ?: 0
      )
    }
}

fun groupKindDisplayLabel(groupType: GroupType): String {
  return when (groupType) {
    GroupType.FAMILY -> "Family"
    GroupType.ORGANIZATION -> "Workplace"
  }
}

@Composable
fun GroupLeaderboardSection(
  isSignedIn: Boolean,
  currentUserUid: String,
  activeGroup: CloudGroup?,
  groupMembers: List<CloudGroupMember>,
  groupTasks: List<CloudGroupTask>,
  isSigningIn: Boolean = false,
  onSignInClick: (() -> Unit)? = null,
  onOpenGroupsTab: (() -> Unit)? = null,
  onOpenCreditSheet: (() -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  val numberFormatter = remember { NumberFormat.getIntegerInstance(Locale.getDefault()) }

  if (!isSignedIn) {
    SignedOutGroupLeaderboardCard(
      isSigningIn = isSigningIn,
      onSignInClick = onSignInClick,
      modifier = modifier
    )
    return
  }

  if (activeGroup == null) {
    NoGroupLeaderboardCard(
      onOpenGroupsTab = onOpenGroupsTab,
      modifier = modifier
    )
    return
  }

  val pointsEnabled = activeGroup.creditSystemEnabled
  val standings = remember(groupMembers, groupTasks, pointsEnabled) {
    rankGroupMembers(groupMembers, groupTasks, pointsEnabled = pointsEnabled)
  }
  val myStanding = remember(standings, currentUserUid) {
    standings.firstOrNull { it.member.uid == currentUserUid }
  }
  val groupKindLabel = groupKindDisplayLabel(activeGroup.parsedType)
  val groupTypeIcon = if (activeGroup.parsedType == GroupType.FAMILY) {
    Icons.Default.Diversity3
  } else {
    Icons.Default.Business
  }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .testTag("group_leaderboard_container"),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    // 1. Group & Personal Standing Hero Card
    Card(
      shape = RoundedCornerShape(24.dp),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
      ),
      border = BorderStroke(
        width = 1.dp,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
      ),
      modifier = Modifier
        .fillMaxWidth()
        .testTag("group_leaderboard_standing_card")
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
            shape = RoundedCornerShape(50)
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Icon(
                imageVector = groupTypeIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp)
              )
              Text(
                text = "$groupKindLabel Leaderboard",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
              )
            }
          }

          Text(
            text = activeGroup.name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp)
          )
        }

        val me = myStanding?.member
        val myName = me?.displayName?.ifBlank { "You" } ?: "You"
        val myPoints = me?.creditBalance ?: 0L
        val myCompleted = myStanding?.completedTasksCount ?: 0

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          ProfileAvatarBadge(
            fallbackInitial = myName,
            size = 54.dp,
            modifier = Modifier.size(54.dp)
          )
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
          ) {
            Text(
              text = myName,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              text = if (pointsEnabled) {
                "${formatPoints(myPoints, numberFormatter)} pts"
              } else {
                "$myCompleted ${if (myCompleted == 1) "Task" else "Tasks"} Done"
              },
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.ExtraBold,
              color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
              text = buildString {
                if (me != null) {
                  append(me.parsedRole.label)
                  append(" • ")
                }
                append("$myCompleted ${if (myCompleted == 1) "task" else "tasks"} done")
              },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }

          Surface(
            color = MaterialTheme.colorScheme.primary,
            shape = RoundedCornerShape(16.dp)
          ) {
            Column(
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Text(
                text = if (myStanding != null) "#${myStanding.rank}" else "--",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onPrimary
              )
              Text(
                text = if (standings.isNotEmpty()) "of ${standings.size}" else "Rank",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
              )
            }
          }
        }

        if (!pointsEnabled) {
          Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(
              text = "Group points are off by default, so standings rank by completed tasks. The main Group Manager can enable optional points and assign perks anytime.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
          }
        }

        if (onOpenCreditSheet != null || onOpenGroupsTab != null) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            if (onOpenCreditSheet != null) {
              FilledTonalButton(
                onClick = onOpenCreditSheet,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                modifier = Modifier
                  .weight(1f)
                  .heightIn(min = 48.dp)
                  .testTag("group_leaderboard_credits_button")
              ) {
                Icon(
                  imageVector = Icons.Default.Stars,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Points & Perks")
              }
            }
            if (onOpenGroupsTab != null) {
              FilledTonalButton(
                onClick = onOpenGroupsTab,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                modifier = Modifier
                  .weight(1f)
                  .heightIn(min = 48.dp)
                  .testTag("group_leaderboard_open_group_button")
              ) {
                Icon(
                  imageVector = Icons.Default.Groups,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Open $groupKindLabel")
              }
            }
          }
        }
      }
    }

    // 2. Empty or Podium + Full Standings
    if (standings.isEmpty()) {
      Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Icon(
            imageVector = Icons.Default.EmojiEvents,
            contentDescription = stringResource(R.string.cd_leaderboard_trophy),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(44.dp)
          )
          Text(
            text = "Loading ${activeGroup.name} Standings",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
          )
          Text(
            text = "Complete group tasks ahead of deadline to earn points and claim the #1 spot in your $groupKindLabel group.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
          )
        }
      }
    } else {
      GroupTopThreePodiumCard(
        groupKindLabel = groupKindLabel,
        standings = standings.take(3),
        currentUid = currentUserUid,
        pointsEnabled = pointsEnabled,
        numberFormatter = numberFormatter
      )

      Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("group_leaderboard_rankings_list")
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp, horizontal = 14.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(
                imageVector = Icons.Default.EmojiEvents,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
              )
              Text(
                text = "$groupKindLabel Standings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
              )
            }
            Surface(
              color = MaterialTheme.colorScheme.secondaryContainer,
              shape = RoundedCornerShape(50)
            ) {
              Text(
                text = "${standings.size} ${if (standings.size == 1) "member" else "members"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
              )
            }
          }

          HorizontalDivider(
            modifier = Modifier.padding(vertical = 2.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
          )

          standings.forEach { standing ->
            GroupStandingRow(
              standing = standing,
              isCurrentUser = standing.member.uid == currentUserUid,
              pointsEnabled = pointsEnabled,
              formattedPoints = formatPoints(standing.member.creditBalance, numberFormatter)
            )
          }
        }
      }
    }
  }
}

@Composable
private fun SignedOutGroupLeaderboardCard(
  isSigningIn: Boolean,
  onSignInClick: (() -> Unit)?,
  modifier: Modifier = Modifier
) {
  Card(
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ),
    modifier = modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(20.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .size(64.dp)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.primaryContainer)
      ) {
        Icon(
          imageVector = Icons.Default.EmojiEvents,
          contentDescription = stringResource(R.string.cd_leaderboard_trophy),
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(34.dp)
        )
      }
      Text(
        text = "Family & Workplace Leaderboard",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
      )
      Text(
        text = "Sign in with Google to join or create a Family or Workplace group and compete on your private group leaderboard.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
      )
      if (onSignInClick != null) {
        Button(
          onClick = onSignInClick,
          enabled = !isSigningIn,
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("group_leaderboard_sign_in_button")
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
      }
    }
  }
}

@Composable
private fun NoGroupLeaderboardCard(
  onOpenGroupsTab: (() -> Unit)?,
  modifier: Modifier = Modifier
) {
  Card(
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ),
    modifier = modifier
      .fillMaxWidth()
      .testTag("group_leaderboard_no_group_card")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(22.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .size(64.dp)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.secondaryContainer)
      ) {
        Icon(
          imageVector = Icons.Default.Groups,
          contentDescription = stringResource(R.string.cd_leaderboard_trophy),
          tint = MaterialTheme.colorScheme.onSecondaryContainer,
          modifier = Modifier.size(34.dp)
        )
      }
      Text(
        text = "No Family or Workplace Group Yet",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
      )
      Text(
        text = "Leaderboards are private to your Family or Workplace group. Create a group or accept an invite in the Group tab to rank members by points and completed tasks.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
      )
      if (onOpenGroupsTab != null) {
        Button(
          onClick = onOpenGroupsTab,
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("group_leaderboard_join_group_button")
        ) {
          Icon(
            imageVector = Icons.Default.Groups,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text("Go to Family & Workplace Groups")
        }
      }
    }
  }
}

@Composable
private fun GroupTopThreePodiumCard(
  groupKindLabel: String,
  standings: List<GroupLeaderboardStanding>,
  currentUid: String,
  pointsEnabled: Boolean,
  numberFormatter: NumberFormat
) {
  val first = standings.getOrNull(0)
  val second = standings.getOrNull(1)
  val third = standings.getOrNull(2)

  Card(
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("group_leaderboard_podium_card")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Icon(
          imageVector = Icons.Default.WorkspacePremium,
          contentDescription = stringResource(R.string.cd_leaderboard_trophy),
          tint = GoldMedalColor,
          modifier = Modifier.size(22.dp)
        )
        Text(
          text = "Top $groupKindLabel Champions",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
      ) {
        GroupPodiumSlot(
          rank = 2,
          standing = second,
          medalColor = SilverMedalColor,
          pedestalHeightDp = 52,
          avatarSizeDp = 46,
          isCurrentUser = second?.member?.uid == currentUid,
          pointsEnabled = pointsEnabled,
          numberFormatter = numberFormatter,
          modifier = Modifier.weight(1f)
        )
        GroupPodiumSlot(
          rank = 1,
          standing = first,
          medalColor = GoldMedalColor,
          pedestalHeightDp = 72,
          avatarSizeDp = 58,
          isCurrentUser = first?.member?.uid == currentUid,
          pointsEnabled = pointsEnabled,
          numberFormatter = numberFormatter,
          modifier = Modifier.weight(1.15f)
        )
        GroupPodiumSlot(
          rank = 3,
          standing = third,
          medalColor = BronzeMedalColor,
          pedestalHeightDp = 40,
          avatarSizeDp = 44,
          isCurrentUser = third?.member?.uid == currentUid,
          pointsEnabled = pointsEnabled,
          numberFormatter = numberFormatter,
          modifier = Modifier.weight(1f)
        )
      }
    }
  }
}

@Composable
private fun GroupPodiumSlot(
  rank: Int,
  standing: GroupLeaderboardStanding?,
  medalColor: Color,
  pedestalHeightDp: Int,
  avatarSizeDp: Int,
  isCurrentUser: Boolean,
  pointsEnabled: Boolean,
  numberFormatter: NumberFormat,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    if (standing != null) {
      val member = standing.member
      val label = member.displayName.ifBlank { member.email.substringBefore("@").ifBlank { "Member" } }
      if (rank == 1) {
        Icon(
          imageVector = Icons.Default.EmojiEvents,
          contentDescription = "First place trophy",
          tint = GoldMedalColor,
          modifier = Modifier.size(22.dp)
        )
      }
      ProfileAvatarBadge(
        fallbackInitial = label,
        size = avatarSizeDp.dp,
        modifier = Modifier.size(avatarSizeDp.dp)
      )
      Text(
        text = if (isCurrentUser) "$label (You)" else label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (rank == 1 || isCurrentUser) FontWeight.Bold else FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center
      )
      Text(
        text = if (pointsEnabled) {
          "${formatPoints(member.creditBalance, numberFormatter)} pts"
        } else {
          "${standing.completedTasksCount} done"
        },
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    } else {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .size(avatarSizeDp.dp)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f))
      ) {
        Text(
          text = "—",
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      Text(
        text = "Open Spot",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1
      )
    }

    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier
        .fillMaxWidth()
        .height(pedestalHeightDp.dp)
        .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 8.dp, bottomEnd = 8.dp))
        .background(
          brush = Brush.verticalGradient(
            colors = listOf(
              medalColor.copy(alpha = 0.34f),
              medalColor.copy(alpha = 0.14f)
            )
          )
        )
    ) {
      Text(
        text = "#$rank",
        style = if (rank == 1) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onSurface
      )
    }
  }
}

@Composable
private fun GroupStandingRow(
  standing: GroupLeaderboardStanding,
  isCurrentUser: Boolean,
  pointsEnabled: Boolean,
  formattedPoints: String
) {
  val rank = standing.rank
  val member = standing.member
  val displayName = member.displayName.ifBlank { member.email.substringBefore("@").ifBlank { "Group Member" } }
  val rankBadgeColor = when (rank) {
    1 -> GoldMedalColor.copy(alpha = 0.25f)
    2 -> SilverMedalColor.copy(alpha = 0.28f)
    3 -> BronzeMedalColor.copy(alpha = 0.25f)
    else -> MaterialTheme.colorScheme.surfaceContainerHighest
  }
  val rankTextColor = when (rank) {
    1 -> Color(0xFFB28704)
    2 -> MaterialTheme.colorScheme.onSurface
    3 -> Color(0xFF9A5B1F)
    else -> MaterialTheme.colorScheme.onSurfaceVariant
  }

  Surface(
    shape = RoundedCornerShape(18.dp),
    color = if (isCurrentUser) {
      MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    } else {
      MaterialTheme.colorScheme.surface
    },
    border = if (isCurrentUser) {
      BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
    } else {
      BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    },
    modifier = Modifier
      .fillMaxWidth()
      .testTag("group_leaderboard_row_$rank")
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .size(34.dp)
          .clip(CircleShape)
          .background(rankBadgeColor)
      ) {
        Text(
          text = "#$rank",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.ExtraBold,
          color = rankTextColor
        )
      }

      ProfileAvatarBadge(
        fallbackInitial = displayName,
        size = 42.dp,
        modifier = Modifier.size(42.dp)
      )

      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(2.dp)
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Text(
            text = displayName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (isCurrentUser) FontWeight.ExtraBold else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
          )
          if (isCurrentUser) {
            Surface(
              color = MaterialTheme.colorScheme.primary,
              shape = RoundedCornerShape(50)
            ) {
              Text(
                text = "You",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
              )
            }
          }
        }
        Text(
          text = buildString {
            append(member.parsedRole.label)
            append(" • ")
            append("${standing.completedTasksCount} ${if (standing.completedTasksCount == 1) "task" else "tasks"}")
          },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }

      Surface(
        color = if (!pointsEnabled || member.creditBalance >= 0L) {
          MaterialTheme.colorScheme.secondaryContainer
        } else {
          MaterialTheme.colorScheme.errorContainer
        },
        shape = RoundedCornerShape(12.dp)
      ) {
        Text(
          text = if (pointsEnabled) "$formattedPoints pts" else "${standing.completedTasksCount} done",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.ExtraBold,
          color = if (!pointsEnabled || member.creditBalance >= 0L) {
            MaterialTheme.colorScheme.onSecondaryContainer
          } else {
            MaterialTheme.colorScheme.onErrorContainer
          },
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
      }
    }
  }
}

private fun formatPoints(points: Long, formatter: NumberFormat): String {
  return formatter.format(points)
}
