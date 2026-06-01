package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.RadioViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BackupRestoreSection(
    viewModel: RadioViewModel,
    modifier: Modifier = Modifier,
    onComplete: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val jsonString = viewModel.exportDatabaseToJson()
                val success = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openOutputStream(uri)?.use { 
                            java.io.OutputStreamWriter(it).use { writer -> writer.write(jsonString) }
                        }
                        true
                    } catch (e: Exception) { false }
                }
                if (success) onComplete()
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val jsonString = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            java.io.BufferedReader(java.io.InputStreamReader(inputStream)).use { it.readText() }
                        }
                    } catch (e: Exception) { null }
                }
                if (!jsonString.isNullOrBlank()) {
                    viewModel.importDatabaseFromJson(jsonString)
                    onComplete()
                }
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Local Backup & Restore", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
        Text(
            text = "Export your favorite radio stations and liked songs to a local JSON file, or restore them from a previous backup.",
            style = MaterialTheme.typography.bodyMedium, color = Color.Gray, textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Tombol Kapsul Backup
            Button(
                onClick = { backupLauncher.launch("RadioApp_Backup.json") },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD0BCFF), contentColor = Color.Black)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(imageVector = Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = "Backup Data", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            // Tombol Kapsul Restore
            Button(
                onClick = { restoreLauncher.launch(arrayOf("application/json")) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A), contentColor = Color.White)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(imageVector = Icons.Default.SettingsBackupRestore, contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = "Restore Data", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}