package com.example.data.api

import com.example.data.model.SomaFmResponse
import retrofit2.http.GET

interface SomaFmApi {
    @GET("channels.json")
    suspend fun getChannels(): SomaFmResponse
}
