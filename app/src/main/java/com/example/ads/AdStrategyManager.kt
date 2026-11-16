package com.example.ads

import com.example.data.model.PlayerProfile

/**
 * High-value ad monetization strategy:
 * - Prioritizes Rewarded Ads for bonus coins, bonus levels, revives, and hints.
 * - Enforces a minimum 90-second interstitial cooldown and frequency caps using persisted
 *   [PlayerProfile] timestamps and counters so limits survive app restarts.
 */
class AdStrategyManager(
  private val adManager: AdManager,
) {

  private val sessionStartMs: Long = System.currentTimeMillis()
  private var levelsClearedInSession: Int = 0
  private var interstitialShownInSession: Int = 0
  private var rewardedShownInSession: Int = 0

  /**
   * Returns true when the player qualifies for a long-session rewarded bonus
   * (active session >= 3 minutes, >= 3 levels cleared in session, or lifetime playtime >= 3 minutes).
   */
  fun isLongSessionBoostActive(
    profile: PlayerProfile,
    nowMs: Long = System.currentTimeMillis(),
  ): Boolean {
    val sessionElapsedMs = (nowMs - sessionStartMs).coerceAtLeast(0L)
    return sessionElapsedMs >= LONG_SESSION_THRESHOLD_MS ||
      levelsClearedInSession >= 3 ||
      profile.totalPlayTimeSeconds >= 180L
  }

  fun onLevelCleared() {
    levelsClearedInSession++
  }

  fun onRewardedAdCompleted() {
    rewardedShownInSession++
  }

  /**
   * Evaluates whether an interstitial may be shown after completing a level.
   * Never interrupts active gameplay; checks persisted cooldown (>= 90s from lastInterstitialAdAtMs),
   * session cap, level cadence, and placement readiness.
   */
  fun shouldShowInterstitialOnLevelComplete(
    profile: PlayerProfile,
    nowMs: Long = System.currentTimeMillis(),
  ): Boolean {
    if (!canShowInterstitialTiming(profile, nowMs)) return false
    val placement = AdConfig.placementsFor(adManager.status.value.platform).interstitialId
    return adManager.isReady(placement)
  }

  /**
   * Pure frequency-capping and cooldown check driven by persisted [PlayerProfile] stats.
   * Separated from SDK readiness for deterministic unit testing.
   */
  fun canShowInterstitialTiming(
    profile: PlayerProfile,
    nowMs: Long = System.currentTimeMillis(),
  ): Boolean {
    if (interstitialShownInSession >= MAX_INTERSTITIALS_PER_SESSION) return false
    if (levelsClearedInSession <= 0 || levelsClearedInSession % 3 != 0) return false
    val lastInterstitialAt = profile.lastInterstitialAdAtMs
    if (lastInterstitialAt > 0L && (nowMs - lastInterstitialAt) < INTERSTITIAL_COOLDOWN_MS) {
      return false
    }
    return true
  }

  /** Evaluates whether another rewarded ad watch is within soft anti-spam pacing. */
  fun isRewardedWithinFrequencyCap(
    profile: PlayerProfile,
    nowMs: Long = System.currentTimeMillis(),
  ): Boolean {
    val lastRewardedAt = profile.lastRewardedAdAtMs
    if (lastRewardedAt > 0L && (nowMs - lastRewardedAt) < REWARDED_MIN_PACING_MS) {
      return false
    }
    return rewardedShownInSession < MAX_REWARDED_PER_SESSION
  }

  fun markInterstitialShown() {
    interstitialShownInSession++
  }

  fun getRewardedPlacementId(): String =
    AdConfig.placementsFor(adManager.status.value.platform).rewardedId

  fun getInterstitialPlacementId(): String =
    AdConfig.placementsFor(adManager.status.value.platform).interstitialId

  fun getBannerPlacementId(): String =
    AdConfig.placementsFor(adManager.status.value.platform).bannerId

  fun getSessionSummary(profile: PlayerProfile): String {
    val sessionMins = ((System.currentTimeMillis() - sessionStartMs) / 60_000L).coerceAtLeast(0L)
    val totalMins = (profile.totalPlayTimeSeconds / 60L).coerceAtLeast(0L)
    return "Sessions: ${profile.totalSessions} · Play: ${totalMins}m (${sessionMins}m now) · " +
      "Rewarded: ${profile.rewardedAdsWatched} · Interstitial: ${profile.interstitialAdsWatched}"
  }

  companion object {
    const val INTERSTITIAL_COOLDOWN_MS = 90_000L // Minimum 90-second cooldown
    const val REWARDED_MIN_PACING_MS = 10_000L // 10-second anti-double-tap pacing
    const val LONG_SESSION_THRESHOLD_MS = 3 * 60_000L // 3-minute session threshold
    const val MAX_INTERSTITIALS_PER_SESSION = 6
    const val MAX_REWARDED_PER_SESSION = 20
  }
}
