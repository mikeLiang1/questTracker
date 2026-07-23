package com.mikeliang.questtracker.ui.questdetail

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mikeliang.questtracker.core.engine.QuestEdit
import com.mikeliang.questtracker.core.engine.TargetEdit
import com.mikeliang.questtracker.core.model.Quest
import com.mikeliang.questtracker.core.model.QuestKind
import com.mikeliang.questtracker.core.model.QuestType
import com.mikeliang.questtracker.core.model.ReminderSchedule
import com.mikeliang.questtracker.ui.common.AttributePicker
import com.mikeliang.questtracker.ui.common.CadencePicker
import com.mikeliang.questtracker.ui.common.DayOfWeekPicker
import com.mikeliang.questtracker.ui.common.ReminderTimeChip
import com.mikeliang.questtracker.ui.common.ReminderTimePickerDialog
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * The edit sheet: everything editable about a quest in one place, saved as one
 * validated event. Kind is displayed nowhere here because it is not editable — the
 * sheet's shape *is* the kind. Target amounts are deliberately absent: changing the
 * amount is escalation, offered on the detail screen, never buried in an edit form.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestEditSheet(
    quest: Quest,
    onDismiss: () -> Unit,
    onEvent: (QuestDetailEvent) -> Unit,
) {
    when (val kind = quest.kind) {
        is QuestKind.Recurring -> RecurringEditSheet(quest, kind, onDismiss, onEvent)
        QuestKind.SideQuest -> SideQuestEditSheet(quest, onDismiss, onEvent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecurringEditSheet(
    quest: Quest,
    kind: QuestKind.Recurring,
    onDismiss: () -> Unit,
    onEvent: (QuestDetailEvent) -> Unit,
) {
    val existingReminder = quest.reminder as? ReminderSchedule.Recurring
    var title by remember { mutableStateOf(quest.title) }
    var cadence by remember { mutableStateOf(kind.cadence) }
    var attribute by remember { mutableStateOf(kind.attribute) }
    var reminderTime by remember { mutableStateOf(existingReminder?.time) }
    var reminderDays by remember { mutableStateOf(existingReminder?.days ?: emptySet()) }
    var showTimePicker by remember { mutableStateOf(false) }
    // Maintenance can gain a target; Progression can drop its own. Never both.
    var addTarget by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf(false) }
    var targetAmount by remember { mutableStateOf("") }
    var targetUnit by remember { mutableStateOf("") }

    val targetEdit = when {
        addTarget -> targetAmount.toDoubleOrNull()
            ?.takeIf { it > 0 && targetUnit.isNotBlank() }
            ?.let { TargetEdit.Add(it, targetUnit.trim()) }
        removeTarget -> TargetEdit.Remove
        else -> TargetEdit.Keep
    }
    val saveable = title.isNotBlank() &&
        (reminderTime == null || reminderDays.isNotEmpty()) &&
        (!addTarget || targetEdit is TargetEdit.Add)

    fun save() {
        onEvent(
            QuestDetailEvent.SaveRecurringEdit(
                QuestEdit.EditRecurring(
                    title = title.trim(),
                    cadence = cadence,
                    attribute = attribute,
                    reminder = reminderTime?.let { ReminderSchedule.Recurring(it, reminderDays) },
                    target = targetEdit ?: TargetEdit.Keep,
                )
            )
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Quest name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))
            Text("How often?", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            CadencePicker(selected = cadence, onSelect = { cadence = it })

            Spacer(Modifier.height(12.dp))
            Text("Which attribute does it build?", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            AttributePicker(selected = attribute, onSelect = { attribute = it })

            Spacer(Modifier.height(12.dp))
            Text("Reminder", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            ReminderTimeChip(
                time = reminderTime,
                onClick = {
                    if (reminderTime == null) showTimePicker = true else reminderTime = null
                },
            )
            if (reminderTime != null) {
                Spacer(Modifier.height(8.dp))
                DayOfWeekPicker(
                    selected = reminderDays,
                    onToggle = { day ->
                        reminderDays = if (day in reminderDays) reminderDays - day else reminderDays + day
                    },
                )
            }

            Spacer(Modifier.height(12.dp))
            when (kind.type) {
                QuestType.Maintenance -> {
                    Text("Target", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    FilterChip(
                        selected = addTarget,
                        onClick = { addTarget = !addTarget },
                        label = { Text("Give it a rising target") },
                    )
                    if (addTarget) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = targetAmount,
                                onValueChange = { targetAmount = it },
                                label = { Text("Amount") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = targetUnit,
                                onValueChange = { targetUnit = it },
                                label = { Text("Unit") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                QuestType.Progression -> {
                    Text("Target", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    FilterChip(
                        selected = removeTarget,
                        onClick = { removeTarget = !removeTarget },
                        label = { Text("Remove the target — keep it as maintenance") },
                    )
                }

                QuestType.Outcome -> Unit // No outcome config yet (build plan: deferred).
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = ::save,
                enabled = saveable,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save changes")
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SideQuestEditSheet(
    quest: Quest,
    onDismiss: () -> Unit,
    onEvent: (QuestDetailEvent) -> Unit,
) {
    var title by remember { mutableStateOf(quest.title) }
    var reminderTime by remember {
        mutableStateOf((quest.reminder as? ReminderSchedule.OneShot)?.at?.toLocalTime())
    }
    var showTimePicker by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Quest name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))
            ReminderTimeChip(
                time = reminderTime,
                onClick = {
                    if (reminderTime == null) showTimePicker = true else reminderTime = null
                },
            )

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { onEvent(QuestDetailEvent.SaveSideQuestEdit(title.trim(), reminderTime)) },
                enabled = title.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save changes")
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
