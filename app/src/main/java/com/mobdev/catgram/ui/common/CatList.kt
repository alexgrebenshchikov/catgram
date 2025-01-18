package com.mobdev.catgram.ui.common

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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mobdev.catgram.R
import com.mobdev.catgram.network.BreedInfo
import com.mobdev.catgram.network.CatsData
import com.mobdev.catgram.ui.theme.CatgramTheme
import com.mobdev.catgram.utils.getNameAndDescription

typealias FavClickCallback = (Boolean, CatsData, () -> Unit, () -> Unit) -> Unit
typealias CheckIsFavCallback = (String) -> Boolean
typealias GetLikesCountCallback = ((String, (Long) -> Unit) -> Unit)?

@Composable
fun CatList(
    itemList: List<CatsData>,
    isLoading: Boolean,
    listState: LazyListState,
    onFavClick: FavClickCallback,
    checkIsFavourite: CheckIsFavCallback,
    getLikesCount: GetLikesCountCallback
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
    ) {
        items(itemList, key = { it.id }) { item ->
            CatCard(item, onFavClick, checkIsFavourite, getLikesCount)
        }

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
}

@Composable
fun CatCard(
    item: CatsData,
    onFavClick: FavClickCallback,
    checkIsFavourite: CheckIsFavCallback,
    getLikesCount: GetLikesCountCallback?
) {
    val isActivated = remember { mutableStateOf(checkIsFavourite(item.id)) }
    val likesCounter = getLikesCount?.let { f ->
        val likesCounter = remember { mutableLongStateOf(0) }
        f(item.id) { likesCounter.longValue = it }
        likesCounter
    }

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
            contentScale = ContentScale.FillWidth,
            error = painterResource(R.drawable.ic_broken_image),
            placeholder = painterResource(R.drawable.loading_img),
            modifier = Modifier.fillMaxWidth()
        )
        item.breeds.getNameAndDescription()?.let {
            ExpandableHeadingWithDetail(it.first, it.second)
        }
        FavouritesButton(onFavClick, isActivated, likesCounter, item)
    }
}

@Composable
fun ExpandableHeadingWithDetail(heading: String, description: String) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }

    val arrowRotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "arrow rotation"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = heading,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f) // Take up available space in the row
            )

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

        AnimatedVisibility(visible = isExpanded) {
            Text(
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
fun FavouritesButton(
    onFavClick: FavClickCallback,
    isActivated: MutableState<Boolean>,
    likesCounter: MutableState<Long>?,
    item: CatsData
) {
    var isEnabled by rememberSaveable { mutableStateOf(true) }

    val color = if (isActivated.value) Color.Yellow else Color.White
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            enabled = isEnabled,
            onClick = {
                val initialState = isActivated.value
                isActivated.value = !isActivated.value
                isEnabled = false
                likesCounter.updateState(initialState)

                onFavClick(!initialState, item, {
                    isEnabled = true
                }, {
                    isActivated.value = !isActivated.value
                    likesCounter.updateState(!initialState)
                    isEnabled = true
                })
            }
        ) {
            Icon(
                painter = painterResource(id = R.drawable.star_icon),
                contentDescription = "Star Icon",
                tint = color,
                modifier = Modifier.size(20.dp)
            )
        }

        if (likesCounter != null) {
            Text(
                text = "${likesCounter.value}",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Start,
                fontSize = 14.sp,
                modifier = Modifier.wrapContentSize()
            )
        }
    }
}

private fun MutableState<Long>?.updateState(initialActState: Boolean) {
    if (this == null) {
        return
    }

    if (initialActState) {
        value -= 1
    } else {
        value += 1
    }
}

@Preview
@Composable
fun FavouritesButtonPreview() {
    CatgramTheme {
        val c = remember { mutableStateOf(false) }
        val l = remember { mutableLongStateOf(0L) }
        FavouritesButton({ _, _, _, _ -> }, c, l, CatsData("Dsds", "dsds", listOf()))
    }
}

@Preview
@Composable
fun CatCardPreview() {
    CatgramTheme {
        CatCard(
            CatsData("id", "url", listOf(BreedInfo("id", "name", "description"))),
            { _, _, _, _ -> },
            { true },
            { _, _ -> 0 }
        )
    }
}