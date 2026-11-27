package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.AppLanguage

class DriverPrefs(context: Context) {
  private val prefs: SharedPreferences =
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  fun getLanguage(): AppLanguage {
    val code = prefs.getString(KEY_LANGUAGE, AppLanguage.BN.code)
    return AppLanguage.fromCode(code)
  }

  fun hasSelectedLanguage(): Boolean = prefs.getBoolean(KEY_LANGUAGE_SELECTED, false)

  fun saveLanguage(language: AppLanguage) {
    prefs.edit()
      .putString(KEY_LANGUAGE, language.code)
      .putBoolean(KEY_LANGUAGE_SELECTED, true)
      .apply()
  }

  fun isOnboardingComplete(): Boolean = prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)

  fun setOnboardingComplete(complete: Boolean) {
    prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETE, complete).apply()
  }

  fun shouldShowUpdatePopup(
    latestVersionCode: Long,
    currentVersionCode: Long,
    forceUpdate: Boolean,
  ): Boolean {
    if (latestVersionCode <= currentVersionCode) return false
    if (forceUpdate) return true
    val dismissedForVersion = prefs.getLong(KEY_UPDATE_DISMISSED_VERSION, -1L)
    val dismissedAt = prefs.getLong(KEY_UPDATE_DISMISSED_AT, 0L)
    if (dismissedForVersion != latestVersionCode) return true
    val elapsed = System.currentTimeMillis() - dismissedAt
    return elapsed >= TWENTY_FOUR_HOURS_MS
  }

  fun dismissUpdateFor24Hours(versionCode: Long) {
    prefs.edit()
      .putLong(KEY_UPDATE_DISMISSED_VERSION, versionCode)
      .putLong(KEY_UPDATE_DISMISSED_AT, System.currentTimeMillis())
      .apply()
  }

  fun saveSignedInSession(uid: String, email: String, displayName: String) {
    prefs.edit()
      .putString(KEY_SIGNED_IN_UID, uid)
      .putString(KEY_SIGNED_IN_EMAIL, email)
      .putString(KEY_SIGNED_IN_NAME, displayName)
      .apply()
  }

  fun getSavedUserId(): String? = prefs.getString(KEY_SIGNED_IN_UID, null)?.takeIf { it.isNotBlank() }

  fun getSavedEmail(): String? = prefs.getString(KEY_SIGNED_IN_EMAIL, null)?.takeIf { it.isNotBlank() }

  fun getSavedDisplayName(): String? = prefs.getString(KEY_SIGNED_IN_NAME, null)?.takeIf { it.isNotBlank() }

  fun clearSession() {
    prefs.edit()
      .remove(KEY_SIGNED_IN_UID)
      .remove(KEY_SIGNED_IN_EMAIL)
      .remove(KEY_SIGNED_IN_NAME)
      .remove(KEY_ONBOARDING_COMPLETE)
      .apply()
  }

  fun clearAllOnboarding() {
    prefs.edit()
      .remove(KEY_ONBOARDING_COMPLETE)
      .apply()
  }

  private companion object {
    const val PREFS_NAME = "local_ride_driver_prefs"
    const val KEY_LANGUAGE = "selected_language"
    const val KEY_LANGUAGE_SELECTED = "has_selected_language"
    const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
    const val KEY_SIGNED_IN_UID = "signed_in_uid"
    const val KEY_SIGNED_IN_EMAIL = "signed_in_email"
    const val KEY_SIGNED_IN_NAME = "signed_in_name"
    const val KEY_UPDATE_DISMISSED_VERSION = "update_dismissed_version"
    const val KEY_UPDATE_DISMISSED_AT = "update_dismissed_at"
    const val TWENTY_FOUR_HOURS_MS = 24L * 60L * 60L * 1000L
  }
}
