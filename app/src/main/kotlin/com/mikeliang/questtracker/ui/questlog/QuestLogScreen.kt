package com.mikeliang.questtracker.ui.questlog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mikeliang.questtracker.core.engine.QuestLogItem
import com.mikeliang.questtracker.core.model.JournalEntry
import com.mikeliang.questtracker.core.model.QuestId
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Stateful entry point: hooks the ViewModel up to the stateless content. [onOpenQuest]
 * carries the day the tapped row sits under, so detail can scope its journal to it.
 */
@Composable
fun QuestLogScreen(
    onOpenQuest: (QuestId, LocalDate) -> Unit = { _, _ -> },
    viewModel: QuestLogViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    QuestLogContent(state = state, onEvent = viewModel::onEvent, onOpenQuest = onOpenQuest)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestLogContent(
    state: QuestLogUiState,
    onEvent: (QuestLogEvent) -> Unit,
    // Screen-level navigation, not a ViewModel event: opening detail changes no state.
    onOpenQuest: (QuestId, LocalDate) -> Unit = { _, _ -> },
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showWrite by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<JournalEntry?>(null) }

    val feedback = state.feedback
    LaunchedEffect(feedback) {
        if (feedback != null) {
            snackbarHostState.showSnackbar(feedback.message)
            onEvent(QuestLogEvent.FeedbackShown)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Quest Log") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showWrite = true }) {
                Icon(Icons.Filled.Edit, contentDescription = "Write an entry")
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                state.loading -> LoadingState()
                state.isEmpty -> EmptyState()
                else -> Timeline(state, onOpenQuest, onOpenEntry = { editingEntry = it })
            }
        }
    }

    if (showWrite) {
        WriteEntrySheet(
            onDismiss = { showWrite = false },
            onSave = { text, countToward ->
                showWrite = false
                onEvent(QuestLogEvent.SaveEntry(text, countToward))
            },
            linkedQuests = state.linkedOptions,
        )
    }

    editingEntry?.let { entry ->
        WriteEntrySheet(
            onDismiss = { editingEntry = null },
            onSave = { text, _ ->
                editingEntry = null
                onEvent(QuestLogEvent.EditEntry(entry.id, text))
            },
            initialText = entry.text,
            onDelete = {
                editingEntry = null
                onEvent(QuestLogEvent.DeleteEntry(entry.id))
            },
        )
    }
}

@Composable
private fun Timeline(
    state: QuestLogUiState,
    onOpenQuest: (QuestId, LocalDate) -> Unit,
    onOpenEntry: (JournalEntry) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
    ) {
        state.days.forEach { day ->
            item(key = "day-${day.date}") {
                Text(
                    text = dayLabel(day.date, state.today),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                )
            }
            items(day.items, key = { it.key }) { item ->
                when (item) {
                    is QuestLogItem.Entry -> EntryCard(item, onOpenEntry)
                    is QuestLogItem.Completion ->
                        // The day comes from the group, not the record: it is the day
                        // the user tapped, and the day whose journal detail will show.
                        CompletionRow(item) { onOpenQuest(item.record.questId, day.date) }
                }
            }
        }
    }
}

/** Stable LazyColumn key: entries by id, completions by quest + moment. */
private val QuestLogItem.key: String
    get() = when (this) {
        is QuestLogItem.Entry -> "entry-${entry.id.value}"
        is QuestLogItem.Completion -> "completion-${record.questId.value}-${record.completedAt.toEpochMilli()}"
    }

/**
 * A written entry: the user's own words carry the card; metadata stays quiet.
 * Tapping opens the edit sheet — entries are the one editable thing in the app.
 * When the entry counted toward a quest, a quiet "counted toward …" line names it —
 * the same writing also reads on that quest's detail screen for the day.
 */
@Composable
private fun EntryCard(item: QuestLogItem.Entry, onOpenEntry: (JournalEntry) -> Unit) {
    val entry = item.entry
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onOpenEntry(entry) },
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = entry.text,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (item.linkedQuestTitles.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Counted toward ${item.linkedQuestTitles.joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = buildString {
                    append(timeOf(entry.createdAt))
                    if (entry.editedAt != null) append(" · edited")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A banked completion: quieter than entries — evidence, not writing. */
@Composable
private fun CompletionRow(item: QuestLogItem.Completion, onOpen: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onOpen),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.questTitle ?: "A completed quest",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = buildString {
                        append(timeOf(item.record.completedAt))
                        item.attribute?.let { append(" · ${it.name}") }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyState() {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Nothing logged yet",
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Completions and anything you write gather here, day by day.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")
private val sameYearFormatter = DateTimeFormatter.ofPattern("MMMM d")
private val otherYearFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy")

private fun timeOf(at: Instant): String =
    timeFormatter.format(at.atZone(ZoneId.systemDefault()).toLocalTime())

private fun dayLabel(date: LocalDate, today: LocalDate?): String = when {
    date == today -> "Today"
    today != null && date == today.minusDays(1) -> "Yesterday"
    today != null && date.year == today.year -> sameYearFormatter.format(date)
    else -> otherYearFormatter.format(date)
}
