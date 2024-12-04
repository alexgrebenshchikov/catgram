package com.mobdev.catgram.ui

import android.app.Activity
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth

sealed class BottomNavScreen(val route: String, val label: String) {
    object Feed : BottomNavScreen("feed", "Feed")
    object Search : BottomNavScreen("search", "Search")
    object Favourites : BottomNavScreen("favourites", "Favourites")
    object Profile : BottomNavScreen("profile", "Profile")
}

@Composable
fun CatgramApp(auth: FirebaseAuth, activity: Activity) {
    val screens = listOf(
        BottomNavScreen.Feed,
        BottomNavScreen.Search,
        BottomNavScreen.Favourites,
        BottomNavScreen.Profile,
    )
    var selectedScreen by remember { mutableStateOf<BottomNavScreen>(BottomNavScreen.Feed) }
    var loggedIn by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            if (loggedIn) {
                BottomNavigationBar(
                    screens = screens,
                    selectedScreen = selectedScreen,
                    onItemSelected = { selectedScreen = it }
                )
            }
        }
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            if (!loggedIn) {
                SignUpForm(auth, activity) { authResult ->
                    loggedIn = authResult
                }
                return@Box
            }

            when (selectedScreen) {
                is BottomNavScreen.Feed -> FeedScreen()
                is BottomNavScreen.Search -> SearchScreen()
                is BottomNavScreen.Favourites -> FavouritesScreen()
                is BottomNavScreen.Profile -> ProfileScreen()
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

// Screen Composables
@Composable
fun FeedScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("This is the Feed Screen")
    }
}

@Composable
fun SearchScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("This is the Search Screen")
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

@Composable
fun ProfileScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("This is the Profile Screen")
    }
}

@Composable
fun SignUpForm(auth: FirebaseAuth, activity: Activity, onAuthStatusChanged: (Boolean) -> Unit) {
    // Remember state for both the email and the password input fields
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var validInput by remember { mutableStateOf(true) }

    // Form content
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Email Text Field
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(text = "Email") },
            modifier = Modifier.fillMaxWidth(),
            isError = !validInput
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Password Text Field
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(text = "Password") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            isError = !validInput
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Submit Button
        Button(
            onClick = {
                validInput = email.isNotEmpty() && password.isNotEmpty()
                if (validInput) {
                    auth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener(activity) { task ->
                            if (task.isSuccessful) {
                                // Sign in success, update UI with the signed-in user's information
                                val user = auth.currentUser
                                Toast.makeText(
                                    activity,
                                    "Authentication succeed. $user",
                                    Toast.LENGTH_SHORT,
                                ).show()
                                onAuthStatusChanged(true)
                            } else {
                                // If sign in fails, display a message to the user.
                                Log.d("GDFR", "${task.exception?.message}")
                                Toast.makeText(
                                    activity,
                                    "Authentication failed. ${task.exception?.message}",
                                    Toast.LENGTH_SHORT,
                                ).show()
                                onAuthStatusChanged(false)
                            }
                        }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text(text = "Submit")
        }

        // Error message if fields are invalid
        if (!validInput) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Please fill in both email and password.",
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}