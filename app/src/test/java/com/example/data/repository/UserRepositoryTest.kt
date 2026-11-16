package com.example.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ads.AdManager
import com.example.ads.AdPlatform
import com.example.ads.AdStrategyManager
import com.example.data.model.PlayerProfile
import com.example.security.AnonymousIdentityStore
import com.example.security.EncryptedAdConfigStore
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class UserRepositoryTest {

  private lateinit var context: Context
  private lateinit var testFilesDir: File

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    testFilesDir = File(context.filesDir, "user_repo_test_${System.nanoTime()}").apply { mkdirs() }
    context
      .getSharedPreferences("pixel_arrow_anonymous_identity", Context.MODE_PRIVATE)
      .edit()
      .clear()
      .commit()
    context
      .getSharedPreferences("pixel_arrow_anonymous_identity_fallback", Context.MODE_PRIVATE)
      .edit()
      .clear()
      .commit()
  }

  @Test
  fun anonymousUuidV4_generatedOnceAndReusedAcrossAppRestarts() {
    val firstStore = AnonymousIdentityStore(context)
    val id1 = firstStore.getOrCreateAnonymousUserId()
    assertTrue("Must be valid UUID v4", AnonymousIdentityStore.isValidUuidV4(id1))

    // Simulate cold app restart with a fresh store instance
    val secondStore = AnonymousIdentityStore(context)
    val id2 = secondStore.getOrCreateAnonymousUserId()
    assertEquals("Subsequent launches must reuse the exact same UUID v4", id1, id2)
  }

  @Test
  fun userRepository_createsProfileOnFirstLaunch_andIncrementsSessionsOnNextLaunch() = runBlocking {
    var fakeNow = 1_700_000_000_000L
    val identityStore = AnonymousIdentityStore(context)
    val repo1 =
      UserRepository(
        filesDir = testFilesDir,
        identityStore = identityStore,
        db = null,
        clock = { fakeNow },
      )

    val created = repo1.loadOrCreateProfile(incrementSession = true)
    assertEquals(PlayerProfile.CURRENT_SCHEMA_VERSION, created.schemaVersion)
    assertEquals(identityStore.getOrCreateAnonymousUserId(), created.userId)
    assertEquals(1_700_000_000_000L, created.createdAt)
    assertEquals(1_700_000_000_000L, created.lastActiveAt)
    assertEquals(1L, created.totalSessions)

    fakeNow += 60_000L
    repo1.addPlayTimeSeconds(45L)
    repo1.recordAdWatched(isRewarded = true, watchedAtMs = fakeNow)

    // Simulate next app launch with a brand-new repository instance
    fakeNow += 120_000L
    val repo2 =
      UserRepository(
        filesDir = testFilesDir,
        identityStore = AnonymousIdentityStore(context),
        db = null,
        clock = { fakeNow },
      )
    val reloaded = repo2.loadOrCreateProfile(incrementSession = true)

    assertEquals(created.userId, reloaded.userId)
    assertEquals(created.createdAt, reloaded.createdAt) // Immutable first-launch timestamp
    assertEquals(fakeNow, reloaded.lastActiveAt)
    assertEquals(2L, reloaded.totalSessions)
    assertEquals(45L, reloaded.totalPlayTimeSeconds)
    assertEquals(1L, reloaded.rewardedAdsWatched)
  }

  @Test
  fun userRepository_migratesLegacySchemaV0AndRecoversFromCrashBackup() = runBlocking {
    val identityStore = AnonymousIdentityStore(context)
    val uid = identityStore.getOrCreateAnonymousUserId()

    // Seed a legacy v0 file using old field names ("energy", "lastEnergyRefillEpochMs")
    val primaryFile = File(testFilesDir, "pixel_arrow_profile_${uid}.json")
    primaryFile.writeText(
      """
      {
        "schemaVersion": 0,
        "userId": "$uid",
        "createdAt": 1690000000000,
        "classicLevel": 14,
        "classicHighestCleared": 13,
        "energy": 3,
        "lastEnergyRefillEpochMs": 1690000005000,
        "coins": 420,
        "redeemedPromos": ["PIXEL100"]
      }
      """.trimIndent()
    )

    val repo =
      UserRepository(
        filesDir = testFilesDir,
        identityStore = identityStore,
        db = null,
        clock = { 1_700_000_500_000L },
      )
    val migrated = repo.loadOrCreateProfile(incrementSession = true)

    assertEquals(PlayerProfile.CURRENT_SCHEMA_VERSION, migrated.schemaVersion)
    assertEquals(14L, migrated.classicLevel)
    assertEquals(3L, migrated.lives)
    assertEquals(1690000005000L, migrated.lastLivesRefillEpochMs)
    assertEquals(420L, migrated.coins)
    assertEquals(listOf("PIXEL100"), migrated.redeemedPromos)

    // Simulate crash corruption of primary JSON file; .bak should recover cleanly
    primaryFile.writeText("{corrupted-half-written")
    val recoveredRepo =
      UserRepository(
        filesDir = testFilesDir,
        identityStore = identityStore,
        db = null,
        clock = { 1_700_000_600_000L },
      )
    val recovered = recoveredRepo.loadOrCreateProfile(incrementSession = true)
    assertEquals(14L, recovered.classicLevel)
    assertEquals(420L, recovered.coins)
  }

  @Test
  fun promoRedemption_enforcesSingleUseAndPersistsCoins() = runBlocking {
    val repo = UserRepository(testFilesDir, AnonymousIdentityStore(context), db = null)
    val initial = repo.loadOrCreateProfile(incrementSession = true)

    val firstOk = repo.redeemPromoAtomic("PIXEL100", rewardCoins = 100L, rewardHints = 1L)
    val secondOk = repo.redeemPromoAtomic("pixel100", rewardCoins = 100L, rewardHints = 1L)

    assertTrue(firstOk)
    assertFalse("Promo code cannot be redeemed twice", secondOk)
    assertEquals(initial.coins + 100L, repo.profileFlow.value.coins)
    assertEquals(initial.hintsRemaining + 1L, repo.profileFlow.value.hintsRemaining)
    assertTrue("PIXEL100" in repo.profileFlow.value.redeemedPromos)
  }

  @Test
  fun adStrategy_enforces90sCooldownFromPersistedProfileAndConfigStaysSeparate() = runBlocking {
    val adStore = EncryptedAdConfigStore(context)
    adStore.saveAdConfiguration("5742918", AdPlatform.GOOGLE_PLAY, testMode = true)
    val identityStore = AnonymousIdentityStore(context)
    val uid = identityStore.getOrCreateAnonymousUserId()

    val adManager = AdManager(context, adStore, initialPlayerUserId = uid)
    val strategy = AdStrategyManager(adManager)

    // Simulate 3 levels cleared in session
    repeat(3) { strategy.onLevelCleared() }

    val now = 2_000_000_000L
    val recentlyWatchedProfile =
      PlayerProfile(
        userId = uid,
        lastInterstitialAdAtMs = now - 30_000L, // Only 30s ago -> blocked by 90s cooldown
      )
    assertFalse(strategy.canShowInterstitialTiming(recentlyWatchedProfile, nowMs = now))

    val cooledDownProfile =
      recentlyWatchedProfile.copy(
        lastInterstitialAdAtMs = now - 95_000L, // 95s ago -> allowed
        totalPlayTimeSeconds = 200L,
      )
    assertTrue(strategy.canShowInterstitialTiming(cooledDownProfile, nowMs = now))
    assertTrue(strategy.isLongSessionBoostActive(cooledDownProfile, nowMs = now))

    // Admin Unity Game ID config remains independent from PlayerProfile
    assertEquals("5742918", adStore.getSavedGameId())
    assertNotEquals(uid, adStore.getSavedGameId())
  }
}
