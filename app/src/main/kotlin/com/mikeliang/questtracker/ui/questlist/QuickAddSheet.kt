package com.mikeliang.questtracker.ui.questlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mikeliang.questtracker.core.model.Attribute
import com.mikeliang.questtracker.core.model.Cadence
import com.mikeliang.questtracker.ui.common.AttributePicker
import com.mikeliang.questtracker.ui.common.CadencePicker
import com.mikeliang.questtracker.ui.common.ReminderTimeChip
import com.mikeliang.questtracker.ui.common.ReminderTimePickerDialog
import java.time.LocalTime

/**
 * The 5-second capture sheet: title → optional reminder → save as side quest.
 * "Make this recurring…" expands cadence + attribute choices in place — a secondary
 * path, not a separate flow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheet(
    onDismiss: () -> Unit,
    onEvent: (QuestListEvent) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var reminderTime by remember { mutableStateOf<LocalTime?>(null) }
    var showTimePicker by remember { mutableStateOf(false) }
    var recurring by remember { mutableStateOf(false) }
    var cadence by remember { mutableStateOf(Cadence.Daily) }
    var attribute by remember { mutableStateOf(Attribute.Discipline) }
    var journalLinked by remember { mutableStateOf(false) }

    fun save() {
        if (title.isBlank()) return
        onEvent(
            if (recurring) {
                QuestListEvent.AddRecurringQuest(title, cadence, attribute, reminderTime, journalLinked)
            } else {
                QuestListEvent.AddSideQuest(title, reminderTime)
            }
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("What needs doing?") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ReminderTimeChip(
                    time = reminderTime,
                    onClick = {
                        if (reminderTime == null) showTimePicker = true else reminderTime = null
                    },
                )
                FilterChip(
                    selected = recurring,
                    onClick = { recurring = !recurring },
                    label = { Text("Make this recurring…") },
                )
            }

            if (recurring) {
                Spacer(Modifier.height(16.dp))
                Text("How often?", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                CadencePicker(selected = cadence, onSelect = { cadence = it })
                Spacer(Modifier.height(12.dp))
                Text("Which attribute does it build?", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                AttributePicker(selected = attribute, onSelect = { attribute = it })
                Spacer(Modifier.height(12.dp))
                FilterChip(
                    selected = journalLinked,
                    onClick = { journalLinked = !journalLinked },
                    label = { Text("Completes when I journal") },
                )
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = ::save,
                enabled = title.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (recurring) "Add recurring quest" else "Add side quest")
            }
        }
    }

    if (showTimePicker) {
        ReminderTimePickerDialog(
            initial = reminderTime,
            onConfirm = {
                reminderTime = it
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
        )
    }
}
