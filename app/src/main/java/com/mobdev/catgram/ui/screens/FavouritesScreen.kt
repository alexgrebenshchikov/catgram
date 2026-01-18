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
    val isFavouritesReady = favViewModel.items != null && !favViewModel.isLoading
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
    val isRefreshing  = favViewModel.isLoading
    val state = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            favViewModel.fetchFavourites()
        },
        state = state,
        contentAlignment = Alignment.Center
    ) {
        CatList(
            itemList, isLoading, isError, listState,
            onFavClick = { shouldAdd, item ->
                if (shouldAdd) {
                    favViewModel.addToFavourites(item)
                } else {
                    favViewModel.removeFromFavourites(item)
                }
            },
            checkIsFavourite = { id -> favViewModel.checkInFavourites(id) },
            checkIsEnabledCallback = { id -> !favViewModel.checkIsUpdating(id) && isFavouritesReady},
            getLikesCount = null,
            onErrorItemClicked = null,
            onPostDeleteClick = null,
            checkIsMyPostCallback = { userId -> favViewModel.currentUser?.uid == userId }
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