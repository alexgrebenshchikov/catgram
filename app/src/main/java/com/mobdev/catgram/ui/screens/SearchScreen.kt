package com.mobdev.catgram.ui.screens

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mobdev.catgram.R
import com.mobdev.catgram.TAG
import com.mobdev.catgram.network.BreedInfo
import com.mobdev.catgram.network.CatsData
import com.mobdev.catgram.utils.getNameAndDescription
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private const val LOADING_OFFSET = 3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen() {
    val searchViewModel: SearchViewModel = viewModel(factory = SearchViewModel.factory)
    val itemList = searchViewModel.items
    val isLoading = searchViewModel.isLoading
    val listState = rememberLazyListState()
    val bottomSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true // Prevent half-expanded state
    )
    var bottomSheetOpened by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.BottomEnd
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
        ) {
            items(itemList) { item ->
                CatCard(item)
            }

            // Add a loading spinner at the bottom of the list when loading more items
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }

        // Trigger loading of more data when we reach the end of the list
        LaunchedEffect(listState) {
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
                .collectLatest { index ->
                    Log.d(TAG, "index: $index")
                    if (searchViewModel.choosedBreeds.isEmpty()) {
                        return@collectLatest
                    }

                    if (index == null) {
                        searchViewModel.loadMoreCatsItemsIfNeeded()
                        return@collectLatest
                    }
                    if (index >= searchViewModel.items.size - LOADING_OFFSET) {
                        searchViewModel.loadMoreCatsItemsIfNeeded()
                    }
                }
        }

        FloatingActionButton(
            onClick = {
                bottomSheetOpened = true
            },
            modifier = Modifier.padding(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Open Bottom Sheet"
            )
        }

        if (bottomSheetOpened) {
            ModalBottomSheet(
                sheetState = bottomSheetState,
                onDismissRequest = {
                    bottomSheetOpened = false
                    searchViewModel.applyBreedsFilter()
                },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                BottomSheetContent(searchViewModel)
            }
        }
    }
}

@Composable
fun CatCard(item: CatsData) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context = LocalContext.current)
                .data(item.url)
                .build(),
            contentDescription = stringResource(R.string.cats_card),
            contentScale = ContentScale.Crop,
            error = painterResource(R.drawable.ic_broken_image),
            placeholder = painterResource(R.drawable.loading_img),
            modifier = Modifier.fillMaxWidth()
        )
        /*Text(
            text = item.id,
            modifier = Modifier.padding(16.dp),
        )*/
        item.breeds.getNameAndDescription()?.let {
            ExpandableHeadingWithDetail(it.first, it.second)
        }
    }
}

@Composable
fun ExpandableHeadingWithDetail(heading: String, description: String) {
    // State variable to track whether the detailed text is expanded or collapsed
    var isExpanded by remember { mutableStateOf(false) }

    // Animation for rotating the arrow
    val arrowRotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "arrow rotation"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        // Heading row with title and button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Heading text
            Text(
                text = heading,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f) // Take up available space in the row
            )

            // Expand/Collapse button with arrow
            IconButton(onClick = { isExpanded = !isExpanded }) {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(arrowRotationAngle) // Rotate the arrow based on state
                )
            }
        }

        // Detailed description text - shown/hidden based on `isExpanded` state
        AnimatedVisibility(visible = isExpanded) {
            Text(
                //text = "This is the detailed description. It provides more information about the heading above. You can toggle its visibility by clicking the arrow button.",
                text = description,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Start,
                fontSize = 14.sp,
                modifier = Modifier
                    .fillMaxWidth()
            )
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
