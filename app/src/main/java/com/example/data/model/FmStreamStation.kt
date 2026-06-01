package com.example.data.model

data class StreamOption(
    val bitrate: String,
    val url: String
)

data class FmStreamStation(
    val id: String,
    val name: String,
    val imageUrl: String?,
    val streams: List<StreamOption>,
    val codec: String?,
    val location: String? = null
)
