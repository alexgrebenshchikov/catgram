package com.mobdev.catgram.ui.screens

import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.mobdev.catgram.MainViewModel
import com.mobdev.catgram.R
import com.mobdev.catgram.ui.common.CatList
import kotlinx.coroutines.flow.collectLatest

private const val LOADING_OFFSET = 2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen() {
    val feedViewModel: FeedViewModel = viewModel(factory = FeedViewModel.factory)
    val favViewModel: FavouritesViewModel = viewModel(factory = FavouritesViewModel.factory)
    val mainViewModel: MainViewModel = viewModel()

    val itemList = feedViewModel.items
    val isTopLoading = (feedViewModel.uiState as? FeedViewModel.FeedUiState.Loading)?.let {
        it.isFirstPage
    } ?: false
    val isBottomLoading = (feedViewModel.uiState as? FeedViewModel.FeedUiState.Loading)?.let {
        !it.isFirstPage
    } ?: false
    val isError = feedViewModel.uiState == FeedViewModel.FeedUiState.Error
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = feedViewModel.scrollPositionIndex,
        initialFirstVisibleItemScrollOffset = feedViewModel.scrollPositionOffset
    )
    DisposableEffect(listState) {
        onDispose {
            feedViewModel.scrollPositionIndex = listState.firstVisibleItemIndex
            feedViewModel.scrollPositionOffset = listState.firstVisibleItemScrollOffset
        }
    }

    val bottomSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true // Prevent half-expanded state
    )
    var bottomSheetOpened by rememberSaveable { mutableStateOf(false) }
    var filterChanged by rememberSaveable { mutableStateOf(false) }
    val selectedFilterType = feedViewModel.selectedFilterType
    val showOnlyMyPosts = feedViewModel.showOnlyMyPosts
    val isFavouritesReady = favViewModel.isReady

    // Create Post sheet state
    val createPostSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
    var createPostSheetOpened by rememberSaveable { mutableStateOf(false) }
    var selectedImageUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var postText by rememberSaveable { mutableStateOf("") }

    val state = rememberPullToRefreshState()
    val context = LocalContext.current

    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage = feedViewModel.snackbarMessage
    val isCreatingPost = feedViewModel.isCreatingPost

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            feedViewModel.clearSnackbarMessage()
        }
    }

    val shouldScrollToTop = feedViewModel.shouldScrollToTop
    LaunchedEffect(shouldScrollToTop) {
        if (shouldScrollToTop) {
            listState.scrollToItem(0)
            feedViewModel.onScrolledToTop()
        }
    }

    PullToRefreshBox(
        modifier = Modifier.fillMaxSize(),
        isRefreshing = isTopLoading,
        onRefresh = {
            feedViewModel.refreshData()
            favViewModel.refreshData()
        },
        state = state,
        contentAlignment = Alignment.BottomEnd
    ) {
        CatList(
            itemList, isBottomLoading, isError, listState,
            onFavClick = { shouldAdd, item ->
                if (shouldAdd) {
                    mainViewModel.startReviewFlow(context)
                    favViewModel.addToFavourites(item)
                } else {
                    favViewModel.removeFromFavourites(item)
                }
            },
            checkIsFavourite = { item -> favViewModel.checkInFavourites(item) },
            checkIsEnabledCallback = { id -> !favViewModel.checkIsUpdating(id) && isFavouritesReady },
            getLikesCount = { id -> favViewModel.getLikesCount(id) },
            onErrorItemClicked = { feedViewModel.loadDataPageIfNeeded(checkErrorState = false) },
            onPostDeleteClick = {
                feedViewModel.deleteUserPost(it.id) {
                    favViewModel.refreshData()
                }
            },
            checkIsMyPostCallback = { userId -> feedViewModel.currentUser?.uid == userId },
        )

        // Trigger loading of more data when we reach the end of the list
        LaunchedEffect(listState) {
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
                .collectLatest { index ->
                    if (index == null) {
                        return@collectLatest
                    }

                    if (index == feedViewModel.items.lastIndex - LOADING_OFFSET) {
                        feedViewModel.loadDataPageIfNeeded()
                    }
                }
        }

        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.End
        ) {
            // Secondary action - Filter
            SmallFloatingActionButton(
                onClick = {
                    if (!isTopLoading) {
                        filterChanged = false
                        bottomSheetOpened = true
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = stringResource(R.string.filter_by_breed)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            // Primary action - Create Post
            FloatingActionButton(
                onClick = {
                    if (!isCreatingPost) {
                        selectedImageUri = null
                        postText = ""
                        createPostSheetOpened = true
                    }
                }
            ) {
                if (isCreatingPost) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.create_new_post)
                    )
                }
            }
        }

        if (bottomSheetOpened) {
            ModalBottomSheet(
                sheetState = bottomSheetState,
                onDismissRequest = {
                    bottomSheetOpened = false
                    if (filterChanged) {
                        feedViewModel.refreshData()
                        favViewModel.refreshData()
                    }
                },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                FilterSheetContent(
                    selectedFilterType = selectedFilterType,
                    onFilterTypeChange = {
                        filterChanged = true
                        feedViewModel.updateFilterType(it)
                    },
                    showOnlyMyPosts = showOnlyMyPosts,
                    onShowOnlyMyPostsChange = {
                        filterChanged = true
                        feedViewModel.updateShowOnlyMyPosts(it)
                    },
                    checkedItems = feedViewModel.choosedBreeds,
                    breedUiState = feedViewModel.breedUiState,
                    toBreedName = { feedViewModel.toBreedName(it) },
                    onCheckedChange = { item, isChecked ->
                        filterChanged = true
                        feedViewModel.updateChoosedBreeds(item, isChecked)
                    },
                    onRetryBreedList = feedViewModel::retryBreedList,
                )
            }
        }

        if (createPostSheetOpened) {
            ModalBottomSheet(
                sheetState = createPostSheetState,
                onDismissRequest = {
                    createPostSheetOpened = false
                },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                CreatePostSheetContent(
                    selectedImageUri = selectedImageUri,
                    onImageSelected = { selectedImageUri = it },
                    postText = postText,
                    onPostTextChange = { postText = it },
                    onPostClick = {
                        selectedImageUri?.let {
                            feedViewModel.createUserPost(it, postText)
                        }
                        createPostSheetOpened = false
                    }
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
fun FilterSheetContent(
    selectedFilterType: FeedViewModel.FilterType,
    onFilterTypeChange: (FeedViewModel.FilterType) -> Unit,
    showOnlyMyPosts: Boolean,
    onShowOnlyMyPostsChange: (Boolean) -> Unit,
    checkedItems: Map<String, Boolean>,
    breedUiState: FeedViewModel.BreedUiState,
    toBreedName: (String) -> String,
    onCheckedChange: (String, Boolean) -> Unit,
    onRetryBreedList: () -> Unit,
) {
    val gap = if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        0
    } else {
        168
    }
    val maxHeight = LocalConfiguration.current.screenHeightDp - gap
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight.dp)
            .padding(top = 4.dp, start = 16.dp, bottom = 16.dp, end = 16.dp)
    ) {
        // Bottom sheet heading
        Text(
            text = stringResource(R.string.filter_title),
            style = MaterialTheme.typography.titleMedium,
            fontSize = 20.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(bottom = 16.dp)
                .fillMaxWidth()
        )

        // Radio option: Users posts
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onFilterTypeChange(FeedViewModel.FilterType.USERS_POSTS) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selectedFilterType == FeedViewModel.FilterType.USERS_POSTS,
                onClick = { onFilterTypeChange(FeedViewModel.FilterType.USERS_POSTS) }
            )
            Text(
                text = stringResource(R.string.filter_users_posts),
                style = MaterialTheme.typography.bodyLarge
            )
        }

        // Show only my posts checkbox (under Users posts)
        AnimatedVisibility(visible = selectedFilterType == FeedViewModel.FilterType.USERS_POSTS) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 32.dp)
                    .clickable { onShowOnlyMyPostsChange(!showOnlyMyPosts) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = showOnlyMyPosts,
                    onCheckedChange = onShowOnlyMyPostsChange
                )
                Text(
                    text = stringResource(R.string.filter_show_only_my_posts),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Radio option: Cats by breed
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onFilterTypeChange(FeedViewModel.FilterType.CATS_BY_BREED) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selectedFilterType == FeedViewModel.FilterType.CATS_BY_BREED,
                onClick = { onFilterTypeChange(FeedViewModel.FilterType.CATS_BY_BREED) }
            )
            Text(
                text = stringResource(R.string.filter_cats_by_breed),
                style = MaterialTheme.typography.bodyLarge
            )
        }

        // Breed checkboxes (under Cats by breed)
        AnimatedVisibility(visible = selectedFilterType == FeedViewModel.FilterType.CATS_BY_BREED) {
            val hasUsableBreedData = checkedItems.isNotEmpty() && when (breedUiState) {
                FeedViewModel.BreedUiState.Ready -> true
                is FeedViewModel.BreedUiState.Loading -> breedUiState.hasCachedData
                is FeedViewModel.BreedUiState.Error -> breedUiState.hasCachedData
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                when (breedUiState) {
                    is FeedViewModel.BreedUiState.Loading -> {
                        if (!hasUsableBreedData) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Text(
                                    text = stringResource(R.string.breed_list_loading),
                                    modifier = Modifier.padding(start = 12.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                    is FeedViewModel.BreedUiState.Error -> BreedListError(
                        error = breedUiState,
                        onRetry = onRetryBreedList,
                    )
                    FeedViewModel.BreedUiState.Ready -> Unit
                }

                if (hasUsableBreedData) {
                    LazyColumn(
                        modifier = Modifier.padding(start = 32.dp)
                    ) {
                        items(checkedItems.keys.toList()) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = checkedItems[item] ?: false,
                                    onCheckedChange = { isChecked ->
                                        onCheckedChange(item, isChecked)
                                    }
                                )
                                Text(
                                    text = toBreedName(item),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                } else if (breedUiState == FeedViewModel.BreedUiState.Ready) {
                    BreedListError(
                        error = FeedViewModel.BreedUiState.Error(
                            reason = FeedViewModel.BreedLoadError.RESPONSE,
                            hasCachedData = false,
                        ),
                        onRetry = onRetryBreedList,
                    )
                }
            }
        }
    }
}

@Composable
private fun BreedListError(
    error: FeedViewModel.BreedUiState.Error,
    onRetry: () -> Unit,
) {
    val message = when (error.reason) {
        FeedViewModel.BreedLoadError.CONFIGURATION -> R.string.breed_list_error_configuration
        FeedViewModel.BreedLoadError.AUTHENTICATION -> R.string.breed_list_error_authentication
        FeedViewModel.BreedLoadError.DATE_TIME -> R.string.breed_list_error_date_time
        FeedViewModel.BreedLoadError.NETWORK -> R.string.breed_list_error_network
        FeedViewModel.BreedLoadError.SERVER -> R.string.breed_list_error_server
        FeedViewModel.BreedLoadError.RESPONSE -> R.string.breed_list_error_response
        FeedViewModel.BreedLoadError.UNKNOWN -> R.string.breed_list_error_unknown
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(message),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        if (error.hasCachedData) {
            Text(
                text = stringResource(R.string.breed_list_using_cache),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Button(
            onClick = onRetry,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text(stringResource(R.string.retry))
        }
    }
}

@Composable
fun CreatePostSheetContent(
    selectedImageUri: Uri?,
    onImageSelected: (Uri?) -> Unit,
    postText: String,
    onPostTextChange: (String) -> Unit,
    onPostClick: () -> Unit
) {
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        onImageSelected(uri)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Title
        Text(
            text = stringResource(R.string.create_new_post),
            style = MaterialTheme.typography.titleMedium,
            fontSize = 20.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Image picker area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(12.dp)
                )
                .clickable { imagePickerLauncher.launch("image/*") },
            contentAlignment = Alignment.Center
        ) {
            if (selectedImageUri != null) {
                AsyncImage(
                    model = selectedImageUri,
                    contentDescription = stringResource(R.string.selected_image),
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = stringResource(R.string.add_photo),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                    Text(
                        text = stringResource(R.string.tap_to_select_image),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Post text input
        val maxPostLength = 500
        OutlinedTextField(
            value = postText,
            onValueChange = { if (it.length <= maxPostLength) onPostTextChange(it) },
            label = { Text(stringResource(R.string.post_description_label)) },
            placeholder = { Text(stringResource(R.string.post_description_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 5,
            supportingText = {
                Text(
                    text = "${postText.length}/$maxPostLength",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End
                )
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Post button
        Button(
            onClick = onPostClick,
            modifier = Modifier.fillMaxWidth(1.0f),
            shape = RoundedCornerShape(32),
            enabled = selectedImageUri != null && postText.isNotBlank()
        ) {
            Text(stringResource(R.string.post_button))
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Preview
@Composable
fun FilterSheetContentPreview() {
    FilterSheetContent(
        selectedFilterType = FeedViewModel.FilterType.CATS_BY_BREED,
        onFilterTypeChange = {},
        showOnlyMyPosts = false,
        onShowOnlyMyPostsChange = {},
        checkedItems = mapOf("a" to false, "b" to false),
        breedUiState = FeedViewModel.BreedUiState.Ready,
        toBreedName = { "breed" },
        onCheckedChange = { _, _ -> },
        onRetryBreedList = {},
    )
}
