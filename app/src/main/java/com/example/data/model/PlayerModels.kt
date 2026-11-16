package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

/**
 * Complete player profile persisted under the anonymous UUID v4 userId.
 * Holds level progression, balances, lives, redeemed promo codes, settings, and session/ad stats.
 */
@IgnoreExtraProperties
data class PlayerProfile(
  val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
  @DocumentId val id: String = "",
  val userId: String = "",
  val createdAt: Long = 0L,
  val lastActiveAt: Long = 0L,
  val displayName: String = "Player",
  val classicLevel: Long = 1L,
  val classicHighestCleared: Long = 0L,
  val fallStage: Long = 1L,
  val fallHighScore: Long = 0L,
  val selectedSkinId: String = DEFAULT_SKIN_ID,
  val unlockedSkins: List<String> = ALL_SKIN_IDS,
  val lives: Long = MAX_LIVES.toLong(),
  val lastLivesRefillEpochMs: Long = 0L,
  val hintsRemaining: Long = 3L,
  val sfxEnabled: Boolean = true,
  val bgmEnabled: Boolean = false,
  val vibrationEnabled: Boolean = true,
  val darkMode: Boolean = false,
  val totalArrowsCleared: Long = 0L,
  val coins: Long = 50L,
  val redeemedPromos: List<String> = emptyList(),
  val totalSessions: Long = 0L,
  val totalPlayTimeSeconds: Long = 0L,
  val rewardedAdsWatched: Long = 0L,
  val interstitialAdsWatched: Long = 0L,
  val lastRewardedAdAtMs: Long = 0L,
  val lastInterstitialAdAtMs: Long = 0L,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  @get:Exclude
  val currentLives: Int
    get() = lives.toInt().coerceIn(0, MAX_LIVES)

  companion object {
    const val CURRENT_SCHEMA_VERSION = 1
    const val MAX_LIVES = 5
    const val DEFAULT_SKIN_ID = "green_dots"
    val ALL_SKIN_IDS =
      listOf(
        "green_dots",
        "classic_ink",
        "googly_noodles",
        "matchsticks",
        "caterpillars",
        "prism_arrows",
        "reindeer",
        "santa",
        "pine_tree",
      )
  }
}

/**
 * Individual level completion entry recorded under a player's profile history.
 */
@IgnoreExtraProperties
data class LevelRecord(
  @DocumentId val id: String = "",
  val userId: String = "",
  val mode: String = "classic",
  val levelNumber: Long = 1L,
  val stars: Long = 3L,
  val mistakes: Long = 0L,
  val durationSeconds: Long = 0L,
  val arrowsCleared: Long = 1L,
  val createdAtMs: Long = 0L,
  @ServerTimestamp val createdAt: Timestamp? = null,
)
