package org.example.project

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview

import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.compose_multiplatform
import kotlinx.coroutines.launch
import org.example.project.data.YouTubeRepository
import org.example.project.domain.model.Song

@Preview
@Composable
fun App() {
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var songs by remember { mutableStateOf(listOf<Song>()) }
    val repository = YouTubeRepository()

    MaterialTheme(colorScheme = darkColorScheme()) { // Spotify style!
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            // Search Bar
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search on YouTube Music...") },
                trailingIcon = {
                    IconButton(onClick = {
                        scope.launch {
                            songs = repository.searchSongs(searchQuery)
                        }
                    }) {
                        Icon(, contentDescription = null)
                    }
                }
            )

            // Results List
            LazyColumn {
                items(songs) { song ->
                    SongItem(song) {
                        // When clicked:
                        // 1. Get the real audio URL (using the extractor)
                        // 2. Pass it to the Player
                        println("Selected: ${song.title}")
                    }
                }
            }
        }
    }
}

@Composable
fun SongItem(song: Song, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage( // From Coil library
            model = song.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier.size(50.dp).clip(RoundedCornerShape(4.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(song.title, maxLines = 1, fontWeight = FontWeight.Bold, color = Color.White)
            Text(song.artist, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}
