package com.example.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

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

  private fun isValidWebClientId(clientId: String?): Boolean {
    val trimmed = clientId?.trim().orEmpty()
    return trimmed.isNotEmpty() &&
      trimmed != "YOUR_WEB_CLIENT_ID" &&
      trimmed.endsWith(".apps.googleusercontent.com")
  }

  fun resolveDefaultWebClientId(context: Context): String? {
    return try {
      val value = context.getString(R.string.default_web_client_id).trim()
      value.takeIf { isValidWebClientId(it) }
    } catch (e: Exception) {
      e.printStackTrace()
      null
    }
  }

  fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager?,
    scope: CoroutineScope,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
  ) {
    val auth = safeAuth(context)
    if (auth?.currentUser != null) {
      onAuthSuccess()
      return
    }
    val cm = credentialManager ?: safeCredentialManager(context) ?: run {
      onUnauthenticated()
      return
    }
    val clientId = resolveDefaultWebClientId(context) ?: run {
      onUnauthenticated()
      return
    }

    val request = try {
      val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()
      GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()
    } catch (e: Exception) {
      e.printStackTrace()
      onUnauthenticated()
      return
    }

    scope.launch {
      try {
        val cred = cm.getCredential(context, request).credential
        if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
          val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
          val firebaseAuth = safeAuth(context) ?: Firebase.auth
          firebaseAuth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
          onAuthSuccess()
        } else {
          onUnauthenticated()
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        e.printStackTrace()
        onUnauthenticated()
      }
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

  private fun showErrorToastSync(context: Context, message: String) {
    try {
      Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
      Log.w(TAG, "Unable to show Toast: ${e.message}")
    }
  }

  fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager?,
    scope: CoroutineScope,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    onAuthCancelled: () -> Unit,
  ) {
    ensureFirebaseInitialized(context)
    val clientId = resolveDefaultWebClientId(context) ?: run {
      val msg = "Google Sign-In configuration missing or invalid Web Client ID."
      showErrorToastSync(context, msg)
      onAuthError(msg)
      return
    }
    val activity = context as? Activity ?: run {
      val msg = "Unable to launch Google Sign-In without an Activity context."
      showErrorToastSync(context, msg)
      onAuthError(msg)
      return
    }
    val cm = credentialManager ?: safeCredentialManager(context) ?: run {
      val msg = "Google Credential Manager is unavailable on this device."
      showErrorToastSync(context, msg)
      onAuthError(msg)
      return
    }

    val request = try {
      val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
      GetCredentialRequest.Builder()
        .addCredentialOption(signInOption)
        .build()
    } catch (e: Exception) {
      e.printStackTrace()
      val msg = "Google Sign-In setup failed: ${e.javaClass.simpleName}: ${e.localizedMessage ?: e.message ?: "Invalid configuration"}"
      showErrorToastSync(context, msg)
      onAuthError(msg)
      return
    }

    scope.launch {
      try {
        val cred = cm.getCredential(activity, request).credential
        if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
          val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
          val auth = safeAuth(context) ?: run {
            val msg = "FirebaseAuth is unavailable. Ensure google-services.json matches com.localdeal.driver."
            showErrorToast(context, msg)
            onAuthError(msg)
            return@launch
          }
          auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
          onAuthSuccess()
        } else {
          val msg = "Google Sign-In failed: Unexpected credential type (${cred.type})"
          showErrorToast(context, msg)
          onAuthError(msg)
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: GetCredentialCancellationException) {
        Log.w(TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
        onAuthCancelled()
      } catch (e: GetCredentialException) {
        e.printStackTrace()
        Log.e(TAG, "Google Sign-In credential error (${e.type})", e)
        val msg = "Google Sign-In failed (${e.javaClass.simpleName}): ${e.localizedMessage ?: e.message ?: e.type}"
        showErrorToast(context, msg)
        onAuthError(msg)
      } catch (e: Exception) {
        e.printStackTrace()
        Log.e(TAG, "Google Sign-In failed", e)
        val msg = "Google Sign-In failed (${e.javaClass.simpleName}): ${e.localizedMessage ?: e.message ?: "Unknown error"}"
        showErrorToast(context, msg)
        onAuthError(msg)
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
        } finally {
          try {
            if (context != null) {
              safeAuth(context)?.signOut()
            } else {
              Firebase.auth.signOut()
            }
          } catch (e: Exception) {
            e.printStackTrace()
          }
          onSignOutComplete()
        }
      }
    }
  }
}
