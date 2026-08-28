package com.mobdev.catgram.ui.screens

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.mobdev.catgram.R
import com.mobdev.catgram.data.Comment
import com.mobdev.catgram.data.MAX_COMMENT_LENGTH
import com.mobdev.catgram.ui.common.UserPostCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    postId: String,
    onBack: () -> Unit,
    sharedFavouritesViewModel: FavouritesViewModel? = null,
) {
    val detailViewModel: PostDetailViewModel = viewModel(
        key = "post-detail:$postId",
        factory = PostDetailViewModel.factory(postId),
    )
    val favouritesViewModel: FavouritesViewModel =
        sharedFavouritesViewModel ?: viewModel(factory = FavouritesViewModel.factory)
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var draft by rememberSaveable(postId, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }

    LaunchedEffect(detailViewModel.commentError) {
        detailViewModel.commentError?.let {
            snackbarHostState.showSnackbar(it)
            detailViewModel.clearCommentError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.post_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (detailViewModel.post != null) {
                CommentComposer(
                    value = draft,
                    isSubmitting = detailViewModel.isSubmitting,
                    onValueChange = { draft = it },
                    onSend = {
                        detailViewModel.addComment(draft.text) {
                            draft = TextFieldValue()
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        when {
            detailViewModel.isLoading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            detailViewModel.error != null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        when (detailViewModel.error) {
                            PostDetailViewModel.DetailError.NOT_FOUND ->
                                stringResource(R.string.post_not_available)
                            else -> stringResource(R.string.post_load_failed)
                        }
                    )
                    if (detailViewModel.error == PostDetailViewModel.DetailError.LOAD_FAILED) {
                        Button(onClick = detailViewModel::retry) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
            }

            else -> detailViewModel.post?.let { post ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "post") {
                        UserPostCard(
                            item = post,
                            isMyPost = post.userId == favouritesViewModel.currentUser?.uid,
                            onFavClick = { shouldAdd, item ->
                                if (shouldAdd) favouritesViewModel.addToFavourites(item)
                                else favouritesViewModel.removeFromFavourites(item)
                            },
                            checkIsFavourite = favouritesViewModel::checkInFavourites,
                            getLikesCount = favouritesViewModel::getLikesCount,
                            onPostDeleteCallback = null,
                            checkIsEnabledCallback = { id ->
                                favouritesViewModel.isReady &&
                                    !favouritesViewModel.checkIsUpdating(id)
                            },
                            onCommentsClick = null,
                        )
                    }
                    item(key = "comments-heading") {
                        Text(
                            text = stringResource(
                                R.string.comments_heading,
                                detailViewModel.comments.size,
                            ),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    if (detailViewModel.hasOlderComments) {
                        item(key = "comments-load-older") {
                            TextButton(
                                onClick = detailViewModel::loadOlderComments,
                                enabled = !detailViewModel.isLoadingOlderComments,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (detailViewModel.isLoadingOlderComments) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(stringResource(R.string.comments_load_older))
                            }
                        }
                    }
                    if (detailViewModel.comments.isEmpty()) {
                        item(key = "comments-empty") {
                            Text(
                                text = stringResource(R.string.comments_empty),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(detailViewModel.comments, key = Comment::id) { comment ->
                        val currentUid = favouritesViewModel.currentUser?.uid
                        CommentRow(
                            comment = comment,
                            canDelete = comment.authorUid == currentUid,
                            onDelete = { detailViewModel.deleteComment(comment, currentUid) },
                        )
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }
}

@Composable
private fun CommentComposer(
    value: TextFieldValue,
    isSubmitting: Boolean,
    onValueChange: (TextFieldValue) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                if (it.text.length <= MAX_COMMENT_LENGTH) onValueChange(it)
            },
            label = { Text(stringResource(R.string.comment_hint)) },
            singleLine = false,
            maxLines = 4,
            supportingText = { Text("${value.text.length}/$MAX_COMMENT_LENGTH") },
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(4.dp))
        IconButton(
            onClick = onSend,
            enabled = value.text.isNotBlank() && !isSubmitting,
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    modifier = Modifier.size(24.dp),
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.comment_send),
                )
            }
        }
    }
}

@Composable
private fun CommentRow(
    comment: Comment,
    canDelete: Boolean,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        AsyncImage(
            model = comment.authorAvatarUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(comment.authorName, style = MaterialTheme.typography.labelLarge)
            Text(comment.text, style = MaterialTheme.typography.bodyMedium)
            comment.createdAt?.let { timestamp ->
                Text(
                    text = DateUtils.getRelativeTimeSpanString(
                        timestamp.toDate().time,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS,
                    ).toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (canDelete) {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.comment_delete),
                )
            }
        }
    }
}
