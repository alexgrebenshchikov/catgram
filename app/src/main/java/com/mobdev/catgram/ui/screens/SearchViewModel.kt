package com.mobdev.catgram.ui.screens

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mobdev.catgram.CatgramApplication
import com.mobdev.catgram.TAG
import com.mobdev.catgram.data.CatgramRepository
import com.mobdev.catgram.network.BreedInfo
import com.mobdev.catgram.network.CatsData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.mobdev.catgram.auth.isSignedIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement

class SearchViewModel(
    private val catgramRepository: CatgramRepository,
    private val context: Application
) : ViewModel() {
    private var currentPage by mutableIntStateOf(0)
    private val pageSize = 10

    var items by mutableStateOf<List<CatsData>>(listOf())
        private set
    private var itemsIds = mutableSetOf<String>()

    var uiState: SearchUiState by mutableStateOf(SearchUiState.Ready)
        private set
    private var loadingJob: Job? = null
    private var isAllCatsDataLoaded = false

    var choosedBreeds by mutableStateOf<Map<String, Boolean>>(mapOf())
        private set
    private var breedIdToName: Map<String, String> = mapOf()

    private val json = Json { ignoreUnknownKeys = true }

    var scrollPositionIndex: Int = 0
    var scrollPositionOffset: Int = 0

    private val breedsKey: Preferences.Key<String>
        get() {
            val userUid =
                Firebase.auth.currentUser?.uid ?: throw IllegalStateException("User unauthorized.")
            return stringPreferencesKey(userUid)
        }


    init {
        Log.d(TAG, "search vm init")
        if (isSignedIn()) {
            loadChoosedBreeds()
        }
    }

    fun loadChoosedBreeds() {
        uiState = SearchUiState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val breedInfoList = catgramRepository.getBreedList()
                val breeds = breedInfoList.map { it.id }

                context.dataStore.data.first().let { prefs ->
                    val breedsFromStore = getBreedsFromStore(prefs)
                    val result = breeds.associateWith { false }.let {
                        it.plus(breedsFromStore.filter { entry -> breeds.contains(entry.key) && entry.value })
                    }
                    Log.d(TAG, "store: $breedsFromStore")
                    Log.d(TAG, "result: $result")
                    updateBreedsDataStore(result)
                    withContext(Dispatchers.Main) {
                        breedIdToName = breedInfoList.associate { it.id to it.name }
                        choosedBreeds = result
                        uiState = SearchUiState.Ready
                        loadMoreCatsItemsIfNeeded()
                    }
                }
            } catch (e: Throwable) {
                Log.d(TAG, "load choosed breeds failed: ${e.message}")
                withContext(Dispatchers.Main) {
                    uiState = SearchUiState.Error
                }
            }
        }
    }

    fun loadMoreCatsItemsIfNeeded() {
        Log.d(TAG, "loadMoreCatsItemsIfNeeded page: $currentPage, uiState: $uiState")
        if (uiState == SearchUiState.Ready && !isAllCatsDataLoaded && choosedBreeds.isNotEmpty()) {
            loadCatsDataPage()
        }
    }

    fun updateChoosedBreeds(breed: String, isChoosed: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            updateBreedsDataStore(choosedBreeds)
        }
        choosedBreeds = choosedBreeds.plus(breed to isChoosed)
    }

    fun applyBreedsFilter() {
        resetItems()
        if (choosedBreeds.isEmpty()) {
            loadChoosedBreeds()
        } else {
            loadMoreCatsItemsIfNeeded()
        }
    }

    fun reset() {
        resetItems()
        choosedBreeds = mapOf()
        breedIdToName = mapOf()
    }

    private fun resetItems() {
        uiState = SearchUiState.Ready
        items = listOf()
        itemsIds = mutableSetOf()
        isAllCatsDataLoaded = false
        currentPage = 0
        loadingJob?.cancel()
        scrollPositionIndex = 0
        scrollPositionOffset = 0
    }

    fun toBreedName(breedId: String): String {
        return breedIdToName[breedId] ?: throw IllegalStateException("Wrong breed id")
    }

    private fun loadCatsDataPage() {
        Log.d(TAG, "loadCatsDataPage")
        uiState = SearchUiState.Loading

        loadingJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val catsData = catgramRepository.getCatsData(
                    pageSize,
                    choosedBreeds.filter { it.value }.keys.toList(),
                    currentPage
                )
                /*delay(100)
                val catsData = Array(pageSize) {
                    CatsData(
                        "$currentPage $it",
                        "https://cdn2.thecatapi.com/images/byQhFO7iV.jpg",
                        if (it == 3) listOf() else listOf(
                            BreedInfo("beng", "Bengal", "some long long long desciprtion")
                        )
                    )
                }*/
                val newItems = catsData.filter { !itemsIds.contains(it.id) }
                if (newItems.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        isAllCatsDataLoaded = true
                        uiState = SearchUiState.Ready
                    }
                    return@launch
                }
                withContext(Dispatchers.Main) {
                    itemsIds.addAll(newItems.map { it.id })
                    items = items + newItems
                    Log.d(TAG, "items size: ${items.size}")
                    currentPage++
                    uiState = SearchUiState.Ready
                }
            } catch (error: Throwable) {
                //SearchUiState.Failed(error)
                Log.d(TAG, "load cats data failed: ${error.message}")
                withContext(Dispatchers.Main) {
                    uiState = SearchUiState.Error
                }
            }
        }
    }

    private suspend fun updateBreedsDataStore(newValue: Map<String, Boolean>) {
        context.dataStore.updateData { prefs ->
            prefs.toMutablePreferences().apply {
                set(breedsKey, json.encodeToJsonElement(newValue).toString())
            }
        }
    }

    private fun getBreedsFromStore(prefs: Preferences): Map<String, Boolean> {
        return prefs[breedsKey]?.let {
            try {
                json.decodeFromString<Map<String, Boolean>>(it)
            } catch (e: Throwable) {
                null
            }
        } ?: mapOf()
    }

    companion object {
        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[APPLICATION_KEY] as CatgramApplication)
                val catgramRepository = application.container.catgramRepository
                SearchViewModel(catgramRepository = catgramRepository, context = application)
            }
        }
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("choosed-breeds")
    }

    sealed interface SearchUiState {
        data object Ready: SearchUiState
        data object Loading: SearchUiState
        data object Error: SearchUiState
    }
}