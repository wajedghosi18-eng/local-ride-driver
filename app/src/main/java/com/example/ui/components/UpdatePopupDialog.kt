package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.localization.AppStrings
import com.example.model.DriverAppVersion

@Composable
fun UpdatePopupDialog(
  versionInfo: DriverAppVersion,
  strings: AppStrings,
  onUpdateNow: (String) -> Unit,
  onDismissFor24Hours: () -> Unit,
) {
  Dialog(
    onDismissRequest = {
      if (!versionInfo.forceUpdate) onDismissFor24Hours()
    },
    properties = DialogProperties(
      dismissOnBackPress = !versionInfo.forceUpdate,
      dismissOnClickOutside = !versionInfo.forceUpdate,
    ),
  ) {
    Surface(
      shape = RoundedCornerShape(24.dp),
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 6.dp,
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(20.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f),
          ) {
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = MaterialTheme.colorScheme.primaryContainer,
            ) {
              Icon(
                imageVector = Icons.Filled.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(10.dp).size(24.dp),
              )
            }
            Text(
              text = if (versionInfo.forceUpdate) strings.updateForceTitle else strings.updateAvailableTitle,
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
            )
          }

          if (!versionInfo.forceUpdate) {
            IconButton(
              onClick = onDismissFor24Hours,
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("update_popup_close_button"),
            ) {
              Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = strings.later,
              )
            }
          }
        }

        Spacer(Modifier.height(12.dp))

        if (versionInfo.latestVersionName.isNotBlank()) {
          Text(
            text = "v${versionInfo.latestVersionName} (Build ${versionInfo.latestVersionCode})",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
          )
          Spacer(Modifier.height(6.dp))
        }

        Text(
          text = versionInfo.releaseNotes.ifBlank { strings.defaultReleaseNotes },
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(20.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          if (!versionInfo.forceUpdate) {
            OutlinedButton(
              onClick = onDismissFor24Hours,
              modifier = Modifier
                .weight(1f)
                .minimumInteractiveComponentSize()
                .testTag("update_popup_later_button"),
            ) {
              Text(strings.later)
            }
          }
          Button(
            onClick = { onUpdateNow(versionInfo.apkDownloadUrl) },
            modifier = Modifier
              .weight(1f)
              .minimumInteractiveComponentSize()
              .testTag("update_popup_now_button"),
          ) {
            Text(strings.updateNow)
          }
        }
      }
    }
  }
}
