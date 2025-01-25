package com.mobdev.catgram

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.mobdev.catgram.auth.SignInResult
import com.mobdev.catgram.auth.createSignInLauncher
import com.mobdev.catgram.auth.isSignedIn
import com.mobdev.catgram.ui.CatgramApp
import com.mobdev.catgram.ui.screens.FavouritesViewModel
import com.mobdev.catgram.ui.screens.SearchViewModel
import com.mobdev.catgram.ui.theme.CatgramTheme
import com.mobdev.catgram.worker.scheduleOpenAppReminder
import kotlinx.coroutines.launch


class MainActivity : ComponentActivity() {
    private val signInLauncher = createSignInLauncher(this, ::updateUiAfterSignIn)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "Notifications permission granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Notifications permission not granted", Toast.LENGTH_SHORT).show()
        }
    }

    private val uiState = UiState()
    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "${Firebase.auth.currentUser?.uid}")
        uiState.signedIn.value = isSignedIn()
        askNotificationPermission()
        scheduleOpenAppReminder(this.applicationContext)
        checkForUpdates()
        startReviewFlow()
        //getFCMToken()
        enableEdgeToEdge()
        setContent {
            CatgramTheme {
                CatgramApp(this, signInLauncher, uiState)
            }
        }
    }

    private fun updateUiAfterSignIn(result: SignInResult) {
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

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun getFCMToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                return@OnCompleteListener
            }

            // Get new FCM registration token
            val token = task.result

            // Log and toast
            val msg = "FCM token: $token"
            Log.d(TAG, msg)
            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
        })
    }

    private fun checkForUpdates() {
        mainViewModel.startCheckForUpdates(this)

        lifecycleScope.launch {
            mainViewModel.events
                .flowWithLifecycle(lifecycle)
                .collect { event ->
                    when (event) {
                        Event.UpdateCompleted -> showDialogForCompleteUpdate()
                    }
                }
        }
    }

    private fun startReviewFlow() {
        mainViewModel.startReviewFlow(this)
    }

    private fun showDialogForCompleteUpdate() {
        Log.d(TAG, "update completed")
        uiState.needToShowSnackbar.value = true
    }

    data class UiState(
        var signedIn: MutableState<Boolean> = mutableStateOf(false),
        var signInInProgress: MutableState<Boolean> = mutableStateOf(false),
        var needToShowSnackbar: MutableState<Boolean> = mutableStateOf(false)
    )
}
