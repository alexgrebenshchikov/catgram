package com.mobdev.catgram.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mobdev.catgram.CatgramApplication
import com.mobdev.catgram.data.Comment
import com.mobdev.catgram.data.CommentCursor
import com.mobdev.catgram.data.CommentsRepository
import com.mobdev.catgram.data.MAX_COMMENT_LENGTH
import com.mobdev.catgram.data.UserPostsRepository
import com.mobdev.catgram.data.cursorOrNull
import com.mobdev.catgram.logging.logger
import com.mobdev.catgram.ui.common.CatCardData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PostDetailViewModel(
    private val postId: String,
    private val userPostsRepository: UserPostsRepository,
    private val commentsRepository: CommentsRepository,
) : ViewModel() {
    var post by mutableStateOf<CatCardData.UserPost?>(null)
        private set
    var comments by mutableStateOf<List<Comment>>(emptyList())
        private set
    var isLoading by mutableStateOf(true)
        private set
    var isSubmitting by mutableStateOf(false)
        private set
    var isLoadingOlderComments by mutableStateOf(false)
        private set
    var hasOlderComments by mutableStateOf(false)
        private set
    var error by mutableStateOf<DetailError?>(null)
        private set
    var commentError by mutableStateOf<String?>(null)
        private set

    private var latestComments: List<Comment> = emptyList()
    private var olderComments: List<Comment> = emptyList()
    private val commentRangeStart = MutableStateFlow<CommentCursor?>(null)
    private var loadJob: Job? = null

    init {
        load()
    }

    fun retry() {
        load()
    }

    fun addComment(text: String, onSuccess: () -> Unit) {
        val normalized = text.trim()
        when {
            normalized.isEmpty() -> {
                commentError = "Comment must not be empty"
                return
            }
            normalized.length > MAX_COMMENT_LENGTH -> {
                commentError = "Comment is too long"
                return
            }
        }

        viewModelScope.launch {
            isSubmitting = true
            commentError = null
            try {
                commentsRepository.addComment(postId, normalized)
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logger.e("add comment failed: ${e.message}", e)
                commentError = "Failed to post comment"
            } finally {
                isSubmitting = false
            }
        }
    }

    fun deleteComment(comment: Comment, currentUid: String?) {
        if (currentUid == null || comment.authorUid != currentUid) return
        viewModelScope.launch {
            val previousLatest = latestComments
            val previousOlder = olderComments
            latestComments = latestComments.filterNot { it.id == comment.id }
            olderComments = olderComments.filterNot { it.id == comment.id }
            rebuildComments()
            try {
                commentsRepository.deleteComment(postId, comment.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logger.e("delete comment failed: ${e.message}", e)
                latestComments = previousLatest
                olderComments = previousOlder
                rebuildComments()
                commentError = "Failed to delete comment"
            }
        }
    }

    fun loadOlderComments() {
        if (isLoadingOlderComments || !hasOlderComments) return
        val oldestCursor = comments.firstNotNullOfOrNull(Comment::cursorOrNull) ?: return
        viewModelScope.launch {
            isLoadingOlderComments = true
            try {
                val page = commentsRepository.getOlderComments(
                    postId = postId,
                    before = oldestCursor,
                    limit = COMMENT_PAGE_SIZE.toLong(),
                )
                olderComments = (page.items + olderComments).distinctBy(Comment::id)
                hasOlderComments = page.hasMore
                rebuildComments()
                comments.firstNotNullOfOrNull(Comment::cursorOrNull)?.let {
                    commentRangeStart.value = it
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logger.e("load older comments failed: ${e.message}", e)
                commentError = "Failed to load older comments"
            } finally {
                isLoadingOlderComments = false
            }
        }
    }

    fun clearCommentError() {
        commentError = null
    }

    private fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            isLoading = true
            error = null
            latestComments = emptyList()
            olderComments = emptyList()
            comments = emptyList()
            hasOlderComments = false
            commentRangeStart.value = null
            try {
                val loadedPost = userPostsRepository.getUserPost(postId)
                if (loadedPost == null) {
                    post = null
                    error = DetailError.NOT_FOUND
                    return@launch
                }
                post = CatCardData.UserPost(
                    id = loadedPost.id,
                    userId = loadedPost.userId,
                    url = loadedPost.url,
                    text = loadedPost.text,
                    displayName = loadedPost.displayName,
                    avatarUrl = loadedPost.avatarUrl,
                    timestamp = loadedPost.timestamp,
                )
                isLoading = false
                commentRangeStart.collectLatest { rangeStart ->
                    commentsRepository.observeComments(
                        postId = postId,
                        limit = COMMENT_PAGE_SIZE.toLong(),
                        fromInclusive = rangeStart,
                    ).collect { snapshot ->
                        latestComments = snapshot
                        if (rangeStart == null) {
                            hasOlderComments = snapshot.size >= COMMENT_PAGE_SIZE
                        } else {
                            // The ranged listener now covers everything that was
                            // previously held in the older-page buffer.
                            olderComments = emptyList()
                        }
                        rebuildComments()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logger.e("load post detail failed: ${e.message}", e)
                if (post == null) {
                    error = DetailError.LOAD_FAILED
                } else {
                    commentError = "Failed to load comments"
                }
            } finally {
                isLoading = false
            }
        }
    }

    private fun rebuildComments() {
        comments = (olderComments + latestComments)
            .distinctBy(Comment::id)
            .sortedWith(
                compareBy<Comment> { it.createdAt?.seconds ?: Long.MAX_VALUE }
                    .thenBy { it.createdAt?.nanoseconds ?: Int.MAX_VALUE }
                    .thenBy(Comment::id),
            )
    }

    enum class DetailError {
        NOT_FOUND,
        LOAD_FAILED,
    }

    companion object {
        private const val COMMENT_PAGE_SIZE = 50
        fun factory(postId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as CatgramApplication).container
                PostDetailViewModel(
                    postId = postId,
                    userPostsRepository = container.userPostsRepository,
                    commentsRepository = container.commentsRepository,
                )
            }
        }
    }
}
