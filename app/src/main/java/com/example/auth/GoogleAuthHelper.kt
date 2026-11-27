package com.example.auth

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class GoogleSignInAccountResult(
  val uid: String,
  val email: String,
  val displayName: String,
  val photoUrl: String = "",
  val isFirebaseAuthenticated: Boolean = false,
)

object GoogleAuthHelper {
  private const val TAG = "Auth"

  fun ensureFirebaseInitialized(context: Context): Boolean {
    return try {
      val appContext = context.applicationContext
      if (FirebaseApp.getApps(appContext).isNotEmpty()) {
        true
      } else {
        FirebaseApp.initializeApp(appContext) != null || FirebaseApp.getApps(appContext).isNotEmpty()
      }
    } catch (e: Exception) {
      e.printStackTrace()
      false
    }
  }

  fun safeAuth(context: Context): FirebaseAuth? {
    return try {
      val appContext = context.applicationContext
      if (FirebaseApp.getApps(appContext).isNotEmpty() || ensureFirebaseInitialized(appContext)) {
        FirebaseAuth.getInstance()
      } else {
        null
      }
    } catch (e: Exception) {
      e.printStackTrace()
      null
    }
  }

  fun safeCurrentUser(context: Context): FirebaseUser? {
    return try {
      safeAuth(context)?.currentUser
    } catch (e: Exception) {
      e.printStackTrace()
      null
    }
  }

  fun safeCredentialManager(context: Context): CredentialManager? {
    return try {
      CredentialManager.create(context)
    } catch (e: Exception) {
      e.printStackTrace()
      null
    }
  }

  private fun buildGoogleSignInOptions(): GoogleSignInOptions =
    GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
      .requestEmail()
      .requestProfile()
      .build()

  fun buildSignInIntent(context: Context): Intent {
    ensureFirebaseInitialized(context)
    return GoogleSignIn.getClient(context, buildGoogleSignInOptions()).signInIntent
  }

  private fun uidForAccount(account: GoogleSignInAccount, existingFirebaseUid: String?): String {
    if (!existingFirebaseUid.isNullOrBlank()) return existingFirebaseUid
    val rawId = account.id?.trim().orEmpty()
    if (rawId.isNotEmpty()) return "google_$rawId"
    val emailPart = account.email.orEmpty().trim().lowercase().replace(Regex("[^a-zA-Z0-9_-]"), "_")
    return if (emailPart.isNotEmpty()) "email_$emailPart" else "driver_${System.currentTimeMillis()}"
  }

  fun handleSignInActivityResult(
    context: Context,
    data: Intent?,
    scope: CoroutineScope,
    onSuccess: (GoogleSignInAccountResult) -> Unit,
    onError: (String) -> Unit,
  ) {
    scope.launch {
      try {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        val account = task.getResult(ApiException::class.java)
        val email = account.email.orEmpty().trim()
        val displayName = account.displayName.orEmpty().trim()
        val photoUrl = account.photoUrl?.toString().orEmpty()

        if (email.isBlank() && account.id.isNullOrBlank()) {
          val msg = "Google account did not return an email address. Please choose another Gmail account."
          showErrorToast(context, msg)
          onError(msg)
          return@launch
        }

        val fbUser = safeCurrentUser(context)
        val resolvedUid = uidForAccount(account, fbUser?.uid)

        onSuccess(
          GoogleSignInAccountResult(
            uid = resolvedUid,
            email = email,
            displayName = displayName,
            photoUrl = photoUrl,
            isFirebaseAuthenticated = fbUser != null,
          )
        )
      } catch (e: CancellationException) {
        throw e
      } catch (e: ApiException) {
        Log.e(TAG, "GoogleSignIn ApiException (code=${e.statusCode})", e)
        val statusDesc = GoogleSignInStatusCodes.getStatusCodeString(e.statusCode)
        val msg = if (e.statusCode == GoogleSignInStatusCodes.SIGN_IN_CANCELLED) {
          "Google Sign-In was cancelled (code ${e.statusCode}). Please select a Gmail account to continue."
        } else {
          "Google Sign-In failed (code ${e.statusCode} - $statusDesc): ${e.localizedMessage ?: e.message ?: "Unknown error"}"
        }
        showErrorToast(context, msg)
        onError(msg)
      } catch (e: Exception) {
        Log.e(TAG, "Google Sign-In failed unexpectedly", e)
        val msg = "Google Sign-In failed (${e.javaClass.simpleName}): ${e.localizedMessage ?: e.message ?: "Unknown error"}"
        showErrorToast(context, msg)
        onError(msg)
      }
    }
  }

  fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager?,
    scope: CoroutineScope,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
  ) {
    val existingFbUser = safeCurrentUser(context)
    val lastGoogleAccount = runCatching { GoogleSignIn.getLastSignedInAccount(context) }.getOrNull()
    if (existingFbUser != null || lastGoogleAccount != null) {
      onAuthSuccess()
    } else {
      onUnauthenticated()
    }
  }

  private suspend fun showErrorToast(context: Context, message: String) {
    withContext(Dispatchers.Main) {
      try {
        Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show()
      } catch (e: Exception) {
        Log.w(TAG, "Unable to show Toast: ${e.message}")
      }
    }
  }

  fun signOut(
    credentialManager: CredentialManager?,
    scope: CoroutineScope,
    context: Context? = null,
    onSignOutComplete: () -> Unit = {},
  ) {
    scope.launch {
      withContext(NonCancellable) {
        try {
          credentialManager?.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
          Log.w(TAG, "Failed to clear credential state: ${e.message}")
        }
        try {
          if (context != null) {
            GoogleSignIn.getClient(context, buildGoogleSignInOptions()).signOut().await()
          }
        } catch (e: Exception) {
          Log.w(TAG, "Failed to sign out GoogleSignInClient: ${e.message}")
        }
        try {
          if (context != null) {
            safeAuth(context)?.signOut()
          } else {
            Firebase.auth.signOut()
          }
        } catch (e: Exception) {
          e.printStackTrace()
        }
        withContext(Dispatchers.Main) {
          onSignOutComplete()
        }
      }
    }
  }
}
