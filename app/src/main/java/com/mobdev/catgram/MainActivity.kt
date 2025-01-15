package com.mobdev.catgram

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.mobdev.catgram.auth.SignInResult
import com.mobdev.catgram.auth.createSignInLauncher
import com.mobdev.catgram.auth.isSignedIn
import com.mobdev.catgram.ui.CatgramApp
import com.mobdev.catgram.ui.screens.FavouritesViewModel
import com.mobdev.catgram.ui.screens.SearchViewModel
import com.mobdev.catgram.ui.theme.CatgramTheme


class MainActivity : ComponentActivity() {
    private val signInLauncher = createSignInLauncher(this) { result ->
        when (result) {
            SignInResult.Succeed -> {
                uiState.signedIn.value = true
                val searchViewModel: SearchViewModel by viewModels { SearchViewModel.factory }
                searchViewModel.loadChoosedBreeds()
                val favViewModel: FavouritesViewModel by viewModels()
                favViewModel.initialize()
            }
            is SignInResult.Failed -> {
                Toast.makeText(
                    this,
                    result.error.message
                        ?: getString(R.string.sign_in_default_error),
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        uiState.signInInProgress.value = false
    }

    private val uiState = UiState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "${Firebase.auth.currentUser?.uid}")
        uiState.signedIn.value = isSignedIn()
        enableEdgeToEdge()
        setContent {
            CatgramTheme {
                CatgramApp(this, signInLauncher, uiState)
            }
        }

        /*val currentUser = Firebase.auth.currentUser ?: throw IllegalStateException("cock")

        val firestore = Firebase.firestore
        val userDocRef = firestore.collection("users").document(currentUser.uid)

        val userData = hashMapOf(
            "first" to "Ada",
            "last" to "Lovelace",
            "born" to 1815,
        )
        // Add a new document with a generated ID
        /*userDocRef.set(userData)
            .addOnSuccessListener {
                Log.d(TAG, "DocumentSnapshot added")
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Error adding document", e)
            }*/
        userDocRef.get()
            .addOnSuccessListener { result ->
                Log.d(TAG, "DocumentSnapshot retrieved ${result["born"]}")
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Error adding document", e)
            }*/

    }

    data class UiState(var signedIn: MutableState<Boolean> = mutableStateOf(false),
                       var signInInProgress: MutableState<Boolean> = mutableStateOf(false))
}
