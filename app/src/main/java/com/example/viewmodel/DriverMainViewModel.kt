package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.DriverPrefs
import com.example.data.DriverRepository
import com.example.model.AppLanguage
import com.example.model.DriverAppVersion
import com.example.model.TotoDriver
import com.example.model.TotoRide
import com.example.model.UiState
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DriverMainViewModel(
  private val repository: DriverRepository,
  private val prefs: DriverPrefs,
  private val currentUserId: String,
) : ViewModel() {

  private val _dismissedRideKeys = MutableStateFlow<Set<String>>(emptySet())

  val driverProfileState: StateFlow<UiState<TotoDriver?>> =
    repository.observeDriverProfile(currentUserId)
      .map<TotoDriver?, UiState<TotoDriver?>> { UiState.Success(it) }
      .catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
          .filterIsInstance<FirebaseFirestoreException>()
          .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing driver profile", rootError)
        emit(UiState.Error(rootError.message ?: "Failed to load driver profile"))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = UiState.Loading,
      )

  @OptIn(ExperimentalCoroutinesApi::class)
  val pendingRidesState: StateFlow<UiState<List<TotoRide>>> =
    driverProfileState
      .map { state ->
        val driver = (state as? UiState.Success)?.data
        if (driver != null && driver.online) {
          Triple(true, driver.lat, driver.lng)
        } else {
          Triple(false, 0.0, 0.0)
        }
      }
      .distinctUntilChanged { old, new -> old.first == new.first }
      .flatMapLatest { (isOnline, lat, lng) ->
        if (!isOnline) {
          flowOf(UiState.Success(emptyList()))
        } else {
          combine(
            repository.observePendingRides(currentUserId, lat, lng),
            _dismissedRideKeys,
          ) { rides, dismissed ->
            val filtered = rides.filter { "${it.sourceCollection}/${it.id}" !in dismissed }
            UiState.Success(filtered) as UiState<List<TotoRide>>
          }.catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
              .filterIsInstance<FirebaseFirestoreException>()
              .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing pending rides", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to observe incoming rides"))
          }
        }
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = UiState.Loading,
      )

  val myRidesState: StateFlow<UiState<List<TotoRide>>> =
    repository.observeMyRides(currentUserId)
      .map<List<TotoRide>, UiState<List<TotoRide>>> { UiState.Success(it) }
      .catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
          .filterIsInstance<FirebaseFirestoreException>()
          .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing my rides", rootError)
        emit(UiState.Error(rootError.message ?: "Failed to load ride history"))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = UiState.Loading,
      )

  private val _language = MutableStateFlow(prefs.getLanguage())
  val language: StateFlow<AppLanguage> = _language.asStateFlow()

  private val _updateVersionToShow = MutableStateFlow<DriverAppVersion?>(null)
  val updateVersionToShow: StateFlow<DriverAppVersion?> = _updateVersionToShow.asStateFlow()

  private val _isBusy = MutableStateFlow(false)
  val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

  private val _feedback = MutableSharedFlow<String>()
  val feedback: SharedFlow<String> = _feedback.asSharedFlow()

  init {
    checkAppUpdate()
  }

  private fun checkAppUpdate() {
    viewModelScope.launch {
      val result = repository.fetchLatestAppVersion()
      result.onSuccess { remoteVersion ->
        if (remoteVersion != null &&
          remoteVersion.apkDownloadUrl.isNotBlank() &&
          prefs.shouldShowUpdatePopup(
            latestVersionCode = remoteVersion.latestVersionCode,
            currentVersionCode = BuildConfig.VERSION_CODE.toLong(),
            forceUpdate = remoteVersion.forceUpdate,
          )
        ) {
          _updateVersionToShow.value = remoteVersion
        }
      }
    }
  }

  fun dismissUpdatePopup() {
    val current = _updateVersionToShow.value ?: return
    if (!current.forceUpdate) {
      prefs.dismissUpdateFor24Hours(current.latestVersionCode)
      _updateVersionToShow.value = null
    }
  }

  fun toggleOnline(isOnline: Boolean, lat: Double, lng: Double) {
    viewModelScope.launch {
      _isBusy.value = true
      val res = repository.updateOnlineStatus(currentUserId, isOnline, lat, lng)
      _isBusy.value = false
      res.onFailure { err ->
        _feedback.emit(err.localizedMessage ?: "Unable to update online status")
      }
    }
  }

  fun acceptRide(ride: TotoRide, driver: TotoDriver) {
    viewModelScope.launch {
      _isBusy.value = true
      val res = repository.acceptRide(ride, driver)
      _isBusy.value = false
      res.onFailure { err ->
        _feedback.emit(err.localizedMessage ?: "Ride may have already been taken")
      }
    }
  }

  fun startRideWithOtp(ride: TotoRide) {
    viewModelScope.launch {
      _isBusy.value = true
      val res = repository.startRide(ride)
      _isBusy.value = false
      res.onFailure { err ->
        _feedback.emit(err.localizedMessage ?: "Failed to start ride")
      }
    }
  }

  fun rejectRide(ride: TotoRide) {
    _dismissedRideKeys.update { it + "${ride.sourceCollection}/${ride.id}" }
    viewModelScope.launch {
      repository.rejectRide(ride)
    }
  }

  fun updateRideStatus(ride: TotoRide, status: String) {
    viewModelScope.launch {
      _isBusy.value = true
      val res = repository.updateRideStatus(ride, status)
      _isBusy.value = false
      res.onFailure { err ->
        _feedback.emit(err.localizedMessage ?: "Failed to update ride status")
      }
    }
  }

  fun changeLanguage(newLanguage: AppLanguage) {
    prefs.saveLanguage(newLanguage)
    _language.value = newLanguage
    viewModelScope.launch {
      repository.updateDriverProfile(currentUserId, mapOf("language" to newLanguage.code))
    }
  }

  fun saveEditedProfile(updates: Map<String, Any>, onSuccess: () -> Unit) {
    viewModelScope.launch {
      _isBusy.value = true
      val res = repository.updateDriverProfile(currentUserId, updates)
      _isBusy.value = false
      res.onSuccess { onSuccess() }
        .onFailure { err ->
          _feedback.emit(err.localizedMessage ?: "Failed to update profile")
        }
    }
  }

  fun deleteProfile(onDeleted: () -> Unit) {
    viewModelScope.launch {
      _isBusy.value = true
      val res = repository.deleteDriverProfile(currentUserId)
      _isBusy.value = false
      res.onSuccess {
        prefs.clearAllOnboarding()
        onDeleted()
      }.onFailure { err ->
        _feedback.emit(err.localizedMessage ?: "Failed to delete profile")
      }
    }
  }

  private companion object {
    const val TAG = "DriverMainVM"
    const val STOP_TIMEOUT_MS = 5000L
  }
}
