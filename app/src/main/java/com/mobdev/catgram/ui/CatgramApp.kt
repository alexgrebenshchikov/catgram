package com.mobdev.catgram.ui

import android.content.Intent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.mobdev.catgram.MainActivity
import com.mobdev.catgram.R
import com.mobdev.catgram.TAG
import com.mobdev.catgram.auth.signInViaGoogle
import com.mobdev.catgram.auth.signOut
import com.mobdev.catgram.ui.BottomNavScreen.Companion.FAVOURITES_SCREEN_LABEL
import com.mobdev.catgram.ui.BottomNavScreen.Companion.PROFILE_SCREEN_LABEL
import com.mobdev.catgram.ui.BottomNavScreen.Companion.SEARCH_SCREEN_LABEL
import com.mobdev.catgram.ui.screens.FavouritesScreen
import com.mobdev.catgram.ui.screens.FavouritesViewModel
import com.mobdev.catgram.ui.screens.ProfileScreen
import com.mobdev.catgram.ui.screens.SearchScreen
import com.mobdev.catgram.ui.screens.SearchViewModel
import com.mobdev.catgram.ui.screens.StartScreen

sealed class BottomNavScreen(val route: String, val label: String) {
    data object Search : BottomNavScreen("search", SEARCH_SCREEN_LABEL)
    data object Favourites : BottomNavScreen("favourites", FAVOURITES_SCREEN_LABEL)
    data object Profile : BottomNavScreen("profile", PROFILE_SCREEN_LABEL)

    companion object {
        const val SEARCH_SCREEN_LABEL = "Search"
        const val FAVOURITES_SCREEN_LABEL = "Favourites"
        const val PROFILE_SCREEN_LABEL = "Profile"
    }
}

@Composable
fun CatgramApp(
    activity: ComponentActivity,
    signInLauncher: ActivityResultLauncher<Intent>,
    uiState: MainActivity.UiState
) {
    val screens = listOf(
        BottomNavScreen.Search,
        BottomNavScreen.Favourites,
        BottomNavScreen.Profile,
    )

    val customSaver = Saver<BottomNavScreen, String>(
        save = {
            it.label
        },
        restore = { when(it) {
            SEARCH_SCREEN_LABEL -> BottomNavScreen.Search
            FAVOURITES_SCREEN_LABEL -> BottomNavScreen.Favourites
            PROFILE_SCREEN_LABEL -> BottomNavScreen.Profile
            else -> throw IllegalStateException("Should not reach.")
        } }
    )

    var selectedScreen by rememberSaveable(
        stateSaver = customSaver
    ) { mutableStateOf(BottomNavScreen.Search) }
    val signedIn = uiState.signedIn.value

    val favViewModel: FavouritesViewModel = viewModel()
    val searchViewModel: SearchViewModel = viewModel(factory = SearchViewModel.factory)

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
                StartScreen(uiState) {
                    signInViaGoogle(activity, signInLauncher)
                }
                return@Box
            }

            when (selectedScreen) {
                is BottomNavScreen.Search -> SearchScreen()
                is BottomNavScreen.Favourites -> FavouritesScreen()
                is BottomNavScreen.Profile -> {
                    val avatarUrl = GoogleSignIn.getLastSignedInAccount(activity)?.photoUrl
                    val user = Firebase.auth.currentUser
                        ?: throw IllegalStateException("User unauthorized.")
                    ProfileScreen(
                        avatarUrl,
                        user.displayName ?: stringResource(R.string.unknown_user_name),
                        user.email ?: stringResource(
                            R.string.unknown_email
                        )
                    ) {
                        searchViewModel.reset()
                        favViewModel.reset()
                        signOut()
                        uiState.signedIn.value = false
                        selectedScreen = BottomNavScreen.Search
                    }
                }
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
                    Text(screen.label)
                },
                icon = {
                    when(screen) {
                        BottomNavScreen.Favourites -> {
                            Icon(imageVector = Icons.Default.Star, contentDescription = null)
                        }
                        BottomNavScreen.Profile -> {
                            Icon(imageVector = Icons.Default.Person, contentDescription = null)
                        }
                        BottomNavScreen.Search -> {
                            Icon(imageVector = Icons.Default.Search, contentDescription = null)
                        }
                    }
                },
            )
        }
    }
}
