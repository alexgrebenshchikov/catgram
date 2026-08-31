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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mobdev.catgram.R
import com.mobdev.catgram.ui.common.CatList
import kotlinx.coroutines.flow.collectLatest

private const val LOADING_OFFSET = 2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavouritesScreen(
    favouritesViewModel: FavouritesViewModel? = null,
    onPostClick: (String) -> Unit = {},
) {
    val favViewModel: FavouritesViewModel =
        favouritesViewModel ?: viewModel(factory = FavouritesViewModel.factory)
    val itemList = favViewModel.items ?: listOf()
    val isLoading = favViewModel.isLoading && !favViewModel.isRefreshing
    val isError = false
    val isFavouritesReady = favViewModel.isReady
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
    val isRefreshing = favViewModel.isRefreshing
    val state = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            favViewModel.refreshData()
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
            checkIsFavourite = { item -> favViewModel.checkInFavourites(item) },
            checkIsEnabledCallback = { id -> !favViewModel.checkIsUpdating(id) && isFavouritesReady},
            getLikesCount = { id -> favViewModel.getLikesCount(id) },
            getCommentsCount = { id -> favViewModel.getCommentsCount(id) },
            onErrorItemClicked = null,
            onPostDeleteClick = null,
            checkIsMyPostCallback = { userId -> favViewModel.currentUser?.uid == userId },
            onPostClick = onPostClick,
        )

        LaunchedEffect(listState) {
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
                .collectLatest { index ->
                    val lastItemIndex = favViewModel.items.orEmpty().lastIndex
                    if (index != null && index >= lastItemIndex - LOADING_OFFSET) {
                        favViewModel.loadNextPageIfNeeded()
                    }
                }
        }

        if (itemList.isEmpty() && !isRefreshing) {
            Text(
                text = stringResource(R.string.favourites_screen_placeholder),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}
