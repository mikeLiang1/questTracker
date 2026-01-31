package org.example.project.core.repository

import com.yushosei.newpipe.extractor.stream.StreamInfoItem
import com.yushosei.newpipe.util.ExtractorHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.example.project.core.model.Song

class YouTubeRepository {
    suspend fun searchSongs(query: String): List<Song> {
        return withContext(Dispatchers.IO) {

            val searchResult = ExtractorHelper.searchFor(serviceId = 0, searchString = query, listOf("videos"), sortFilter = "")
            val videoItems = searchResult.relatedItems.filterIsInstance<StreamInfoItem>()

            videoItems.map { item ->
                Song(
                    id = item.url.split("v").last(),
                    title = item.name,
                    artist = item.uploaderName ?: "Unknown",
                    thumbnailUrl = item.thumbnails.firstOrNull()?.url,
                    duration =(item.duration * 1000).millisToDuration()
                )
            }
        }
    }


    private fun Long.millisToDuration(): String {
        val totalSeconds = this / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60

        return "${minutes}:${seconds.toString().padStart(2, '0')}"
    }
}
