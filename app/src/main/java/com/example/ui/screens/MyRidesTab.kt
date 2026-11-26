package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.localization.AppStrings
import com.example.model.TotoRide
import com.example.model.UiState
import com.example.ui.components.VoiceNotePlayerRow
import com.example.ui.theme.OfflineRed
import com.example.ui.theme.OnlineGreen
import com.example.ui.theme.OnlineGreenSurface
import com.example.util.VoiceNotePlayer

@Composable
fun MyRidesTab(
  myRidesState: UiState<List<TotoRide>>,
  strings: AppStrings,
  isBusy: Boolean,
  onCompleteRide: (String) -> Unit,
  onCancelRide: (String) -> Unit,
) {
  val voiceNotePlayer = remember { VoiceNotePlayer() }
  var playingRideId by remember { mutableStateOf<String?>(null) }

  DisposableEffect(Unit) {
    onDispose { voiceNotePlayer.stop() }
  }

  Column(
    modifier = Modifier
      .widthIn(max = 600.dp)
      .fillMaxSize()
      .padding(horizontal = 16.dp, vertical = 12.dp),
  ) {
    Text(
      text = strings.myRidesTitle,
      style = MaterialTheme.typography.headlineMedium,
      fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(12.dp))

    when (myRidesState) {
      is UiState.Loading -> {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator()
        }
      }
      is UiState.Error -> {
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.errorContainer,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = myRidesState.message,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(16.dp),
          )
        }
      }
      is UiState.Success -> {
        val rides = myRidesState.data
        if (rides.isEmpty()) {
          Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.padding(20.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Image(
                painter = painterResource(id = R.drawable.img_driver_hero_banner),
                contentDescription = strings.noRidesYetTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                  .fillMaxWidth()
                  .height(150.dp)
                  .clip(RoundedCornerShape(16.dp)),
              )
              Spacer(Modifier.height(14.dp))
              Text(
                text = strings.noRidesYetTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
              )
              Spacer(Modifier.height(4.dp))
              Text(
                text = strings.noRidesYetSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        } else {
          LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
          ) {
            items(rides, key = { it.id }) { ride ->
              RideHistoryCard(
                ride = ride,
                strings = strings,
                isPlaying = playingRideId == ride.id,
                isBusy = isBusy,
                onToggleVoice = {
                  voiceNotePlayer.togglePlay(
                    url = ride.voiceNoteUrl,
                    onStateChanged = { playing ->
                      playingRideId = if (playing) ride.id else null
                    },
                  )
                },
                onComplete = {
                  voiceNotePlayer.stop()
                  onCompleteRide(ride.id)
                },
                onCancel = {
                  voiceNotePlayer.stop()
                  onCancelRide(ride.id)
                },
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun RideHistoryCard(
  ride: TotoRide,
  strings: AppStrings,
  isPlaying: Boolean,
  isBusy: Boolean,
  onToggleVoice: () -> Unit,
  onComplete: () -> Unit,
  onCancel: () -> Unit,
) {
  val statusLabel = when (ride.status) {
    "accepted" -> strings.statusAccepted
    "completed" -> strings.statusCompleted
    else -> strings.statusCancelled
  }
  val badgeColor = when (ride.status) {
    "accepted" -> MaterialTheme.colorScheme.secondaryContainer
    "completed" -> OnlineGreenSurface
    else -> MaterialTheme.colorScheme.errorContainer
  }
  val badgeTextColor = when (ride.status) {
    "accepted" -> MaterialTheme.colorScheme.onSecondaryContainer
    "completed" -> Color(0xFF14532D)
    else -> MaterialTheme.colorScheme.onErrorContainer
  }

  Surface(
    shape = RoundedCornerShape(20.dp),
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = 2.dp,
    border = BorderStroke(
      width = if (ride.status == "accepted") 1.5.dp else 1.dp,
      color = if (ride.status == "accepted") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
    ),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("ride_card_${ride.id}"),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = badgeColor,
        ) {
          Text(
            text = statusLabel,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = badgeTextColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
          )
        }
      }

      RideRouteDetails(ride = ride, strings = strings)

      VoiceNotePlayerRow(
        voiceNoteUrl = ride.voiceNoteUrl,
        isPlaying = isPlaying,
        strings = strings,
        onTogglePlay = onToggleVoice,
      )

      if (ride.status == "accepted") {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          OutlinedButton(
            onClick = onCancel,
            enabled = !isBusy,
            border = BorderStroke(1.dp, OfflineRed),
            modifier = Modifier
              .weight(1f)
              .minimumInteractiveComponentSize()
              .testTag("my_rides_cancel_button"),
            shape = RoundedCornerShape(12.dp),
          ) {
            Text(strings.cancelRide, color = OfflineRed)
          }
          Button(
            onClick = onComplete,
            enabled = !isBusy,
            modifier = Modifier
              .weight(1.2f)
              .minimumInteractiveComponentSize()
              .testTag("my_rides_complete_button"),
            shape = RoundedCornerShape(12.dp),
          ) {
            Text(strings.completeRide)
          }
        }
      }
    }
  }
}
