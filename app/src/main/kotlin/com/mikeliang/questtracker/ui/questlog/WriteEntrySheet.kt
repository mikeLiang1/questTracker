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

/**
 * The write/edit sheet: one multiline field, nothing else to configure. Writing is
 * the whole act — any journal-linked quest completion happens on save without being
 * announced up front (the snackbar tells the story afterwards). Deleting asks once,
 * with factual copy: what was banked stays banked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WriteEntrySheet(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    initialText: String = "",
    // Non-null only when editing an existing entry.
    onDelete: (() -> Unit)? = null,
) {
    var text by remember { mutableStateOf(initialText) }
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

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (editing) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("Delete entry")
                    }
                }
                Button(
                    onClick = { onSave(text) },
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
