package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
      OnboardingUiState(
        language = prefs.getLanguage(),
        currentStep = if (existingUser != null) 6 else 1,
        fullName = existingUser?.displayName.orEmpty(),
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

  fun onGoogleSignedIn(onExistingProfileFound: () -> Unit) {
    val user = safeCurrentUser() ?: return
    _state.update {
      it.copy(
        isCheckingExistingProfile = true,
        fullName = it.fullName.ifBlank { user.displayName.orEmpty() },
        errorMessage = null,
      )
    }
    viewModelScope.launch {
      val result = repository.getDriverProfile(user.uid)
      result.onSuccess { existing ->
        if (existing != null) {
          prefs.saveLanguage(AppLanguage.fromCode(existing.language))
          prefs.setOnboardingComplete(true)
          _state.update { it.copy(isCheckingExistingProfile = false) }
          onExistingProfileFound()
        } else {
          _state.update {
            it.copy(
              isCheckingExistingProfile = false,
              currentStep = 6,
              fullName = it.fullName.ifBlank { user.displayName.orEmpty() },
            )
          }
        }
      }.onFailure { err ->
        _state.update {
          it.copy(
            isCheckingExistingProfile = false,
            errorMessage = err.localizedMessage ?: "Failed to check profile",
          )
        }
      }
    }
  }

  fun completeRegistration(onCompleted: () -> Unit) {
    val user = safeCurrentUser()
    if (user == null) {
      _state.update { it.copy(currentStep = 5, errorMessage = "Please sign in with Google first.") }
      return
    }
    val s = _state.value
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
        userId = user.uid,
        email = user.email.orEmpty(),
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
        status = "pending_or_active",
        online = false,
        lat = s.lat,
        lng = s.lng,
      )
      val saveResult = repository.saveDriverProfile(driver)
      saveResult.onSuccess {
        prefs.saveLanguage(s.language)
        prefs.setOnboardingComplete(true)
        _state.update { it.copy(isSubmitting = false) }
        onCompleted()
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
}
