package com.example.util

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Geocoder
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.core.content.FileProvider
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object MediaAndLocationUtils {
  private const val TAG = "MediaLocationUtils"

  fun createTempCameraUri(context: Context, prefix: String): Uri {
    val dir = File(context.cacheDir, "captured_photos").apply { mkdirs() }
    val file = File(dir, "${prefix}_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(
      context,
      "${context.packageName}.fileprovider",
      file,
    )
  }

  suspend fun compressUriToBase64(
    context: Context,
    uri: Uri,
    maxDimension: Int = 360,
    jpegQuality: Int = 72,
  ): String? = withContext(Dispatchers.IO) {
    try {
      val resolver = context.contentResolver
      val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, boundsOptions) }

      val srcW = boundsOptions.outWidth
      val srcH = boundsOptions.outHeight
      if (srcW <= 0 || srcH <= 0) return@withContext null

      var sampleSize = 1
      while ((srcW / sampleSize) > maxDimension * 2 || (srcH / sampleSize) > maxDimension * 2) {
        sampleSize *= 2
      }

      val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
      val sampled = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, decodeOptions)
      } ?: return@withContext null

      val scale = minOf(
        maxDimension.toFloat() / sampled.width.toFloat(),
        maxDimension.toFloat() / sampled.height.toFloat(),
        1f,
      )
      val targetW = (sampled.width * scale).toInt().coerceAtLeast(1)
      val targetH = (sampled.height * scale).toInt().coerceAtLeast(1)
      val finalBitmap = if (targetW != sampled.width || targetH != sampled.height) {
        Bitmap.createScaledBitmap(sampled, targetW, targetH, true).also {
          if (it != sampled) sampled.recycle()
        }
      } else {
        sampled
      }

      val baos = ByteArrayOutputStream()
      finalBitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, baos)
      finalBitmap.recycle()
      Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to compress image to Base64", e)
      null
    }
  }

  fun decodeBase64ToBitmap(base64: String): Bitmap? {
    if (base64.isBlank()) return null
    return try {
      val clean = base64.substringAfter("base64,", base64)
      val bytes = Base64.decode(clean, Base64.DEFAULT)
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (_: Exception) {
      null
    }
  }

  data class GpsLocationResult(
    val lat: Double,
    val lng: Double,
    val villageOrTown: String,
  )

  @SuppressLint("MissingPermission")
  suspend fun fetchCurrentLocationAndVillage(context: Context): GpsLocationResult? {
    return withContext(Dispatchers.IO) {
      try {
        val fused = LocationServices.getFusedLocationProviderClient(context)
        val tokenSource = CancellationTokenSource()
        val loc = fused.getCurrentLocation(
          Priority.PRIORITY_BALANCED_POWER_ACCURACY,
          tokenSource.token,
        ).await() ?: fused.lastLocation.await()

        if (loc != null) {
          val area = reverseGeocodeName(context, loc.latitude, loc.longitude)
            ?: "Lat ${"%.4f".format(loc.latitude)}, Lng ${"%.4f".format(loc.longitude)}"
          GpsLocationResult(loc.latitude, loc.longitude, area)
        } else {
          null
        }
      } catch (e: Exception) {
        Log.w(TAG, "GPS fetch failed: ${e.message}")
        null
      }
    }
  }

  @Suppress("DEPRECATION")
  private fun reverseGeocodeName(context: Context, lat: Double, lng: Double): String? {
    return try {
      if (!Geocoder.isPresent()) return null
      val geocoder = Geocoder(context, Locale.getDefault())
      val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        geocoder.getFromLocation(lat, lng, 1)
      } else {
        geocoder.getFromLocation(lat, lng, 1)
      }
      val addr = addresses?.firstOrNull() ?: return null
      addr.subLocality
        ?: addr.locality
        ?: addr.featureName
        ?: addr.subAdminArea
    } catch (_: Exception) {
      null
    }
  }
}

class RideRingtonePlayer(private val context: Context) {
  private var ringtone: Ringtone? = null

  fun playAlert() {
    try {
      stop()
      val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
      ringtone = RingtoneManager.getRingtone(context.applicationContext, uri)?.apply {
        play()
      }
    } catch (e: Exception) {
      Log.w("RideAlert", "Unable to play ringtone alert: ${e.message}")
    }
  }

  fun stop() {
    try {
      ringtone?.stop()
      ringtone = null
    } catch (_: Exception) {
    }
  }
}

class VoiceNotePlayer {
  private var mediaPlayer: MediaPlayer? = null
  private var currentUrl: String? = null

  fun togglePlay(
    url: String,
    onStateChanged: (Boolean) -> Unit,
    onError: () -> Unit = {},
  ) {
    if (url.isBlank()) {
      onError()
      return
    }
    if (currentUrl == url && mediaPlayer?.isPlaying == true) {
      stop()
      onStateChanged(false)
      return
    }
    stop()
    try {
      currentUrl = url
      mediaPlayer = MediaPlayer().apply {
        setAudioAttributes(
          AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()
        )
        setDataSource(url)
        setOnPreparedListener {
          it.start()
          onStateChanged(true)
        }
        setOnCompletionListener {
          onStateChanged(false)
          stop()
        }
        setOnErrorListener { _, _, _ ->
          onStateChanged(false)
          stop()
          onError()
          true
        }
        prepareAsync()
      }
    } catch (e: Exception) {
      Log.w("VoiceNotePlayer", "Failed to play voice note: ${e.message}")
      onStateChanged(false)
      stop()
      onError()
    }
  }

  fun stop() {
    try {
      mediaPlayer?.release()
    } catch (_: Exception) {
    }
    mediaPlayer = null
    currentUrl = null
  }
}
