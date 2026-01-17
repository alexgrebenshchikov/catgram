package com.mobdev.catgram.auth

import android.app.Activity.RESULT_OK
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.mobdev.catgram.R
import com.mobdev.catgram.logging.logger

fun getCurrentUser() = Firebase.auth.currentUser

fun getCurrentUserOrThrow() =
    Firebase.auth.currentUser ?: throw IllegalStateException("User unauthorized")

fun isSignedIn() = getCurrentUser() != null

fun signInViaGoogle(
    activity: ComponentActivity,
    signInLauncher: ActivityResultLauncher<Intent>,
) {
    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(activity.getString(R.string.your_web_client_id))
        .requestEmail()
        .build()
    val googleSignInClient = GoogleSignIn.getClient(activity, gso)

    googleSignInClient.signOut()
    signInLauncher.launch(googleSignInClient.signInIntent)
}

fun signOut() = Firebase.auth.signOut()

fun createSignInLauncher(
    activity: ComponentActivity,
    resultCallback: (SignInResult) -> Unit
): ActivityResultLauncher<Intent> {
    return activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        logger.d("act result: ${result.resultCode} ${result.data}")
        if (result.resultCode == RESULT_OK && result.data != null) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(
                    ApiException::class.java
                )
                val idToken = account.idToken
                when {
                    idToken != null -> {
                        val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                        val auth = Firebase.auth
                        auth.signInWithCredential(firebaseCredential)
                            .addOnCompleteListener(activity) { firebaseTask ->
                                if (firebaseTask.isSuccessful) {
                                    val user = auth.currentUser
                                    logger.d("signInWithCredential:success $user")
                                    resultCallback(SignInResult.Succeed)
                                } else {
                                    logger.e("signInWithCredential:failure ${firebaseTask.exception?.message}",
                                        Throwable())
                                    resultCallback(
                                        SignInResult.Failed(
                                            firebaseTask.exception
                                                ?: Throwable("signInWithCredential:failure")
                                        )
                                    )
                                }
                            }
                    }

                    else -> {
                        logger.d("No ID token!")
                        resultCallback(SignInResult.Failed(Throwable("No ID token!")))
                    }
                }
            } catch (e: ApiException) {
                resultCallback(SignInResult.Failed(e))
            }
        } else {
            resultCallback(SignInResult.Failed(Throwable(activity.getString(R.string.sign_in_default_error))))
        }
    }
}


sealed interface SignInResult {
    data object Succeed : SignInResult
    data class Failed(val error: Throwable) : SignInResult
}
