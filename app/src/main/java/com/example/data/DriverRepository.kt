package com.example.data

import android.content.Context
import com.example.R
import com.example.auth.GoogleAuthHelper
import com.example.model.DriverAppVersion
import com.example.model.TotoDriver
import com.example.model.TotoRide
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

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
      ?: throw IllegalStateException("Firebase Firestore is not initialized.")

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

  fun observePendingRides(uid: String): Flow<List<TotoRide>> = flow {
    val firestore = requireDb()
    val currentUid = requireUserId()
    check(currentUid == uid) { "Signed-in user changed; refusing ride query." }
    emitAll(
      firestore.collection(COLLECTION_RIDES)
        .whereEqualTo("status", "pending")
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(TotoRide::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .filter { ride -> uid !in ride.rejectedBy }
            .sortedByDescending { it.createdAt?.seconds ?: 0L }
        }
        .catch { error ->
          val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && auth?.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, OperationType.LIST, COLLECTION_RIDES)
          }
          throw error
        }
    )
  }

  fun observeMyRides(uid: String): Flow<List<TotoRide>> = flow {
    val firestore = requireDb()
    val currentUid = requireUserId()
    check(currentUid == uid) { "Signed-in user changed; refusing my rides query." }
    emitAll(
      firestore.collection(COLLECTION_RIDES)
        .whereEqualTo("driverId", uid)
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(TotoRide::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.updatedAt?.seconds ?: it.createdAt?.seconds ?: 0L }
        }
        .catch { error ->
          val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && auth?.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, OperationType.LIST, COLLECTION_RIDES)
          }
          throw error
        }
    )
  }

  suspend fun acceptRide(rideId: String, driverName: String): Result<Unit> {
    val path = "$COLLECTION_RIDES/$rideId"
    return try {
      val firestore = requireDb()
      val uid = requireUserId()
      require(rideId.isNotBlank()) { "Ride ID must not be blank" }
      firestore.collection(COLLECTION_RIDES).document(rideId).update(
        mapOf(
          "status" to "accepted",
          "driverId" to uid,
          "driverName" to driverName.take(100),
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

  suspend fun rejectRide(rideId: String): Result<Unit> {
    val path = "$COLLECTION_RIDES/$rideId"
    return try {
      val firestore = requireDb()
      val uid = requireUserId()
      require(rideId.isNotBlank()) { "Ride ID must not be blank" }
      firestore.collection(COLLECTION_RIDES).document(rideId).update(
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

  suspend fun updateRideStatus(rideId: String, newStatus: String): Result<Unit> {
    val path = "$COLLECTION_RIDES/$rideId"
    return try {
      val firestore = requireDb()
      requireUserId()
      require(rideId.isNotBlank()) { "Ride ID must not be blank" }
      require(newStatus == "completed" || newStatus == "cancelled") { "Invalid target status" }
      firestore.collection(COLLECTION_RIDES).document(rideId).update(
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
    const val COLLECTION_DRIVERS = "toto_drivers"
    const val COLLECTION_RIDES = "toto_rides"
    const val COLLECTION_APP_SETTINGS = "app_settings"
    const val DOC_DRIVER_APP_VERSION = "driver_app_version"

    private fun ensureFirebase(context: Context): Boolean =
      GoogleAuthHelper.ensureFirebaseInitialized(context)

    fun safeAuth(context: Context): FirebaseAuth? {
      return try {
        if (!ensureFirebase(context)) return null
        Firebase.auth
      } catch (e: Exception) {
        e.printStackTrace()
        null
      }
    }

    fun safeFirestore(context: Context): FirebaseFirestore? {
      return try {
        val appCtx = context.applicationContext
        if (!ensureFirebase(appCtx)) return null
        val activeProjectId = runCatching { FirebaseApp.getInstance().options.projectId }.getOrNull()
        val dbId = runCatching { appCtx.getString(R.string.firestore_database_id).trim() }.getOrNull()
        if (activeProjectId == "local-deal-683e8" || dbId.isNullOrBlank()) {
          FirebaseFirestore.getInstance()
        } else {
          try {
            FirebaseFirestore.getInstance(dbId)
          } catch (e: Exception) {
            e.printStackTrace()
            FirebaseFirestore.getInstance()
          }
        }
      } catch (e: Exception) {
        e.printStackTrace()
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
