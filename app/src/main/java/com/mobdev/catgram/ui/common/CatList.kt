package com.mobdev.catgram.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.TextButton
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.composed
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Locale
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.google.firebase.Timestamp
import com.mobdev.catgram.R
import com.mobdev.catgram.auth.getCurrentUser
import com.mobdev.catgram.network.BreedInfo
import com.mobdev.catgram.ui.theme.CatgramTheme
import com.mobdev.catgram.ui.theme.StarYellow
import com.mobdev.catgram.utils.getNameAndDescription
import java.util.Date

typealias FavClickCallback = (Boolean, CatCardData, () -> Unit) -> Unit
typealias CheckIsFavCallback = (String) -> Boolean
typealias GetLikesCountCallback = ((String) -> Long?)?
typealias OnErrorItemClicked = (() -> Unit)?
typealias OnPostDeleteCallback = ((CatCardData.UserPost) -> Unit)?

@Composable
fun CatList(
    itemList: List<CatCardData>,
    isLoading: Boolean,
    isError: Boolean,
    listState: LazyListState,
    onFavClick: FavClickCallback,
    checkIsFavourite: CheckIsFavCallback,
    getLikesCount: GetLikesCountCallback,
    onErrorItemClicked: OnErrorItemClicked,
    onPostDeleteClick: OnPostDeleteCallback,
    isFavouritesReady: Boolean
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
    ) {
        items(itemList, key = { it.id }) { item ->
            when (item) {
                is CatCardData.CatsApi -> CatsApiCard(
                    item = item,
                    onFavClick = onFavClick,
                    checkIsFavourite = checkIsFavourite,
                    getLikesCount = getLikesCount,
                    isFavouritesReady = isFavouritesReady
                )

                is CatCardData.UserPost -> UserPostCard(
                    item = item,
                    isMyPost = getCurrentUser()?.uid == item.userId,
                    onFavClick = onFavClick,
                    checkIsFavourite = checkIsFavourite,
                    getLikesCount = getLikesCount,
                    isFavouritesReady = isFavouritesReady,
                    onPostDeleteCallback = { onPostDeleteClick?.invoke(item) }
                )
            }
        }

        if (isLoading && itemList.isNotEmpty()) {
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

        if (isError) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.search_screen_error_message),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (itemList.isNotEmpty()) {
                                    onErrorItemClicked?.invoke()
                                }
                            },
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
fun CatsApiCard(
    item: CatCardData.CatsApi,
    onFavClick: FavClickCallback,
    checkIsFavourite: CheckIsFavCallback,
    getLikesCount: GetLikesCountCallback?,
    isFavouritesReady: Boolean
) {
    val isActivated = checkIsFavourite(item.id)

    val isEnabled = rememberSaveable { mutableStateOf(true) }
    isEnabled.value = isEnabled.value && isFavouritesReady

    val likesCounter = getLikesCount?.invoke(item.id)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context = LocalContext.current)
                .data(item.url)
                .build(),
            contentDescription = stringResource(R.string.cats_card),
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth(),
            loading = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .shimmerEffect()
                )
            },
            error = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(R.drawable.ic_broken_image),
                        contentDescription = "Error"
                    )
                }
            },
            success = {
                SubcomposeAsyncImageContent()
            }
        )
        item.breeds.getNameAndDescription()?.let {
            ExpandableHeadingWithDetail(it.first, it.second)
        }
        FavouritesButton(
            isEnabled = isEnabled.value,
            isActivated = isActivated,
            onClick = {
                onClickFavouritesButton(
                    item,
                    isEnabled,
                    isActivated,
                    onFavClick
                )
            },
            likesCounter = likesCounter
        )
    }
}

@Composable
fun UserPostCard(
    item: CatCardData.UserPost,
    isMyPost: Boolean,
    onFavClick: FavClickCallback,
    checkIsFavourite: CheckIsFavCallback,
    getLikesCount: GetLikesCountCallback?,
    isFavouritesReady: Boolean,
    onPostDeleteCallback: (() -> Unit)? = null
) {
    val isActivated = checkIsFavourite(item.id)

    val isEnabled = rememberSaveable { mutableStateOf(true) }
    isEnabled.value = isEnabled.value && isFavouritesReady

    val likesCounter = getLikesCount?.invoke(item.id)
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    val cardColors = CardDefaults.cardColors().let {
        if (isMyPost) {
            it.copy(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            it
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(text = stringResource(R.string.delete_post_dialog_title))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onPostDeleteCallback?.invoke()
                    }
                ) {
                    Text(
                        text = stringResource(R.string.delete_post_dialog_confirm),
                        color = Color.Red
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(text = stringResource(R.string.delete_post_dialog_cancel))
                }
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(4.dp),
        colors = cardColors
    ) {
        // User info row: avatar + display name + delete button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context = LocalContext.current)
                    .data(item.avatarUrl)
                    .build(),
                contentDescription = "User avatar",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape),
                loading = {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .shimmerEffect()
                    )
                },
                error = {
                    androidx.compose.foundation.Image(
                        painter = painterResource(R.drawable.ic_broken_image),
                        contentDescription = "Error",
                        modifier = Modifier.size(32.dp)
                    )
                },
                success = {
                    SubcomposeAsyncImageContent()
                }
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.titleMedium
                )
                item.timestamp?.let { timestamp ->
                    val dateFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
                    val formattedDate = dateFormat.format(timestamp.toDate())
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (isMyPost) {
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.delete_post_dialog_confirm),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Post image
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context = LocalContext.current)
                .data(item.url)
                .build(),
            contentDescription = stringResource(R.string.cats_card),
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth(),
            loading = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .shimmerEffect()
                )
            },
            error = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(R.drawable.ic_broken_image),
                        contentDescription = "Error"
                    )
                }
            },
            success = {
                SubcomposeAsyncImageContent()
            }
        )

        // Post text
        if (item.text.isNotBlank()) {
            Text(
                text = item.text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            )
        }

        FavouritesButton(
            isEnabled = isEnabled.value,
            isActivated = isActivated,
            onClick = {
                onClickFavouritesButton(
                    item,
                    isEnabled,
                    isActivated,
                    onFavClick
                )
            },
            likesCounter = likesCounter
        )
    }
}

private fun onClickFavouritesButton(
    item: CatCardData,
    isEnabled: MutableState<Boolean>,
    isActivated: Boolean,
    onFavClick: FavClickCallback,
) {
    //val initialState = isActivated.value
    //isActivated.value = !isActivated.value
    isEnabled.value = false
    //likesCounter.updateState(initialState)

    onFavClick(!isActivated, item) {
        isEnabled.value = true
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
    isEnabled: Boolean,
    isActivated: Boolean,
    likesCounter: Long?,
    onClick: () -> Unit
) {
    val color by animateColorAsState(
        targetValue = if (isActivated) StarYellow else Color.Unspecified,
        label = "star color"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            enabled = isEnabled,
            onClick = onClick
        ) {
            Icon(
                painter = painterResource(id = R.drawable.rounded_star_icon),
                contentDescription = "Star Icon",
                tint = color,
                modifier = Modifier.size(20.dp)
            )
        }

        if (likesCounter != null) {
            Text(
                text = "$likesCounter",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Start,
                fontSize = 14.sp,
                modifier = Modifier.wrapContentSize()
            )
        }
    }
}

private fun Modifier.shimmerEffect(): Modifier = composed {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val transition = rememberInfiniteTransition(label = "shimmer")
    val startOffsetX by transition.animateFloat(
        initialValue = -2 * size.width.toFloat(),
        targetValue = 2 * size.width.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1000)
        ),
        label = "shimmer"
    )

    val shimmerColors = if (isSystemInDarkTheme()) {
        listOf(
            Color(0xFF3A3A3A),
            Color(0xFF505050),
            Color(0xFF3A3A3A),
        )
    } else {
        listOf(
            Color(0xFFE0E0E0),
            Color(0xFFB0B0B0),
            Color(0xFFE0E0E0),
        )
    }

    background(
        brush = Brush.linearGradient(
            colors = shimmerColors,
            start = Offset(startOffsetX, 0f),
            end = Offset(startOffsetX + size.width.toFloat(), size.height.toFloat())
        )
    ).onGloballyPositioned {
        size = it.size
    }
}

@Preview
@Composable
fun FavouritesButtonPreview() {
    CatgramTheme {
        FavouritesButton(true, false, 2, {})
    }
}

@Preview
@Composable
fun CatsApiCardPreview() {
    CatgramTheme {
        CatsApiCard(
            CatCardData.CatsApi("id", "url", listOf(BreedInfo("id", "name", "description"))),
            { _, _, _ -> },
            { true },
            { _ -> 0 },
            true
        )
    }
}

@Preview
@Composable
fun UserPostCardPreview() {
    CatgramTheme {
        UserPostCard(
            CatCardData.UserPost("id", "user id", "url", "text", "user", "avatar_url", Timestamp(0, 0)),
            isMyPost = true,
            onFavClick = { _, _, _ -> },
            checkIsFavourite = { true },
            getLikesCount = { _ -> 0 },
            true,
            onPostDeleteCallback = {}
        )
    }
}

sealed interface CatCardData {
    val id: String

    data class CatsApi(
        override val id: String,
        val url: String,
        val breeds: List<BreedInfo>
    ) : CatCardData

    data class UserPost(
        override val id: String,
        val userId: String,
        val url: String,
        val text: String,
        val displayName: String,
        val avatarUrl: String?,
        val timestamp: Timestamp?
    ) : CatCardData
}