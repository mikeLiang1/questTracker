package com.mikeliang.questtracker.ui.questdetail

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mikeliang.questtracker.core.engine.ConsistencyScore
import com.mikeliang.questtracker.core.model.Cadence
import com.mikeliang.questtracker.core.model.JournalEntry
import com.mikeliang.questtracker.core.model.Quest
import com.mikeliang.questtracker.core.model.QuestId
import com.mikeliang.questtracker.core.model.QuestKind
import com.mikeliang.questtracker.core.model.QuestStatus
import com.mikeliang.questtracker.core.model.QuestType
import com.mikeliang.questtracker.core.model.ReminderSchedule
import com.mikeliang.questtracker.ui.questlog.WriteEntrySheet
import dagger.hilt.android.lifecycle.withCreationCallback
import java.text.NumberFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Stateful entry point. The ViewModel is assisted-injected with the quest id via
 * Hilt's creation callback — plain `viewModel(key = …)`, no navigation artifact —
 * and keyed per quest so revisiting different quests never crosses state.
 */
@Composable
fun QuestDetailScreen(questId: QuestId, onClose: () -> Unit) {
    val owner = checkNotNull(LocalViewModelStoreOwner.current)
    val extras = if (owner is HasDefaultViewModelProviderFactory) {
        owner.defaultViewModelCreationExtras.withCreationCallback<QuestDetailViewModel.Factory> {
            it.create(questId.value)
        }
    } else {
        CreationExtras.Empty
    }
    val viewModel: QuestDetailViewModel = viewModel(
        viewModelStoreOwner = owner,
        key = "quest-detail-${questId.value}",
        extras = extras,
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.closed) {
        if (state.closed) onClose()
    }

    QuestDetailContent(state = state, onEvent = viewModel::onEvent, onBack = onClose)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestDetailContent(
    state: QuestDetailUiState,
    onEvent: (QuestDetailEvent) -> Unit,
    onBack: () -> Unit,
) {
    val quest = state.quest
    var showEditSheet by remember { mutableStateOf(false) }
    var showEscalateDialog by remember { mutableStateOf(false) }
    var showRetireConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<JournalEntry?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(quest?.title.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Retired quests are finished chapters: read-only, no pencil.
                    if (quest?.status == QuestStatus.Active) {
                        IconButton(onClick = { showEditSheet = true }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit quest")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                state.loading || quest == null ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }

                else -> DetailList(
                    quest = quest,
                    state = state,
                    onEscalate = { showEscalateDialog = true },
                    onRetire = { showRetireConfirm = true },
                    onDelete = { showDeleteConfirm = true },
                    onOpenEntry = { editingEntry = it },
                )
            }
        }
    }

    if (showEditSheet && quest != null) {
        QuestEditSheet(
            quest = quest,
            onDismiss = { showEditSheet = false },
            onEvent = { event ->
                showEditSheet = false
                onEvent(event)
            },
        )
    }

    editingEntry?.let { entry ->
        WriteEntrySheet(
            onDismiss = { editingEntry = null },
            onSave = { text, _ ->
                editingEntry = null
                onEvent(QuestDetailEvent.EditJournalEntry(entry.id, text))
            },
            initialText = entry.text,
            onDelete = {
                editingEntry = null
                onEvent(QuestDetailEvent.DeleteJournalEntry(entry.id))
            },
        )
    }

    if (showEscalateDialog && quest != null) {
        val progression = (quest.kind as? QuestKind.Recurring)?.progression
        if (progression != null) {
            EscalateDialog(
                currentAmount = progression.amount,
                unit = progression.unit,
                onConfirm = { newAmount ->
                    showEscalateDialog = false
                    onEvent(QuestDetailEvent.Escalate(newAmount))
                },
                onDismiss = { showEscalateDialog = false },
            )
        }
    }

    if (showRetireConfirm && quest != null) {
        AlertDialog(
            onDismissRequest = { showRetireConfirm = false },
            title = { Text("Retire this quest?") },
            text = {
                Text(
                    "It moves to your completed chapters. Everything you banked stays — " +
                        "every completion, every point."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRetireConfirm = false
                    onEvent(QuestDetailEvent.Retire)
                }) { Text("Retire") }
            },
            dismissButton = {
                TextButton(onClick = { showRetireConfirm = false }) { Text("Cancel") }
            },
        )
    }

    if (showDeleteConfirm && quest != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this quest?") },
            text = { Text("It has no completions, so nothing is lost.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onEvent(QuestDetailEvent.Delete)
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun DetailList(
    quest: Quest,
    state: QuestDetailUiState,
    onEscalate: () -> Unit,
    onRetire: () -> Unit,
    onDelete: () -> Unit,
    onOpenEntry: (JournalEntry) -> Unit,
) {
    val kind = quest.kind as? QuestKind.Recurring
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { IdentityCard(quest, kind) }
        item { EvidenceCard(state, kind) }
        item { ReminderCard(quest.reminder) }
        if (state.journalEntries.isNotEmpty()) {
            item {
                Text(
                    text = "Journal",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(state.journalEntries, key = { it.id.value }) { entry ->
                JournalEntryCard(entry, onOpenEntry)
            }
        }
        if (kind?.type == QuestType.Progression && quest.status == QuestStatus.Active) {
            item { ProgressionCard(kind, onEscalate) }
        }
        if (quest.status == QuestStatus.Active) {
            item {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    OutlinedButton(onClick = onRetire, modifier = Modifier.fillMaxWidth()) {
                        Text("Retire quest")
                    }
                    // Rendered only for zero-completion mis-creations; anything with
                    // history retires — the affordance simply isn't shown.
                    if (state.canDelete) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                            Text("Delete quest")
                        }
                    }
                }
            }
        }
    }
}

/**
 * One journal entry written toward this quest — the entry's home now that
 * quest-scoped entries stay off the main timeline. Tap to edit or delete; either
 * touches only the entry, never the completion it banked.
 */
@Composable
private fun JournalEntryCard(entry: JournalEntry, onOpenEntry: (JournalEntry) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenEntry(entry) },
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(entry.text, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = buildString {
                    append(entryStamp.format(entry.createdAt.atZone(ZoneId.systemDefault())))
                    if (entry.editedAt != null) append(" · edited")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val entryStamp = DateTimeFormatter.ofPattern("MMM d, h:mm a")

@Composable
private fun IdentityCard(quest: Quest, kind: QuestKind.Recurring?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(quest.title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (kind != null) {
                    DetailBadge(kind.cadence.name)
                    DetailBadge(kind.type.name)
                    DetailBadge(kind.attribute.name)
                } else {
                    DetailBadge("Side quest")
                }
                if (quest.status == QuestStatus.Retired) DetailBadge("Retired")
            }
        }
    }
}

/**
 * The evidence behind the quest. Lifetime completions lead — the number that can
 * never break — and consistency follows with its absorbed rest framed neutrally.
 * No red, no "broken", nothing to lose.
 */
@Composable
private fun EvidenceCard(state: QuestDetailUiState, kind: QuestKind.Recurring?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Lifetime completions",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${state.lifetimeCompletions}",
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            val consistency = state.consistency
            if (kind != null && consistency != null && consistency.evaluatedPeriods > 0) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Consistency",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${(consistency.rate * 100).toInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                if (consistency.absorbedRestPeriods > 0) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = restLine(consistency, kind.cadence),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun restLine(consistency: ConsistencyScore, cadence: Cadence): String {
    val noun = when (cadence) {
        Cadence.Daily -> "day"
        Cadence.Weekly -> "week"
        Cadence.Monthly -> "month"
    }
    val count = consistency.absorbedRestPeriods
    return if (count == 1) "1 rest $noun absorbed" else "$count rest ${noun}s absorbed"
}

@Composable
private fun ReminderCard(reminder: ReminderSchedule?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("Reminder", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                text = reminderLine(reminder),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun reminderLine(reminder: ReminderSchedule?): String = when (reminder) {
    null -> "No reminder"
    is ReminderSchedule.Recurring -> {
        val time = reminder.time.format(DateTimeFormatter.ofPattern("h:mm a"))
        val days = if (reminder.days.size == 7) {
            "every day"
        } else {
            reminder.days.sorted()
                .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
        }
        "$time · $days"
    }
    is ReminderSchedule.OneShot ->
        reminder.at.format(DateTimeFormatter.ofPattern("MMM d, h:mm a"))
}

@Composable
private fun ProgressionCard(kind: QuestKind.Recurring, onEscalate: () -> Unit) {
    val progression = kind.progression ?: return
    val format = remember { NumberFormat.getInstance() }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("Target", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${format.format(progression.amount)} ${progression.unit} · level ${progression.escalationLevel}",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                // Offered, never suggested: no nudge, no highlight, the user picks.
                TextButton(onClick = onEscalate) { Text("Change target…") }
            }
        }
    }
}

@Composable
private fun EscalateDialog(
    currentAmount: Double,
    unit: String,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var amountText by remember { mutableStateOf("") }
    val newAmount = amountText.toDoubleOrNull()?.takeIf { it > 0 }
    val format = remember { NumberFormat.getInstance() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change the target") },
        text = {
            Column {
                Text(
                    "Currently ${format.format(currentAmount)} $unit. Raising or lowering " +
                        "both count — a fresh level restores the full rate either way."
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("New amount ($unit)") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { newAmount?.let(onConfirm) },
                enabled = newAmount != null,
            ) { Text("Set target") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun DetailBadge(label: String) {
    SuggestionChip(onClick = {}, enabled = false, label = {
        Text(label, style = MaterialTheme.typography.labelSmall)
    })
}
