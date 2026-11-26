package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.localization.AppStrings
import com.example.model.TotoRide
import com.example.ui.theme.AmberCaptain
import com.example.ui.theme.EmeraldDeep
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.OnlineGreen
import com.example.util.MediaAndLocationUtils

@Composable
fun DriverAvatar(
  base64Photo: String,
  contentDescription: String,
  size: Dp = 52.dp,
  modifier: Modifier = Modifier,
) {
  val bitmap = remember(base64Photo) {
    MediaAndLocationUtils.decodeBase64ToBitmap(base64Photo)?.asImageBitmap()
  }
  Box(
    modifier = modifier
      .size(size)
      .clip(CircleShape)
      .background(MaterialTheme.colorScheme.primaryContainer)
      .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), CircleShape),
    contentAlignment = Alignment.Center,
  ) {
    if (bitmap != null) {
      Image(
        bitmap = bitmap,
        contentDescription = contentDescription,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
      )
    } else {
      Icon(
        imageVector = Icons.Filled.Person,
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.size(size * 0.55f),
      )
    }
  }
}

@Composable
fun Base64DocumentImage(
  base64: String,
  contentDescription: String,
  modifier: Modifier = Modifier,
) {
  val bitmap = remember(base64) {
    MediaAndLocationUtils.decodeBase64ToBitmap(base64)?.asImageBitmap()
  }
  if (bitmap != null) {
    Image(
      bitmap = bitmap,
      contentDescription = contentDescription,
      modifier = modifier,
      contentScale = ContentScale.Crop,
    )
  }
}

@Composable
fun TotoColorDot(
  hex: String,
  secondaryHex: String? = null,
  size: Dp = 16.dp,
) {
  val primaryColor = remember(hex) { parseHexColor(hex, Color(0xFFFBC02D)) }
  val secondaryColor = remember(secondaryHex) {
    secondaryHex?.let { parseHexColor(it, primaryColor) }
  }
  Canvas(
    modifier = Modifier
      .size(size)
      .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
  ) {
    if (secondaryColor != null) {
      drawArc(
        color = primaryColor,
        startAngle = 90f,
        sweepAngle = 180f,
        useCenter = true,
      )
      drawArc(
        color = secondaryColor,
        startAngle = 270f,
        sweepAngle = 180f,
        useCenter = true,
      )
    } else {
      drawCircle(color = primaryColor)
    }
  }
}

fun parseHexColor(hex: String, fallback: Color): Color {
  return try {
    Color(android.graphics.Color.parseColor(hex))
  } catch (_: Exception) {
    fallback
  }
}

@Composable
fun VoiceNotePlayerRow(
  voiceNoteUrl: String,
  isPlaying: Boolean,
  strings: AppStrings,
  onTogglePlay: () -> Unit,
  modifier: Modifier = Modifier,
) {
  if (voiceNoteUrl.isBlank()) return
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(14.dp),
    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.weight(1f),
      ) {
        Icon(
          imageVector = Icons.Filled.GraphicEq,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Spacer(Modifier.width(8.dp))
        Text(
          text = if (isPlaying) strings.pauseVoiceNote else strings.playVoiceNote,
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSecondaryContainer,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      FilledTonalButton(
        onClick = onTogglePlay,
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .testTag("voice_note_toggle_button"),
      ) {
        Icon(
          imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
          contentDescription = if (isPlaying) strings.pauseVoiceNote else strings.playVoiceNote,
        )
      }
    }
  }
}

@Composable
fun LiveAreaMapCard(
  village: String,
  district: String,
  isOnline: Boolean,
  lat: Double,
  lng: Double,
  activeOrPendingRide: TotoRide?,
  strings: AppStrings,
  modifier: Modifier = Modifier,
) {
  val infiniteTransition = rememberInfiniteTransition(label = "radarPulse")
  val pulse by infiniteTransition.animateFloat(
    initialValue = 0.2f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 1600),
      repeatMode = RepeatMode.Restart,
    ),
    label = "pulseAlpha",
  )

  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(22.dp),
    tonalElevation = 2.dp,
    color = MaterialTheme.colorScheme.surface,
  ) {
    Column {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(175.dp)
          .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
          .background(
            Brush.linearGradient(
              colors = listOf(Color(0xFFE4F2EA), Color(0xFFD2EADF), Color(0xFFEAF3DC))
            )
          )
      ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
          val w = size.width
          val h = size.height

          // Rural & town road grid
          val roadColor = Color.White.copy(alpha = 0.85f)
          val accentRoad = Color(0xFFFDE68A).copy(alpha = 0.75f)
          drawLine(roadColor, Offset(0f, h * 0.30f), Offset(w, h * 0.42f), strokeWidth = 10f)
          drawLine(roadColor, Offset(w * 0.22f, 0f), Offset(w * 0.35f, h), strokeWidth = 9f)
          drawLine(roadColor, Offset(w * 0.68f, 0f), Offset(w * 0.58f, h), strokeWidth = 8f)
          drawLine(accentRoad, Offset(0f, h * 0.72f), Offset(w, h * 0.56f), strokeWidth = 14f)

          // Radar rings around driver stand
          val center = Offset(w * 0.48f, h * 0.52f)
          if (isOnline) {
            drawCircle(
              color = OnlineGreen.copy(alpha = (1f - pulse) * 0.35f),
              radius = 34f + pulse * 120f,
              center = center,
            )
          }
          drawCircle(
            color = EmeraldPrimary.copy(alpha = 0.14f),
            radius = 95f,
            center = center,
            style = Stroke(width = 2.5f),
          )
          // Driver pin
          drawCircle(color = Color.White, radius = 20f, center = center)
          drawCircle(
            color = if (isOnline) OnlineGreen else Color(0xFFDC2626),
            radius = 13f,
            center = center,
          )

          // Incoming pickup pin when a ride is available
          if (activeOrPendingRide != null) {
            val pickupCenter = Offset(w * 0.74f, h * 0.30f)
            drawLine(
              color = EmeraldDeep.copy(alpha = 0.6f),
              start = center,
              end = pickupCenter,
              strokeWidth = 4f,
            )
            drawCircle(color = Color.White, radius = 18f, center = pickupCenter)
            drawCircle(color = AmberCaptain, radius = 12f, center = pickupCenter)
          }
        }

        Surface(
          modifier = Modifier
            .align(Alignment.TopStart)
            .padding(12.dp),
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        ) {
          Text(
            text = strings.liveAreaMapTitle,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
          )
        }

        Surface(
          modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(12.dp),
          shape = RoundedCornerShape(10.dp),
          color = EmeraldDeep.copy(alpha = 0.88f),
        ) {
          Text(
            text = "${"%.4f".format(lat)}, ${"%.4f".format(lng)}",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
          )
        }
      }

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "${strings.nearbyStandPrefix} ${village.ifBlank { district }}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = district,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        if (activeOrPendingRide != null) {
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = AmberCaptain.copy(alpha = 0.18f),
          ) {
            Text(
              text = activeOrPendingRide.pickupVillage,
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              color = Color(0xFF92400E),
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
    }
  }
}
