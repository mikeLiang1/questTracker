package com.mikeliang.questtracker.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mikeliang.questtracker.core.engine.AttributeCard
import com.mikeliang.questtracker.core.engine.CompletedChapter
import com.mikeliang.questtracker.core.model.Attribute
import com.mikeliang.questtracker.core.model.Cadence
import com.mikeliang.questtracker.core.model.Quest
import com.mikeliang.questtracker.core.model.QuestId
import com.mikeliang.questtracker.core.model.QuestKind
import com.mikeliang.questtracker.core.model.QuestStatus
import com.mikeliang.questtracker.core.model.QuestType
import com.mikeliang.questtracker.ui.theme.QuestTrackerTheme
import java.time.Instant

/** Stateful entry point: hooks the ViewModel up to the stateless content. */
@Composable
fun ProfileScreen(viewModel: ProfileViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProfileContent(state = state)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileContent(state: ProfileUiState) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Profile") }) },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (state.loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                ProfileList(state)
            }
        }
    }
}

@Composable
private fun ProfileList(state: ProfileUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.attributes, key = { it.attribute.name }) { card ->
            AttributeCardRow(card)
        }

        item { SectionHeader("Lifetime") }
        item { LifetimeRow(state.lifetimeCompletions) }

        if (state.chapters.isNotEmpty()) {
            item { SectionHeader("Completed chapters") }
            items(state.chapters, key = { it.quest.id.value }) { chapter ->
                ChapterRow(chapter)
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

/**
 * One attribute: earned title, progress toward the next milestone, and the evidence
 * behind it. Every number here only ever grows — there is nothing to lose, so there
 * is nothing to warn about.
 */
@Composable
private fun AttributeCardRow(card: AttributeCard) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${card.title} — ${card.attribute}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                // The evidence count, not a score: it can only ever go up.
                Text(
                    text = "${"%.0f".format(card.points)} pts",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { card.progressToNextRank.toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = nextMilestoneLine(card),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            // Copy comes verbatim from :core — the untouched-attribute wording is
            // deliberately factual, and the UI must not editorialize it.
            Text(
                text = card.evidence,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun nextMilestoneLine(card: AttributeCard): String {
    val noun = if (card.completionsToNextRank == 1) "completion" else "completions"
    return "${card.completionsToNextRank} more $noun to ${card.nextTitle}"
}

@Composable
private fun LifetimeRow(lifetimeCompletions: Int) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Total completions",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$lifetimeCompletions",
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}

/** A retired quest: a finished chapter, framed as an achievement, never a loss. */
@Composable
private fun ChapterRow(chapter: CompletedChapter) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = chapter.quest.title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (chapter.completions == 1) "1 completion" else "${chapter.completions} completions",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// --- Previews -------------------------------------------------------------

private fun previewCard(
    attribute: Attribute,
    rank: Int,
    title: String,
    points: Double,
    completions: Int,
    nextTitle: String,
    completionsToNextRank: Int,
    progressToNextRank: Double,
    evidence: String,
) = AttributeCard(
    attribute = attribute,
    rank = rank,
    title = title,
    points = points,
    completions = completions,
    nextTitle = nextTitle,
    completionsToNextRank = completionsToNextRank,
    progressToNextRank = progressToNextRank,
    evidence = evidence,
)

private fun untouched(attribute: Attribute) = previewCard(
    attribute = attribute,
    rank = 0,
    title = "Unwritten",
    points = 0.0,
    completions = 0,
    nextTitle = "Awakened",
    completionsToNextRank = 5,
    progressToNextRank = 0.0,
    evidence = "No quests feed $attribute yet",
)

private val freshUser = ProfileUiState(
    attributes = Attribute.entries.map { untouched(it) },
)

private val balancedUser = ProfileUiState(
    attributes = listOf(
        previewCard(Attribute.Body, 3, "Consistent", 34.0, 34, "Established", 16, 0.2,
            "34 completions over 8 weeks"),
        previewCard(Attribute.Mind, 2, "Committed", 21.0, 21, "Consistent", 9, 0.4,
            "21 completions over 6 weeks"),
        previewCard(Attribute.Social, 2, "Committed", 18.0, 6, "Consistent", 4, 0.2,
            "6 completions over 6 weeks"),
        previewCard(Attribute.Discipline, 3, "Consistent", 31.0, 31, "Established", 19, 0.05,
            "31 completions over 8 weeks"),
    ),
    lifetimeCompletions = 97,
    chapters = listOf(
        CompletedChapter(
            quest = Quest(
                id = QuestId("c2k"),
                title = "Couch to 5k",
                kind = QuestKind.Recurring(Cadence.Weekly, QuestType.Maintenance, Attribute.Body),
                createdAt = Instant.parse("2026-01-01T00:00:00Z"),
                status = QuestStatus.Retired,
            ),
            completions = 9,
        ),
    ),
)

/** The lopsided case: high Body, untouched Social — information, never shame. */
private val lopsidedUser = ProfileUiState(
    attributes = listOf(
        previewCard(Attribute.Body, 5, "Relentless", 82.0, 82, "Exemplar", 23, 0.23,
            "82 completions over 4 months"),
        previewCard(Attribute.Mind, 1, "Awakened", 8.0, 8, "Committed", 7, 0.3,
            "8 completions over 3 weeks"),
        untouched(Attribute.Social),
        untouched(Attribute.Discipline),
    ),
    lifetimeCompletions = 90,
)

@Preview(showBackground = true)
@Composable
private fun FreshUserPreview() {
    QuestTrackerTheme { ProfileContent(state = freshUser) }
}

@Preview(showBackground = true)
@Composable
private fun BalancedUserPreview() {
    QuestTrackerTheme { ProfileContent(state = balancedUser) }
}

@Preview(showBackground = true)
@Composable
private fun LopsidedUserPreview() {
    QuestTrackerTheme { ProfileContent(state = lopsidedUser) }
}

@Preview(showBackground = true)
@Composable
private fun LoadingPreview() {
    QuestTrackerTheme { ProfileContent(state = ProfileUiState(loading = true)) }
}
