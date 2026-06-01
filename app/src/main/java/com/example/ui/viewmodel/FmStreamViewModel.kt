package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.FmStreamStation
import com.example.data.repository.FmStreamRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

sealed interface FmStreamSearchState {
    object Idle : FmStreamSearchState
    object Loading : FmStreamSearchState
    data class Success(val stations: List<FmStreamStation>) : FmStreamSearchState
    data class Error(val message: String) : FmStreamSearchState
}

class FmStreamViewModel(
    private val repository: FmStreamRepository = FmStreamRepository()
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchState = MutableStateFlow<FmStreamSearchState>(FmStreamSearchState.Idle)
    val searchState: StateFlow<FmStreamSearchState> = _searchState.asStateFlow()

    private var lastSearchedQuery: String? = null

    init {
        // Load default/featured stations immediately on startup
        viewModelScope.launch {
            performSearch("")
        }

        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            _searchQuery
                .debounce(2000L) // 2 full seconds of no typing
                .filter { it.isEmpty() || it.trim().length >= 3 } // Do not search for 1 or 2 characters
                .distinctUntilChanged() // Do not search if the query hasn't actually changed
                .collectLatest { query ->
                    // CRITICAL: use collectLatest so previous ongoing searches are cancelled if a new one starts
                    performSearch(query.trim())
                }
        }
    }

    fun onSearchQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
        val trimmed = newQuery.trim()
        if (trimmed.isNotEmpty() && trimmed.length < 3) {
            _searchState.value = FmStreamSearchState.Idle
        }
    }

    // Keep setSearchQuery as an alias so we maintain 100% compatibility with any existing reference if needed
    fun setSearchQuery(query: String) {
        onSearchQueryChanged(query)
    }

    // Let's also keep search(query) or a manual search invocation if they press the IME Search button
    fun search(query: String) {
        val trimmed = query.trim()
        _searchQuery.value = query
        if (trimmed.isNotEmpty() && trimmed.length < 3) {
            _searchState.value = FmStreamSearchState.Idle
            return
        }
        viewModelScope.launch {
            performSearch(trimmed)
        }
    }

    private suspend fun performSearch(query: String) {
        val trimmedQuery = query.trim()
        if (lastSearchedQuery == trimmedQuery) return
        lastSearchedQuery = trimmedQuery
        _searchState.value = FmStreamSearchState.Loading
        try {
            val results = repository.searchStations(trimmedQuery)
            _searchState.value = FmStreamSearchState.Success(results)
        } catch (e: Exception) {
            _searchState.value = FmStreamSearchState.Error(e.message ?: "An unknown error occurred")
        }
    }
}
