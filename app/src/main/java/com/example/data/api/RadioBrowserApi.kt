package com.example.data.api

import com.example.data.model.RadioBrowserStation
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface RadioBrowserApi {
    @GET("json/stations/topclick/{limit}")
    suspend fun getTopClickedStations(
        @Path("limit") limit: Int = 100
    ): List<RadioBrowserStation>

    @GET("json/stations/search")
    suspend fun searchStations(
        @Query("name") name: String? = null,
        @Query("countrycode") countryCode: String? = null,
        @Query("limit") limit: Int = 100,
        @Query("hidebroken") hideBroken: Boolean = true,
        @Query("order") order: String? = null,
        @Query("reverse") reverse: Boolean? = null
    ): List<RadioBrowserStation>

    @GET("json/stations/search")
    suspend fun getTopClickedByCodec(
        @Query("codec") codec: String,
        @Query("limit") limit: Int = 100,
        @Query("order") order: String = "clickcount",
        @Query("reverse") reverse: Boolean = true,
        @Query("hidebroken") hideBroken: Boolean = true
    ): List<RadioBrowserStation>

    @GET("json/stations/bytag/{tag}")
    suspend fun getStationsByTag(
        @Path("tag") tag: String,
        @Query("limit") limit: Int = 40
    ): List<RadioBrowserStation>

    @GET("json/countries")
    suspend fun getCountries(): List<com.example.data.model.CountryDto>

    @GET("json/stations/bycountryexact/{country}")
    suspend fun getStationsByCountry(
        @Path("country") country: String,
        @Query("limit") limit: Int = 100
    ): List<RadioBrowserStation>
}
