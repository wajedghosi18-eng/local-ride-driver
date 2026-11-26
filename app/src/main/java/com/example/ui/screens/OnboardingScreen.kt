package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.ElectricRickshaw
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocalTaxi
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.localization.AppStrings
import com.example.localization.stringsFor
import com.example.model.AppLanguage
import com.example.model.DriverCatalog
import com.example.model.VehicleType
import com.example.util.MediaAndLocationUtils
import com.example.viewmodel.OnboardingUiState
import com.example.viewmodel.OnboardingViewModel
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
  viewModel: OnboardingViewModel,
  state: OnboardingUiState,
  onCompleted: () -> Unit,
) {
  val strings = remember(state.language) { stringsFor(state.language) }

  BackHandler(enabled = state.currentStep > 1) {
    viewModel.previousStep()
  }

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    containerColor = MaterialTheme.colorScheme.background,
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      Column(
        modifier = Modifier
          .widthIn(max = 560.dp)
          .fillMaxSize()
          .padding(horizontal = 20.dp, vertical = 12.dp),
      ) {
        // Top Header + Progress
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          if (state.currentStep > 1) {
            IconButton(
              onClick = { viewModel.previousStep() },
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("onboarding_back_button"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = strings.back,
              )
            }
          } else {
            Spacer(Modifier.size(48.dp))
          }

          Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
          ) {
            Text(
              text = strings.stepLabel(state.currentStep, 8),
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
          }

          Spacer(Modifier.size(48.dp))
        }

        Spacer(Modifier.height(10.dp))

        LinearProgressIndicator(
          progress = { state.currentStep / 8f },
          modifier = Modifier
            .fillMaxWidth()
            .height(6.dp),
        )

        Spacer(Modifier.height(16.dp))

        if (!state.errorMessage.isNullOrBlank()) {
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = state.errorMessage,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onErrorContainer,
              modifier = Modifier.padding(12.dp),
            )
          }
          Spacer(Modifier.height(12.dp))
        }

        when (state.currentStep) {
          1 -> LanguageSelectionStep(
            selectedLanguage = state.language,
            strings = strings,
            onSelectLanguage = { viewModel.selectLanguage(it) },
            onContinue = { viewModel.nextStep() },
          )
          2 -> VehicleSelectionStep(
            selectedVehicle = state.vehicleType,
            strings = strings,
            onSelectVehicle = { viewModel.selectVehicle(it) },
            onContinue = { viewModel.nextStep() },
          )
          3 -> DistrictSelectionStep(
            selectedDistrict = state.district,
            strings = strings,
            onSelectDistrict = { viewModel.selectDistrict(it) },
            onContinue = { viewModel.nextStep() },
          )
          4 -> VillageSelectionStep(
            state = state,
            strings = strings,
            viewModel = viewModel,
            onContinue = { viewModel.nextStep() },
          )
          5 -> GoogleSignInStep(
            strings = strings,
            isCheckingProfile = state.isCheckingExistingProfile,
            onSignedIn = { viewModel.onGoogleSignedIn(onExistingProfileFound = onCompleted) },
            onError = { viewModel.setError(it) },
          )
          6 -> NameAndMobileStep(
            state = state,
            strings = strings,
            onNameChange = { viewModel.updateFullName(it) },
            onMobileChange = { viewModel.updateMobileNumber(it) },
            onContinue = {
              when {
                state.fullName.trim().length < 2 -> viewModel.setError(strings.invalidNameError)
                state.mobileNumber.length < 10 -> viewModel.setError(strings.invalidMobileError)
                else -> viewModel.nextStep()
              }
            },
          )
          7 -> PassportPhotoStep(
            state = state,
            strings = strings,
            onPhotoCaptured = { viewModel.setPassportPhoto(it) },
            onError = { viewModel.setError(it) },
            onContinue = { viewModel.nextStep() },
          )
          8 -> TotoColorAndAadhaarStep(
            state = state,
            strings = strings,
            viewModel = viewModel,
            onComplete = { viewModel.completeRegistration(onCompleted) },
          )
        }
      }
    }
  }
}

@Composable
private fun LanguageSelectionStep(
  selectedLanguage: AppLanguage,
  strings: AppStrings,
  onSelectLanguage: (AppLanguage) -> Unit,
  onContinue: () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        imageVector = Icons.Filled.Translate,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(28.dp),
      )
      Spacer(Modifier.width(10.dp))
      Text(
        text = strings.chooseLanguageTitle,
        style = MaterialTheme.typography.headlineMedium,
      )
    }

    Text(
      text = strings.chooseLanguageSubtitle,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(4.dp))

    AppLanguage.entries.forEach { lang ->
      val isSelected = lang == selectedLanguage
      Surface(
        modifier = Modifier
          .fillMaxWidth()
          .minimumInteractiveComponentSize()
          .testTag("lang_card_${lang.code}")
          .clickable { onSelectLanguage(lang) },
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
          width = if (isSelected) 2.dp else 1.dp,
          color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
        tonalElevation = if (isSelected) 3.dp else 0.dp,
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 20.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column {
            Text(
              text = lang.nativeLabel,
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.Bold,
              color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text = lang.englishLabel,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          if (isSelected) {
            Icon(
              imageVector = Icons.Filled.CheckCircle,
              contentDescription = lang.nativeLabel,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(28.dp),
            )
          }
        }
      }
    }

    Spacer(Modifier.weight(1f, fill = true))

    Button(
      onClick = onContinue,
      modifier = Modifier
        .fillMaxWidth()
        .height(54.dp)
        .testTag("step1_continue_button"),
      shape = RoundedCornerShape(16.dp),
    ) {
      Text(strings.continueBtn, style = MaterialTheme.typography.titleMedium)
    }
  }
}

@Composable
private fun VehicleSelectionStep(
  selectedVehicle: VehicleType,
  strings: AppStrings,
  onSelectVehicle: (VehicleType) -> Unit,
  onContinue: () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Text(text = strings.selectVehicleTitle, style = MaterialTheme.typography.headlineMedium)
    Text(
      text = strings.selectVehicleSubtitle,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    VehicleOptionCard(
      icon = Icons.Filled.ElectricRickshaw,
      title = "🛺 ${strings.vehicleTotoTitle}",
      description = strings.vehicleTotoDesc,
      badge = strings.recommendedBadge,
      selected = selectedVehicle == VehicleType.TOTO,
      testTag = "vehicle_card_toto",
      onClick = { onSelectVehicle(VehicleType.TOTO) },
    )

    VehicleOptionCard(
      icon = Icons.Filled.LocalTaxi,
      title = strings.vehicleAutoTitle,
      description = strings.vehicleAutoDesc,
      badge = null,
      selected = selectedVehicle == VehicleType.AUTO,
      testTag = "vehicle_card_auto",
      onClick = { onSelectVehicle(VehicleType.AUTO) },
    )

    VehicleOptionCard(
      icon = Icons.Filled.DirectionsBike,
      title = strings.vehicleBikeTitle,
      description = strings.vehicleBikeDesc,
      badge = null,
      selected = selectedVehicle == VehicleType.BIKE,
      testTag = "vehicle_card_bike",
      onClick = { onSelectVehicle(VehicleType.BIKE) },
    )

    Spacer(Modifier.weight(1f, fill = true))

    Button(
      onClick = onContinue,
      modifier = Modifier
        .fillMaxWidth()
        .height(54.dp)
        .testTag("step2_continue_button"),
      shape = RoundedCornerShape(16.dp),
    ) {
      Text(strings.next, style = MaterialTheme.typography.titleMedium)
    }
  }
}

@Composable
private fun VehicleOptionCard(
  icon: ImageVector,
  title: String,
  description: String,
  badge: String?,
  selected: Boolean,
  testTag: String,
  onClick: () -> Unit,
) {
  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .minimumInteractiveComponentSize()
      .testTag(testTag)
      .clickable(onClick = onClick),
    shape = RoundedCornerShape(20.dp),
    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
    border = BorderStroke(
      width = if (selected) 2.dp else 1.dp,
      color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
    ),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(18.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
      ) {
        Icon(
          imageVector = icon,
          contentDescription = title,
          tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(12.dp).size(28.dp),
        )
      }
      Spacer(Modifier.width(14.dp))
      Column(modifier = Modifier.weight(1f)) {
        if (badge != null) {
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
          ) {
            Text(
              text = badge,
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
          }
          Spacer(Modifier.height(4.dp))
        }
        Text(
          text = title,
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = description,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (selected) {
        Icon(
          imageVector = Icons.Filled.CheckCircle,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
        )
      }
    }
  }
}

@Composable
private fun DistrictSelectionStep(
  selectedDistrict: String,
  strings: AppStrings,
  onSelectDistrict: (String) -> Unit,
  onContinue: () -> Unit,
) {
  var query by remember { mutableStateOf("") }
  val filtered = remember(query) {
    val q = query.trim()
    if (q.isEmpty()) DriverCatalog.westBengalDistricts
    else DriverCatalog.westBengalDistricts.filter { it.contains(q, ignoreCase = true) }
  }

  Column(modifier = Modifier.fillMaxSize()) {
    Text(text = strings.selectDistrictTitle, style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(4.dp))
    Text(
      text = strings.selectDistrictSubtitle,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))

    OutlinedTextField(
      value = query,
      onValueChange = { query = it },
      leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
      placeholder = { Text(strings.searchDistrictHint) },
      singleLine = true,
      shape = RoundedCornerShape(14.dp),
      modifier = Modifier
        .fillMaxWidth()
        .testTag("district_search_input"),
    )

    Spacer(Modifier.height(12.dp))

    Column(
      modifier = Modifier
        .weight(1f)
        .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      filtered.forEach { district ->
        val isSelected = district == selectedDistrict
        val isPinned = district == "Paschim Medinipur"
        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .testTag("district_item_${district.lowercase().replace(" ", "_")}")
            .clickable { onSelectDistrict(district) },
          shape = RoundedCornerShape(14.dp),
          color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
          border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
          ),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Filled.LocationCity,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Spacer(Modifier.width(12.dp))
              Column {
                Text(
                  text = district,
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = if (isPinned || isSelected) FontWeight.Bold else FontWeight.Medium,
                )
                if (isPinned) {
                  Text(
                    text = strings.pinnedTopLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                  )
                }
              }
            }
            if (isSelected) {
              Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
            }
          }
        }
      }
    }

    Spacer(Modifier.height(12.dp))
    Button(
      onClick = onContinue,
      modifier = Modifier
        .fillMaxWidth()
        .height(54.dp)
        .testTag("step3_continue_button"),
      shape = RoundedCornerShape(16.dp),
    ) {
      Text(strings.next, style = MaterialTheme.typography.titleMedium)
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VillageSelectionStep(
  state: OnboardingUiState,
  strings: AppStrings,
  viewModel: OnboardingViewModel,
  onContinue: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  fun triggerGps() {
    viewModel.setGpsDetecting(true)
    scope.launch {
      val res = MediaAndLocationUtils.fetchCurrentLocationAndVillage(context)
      if (res != null) {
        viewModel.setGpsResult(res.lat, res.lng, res.villageOrTown)
      } else {
        viewModel.setGpsDetecting(false)
        viewModel.setError(strings.gpsPermissionDenied)
      }
    }
  }

  val locationPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions()
  ) { grants ->
    val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
      grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    if (granted) {
      triggerGps()
    } else {
      viewModel.setError(strings.gpsPermissionDenied)
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(text = strings.selectVillageTitle, style = MaterialTheme.typography.headlineMedium)
    Text(
      text = strings.selectVillageSubtitle,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    OutlinedButton(
      onClick = {
        val hasFine = ContextCompat.checkSelfPermission(
          context,
          Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
          context,
          Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (hasFine || hasCoarse) {
          triggerGps()
        } else {
          locationPermissionLauncher.launch(
            arrayOf(
              Manifest.permission.ACCESS_COARSE_LOCATION,
              Manifest.permission.ACCESS_FINE_LOCATION,
            )
          )
        }
      },
      enabled = !state.isDetectingGps,
      modifier = Modifier
        .fillMaxWidth()
        .height(50.dp)
        .testTag("use_gps_button"),
      shape = RoundedCornerShape(14.dp),
    ) {
      if (state.isDetectingGps) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Text(strings.detectingGps)
      } else {
        Icon(imageVector = Icons.Filled.GpsFixed, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("📍 ${strings.useCurrentGps}")
      }
    }

    OutlinedTextField(
      value = state.village,
      onValueChange = { viewModel.updateVillage(it) },
      label = { Text(strings.customVillageHint) },
      singleLine = true,
      shape = RoundedCornerShape(14.dp),
      modifier = Modifier
        .fillMaxWidth()
        .testTag("custom_village_input"),
    )

    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      DriverCatalog.localTownsAndVillages.forEach { place ->
        val selected = state.village.equals(place, ignoreCase = true)
        FilterChip(
          selected = selected,
          onClick = { viewModel.updateVillage(place) },
          label = { Text(place) },
          modifier = Modifier.minimumInteractiveComponentSize(),
        )
      }
    }

    Spacer(Modifier.weight(1f, fill = true))

    Button(
      onClick = onContinue,
      enabled = state.village.isNotBlank(),
      modifier = Modifier
        .fillMaxWidth()
        .height(54.dp)
        .testTag("step4_continue_button"),
      shape = RoundedCornerShape(16.dp),
    ) {
      Text(strings.next, style = MaterialTheme.typography.titleMedium)
    }
  }
}
