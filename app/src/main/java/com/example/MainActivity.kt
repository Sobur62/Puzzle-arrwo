package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ads.AdManager
import com.example.ads.AdStrategyManager
import com.example.data.repository.UserRepository
import com.example.game.audio.SoundAndHapticsManager
import com.example.game.engine.ProceduralLevelGenerator
import com.example.game.model.ArrowSkin
import com.example.game.viewmodel.AppScreen
import com.example.game.viewmodel.ArrowPuzzleViewModel
import com.example.security.AnonymousIdentityStore
import com.example.security.EncryptedAdConfigStore
import com.example.ui.components.AdminPanelDialog
import com.example.ui.components.LevelResultDialog
import com.example.ui.components.LevelSelectDialog
import com.example.ui.components.PrivacyConsentDialog
import com.example.ui.components.PromoCodeDialog
import com.example.ui.components.SettingsDialog
import com.example.ui.components.SkinSelectorDialog
import com.example.ui.screens.GameplayScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SplashLoadingScreen
import com.example.ui.theme.ArrowPuzzleTheme

/**
 * Main Activity for Pixel Arrow.
 * Initializes the anonymous UUID v4 identity, Unity Ads SDK (bound to PlayerMetaData),
 * audio/haptic feedback, and launches straight into gameplay without any login gate.
 */
class MainActivity : ComponentActivity() {

  private lateinit var soundManager: SoundAndHapticsManager
  private lateinit var adConfigStore: EncryptedAdConfigStore
  private lateinit var identityStore: AnonymousIdentityStore
  private lateinit var adManager: AdManager
  private lateinit var adStrategy: AdStrategyManager

  private var foregroundChangeListener: ((Boolean) -> Unit)? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    soundManager = SoundAndHapticsManager(applicationContext)
    adConfigStore = EncryptedAdConfigStore(applicationContext)
    identityStore = AnonymousIdentityStore(applicationContext)

    // Auto-generate or load the persistent UUID v4 silently without any login UI
    val anonymousUserId = identityStore.getOrCreateAnonymousUserId()

    adManager = AdManager(applicationContext, adConfigStore, initialPlayerUserId = anonymousUserId)
    adStrategy = AdStrategyManager(adManager)

    // Attach Activity, bind player UUID to Unity Ads PlayerMetaData, and init if Game ID is saved
    adManager.attachActivity(this)
    adManager.applyPlayerUserId(anonymousUserId)
    adManager.initializeFromSavedConfig()

    enableEdgeToEdge()
    setContent {
      PixelArrowRoot(
        anonymousUserId = anonymousUserId,
        identityStore = identityStore,
        soundManager = soundManager,
        adManager = adManager,
        adStrategy = adStrategy,
        adConfigStore = adConfigStore,
        onRegisterForegroundListener = { listener -> foregroundChangeListener = listener },
      )
    }
  }

  override fun onResume() {
    super.onResume()
    foregroundChangeListener?.invoke(true)
    if (::soundManager.isInitialized) {
      soundManager.onAppResume()
    }
  }

  override fun onPause() {
    foregroundChangeListener?.invoke(false)
    super.onPause()
    if (::soundManager.isInitialized) {
      soundManager.onAppPause()
    }
  }

  override fun onDestroy() {
    foregroundChangeListener = null
    if (::adManager.isInitialized) {
      adManager.detachActivity()
    }
    if (::soundManager.isInitialized) {
      soundManager.release()
    }
    super.onDestroy()
  }
}

@Composable
private fun PixelArrowRoot(
  anonymousUserId: String,
  identityStore: AnonymousIdentityStore,
  soundManager: SoundAndHapticsManager,
  adManager: AdManager,
  adStrategy: AdStrategyManager,
  adConfigStore: EncryptedAdConfigStore,
  onRegisterForegroundListener: ((Boolean) -> Unit) -> Unit,
) {
  var showSplash by remember { mutableStateOf(true) }

  if (showSplash) {
    ArrowPuzzleTheme(darkTheme = false) {
      SplashLoadingScreen(onFinished = { showSplash = false })
    }
    return
  }

  PixelArrowGameApp(
    anonymousUserId = anonymousUserId,
    identityStore = identityStore,
    soundManager = soundManager,
    adManager = adManager,
    adStrategy = adStrategy,
    adConfigStore = adConfigStore,
    onRegisterForegroundListener = onRegisterForegroundListener,
  )
}

@Composable
private fun PixelArrowGameApp(
  anonymousUserId: String,
  identityStore: AnonymousIdentityStore,
  soundManager: SoundAndHapticsManager,
  adManager: AdManager,
  adStrategy: AdStrategyManager,
  adConfigStore: EncryptedAdConfigStore,
  onRegisterForegroundListener: ((Boolean) -> Unit) -> Unit,
) {
  val viewModel: ArrowPuzzleViewModel =
    viewModel(
      key = anonymousUserId,
      factory =
        viewModelFactory {
          initializer {
            val app = checkNotNull(this[APPLICATION_KEY])
            val userRepository = UserRepository(context = app, identityStore = identityStore)
            ArrowPuzzleViewModel(
              repository = userRepository,
              soundManager = soundManager,
              adManager = adManager,
              adStrategy = adStrategy,
              adConfigStore = adConfigStore,
              levelGenerator = ProceduralLevelGenerator(),
            )
          }
        },
    )

  LaunchedEffect(viewModel) {
    onRegisterForegroundListener { inForeground ->
      viewModel.onAppForegroundChanged(inForeground)
    }
  }

  val profile by viewModel.profileState.collectAsStateWithLifecycle()
  val recentHistory by viewModel.recentHistoryState.collectAsStateWithLifecycle()
  val screen by viewModel.screen.collectAsStateWithLifecycle()
  val boardState by viewModel.boardState.collectAsStateWithLifecycle()
  val livesState by viewModel.livesState.collectAsStateWithLifecycle()
  val promoState by viewModel.promoState.collectAsStateWithLifecycle()
  val openAdminEvent by viewModel.openAdminEvent.collectAsStateWithLifecycle()
  val showConsentDialog by viewModel.showConsentDialog.collectAsStateWithLifecycle()
  val adStatus by viewModel.adStatus.collectAsStateWithLifecycle()
  val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

  var showSettings by remember { mutableStateOf(false) }
  var showSkins by remember { mutableStateOf(false) }
  var showLevelSelect by remember { mutableStateOf(false) }
  var showPromoDialog by remember { mutableStateOf(false) }
  var showAdminPanel by remember { mutableStateOf(false) }

  // Close the Promo dialog and open the hidden Admin Panel when the SHA-256 secret matches
  LaunchedEffect(openAdminEvent) {
    if (openAdminEvent) {
      showPromoDialog = false
      showAdminPanel = true
      viewModel.consumeAdminOpenEvent()
    }
  }

  ArrowPuzzleTheme(darkTheme = profile.darkMode) {
    Box(modifier = Modifier.fillMaxSize()) {
      when (screen) {
        AppScreen.HOME -> {
          HomeScreen(
            profile = profile,
            livesState = livesState,
            adManager = adManager,
            adStatus = adStatus,
            bannerPlacementId = viewModel.getBannerPlacementId(),
            isLongSessionBoost = viewModel.isLongSessionBoostActive(),
            onPlayClassic = { viewModel.startClassicLevel() },
            onPlayFallMode = { viewModel.startFallMode() },
            onPlayBonusLevel = { viewModel.unlockBonusLevelWithRewardedAd() },
            onOpenPromoCode = {
              soundManager.playClickSfx()
              viewModel.clearPromoMessage()
              showPromoDialog = true
            },
            onOpenSettings = {
              soundManager.playClickSfx()
              showSettings = true
            },
            onOpenSkins = {
              soundManager.playClickSfx()
              showSkins = true
            },
            onOpenLevels = {
              soundManager.playClickSfx()
              showLevelSelect = true
            },
            onToggleQuickSound = {
              val currentlyOn = profile.sfxEnabled || profile.bgmEnabled
              viewModel.setSfxEnabled(!currentlyOn)
              viewModel.setBgmEnabled(!currentlyOn)
            },
            onRefillLives = { viewModel.refillLivesFree() },
          )
        }

        AppScreen.PLAYING -> {
          GameplayScreen(
            boardState = boardState,
            skin = ArrowSkin.fromId(profile.selectedSkinId),
            hintsRemaining = profile.hintsRemaining,
            soundActive = profile.sfxEnabled || profile.bgmEnabled,
            darkTheme = profile.darkMode,
            onArrowTapped = { viewModel.onArrowTapped(it) },
            onTriggerHint = { viewModel.triggerHint() },
            onOpenSettings = {
              soundManager.playClickSfx()
              showSettings = true
            },
            onToggleQuickSound = {
              val currentlyOn = profile.sfxEnabled || profile.bgmEnabled
              viewModel.setSfxEnabled(!currentlyOn)
              viewModel.setBgmEnabled(!currentlyOn)
            },
            onBackHome = { viewModel.goHome() },
          )
        }
      }

      if (showSettings) {
        SettingsDialog(
          profile = profile,
          inGame = screen == AppScreen.PLAYING,
          onToggleSfx = { viewModel.setSfxEnabled(it) },
          onToggleBgm = { viewModel.setBgmEnabled(it) },
          onToggleVibration = { viewModel.setVibrationEnabled(it) },
          onToggleDark = { viewModel.setDarkMode(it) },
          onOpenPromoCode = {
            viewModel.clearPromoMessage()
            showPromoDialog = true
          },
          onOpenPrivacyConsent = { viewModel.openPrivacyConsentDialog() },
          onRestartLevel = { viewModel.restartCurrentLevel() },
          onGoHome = { viewModel.goHome() },
          onDismiss = { showSettings = false },
        )
      }

      if (showSkins) {
        SkinSelectorDialog(
          selectedSkin = ArrowSkin.fromId(profile.selectedSkinId),
          darkTheme = profile.darkMode,
          onSelectSkin = { skin -> viewModel.selectSkin(skin) },
          onDismiss = { showSkins = false },
        )
      }

      if (showLevelSelect) {
        LevelSelectDialog(
          currentLevel = profile.classicLevel.toInt(),
          highestCleared = profile.classicHighestCleared.toInt(),
          history = recentHistory,
          darkTheme = profile.darkMode,
          onSelectLevel = { lvl ->
            showLevelSelect = false
            viewModel.startClassicLevel(lvl)
          },
          onDismiss = { showLevelSelect = false },
        )
      }

      if (showPromoDialog) {
        PromoCodeDialog(
          promoState = promoState,
          darkTheme = profile.darkMode,
          onSubmitCode = { code -> viewModel.submitPromoOrAdminCode(code) },
          onDismiss = {
            viewModel.clearPromoMessage()
            showPromoDialog = false
          },
        )
      }

      if (showAdminPanel) {
        AdminPanelDialog(
          adStatus = adStatus,
          sessionSummary = viewModel.getSessionSummary(),
          darkTheme = profile.darkMode,
          onSaveConfig = { gameId, platform, testMode ->
            viewModel.saveAdminAdConfig(gameId, platform, testMode)
          },
          onDismiss = { showAdminPanel = false },
        )
      }

      if (showConsentDialog) {
        PrivacyConsentDialog(
          initialGdpr = adConfigStore.getGdprConsent(),
          initialCcpa = adConfigStore.getCcpaOptIn(),
          darkTheme = profile.darkMode,
          onSaveConsent = { gdpr, ccpa -> viewModel.savePrivacyConsent(gdpr, ccpa) },
        )
      }

      val levelResult = boardState.result
      if (screen == AppScreen.PLAYING && levelResult != null) {
        LevelResultDialog(
          result = levelResult,
          darkTheme = profile.darkMode,
          isLongSessionBoost = viewModel.isLongSessionBoostActive(),
          onClaimDoubleCoins = { viewModel.claimDoubleCoinsViaRewardedAd() },
          onNextLevel = { viewModel.advanceToNextLevel() },
          onRetry = { viewModel.restartCurrentLevel() },
          onRevive = { viewModel.reviveWithRewardedAd() },
          onHome = { viewModel.goHome() },
        )
      }

      if (statusBanner != null) {
        Snackbar(
          modifier = Modifier
            .align(Alignment.TopCenter)
            .statusBarsPadding()
            .padding(16.dp),
          action = {
            TextButton(onClick = { viewModel.dismissBanner() }) {
              Text("OK")
            }
          },
        ) {
          Text(statusBanner!!)
        }
      }
    }
  }
}
