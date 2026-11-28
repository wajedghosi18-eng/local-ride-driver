package com.example.data

import android.content.Context
import android.util.Log
import com.example.R
import com.example.auth.GoogleAuthHelper
import com.example.model.DriverAppVersion
import com.example.model.TotoDriver
import com.example.model.TotoRide
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class DriverRepository(
  private val db: FirebaseFirestore?,
  private val auth: FirebaseAuth? = runCatching { Firebase.auth }.getOrNull(),
  private val fallbackUserId: String? = null,
  private val appContext: Context? = null,
) {
  constructor(context: Context) : this(
    db = safeFirestore(context),
    auth = safeAuth(context),
    appContext = context.applicationContext,
  )

  private fun requireDb(): FirebaseFirestore =
    db
      ?: appContext?.let { safeFirestore(it) }
      ?: throw IllegalStateException(
        lastInitError?.let { "Firebase Firestore is not initialized ($it)." }
          ?: "Firebase Firestore is not initialized."
      )

  private fun requireUserId(): String =
    auth?.currentUser?.uid
      ?: fallbackUserId?.takeIf { it.isNotBlank() }
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

  suspend fun getDriverProfile(uid: String): Result<TotoDriver?> {
    val path = "$COLLECTION_DRIVERS/$uid"
    return try {
      val firestore = requireDb()
      val currentUid = requireUserId()
      check(currentUid == uid) { "Signed-in user changed; refusing cross-user read." }
      val snap = firestore.collection(COLLECTION_DRIVERS).document(uid).get().await()
      if (!snap.exists()) {
        Result.success(null)
      } else {
        Result.success(
          snap.toObject(TotoDriver::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        )
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, path)
      Result.failure(e)
    }
  }

  suspend fun findDriverProfileByEmailOrUid(uid: String, email: String): Result<TotoDriver?> {
    val path = "$COLLECTION_DRIVERS/$uid"
    return try {
      val firestore = requireDb()
      val trimmedEmail = email.trim()
      if (trimmedEmail.isNotBlank()) {
        val query = firestore.collection(COLLECTION_DRIVERS)
          .whereEqualTo("email", trimmedEmail)
          .limit(1)
          .get()
          .await()
        val firstDoc = query.documents.firstOrNull()
        if (firstDoc != null && firstDoc.exists()) {
          return Result.success(
            firstDoc.toObject(TotoDriver::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          )
        }
        val lowerEmail = trimmedEmail.lowercase()
        if (lowerEmail != trimmedEmail) {
          val lowerQuery = firestore.collection(COLLECTION_DRIVERS)
            .whereEqualTo("email", lowerEmail)
            .limit(1)
            .get()
            .await()
          val lowerDoc = lowerQuery.documents.firstOrNull()
          if (lowerDoc != null && lowerDoc.exists()) {
            return Result.success(
              lowerDoc.toObject(TotoDriver::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            )
          }
        }
      }
      if (uid.isNotBlank()) {
        val snap = firestore.collection(COLLECTION_DRIVERS).document(uid).get().await()
        if (snap.exists()) {
          return Result.success(
            snap.toObject(TotoDriver::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          )
        }
      }
      Result.success(null)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, path)
      Result.failure(e)
    }
  }

  fun observeDriverProfile(uid: String): Flow<TotoDriver?> = flow {
    val firestore = requireDb()
    val currentUid = requireUserId()
    check(currentUid == uid) { "Signed-in user changed; refusing cross-user stream." }
    val path = "$COLLECTION_DRIVERS/$uid"
    emitAll(
      firestore.collection(COLLECTION_DRIVERS)
        .document(uid)
        .snapshots()
        .map { snap ->
          if (snap.exists()) {
            snap.toObject(TotoDriver::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          } else {
            null
          }
        }
        .catch { error ->
          val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && auth?.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, OperationType.GET, path)
          }
          throw error
        }
    )
  }

  suspend fun saveDriverProfile(driver: TotoDriver): Result<Unit> {
    var path = COLLECTION_DRIVERS
    return try {
      val firestore = requireDb()
      val uid = driver.userId.takeIf { auth?.currentUser?.uid == null && it.isNotBlank() } ?: requireUserId()
      path = "$COLLECTION_DRIVERS/$uid"
      require(driver.fullName.trim().length in 2..100) { "Full name must be 2..100 chars" }
      require(driver.mobileNumber.trim().length in 10..15) { "Mobile must be 10..15 chars" }
      require(driver.district.trim().length in 2..80) { "District must be 2..80 chars" }
      require(driver.village.trim().length in 1..120) { "Village must be 1..120 chars" }

      val payload = driver.copy(
        fullName = driver.fullName.trim(),
        mobileNumber = driver.mobileNumber.trim(),
        district = driver.district.trim(),
        village = driver.village.trim(),
        vehicleNumber = driver.vehicleNumber.trim(),
        emergencyContact = driver.emergencyContact.trim(),
      ).toCreateMap(uid)

      firestore.collection(COLLECTION_DRIVERS).document(uid).set(payload).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, path)
      Result.failure(e)
    }
  }

  suspend fun updateOnlineStatus(
    uid: String,
    isOnline: Boolean,
    lat: Double,
    lng: Double,
  ): Result<Unit> {
    val path = "$COLLECTION_DRIVERS/$uid"
    return try {
      val firestore = requireDb()
      val currentUid = requireUserId()
      check(currentUid == uid) { "Signed-in user changed; refusing cross-user write." }
      firestore.collection(COLLECTION_DRIVERS)
        .document(uid)
        .update(
          mapOf(
            "isOnline" to isOnline,
            "lat" to lat.coerceIn(-90.0, 90.0),
            "lng" to lng.coerceIn(-180.0, 180.0),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun updateDriverProfile(
    uid: String,
    updates: Map<String, Any>,
  ): Result<Unit> {
    val path = "$COLLECTION_DRIVERS/$uid"
    return try {
      val firestore = requireDb()
      val currentUid = requireUserId()
      check(currentUid == uid) { "Signed-in user changed; refusing cross-user write." }
      val payload = updates.toMutableMap().apply {
        put("updatedAt", FieldValue.serverTimestamp())
      }
      firestore.collection(COLLECTION_DRIVERS).document(uid).update(payload).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun deleteDriverProfile(uid: String): Result<Unit> {
    val path = "$COLLECTION_DRIVERS/$uid"
    return try {
      val firestore = requireDb()
      val currentUid = requireUserId()
      check(currentUid == uid) { "Signed-in user changed; refusing cross-user delete." }
      firestore.collection(COLLECTION_DRIVERS).document(uid).delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, path)
      Result.failure(e)
    }
  }

  fun observePendingRides(
    uid: String,
    driverLat: Double = 0.0,
    driverLng: Double = 0.0,
  ): Flow<List<TotoRide>> = callbackFlow {
    val firestore = requireDb()
    val currentUid = requireUserId()
    check(currentUid == uid) { "Signed-in user changed; refusing ride query." }

    val ridesByCollection = ConcurrentHashMap<String, List<TotoRide>>()
    val registrations = mutableListOf<ListenerRegistration>()

    fun emitMerged() {
      val merged = RIDE_COLLECTIONS
        .flatMap { collectionName -> ridesByCollection[collectionName].orEmpty() }
        .distinctBy { "${it.sourceCollection}/${it.id}" }
        .sortedByDescending { it.createdAt?.seconds ?: it.updatedAt?.seconds ?: 0L }
      trySend(merged)
    }

    for (collectionName in RIDE_COLLECTIONS) {
      val registration = firestore.collection(collectionName)
        .addSnapshotListener { snapshot, error ->
          if (error != null) {
            Log.w(TAG, "Listener warning on $collectionName: ${error.message}")
            handleFirestoreError(error, OperationType.LIST, collectionName)
            ridesByCollection.remove(collectionName)
            emitMerged()
            return@addSnapshotListener
          }
          if (snapshot == null) return@addSnapshotListener

          val matchingRides = snapshot.documents.mapNotNull { doc ->
            parseIncomingRideIfEligible(
              doc = doc,
              sourceCollection = collectionName,
              uid = uid,
              driverLat = driverLat,
              driverLng = driverLng,
            )
          }
          ridesByCollection[collectionName] = matchingRides
          emitMerged()
        }
      registrations.add(registration)
    }

    awaitClose {
      registrations.forEach { reg ->
        runCatching { reg.remove() }
      }
    }
  }

  fun observeMyRides(uid: String): Flow<List<TotoRide>> = callbackFlow {
    val firestore = requireDb()
    val currentUid = requireUserId()
    check(currentUid == uid) { "Signed-in user changed; refusing my rides query." }

    val ridesByCollection = ConcurrentHashMap<String, List<TotoRide>>()
    val registrations = mutableListOf<ListenerRegistration>()

    fun emitMerged() {
      val merged = RIDE_COLLECTIONS
        .flatMap { collectionName -> ridesByCollection[collectionName].orEmpty() }
        .distinctBy { "${it.sourceCollection}/${it.id}" }
        .sortedByDescending { it.updatedAt?.seconds ?: it.createdAt?.seconds ?: 0L }
      trySend(merged)
    }

    for (collectionName in RIDE_COLLECTIONS) {
      val registration = firestore.collection(collectionName)
        .addSnapshotListener { snapshot, error ->
          if (error != null) {
            Log.w(TAG, "My rides listener warning on $collectionName: ${error.message}")
            handleFirestoreError(error, OperationType.LIST, collectionName)
            ridesByCollection.remove(collectionName)
            emitMerged()
            return@addSnapshotListener
          }
          if (snapshot == null) return@addSnapshotListener

          val driverRides = snapshot.documents.mapNotNull { doc ->
            val data = doc.data ?: return@mapNotNull null
            val assignedDriverId = (data["driverId"] as? String)?.trim().orEmpty()
            if (assignedDriverId != uid) return@mapNotNull null
            parseRideDocument(doc, collectionName)
          }
          ridesByCollection[collectionName] = driverRides
          emitMerged()
        }
      registrations.add(registration)
    }

    awaitClose {
      registrations.forEach { reg ->
        runCatching { reg.remove() }
      }
    }
  }

  suspend fun acceptRide(ride: TotoRide, driver: TotoDriver): Result<Unit> {
    val collection = ride.sourceCollection.ifBlank { COLLECTION_RIDES }
    val path = "$collection/${ride.id}"
    return try {
      val firestore = requireDb()
      val uid = requireUserId()
      require(ride.id.isNotBlank()) { "Ride ID must not be blank" }
      val updates = mapOf(
        "status" to "accepted",
        "driverId" to uid,
        "driverName" to driver.fullName.take(100),
        "driverPhone" to driver.mobileNumber,
        "driverPhoto" to driver.passportPhotoBase64,
        "totoColor" to driver.totoColor,
        "vehicleNumber" to driver.vehicleNumber,
        "driverLat" to driver.lat.coerceIn(-90.0, 90.0),
        "driverLng" to driver.lng.coerceIn(-180.0, 180.0),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
      firestore.collection(collection).document(ride.id).update(updates).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun startRide(ride: TotoRide): Result<Unit> {
    val collection = ride.sourceCollection.ifBlank { COLLECTION_RIDES }
    val path = "$collection/${ride.id}"
    return try {
      val firestore = requireDb()
      requireUserId()
      require(ride.id.isNotBlank()) { "Ride ID must not be blank" }
      firestore.collection(collection).document(ride.id).update(
        mapOf(
          "status" to "started",
          "otpVerified" to true,
          "startedAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun rejectRide(ride: TotoRide): Result<Unit> {
    val collection = ride.sourceCollection.ifBlank { COLLECTION_RIDES }
    val path = "$collection/${ride.id}"
    return try {
      val firestore = requireDb()
      val uid = requireUserId()
      require(ride.id.isNotBlank()) { "Ride ID must not be blank" }
      firestore.collection(collection).document(ride.id).update(
        mapOf(
          "rejectedBy" to FieldValue.arrayUnion(uid),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun updateRideStatus(ride: TotoRide, newStatus: String): Result<Unit> {
    val collection = ride.sourceCollection.ifBlank { COLLECTION_RIDES }
    val path = "$collection/${ride.id}"
    return try {
      val firestore = requireDb()
      requireUserId()
      require(ride.id.isNotBlank()) { "Ride ID must not be blank" }
      require(newStatus in setOf("started", "completed", "cancelled")) { "Invalid target status" }
      firestore.collection(collection).document(ride.id).update(
        mapOf(
          "status" to newStatus,
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  private fun parseIncomingRideIfEligible(
    doc: DocumentSnapshot,
    sourceCollection: String,
    uid: String,
    driverLat: Double,
    driverLng: Double,
  ): TotoRide? {
    val data = doc.data ?: return null
    val rawStatus = (data["status"] as? String)?.trim()?.lowercase().orEmpty()
    if (rawStatus !in PENDING_STATUSES) return null

    val rawVehicleType = listOf("vehicleType", "vehicle", "rideType", "cabType")
      .firstNotNullOfOrNull { key -> (data[key] as? String)?.trim()?.takeIf { it.isNotEmpty() } }
      ?.lowercase()
      .orEmpty()

    val vehicleMatches = rawVehicleType.isEmpty() ||
      rawVehicleType in ACCEPTED_VEHICLE_TYPES ||
      rawVehicleType.contains("toto") ||
      rawVehicleType.contains("bike") ||
      rawVehicleType.contains("auto") ||
      rawVehicleType.contains("rickshaw")
    if (!vehicleMatches) return null

    val ride = parseRideDocument(doc, sourceCollection)
    if (uid in ride.rejectedBy) return null
    if (ride.driverId.isNotBlank() && ride.driverId != uid) return null
    if (!isWithinRadarRange(driverLat, driverLng, ride.pickupLat, ride.pickupLng)) return null

    return ride
  }

  private fun parseRideDocument(doc: DocumentSnapshot, sourceCollection: String): TotoRide {
    val data = doc.data ?: emptyMap()

    val passengerId = listOf("passengerId", "userId", "customerId")
      .firstNotNullOfOrNull { key -> (data[key] as? String)?.trim()?.takeIf { it.isNotEmpty() } }
      .orEmpty()

    val passengerName = listOf("passengerName", "userName", "customerName", "riderName", "name")
      .firstNotNullOfOrNull { key -> (data[key] as? String)?.trim()?.takeIf { it.isNotEmpty() } }
      ?: "Passenger"

    val passengerPhone = listOf("passengerPhone", "userPhone", "customerPhone", "phone", "mobile", "mobileNumber")
      .firstNotNullOfOrNull { key -> data[key]?.toString()?.trim()?.takeIf { it.isNotEmpty() } }
      .orEmpty()

    val pickupVillage = listOf("pickupAddress", "pickup", "pickupVillage", "pickupLocation", "from", "source")
      .firstNotNullOfOrNull { key -> (data[key] as? String)?.trim()?.takeIf { it.isNotEmpty() } }
      ?: "Pickup Location"

    val destination = listOf("dropAddress", "drop", "destination", "dropLocation", "to")
      .firstNotNullOfOrNull { key -> (data[key] as? String)?.trim()?.takeIf { it.isNotEmpty() } }
      ?: "Destination"

    val pickupLat = extractCoordinate(data, isLat = true)
    val pickupLng = extractCoordinate(data, isLat = false)

    val vehicleType = listOf("vehicleType", "vehicle", "rideType", "cabType")
      .firstNotNullOfOrNull { key -> (data[key] as? String)?.trim()?.takeIf { it.isNotEmpty() } }
      ?: "toto"

    val rideOtp = listOf("rideOtp", "otp", "startOtp", "startPin", "pin")
      .firstNotNullOfOrNull { key ->
        when (val raw = data[key]) {
          is Number -> raw.toLong().toString()
          is String -> raw.trim().takeIf { it.isNotEmpty() }
          else -> null
        }
      }
      .orEmpty()

    val rejectedBy = (data["rejectedBy"] as? List<*>)
      ?.mapNotNull { it?.toString()?.trim()?.takeIf { id -> id.isNotEmpty() } }
      ?: emptyList()

    val createdAt = parseTimestamp(data["createdAt"] ?: data["timestamp"] ?: data["bookedAt"])
      ?: doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
    val updatedAt = parseTimestamp(data["updatedAt"])
      ?: doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)

    return TotoRide(
      id = doc.id,
      sourceCollection = sourceCollection,
      passengerId = passengerId,
      passengerName = passengerName,
      passengerPhone = passengerPhone,
      pickupVillage = pickupVillage,
      destination = destination,
      pickupLat = pickupLat,
      pickupLng = pickupLng,
      voiceNoteUrl = (data["voiceNoteUrl"] as? String)?.trim().orEmpty(),
      status = (data["status"] as? String)?.trim()?.ifEmpty { "pending" } ?: "pending",
      driverId = (data["driverId"] as? String)?.trim().orEmpty(),
      driverName = (data["driverName"] as? String)?.trim().orEmpty(),
      vehicleType = vehicleType,
      rideOtp = rideOtp,
      district = (data["district"] as? String)?.trim().orEmpty(),
      rejectedBy = rejectedBy,
      createdAt = createdAt,
      updatedAt = updatedAt,
    )
  }

  private fun extractCoordinate(data: Map<String, Any>, isLat: Boolean): Double {
    val keys = if (isLat) {
      listOf("pickupLat", "lat", "pickup_lat", "originLat")
    } else {
      listOf("pickupLng", "lng", "pickup_lng", "originLng", "lon", "pickupLon")
    }
    for (key in keys) {
      val value = data[key]
      val parsed = (value as? Number)?.toDouble() ?: (value as? String)?.toDoubleOrNull()
      if (parsed != null) return parsed
    }
    val nested = listOf("pickupCoords", "pickupLocationCoords", "pickupPoint", "location")
      .firstNotNullOfOrNull { data[it] }
    if (nested is GeoPoint) {
      return if (isLat) nested.latitude else nested.longitude
    }
    if (nested is Map<*, *>) {
      val nestedKeys = if (isLat) listOf("lat", "latitude") else listOf("lng", "lon", "longitude")
      for (k in nestedKeys) {
        val v = nested[k]
        val parsed = (v as? Number)?.toDouble() ?: (v as? String)?.toDoubleOrNull()
        if (parsed != null) return parsed
      }
    }
    return 0.0
  }

  private fun parseTimestamp(raw: Any?): Timestamp? = when (raw) {
    is Timestamp -> raw
    is Number -> {
      val millis = raw.toLong()
      if (millis > 10_000_000_000L) Timestamp(millis / 1000L, ((millis % 1000L) * 1_000_000L).toInt())
      else Timestamp(millis, 0)
    }
    else -> null
  }

  private fun isWithinRadarRange(
    driverLat: Double,
    driverLng: Double,
    rideLat: Double,
    rideLng: Double,
  ): Boolean {
    if (rideLat == 0.0 && rideLng == 0.0) return true
    if (driverLat == 0.0 && driverLng == 0.0) return true
    if (rideLat !in -90.0..90.0 || rideLng !in -180.0..180.0) return true
    val earthRadiusKm = 6371.0
    val dLat = Math.toRadians(rideLat - driverLat)
    val dLng = Math.toRadians(rideLng - driverLng)
    val a = sin(dLat / 2) * sin(dLat / 2) +
      cos(Math.toRadians(driverLat)) * cos(Math.toRadians(rideLat)) *
      sin(dLng / 2) * sin(dLng / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return (earthRadiusKm * c) <= MAX_RADAR_DISTANCE_KM
  }

  suspend fun fetchLatestAppVersion(): Result<DriverAppVersion?> {
    val path = "$COLLECTION_APP_SETTINGS/$DOC_DRIVER_APP_VERSION"
    return try {
      val firestore = requireDb()
      requireUserId()
      val snap = firestore.collection(COLLECTION_APP_SETTINGS).document(DOC_DRIVER_APP_VERSION).get().await()
      if (!snap.exists()) {
        Result.success(null)
      } else {
        val data = snap.data ?: emptyMap()
        val version = DriverAppVersion(
          latestVersionCode = (data["latestVersionCode"] as? Number)?.toLong() ?: 1L,
          latestVersionName = data["latestVersionName"] as? String ?: "",
          apkDownloadUrl = data["apkDownloadUrl"] as? String ?: "",
          forceUpdate = data["forceUpdate"] as? Boolean ?: false,
          releaseNotes = data["releaseNotes"] as? String ?: "",
        )
        Result.success(version)
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, path)
      Result.failure(e)
    }
  }

  companion object {
    private const val TAG = "DriverRepository"
    const val COLLECTION_DRIVERS = "toto_drivers"
    const val COLLECTION_RIDES = "toto_rides"
    const val COLLECTION_LOCAL_RIDES = "local_rides"
    const val COLLECTION_RIDE_BOOKINGS = "ride_bookings"
    val RIDE_COLLECTIONS = listOf(
      COLLECTION_RIDES,
      COLLECTION_LOCAL_RIDES,
      COLLECTION_RIDE_BOOKINGS,
    )
    private val PENDING_STATUSES = setOf("searching", "pending", "requested")
    private val ACCEPTED_VEHICLE_TYPES = setOf("toto", "bike", "auto")
    private const val MAX_RADAR_DISTANCE_KM = 50.0
    const val COLLECTION_APP_SETTINGS = "app_settings"
    const val DOC_DRIVER_APP_VERSION = "driver_app_version"

    @Volatile
    private var lastInitError: String? = null

    private fun ensureFirebase(context: Context): Boolean =
      GoogleAuthHelper.ensureFirebaseInitialized(context)

    fun safeAuth(context: Context): FirebaseAuth? {
      return try {
        if (!ensureFirebase(context)) return null
        val app = FirebaseApp.getApps(context.applicationContext).firstOrNull()
        if (app != null) FirebaseAuth.getInstance(app) else Firebase.auth
      } catch (e: Exception) {
        e.printStackTrace()
        null
      }
    }

    fun safeFirestore(context: Context): FirebaseFirestore? {
      return try {
        val appCtx = context.applicationContext
        if (!ensureFirebase(appCtx)) {
          lastInitError = GoogleAuthHelper.lastInitError ?: "FirebaseApp failed to initialize"
          return null
        }
        val firebaseApp = FirebaseApp.getApps(appCtx).firstOrNull()
        if (firebaseApp == null) {
          lastInitError = "No FirebaseApp instances registered"
          return null
        }
        val activeProjectId = firebaseApp.options.projectId
        val dbId = runCatching { appCtx.getString(R.string.firestore_database_id).trim() }.getOrNull()
        val instance = if (
          activeProjectId == "local-deal-683e8" ||
          activeProjectId == "local-deal-663e8" ||
          dbId.isNullOrBlank()
        ) {
          FirebaseFirestore.getInstance(firebaseApp)
        } else {
          try {
            FirebaseFirestore.getInstance(firebaseApp, dbId)
          } catch (e: Exception) {
            e.printStackTrace()
            FirebaseFirestore.getInstance(firebaseApp)
          }
        }
        lastInitError = null
        instance
      } catch (e: Exception) {
        e.printStackTrace()
        lastInitError = "${e.javaClass.simpleName}: ${e.localizedMessage ?: e.message}"
        null
      }
    }

    fun createSafe(context: Context, fallbackUserId: String? = null): DriverRepository {
      val appCtx = context.applicationContext
      return DriverRepository(
        db = safeFirestore(appCtx),
        auth = safeAuth(appCtx),
        fallbackUserId = fallbackUserId,
        appContext = appCtx,
      )
    }
  }
}
