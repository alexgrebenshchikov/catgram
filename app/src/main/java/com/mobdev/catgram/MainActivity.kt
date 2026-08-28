package com.mobdev.catgram

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.mobdev.catgram.auth.SignInResult
import com.mobdev.catgram.logging.logger
import com.mobdev.catgram.notifications.ActivityNotificationCoordinator
import com.mobdev.catgram.ui.CatgramApp
import com.mobdev.catgram.ui.AppDeepLink
import com.mobdev.catgram.ui.AppDeepLinks
import com.mobdev.catgram.ui.UserInfo
import com.mobdev.catgram.ui.theme.CatgramTheme
import com.mobdev.catgram.worker.scheduleActivityNotificationWorker
import com.mobdev.catgram.worker.scheduleNewPostsNotificationWorker
import com.mobdev.catgram.worker.scheduleOpenAppReminder
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow


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
    private val deepLinkRequest = MutableStateFlow<AppDeepLink?>(null)
    private val mainViewModel: MainViewModel by viewModels()
    private val authProvider by lazy {
        (application as CatgramApplication).container.authProvider
    }
    private val activityNotificationCoordinator by lazy {
        val container = (application as CatgramApplication).container
        ActivityNotificationCoordinator(
            applicationContext,
            container.authProvider,
            container.activityRepository,
        )
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logger.d("Main activity created; signedIn=${Firebase.auth.currentUser != null}")
        uiState.signedIn.value = authProvider.isSignedIn()
        deepLinkRequest.value = AppDeepLinks.fromIntent(intent)
        if (uiState.signedIn.value) activityNotificationCoordinator.start(lifecycleScope)
        mainViewModel.askForPostNotificationsPermissionIfNeeded(this, ::askNotificationPermission)
        scheduleOpenAppReminder(this.applicationContext)
        scheduleNewPostsNotificationWorker(this.applicationContext)
        scheduleActivityNotificationWorker(this.applicationContext)
        checkForUpdates()
        enableEdgeToEdge()
        setContent {
            val deepLink by deepLinkRequest.collectAsState()
            CatgramTheme {
                CatgramApp(
                    uiState = uiState,
                    onSignInClick = ::signIn,
                    onSignOutClick = ::signOut,
                    getUserInfoCallback = {
                        val user = authProvider.getCurrentUserOrThrow()
                        UserInfo(
                            authProvider.getAvatarUrl(this),
                            user.displayName,
                            user.email
                        )
                    },
                    deepLink = deepLink,
                    onDeepLinkHandled = { deepLinkRequest.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkRequest.value = AppDeepLinks.fromIntent(intent)
    }

    private fun signIn() {
        lifecycleScope.launch {
            updateUiAfterSignIn(authProvider.signIn(this@MainActivity))
        }
    }

    private fun signOut() {
        activityNotificationCoordinator.stop()
        lifecycleScope.launch {
            runCatching { authProvider.signOut(this@MainActivity) }
                .onFailure { logger.e("Credential sign-out failed", it) }
        }
    }

    private fun updateUiAfterSignIn(result: SignInResult) {
        when (result) {
            SignInResult.Succeed -> {
                uiState.signedIn.value = true
                activityNotificationCoordinator.start(lifecycleScope)
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

    private fun checkForUpdates() {
        mainViewModel.startCheckForUpdates(this)
    }

    data class UiState(
        var signedIn: MutableState<Boolean> = mutableStateOf(false),
        var signInInProgress: MutableState<Boolean> = mutableStateOf(false)
    )
}
