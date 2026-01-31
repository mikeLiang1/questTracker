package org.example.project.data

import com.yushosei.newpipe.extractor.ServiceList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.example.project.domain.model.Song

class YouTubeRepository {
    suspend fun searchSongs(query: String): List<Song> {
        return withContext(Dispatchers.IO) {
            val service = ServiceList.YouTube

            // This gets search results from YouTube
            val extractor = service.getSearchExtractor(query)
            extractor.fetchPage()

            // Convert YouTube results into our "Song" data class
            extractor.initialPage.items.map { item ->
                Song(
                    id = item.url.split("v=").last(),
                    title = item.name,
                    artist = "Unknown Artist",
                    thumbnailUrl = item.thumbnails.first().url,
                    duration = 0L // You can extract this later
                )
            }
        }
    }
}
