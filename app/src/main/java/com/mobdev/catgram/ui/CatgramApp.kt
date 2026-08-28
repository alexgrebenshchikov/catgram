package com.mobdev.catgram.ui

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Feed
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mobdev.catgram.Event
import com.mobdev.catgram.MainActivity
import com.mobdev.catgram.MainViewModel
import com.mobdev.catgram.R
import com.mobdev.catgram.ui.common.UpdateStatus
import com.mobdev.catgram.ui.common.UpdateStatusBanner
import com.mobdev.catgram.ui.screens.ActivityScreen
import com.mobdev.catgram.ui.screens.ActivityViewModel
import com.mobdev.catgram.ui.screens.FavouritesScreen
import com.mobdev.catgram.ui.screens.FavouritesViewModel
import com.mobdev.catgram.ui.screens.FeedScreen
import com.mobdev.catgram.ui.screens.PostDetailScreen
import com.mobdev.catgram.ui.screens.ProfileScreen
import com.mobdev.catgram.ui.screens.StartScreen

sealed class BottomNavScreen(val route: String, val labelResId: Int) {
    data object Feed : BottomNavScreen(FEED_ROUTE, R.string.feed_screen_label)
    data object Favourites : BottomNavScreen(FAVOURITES_ROUTE, R.string.favourites_screen_label)
    data object Activity : BottomNavScreen(ACTIVITY_ROUTE, R.string.activity_screen_label)
    data object Profile : BottomNavScreen(PROFILE_ROUTE, R.string.profile_screen_label)

    companion object {
        const val FEED_ROUTE = "feed"
        const val FAVOURITES_ROUTE = "favourites"
        const val ACTIVITY_ROUTE = "activity"
        const val PROFILE_ROUTE = "profile"
    }
}

private const val POST_ROUTE = "post"
private const val POST_ID_ARGUMENT = "postId"

@Composable
fun CatgramApp(
    uiState: MainActivity.UiState,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit,
    getUserInfoCallback: () -> UserInfo,
    deepLink: AppDeepLink?,
    onDeepLinkHandled: () -> Unit,
) {
    val screens = remember {
        listOf(
            BottomNavScreen.Feed,
            BottomNavScreen.Favourites,
            BottomNavScreen.Activity,
            BottomNavScreen.Profile,
        )
    }
    val navController = rememberNavController()
    val signedIn = uiState.signedIn.value
    val mainViewModel: MainViewModel = viewModel()
    val activityViewModel: ActivityViewModel = viewModel(factory = ActivityViewModel.factory)
    val favouritesViewModel: FavouritesViewModel = viewModel(factory = FavouritesViewModel.factory)
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val showBottomBar = signedIn && screens.any { it.route == currentRoute }
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

    LaunchedEffect(signedIn) {
        if (signedIn) {
            activityViewModel.initialize()
            favouritesViewModel.initialize()
        } else {
            activityViewModel.reset()
            favouritesViewModel.reset()
        }
    }

    LaunchedEffect(signedIn, deepLink) {
        if (!signedIn) return@LaunchedEffect
        when (val request = deepLink) {
            is AppDeepLink.Post -> {
                request.activityId?.let(activityViewModel::markRead)
                navController.navigate(postRoute(request.postId)) {
                    launchSingleTop = true
                }
                onDeepLinkHandled()
            }
            null -> Unit
        }
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                BottomNavigationBar(
                    screens = screens,
                    selectedRoute = currentRoute,
                    hasUnreadActivity = activityViewModel.hasUnread,
                    onItemSelected = { screen -> navController.navigateTopLevel(screen.route) },
                )
            }
        },
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            if (!signedIn) {
                StartScreen(
                    uiState = uiState,
                    onGoogleSignInClick = onSignInClick,
                )
            } else {
                NavHost(
                    navController = navController,
                    startDestination = BottomNavScreen.FEED_ROUTE,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    composable(BottomNavScreen.FEED_ROUTE) {
                        FeedScreen(
                            onPostClick = { navController.navigate(postRoute(it)) },
                            favouritesViewModel = favouritesViewModel,
                        )
                    }
                    composable(BottomNavScreen.FAVOURITES_ROUTE) {
                        FavouritesScreen(
                            favouritesViewModel = favouritesViewModel,
                            onPostClick = { navController.navigate(postRoute(it)) },
                        )
                    }
                    composable(BottomNavScreen.ACTIVITY_ROUTE) {
                        ActivityScreen(
                            viewModel = activityViewModel,
                            onPostClick = { navController.navigate(postRoute(it)) },
                        )
                    }
                    composable(BottomNavScreen.PROFILE_ROUTE) {
                        val userInfo = getUserInfoCallback()
                        ProfileScreen(
                            userInfo.avatarUrl,
                            userInfo.displayName ?: stringResource(R.string.unknown_user_name),
                            userInfo.email ?: stringResource(R.string.unknown_email),
                        ) {
                            activityViewModel.reset()
                            favouritesViewModel.reset()
                            navController.navigateTopLevel(BottomNavScreen.FEED_ROUTE)
                            onSignOutClick()
                            uiState.signedIn.value = false
                        }
                    }
                    composable(
                        route = "$POST_ROUTE/{$POST_ID_ARGUMENT}",
                        arguments = listOf(navArgument(POST_ID_ARGUMENT) { type = NavType.StringType }),
                    ) { entry ->
                        val postId = entry.arguments?.getString(POST_ID_ARGUMENT).orEmpty()
                        PostDetailScreen(
                            postId = postId,
                            sharedFavouritesViewModel = favouritesViewModel,
                            onBack = {
                                if (!navController.popBackStack()) {
                                    navController.navigateTopLevel(BottomNavScreen.FEED_ROUTE)
                                }
                            },
                        )
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
                    onDismiss = { updateStatus = null },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@Composable
fun BottomNavigationBar(
    screens: List<BottomNavScreen>,
    selectedRoute: String?,
    hasUnreadActivity: Boolean,
    onItemSelected: (BottomNavScreen) -> Unit,
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
        NavigationBarItemDefaults.colors()
        screens.forEach { screen ->
            NavigationBarItem(
                selected = selectedRoute == screen.route,
                onClick = { onItemSelected(screen) },
                label = { Text(stringResource(screen.labelResId)) },
                icon = {
                    BadgedBox(
                        badge = {
                            if (screen is BottomNavScreen.Activity && hasUnreadActivity) Badge()
                        },
                    ) {
                        Icon(
                            imageVector = when (screen) {
                                BottomNavScreen.Favourites -> Icons.Default.Star
                                BottomNavScreen.Activity -> Icons.Default.Notifications
                                BottomNavScreen.Profile -> Icons.Default.Person
                                BottomNavScreen.Feed -> Icons.AutoMirrored.Filled.Feed
                            },
                            contentDescription = null,
                        )
                    }
                },
            )
        }
    }
}

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun postRoute(postId: String): String = "$POST_ROUTE/${Uri.encode(postId)}"

data class UserInfo(val avatarUrl: Uri?, val displayName: String?, val email: String?)
