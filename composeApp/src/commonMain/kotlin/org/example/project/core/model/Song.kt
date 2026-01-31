package org.example.project.core.model

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
    val duration: String
)
