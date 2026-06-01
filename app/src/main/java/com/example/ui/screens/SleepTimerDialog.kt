package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SleepTimerDialog(
    onDismiss: () -> Unit,
    onStart: (Int) -> Unit,
    onCancel: () -> Unit,
    isTimerActive: Boolean
) {
    var selectedMinutes by remember { mutableStateOf(15) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Sleep Timer",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                if (isTimerActive) {
                    Text(
                        text = "A sleep timer is currently active. You can cancel it or start a new one.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                } else {
                    Text(
                        text = "Configure ExoPlayer to automatically pause playback after the duration below finishes:",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val minutesList = listOf(15, 30, 45, 60, 90)
                    minutesList.forEach { min ->
                        FilterChip(
                            selected = selectedMinutes == min,
                            onClick = { selectedMinutes = min },
                            label = { Text("$min m") },
                            modifier = Modifier.testTag("timer_chip_$min")
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onStart(selectedMinutes) },
                modifier = Modifier.testTag("timer_start_btn")
            ) {
                Text(if (isTimerActive) "Reset Timer" else "Start")
            }
        },
        dismissButton = {
            Row {
                if (isTimerActive) {
                    TextButton(
                        onClick = onCancel,
                        modifier = Modifier.testTag("timer_cancel_btn")
                    ) {
                        Text("Cancel Timer", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}
