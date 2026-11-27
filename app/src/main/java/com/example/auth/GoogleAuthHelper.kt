package com.example.auth

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.example.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
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

data class GoogleSignInAccountResult(
  val uid: String,
  val email: String,
  val displayName: String,
  val isFirebaseAuthenticated: Boolean,
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

  private fun isValidWebClientId(clientId: String?): Boolean {
    val trimmed = clientId?.trim().orEmpty()
    return trimmed.isNotEmpty() &&
      trimmed != "YOUR_WEB_CLIENT_ID" &&
      trimmed.endsWith(".apps.googleusercontent.com")
  }

  fun resolveDefaultWebClientId(context: Context): String? {
    return try {
      val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
      val value = if (resId != 0) context.getString(resId).trim() else context.getString(R.string.default_web_client_id).trim()
      value.takeIf { isValidWebClientId(it) }
    } catch (e: Exception) {
      e.printStackTrace()
      null
    }
  }

  fun buildIdTokenSignInIntent(context: Context): Intent {
    ensureFirebaseInitialized(context)
    val clientId = resolveDefaultWebClientId(context)
    val gso = if (clientId != null) {
      GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(clientId)
        .requestEmail()
        .build()
    } else {
      GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .build()
    }
    return GoogleSignIn.getClient(context, gso).signInIntent
  }

  fun buildEmailOnlySignInIntent(context: Context): Intent {
    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
      .requestEmail()
      .build()
    return GoogleSignIn.getClient(context, gso).signInIntent
  }

  fun hasValidIdTokenOption(context: Context): Boolean = resolveDefaultWebClientId(context) != null

  private fun fallbackUidForAccount(account: GoogleSignInAccount): String {
    val rawId = account.id?.trim().orEmpty()
    if (rawId.isNotEmpty()) return "google_$rawId"
    val emailPart = account.email.orEmpty().trim().lowercase().replace(Regex("[^a-zA-Z0-9_-]"), "_")
    return if (emailPart.isNotEmpty()) "email_$emailPart" else "driver_${System.currentTimeMillis()}"
  }

  fun handleSignInActivityResult(
    context: Context,
    data: Intent?,
    isEmailOnlyAttempt: Boolean,
    scope: CoroutineScope,
    onSuccess: (GoogleSignInAccountResult) -> Unit,
    onRetryEmailOnly: () -> Unit,
    onError: (String) -> Unit,
  ) {
    scope.launch {
      try {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        val account = task.getResult(ApiException::class.java)
        val idToken = account.idToken
        val email = account.email.orEmpty().trim()
        val displayName = account.displayName.orEmpty().trim()

        if (!isEmailOnlyAttempt && !idToken.isNullOrBlank()) {
          val firebaseAuth = safeAuth(context)
          if (firebaseAuth != null) {
            try {
              val authResult = firebaseAuth
                .signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
                .await()
              val fbUser = authResult.user ?: firebaseAuth.currentUser
              if (fbUser != null) {
                onSuccess(
                  GoogleSignInAccountResult(
                    uid = fbUser.uid,
                    email = fbUser.email?.ifBlank { email } ?: email,
                    displayName = fbUser.displayName?.ifBlank { displayName } ?: displayName,
                    isFirebaseAuthenticated = true,
                  )
                )
                return@launch
              }
            } catch (e: CancellationException) {
              throw e
            } catch (e: Exception) {
              Log.w(TAG, "Firebase signInWithCredential failed, continuing with Google email fallback: ${e.message}", e)
              if (email.isNotBlank()) {
                onSuccess(
                  GoogleSignInAccountResult(
                    uid = fallbackUidForAccount(account),
                    email = email,
                    displayName = displayName,
                    isFirebaseAuthenticated = false,
                  )
                )
                return@launch
              }
              val msg = "Firebase Sign-In failed (${e.javaClass.simpleName}): ${e.localizedMessage ?: e.message ?: "Unknown error"}"
              showErrorToast(context, msg)
              onError(msg)
              return@launch
            }
          }
        }

        if (email.isNotBlank() || !account.id.isNullOrBlank()) {
          onSuccess(
            GoogleSignInAccountResult(
              uid = fallbackUidForAccount(account),
              email = email,
              displayName = displayName,
              isFirebaseAuthenticated = false,
            )
          )
        } else {
          val msg = "Google account did not return an email address. Please try another Gmail account."
          showErrorToast(context, msg)
          onError(msg)
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: ApiException) {
        Log.e(TAG, "GoogleSignIn ApiException (emailOnly=$isEmailOnlyAttempt, code=${e.statusCode})", e)
        if (!isEmailOnlyAttempt && e.statusCode != GoogleSignInStatusCodes.SIGN_IN_CANCELLED) {
          Log.i(TAG, "Retrying Google Sign-In with DEFAULT_SIGN_IN email-only fallback after status ${e.statusCode}")
          onRetryEmailOnly()
        } else {
          val statusDesc = GoogleSignInStatusCodes.getStatusCodeString(e.statusCode)
          val msg = if (e.statusCode == GoogleSignInStatusCodes.SIGN_IN_CANCELLED) {
            "Google Sign-In was cancelled (code ${e.statusCode}). Please select a Gmail account to continue."
          } else {
            "Google Sign-In failed (code ${e.statusCode} - $statusDesc): ${e.localizedMessage ?: e.message ?: "Unknown error"}"
          }
          showErrorToast(context, msg)
          onError(msg)
        }
      } catch (e: Exception) {
        Log.e(TAG, "Google Sign-In failed unexpectedly", e)
        if (!isEmailOnlyAttempt) {
          onRetryEmailOnly()
        } else {
          val msg = "Google Sign-In failed (${e.javaClass.simpleName}): ${e.localizedMessage ?: e.message ?: "Unknown error"}"
          showErrorToast(context, msg)
          onError(msg)
        }
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
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
              .requestEmail()
              .build()
            GoogleSignIn.getClient(context, gso).signOut().await()
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
