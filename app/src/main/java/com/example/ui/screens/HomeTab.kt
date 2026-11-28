package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.localization.AppStrings
import com.example.model.AppLanguage
import com.example.model.TotoDriver
import com.example.model.TotoRide
import com.example.model.UiState
import com.example.ui.components.DriverAvatar
import com.example.ui.components.LiveAreaMapCard
import com.example.ui.components.TotoColorDot
import com.example.ui.components.VoiceNotePlayerRow
import com.example.ui.theme.AmberCaptain
import com.example.ui.theme.OfflineRed
import com.example.ui.theme.OfflineRedSurface
import com.example.ui.theme.OnlineGreen
import com.example.ui.theme.OnlineGreenSurface
import com.example.util.MediaAndLocationUtils
import com.example.util.RideRingtonePlayer
import com.example.util.VoiceNotePlayer
import kotlinx.coroutines.launch

@Composable
fun HomeTab(
  driver: TotoDriver,
  language: AppLanguage,
  strings: AppStrings,
  pendingRidesState: UiState<List<TotoRide>>,
  myRidesState: UiState<List<TotoRide>>,
  isBusy: Boolean,
  onToggleOnline: (Boolean, Double, Double) -> Unit,
  onAcceptRide: (TotoRide) -> Unit,
  onStartRideWithOtp: (TotoRide) -> Unit,
  onRejectRide: (TotoRide) -> Unit,
  onCompleteRide: (TotoRide) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val ringtonePlayer = remember(context) { RideRingtonePlayer(context) }
  val voiceNotePlayer = remember { VoiceNotePlayer() }
  var playingRideId by remember { mutableStateOf<String?>(null) }
  var lastAlertedRideKey by remember { mutableStateOf<String?>(null) }

  DisposableEffect(Unit) {
    onDispose {
      ringtonePlayer.stop()
      voiceNotePlayer.stop()
    }
  }

  val activeRide = (myRidesState as? UiState.Success)?.data
    ?.firstOrNull { it.status.lowercase() in setOf("accepted", "started", "ongoing", "in_progress") }

  val nextPendingRide = if (driver.online && activeRide == null) {
    (pendingRidesState as? UiState.Success)?.data?.firstOrNull()
  } else {
    null
  }

  val pendingRideKey = nextPendingRide?.let { "${it.sourceCollection}/${it.id}" }

  LaunchedEffect(pendingRideKey, driver.online) {
    if (driver.online && pendingRideKey != null) {
      if (pendingRideKey != lastAlertedRideKey) {
        lastAlertedRideKey = pendingRideKey
        ringtonePlayer.playAlert()
      }
    } else {
      lastAlertedRideKey = null
      ringtonePlayer.stop()
    }
  }

  fun performOnlineToggle(targetOnline: Boolean) {
    val hasFine = ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    val hasCoarse = ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    if (targetOnline && (hasFine || hasCoarse)) {
      scope.launch {
        val gps = MediaAndLocationUtils.fetchCurrentLocationAndVillage(context)
        onToggleOnline(true, gps?.lat ?: driver.lat, gps?.lng ?: driver.lng)
      }
    } else {
      onToggleOnline(targetOnline, driver.lat, driver.lng)
    }
  }

  val locationPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions()
  ) {
    performOnlineToggle(true)
  }

  Column(
    modifier = Modifier
      .widthIn(max = 600.dp)
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    // Top Status Header (Driver Photo, Name, Toto Color Badge, Large ONLINE/OFFLINE Toggle)
    Surface(
      shape = RoundedCornerShape(22.dp),
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 3.dp,
      border = BorderStroke(
        width = 1.5.dp,
        color = if (driver.online) OnlineGreen.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline,
      ),
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          DriverAvatar(
            base64Photo = driver.passportPhotoBase64,
            contentDescription = driver.fullName,
            size = 56.dp,
          )
          Spacer(Modifier.width(12.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = driver.fullName,
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                TotoColorDot(hex = driver.totoColorHex, size = 14.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                  text = strings.localizedColor(driver.totoColor, language),
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.SemiBold,
                )
              }
            }
          }
        }

        Spacer(Modifier.height(14.dp))

        // Large Rapido/Uber Captain style ONLINE / OFFLINE toggle bar
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = if (driver.online) OnlineGreenSurface else OfflineRedSurface,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = if (driver.online) "🟢 ${strings.onlineTitle}" else "🔴 ${strings.offlineTitle}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (driver.online) Color(0xFF14532D) else Color(0xFF7F1D1D),
              )
              Text(
                text = if (driver.online) strings.onlineSubtitle else strings.offlineSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = if (driver.online) Color(0xFF166534) else Color(0xFF991B1B),
              )
            }
            Switch(
              checked = driver.online,
              onCheckedChange = { wantOnline ->
                if (wantOnline) {
                  val hasCoarse = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                  ) == PackageManager.PERMISSION_GRANTED
                  if (!hasCoarse) {
                    locationPermissionLauncher.launch(
                      arrayOf(
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                      )
                    )
                  } else {
                    performOnlineToggle(true)
                  }
                } else {
                  ringtonePlayer.stop()
                  performOnlineToggle(false)
                }
              },
              enabled = !isBusy,
              colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = OnlineGreen,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = OfflineRed,
              ),
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("online_status_switch"),
            )
          }
        }

        // Live 3-collection radar indicator when driver is ONLINE
        AnimatedVisibility(visible = driver.online) {
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFECFDF5),
            border = BorderStroke(1.dp, OnlineGreen.copy(alpha = 0.4f)),
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 10.dp)
              .testTag("radar_active_indicator"),
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Box(
                modifier = Modifier
                  .size(9.dp)
                  .clip(CircleShape)
                  .background(OnlineGreen),
              )
              Spacer(Modifier.width(8.dp))
              Text(
                text = strings.radarActiveStatus,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF14532D),
              )
            }
          }
        }
      }
    }

    // Live Area Map
    LiveAreaMapCard(
      village = driver.village,
      district = driver.district,
      isOnline = driver.online,
      lat = driver.lat,
      lng = driver.lng,
      activeOrPendingRide = activeRide ?: nextPendingRide,
      strings = strings,
    )

    // Active Ride Screen (4-Digit Start PIN verification + Complete Ride)
    AnimatedVisibility(visible = activeRide != null) {
      activeRide?.let { ride ->
        var enteredPin by remember(ride.id, ride.sourceCollection) { mutableStateOf("") }
        var pinError by remember(ride.id, ride.sourceCollection) { mutableStateOf<String?>(null) }
        val isRideStarted = ride.status.lowercase() in setOf("started", "ongoing", "in_progress")

        Surface(
          shape = RoundedCornerShape(22.dp),
          color = MaterialTheme.colorScheme.primaryContainer,
          border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
          modifier = Modifier.testTag("active_ride_card"),
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = strings.activeRideBanner,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
              )
              Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isRideStarted) OnlineGreenSurface else MaterialTheme.colorScheme.surface,
              ) {
                Text(
                  text = if (isRideStarted) strings.pinVerifiedStartedBadge else ride.sourceCollection,
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  color = if (isRideStarted) Color(0xFF14532D) else MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
              }
            }

            RideRouteDetails(ride = ride, strings = strings)

            VoiceNotePlayerRow(
              voiceNoteUrl = ride.voiceNoteUrl,
              isPlaying = playingRideId == ride.id,
              strings = strings,
              onTogglePlay = {
                voiceNotePlayer.togglePlay(
                  url = ride.voiceNoteUrl,
                  onStateChanged = { isPlaying ->
                    playingRideId = if (isPlaying) ride.id else null
                  },
                )
              },
            )

            if (!isRideStarted) {
              Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
              ) {
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                  verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                      imageVector = Icons.Filled.Lock,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                      text = strings.enterStartPinLabel,
                      style = MaterialTheme.typography.titleSmall,
                      fontWeight = FontWeight.Bold,
                    )
                  }

                  OutlinedTextField(
                    value = enteredPin,
                    onValueChange = { raw ->
                      val digits = raw.filter { it.isDigit() }.take(4)
                      enteredPin = digits
                      pinError = null
                    },
                    placeholder = { Text(strings.startPinHint) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = pinError != null,
                    modifier = Modifier
                      .fillMaxWidth()
                      .testTag("start_pin_input"),
                    shape = RoundedCornerShape(12.dp),
                  )

                  if (pinError != null) {
                    Text(
                      text = pinError!!,
                      style = MaterialTheme.typography.bodySmall,
                      color = OfflineRed,
                      fontWeight = FontWeight.Medium,
                    )
                  }

                  Button(
                    onClick = {
                      val expectedOtp = ride.rideOtp.trim()
                      if (expectedOtp.isNotEmpty() && enteredPin.trim() != expectedOtp) {
                        pinError = strings.pinMismatchError
                      } else {
                        pinError = null
                        onStartRideWithOtp(ride)
                      }
                    },
                    enabled = !isBusy,
                    colors = ButtonDefaults.buttonColors(
                      containerColor = AmberCaptain,
                      contentColor = Color(0xFF1E293B),
                    ),
                    modifier = Modifier
                      .fillMaxWidth()
                      .height(48.dp)
                      .testTag("verify_start_pin_button"),
                    shape = RoundedCornerShape(12.dp),
                  ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(strings.verifyPinAndStartBtn, fontWeight = FontWeight.Bold)
                  }
                }
              }
            }

            Button(
              onClick = {
                val expectedOtp = ride.rideOtp.trim()
                if (!isRideStarted && expectedOtp.isNotEmpty() && enteredPin.trim() != expectedOtp) {
                  pinError = strings.pinMismatchError
                } else {
                  pinError = null
                  voiceNotePlayer.stop()
                  onCompleteRide(ride)
                }
              },
              enabled = !isBusy,
              colors = ButtonDefaults.buttonColors(
                containerColor = OnlineGreen,
                contentColor = Color.White,
              ),
              modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("home_complete_ride_button"),
              shape = RoundedCornerShape(14.dp),
            ) {
              Icon(Icons.Filled.Check, contentDescription = null)
              Spacer(Modifier.width(8.dp))
              Text(
                strings.completeRide,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
            }
          }
        }
      }
    }

    // Incoming Pending Ride Request Card (Multi-collection: toto_rides / local_rides / ride_bookings)
    AnimatedVisibility(visible = nextPendingRide != null) {
      nextPendingRide?.let { ride ->
        Surface(
          shape = RoundedCornerShape(22.dp),
          color = MaterialTheme.colorScheme.surface,
          border = BorderStroke(2.5.dp, AmberCaptain),
          tonalElevation = 6.dp,
          modifier = Modifier.testTag("incoming_ride_request_card"),
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
              ) {
                Icon(
                  imageVector = Icons.Filled.NotificationsActive,
                  contentDescription = null,
                  tint = AmberCaptain,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                  text = strings.incomingRequestBadge,
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFFB45309),
                )
              }
              Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
              ) {
                Text(
                  text = ride.sourceCollection,
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.SemiBold,
                  color = MaterialTheme.colorScheme.onSecondaryContainer,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
              }
            }

            RideRouteDetails(ride = ride, strings = strings)

            VoiceNotePlayerRow(
              voiceNoteUrl = ride.voiceNoteUrl,
              isPlaying = playingRideId == ride.id,
              strings = strings,
              onTogglePlay = {
                ringtonePlayer.stop()
                voiceNotePlayer.togglePlay(
                  url = ride.voiceNoteUrl,
                  onStateChanged = { isPlaying ->
                    playingRideId = if (isPlaying) ride.id else null
                  },
                )
              },
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              OutlinedButton(
                onClick = {
                  ringtonePlayer.stop()
                  voiceNotePlayer.stop()
                  onRejectRide(ride)
                },
                enabled = !isBusy,
                colors = ButtonDefaults.outlinedButtonColors(
                  contentColor = OfflineRed,
                ),
                border = BorderStroke(1.5.dp, OfflineRed),
                modifier = Modifier
                  .weight(1f)
                  .height(54.dp)
                  .testTag("reject_ride_button"),
                shape = RoundedCornerShape(14.dp),
              ) {
                Icon(Icons.Filled.Close, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(strings.rejectRide, fontWeight = FontWeight.Bold)
              }

              Button(
                onClick = {
                  ringtonePlayer.stop()
                  voiceNotePlayer.stop()
                  onAcceptRide(ride)
                },
                enabled = !isBusy,
                colors = ButtonDefaults.buttonColors(
                  containerColor = OnlineGreen,
                  contentColor = Color.White,
                ),
                modifier = Modifier
                  .weight(1.3f)
                  .height(54.dp)
                  .testTag("accept_ride_button"),
                shape = RoundedCornerShape(14.dp),
              ) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(strings.acceptRide, fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      }
    }

    // Waiting / Offline Empty Status Card
    if (activeRide == null && nextPendingRide == null) {
      Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center,
        ) {
          if (pendingRidesState is UiState.Loading && driver.online) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
          }
          Text(
            text = if (driver.online) strings.waitingForRidesOnline else strings.goOnlineToReceive,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

@Composable
fun RideRouteDetails(
  ride: TotoRide,
  strings: AppStrings,
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        imageVector = Icons.Filled.Person,
        contentDescription = strings.passengerLabel,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(20.dp),
      )
      Spacer(Modifier.width(8.dp))
      Column {
        Text(
          text = strings.passengerLabel,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = ride.passengerName.ifBlank { "Passenger" },
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
      }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        imageVector = Icons.Filled.LocationOn,
        contentDescription = strings.pickupLabel,
        tint = OnlineGreen,
        modifier = Modifier.size(20.dp),
      )
      Spacer(Modifier.width(8.dp))
      Column {
        Text(
          text = strings.pickupLabel,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = ride.pickupVillage,
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.SemiBold,
        )
      }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        imageVector = Icons.Filled.Flag,
        contentDescription = strings.destinationLabel,
        tint = OfflineRed,
        modifier = Modifier.size(20.dp),
      )
      Spacer(Modifier.width(8.dp))
      Column {
        Text(
          text = strings.destinationLabel,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = ride.destination,
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.SemiBold,
        )
      }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        imageVector = Icons.Filled.DirectionsCar,
        contentDescription = strings.vehicleTypeLabel,
        tint = AmberCaptain,
        modifier = Modifier.size(20.dp),
      )
      Spacer(Modifier.width(8.dp))
      Column {
        Text(
          text = strings.vehicleTypeLabel,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = ride.vehicleType.ifBlank { "toto" }.replaceFirstChar { it.uppercase() },
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.SemiBold,
        )
      }
    }
  }
}
