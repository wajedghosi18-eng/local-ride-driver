package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.window.Dialog
import com.example.localization.AppStrings
import com.example.model.AppLanguage
import com.example.model.DriverCatalog
import com.example.model.TotoDriver
import com.example.model.VehicleType
import com.example.ui.components.Base64DocumentImage
import com.example.ui.components.DriverAvatar
import com.example.ui.components.TotoColorDot
import com.example.ui.theme.OfflineRed
import com.example.util.MediaAndLocationUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileTab(
  driver: TotoDriver,
  language: AppLanguage,
  strings: AppStrings,
  isBusy: Boolean,
  onLanguageChange: (AppLanguage) -> Unit,
  onSaveProfile: (Map<String, Any>, () -> Unit) -> Unit,
  onSignOut: () -> Unit,
  onDeleteProfile: () -> Unit,
) {
  var showEditDialog by remember { mutableStateOf(false) }
  var showDeleteConfirm by remember { mutableStateOf(false) }

  Column(
    modifier = Modifier
      .widthIn(max = 600.dp)
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Text(
      text = strings.profileTitle,
      style = MaterialTheme.typography.headlineMedium,
      fontWeight = FontWeight.Bold,
    )

    // Profile Header Card
    Surface(
      shape = RoundedCornerShape(22.dp),
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 2.dp,
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        DriverAvatar(
          base64Photo = driver.passportPhotoBase64,
          contentDescription = driver.fullName,
          size = 72.dp,
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = driver.fullName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
          )
          if (driver.email.isNotBlank()) {
            Text(
              text = driver.email,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Spacer(Modifier.height(6.dp))
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
          ) {
            Text(
              text = strings.statusPendingOrActive,
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
          }
        }
      }
    }

    // Driver Details Card
    Surface(
      shape = RoundedCornerShape(20.dp),
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 2.dp,
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        ProfileInfoRow(strings.vehicleTypeLabel, strings.vehicleName(VehicleType.fromId(driver.vehicleType)))
        ProfileInfoRow(strings.districtLabel, driver.district)
        ProfileInfoRow(strings.villageLabel, driver.village)
        ProfileInfoRow(strings.mobileLabel, driver.mobileNumber)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = strings.totoColorLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Row(verticalAlignment = Alignment.CenterVertically) {
            TotoColorDot(hex = driver.totoColorHex, size = 14.dp)
            Spacer(Modifier.width(6.dp))
            Text(
              text = strings.localizedColor(driver.totoColor, language),
              style = MaterialTheme.typography.bodyLarge,
              fontWeight = FontWeight.SemiBold,
            )
          }
        }
        if (driver.vehicleNumber.isNotBlank()) {
          ProfileInfoRow(strings.vehicleNumberLabel, driver.vehicleNumber)
        }
        if (driver.emergencyContact.isNotBlank()) {
          ProfileInfoRow(strings.emergencyLabel, driver.emergencyContact)
        }
      }
    }

    // Aadhaar Documents Preview (if uploaded)
    if (driver.aadhaarFrontBase64.isNotBlank() || driver.aadhaarBackBase64.isNotBlank()) {
      Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(
            text = strings.aadhaarDocsLabel,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          Spacer(Modifier.height(10.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            if (driver.aadhaarFrontBase64.isNotBlank()) {
              Base64DocumentImage(
                base64 = driver.aadhaarFrontBase64,
                contentDescription = strings.uploadAadhaarFront,
                modifier = Modifier
                  .weight(1f)
                  .height(92.dp)
                  .clip(RoundedCornerShape(12.dp)),
              )
            }
            if (driver.aadhaarBackBase64.isNotBlank()) {
              Base64DocumentImage(
                base64 = driver.aadhaarBackBase64,
                contentDescription = strings.uploadAadhaarBack,
                modifier = Modifier
                  .weight(1f)
                  .height(92.dp)
                  .clip(RoundedCornerShape(12.dp)),
              )
            }
          }
        }
      }
    }

    // Language Switcher
    Surface(
      shape = RoundedCornerShape(20.dp),
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 2.dp,
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Text(
          text = strings.languageLabel,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          AppLanguage.entries.forEach { lang ->
            FilterChip(
              selected = lang == language,
              onClick = { onLanguageChange(lang) },
              label = { Text(lang.nativeLabel) },
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("profile_lang_${lang.code}"),
            )
          }
        }
      }
    }

    // Edit Profile, Sign Out, Delete Profile (Strictly inside Profile Tab)
    Button(
      onClick = { showEditDialog = true },
      enabled = !isBusy,
      modifier = Modifier
        .fillMaxWidth()
        .height(52.dp)
        .testTag("edit_profile_button"),
      shape = RoundedCornerShape(14.dp),
    ) {
      Icon(Icons.Filled.Edit, contentDescription = null)
      Spacer(Modifier.width(8.dp))
      Text(strings.editProfile, style = MaterialTheme.typography.titleMedium)
    }

    OutlinedButton(
      onClick = onSignOut,
      enabled = !isBusy,
      modifier = Modifier
        .fillMaxWidth()
        .height(50.dp)
        .testTag("sign_out_button"),
      shape = RoundedCornerShape(14.dp),
    ) {
      Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
      Spacer(Modifier.width(8.dp))
      Text(strings.signOut)
    }

    OutlinedButton(
      onClick = { showDeleteConfirm = true },
      enabled = !isBusy,
      border = BorderStroke(1.dp, OfflineRed),
      colors = ButtonDefaults.outlinedButtonColors(contentColor = OfflineRed),
      modifier = Modifier
        .fillMaxWidth()
        .height(50.dp)
        .testTag("delete_profile_button"),
      shape = RoundedCornerShape(14.dp),
    ) {
      Icon(Icons.Filled.DeleteForever, contentDescription = null)
      Spacer(Modifier.width(8.dp))
      Text(strings.deleteProfile)
    }
    Spacer(Modifier.height(12.dp))
  }

  if (showEditDialog) {
    EditProfileDialog(
      driver = driver,
      language = language,
      strings = strings,
      isBusy = isBusy,
      onDismiss = { showEditDialog = false },
      onSave = { updates ->
        onSaveProfile(updates) { showEditDialog = false }
      },
    )
  }

  if (showDeleteConfirm) {
    AlertDialog(
      onDismissRequest = { showDeleteConfirm = false },
      title = { Text(strings.deleteProfileTitle) },
      text = { Text(strings.deleteProfileConfirmMsg) },
      confirmButton = {
        Button(
          onClick = {
            showDeleteConfirm = false
            onDeleteProfile()
          },
          colors = ButtonDefaults.buttonColors(containerColor = OfflineRed, contentColor = Color.White),
          modifier = Modifier.testTag("confirm_delete_profile_button"),
        ) {
          Text(strings.confirmDelete)
        }
      },
      dismissButton = {
        TextButton(onClick = { showDeleteConfirm = false }) {
          Text(strings.cancel)
        }
      },
    )
  }
}

@Composable
private fun ProfileInfoRow(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.width(12.dp))
    Text(
      text = value,
      style = MaterialTheme.typography.bodyLarge,
      fontWeight = FontWeight.SemiBold,
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditProfileDialog(
  driver: TotoDriver,
  language: AppLanguage,
  strings: AppStrings,
  isBusy: Boolean,
  onDismiss: () -> Unit,
  onSave: (Map<String, Any>) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var fullName by remember { mutableStateOf(driver.fullName) }
  var mobileNumber by remember { mutableStateOf(driver.mobileNumber) }
  var district by remember { mutableStateOf(driver.district) }
  var village by remember { mutableStateOf(driver.village) }
  var vehicleNumber by remember { mutableStateOf(driver.vehicleNumber) }
  var emergencyContact by remember { mutableStateOf(driver.emergencyContact) }
  var selectedColor by remember {
    mutableStateOf(
      DriverCatalog.totoColors.firstOrNull { it.displayName == driver.totoColor }
        ?: DriverCatalog.totoColors.first()
    )
  }
  var passportBase64 by remember { mutableStateOf(driver.passportPhotoBase64) }
  var districtMenuExpanded by remember { mutableStateOf(false) }

  val photoPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri ->
    if (uri != null) {
      scope.launch {
        MediaAndLocationUtils.compressUriToBase64(context, uri, maxDimension = 360, jpegQuality = 72)
          ?.let { passportBase64 = it }
      }
    }
  }

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      shape = RoundedCornerShape(24.dp),
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 6.dp,
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          text = strings.editProfile,
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
        )

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          DriverAvatar(
            base64Photo = passportBase64,
            contentDescription = fullName,
            size = 56.dp,
          )
          OutlinedButton(
            onClick = {
              photoPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
              )
            },
            modifier = Modifier.minimumInteractiveComponentSize(),
          ) {
            Text(strings.uploadGalleryBtn)
          }
        }

        OutlinedTextField(
          value = fullName,
          onValueChange = { fullName = it },
          label = { Text(strings.fullNameLabel) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
          value = mobileNumber,
          onValueChange = { mobileNumber = it.filter { c -> c.isDigit() }.take(10) },
          label = { Text(strings.mobileNumberLabel) },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )

        ExposedDropdownMenuBox(
          expanded = districtMenuExpanded,
          onExpandedChange = { districtMenuExpanded = !districtMenuExpanded },
        ) {
          OutlinedTextField(
            value = district,
            onValueChange = {},
            readOnly = true,
            label = { Text(strings.districtLabel) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = districtMenuExpanded) },
            modifier = Modifier
              .menuAnchor(MenuAnchorType.PrimaryNotEditable)
              .fillMaxWidth(),
          )
          ExposedDropdownMenu(
            expanded = districtMenuExpanded,
            onDismissRequest = { districtMenuExpanded = false },
          ) {
            DriverCatalog.westBengalDistricts.forEach { d ->
              DropdownMenuItem(
                text = { Text(d) },
                onClick = {
                  district = d
                  districtMenuExpanded = false
                },
              )
            }
          }
        }

        OutlinedTextField(
          value = village,
          onValueChange = { village = it },
          label = { Text(strings.villageLabel) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
          value = vehicleNumber,
          onValueChange = { vehicleNumber = it.uppercase() },
          label = { Text(strings.vehicleNumberLabel) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
          value = emergencyContact,
          onValueChange = { emergencyContact = it.filter { c -> c.isDigit() }.take(12) },
          label = { Text(strings.emergencyContactLabel) },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )

        Text(text = strings.selectTotoColor, style = MaterialTheme.typography.labelLarge)
        DriverCatalog.totoColors.forEach { option ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { selectedColor = option }
              .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            TotoColorDot(hex = option.primaryHex, secondaryHex = option.secondaryHex, size = 18.dp)
            Spacer(Modifier.width(10.dp))
            Text(
              text = strings.localizedColor(option.displayName, language),
              fontWeight = if (option == selectedColor) FontWeight.Bold else FontWeight.Normal,
              color = if (option == selectedColor) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.weight(1f).minimumInteractiveComponentSize(),
          ) {
            Text(strings.cancel)
          }
          Button(
            onClick = {
              if (fullName.trim().length >= 2 && mobileNumber.length >= 10 && village.trim().isNotEmpty()) {
                val updates = mutableMapOf<String, Any>(
                  "fullName" to fullName.trim(),
                  "mobileNumber" to mobileNumber.trim(),
                  "district" to district,
                  "village" to village.trim(),
                  "totoColor" to selectedColor.displayName,
                  "totoColorHex" to selectedColor.primaryHex,
                  "vehicleNumber" to vehicleNumber.trim(),
                  "emergencyContact" to emergencyContact.trim(),
                )
                if (passportBase64.isNotBlank()) {
                  updates["passportPhotoBase64"] = passportBase64
                }
                onSave(updates)
              }
            },
            enabled = !isBusy,
            modifier = Modifier
              .weight(1f)
              .minimumInteractiveComponentSize()
              .testTag("save_profile_edits_button"),
          ) {
            Text(strings.save)
          }
        }
      }
    }
  }
}
