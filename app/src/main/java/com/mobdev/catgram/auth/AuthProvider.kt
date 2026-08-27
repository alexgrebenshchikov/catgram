package com.mobdev.catgram.auth

import android.content.Context
import android.net.Uri
import android.annotation.SuppressLint
import androidx.activity.ComponentActivity
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.mobdev.catgram.R
import kotlinx.coroutines.tasks.await

interface AuthProvider {
    fun getCurrentUser(): FirebaseUser?
    fun getCurrentUserOrThrow(): FirebaseUser
    fun isSignedIn(): Boolean
    fun getAvatarUrl(context: Context): Uri?
    suspend fun signIn(activity: ComponentActivity): SignInResult
    suspend fun signOut(context: Context)
}

class FirebaseAuthProvider : AuthProvider {
    override fun getCurrentUser(): FirebaseUser? = Firebase.auth.currentUser

    override fun getCurrentUserOrThrow(): FirebaseUser =
        getCurrentUser() ?: error("User unauthorized")

    override fun isSignedIn(): Boolean = getCurrentUser() != null

    override fun getAvatarUrl(context: Context): Uri? = getCurrentUser()?.photoUrl

    @SuppressLint("CredentialManagerSignInWithGoogle") // Response types are checked immediately below.
    override suspend fun signIn(activity: ComponentActivity): SignInResult = runCatching {
        ensureGooglePlayServicesAvailable(activity)
        val googleOption = GetSignInWithGoogleOption.Builder(
            serverClientId = activity.getString(R.string.your_web_client_id),
        ).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleOption)
            .build()
        val credential = CredentialManager.create(activity)
            .getCredential(request = request, context = activity)
            .credential
        check(credential is CustomCredential) { "Unexpected credential type" }
        check(
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL ||
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_SIWG_CREDENTIAL
        ) { "Unexpected Google credential type" }

        val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
        val firebaseCredential = GoogleAuthProvider.getCredential(googleCredential.idToken, null)
        Firebase.auth.signInWithCredential(firebaseCredential).await()
        checkNotNull(Firebase.auth.currentUser) { "Firebase sign-in returned no user" }
    }.fold(
        onSuccess = { SignInResult.Succeed },
        onFailure = { SignInResult.Failed(it) },
    )

    override suspend fun signOut(context: Context) {
        Firebase.auth.signOut()
        if (isGooglePlayServicesAvailable(context)) {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        }
    }

    private fun ensureGooglePlayServicesAvailable(context: Context) {
        if (!isGooglePlayServicesAvailable(context)) {
            throw GooglePlayServicesUnavailableException(
                context.getString(R.string.google_play_services_unavailable),
            )
        }
    }

    private fun isGooglePlayServicesAvailable(context: Context): Boolean =
        GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(
                context,
                CREDENTIAL_MANAGER_MIN_GMS_VERSION,
            ) == ConnectionResult.SUCCESS

    companion object {
        // Minimum required by androidx.credentials:credentials-play-services-auth.
        private const val CREDENTIAL_MANAGER_MIN_GMS_VERSION = 230_815_045
    }
}

class GooglePlayServicesUnavailableException(message: String) : IllegalStateException(message)

sealed interface SignInResult {
    data object Succeed : SignInResult
    data class Failed(val error: Throwable) : SignInResult
}
