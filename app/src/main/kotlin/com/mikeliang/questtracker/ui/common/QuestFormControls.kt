package com.mikeliang.questtracker.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.mikeliang.questtracker.core.model.Attribute
import com.mikeliang.questtracker.core.model.Cadence
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

/**
 * The shared quest-form controls: quick-add capture and the detail screen's edit
 * sheet build from the same pieces, so a quest reads the same wherever it's shaped.
 */

@Composable
fun CadencePicker(selected: Cadence, onSelect: (Cadence) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Cadence.entries.forEach { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelect(option) },
                label = { Text(option.name) },
            )
        }
    }
}

@Composable
fun AttributePicker(selected: Attribute, onSelect: (Attribute) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Attribute.entries.forEach { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelect(option) },
                label = { Text(option.name) },
            )
        }
    }
}

/** Multi-select day-of-week chips for a recurring reminder's days. */
@Composable
fun DayOfWeekPicker(selected: Set<DayOfWeek>, onToggle: (DayOfWeek) -> Unit) {
    // Read observably so the chips relabel on a locale change (lint: NonObservableLocale).
    val locale = LocalConfiguration.current.locales[0]
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        DayOfWeek.entries.forEach { day ->
            FilterChip(
                selected = day in selected,
                onClick = { onToggle(day) },
                label = { Text(day.getDisplayName(TextStyle.NARROW, locale)) },
            )
        }
    }
}

/** Shows the chosen reminder time, or the invitation to pick one. */
@Composable
fun ReminderTimeChip(time: LocalTime?, onClick: () -> Unit) {
    FilterChip(
        selected = time != null,
        onClick = onClick,
        label = { Text(time?.format(reminderTimeFormat) ?: "Remind me") },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderTimePickerDialog(
    initial: LocalTime?,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val pickerState = rememberTimePickerState(
        initialHour = initial?.hour ?: 0,
        initialMinute = initial?.minute ?: 0,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(pickerState.hour, pickerState.minute)) }) {
                Text("Set")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        text = { TimePicker(state = pickerState) },
    )
}

private val reminderTimeFormat = DateTimeFormatter.ofPattern("h:mm a")
