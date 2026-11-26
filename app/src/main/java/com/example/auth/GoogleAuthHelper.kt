package com.example.auth

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object GoogleAuthHelper {
  private const val TAG = "Auth"

  fun ensureFirebaseInitialized(context: Context): Boolean {
    return try {
      val appContext = context.applicationContext
      if (FirebaseApp.getApps(appContext).isEmpty()) {
        FirebaseApp.initializeApp(appContext) != null
      } else {
        true
      }
    } catch (e: Exception) {
      e.printStackTrace()
      false
    }
  }

  fun safeAuth(context: Context): FirebaseAuth? {
    return try {
      if (!ensureFirebaseInitialized(context)) return null
      Firebase.auth
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

  @SuppressLint("DiscouragedApi")
  fun resolveDefaultWebClientId(context: Context): String? {
    return try {
      val appContext = context.applicationContext
      val resId = appContext.resources.getIdentifier(
        "default_web_client_id",
        "string",
        appContext.packageName,
      )
      if (resId == 0) return null
      val value = appContext.getString(resId).trim()
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
          val firebaseAuth = safeAuth(context)
          if (firebaseAuth != null) {
            firebaseAuth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
            onAuthSuccess()
          } else {
            onUnauthenticated()
          }
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

  fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager?,
    scope: CoroutineScope,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    onAuthCancelled: () -> Unit,
  ) {
    val auth = safeAuth(context) ?: run {
      onAuthError("Firebase is not configured yet. Please check google-services.json.")
      return
    }
    val clientId = resolveDefaultWebClientId(context) ?: run {
      onAuthError("Google Sign-In configuration missing or invalid Web Client ID.")
      return
    }
    val activity = context as? Activity ?: run {
      onAuthError("Unable to launch Google Sign-In without an Activity context")
      return
    }
    val cm = credentialManager ?: safeCredentialManager(context) ?: run {
      onAuthError("Google Credential Manager is unavailable on this device.")
      return
    }

    val request = try {
      val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
      GetCredentialRequest.Builder()
        .addCredentialOption(signInOption)
        .build()
    } catch (e: Exception) {
      e.printStackTrace()
      onAuthError(e.localizedMessage ?: "Invalid Google Sign-In configuration")
      return
    }

    scope.launch {
      try {
        val cred = cm.getCredential(activity, request).credential
        if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
          val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
          auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
          onAuthSuccess()
        } else {
          onAuthError("Unexpected credential type")
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: GetCredentialCancellationException) {
        Log.w(TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
        onAuthCancelled()
      } catch (e: Exception) {
        e.printStackTrace()
        Log.e(TAG, "Google Sign-In failed", e)
        onAuthError(e.localizedMessage ?: "Google Sign-In failed")
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
