package com.mobdev.catgram.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mobdev.catgram.R
import com.mobdev.catgram.ui.common.CatList


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavouritesScreen() {
    val favViewModel: FavouritesViewModel = viewModel(factory = FavouritesViewModel.factory)
    val itemList = favViewModel.items ?: listOf()
    val isLoading = false
    val isError = false
    val isFavouritesReady = favViewModel.items != null
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
            isRefreshing = true
            favViewModel.fetchFavourites {
                isRefreshing = false
            }
        },
        state = state,
        contentAlignment = Alignment.Center
    ) {
        CatList(
            itemList, isLoading, isError, listState,
            onFavClick = { shouldAdd, item, onFinish ->
                if (shouldAdd) {
                    favViewModel.addToFavourites(item, onFinish)
                } else {
                    favViewModel.removeFromFavourites(item, onFinish)
                }
            },
            checkIsFavourite = { id -> favViewModel.checkInFavourites(id) },
            getLikesCount = null,
            onErrorItemClicked = null,
            onPostDeleteClick = null,
            isFavouritesReady = isFavouritesReady,
        )

        if (itemList.isEmpty()) {
            Text(
                text = stringResource(R.string.favourites_screen_placeholder),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}