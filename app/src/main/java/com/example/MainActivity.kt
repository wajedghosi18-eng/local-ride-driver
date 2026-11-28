package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.GoogleAuthHelper
import com.example.data.DriverPrefs
import com.example.data.DriverRepository
import com.example.localization.stringsFor
import com.example.model.UiState
import com.example.ui.components.UpdatePopupDialog
import com.example.ui.screens.HomeTab
import com.example.ui.screens.MyRidesTab
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.ProfileTab
import com.example.ui.theme.LocalRideDriverTheme
import com.example.viewmodel.DriverMainViewModel
import com.example.viewmodel.OnboardingViewModel
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    installGlobalExceptionHandler()
    super.onCreate(savedInstanceState)

    try {
      if (FirebaseApp.getApps(this).isEmpty()) {
        runCatching { FirebaseApp.initializeApp(this) }
      }
      if (FirebaseApp.getApps(this).isEmpty()) {
        GoogleAuthHelper.ensureFirebaseInitialized(this)
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }

    try {
      enableEdgeToEdge()
    } catch (e: Exception) {
      e.printStackTrace()
    }

    setContent {
      LocalRideDriverTheme {
        LocalRideDriverApp()
      }
    }
  }

  private fun installGlobalExceptionHandler() {
    val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
      try {
        Log.e("LocalRideDriver", "Uncaught exception on thread ${thread.name}", throwable)
        throwable.printStackTrace()
      } catch (_: Throwable) {
      }
      if (thread.id != Looper.getMainLooper().thread.id) {
        return@setDefaultUncaughtExceptionHandler
      }
      previousHandler?.uncaughtException(thread, throwable)
    }
  }
}

@Composable
fun LocalRideDriverApp() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val prefs = remember(context) { DriverPrefs(context) }
  val firebaseAuth = remember(context) { GoogleAuthHelper.safeAuth(context) }
  var activeDriverId by remember {
    mutableStateOf(
      GoogleAuthHelper.safeCurrentUser(context)?.uid
        ?: if (prefs.isOnboardingComplete()) prefs.getSavedUserId() else null
    )
  }
  var forceOnboarding by remember { mutableStateOf(false) }

  DisposableEffect(firebaseAuth) {
    val listener = FirebaseAuth.AuthStateListener { auth ->
      val fbUid = try {
        auth.currentUser?.uid
      } catch (e: Exception) {
        e.printStackTrace()
        null
      }
      if (!fbUid.isNullOrBlank()) {
        activeDriverId = fbUid
      }
    }
    try {
      firebaseAuth?.addAuthStateListener(listener)
    } catch (e: Exception) {
      e.printStackTrace()
    }
    onDispose {
      try {
        firebaseAuth?.removeAuthStateListener(listener)
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }
  }

  val driverId = activeDriverId
  if (driverId.isNullOrBlank() || forceOnboarding) {
    val onboardingViewModel: OnboardingViewModel = viewModel(
      key = driverId ?: "guest",
      factory = viewModelFactory {
        initializer {
          val app = checkNotNull(this[APPLICATION_KEY])
          OnboardingViewModel(
            repository = DriverRepository.createSafe(app, driverId),
            prefs = DriverPrefs(app),
          )
        }
      },
    )
    val onboardingState by onboardingViewModel.state.collectAsStateWithLifecycle()
    OnboardingScreen(
      viewModel = onboardingViewModel,
      state = onboardingState,
      onCompleted = { resolvedDriverId ->
        forceOnboarding = false
        activeDriverId = resolvedDriverId
      },
    )
  } else {
    key(driverId) {
      AuthenticatedDriverRoot(
        currentUserId = driverId,
        prefs = prefs,
        onRequireRegistration = { forceOnboarding = true },
        onSignedOut = {
          prefs.clearSession()
          forceOnboarding = false
          activeDriverId = null
        },
      )
    }
  }
}

@Composable
private fun AuthenticatedDriverRoot(
  currentUserId: String,
  prefs: DriverPrefs,
  onRequireRegistration: () -> Unit,
  onSignedOut: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  val viewModel: DriverMainViewModel = viewModel(
    key = currentUserId,
    factory = viewModelFactory {
      initializer {
        val app = checkNotNull(this[APPLICATION_KEY])
        DriverMainViewModel(
          repository = DriverRepository.createSafe(app, currentUserId),
          prefs = DriverPrefs(app),
          currentUserId = currentUserId,
        )
      }
    },
  )

  val driverProfileState by viewModel.driverProfileState.collectAsStateWithLifecycle()
  val pendingRidesState by viewModel.pendingRidesState.collectAsStateWithLifecycle()
  val myRidesState by viewModel.myRidesState.collectAsStateWithLifecycle()
  val language by viewModel.language.collectAsStateWithLifecycle()
  val updateVersion by viewModel.updateVersionToShow.collectAsStateWithLifecycle()
  val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(Unit) {
    viewModel.feedback.collect { message ->
      snackbarHostState.showSnackbar(message)
    }
  }

  val strings = remember(language) { stringsFor(language) }

  when (val profileUi = driverProfileState) {
    is UiState.Loading -> {
      Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(contentAlignment = Alignment.Center) {
          CircularProgressIndicator()
        }
      }
    }
    is UiState.Error -> {
      Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
          contentAlignment = Alignment.Center,
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Text(
              text = profileUi.message,
              color = MaterialTheme.colorScheme.error,
              style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(4.dp))
            Button(
              onClick = {
                GoogleAuthHelper.signOut(scope, context = context) {
                  onSignedOut()
                }
              },
            ) {
              Text(strings.signOut)
            }
          }
        }
      }
    }
    is UiState.Success -> {
      val driver = profileUi.data
      if (driver == null) {
        LaunchedEffect(Unit) {
          prefs.setOnboardingComplete(false)
          onRequireRegistration()
        }
      } else {
        var selectedTab by remember { mutableIntStateOf(0) }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
          val useRail = maxWidth >= 600.dp

          Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
              if (!useRail) {
                NavigationBar {
                  NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                      Icon(
                        if (selectedTab == 0) Icons.Filled.Home else Icons.Outlined.Home,
                        contentDescription = strings.navHome,
                      )
                    },
                    label = { Text(strings.navHome) },
                    modifier = Modifier.testTag("nav_home_tab"),
                  )
                  NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                      Icon(
                        if (selectedTab == 1) Icons.Filled.DirectionsCar else Icons.Outlined.DirectionsCar,
                        contentDescription = strings.navMyRides,
                      )
                    },
                    label = { Text(strings.navMyRides) },
                    modifier = Modifier.testTag("nav_my_rides_tab"),
                  )
                  NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = {
                      Icon(
                        if (selectedTab == 2) Icons.Filled.Person else Icons.Outlined.Person,
                        contentDescription = strings.navProfile,
                      )
                    },
                    label = { Text(strings.navProfile) },
                    modifier = Modifier.testTag("nav_profile_tab"),
                  )
                }
              }
            },
          ) { innerPadding ->
            Row(
              modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            ) {
              if (useRail) {
                NavigationRail {
                  NavigationRailItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                      Icon(
                        if (selectedTab == 0) Icons.Filled.Home else Icons.Outlined.Home,
                        contentDescription = strings.navHome,
                      )
                    },
                    label = { Text(strings.navHome) },
                  )
                  NavigationRailItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                      Icon(
                        if (selectedTab == 1) Icons.Filled.DirectionsCar else Icons.Outlined.DirectionsCar,
                        contentDescription = strings.navMyRides,
                      )
                    },
                    label = { Text(strings.navMyRides) },
                  )
                  NavigationRailItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = {
                      Icon(
                        if (selectedTab == 2) Icons.Filled.Person else Icons.Outlined.Person,
                        contentDescription = strings.navProfile,
                      )
                    },
                    label = { Text(strings.navProfile) },
                  )
                }
              }

              Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter,
              ) {
                when (selectedTab) {
                  0 -> HomeTab(
                    driver = driver,
                    language = language,
                    strings = strings,
                    pendingRidesState = pendingRidesState,
                    myRidesState = myRidesState,
                    isBusy = isBusy,
                    onToggleOnline = { online, lat, lng ->
                      viewModel.toggleOnline(online, lat, lng)
                    },
                    onAcceptRide = { ride ->
                      viewModel.acceptRide(ride, driver)
                    },
                    onStartRideWithOtp = { ride ->
                      viewModel.startRideWithOtp(ride)
                    },
                    onRejectRide = { ride ->
                      viewModel.rejectRide(ride)
                    },
                    onCompleteRide = { ride ->
                      viewModel.updateRideStatus(ride, "completed")
                    },
                  )
                  1 -> MyRidesTab(
                    myRidesState = myRidesState,
                    strings = strings,
                    isBusy = isBusy,
                    onCompleteRide = { ride ->
                      viewModel.updateRideStatus(ride, "completed")
                    },
                    onCancelRide = { ride ->
                      viewModel.updateRideStatus(ride, "cancelled")
                    },
                  )
                  2 -> ProfileTab(
                    driver = driver,
                    language = language,
                    strings = strings,
                    isBusy = isBusy,
                    onLanguageChange = { viewModel.changeLanguage(it) },
                    onSaveProfile = { updates, onDone ->
                      viewModel.saveEditedProfile(updates, onDone)
                    },
                    onSignOut = {
                      GoogleAuthHelper.signOut(scope, context = context) {
                        onSignedOut()
                      }
                    },
                    onDeleteProfile = {
                      viewModel.deleteProfile {
                        GoogleAuthHelper.signOut(scope, context = context) {
                          onSignedOut()
                        }
                      }
                    },
                  )
                }
              }
            }
          }
        }

        // Dismissible 24-Hour In-App Auto-Update Dialog
        updateVersion?.let { version ->
          UpdatePopupDialog(
            versionInfo = version,
            strings = strings,
            onUpdateNow = { url ->
              runCatching {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(intent)
              }
            },
            onDismissFor24Hours = { viewModel.dismissUpdatePopup() },
          )
        }
      }
    }
  }
}
