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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DarkColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Settings.init(this)

        setContent {
            var themeMode by remember {
                mutableStateOf(Settings.themeMode())
            }

            EratoTheme(themeMode) {
                EratoApp(
                    themeMode = themeMode,
                    onThemeChanged = {
                        themeMode = it
                        Settings.setThemeMode(it)
                    }
                )
            }
        }
    }
}

private val AmoledColors: DarkColorScheme = darkColorScheme(
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color(0xFF171717),
    onBackground = Color.White,
    onSurface = Color.White
)

@Composable
private fun EratoTheme(
    mode: ThemeMode,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()

    val colors = when (mode) {
        ThemeMode.LIGHT -> {
            if (Build.VERSION.SDK_INT >= 31) {
                dynamicLightColorScheme(context)
            } else {
                lightColorScheme()
            }
        }

        ThemeMode.DARK -> {
            if (Build.VERSION.SDK_INT >= 31) {
                dynamicDarkColorScheme(context)
            } else {
                darkColorScheme()
            }
        }

        ThemeMode.AMOLED -> {
            val base = if (Build.VERSION.SDK_INT >= 31) {
                dynamicDarkColorScheme(context)
            } else {
                AmoledColors
            }

            base.copy(
                background = Color.Black,
                surface = Color.Black,
                surfaceVariant = Color(0xFF171717)
            )
        }

        ThemeMode.SYSTEM -> {
            if (Build.VERSION.SDK_INT >= 31) {
                if (systemDark) {
                    dynamicDarkColorScheme(context)
                } else {
                    dynamicLightColorScheme(context)
                }
            } else {
                if (systemDark) darkColorScheme() else lightColorScheme()
            }
        }
    }

    MaterialTheme(colorScheme = colors) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            content = content
        )
    }
}

private enum class AppPage(
    val title: String,
    val icon: ImageVector
) {
    SEARCH("Search", Icons.Default.Search),
    LIBRARY("Library", Icons.Default.Folder),
    SETTINGS("Settings", Icons.Default.Settings)
}

@Composable
private fun EratoApp(
    themeMode: ThemeMode,
    onThemeChanged: (ThemeMode) -> Unit
) {
    var currentPage by remember { mutableStateOf(AppPage.SEARCH) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                AppPage.entries.forEach { page ->
                    NavigationBarItem(
                        selected = currentPage == page,
                        onClick = { currentPage = page },
                        icon = {
                            Icon(
                                imageVector = page.icon,
                                contentDescription = page.title
                            )
                        },
                        label = { Text(page.title) }
                    )
                }
            }
        }
    ) { innerPadding ->
        when (currentPage) {
            AppPage.SEARCH -> SearchScreen(innerPadding)
            AppPage.LIBRARY -> LibraryScreen(innerPadding)
            AppPage.SETTINGS -> SettingsPage(
                padding = innerPadding,
                themeMode = themeMode,
                onThemeChanged = onThemeChanged
            )
        }
    }
}

private enum class SearchMode(val title: String) {
    ALL("All enabled sources"),
    AUTOMATIC("Automatic fallback"),
    SINGLE("One source")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchScreen(padding: PaddingValues) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<LyricResult>()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedResult by remember { mutableStateOf<LyricResult?>(null) }

    var searchMode by remember { mutableStateOf(SearchMode.ALL) }
    var selectedSourceName by remember { mutableStateOf("LRCLIB") }
    var modeMenuOpen by remember { mutableStateOf(false) }
    var sourceMenuOpen by remember { mutableStateOf(false) }

    val availableSources = allSources.filter { it.available }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(12.dp))

        Text(
            text = "Erato",
            style = MaterialTheme.typography.headlineLarge
        )

        Text(
            text = "Find synchronized lyrics",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Song, artist or album") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        ExposedDropdownMenuBox(
            expanded = modeMenuOpen,
            onExpandedChange = { modeMenuOpen = !modeMenuOpen }
        ) {
            OutlinedTextField(
                value = searchMode.title,
                onValueChange = {},
                readOnly = true,
                label = { Text("Search mode") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(modeMenuOpen)
                },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = modeMenuOpen,
                onDismissRequest = { modeMenuOpen = false }
            ) {
                SearchMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode.title) },
                        onClick = {
                            searchMode = mode
                            modeMenuOpen = false
                        }
                    )
                }
            }
        }

        if (searchMode == SearchMode.SINGLE) {
            Spacer(Modifier.height(8.dp))

            ExposedDropdownMenuBox(
                expanded = sourceMenuOpen,
                onExpandedChange = { sourceMenuOpen = !sourceMenuOpen }
            ) {
                OutlinedTextField(
                    value = selectedSourceName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Lyrics source") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(sourceMenuOpen)
                    },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = sourceMenuOpen,
                    onDismissRequest = { sourceMenuOpen = false }
                ) {
                    availableSources.forEach { source ->
                        DropdownMenuItem(
                            text = { Text(source.name) },
                            onClick = {
                                selectedSourceName = source.name
                                sourceMenuOpen = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Button(
            enabled = query.isNotBlank() && !loading,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                scope.launch {
                    loading = true
                    error = null
                    results = emptyList()

                    val enabledSources = availableSources.filter {
                        Settings.isEnabled(it.name)
                    }

                    val sourcesToSearch = when (searchMode) {
                        SearchMode.ALL,
                        SearchMode.AUTOMATIC -> enabledSources

                        SearchMode.SINGLE -> enabledSources.filter {
                            it.name == selectedSourceName
                        }
                    }

                    if (sourcesToSearch.isEmpty()) {
                        error = "No matching source is enabled"
                        loading = false
                        return@launch
                    }

                    results = when (searchMode) {
                        SearchMode.ALL -> withContext(Dispatchers.IO) {
                            sourcesToSearch.map { source ->
                                async {
                                    runCatching {
                                        source.search(query)
                                    }.getOrDefault(emptyList())
                                }
                            }.awaitAll().flatten()
                        }

                        SearchMode.AUTOMATIC -> withContext(Dispatchers.IO) {
                            var found = emptyList<LyricResult>()

                            for (source in sourcesToSearch) {
                                found = runCatching {
                                    source.search(query)
                                }.getOrDefault(emptyList())

                                if (found.isNotEmpty()) break
                            }

                            found
                        }

                        SearchMode.SINGLE -> withContext(Dispatchers.IO) {
                            runCatching {
                                sourcesToSearch.first().search(query)
                            }.getOrDefault(emptyList())
                        }
                    }

                    if (results.isEmpty()) {
                        error = "No lyrics found"
                    }

                    loading = false
                }
            }
        ) {
            Text(if (loading) "Searching…" else "Search lyrics")
        }

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            items(
                items = results,
                key = {
                    "${it.source}-${it.artist}-${it.track}-${it.duration}"
                }
            ) { result ->
                ResultCard(
                    result = result,
                    onClick = { selectedResult = result }
                )
            }
        }
    }

    selectedResult?.let { result ->
        LyricsDialog(
            result = result,
            onDismiss = { selectedResult = null },
            onSave = {
                val contents = result.synced ?: result.plain

                if (contents != null) {
                    try {
                        saveLrc(
                            context,
                            "${result.artist} - ${result.track}",
                            contents
                        )
                        Toast.makeText(
                            context,
                            "Saved to Downloads/Lyrics",
                            Toast.LENGTH_SHORT
                        ).show()
                    } catch (error: Exception) {
                        Toast.makeText(
                            context,
                            "Could not save: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                selectedResult = null
            }
        )
    }
}

@Composable
private fun ResultCard(
    result: LyricResult,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = result.track,
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = result.artist,
                style = MaterialTheme.typography.bodyMedium
            )

            if (result.album.isNotBlank()) {
                Text(
                    text = result.album,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusChip(
                    text = if (result.synced != null) {
                        "Synced"
                    } else {
                        "Plain"
                    }
                )

                StatusChip(result.source)
            }
        }
    }
}

@Composable
private fun StatusChip(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.secondaryContainer,
                RoundedCornerShape(50)
            )
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

@Composable
private fun LyricsDialog(
    result: LyricResult,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val lyrics = result.synced ?: result.plain ?: "No lyrics available"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(result.track)
                Text(
                    text = result.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column {
                Text(
                    text = if (result.synced != null) {
                        "Synchronized lyrics • ${result.source}"
                    } else {
                        "Plain lyrics • ${result.source}"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                Text(
                    text = lyrics,
                    modifier = Modifier
                        .heightIn(max = 430.dp)
                        .verticalScroll(rememberScrollState())
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) {
                Text("Save .lrc")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun LibraryScreen(padding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .statusBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Music library",
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Folder selection and local music scanning are coming in v0.3.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsPage(
    padding: PaddingValues,
    themeMode: ThemeMode,
    onThemeChanged: (ThemeMode) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(12.dp))

        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineLarge
        )

        Spacer(Modifier.height(20.dp))

        AppearanceSettings(
            selected = themeMode,
            onSelected = onThemeChanged
        )

        Spacer(Modifier.height(24.dp))

        SourcesSettings()
    }
}

fun saveLrc(
    context: Context,
    name: String,
    content: String
) {
    val safeName = name.replace(
        Regex("[\\\\/:*?\"<>|]"),
        "_"
    )

    val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, "$safeName.lrc")
        put(
            MediaStore.Downloads.MIME_TYPE,
            "application/octet-stream"
        )
        put(
            MediaStore.Downloads.RELATIVE_PATH,
            "${Environment.DIRECTORY_DOWNLOADS}/Lyrics"
        )
    }

    val uri = context.contentResolver.insert(
        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
        values
    ) ?: throw Exception("Could not create the file")

    context.contentResolver.openOutputStream(uri)?.use {
        it.write(content.toByteArray())
    } ?: throw Exception("Could not write the file")
}
