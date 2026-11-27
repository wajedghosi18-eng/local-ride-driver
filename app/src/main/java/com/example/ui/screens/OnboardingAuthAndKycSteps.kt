package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import android.app.Activity
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.R
import com.example.auth.GoogleAuthHelper
import com.example.localization.AppStrings
import com.example.model.DriverCatalog
import com.example.ui.components.Base64DocumentImage
import com.example.ui.components.DriverAvatar
import com.example.ui.components.TotoColorDot
import com.example.ui.theme.OnlineGreen
import com.example.util.MediaAndLocationUtils
import com.example.viewmodel.OnboardingUiState
import com.example.viewmodel.OnboardingViewModel
import kotlinx.coroutines.launch

@Composable
fun GoogleSignInStep(
  strings: AppStrings,
  isCheckingProfile: Boolean,
  onSignedIn: (com.example.auth.GoogleSignInAccountResult) -> Unit,
  onError: (String?) -> Unit,
) {
  val context = LocalContext.current
  var isLaunchingPicker by remember { mutableStateOf(false) }
  var manualGmail by remember { mutableStateOf("") }

  val accountPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult()
  ) { activityResult ->
    isLaunchingPicker = false
    GoogleAuthHelper.handleAccountPickerResult(
      context = context,
      resultCode = activityResult.resultCode,
      data = activityResult.data,
      onSuccess = { accountResult ->
        onError(null)
        onSignedIn(accountResult)
      },
      onNoAccountSelected = { msg ->
        onError(msg)
      },
    )
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Image(
      painter = painterResource(id = R.drawable.img_driver_hero_banner),
      contentDescription = strings.appTitle,
      contentScale = ContentScale.Crop,
      modifier = Modifier
        .fillMaxWidth()
        .height(175.dp)
        .clip(RoundedCornerShape(22.dp)),
    )

    Text(text = strings.googleSignInTitle, style = MaterialTheme.typography.headlineMedium)
    Text(
      text = strings.googleSignInSubtitle,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Surface(
      shape = RoundedCornerShape(16.dp),
      color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        modifier = Modifier.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          imageVector = Icons.Filled.VerifiedUser,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(10.dp))
        Text(
          text = strings.noOtpNeededNote,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
      }
    }

    Button(
      onClick = {
        isLaunchingPicker = true
        onError(null)
        try {
          accountPickerLauncher.launch(GoogleAuthHelper.buildAccountPickerIntent())
        } catch (e: Exception) {
          isLaunchingPicker = false
          val msg = "Unable to open Google account picker: ${e.localizedMessage ?: e.message ?: "Unknown error"}"
          Toast.makeText(context.applicationContext, msg, Toast.LENGTH_LONG).show()
          onError(msg)
        }
      },
      enabled = !isLaunchingPicker && !isCheckingProfile,
      modifier = Modifier
        .fillMaxWidth()
        .height(56.dp)
        .testTag("google_sign_in_button"),
      shape = RoundedCornerShape(16.dp),
    ) {
      if (isLaunchingPicker || isCheckingProfile) {
        CircularProgressIndicator(
          modifier = Modifier.size(22.dp),
          color = MaterialTheme.colorScheme.onPrimary,
          strokeWidth = 2.dp,
        )
        Spacer(Modifier.width(10.dp))
        Text(if (isCheckingProfile) strings.checkingProfile else strings.loading)
      } else {
        Text(strings.signInWithGoogle, style = MaterialTheme.typography.titleMedium)
      }
    }

    OutlinedTextField(
      value = manualGmail,
      onValueChange = {
        manualGmail = it
        onError(null)
      },
      label = { Text(strings.orEnterGmailLabel) },
      placeholder = { Text(strings.gmailHint) },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
      singleLine = true,
      shape = RoundedCornerShape(14.dp),
      modifier = Modifier
        .fillMaxWidth()
        .testTag("manual_gmail_input"),
    )

    if (manualGmail.isNotBlank()) {
      OutlinedButton(
        onClick = {
          val trimmed = manualGmail.trim()
          if (!GoogleAuthHelper.isValidEmail(trimmed)) {
            onError(strings.invalidEmailError)
          } else {
            onError(null)
            onSignedIn(GoogleAuthHelper.buildResultForEmail(context, trimmed))
          }
        },
        enabled = !isLaunchingPicker && !isCheckingProfile,
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("manual_gmail_continue_button"),
        shape = RoundedCornerShape(14.dp),
      ) {
        Text(strings.continueWithEmailBtn, style = MaterialTheme.typography.titleMedium)
      }
    }
  }
}

@Composable
fun NameAndMobileStep(
  state: OnboardingUiState,
  strings: AppStrings,
  onNameChange: (String) -> Unit,
  onMobileChange: (String) -> Unit,
  onContinue: () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Text(text = strings.profileDetailsTitle, style = MaterialTheme.typography.headlineMedium)
    Text(
      text = strings.profileDetailsSubtitle,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    OutlinedTextField(
      value = state.fullName,
      onValueChange = onNameChange,
      label = { Text(strings.fullNameLabel) },
      singleLine = true,
      shape = RoundedCornerShape(14.dp),
      modifier = Modifier
        .fillMaxWidth()
        .testTag("full_name_input"),
    )

    OutlinedTextField(
      value = state.mobileNumber,
      onValueChange = onMobileChange,
      label = { Text(strings.mobileNumberLabel) },
      placeholder = { Text(strings.mobileHint) },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
      singleLine = true,
      shape = RoundedCornerShape(14.dp),
      modifier = Modifier
        .fillMaxWidth()
        .testTag("mobile_number_input"),
    )

    Spacer(Modifier.weight(1f, fill = true))

    Button(
      onClick = onContinue,
      modifier = Modifier
        .fillMaxWidth()
        .height(54.dp)
        .testTag("step6_continue_button"),
      shape = RoundedCornerShape(16.dp),
    ) {
      Text(strings.next, style = MaterialTheme.typography.titleMedium)
    }
  }
}

@Composable
fun PassportPhotoStep(
  state: OnboardingUiState,
  strings: AppStrings,
  onPhotoCaptured: (String) -> Unit,
  onError: (String) -> Unit,
  onContinue: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

  fun handleSelectedUri(uri: Uri?) {
    if (uri == null) return
    scope.launch {
      val b64 = MediaAndLocationUtils.compressUriToBase64(context, uri, maxDimension = 360, jpegQuality = 72)
      if (b64 != null) onPhotoCaptured(b64)
    }
  }

  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri -> handleSelectedUri(uri) }

  val takePictureLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.TakePicture()
  ) { success ->
    if (success) handleSelectedUri(pendingCameraUri)
  }

  val cameraPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { granted ->
    if (granted) {
      val uri = MediaAndLocationUtils.createTempCameraUri(context, "passport")
      pendingCameraUri = uri
      takePictureLauncher.launch(uri)
    } else {
      onError(strings.cameraPermissionDenied)
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Text(text = strings.passportStepTitle, style = MaterialTheme.typography.headlineMedium)
    Text(
      text = strings.passportStepSubtitle,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    // Visual Tutorial / Demo Card
    Surface(
      shape = RoundedCornerShape(20.dp),
      color = MaterialTheme.colorScheme.surface,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
      tonalElevation = 2.dp,
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Text(
          text = strings.passportGuideHeader,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(10.dp))
        Image(
          painter = painterResource(id = R.drawable.img_passport_guide_demo),
          contentDescription = strings.passportGuideHeader,
          contentScale = ContentScale.Crop,
          modifier = Modifier
            .fillMaxWidth()
            .height(155.dp)
            .clip(RoundedCornerShape(14.dp)),
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = OnlineGreen, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text(text = strings.passportGoodTip, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Filled.Cancel, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text(text = strings.passportBadTip, style = MaterialTheme.typography.bodyMedium)
        }
      }
    }

    // Selected Photo Preview
    if (state.passportPhotoBase64.isNotBlank()) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
      ) {
        DriverAvatar(
          base64Photo = state.passportPhotoBase64,
          contentDescription = strings.photoReadyBadge,
          size = 86.dp,
        )
        Spacer(Modifier.width(12.dp))
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.primaryContainer,
        ) {
          Text(
            text = "✅ ${strings.photoReadyBadge}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
          )
        }
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Button(
        onClick = {
          val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
          ) == PackageManager.PERMISSION_GRANTED
          if (granted) {
            val uri = MediaAndLocationUtils.createTempCameraUri(context, "passport")
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
          } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
          }
        },
        modifier = Modifier
          .weight(1f)
          .height(52.dp)
          .testTag("take_selfie_button"),
        shape = RoundedCornerShape(14.dp),
      ) {
        Icon(Icons.Filled.CameraAlt, contentDescription = null)
        Spacer(Modifier.width(6.dp))
        Text(strings.takeSelfieBtn, maxLines = 1)
      }

      OutlinedButton(
        onClick = {
          photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
          )
        },
        modifier = Modifier
          .weight(1f)
          .height(52.dp)
          .testTag("upload_passport_button"),
        shape = RoundedCornerShape(14.dp),
      ) {
        Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
        Spacer(Modifier.width(6.dp))
        Text(strings.uploadGalleryBtn, maxLines = 1)
      }
    }

    Spacer(Modifier.weight(1f, fill = true))

    Button(
      onClick = onContinue,
      modifier = Modifier
        .fillMaxWidth()
        .height(54.dp)
        .testTag("step7_continue_button"),
      shape = RoundedCornerShape(16.dp),
    ) {
      Text(strings.next, style = MaterialTheme.typography.titleMedium)
    }
  }
}

@Composable
fun TotoColorAndAadhaarStep(
  state: OnboardingUiState,
  strings: AppStrings,
  viewModel: OnboardingViewModel,
  onComplete: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var pickingFront by remember { mutableStateOf(true) }

  val aadhaarPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri ->
    if (uri != null) {
      scope.launch {
        val b64 = MediaAndLocationUtils.compressUriToBase64(context, uri, maxDimension = 480, jpegQuality = 68)
        if (b64 != null) {
          if (pickingFront) viewModel.setAadhaarFront(b64) else viewModel.setAadhaarBack(b64)
        }
      }
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Text(text = strings.vehicleAndKycTitle, style = MaterialTheme.typography.headlineMedium)
    Text(
      text = strings.vehicleAndKycSubtitle,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Text(
      text = strings.selectTotoColor,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      DriverCatalog.totoColors.forEach { colorOption ->
        val isSelected = colorOption.id == state.selectedColor.id
        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .testTag("toto_color_${colorOption.id.lowercase().replace(" ", "_")}")
            .clickable { viewModel.selectTotoColor(colorOption) },
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
              .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              TotoColorDot(
                hex = colorOption.primaryHex,
                secondaryHex = colorOption.secondaryHex,
                size = 24.dp,
              )
              Spacer(Modifier.width(12.dp))
              Text(
                text = strings.localizedColor(colorOption.displayName, state.language),
                style = MaterialTheme.typography.titleMedium,
              )
            }
            if (isSelected) {
              Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
          }
        }
      }
    }

    OutlinedTextField(
      value = state.vehicleNumber,
      onValueChange = { viewModel.updateVehicleNumber(it) },
      label = { Text(strings.vehicleNumberLabel) },
      placeholder = { Text(strings.vehicleNumberHint) },
      singleLine = true,
      shape = RoundedCornerShape(14.dp),
      modifier = Modifier.fillMaxWidth(),
    )

    OutlinedTextField(
      value = state.emergencyContact,
      onValueChange = { viewModel.updateEmergencyContact(it) },
      label = { Text(strings.emergencyContactLabel) },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
      singleLine = true,
      shape = RoundedCornerShape(14.dp),
      modifier = Modifier.fillMaxWidth(),
    )

    Text(
      text = strings.aadhaarSectionTitle,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
    )

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      AadhaarUploadCard(
        title = "🪪 ${strings.uploadAadhaarFront}",
        uploadedLabel = strings.aadhaarUploaded,
        base64 = state.aadhaarFrontBase64,
        testTag = "upload_aadhaar_front_card",
        onClick = {
          pickingFront = true
          aadhaarPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
          )
        },
        modifier = Modifier.weight(1f),
      )

      AadhaarUploadCard(
        title = "🪪 ${strings.uploadAadhaarBack}",
        uploadedLabel = strings.aadhaarUploaded,
        base64 = state.aadhaarBackBase64,
        testTag = "upload_aadhaar_back_card",
        onClick = {
          pickingFront = false
          aadhaarPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
          )
        },
        modifier = Modifier.weight(1f),
      )
    }

    Spacer(Modifier.height(6.dp))

    Button(
      onClick = onComplete,
      enabled = !state.isSubmitting,
      modifier = Modifier
        .fillMaxWidth()
        .height(56.dp)
        .testTag("complete_registration_button"),
      shape = RoundedCornerShape(16.dp),
    ) {
      if (state.isSubmitting) {
        CircularProgressIndicator(
          modifier = Modifier.size(22.dp),
          color = Color.White,
          strokeWidth = 2.dp,
        )
        Spacer(Modifier.width(10.dp))
        Text(strings.savingProfile)
      } else {
        Text(strings.completeRegistration, style = MaterialTheme.typography.titleMedium)
      }
    }
    Spacer(Modifier.height(12.dp))
  }
}

@Composable
private fun AadhaarUploadCard(
  title: String,
  uploadedLabel: String,
  base64: String,
  testTag: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val hasDoc = base64.isNotBlank()
  Surface(
    modifier = modifier
      .height(124.dp)
      .minimumInteractiveComponentSize()
      .testTag(testTag)
      .clickable(onClick = onClick),
    shape = RoundedCornerShape(16.dp),
    color = if (hasDoc) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface,
    border = BorderStroke(
      width = if (hasDoc) 2.dp else 1.dp,
      color = if (hasDoc) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
    ),
  ) {
    if (hasDoc) {
      Box(modifier = Modifier.fillMaxSize()) {
        Base64DocumentImage(
          base64 = base64,
          contentDescription = title,
          modifier = Modifier.fillMaxSize(),
        )
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(6.dp),
        ) {
          Text(
            text = "✅ $uploadedLabel",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
          )
        }
      }
    } else {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
      ) {
        Icon(
          imageVector = Icons.Filled.Badge,
          contentDescription = title,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(32.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
          text = title,
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }
    }
  }
}
