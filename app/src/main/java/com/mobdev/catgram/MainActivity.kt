package com.mobdev.catgram

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.mobdev.catgram.auth.SignInResult
import com.mobdev.catgram.auth.createSignInLauncher
import com.mobdev.catgram.auth.isSignedIn
import com.mobdev.catgram.ui.CatgramApp
import com.mobdev.catgram.ui.theme.CatgramTheme


class MainActivity : ComponentActivity() {
    private val signInLauncher = createSignInLauncher(this) { result ->
        when (result) {
            SignInResult.Succeed -> uiState.signedIn.value = true
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
        Log.d(TAG, "${Firebase.auth.currentUser}")
        uiState.signedIn.value = isSignedIn()
        enableEdgeToEdge()
        setContent {
            CatgramTheme {
                CatgramApp(this, signInLauncher, uiState)
            }
        }
    }

    data class UiState(var signedIn: MutableState<Boolean> = mutableStateOf(false),
                       var signInInProgress: MutableState<Boolean> = mutableStateOf(false))
}
