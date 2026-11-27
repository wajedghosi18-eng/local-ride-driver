package com.example.auth

import android.content.Context
import android.util.Log
import android.util.Patterns
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

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
  private const val OTP_SCRIPT_URL =
    "https://script.google.com/macros/s/AKfycbx748m7-S5M8D8e-Sn5moxkr6RH0CsOU6MNj_6g2PV7B8QbTagqQf-2ELvvCVmF_dsUEw/exec"

  @Volatile
  var lastInitError: String? = null
    private set

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
        lastInitError = null
        return true
      }

      runCatching { FirebaseApp.initializeApp(appContext) }
      if (FirebaseApp.getApps(appContext).isNotEmpty()) {
        lastInitError = null
        return true
      }

      val assetConfig = readConfigFromAssets(appContext)
      val apiKey = resolveStringResource(appContext, "google_api_key") ?: assetConfig?.apiKey

      if (apiKey.isNullOrBlank()) {
        lastInitError = "Missing Firebase API key in google-services.json / assets / Secrets"
        Log.e(TAG, lastInitError!!)
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
      val initialized = FirebaseApp.getApps(appContext).isNotEmpty()
      lastInitError = if (initialized) null else "FirebaseApp.initializeApp(context, options) returned null"
      initialized
    } catch (e: Exception) {
      e.printStackTrace()
      lastInitError = "${e.javaClass.simpleName}: ${e.localizedMessage ?: e.message}"
      false
    }
  }

  fun safeAuth(context: Context): FirebaseAuth? {
    return try {
      val appContext = context.applicationContext
      if (FirebaseApp.getApps(appContext).isNotEmpty() || ensureFirebaseInitialized(appContext)) {
        val app = FirebaseApp.getApps(appContext).firstOrNull()
        if (app != null) FirebaseAuth.getInstance(app) else FirebaseAuth.getInstance()
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

  fun generateFourDigitOtp(): String = (1000..9999).random().toString()

  suspend fun sendEmailOtp(email: String, otp: String): Result<Unit> = withContext(Dispatchers.IO) {
    var connection: HttpURLConnection? = null
    try {
      val url = URL(OTP_SCRIPT_URL)
      connection = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        instanceFollowRedirects = true
        doOutput = true
        connectTimeout = 15_000
        readTimeout = 15_000
        setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        setRequestProperty("Accept", "application/json")
      }
      val payload = JSONObject()
        .put("email", email.trim())
        .put("otp", otp)
        .toString()

      connection.outputStream.use { os ->
        os.write(payload.toByteArray(Charsets.UTF_8))
        os.flush()
      }

      val responseCode = connection.responseCode
      if (responseCode in 200..399) {
        Result.success(Unit)
      } else {
        val errBody = runCatching {
          connection.errorStream?.bufferedReader()?.use { it.readText() }
        }.getOrNull().orEmpty()
        Result.failure(IllegalStateException("HTTP $responseCode ${errBody.take(120)}".trim()))
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Failed to send OTP email", e)
      Result.failure(e)
    } finally {
      runCatching { connection?.disconnect() }
    }
  }

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
