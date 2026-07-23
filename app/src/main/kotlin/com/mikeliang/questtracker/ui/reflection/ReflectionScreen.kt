package com.mikeliang.questtracker.ui.reflection

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mikeliang.questtracker.core.model.Attribute
import com.mikeliang.questtracker.core.model.Cadence
import com.mikeliang.questtracker.core.model.ProgressionTarget
import com.mikeliang.questtracker.core.model.Quest
import com.mikeliang.questtracker.core.model.QuestId
import com.mikeliang.questtracker.core.model.QuestKind
import com.mikeliang.questtracker.core.model.QuestType
import com.mikeliang.questtracker.ui.questlist.QuickAddSheet
import com.mikeliang.questtracker.ui.theme.QuestTrackerTheme
import java.text.DecimalFormat
import java.time.Instant
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The 90-second monthly ritual (design foundation §6): last month's trajectory, one
 * question, and answers that edit the system. Back or the close icon skip — skipping
 * is a first-class exit, costs nothing, and simply re-arms the banner next month.
 */
@Composable
fun ReflectionScreen(
    onClose: () -> Unit,
    viewModel: ReflectionViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.closed) {
        if (state.closed) onClose()
    }
    BackHandler { viewModel.onEvent(ReflectionEvent.Skip) }
    ReflectionContent(state = state, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReflectionContent(
    state: ReflectionUiState,
    onEvent: (ReflectionEvent) -> Unit,
) {
    var escalating by remember { mutableStateOf<ReflectionUiState.Row?>(null) }
    var showAddQuest by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.month?.label() ?: "Reflection") },
                navigationIcon = {
                    IconButton(onClick = { onEvent(ReflectionEvent.Skip) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Skip reflection")
                    }
                },
            )
        },
    ) { innerPadding ->
        if (state.loading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    text = "A month in review — about 90 seconds.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item { AttributeGains(state.attributeGains) }

            item {
                Text(
                    text = "Which of these still points where you want to go?",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
            }

            items(state.rows, key = { it.quest.id.value }) { row ->
                ReflectionQuestCard(
                    row = row,
                    onChoose = { choice ->
                        if (choice is ReflectionChoice.Escalate) {
                            escalating = row
                        } else {
                            onEvent(ReflectionEvent.Choose(row.quest.id, choice))
                        }
                    },
                )
            }

            if (state.rows.isEmpty()) {
                item {
                    Text(
                        text = "No active recurring quests to review — maybe last month's chapter closed. Add what comes next.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    )
                }
            }

            item {
                OutlinedButton(
                    onClick = { showAddQuest = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("Add a quest", modifier = Modifier.padding(start = 8.dp))
                }
            }

            state.addedQuestTitles.forEach { title ->
                item {
                    Text(
                        text = "Added: $title",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    Button(
                        onClick = { onEvent(ReflectionEvent.Complete) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Done") }
                    TextButton(
                        onClick = { onEvent(ReflectionEvent.Skip) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Skip this month") }
                }
            }
        }
    }

    escalating?.let { row ->
        EscalateDialog(
            row = row,
            onConfirm = { newAmount ->
                onEvent(ReflectionEvent.Choose(row.quest.id, ReflectionChoice.Escalate(newAmount)))
                escalating = null
            },
            onDismiss = { escalating = null },
        )
    }

    if (showAddQuest) {
        QuickAddSheet(
            onDismiss = { showAddQuest = false },
            onEvent = { event ->
                showAddQuest = false
                onEvent(ReflectionEvent.AddQuest(event))
            },
        )
    }
}

/** The month's banked points — evidence first, question second. Zeros stay quiet. */
@Composable
private fun AttributeGains(gains: Map<Attribute, Double>) {
    val earned = gains.filterValues { it > 0.0 }
    if (earned.isEmpty()) return
    val format = remember { DecimalFormat("0.#") }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        earned.forEach { (attribute, points) ->
            Card {
                Text(
                    text = "${attribute.name} +${format.format(points)}",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/**
 * One quest's evidence and its pending answer. Keep is pre-selected — the calm
 * default is that everything stays exactly as it is.
 */
@Composable
private fun ReflectionQuestCard(
    row: ReflectionUiState.Row,
    onChoose: (ReflectionChoice) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = row.quest.title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = buildString {
                    append("${row.completionsInMonth} completed · ")
                    append("${(row.consistencyRate * 100).roundToInt()}% consistent")
                    if (row.escalatedInMonth) append(" · escalated ↑")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (row.barelyMoved) {
                Spacer(Modifier.height(2.dp))
                Text(
                    // Neutral observation, never a judgement: quiet is information.
                    text = "Quietest quest this month",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = row.choice is ReflectionChoice.Keep,
                    onClick = { onChoose(ReflectionChoice.Keep) },
                    label = { Text("Keep") },
                )
                if (row.canEscalate) {
                    val choice = row.choice
                    FilterChip(
                        selected = choice is ReflectionChoice.Escalate,
                        onClick = { onChoose(ReflectionChoice.Escalate(0.0)) },
                        label = {
                            Text(
                                if (choice is ReflectionChoice.Escalate) {
                                    "Raise to ${DecimalFormat("0.#").format(choice.newAmount)}"
                                } else {
                                    "Raise the bar"
                                }
                            )
                        },
                    )
                }
                FilterChip(
                    selected = row.choice is ReflectionChoice.Retire,
                    onClick = { onChoose(ReflectionChoice.Retire) },
                    label = { Text("Retire") },
                )
            }
        }
    }
}

/**
 * New target for a progression quest. Prefilled with the current amount; any change
 * counts as escalation — lowering the bar is equally valid (:core's rule, not ours).
 */
@Composable
private fun EscalateDialog(
    row: ReflectionUiState.Row,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    val target = row.progression ?: return
    val format = remember { DecimalFormat("0.#") }
    var text by remember { mutableStateOf(format.format(target.amount)) }
    val newAmount = text.toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New target") },
        text = {
            Column {
                Text(
                    text = "${row.quest.title} — currently ${format.format(target.amount)} ${target.unit}.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(target.unit) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { newAmount?.let(onConfirm) },
                enabled = newAmount != null && newAmount > 0,
            ) { Text("Set target") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun YearMonth.label(): String =
    format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))

// --- Previews -------------------------------------------------------------

private fun previewRow(
    id: String,
    title: String,
    completions: Int,
    rate: Double,
    escalated: Boolean = false,
    barelyMoved: Boolean = false,
    progression: ProgressionTarget? = null,
) = ReflectionUiState.Row(
    quest = Quest(
        id = QuestId(id),
        title = title,
        kind = QuestKind.Recurring(
            cadence = Cadence.Daily,
            type = if (progression != null) QuestType.Progression else QuestType.Maintenance,
            attribute = Attribute.Body,
            progression = progression,
        ),
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
    ),
    completionsInMonth = completions,
    consistencyRate = rate,
    escalatedInMonth = escalated,
    barelyMoved = barelyMoved,
)

@Preview(showBackground = true)
@Composable
private fun ReflectionPreview() {
    QuestTrackerTheme {
        ReflectionContent(
            state = ReflectionUiState(
                month = YearMonth.of(2026, 6),
                rows = listOf(
                    previewRow(
                        "q1", "10k steps", 26, 0.96,
                        escalated = true,
                        progression = ProgressionTarget(10_000.0, "steps", 2),
                    ),
                    previewRow("q2", "Read 20 pages", 21, 0.88),
                    previewRow("q3", "Call parents", 1, 0.25, barelyMoved = true),
                ),
                attributeGains = mapOf(
                    Attribute.Body to 26.0,
                    Attribute.Mind to 18.5,
                    Attribute.Social to 3.0,
                    Attribute.Discipline to 0.0,
                ),
            ),
            onEvent = {},
        )
    }
}
