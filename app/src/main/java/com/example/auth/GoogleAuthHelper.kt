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
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class GoogleSignInAccountResult(
  val uid: String,
  val email: String,
  val displayName: String,
  val photoUrl: String = "",
  val isFirebaseAuthenticated: Boolean = false,
)

object GoogleAuthHelper {
  private const val TAG = "Auth"
  private const val FALLBACK_PROJECT_ID = "local-deal-683e8"
  private const val FALLBACK_APP_ID = "1:153133574988:android:ba957e6b4e51ecc632bde9"
  private const val FALLBACK_STORAGE_BUCKET = "local-deal-683e8.firebasestorage.app"
  private const val FALLBACK_GCM_SENDER_ID = "153133574988"

  private fun resolveStringResource(context: Context, name: String): String? {
    return try {
      val id = context.resources.getIdentifier(name, "string", context.packageName)
      if (id != 0) context.getString(id).trim().takeIf { it.isNotEmpty() } else null
    } catch (_: Exception) {
      null
    }
  }

  private data class ParsedGoogleServicesConfig(
    val apiKey: String? = null,
    val projectId: String? = null,
    val applicationId: String? = null,
    val storageBucket: String? = null,
    val gcmSenderId: String? = null,
  )

  private fun readConfigFromAssets(context: Context): ParsedGoogleServicesConfig? {
    return try {
      val jsonText = context.assets.open("google-services.json").bufferedReader().use { it.readText() }
      val root = JSONObject(jsonText)
      val projectInfo = root.optJSONObject("project_info")
      val projectId = projectInfo?.optString("project_id")?.trim()?.takeIf { it.isNotEmpty() }
      val storageBucket = projectInfo?.optString("storage_bucket")?.trim()?.takeIf { it.isNotEmpty() }
      val gcmSenderId = projectInfo?.optString("project_number")?.trim()?.takeIf { it.isNotEmpty() }

      val clients = root.optJSONArray("client")
      var matchedApiKey: String? = null
      var matchedAppId: String? = null
      if (clients != null) {
        for (i in 0 until clients.length()) {
          val client = clients.optJSONObject(i) ?: continue
          val clientInfo = client.optJSONObject("client_info")
          val pkg = clientInfo?.optJSONObject("android_client_info")?.optString("package_name")
          if (pkg == context.packageName || (matchedAppId == null && i == clients.length() - 1)) {
            matchedAppId = clientInfo?.optString("mobilesdk_app_id")?.trim()?.takeIf { it.isNotEmpty() }
            matchedApiKey = client.optJSONArray("api_key")
              ?.optJSONObject(0)
              ?.optString("current_key")
              ?.trim()
              ?.takeIf { it.isNotEmpty() }
            if (pkg == context.packageName) break
          }
        }
      }
      ParsedGoogleServicesConfig(
        apiKey = matchedApiKey,
        projectId = projectId,
        applicationId = matchedAppId,
        storageBucket = storageBucket,
        gcmSenderId = gcmSenderId,
      )
    } catch (_: Exception) {
      null
    }
  }

  fun ensureFirebaseInitialized(context: Context): Boolean {
    return try {
      val appContext = context.applicationContext
      if (FirebaseApp.getApps(appContext).isNotEmpty()) {
        return true
      }

      runCatching { FirebaseApp.initializeApp(appContext) }
      if (FirebaseApp.getApps(appContext).isNotEmpty()) {
        return true
      }

      val assetConfig = readConfigFromAssets(appContext)
      val apiKey = resolveStringResource(appContext, "google_api_key") ?: assetConfig?.apiKey
      if (apiKey.isNullOrBlank()) {
        Log.e(TAG, "Firebase fallback missing google_api_key; check google-services.json")
        return false
      }

      val projectId = resolveStringResource(appContext, "project_id")
        ?: assetConfig?.projectId
        ?: FALLBACK_PROJECT_ID
      val applicationId = resolveStringResource(appContext, "google_app_id")
        ?: assetConfig?.applicationId
        ?: FALLBACK_APP_ID
      val storageBucket = resolveStringResource(appContext, "google_storage_bucket")
        ?: assetConfig?.storageBucket
        ?: FALLBACK_STORAGE_BUCKET
      val gcmSenderId = resolveStringResource(appContext, "gcm_defaultSenderId")
        ?: assetConfig?.gcmSenderId
        ?: FALLBACK_GCM_SENDER_ID

      val options = FirebaseOptions.Builder()
        .setProjectId(projectId)
        .setApplicationId(applicationId)
        .setStorageBucket(storageBucket)
        .setGcmSenderId(gcmSenderId)
        .setApiKey(apiKey)
        .build()

      FirebaseApp.initializeApp(appContext, options)
      FirebaseApp.getApps(appContext).isNotEmpty()
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
