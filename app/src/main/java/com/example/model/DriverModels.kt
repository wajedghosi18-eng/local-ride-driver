package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName

enum class AppLanguage(val code: String, val nativeLabel: String, val englishLabel: String) {
  BN("bn", "বাংলা", "Bengali"),
  HI("hi", "हिंदी", "Hindi"),
  EN("en", "English", "English");

  companion object {
    fun fromCode(code: String?): AppLanguage =
      entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: BN
  }
}

enum class VehicleType(val id: String) {
  TOTO("toto"),
  AUTO("auto"),
  BIKE("bike");

  companion object {
    fun fromId(id: String?): VehicleType =
      entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: TOTO
  }
}

data class TotoColorOption(
  val id: String,
  val displayName: String,
  val primaryHex: String,
  val secondaryHex: String? = null,
)

object DriverCatalog {
  val westBengalDistricts: List<String> = listOf(
    "Paschim Medinipur",
    "Jhargram",
    "Purba Medinipur",
    "Bankura",
    "Purulia",
    "Howrah",
    "Hooghly",
    "North 24 Parganas",
    "South 24 Parganas",
    "Kolkata",
    "Nadia",
    "Murshidabad",
    "Birbhum",
    "Paschim Bardhaman",
    "Purba Bardhaman",
    "Malda",
    "Dakshin Dinajpur",
    "Uttar Dinajpur",
    "Jalpaiguri",
    "Darjeeling",
    "Kalimpong",
    "Alipurduar",
    "Cooch Behar",
  )

  val localTownsAndVillages: List<String> = listOf(
    "Chandra",
    "Bansol",
    "Beliya",
    "Chaladan",
    "Upar Danga",
    "Midnapore Town",
    "Kharagpur",
    "Salboni",
    "Keshpur",
    "Garhbeta",
    "Ghatal",
    "Belda",
    "Debra",
    "Sabang",
  )

  val totoColors: List<TotoColorOption> = listOf(
    TotoColorOption("Yellow", "Yellow", "#FBC02D"),
    TotoColorOption("Blue", "Blue", "#1E88E5"),
    TotoColorOption("Green", "Green", "#2E7D32"),
    TotoColorOption("Red", "Red", "#D32F2F"),
    TotoColorOption("White", "White", "#F5F5F5"),
    TotoColorOption("Silver/Grey", "Silver/Grey", "#90A4AE"),
    TotoColorOption("Black & Yellow", "Black & Yellow", "#212121", "#FBC02D"),
  )
}

@Keep
@IgnoreExtraProperties
data class TotoDriver(
  @DocumentId val id: String = "",
  val userId: String = "",
  val email: String = "",
  val language: String = "bn",
  val vehicleType: String = "toto",
  val district: String = "Paschim Medinipur",
  val village: String = "",
  val fullName: String = "",
  val mobileNumber: String = "",
  val totoColor: String = "Yellow",
  val totoColorHex: String = "#FBC02D",
  val vehicleNumber: String = "",
  val emergencyContact: String = "",
  val passportPhotoBase64: String = "",
  val aadhaarFrontBase64: String = "",
  val aadhaarBackBase64: String = "",
  val status: String = "pending_or_active",
  @get:PropertyName("isOnline")
  @PropertyName("isOnline")
  val online: Boolean = false,
  val lat: Double = 22.4257,
  val lng: Double = 87.3199,
  val createdAt: Timestamp? = null,
  val updatedAt: Timestamp? = null,
) {
  fun toCreateMap(uid: String): Map<String, Any> {
    val map = mutableMapOf<String, Any?>(
      "userId" to uid,
      "email" to email,
      "language" to language,
      "vehicleType" to vehicleType,
      "district" to district,
      "village" to village,
      "fullName" to fullName,
      "mobileNumber" to mobileNumber,
      "totoColor" to totoColor,
      "totoColorHex" to totoColorHex,
      "vehicleNumber" to vehicleNumber.takeIf { it.isNotBlank() },
      "emergencyContact" to emergencyContact.takeIf { it.isNotBlank() },
      "passportPhotoBase64" to passportPhotoBase64.takeIf { it.isNotBlank() },
      "aadhaarFrontBase64" to aadhaarFrontBase64.takeIf { it.isNotBlank() },
      "aadhaarBackBase64" to aadhaarBackBase64.takeIf { it.isNotBlank() },
      "status" to status,
      "isOnline" to online,
      "lat" to lat,
      "lng" to lng,
      "createdAt" to FieldValue.serverTimestamp(),
      "updatedAt" to FieldValue.serverTimestamp(),
    )
    @Suppress("UNCHECKED_CAST")
    return map.filterValues { it != null } as Map<String, Any>
  }
}

@Keep
@IgnoreExtraProperties
data class TotoRide(
  @DocumentId val id: String = "",
  val passengerId: String = "",
  val passengerName: String = "",
  val pickupVillage: String = "",
  val destination: String = "",
  val pickupLat: Double = 0.0,
  val pickupLng: Double = 0.0,
  val voiceNoteUrl: String = "",
  val status: String = "pending",
  val driverId: String = "",
  val driverName: String = "",
  val vehicleType: String = "toto",
  val district: String = "",
  val rejectedBy: List<String> = emptyList(),
  val createdAt: Timestamp? = null,
  val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class DriverAppVersion(
  val latestVersionCode: Long = 1L,
  val latestVersionName: String = "",
  val apkDownloadUrl: String = "",
  val forceUpdate: Boolean = false,
  val releaseNotes: String = "",
)

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>
  data class Success<T>(val data: T) : UiState<T>
  data class Error(val message: String) : UiState<Nothing>
}
