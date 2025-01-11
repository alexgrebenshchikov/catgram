package com.mobdev.catgram.ui

import android.content.Intent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.mobdev.catgram.MainActivity
import com.mobdev.catgram.auth.signInViaGoogle
import com.mobdev.catgram.ui.screens.ProfileScreen
import com.mobdev.catgram.ui.screens.SearchScreen
import com.mobdev.catgram.ui.screens.StartScreen

sealed class BottomNavScreen(val route: String, val label: String) {
    object Search : BottomNavScreen("search", "Search")
    object Favourites : BottomNavScreen("favourites", "Favourites")
    object Profile : BottomNavScreen("profile", "Profile")
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
    var selectedScreen by remember { mutableStateOf<BottomNavScreen>(BottomNavScreen.Search) }
    //var loggedIn by remember { mutableStateOf(false) }
    val signedIn = uiState.signedIn.value
    //loggedIn = isSignedIn()

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
                    Log.d("GDFR", "avater url: $avatarUrl")
                    val user = Firebase.auth.currentUser ?: throw IllegalStateException("User unauthorized.")
                    ProfileScreen(avatarUrl, user.displayName ?: "?", user.email ?: "?") {
                        Firebase.auth.signOut()
                        uiState.signedIn.value = false
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
        containerColor = Color.White
    ) {
        screens.forEach { screen ->
            NavigationBarItem(
                selected = selectedScreen == screen,
                onClick = { onItemSelected(screen) },
                label = {
                    Text(screen.label)
                },
                icon = {
                    // Replace with actual icons if needed
                    if (selectedScreen == screen) {
                        Text("★")
                    } else {
                        Text("☆")
                    }
                },
            )
        }
    }
}

@Composable
fun FavouritesScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("This is the Favourites Screen")
    }
}
