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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement

//sealed interface SearchUiState {
//    data object Loading: SearchUiState
//    data class Succeed(val data: List<CatsData>): SearchUiState
//    data class Failed(val error: Throwable): SearchUiState
//}

class SearchViewModel(
    private val catgramRepository: CatgramRepository,
    private val context: Application
) : ViewModel() {
    private var currentPage by mutableIntStateOf(0)
    private val pageSize = 10

    var items by mutableStateOf<List<CatsData>>(listOf())
        private set
    private var itemsIds = mutableSetOf<String>()

    var isLoading by mutableStateOf(false)
        private set
    private var loadingJob: Job? = null
    private var isAllCatsDataLoaded = false

    var choosedBreeds by mutableStateOf<Map<String, Boolean>>(mapOf())
        private set
    private var breedIdToName: Map<String, String> = mapOf()

    private val json = Json { ignoreUnknownKeys = true }


    init {
        //loadMoreCatsItemsIfNeeded()
        viewModelScope.launch(Dispatchers.IO) {
            val breedInfoList = catgramRepository.getBreedList()
            val breeds = breedInfoList.map { it.id }
            breedIdToName = breedInfoList.associate { it.id to it.name }
            Log.d(TAG, "breeds: ${breeds}")
            context.dataStore.data.first().let { prefs ->
                Log.d(TAG, "ds collect")
                val breedsFromStore = getBreedsFromStore(prefs)
                val result = breeds.associateWith { false }.let {
                    it.plus(breedsFromStore.filter { entry -> breeds.contains(entry.key) && entry.value })
                }
                Log.d(TAG, "store: $breedsFromStore")
                Log.d(TAG, "result: $result")
                choosedBreeds = result
                updateBreedsDataStore(result)
                loadMoreCatsItemsIfNeeded()
            }
        }
    }

    fun loadMoreCatsItemsIfNeeded() {
        Log.d(TAG, "loadMoreCatsItemsIfNeeded page: $currentPage, isLoading: $isLoading")
        if (!isLoading && !isAllCatsDataLoaded) {
            loadCatsDataPage()
        }
    }

    fun updateChoosedBreeds(breed: String, isChoosed: Boolean) {
        choosedBreeds = choosedBreeds.plus(breed to isChoosed)
        viewModelScope.launch(Dispatchers.IO) {
            updateBreedsDataStore(choosedBreeds)
        }
    }

    fun applyBreedsFilter() {
        items = listOf()
        itemsIds = mutableSetOf()
        isLoading = false
        currentPage = 0
        loadingJob?.cancel()
    }

    fun toBreedName(breedId: String): String {
        return breedIdToName[breedId] ?: throw IllegalStateException("Wrong breed id")
    }

    private fun loadCatsDataPage() {
        Log.d(TAG, "loadCatsDataPage")
        isLoading = true

        loadingJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                //val catsData = catgramRepository.getCatsData(pageSize, choosedBreeds.filter { it.value }.keys.toList(), currentPage)
                delay(100)
                val catsData = Array(pageSize) {
                    CatsData(
                        "$currentPage $it",
                        "https://cdn2.thecatapi.com/images/byQhFO7iV.jpg",
                        if (it == 3) listOf() else listOf(
                            BreedInfo("beng", "Bengal", "some long long long desciprtion")
                        )
                    )
                }
                val newItems = catsData.filter { !itemsIds.contains(it.id) }
                if (newItems.isEmpty()) {
                    isAllCatsDataLoaded = true
                    withContext(Dispatchers.Main) {
                        isLoading = false
                    }
                    return@launch
                }

                itemsIds.addAll(newItems.map { it.id })
                items = items + newItems
                currentPage++
                Log.d(TAG, "items size: ${items.size}")
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            } catch (error: Throwable) {
                //SearchUiState.Failed(error)
                Log.d(TAG, "failed: ${error.message}")
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
        }
    }

    private suspend fun updateBreedsDataStore(newValue: Map<String, Boolean>) {
        context.dataStore.updateData { prefs ->
            prefs.toMutablePreferences().apply {
                set(BREEDS_KEY, json.encodeToJsonElement(newValue).toString())
            }
        }
    }

    private fun getBreedsFromStore(prefs: Preferences): Map<String, Boolean> {
        return prefs[BREEDS_KEY]?.let {
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
        private val BREEDS_KEY = stringPreferencesKey("name")
    }
}