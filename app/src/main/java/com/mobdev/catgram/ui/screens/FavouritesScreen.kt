package com.mobdev.catgram.ui.screens

import android.util.Log
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mobdev.catgram.TAG
import com.mobdev.catgram.ui.common.CatList


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavouritesScreen() {
    val favViewModel: FavouritesViewModel = viewModel()
    /*val itemList = Array(10) {
        CatsData(
            "0 $it",
            "https://cdn2.thecatapi.com/images/byQhFO7iV.jpg",
            if (it == 3) listOf() else listOf(
                BreedInfo("beng", "Bengal", "some long long long desciprtion")
            )
        )
    }.toList()*/
    val itemList = favViewModel.items
    val isLoading = false
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = favViewModel.scrollPositionIndex,
        initialFirstVisibleItemScrollOffset = favViewModel.scrollPositionOffset
    )
    DisposableEffect(listState) {
        onDispose {
            favViewModel.scrollPositionIndex = listState.firstVisibleItemIndex
            favViewModel.scrollPositionOffset = listState.firstVisibleItemScrollOffset
        }
    }
    var isRefreshing by remember { mutableStateOf(false) }
    val state = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            Log.d(TAG, "onRefresh")
            isRefreshing = true
            favViewModel.fetchFavourites {
                isRefreshing = false
            }
        },
        state = state
    ) {
        CatList(itemList, isLoading, listState,
            onFavClick = { shouldAdd, item, onSuccess, onFailure ->
                if (shouldAdd) {
                    favViewModel.addToFavourites(item, onSuccess, onFailure)
                } else {
                    favViewModel.removeFromFavourites(item, onSuccess, onFailure)
                }
            },
            checkIsFavourite = { id -> favViewModel.checkInFavourites(id) })
    }
}