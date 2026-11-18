package com.erato.lyrics

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class ThemeMode(val title: String) {
    SYSTEM("Follow system"),
    LIGHT("Light"),
    DARK("Dark"),
    AMOLED("AMOLED black")
}

object Settings {
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    }

    fun isEnabled(source: String): Boolean {
        return prefs.getBoolean("enabled_$source", source == "LRCLIB")
    }

    fun setEnabled(source: String, enabled: Boolean) {
        prefs.edit().putBoolean("enabled_$source", enabled).apply()
    }

    fun themeMode(): ThemeMode {
        val saved = prefs.getString("theme_mode", ThemeMode.SYSTEM.name)
        return runCatching { ThemeMode.valueOf(saved!!) }
            .getOrDefault(ThemeMode.SYSTEM)
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
    }
}

@Composable
fun AppearanceSettings(
    selected: ThemeMode,
    onSelected: (ThemeMode) -> Unit
) {
    Text(
        text = "Appearance",
        style = MaterialTheme.typography.titleLarge
    )

    Spacer(Modifier.height(8.dp))

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 4.dp)) {
            ThemeMode.entries.forEach { mode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selected == mode,
                        onClick = { onSelected(mode) }
                    )

                    Text(
                        text = mode.title,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SourcesSettings() {
    Text(
        text = "Lyrics services",
        style = MaterialTheme.typography.titleLarge
    )

    Text(
        text = "Choose which services Erato can search. More providers will become functional in later versions.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(Modifier.height(12.dp))

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(allSources, key = { it.name }) { source ->
            SourceCard(source)
        }
    }
}

@Composable
private fun SourceCard(source: LyricsSource) {
    var enabled by remember(source.name) {
        mutableStateOf(Settings.isEnabled(source.name))
    }

    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = source.name,
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    text = if (source.available) {
                        "Available • No API key"
                    } else {
                        "Coming soon • Free source"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (source.available) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            Switch(
                checked = enabled && source.available,
                enabled = source.available,
                onCheckedChange = {
                    enabled = it
                    Settings.setEnabled(source.name, it)
                }
            )
        }
    }
}
