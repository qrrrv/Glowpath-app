package com.musicplayer.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.musicplayer.data.Song

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditMetadataScreen(
    song: Song,
    onBack: () -> Unit,
    onSave: (newTitle: String?, newArtist: String?, newAlbumArt: Uri?) -> Unit
) {
    var title by remember { mutableStateOf(song.customTitle ?: song.title) }
    var artist by remember { mutableStateOf(song.customArtist ?: song.artist) }
    var customAlbumArt by remember { mutableStateOf<Uri?>(song.customAlbumArtUri) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            customAlbumArt = uri
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Редактировать метаданные") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Назад")
                    }
                },
                actions = {
                    // Кнопка сброса
                    IconButton(
                        onClick = {
                            title = song.title
                            artist = song.artist
                            customAlbumArt = null
                        }
                    ) {
                        Icon(Icons.Default.RestartAlt, "Сбросить")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Обложка
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                onClick = { imagePickerLauncher.launch("image/*") }
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    val displayArt = customAlbumArt ?: song.albumArtUri
                    if (displayArt != null) {
                        AsyncImage(
                            model = displayArt,
                            contentDescription = "Обложка",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }
                }
            }

            Button(
                onClick = { imagePickerLauncher.launch("image/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Image, null)
                Spacer(Modifier.width(8.dp))
                Text("Выбрать обложку")
            }

            // Название
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Название") },
                leadingIcon = { Icon(Icons.Default.MusicNote, null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Исполнитель
            OutlinedTextField(
                value = artist,
                onValueChange = { artist = it },
                label = { Text("Исполнитель") },
                leadingIcon = { Icon(Icons.Default.Person, null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Оригинальные данные
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Оригинальные метаданные:",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Название: ${song.title}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Исполнитель: ${song.artist}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Кнопка сохранения
            Button(
                onClick = {
                    val newTitle = if (title != song.title) title else null
                    val newArtist = if (artist != song.artist) artist else null
                    onSave(newTitle, newArtist, customAlbumArt)
                    onBack()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Сохранить изменения")
            }

            Text(
                "Примечание: изменения сохраняются только локально в приложении. " +
                        "Для постоянного изменения файла используйте внешний редактор тегов.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}
