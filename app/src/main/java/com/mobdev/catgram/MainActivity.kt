package com.mobdev.catgram

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import com.mobdev.catgram.logging.logger
import com.mobdev.catgram.ui.CatgramApp
import com.mobdev.catgram.ui.UserInfo
import com.mobdev.catgram.ui.screens.FavouritesViewModel
import com.mobdev.catgram.ui.screens.FeedViewModel
import com.mobdev.catgram.ui.theme.CatgramTheme
import com.mobdev.catgram.worker.scheduleOpenAppReminder
import kotlinx.coroutines.launch


class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(
                this,
                resources.getString(R.string.notification_granted_toast),
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(
                this,
                resources.getString(R.string.notification_not_granted_toast),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private val uiState = UiState()
    private val mainViewModel: MainViewModel by viewModels()
    private val authProvider by lazy {
        (application as CatgramApplication).container.authProvider
    }
    private val signInLauncher by lazy {
        authProvider.createSignInLauncher(this, ::updateUiAfterSignIn)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logger.d("${Firebase.auth.currentUser?.uid}")
        uiState.signedIn.value = authProvider.isSignedIn()
        mainViewModel.askForPostNotificationsPermissionIfNeeded(this, ::askNotificationPermission)
        scheduleOpenAppReminder(this.applicationContext)
        checkForUpdates()
        enableEdgeToEdge()
        setContent {
            CatgramTheme {
                CatgramApp(
                    uiState = uiState,
                    onSignInClick = { authProvider.signIn(this, signInLauncher) },
                    onSignOutClick = { authProvider.signOut() },
                    getUserInfoCallback = {
                        val user = authProvider.getCurrentUserOrThrow()
                        UserInfo(
                            authProvider.getAvatarUrl(this),
                            user.displayName,
                            user.email
                        )
                    }
                )
            }
        }
    }

    private fun updateUiAfterSignIn(result: SignInResult) {
        when (result) {
            SignInResult.Succeed -> {
                uiState.signedIn.value = true
                val feedViewModel: FeedViewModel by viewModels { FeedViewModel.factory }
                feedViewModel.loadFilterState()
                val favViewModel: FavouritesViewModel by viewModels { FavouritesViewModel.factory }
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
                return@OnCompleteListener
            }

            // Get new FCM registration token
            val token = task.result

            // Log and toast
            val msg = "FCM token: $token"
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

    private fun showDialogForCompleteUpdate() {
        logger.d("update completed")
        uiState.needToShowSnackbar.value = true
    }

    data class UiState(
        var signedIn: MutableState<Boolean> = mutableStateOf(false),
        var signInInProgress: MutableState<Boolean> = mutableStateOf(false),
        var needToShowSnackbar: MutableState<Boolean> = mutableStateOf(false)
    )
}
