package com.mikeliang.questtracker.ui.questlist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mikeliang.questtracker.core.health.HealthMetric
import com.mikeliang.questtracker.core.model.Attribute
import com.mikeliang.questtracker.core.model.AutoTracking
import com.mikeliang.questtracker.core.model.Cadence
import com.mikeliang.questtracker.core.model.Quest
import com.mikeliang.questtracker.core.model.QuestId
import com.mikeliang.questtracker.core.model.QuestKind
import com.mikeliang.questtracker.core.model.QuestType
import com.mikeliang.questtracker.ui.theme.QuestTrackerTheme
import java.text.NumberFormat
import java.time.Instant

/** Stateful entry point: hooks the ViewModel up to the stateless content. */
@Composable
fun QuestListScreen(
    onOpenQuest: (QuestId) -> Unit = {},
    onOpenReflection: () -> Unit = {},
    viewModel: QuestListViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    QuestListContent(
        state = state,
        onEvent = viewModel::onEvent,
        onOpenQuest = onOpenQuest,
        onOpenReflection = onOpenReflection,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestListContent(
    state: QuestListUiState,
    onEvent: (QuestListEvent) -> Unit,
    // Screen-level navigation, not a ViewModel event: opening detail changes no state.
    onOpenQuest: (QuestId) -> Unit = {},
    onOpenReflection: () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showQuickAdd by remember { mutableStateOf(false) }

    // Identity-framed copy straight from :core — shown once, then cleared.
    val feedback = state.feedback
    LaunchedEffect(feedback) {
        if (feedback != null) {
            snackbarHostState.showSnackbar(feedback.message)
            onEvent(QuestListEvent.FeedbackShown)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Today") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showQuickAdd = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add quest")
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                state.loading -> LoadingState()
                state.isEmpty -> EmptyState()
                else -> QuestBoard(state, onEvent, onOpenQuest, onOpenReflection)
            }
        }
    }

    if (showQuickAdd) {
        QuickAddSheet(
            onDismiss = { showQuickAdd = false },
            onEvent = { event ->
                showQuickAdd = false
                onEvent(event)
            },
        )
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
                text = "No quests yet",
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Tap + to capture your first one.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * The monthly reflection's only trigger — an invitation, not an interruption. Copy
 * frames it as something owned ("your month"), never as an obligation or a nag.
 */
@Composable
private fun ReflectionBanner(onOpen: () -> Unit) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "A month is on the books",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Take 90 seconds to look back at your month and adjust the board.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The calm end-of-day banner. Recurring work is done; deliberately no suggestion to
 * do more. The caller keeps the cleared quests listed beneath it — banked gains stay
 * visible — along with any open side quests (life admin, not part of "done").
 */
@Composable
private fun DoneForToday() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Done for today",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Every quest is banked. Rest is part of the work.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun QuestBoard(
    state: QuestListUiState,
    onEvent: (QuestListEvent) -> Unit,
    onOpenQuest: (QuestId) -> Unit,
    onOpenReflection: () -> Unit = {},
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Surfaced, never forced: a tap opens the reflection, ignoring it costs
        // nothing, and completing or skipping it drops the banner until next month.
        if (state.reflectionDue) {
            item { ReflectionBanner(onOpen = onOpenReflection) }
        }
        // The banner celebrates; it never hides what was cleared. Swapping the list
        // out for the banner made clearing the last quest look like it deleted the
        // rest of the board.
        if (state.doneForToday) {
            item { DoneForToday() }
        }
        items(state.activeRecurring, key = { it.quest.id.value }) { item ->
            RecurringQuestRow(
                item = item,
                onComplete = { onEvent(QuestListEvent.CompleteQuest(item.quest.id)) },
                onUnclear = { onEvent(QuestListEvent.UnclearQuest(item.quest.id)) },
                onOpen = { onOpenQuest(item.quest.id) },
            )
        }

        // Cleared quests always sit in their own section, mid-day included — ticking
        // a quest visibly moves it out of the active list instead of leaving it mixed
        // in, while the banked gain stays on screen.
        if (state.clearedRecurring.isNotEmpty()) {
            item {
                Text(
                    text = "Cleared today",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        top = if (state.activeRecurring.isEmpty()) 0.dp else 16.dp,
                        bottom = 4.dp,
                    ),
                )
            }
            items(state.clearedRecurring, key = { it.quest.id.value }) { item ->
                RecurringQuestRow(
                    item = item,
                    onComplete = { onEvent(QuestListEvent.CompleteQuest(item.quest.id)) },
                    onUnclear = { onEvent(QuestListEvent.UnclearQuest(item.quest.id)) },
                    onOpen = { onOpenQuest(item.quest.id) },
                )
            }
        }

        if (state.sideQuests.isNotEmpty()) {
            item {
                Text(
                    text = "Side Quests",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
            }
            items(state.sideQuests, key = { it.quest.id.value }) { item ->
                SideQuestRow(
                    item = item,
                    onComplete = { onEvent(QuestListEvent.CompleteQuest(item.quest.id)) },
                    onUnclear = { onEvent(QuestListEvent.UnclearQuest(item.quest.id)) },
                    onOpen = { onOpenQuest(item.quest.id) },
                )
            }
        }
    }
}

@Composable
private fun RecurringQuestRow(
    item: QuestListUiState.RecurringItem,
    onComplete: () -> Unit,
    onUnclear: () -> Unit = {},
    onOpen: () -> Unit = {},
) {
    val kind = item.quest.kind as QuestKind.Recurring
    // Tap opens detail (the convention every list app trains); a horizontal swipe
    // in either direction clears — or un-clears during the same-day undo window.
    // The tick stays tappable too, as the visible/accessible fallback.
    SwipeToClear(
        enabled = !item.completed || item.undoable,
        onToggle = if (item.completed) onUnclear else onComplete,
    ) {
        Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.quest.title,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            QuestBadge(kind.cadence.name)
                            QuestBadge(kind.type.name)
                            QuestBadge(kind.attribute.name)
                        }
                    }
                    CompletionTick(
                        completed = item.completed,
                        undoable = item.undoable,
                        onComplete = onComplete,
                        onUnclear = onUnclear,
                    )
                }
                item.progress?.let { progress ->
                    Spacer(Modifier.height(8.dp))
                    AutoProgressBar(progress)
                }
            }
        }
    }
}

@Composable
private fun SideQuestRow(
    item: QuestListUiState.SideQuestItem,
    onComplete: () -> Unit,
    onUnclear: () -> Unit = {},
    onOpen: () -> Unit = {},
) {
    // Visually lighter than recurring rows: a tick and a lifetime credit, never
    // attribute badges — side quests are not growth. Same gesture model as
    // recurring rows: tap opens, swipe clears.
    SwipeToClear(
        enabled = !item.completed || item.undoable,
        onToggle = if (item.completed) onUnclear else onComplete,
    ) {
        Card(
            onClick = onOpen,
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
                    text = item.quest.title,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                CompletionTick(
                    completed = item.completed,
                    undoable = item.undoable,
                    onComplete = onComplete,
                    onUnclear = onUnclear,
                )
            }
        }
    }
}

/**
 * Horizontal swipe in either direction clears a quest — or un-clears it during the
 * same-day undo window. The row is never actually dismissed: the veto below snaps
 * it back, and the completion change re-sorts it into its new section — so the
 * "swipe = done" gesture reads true without ever hiding the banked gain.
 */
@Composable
private fun SwipeToClear(
    enabled: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    // The swipe state survives the row moving between board sections (same
    // LazyColumn key), so read the latest lambda rather than the one captured
    // when the state was first remembered.
    val currentOnToggle by rememberUpdatedState(onToggle)
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) currentOnToggle()
            false
        },
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = enabled,
        enableDismissFromEndToStart = enabled,
        backgroundContent = { SwipeClearBackground(state) },
    ) {
        content()
    }
}

/** Calm check reveal behind a swiping row — clearing is a win, never a deletion. */
@Composable
private fun SwipeClearBackground(state: SwipeToDismissBoxState) {
    val alignment = when (state.dismissDirection) {
        SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
        else -> Alignment.CenterEnd
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(CardDefaults.shape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 24.dp),
        contentAlignment = alignment,
    ) {
        Icon(
            Icons.Filled.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun CompletionTick(
    completed: Boolean,
    undoable: Boolean = false,
    onComplete: () -> Unit,
    onUnclear: () -> Unit = {},
) {
    // RadioButton reads as a calm "done" dot; also the tap fallback for anyone who
    // doesn't discover the swipe. Tapping again the same day undoes a mis-tap; once
    // the day rolls over the gain is banked and the tick locks (gains are permanent).
    val enabled = !completed || undoable
    val onClick = if (completed) onUnclear else onComplete
    IconButton(onClick = onClick, enabled = enabled) {
        RadioButton(selected = completed, onClick = onClick, enabled = enabled)
    }
}

@Composable
private fun QuestBadge(label: String) {
    // Deliberately not a chip: a disabled SuggestionChip still consumes taps, which
    // left the badge strip as a dead zone inside an otherwise fully tappable card.
    Surface(
        shape = MaterialTheme.shapes.small,
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun AutoProgressBar(progress: QuestListUiState.AutoProgress) {
    val format = remember { NumberFormat.getIntegerInstance() }
    val current = progress.current
    Column {
        LinearProgressIndicator(
            progress = {
                if (current == null) 0f
                else (current / progress.target).toFloat().coerceIn(0f, 1f)
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = buildString {
                append(if (current == null) "—" else format.format(current.toLong()))
                append(" / ")
                append(format.format(progress.target.toLong()))
                append(" ")
                append(progress.metric.unitLabel())
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun HealthMetric.unitLabel(): String = when (this) {
    HealthMetric.Steps -> "steps"
    HealthMetric.DistanceMeters -> "m"
    HealthMetric.Floors -> "floors"
    HealthMetric.ActiveEnergyKcal -> "kcal"
    HealthMetric.ExerciseMinutes -> "min"
    HealthMetric.SleepMinutes -> "min slept"
}

// --- Previews -------------------------------------------------------------

private fun previewQuest(
    id: String,
    title: String,
    kind: QuestKind,
    autoTracking: AutoTracking? = null,
) = Quest(
    id = QuestId(id),
    title = title,
    kind = kind,
    createdAt = Instant.parse("2026-01-01T00:00:00Z"),
    autoTracking = autoTracking,
)

private val previewListState = QuestListUiState(
    recurring = listOf(
        QuestListUiState.RecurringItem(
            quest = previewQuest(
                "q1", "10k steps",
                QuestKind.Recurring(Cadence.Daily, QuestType.Progression, Attribute.Body,
                    progression = com.mikeliang.questtracker.core.model.ProgressionTarget(8000.0, "steps")),
                autoTracking = AutoTracking(HealthMetric.Steps, 8000.0),
            ),
            completed = false,
            progress = QuestListUiState.AutoProgress(HealthMetric.Steps, 5200.0, 8000.0),
        ),
        QuestListUiState.RecurringItem(
            quest = previewQuest(
                "q2", "Read 20 pages",
                QuestKind.Recurring(Cadence.Daily, QuestType.Maintenance, Attribute.Mind),
            ),
            completed = true,
            progress = null,
            undoable = true,
        ),
        QuestListUiState.RecurringItem(
            quest = previewQuest(
                "q3", "Call parents",
                QuestKind.Recurring(Cadence.Weekly, QuestType.Maintenance, Attribute.Social),
            ),
            completed = false,
            progress = null,
        ),
    ),
    sideQuests = listOf(
        QuestListUiState.SideQuestItem(previewQuest("s1", "Book dentist", QuestKind.SideQuest), completed = false),
        QuestListUiState.SideQuestItem(previewQuest("s2", "Renew rego", QuestKind.SideQuest), completed = true, undoable = true),
    ),
)

@Preview(showBackground = true)
@Composable
private fun QuestListPreview() {
    QuestTrackerTheme { QuestListContent(state = previewListState, onEvent = {}) }
}

@Preview(showBackground = true)
@Composable
private fun DoneForTodayPreview() {
    QuestTrackerTheme {
        QuestListContent(
            state = previewListState.copy(
                recurring = previewListState.recurring.map { it.copy(completed = true) },
                sideQuests = previewListState.sideQuests.filter { !it.completed },
                doneForToday = true,
            ),
            onEvent = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LoadingPreview() {
    QuestTrackerTheme { QuestListContent(state = QuestListUiState(loading = true), onEvent = {}) }
}

@Preview(showBackground = true)
@Composable
private fun EmptyPreview() {
    QuestTrackerTheme { QuestListContent(state = QuestListUiState(), onEvent = {}) }
}
