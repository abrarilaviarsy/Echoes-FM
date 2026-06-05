package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.RetrofitClient
import com.example.data.db.AppDatabase
import com.example.data.model.FavoriteStation
import com.example.data.model.RadioBrowserStation
import com.example.data.repository.RadioRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RadioBrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = RadioRepository(
        radioBrowserApi = RetrofitClient.radioBrowserApi,
        somaFmApi = RetrofitClient.somaFmApi,
        favoriteDao = db.favoriteDao(),
        songHistoryDao = db.songHistoryDao(),
        likedSongDao = db.likedSongDao()
    )

    // --- UI States ---
    val favorites: StateFlow<List<FavoriteStation>> = repository.allFavorites
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _radioBrowserStations = MutableStateFlow<List<RadioBrowserStation>>(emptyList())
    val radioBrowserStations: StateFlow<List<RadioBrowserStation>> = kotlinx.coroutines.flow.combine(
        _radioBrowserStations,
        favorites
    ) { stations, favList ->
        stations.sortedByDescending { station ->
            favList.any { it.id == station.stationuuid }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _isLoadingRadioBrowser = MutableStateFlow(false)
    val isLoadingRadioBrowser: StateFlow<Boolean> = _isLoadingRadioBrowser.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _currentOrder = MutableStateFlow<String>("clickcount")
    val currentOrder: StateFlow<String> = _currentOrder.asStateFlow()

    private val _countries = MutableStateFlow<List<com.example.data.model.CountryDto>>(emptyList())
    val countries: StateFlow<List<com.example.data.model.CountryDto>> = _countries.asStateFlow()

    private val _isLoadingCountries = MutableStateFlow(false)
    val isLoadingCountries: StateFlow<Boolean> = _isLoadingCountries.asStateFlow()

    init {
        // Run an empty search on startup to show standard results
        searchRadioBrowser("")
    }

    fun searchRadioBrowser(query: String, order: String? = null) {
        _searchQuery.value = query
        if (order != null) {
            _currentOrder.value = order
        }
        val orderParam = order ?: _currentOrder.value
        viewModelScope.launch {
            _isLoadingRadioBrowser.value = true
            _radioBrowserStations.value = repository.searchStations(query, order = orderParam)
            _isLoadingRadioBrowser.value = false
        }
    }

    fun searchByCountry(countryName: String) {
        _searchQuery.value = countryName
        viewModelScope.launch {
            _isLoadingRadioBrowser.value = true
            _radioBrowserStations.value = repository.getStationsByCountry(countryName)
            _isLoadingRadioBrowser.value = false
        }
    }

    fun searchByTag(tagName: String) {
        _searchQuery.value = tagName
        viewModelScope.launch {
            _isLoadingRadioBrowser.value = true
            _radioBrowserStations.value = repository.getStationsByTag(tagName)
            _isLoadingRadioBrowser.value = false
        }
    }

    fun fetchCountries() {
        viewModelScope.launch {
            _isLoadingCountries.value = true
            _countries.value = repository.getCountries()
            _isLoadingCountries.value = false
        }
    }

    fun toggleFavorite(favoriteStation: FavoriteStation) {
        viewModelScope.launch {
            val isFav = favorites.value.any { it.id == favoriteStation.id }
            if (isFav) {
                repository.removeFavoriteById(favoriteStation.id)
            } else {
                repository.addFavorite(favoriteStation)
            }
        }
    }
}
