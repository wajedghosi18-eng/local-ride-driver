package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.auth.GoogleSignInAccountResult
import com.example.data.DriverPrefs
import com.example.data.DriverRepository
import com.example.model.AppLanguage
import com.example.model.DriverCatalog
import com.example.model.TotoColorOption
import com.example.model.TotoDriver
import com.example.model.VehicleType
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUiState(
  val currentStep: Int = 1,
  val language: AppLanguage = AppLanguage.BN,
  val vehicleType: VehicleType = VehicleType.TOTO,
  val district: String = DriverCatalog.westBengalDistricts.first(),
  val village: String = "",
  val lat: Double = 22.4257,
  val lng: Double = 87.3199,
  val signedInUid: String = "",
  val signedInEmail: String = "",
  val fullName: String = "",
  val mobileNumber: String = "",
  val passportPhotoBase64: String = "",
  val selectedColor: TotoColorOption = DriverCatalog.totoColors.first(),
  val vehicleNumber: String = "",
  val emergencyContact: String = "",
  val aadhaarFrontBase64: String = "",
  val aadhaarBackBase64: String = "",
  val isDetectingGps: Boolean = false,
  val isCheckingExistingProfile: Boolean = false,
  val isSubmitting: Boolean = false,
  val errorMessage: String? = null,
)

class OnboardingViewModel(
  private val repository: DriverRepository,
  private val prefs: DriverPrefs,
) : ViewModel() {

  private fun safeCurrentUser(): FirebaseUser? = try {
    Firebase.auth.currentUser
  } catch (e: Exception) {
    e.printStackTrace()
    null
  }

  private val _state = MutableStateFlow(
    run {
      val existingUser = safeCurrentUser()
      val savedUid = prefs.getSavedUserId().orEmpty()
      val savedEmail = prefs.getSavedEmail().orEmpty()
      val savedName = prefs.getSavedDisplayName().orEmpty()
      OnboardingUiState(
        language = prefs.getLanguage(),
        currentStep = if (existingUser != null) 6 else 1,
        signedInUid = existingUser?.uid ?: savedUid,
        signedInEmail = existingUser?.email ?: savedEmail,
        fullName = existingUser?.displayName ?: savedName,
      )
    }
  )
  val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

  fun selectLanguage(lang: AppLanguage) {
    prefs.saveLanguage(lang)
    _state.update { it.copy(language = lang, errorMessage = null) }
  }

  fun selectVehicle(type: VehicleType) {
    _state.update { it.copy(vehicleType = type, errorMessage = null) }
  }

  fun selectDistrict(district: String) {
    _state.update { it.copy(district = district, errorMessage = null) }
  }

  fun updateVillage(village: String) {
    _state.update { it.copy(village = village, errorMessage = null) }
  }

  fun setGpsDetecting(detecting: Boolean) {
    _state.update { it.copy(isDetectingGps = detecting, errorMessage = null) }
  }

  fun setGpsResult(lat: Double, lng: Double, areaName: String) {
    _state.update {
      it.copy(
        lat = lat,
        lng = lng,
        village = areaName,
        isDetectingGps = false,
        errorMessage = null,
      )
    }
  }

  fun updateFullName(name: String) {
    _state.update { it.copy(fullName = name, errorMessage = null) }
  }

  fun updateMobileNumber(mobile: String) {
    val digitsOnly = mobile.filter { it.isDigit() }.take(10)
    _state.update { it.copy(mobileNumber = digitsOnly, errorMessage = null) }
  }

  fun setPassportPhoto(base64: String) {
    _state.update { it.copy(passportPhotoBase64 = base64, errorMessage = null) }
  }

  fun selectTotoColor(option: TotoColorOption) {
    _state.update { it.copy(selectedColor = option, errorMessage = null) }
  }

  fun updateVehicleNumber(number: String) {
    _state.update { it.copy(vehicleNumber = number.uppercase(), errorMessage = null) }
  }

  fun updateEmergencyContact(number: String) {
    val digitsOnly = number.filter { it.isDigit() }.take(12)
    _state.update { it.copy(emergencyContact = digitsOnly, errorMessage = null) }
  }

  fun setAadhaarFront(base64: String) {
    _state.update { it.copy(aadhaarFrontBase64 = base64, errorMessage = null) }
  }

  fun setAadhaarBack(base64: String) {
    _state.update { it.copy(aadhaarBackBase64 = base64, errorMessage = null) }
  }

  fun setError(message: String?) {
    _state.update { it.copy(errorMessage = message) }
  }

  fun nextStep() {
    _state.update { s ->
      s.copy(currentStep = (s.currentStep + 1).coerceAtMost(8), errorMessage = null)
    }
  }

  fun previousStep() {
    _state.update { s ->
      s.copy(currentStep = (s.currentStep - 1).coerceAtLeast(1), errorMessage = null)
    }
  }

  fun goToStep(step: Int) {
    _state.update { it.copy(currentStep = step.coerceIn(1, 8), errorMessage = null) }
  }

  fun onGoogleAccountSignedIn(
    accountResult: GoogleSignInAccountResult,
    onExistingProfileFound: (String) -> Unit,
  ) {
    val user = safeCurrentUser()
    val effectiveUid = user?.uid?.takeIf { it.isNotBlank() } ?: accountResult.uid
    val effectiveEmail = user?.email?.takeIf { it.isNotBlank() } ?: accountResult.email
    val effectiveName = user?.displayName?.takeIf { it.isNotBlank() } ?: accountResult.displayName
    val effectivePhoto = accountResult.photoUrl

    prefs.saveSignedInSession(effectiveUid, effectiveEmail, effectiveName, effectivePhoto)

    _state.update {
      it.copy(
        isCheckingExistingProfile = true,
        signedInUid = effectiveUid,
        signedInEmail = effectiveEmail,
        fullName = it.fullName.ifBlank { effectiveName },
        errorMessage = null,
      )
    }

    viewModelScope.launch {
      val result = repository.findDriverProfileByEmailOrUid(effectiveUid, effectiveEmail)
      result.onSuccess { existing ->
        if (existing != null) {
          val resolvedUid = existing.userId.ifBlank { effectiveUid }
          prefs.saveSignedInSession(
            resolvedUid,
            existing.email.ifBlank { effectiveEmail },
            existing.fullName.ifBlank { effectiveName },
            effectivePhoto,
          )
          prefs.saveLanguage(AppLanguage.fromCode(existing.language))
          prefs.setOnboardingComplete(true)
          _state.update { it.copy(isCheckingExistingProfile = false, errorMessage = null) }
          onExistingProfileFound(resolvedUid)
        } else {
          _state.update {
            it.copy(
              isCheckingExistingProfile = false,
              currentStep = 6,
              fullName = it.fullName.ifBlank { effectiveName },
              errorMessage = null,
            )
          }
        }
      }.onFailure { err ->
        Log.w(TAG, "Profile check failed after Google Sign-In; advancing to Step 6: ${err.message}", err)
        _state.update {
          it.copy(
            isCheckingExistingProfile = false,
            currentStep = 6,
            fullName = it.fullName.ifBlank { effectiveName },
            errorMessage = null,
          )
        }
      }
    }
  }

  fun completeRegistration(onCompleted: (String) -> Unit) {
    val user = safeCurrentUser()
    val s = _state.value
    val uid = user?.uid?.takeIf { it.isNotBlank() }
      ?: s.signedInUid.ifBlank { prefs.getSavedUserId().orEmpty() }
    val email = user?.email?.takeIf { it.isNotBlank() }
      ?: s.signedInEmail.ifBlank { prefs.getSavedEmail().orEmpty() }

    if (uid.isBlank()) {
      _state.update { it.copy(currentStep = 5, errorMessage = "Please sign in with Google first.") }
      return
    }
    if (s.fullName.trim().length < 2) {
      _state.update { it.copy(currentStep = 6, errorMessage = "Full name is required.") }
      return
    }
    if (s.mobileNumber.length < 10) {
      _state.update { it.copy(currentStep = 6, errorMessage = "Enter a valid 10-digit mobile number.") }
      return
    }

    _state.update { it.copy(isSubmitting = true, errorMessage = null) }
    viewModelScope.launch {
      val driver = TotoDriver(
        userId = uid,
        email = email,
        language = s.language.code,
        vehicleType = s.vehicleType.id,
        district = s.district,
        village = s.village.ifBlank { s.district },
        fullName = s.fullName.trim(),
        mobileNumber = s.mobileNumber.trim(),
        totoColor = s.selectedColor.displayName,
        totoColorHex = s.selectedColor.primaryHex,
        vehicleNumber = s.vehicleNumber.trim(),
        emergencyContact = s.emergencyContact.trim(),
        passportPhotoBase64 = s.passportPhotoBase64,
        aadhaarFrontBase64 = s.aadhaarFrontBase64,
        aadhaarBackBase64 = s.aadhaarBackBase64,
        status = "active",
        online = true,
        lat = s.lat,
        lng = s.lng,
      )
      val saveResult = repository.saveDriverProfile(driver)
      saveResult.onSuccess {
        prefs.saveSignedInSession(uid, email, s.fullName.trim())
        prefs.saveLanguage(s.language)
        prefs.setOnboardingComplete(true)
        _state.update { it.copy(isSubmitting = false) }
        onCompleted(uid)
      }.onFailure { err ->
        _state.update {
          it.copy(
            isSubmitting = false,
            errorMessage = err.localizedMessage ?: "Failed to save driver profile",
          )
        }
      }
    }
  }

  private companion object {
    const val TAG = "OnboardingVM"
  }
}
