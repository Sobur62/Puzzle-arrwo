package com.example.ads

/** Supported store platforms for Unity Ads placement selection. */
enum class AdPlatform(val id: String, val displayName: String) {
  GOOGLE_PLAY("google_play", "Google Play"),
  APPLE_APP_STORE("apple_app_store", "Apple App Store");

  companion object {
    fun fromId(id: String?): AdPlatform =
      entries.firstOrNull { it.id == id } ?: GOOGLE_PLAY
  }
}

/**
 * Centralized placement configuration for Unity Ads.
 * No Game ID is hardcoded; Game IDs are loaded exclusively from EncryptedAdConfigStore.
 */
object AdConfig {
  // Google Play (Android) placement IDs
  private const val ANDROID_REWARDED_ID = "Rewarded_Android"
  private const val ANDROID_INTERSTITIAL_ID = "Interstitial_Android"
  private const val ANDROID_BANNER_ID = "Banner_Android"

  // Apple App Store (iOS) placement IDs (used when platform is toggled in Admin Panel)
  private const val IOS_REWARDED_ID = "Rewarded_iOS"
  private const val IOS_INTERSTITIAL_ID = "Interstitial_iOS"
  private const val IOS_BANNER_ID = "Banner_iOS"

  data class PlatformPlacements(
    val rewardedId: String,
    val interstitialId: String,
    val bannerId: String,
  )

  /** Returns the placement IDs matching the configured store platform. */
  fun placementsFor(platform: AdPlatform): PlatformPlacements {
    return when (platform) {
      AdPlatform.GOOGLE_PLAY ->
        PlatformPlacements(
          rewardedId = ANDROID_REWARDED_ID,
          interstitialId = ANDROID_INTERSTITIAL_ID,
          bannerId = ANDROID_BANNER_ID,
        )
      AdPlatform.APPLE_APP_STORE ->
        PlatformPlacements(
          rewardedId = IOS_REWARDED_ID,
          interstitialId = IOS_INTERSTITIAL_ID,
          bannerId = IOS_BANNER_ID,
        )
    }
  }
}
