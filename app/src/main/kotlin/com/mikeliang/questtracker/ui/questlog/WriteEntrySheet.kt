package com.mikeliang.questtracker.ui.questlog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mikeliang.questtracker.core.model.QuestId

/**
 * The write/edit sheet: one multiline field, plus — for new entries — the
 * journal-linked quests this entry will count toward, shown as pre-selected chips so
 * the link is visible at the moment of writing and any quest can be unticked.
 * Editing shows no chips: edits never re-bank anything. Deleting asks once, with
 * factual copy: what was banked stays banked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WriteEntrySheet(
    onDismiss: () -> Unit,
    onSave: (text: String, countToward: Set<QuestId>) -> Unit,
    initialText: String = "",
    // Quests a new entry could complete; ignored (and empty) when editing.
    linkedQuests: List<QuestLogUiState.LinkedQuestOption> = emptyList(),
    // Non-null only when editing an existing entry.
    onDelete: (() -> Unit)? = null,
) {
    var text by remember { mutableStateOf(initialText) }
    var selected by remember { mutableStateOf(linkedQuests.map { it.id }.toSet()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val editing = onDelete != null

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("What's on your mind?") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            if (!editing && linkedQuests.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Counts toward", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    linkedQuests.forEach { option ->
                        FilterChip(
                            selected = option.id in selected,
                            onClick = {
                                selected = if (option.id in selected) {
                                    selected - option.id
                                } else {
                                    selected + option.id
                                }
                            },
                            label = { Text(option.title) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (editing) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("Delete entry")
                    }
                }
                Button(
                    onClick = { onSave(text, selected) },
                    enabled = text.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (editing) "Save changes" else "Save entry")
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this entry?") },
            text = { Text("Anything it completed stays completed.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete?.invoke()
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Keep it")
                }
            },
        )
    }
}
