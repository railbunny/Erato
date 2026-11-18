package com.erato.lyrics

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val ctx = LocalContext.current
            val dark = isSystemInDarkTheme()
            val colors = if (Build.VERSION.SDK_INT >= 31) {
                if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
            } else {
                if (dark) darkColorScheme() else lightColorScheme()
            }
            MaterialTheme(colorScheme = colors) {
                Surface(Modifier.fillMaxSize()) { App() }
            }
        }
    }
}

fun saveLrc(ctx: Context, name: String, content: String) {
    val safe = name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, "$safe.lrc")
        put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Lyrics")
    }
    val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        ?: throw Exception("Could not create file")
    ctx.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(listOf<LyricResult>()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<LyricResult?>(null) }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        Text("Lyrics", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Song and artist") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                scope.launch {
                    loading = true; error = null
                    try {
                        results = withContext(Dispatchers.IO) {
                            allSources.flatMap { src ->
                                try { src.search(query) } catch (e: Exception) { emptyList() }
                            }
                        }
                        if (results.isEmpty()) error = "No results"
                    } catch (e: Exception) {
                        error = e.message
                    }
                    loading = false
                }
            },
            enabled = query.isNotBlank() && !loading,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (loading) "Searching..." else "Search") }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(results) { r ->
                Card(onClick = { selected = r }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(r.track, style = MaterialTheme.typography.titleMedium)
                        Text("${r.artist} • ${r.album}", style = MaterialTheme.typography.bodySmall)
                        Text(
                            (if (r.synced != null) "Synced" else "Plain only") + " • ${r.source}",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }

    selected?.let { r ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(r.track) },
            text = {
                Text(
                    r.synced ?: r.plain ?: "No lyrics",
                    Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val content = r.synced ?: r.plain
                    if (content != null) {
                        try {
                            saveLrc(ctx, "${r.artist} - ${r.track}", content)
                            Toast.makeText(ctx, "Saved to Downloads/Lyrics", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(ctx, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                    selected = null
                }) { Text("Save .lrc") }
            },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("Close") } }
        )
    }
}
