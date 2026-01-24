package com.mobdev.catgram.ui

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Feed
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mobdev.catgram.Event
import com.mobdev.catgram.MainActivity
import com.mobdev.catgram.MainViewModel
import com.mobdev.catgram.R
import com.mobdev.catgram.ui.BottomNavScreen.Companion.FAVOURITES_SCREEN_ID
import com.mobdev.catgram.ui.BottomNavScreen.Companion.FEED_SCREEN_ID
import com.mobdev.catgram.ui.BottomNavScreen.Companion.PROFILE_SCREEN_ID
import com.mobdev.catgram.ui.common.UpdateStatus
import com.mobdev.catgram.ui.common.UpdateStatusBanner
import com.mobdev.catgram.ui.screens.FavouritesScreen
import com.mobdev.catgram.ui.screens.FavouritesViewModel
import com.mobdev.catgram.ui.screens.FeedScreen
import com.mobdev.catgram.ui.screens.FeedViewModel
import com.mobdev.catgram.ui.screens.ProfileScreen
import com.mobdev.catgram.ui.screens.StartScreen

sealed class BottomNavScreen(val id: String, val labelResId: Int) {
    data object Feed : BottomNavScreen(FEED_SCREEN_ID, R.string.feed_screen_label)
    data object Favourites : BottomNavScreen(FAVOURITES_SCREEN_ID, R.string.favourites_screen_label)
    data object Profile : BottomNavScreen(PROFILE_SCREEN_ID, R.string.profile_screen_label)

    companion object {
        const val FEED_SCREEN_ID = "Feed"
        const val FAVOURITES_SCREEN_ID = "Favourites"
        const val PROFILE_SCREEN_ID = "Profile"
    }
}

@Composable
fun CatgramApp(
    uiState: MainActivity.UiState,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit,
    getUserInfoCallback: () -> UserInfo
) {
    val screens = listOf(
        BottomNavScreen.Feed,
        BottomNavScreen.Favourites,
        BottomNavScreen.Profile,
    )

    val customSaver = Saver<BottomNavScreen, String>(
        save = {
            it.id
        },
        restore = {
            when (it) {
                FEED_SCREEN_ID -> BottomNavScreen.Feed
                FAVOURITES_SCREEN_ID -> BottomNavScreen.Favourites
                PROFILE_SCREEN_ID -> BottomNavScreen.Profile
                else -> throw IllegalStateException("Should not reach.")
            }
        }
    )

    var selectedScreen by rememberSaveable(
        stateSaver = customSaver
    ) { mutableStateOf(BottomNavScreen.Feed) }
    val signedIn = uiState.signedIn.value

    val favViewModel: FavouritesViewModel = viewModel(factory = FavouritesViewModel.factory)
    val feedViewModel: FeedViewModel = viewModel(factory = FeedViewModel.factory)
    val mainViewModel: MainViewModel = viewModel()

    var updateStatus by remember { mutableStateOf<UpdateStatus?>(null) }

    LaunchedEffect(Unit) {
        mainViewModel.events.collect { event ->
            updateStatus = when (event) {
                is Event.DownloadProgress -> UpdateStatus.Downloading(event.percent)
                is Event.UpdateDownloaded -> UpdateStatus.ReadyToInstall
                is Event.UpdateFailed -> UpdateStatus.Failed
            }
        }
    }

    Scaffold(
        bottomBar = {
            if (signedIn) {
                BottomNavigationBar(
                    screens = screens,
                    selectedScreen = selectedScreen,
                    onItemSelected = { selectedScreen = it }
                )
            }
        }
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (!signedIn) {
                StartScreen(
                    uiState = uiState,
                    onGoogleSignInClick = onSignInClick
                )
            } else {
                when (selectedScreen) {
                    is BottomNavScreen.Feed -> FeedScreen()
                    is BottomNavScreen.Favourites -> FavouritesScreen()
                    is BottomNavScreen.Profile -> {
                        val userInfo = getUserInfoCallback()
                        ProfileScreen(
                            userInfo.avatarUrl,
                            userInfo.displayName ?: stringResource(R.string.unknown_user_name),
                            userInfo.email ?: stringResource(R.string.unknown_email)
                        ) {
                            feedViewModel.reset()
                            favViewModel.reset()
                            onSignOutClick()
                            uiState.signedIn.value = false
                            selectedScreen = BottomNavScreen.Feed
                        }
                    }
                }
            }

            updateStatus?.let { status ->
                UpdateStatusBanner(
                    status = status,
                    onInstallClick = {
                        mainViewModel.completeUpdateRequested()
                        updateStatus = null
                    },
                    onDismiss = {
                        updateStatus = null
                    },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

@Composable
fun BottomNavigationBar(
    screens: List<BottomNavScreen>,
    selectedScreen: BottomNavScreen,
    onItemSelected: (BottomNavScreen) -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.background
    ) {
        NavigationBarItemDefaults.colors()
        screens.forEach { screen ->
            NavigationBarItem(
                selected = selectedScreen == screen,
                onClick = { onItemSelected(screen) },
                label = {
                    Text(stringResource(screen.labelResId))
                },
                icon = {
                    when (screen) {
                        BottomNavScreen.Favourites -> {
                            Icon(imageVector = Icons.Default.Star, contentDescription = null)
                        }

                        BottomNavScreen.Profile -> {
                            Icon(imageVector = Icons.Default.Person, contentDescription = null)
                        }

                        BottomNavScreen.Feed -> {
                            Icon(imageVector = Icons.Default.Feed, contentDescription = null)
                        }
                    }
                },
            )
        }
    }
}

data class UserInfo(val avatarUrl: Uri?, val displayName: String?, val email: String?)
