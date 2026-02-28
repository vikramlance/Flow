package com.flow.presentation.achievements

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.flow.data.local.AchievementEntity
import com.flow.data.local.AchievementType
import com.flow.ui.theme.NeonGreen
import com.flow.ui.theme.SurfaceDark
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * T013 / C-004 — Dedicated Achievements screen.
 *
 * Shows earned badges, hidden placeholders ("???"), greyed-out unearned visible
 * achievements, and an expandable "How Achievements Work" section.
 *
 * The screen is NEVER empty — unearned visible placeholders fill the list when
 * no badges have been earned yet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(
    onBack: () -> Unit,
    viewModel: AchievementsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val earnedTypes = uiState.earned.map { it.type }.toSet()
    val earnedSorted = uiState.earned.sortedByDescending { it.earnedAt }

    Scaffold(
        containerColor = SurfaceDark,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Achievements",
                        style     = MaterialTheme.typography.titleLarge,
                        color     = NeonGreen,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint               = NeonGreen
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier            = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding      = PaddingValues(bottom = 24.dp, top = 8.dp)
        ) {
            // ── Earned badges ──────────────────────────────────────────────
            if (earnedSorted.isNotEmpty()) {
                item {
                    Text(
                        "Earned",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.Gray
                    )
                }
                items(earnedSorted, key = { it.id }) { ach ->
                    EarnedBadgeCard(ach)
                }
            }

            // ── Hidden placeholders (HIDDEN types not yet earned) ──────────
            val hiddenUnearned = AchievementType.values()
                .filter { type ->
                    AchievementMeta.visibilityOf(type) == AchievementVisibility.HIDDEN &&
                            type !in earnedTypes
                }
            if (hiddenUnearned.isNotEmpty()) {
                items(hiddenUnearned, key = { "hidden_${it.name}" }) {
                    HiddenPlaceholderCard()
                }
            }

            // ── Visible unearned rows ──────────────────────────────────────
            val visibleUnearned = AchievementType.values()
                .filter { type ->
                    AchievementMeta.visibilityOf(type) == AchievementVisibility.VISIBLE &&
                            type !in earnedTypes
                }
            if (visibleUnearned.isNotEmpty()) {
                item {
                    Text(
                        "Not yet earned",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.Gray
                    )
                }
                items(visibleUnearned, key = { "unearned_${it.name}" }) { type ->
                    VisibleUnearnedRow(type)
                }
            }

            // ── How Achievements Work (expandable) ─────────────────────────
            item {
                HowItWorksSection(
                    expanded = uiState.isHowItWorksExpanded,
                    onToggle = { viewModel.toggleHowItWorks() }
                )
            }
        }
    }
}

// ── Earned badge card ──────────────────────────────────────────────────────────

@Composable
private fun EarnedBadgeCard(achievement: AchievementEntity) {
    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    Card(
        shape  = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = NeonGreen.copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier              = Modifier.padding(16.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text  = AchievementMeta.achievementEmoji(achievement.type),
                style = MaterialTheme.typography.headlineMedium
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = AchievementMeta.achievementName(achievement.type),
                    style      = MaterialTheme.typography.titleSmall,
                    color      = NeonGreen,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text  = AchievementMeta.descriptions[achievement.type] ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text  = "Earned ${sdf.format(Date(achievement.earnedAt))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
                achievement.periodLabel?.let { label ->
                    Text(
                        text  = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

// ── Hidden placeholder card ────────────────────────────────────────────────────

@Composable
private fun HiddenPlaceholderCard() {
    Card(
        shape  = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier              = Modifier.padding(16.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text  = "???",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.Gray
            )
            Column {
                Text(
                    text       = "???",
                    style      = MaterialTheme.typography.titleSmall,
                    color      = Color.Gray,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text  = "Keep going!",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}

// ── Visible unearned row ───────────────────────────────────────────────────────

@Composable
private fun VisibleUnearnedRow(type: AchievementType) {
    Card(
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier              = Modifier.padding(16.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text  = AchievementMeta.achievementEmoji(type),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.Gray
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = AchievementMeta.achievementName(type),
                    style      = MaterialTheme.typography.titleSmall,
                    color      = Color.Gray,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text  = "Not yet earned",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}

// ── How Achievements Work section ─────────────────────────────────────────────

@Composable
private fun HowItWorksSection(
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Card(
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.07f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text       = "How Achievements Work",
                    style      = MaterialTheme.typography.titleSmall,
                    color      = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = onToggle) {
                    Icon(
                        imageVector        = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint               = NeonGreen
                    )
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier            = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Only list VISIBLE types (hidden types are secret)
                    AchievementType.values()
                        .filter { AchievementMeta.visibilityOf(it) == AchievementVisibility.VISIBLE }
                        .forEach { type ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment     = Alignment.Top
                            ) {
                                Text(
                                    text  = AchievementMeta.achievementEmoji(type),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Column {
                                    Text(
                                        text       = AchievementMeta.achievementName(type),
                                        style      = MaterialTheme.typography.bodySmall,
                                        color      = NeonGreen,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text  = AchievementMeta.descriptions[type] ?: "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                }
            }
        }
    }
}
