package com.erato.lyrics

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

// Stored privately on the phone. Never in the code or on GitHub.
object Settings {
    private lateinit var prefs: SharedPreferences

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    }

    fun getKey(source: String): String = prefs.getString("key_$source", "") ?: ""
    fun setKey(source: String, value: String) =
        prefs.edit().putString("key_$source", value.trim()).apply()

    fun isEnabled(source: String): Boolean = prefs.getBoolean("enabled_$source", true)
    fun setEnabled(source: String, value: Boolean) =
        prefs.edit().putBoolean("enabled_$source", value).apply()
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Back") }
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
        }
        Spacer(Modifier.height(8.dp))
        Text("Sources", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(allSources) { src -> SourceCard(src) }
        }
    }
}

@Composable
fun SourceCard(src: LyricsSource) {
    var enabled by remember { mutableStateOf(Settings.isEnabled(src.name)) }
    var key by remember { mutableStateOf(Settings.getKey(src.name)) }
    var showKey by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(src.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (src.needsKey) "Requires API key" else "No key needed",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { enabled = it; Settings.setEnabled(src.name, it) }
                )
            }
            if (src.needsKey) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it; Settings.setKey(src.name, it) },
                    label = { Text("API key") },
                    singleLine = true,
                    visualTransformation =
                        if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { showKey = !showKey }) {
                            Text(if (showKey) "Hide" else "Show")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
