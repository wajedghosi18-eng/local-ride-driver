package com.example.auth

import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import android.util.Patterns
import android.widget.Toast
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
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

  fun buildAccountPickerIntent(): Intent =
    AccountManager.newChooseAccountIntent(
      null,
      null,
      arrayOf("com.google"),
      null,
      null,
      null,
      null,
    )

  fun isValidEmail(email: String): Boolean {
    val trimmed = email.trim()
    return trimmed.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(trimmed).matches()
  }

  fun uidForEmail(email: String, existingFirebaseUid: String? = null): String {
    if (!existingFirebaseUid.isNullOrBlank()) return existingFirebaseUid
    val sanitized = email.trim().lowercase().replace(Regex("[^a-zA-Z0-9_-]"), "_")
    return if (sanitized.isNotEmpty()) "email_$sanitized" else "driver_${System.currentTimeMillis()}"
  }

  fun buildResultForEmail(context: Context, email: String): GoogleSignInAccountResult {
    ensureFirebaseInitialized(context)
    val fbUser = safeCurrentUser(context)
    val cleanEmail = email.trim()
    return GoogleSignInAccountResult(
      uid = uidForEmail(cleanEmail, fbUser?.uid),
      email = cleanEmail,
      displayName = fbUser?.displayName.orEmpty(),
      photoUrl = "",
      isFirebaseAuthenticated = fbUser != null,
    )
  }

  fun handleAccountPickerResult(
    context: Context,
    resultCode: Int,
    data: Intent?,
    onSuccess: (GoogleSignInAccountResult) -> Unit,
    onNoAccountSelected: (String) -> Unit,
  ) {
    if (resultCode != Activity.RESULT_OK) {
      val msg = "No Google account selected. Pick a Gmail account or enter your Gmail address below."
      onNoAccountSelected(msg)
      return
    }
    val selectedEmail = data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)?.trim().orEmpty()
    if (selectedEmail.isBlank()) {
      val msg = "Could not read Gmail address from device. Please enter your Gmail address below."
      showErrorToastSync(context, msg)
      onNoAccountSelected(msg)
      return
    }
    onSuccess(buildResultForEmail(context, selectedEmail))
  }

  private fun showErrorToastSync(context: Context, message: String) {
    try {
      Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
      Log.w(TAG, "Unable to show Toast: ${e.message}")
    }
  }

  fun signOut(
    scope: CoroutineScope,
    context: Context? = null,
    onSignOutComplete: () -> Unit = {},
  ) {
    scope.launch {
      withContext(NonCancellable) {
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
