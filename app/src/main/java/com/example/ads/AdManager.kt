package com.example.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.example.security.EncryptedAdConfigStore
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds
import com.unity3d.ads.UnityAdsLoadOptions
import com.unity3d.ads.UnityAdsShowOptions
import com.unity3d.ads.metadata.MetaData
import com.unity3d.ads.metadata.PlayerMetaData
import com.unity3d.services.banners.BannerErrorCode
import com.unity3d.services.banners.BannerErrorInfo
import com.unity3d.services.banners.BannerView
import com.unity3d.services.banners.UnityBannerSize
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdRuntimeStatus(
  val savedGameId: String = "",
  val platform: AdPlatform = AdPlatform.GOOGLE_PLAY,
  val testMode: Boolean = true,
  val isInitialized: Boolean = false,
  val isInitializing: Boolean = false,
  val readyPlacements: Set<String> = emptySet(),
  val lastError: String? = null,
)

/**
 * Manages the official Unity Ads SDK lifecycle, privacy metadata, player server ID,
 * background preloading/retries, and Rewarded, Interstitial, and Banner placements.
 */
class AdManager(
  context: Context,
  private val configStore: EncryptedAdConfigStore,
  initialPlayerUserId: String = "",
) {

  private val appContext = context.applicationContext
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  @Volatile private var playerUserId: String = initialPlayerUserId.trim()
  private var activityRef: WeakReference<Activity>? = null
  private val loadedPlacements = ConcurrentHashMap.newKeySet<String>()
  private val retryJobs = ConcurrentHashMap<String, Job>()
  private val retryAttempts = ConcurrentHashMap<String, Int>()
  private val bannerCooldownUntilMs = ConcurrentHashMap<String, Long>()

  private val _status =
    MutableStateFlow(
      AdRuntimeStatus(
        savedGameId = configStore.getSavedGameId(),
        platform = configStore.getPlatform(),
        testMode = configStore.isTestMode(),
      )
    )
  val status: StateFlow<AdRuntimeStatus> = _status.asStateFlow()

  /** Attaches the foreground Activity required to display full-screen placements. */
  fun attachActivity(activity: Activity) {
    activityRef = WeakReference(activity)
  }

  fun detachActivity() {
    activityRef = null
  }

  /**
   * Associates the anonymous UUID v4 player identifier with Unity Ads PlayerMetaData
   * for server-to-server rewarded attribution and reporting.
   */
  fun applyPlayerUserId(userId: String) {
    val cleanId = userId.trim()
    if (cleanId.isBlank()) return
    playerUserId = cleanId
    try {
      val playerMeta = PlayerMetaData(appContext)
      playerMeta.setServerId(cleanId)
      playerMeta.commit()
    } catch (e: Exception) {
      Log.e(TAG, "Failed to commit Unity PlayerMetaData", e)
    }
  }

  /**
   * Automatically initializes Unity Ads from EncryptedAdConfigStore on startup.
   * No ads are loaded or shown if no Game ID has been saved.
   */
  fun initializeFromSavedConfig() {
    val savedId = configStore.getSavedGameId()
    val testMode = configStore.isTestMode()
    val platform = configStore.getPlatform()
    _status.update {
      it.copy(savedGameId = savedId, platform = platform, testMode = testMode)
    }

    if (savedId.isBlank()) {
      Log.i(TAG, "No saved Unity Game ID found; skipping ad initialization.")
      return
    }

    if (playerUserId.isNotBlank()) {
      applyPlayerUserId(playerUserId)
    }
    applyPrivacyConsent(
      gdprConsent = configStore.getGdprConsent(),
      ccpaOptIn = configStore.getCcpaOptIn(),
    )
    initialize(gameId = savedId, testMode = testMode)
  }

  /** Commits GDPR, CCPA, and non-behavioral metadata to Unity Ads before initialization. */
  fun applyPrivacyConsent(gdprConsent: Boolean, ccpaOptIn: Boolean) {
    configStore.saveConsent(gdprConsent, ccpaOptIn)
    try {
      val gdprMeta = MetaData(appContext)
      gdprMeta.set("gdpr.consent", gdprConsent)
      gdprMeta.commit()

      val ccpaMeta = MetaData(appContext)
      ccpaMeta.set("privacy.consent", ccpaOptIn)
      ccpaMeta.commit()

      val userMeta = MetaData(appContext)
      userMeta.set("user.nonbehavioral", !gdprConsent)
      userMeta.commit()
    } catch (e: Exception) {
      Log.e(TAG, "Failed to commit Unity privacy metadata", e)
    }
  }

  /** Initializes the Unity Ads SDK with the configured Game ID and test mode flag. */
  fun initialize(gameId: String, testMode: Boolean) {
    val cleanId = gameId.trim()
    if (cleanId.isBlank()) {
      Log.w(TAG, "initialize() skipped: Game ID is blank.")
      _status.update { it.copy(savedGameId = "", isInitialized = false, isInitializing = false) }
      return
    }

    if (playerUserId.isNotBlank()) {
      applyPlayerUserId(playerUserId)
    }

    loadedPlacements.clear()
    retryAttempts.clear()
    bannerCooldownUntilMs.clear()
    retryJobs.values.forEach { it.cancel() }
    retryJobs.clear()

    _status.update {
      it.copy(
        savedGameId = cleanId,
        platform = configStore.getPlatform(),
        testMode = testMode,
        isInitializing = true,
        isInitialized = false,
        readyPlacements = emptySet(),
        lastError = null,
      )
    }

    try {
      UnityAds.initialize(
        appContext,
        cleanId,
        testMode,
        object : IUnityAdsInitializationListener {
          override fun onInitializationComplete() {
            Log.i(TAG, "Unity Ads initialization complete (testMode=$testMode)")
            _status.update {
              it.copy(isInitialized = true, isInitializing = false, lastError = null)
            }
            // Preload Rewarded and Interstitial placements immediately upon initialization
            val placements = AdConfig.placementsFor(configStore.getPlatform())
            loadAd(placements.rewardedId)
            loadAd(placements.interstitialId)
          }

          override fun onInitializationFailed(
            error: UnityAds.UnityAdsInitializationError?,
            message: String?,
          ) {
            val errMsg = "Init failed ($error): ${message.orEmpty()}"
            Log.e(TAG, errMsg)
            _status.update {
              it.copy(isInitialized = false, isInitializing = false, lastError = errMsg)
            }
          }
        },
      )
    } catch (e: Exception) {
      Log.e(TAG, "Unexpected exception during UnityAds.initialize", e)
      _status.update {
        it.copy(isInitialized = false, isInitializing = false, lastError = e.localizedMessage)
      }
    }
  }

  /** Loads the specified placement and schedules exponential backoff retries on failure. */
  fun loadAd(placementId: String) {
    val savedId = configStore.getSavedGameId()
    if (savedId.isBlank() || placementId.isBlank()) return
    if (!UnityAds.isInitialized) return

    try {
      UnityAds.load(
        placementId,
        UnityAdsLoadOptions(),
        object : IUnityAdsLoadListener {
          override fun onUnityAdsAdLoaded(loadedId: String?) {
            val id = loadedId ?: placementId
            loadedPlacements.add(id)
            retryAttempts.remove(id)
            retryJobs.remove(id)?.cancel()
            _status.update { it.copy(readyPlacements = loadedPlacements.toSet(), lastError = null) }
            Log.d(TAG, "Ad loaded: $id")
          }

          override fun onUnityAdsFailedToLoad(
            failedId: String?,
            error: UnityAds.UnityAdsLoadError?,
            message: String?,
          ) {
            val id = failedId ?: placementId
            val isNoFill =
              error == UnityAds.UnityAdsLoadError.NO_FILL ||
                message?.contains("no fill", ignoreCase = true) == true
            loadedPlacements.remove(id)
            _status.update {
              it.copy(
                readyPlacements = loadedPlacements.toSet(),
                lastError = if (isNoFill) it.lastError else "Load failed ($id): $error",
              )
            }
            if (isNoFill) {
              Log.i(TAG, "Placement $id has no ad inventory fill right now.")
            } else {
              Log.w(TAG, "Failed to load ad $id: $error - $message")
            }
            scheduleBackgroundRetry(id, isNoFill = isNoFill)
          }
        },
      )
    } catch (e: Exception) {
      Log.e(TAG, "Exception in loadAd($placementId)", e)
      scheduleBackgroundRetry(placementId, isNoFill = false)
    }
  }

  /** Schedules a delayed background reload when a placement fails to load. */
  private fun scheduleBackgroundRetry(placementId: String, isNoFill: Boolean) {
    val maxAttempts = if (isNoFill) 3 else 5
    val attempts = (retryAttempts[placementId] ?: 0) + 1
    if (attempts > maxAttempts) return
    retryAttempts[placementId] = attempts
    val baseDelayMs = if (isNoFill) 45_000L else 10_000L
    val delayMs = (attempts * baseDelayMs).coerceAtMost(120_000L)
    retryJobs[placementId]?.cancel()
    retryJobs[placementId] =
      scope.launch {
        delay(delayMs)
        loadAd(placementId)
      }
  }

  /** Returns true when a Game ID is saved, SDK is initialized, and the placement is loaded. */
  fun isReady(placementId: String): Boolean {
    if (configStore.getSavedGameId().isBlank()) return false
    return UnityAds.isInitialized && loadedPlacements.contains(placementId)
  }

  /** Shows a loaded placement using the attached Activity. */
  fun showAd(
    placementId: String,
    onComplete: (rewarded: Boolean) -> Unit = {},
    onUnavailable: () -> Unit = {},
  ) {
    val activity = activityRef?.get()
    if (activity == null || activity.isFinishing || activity.isDestroyed) {
      Log.w(TAG, "showAd($placementId) skipped: no active Activity attached")
      onUnavailable()
      return
    }
    showAd(activity, placementId, onComplete, onUnavailable)
  }

  /** Shows a Unity Ads placement using the provided Activity. */
  fun showAd(
    activity: Activity,
    placementId: String,
    onComplete: (rewarded: Boolean) -> Unit = {},
    onUnavailable: () -> Unit = {},
  ) {
    if (configStore.getSavedGameId().isBlank() || !isReady(placementId)) {
      Log.i(TAG, "Ad not ready for $placementId, triggering graceful fallback & reload")
      loadAd(placementId)
      onUnavailable()
      return
    }

    loadedPlacements.remove(placementId)
    _status.update { it.copy(readyPlacements = loadedPlacements.toSet()) }

    try {
      UnityAds.show(
        activity,
        placementId,
        UnityAdsShowOptions(),
        object : IUnityAdsShowListener {
          override fun onUnityAdsShowFailure(
            id: String?,
            error: UnityAds.UnityAdsShowError?,
            message: String?,
          ) {
            Log.e(TAG, "Show failed for ${id ?: placementId}: $error - $message")
            loadAd(placementId)
            onUnavailable()
          }

          override fun onUnityAdsShowStart(id: String?) {
            Log.d(TAG, "Ad show started: ${id ?: placementId}")
          }

          override fun onUnityAdsShowClick(id: String?) {
            Log.d(TAG, "Ad clicked: ${id ?: placementId}")
          }

          override fun onUnityAdsShowComplete(
            id: String?,
            state: UnityAds.UnityAdsShowCompletionState?,
          ) {
            val completed = state == UnityAds.UnityAdsShowCompletionState.COMPLETED
            Log.d(TAG, "Ad completed (${id ?: placementId}) state=$state")
            // Immediately preload the next ad after completion
            loadAd(placementId)
            onComplete(completed)
          }
        },
      )
    } catch (e: Exception) {
      Log.e(TAG, "Exception during UnityAds.show($placementId)", e)
      loadAd(placementId)
      onUnavailable()
    }
  }

  /** Creates and loads a BannerView, or returns null when no Game ID is saved or in NO_FILL cooldown. */
  fun createAndLoadBanner(
    activity: Activity,
    placementId: String,
    onBannerLoaded: (BannerView) -> Unit,
    onBannerFailed: (String) -> Unit,
  ): BannerView? {
    val savedId = configStore.getSavedGameId()
    if (savedId.isBlank() || placementId.isBlank() || !UnityAds.isInitialized) {
      return null
    }
    val key = "$savedId:$placementId"
    val now = System.currentTimeMillis()
    val cooldownUntil = bannerCooldownUntilMs[key] ?: 0L
    if (now < cooldownUntil) {
      onBannerFailed("Banner inventory cooling down")
      return null
    }
    return try {
      BannerView(activity, placementId, UnityBannerSize(320, 50)).apply {
        listener =
          object : BannerView.Listener() {
            override fun onBannerLoaded(bannerAdView: BannerView?) {
              bannerCooldownUntilMs.remove(key)
              if (bannerAdView != null) {
                onBannerLoaded(bannerAdView)
              }
            }

            override fun onBannerFailedToLoad(
              bannerAdView: BannerView?,
              errorInfo: BannerErrorInfo?,
            ) {
              val isNoFill =
                errorInfo?.errorCode == BannerErrorCode.NO_FILL ||
                  errorInfo?.errorMessage?.contains("no fill", ignoreCase = true) == true
              val cooldownMs = if (isNoFill) BANNER_NO_FILL_COOLDOWN_MS else BANNER_ERROR_COOLDOWN_MS
              bannerCooldownUntilMs[key] = System.currentTimeMillis() + cooldownMs
              if (isNoFill) {
                Log.i(TAG, "No banner fill for $placementId; pausing retries for ${cooldownMs / 1000}s.")
              } else {
                Log.w(TAG, "Banner failed for $placementId (${errorInfo?.errorCode}): ${errorInfo?.errorMessage}")
              }
              onBannerFailed(errorInfo?.errorMessage ?: "Banner load failed")
            }

            override fun onBannerClick(bannerAdView: BannerView?) {
              Log.d(TAG, "Banner clicked: $placementId")
            }

            override fun onBannerLeftApplication(bannerAdView: BannerView?) {}
          }
        load()
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to create Unity BannerView", e)
      onBannerFailed(e.localizedMessage ?: "Banner init error")
      null
    }
  }

  private companion object {
    const val TAG = "UnityAdManager"
    const val BANNER_NO_FILL_COOLDOWN_MS = 120_000L
    const val BANNER_ERROR_COOLDOWN_MS = 30_000L
  }
}
