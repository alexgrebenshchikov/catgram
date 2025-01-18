package com.mobdev.catgram.ui.screens

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mobdev.catgram.R
import com.mobdev.catgram.TAG
import com.mobdev.catgram.ui.common.CatList
import kotlinx.coroutines.flow.collectLatest

private const val LOADING_OFFSET = 3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen() {
    val searchViewModel: SearchViewModel = viewModel(factory = SearchViewModel.factory)
    val favViewModel: FavouritesViewModel = viewModel()

    val itemList = searchViewModel.items
    Log.d(TAG, "uiState: ${searchViewModel.uiState}")
    val isLoading = searchViewModel.uiState == SearchViewModel.SearchUiState.Loading
    val isError = searchViewModel.uiState == SearchViewModel.SearchUiState.Error
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = searchViewModel.scrollPositionIndex,
        initialFirstVisibleItemScrollOffset = searchViewModel.scrollPositionOffset
    )
    DisposableEffect(listState) {
        onDispose {
            searchViewModel.scrollPositionIndex = listState.firstVisibleItemIndex
            searchViewModel.scrollPositionOffset = listState.firstVisibleItemScrollOffset
        }
    }

    val bottomSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true // Prevent half-expanded state
    )
    var bottomSheetOpened by rememberSaveable { mutableStateOf(false) }
    val breedsNotLoaded = searchViewModel.choosedBreeds.isEmpty()

    Box(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.BottomEnd
    ) {
        CatList(itemList, isLoading, listState,
            onFavClick = { shouldAdd, item, onSuccess, onFailure ->
                if (shouldAdd) {
                    favViewModel.addToFavourites(item, onSuccess, onFailure)
                } else {
                    favViewModel.removeFromFavourites(item, onSuccess, onFailure)
                }
            },
            checkIsFavourite = { id -> favViewModel.checkInFavourites(id) },
            getLikesCount = { id, onSuccess -> favViewModel.getLikesCount(id, onSuccess)} )

        if (isError) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.search_screen_error_message),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }

        // Trigger loading of more data when we reach the end of the list
        LaunchedEffect(listState) {
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
                .collectLatest { index ->
                    Log.d(TAG, "index: $index")

                    if (index == null) {
                        return@collectLatest
                    }

                    if (index >= searchViewModel.items.size - LOADING_OFFSET) {
                        searchViewModel.loadMoreCatsItemsIfNeeded()
                    }
                }
        }

        FloatingActionButton(
            onClick = {
                if (breedsNotLoaded) {
                    searchViewModel.applyBreedsFilter()
                    favViewModel.refreshData()
                } else {
                    bottomSheetOpened = true
                }
            },
            modifier = Modifier.padding(8.dp)
        ) {
            FloatingActionButtonDefaults.containerColor
            Icon(
                imageVector = if (breedsNotLoaded) Icons.Default.Refresh else Icons.Default.Search,
                contentDescription = "Open Bottom Sheet"
            )
        }

        if (bottomSheetOpened) {
            ModalBottomSheet(
                sheetState = bottomSheetState,
                onDismissRequest = {
                    bottomSheetOpened = false
                    searchViewModel.applyBreedsFilter()
                    favViewModel.refreshData()
                },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                BottomSheetContent(searchViewModel)
            }
        }
    }
}

@Composable
fun BottomSheetContent(viewModel: SearchViewModel) {
    val checkedItems = viewModel.choosedBreeds

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 16.dp)
    ) {
        // Bottom sheet heading
        Text(
            text = stringResource(R.string.filter_by_breed),
            style = MaterialTheme.typography.titleMedium,
            fontSize = 20.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // LazyColumn to display an arbitrary number of items
        LazyColumn {
            items(checkedItems.keys.toList()) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Item label
                    Text(
                        text = viewModel.toBreedName(item),
                        modifier = Modifier.weight(1f)
                    )

                    // Checkbox for the item
                    Checkbox(
                        checked = checkedItems[item] ?: false,
                        onCheckedChange = { isChecked ->
                            viewModel.updateChoosedBreeds(item, isChecked)
                        }
                    )
                }
            }
        }
    }
}
