package com.example.data.repository

import android.content.Context
import android.util.Log
import androidx.core.util.AtomicFile
import com.example.R
import com.example.data.firestore.OperationType
import com.example.data.firestore.handleFirestoreError
import com.example.data.model.LevelRecord
import com.example.data.model.PlayerProfile
import com.example.security.AnonymousIdentityStore
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

/**
 * Single repository managing the player's PlayerProfile and level history.
 *
 * Responsibilities:
 * - Resolves the immutable anonymous UUID v4 via [AnonymousIdentityStore].
 * - Provides crash-safe atomic local persistence with automatic schema migration.
 * - Synchronizes all profile state and level history to Firebase Firestore under /users/{userId}.
 * - Enforces single-use promo redemption and immutable identity/creation metadata.
 */
class UserRepository(
  private val filesDir: File,
  private val identityStore: AnonymousIdentityStore,
  private val db: FirebaseFirestore? = null,
  private val clock: () -> Long = System::currentTimeMillis,
) {

  constructor(
    context: Context,
    identityStore: AnonymousIdentityStore = AnonymousIdentityStore(context),
    db: FirebaseFirestore? = createDefaultFirestore(context),
  ) : this(
    filesDir = context.applicationContext.filesDir,
    identityStore = identityStore,
    db = db,
  )

  val userId: String = identityStore.getOrCreateAnonymousUserId()

  private val mutex = Mutex()
  private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private val profileFile = File(filesDir, "pixel_arrow_profile_${userId}.json")
  private val profileBackupFile = File(filesDir, "pixel_arrow_profile_${userId}.json.bak")
  private val historyFile = File(filesDir, "pixel_arrow_history_${userId}.json")

  private val _profileFlow = MutableStateFlow(loadInitialSnapshotSync())
  val profileFlow: StateFlow<PlayerProfile> = _profileFlow.asStateFlow()

  private val _localHistoryFlow = MutableStateFlow(readLocalHistorySync())

  @Volatile private var sessionRecorded = false

  /**
   * Loads the existing profile (migrating older schemas if necessary) or creates a new one on
   * first launch. Optionally increments totalSessions once per repository lifecycle.
   */
  suspend fun loadOrCreateProfile(incrementSession: Boolean = true): PlayerProfile =
    mutex.withLock {
      val now = clock()
      val diskResult = readAndMigrateLocalProfile(now)
      val baseProfile: PlayerProfile
      val isFirstCreate: Boolean

      if (diskResult != null) {
        baseProfile = diskResult.profile
        isFirstCreate = false
      } else {
        val remoteProfile = fetchRemoteProfileSafely()
        if (remoteProfile != null) {
          baseProfile = remoteProfile.copy(id = userId, userId = userId)
          isFirstCreate = false
        } else {
          baseProfile = createFreshProfile(userId, now)
          isFirstCreate = true
        }
      }

      val shouldIncrementSession = incrementSession && !sessionRecorded
      if (shouldIncrementSession) {
        sessionRecorded = true
      }

      val updated =
        baseProfile.copy(
          schemaVersion = PlayerProfile.CURRENT_SCHEMA_VERSION,
          id = userId,
          userId = userId,
          createdAt = if (baseProfile.createdAt > 0L) baseProfile.createdAt else now,
          lastActiveAt = now,
          lastLivesRefillEpochMs =
            if (baseProfile.lastLivesRefillEpochMs > 0L) baseProfile.lastLivesRefillEpochMs else now,
          totalSessions =
            if (shouldIncrementSession) baseProfile.totalSessions + 1L else baseProfile.totalSessions,
        )

      if (isFirstCreate || diskResult?.wasMigrated == true || shouldIncrementSession || updated != baseProfile) {
        writeLocalProfileAtomically(updated)
      }
      _profileFlow.value = updated
      scheduleFirestoreSync(updated, isCreate = isFirstCreate)
      updated
    }

  /** Applies an atomic, schema-safe transformation to the persisted PlayerProfile. */
  suspend fun updateProfile(transform: (PlayerProfile) -> PlayerProfile): PlayerProfile =
    mutex.withLock {
      val now = clock()
      val current = _profileFlow.value
      val rawNext = transform(current)
      val sanitized = sanitizeProfileUpdate(previous = current, candidate = rawNext, nowMs = now)
      writeLocalProfileAtomically(sanitized)
      _profileFlow.value = sanitized
      scheduleFirestoreSync(sanitized, isCreate = false)
      sanitized
    }

  suspend fun recordSessionStart(): PlayerProfile = loadOrCreateProfile(incrementSession = true)

  suspend fun addPlayTimeSeconds(deltaSeconds: Long): PlayerProfile {
    if (deltaSeconds <= 0L) return _profileFlow.value
    return updateProfile { current ->
      current.copy(
        totalPlayTimeSeconds = (current.totalPlayTimeSeconds + deltaSeconds).coerceAtMost(MAX_STAT_CAP)
      )
    }
  }

  suspend fun recordAdWatched(isRewarded: Boolean, watchedAtMs: Long = clock()): PlayerProfile =
    updateProfile { current ->
      if (isRewarded) {
        current.copy(
          rewardedAdsWatched = (current.rewardedAdsWatched + 1L).coerceAtMost(MAX_STAT_CAP),
          lastRewardedAdAtMs = watchedAtMs,
        )
      } else {
        current.copy(
          interstitialAdsWatched = (current.interstitialAdsWatched + 1L).coerceAtMost(MAX_STAT_CAP),
          lastInterstitialAdAtMs = watchedAtMs,
        )
      }
    }

  /**
   * Atomically redeems a promo code if not yet redeemed.
   * Returns true if redeemed and rewards were applied; false if already redeemed.
   */
  suspend fun redeemPromoAtomic(
    codeUpper: String,
    rewardCoins: Long,
    rewardHints: Long,
  ): Boolean =
    mutex.withLock {
      val normalized = codeUpper.trim().uppercase()
      val current = _profileFlow.value
      if (normalized.isEmpty() || normalized in current.redeemedPromos) {
        return@withLock false
      }
      val now = clock()
      val updated =
        sanitizeProfileUpdate(
          previous = current,
          candidate =
            current.copy(
              coins = (current.coins + rewardCoins).coerceAtMost(MAX_STAT_CAP),
              hintsRemaining = (current.hintsRemaining + rewardHints).coerceIn(0L, 999L),
              redeemedPromos = (current.redeemedPromos + normalized).distinct().take(50),
            ),
          nowMs = now,
        )
      writeLocalProfileAtomically(updated)
      _profileFlow.value = updated
      scheduleFirestoreSync(updated, isCreate = false)
      true
    }

  suspend fun spendCoins(amount: Long): Boolean {
    if (amount <= 0L) return true
    return mutex.withLock {
      val current = _profileFlow.value
      if (current.coins < amount) return@withLock false
      val now = clock()
      val updated =
        sanitizeProfileUpdate(
          previous = current,
          candidate = current.copy(coins = current.coins - amount),
          nowMs = now,
        )
      writeLocalProfileAtomically(updated)
      _profileFlow.value = updated
      scheduleFirestoreSync(updated, isCreate = false)
      true
    }
  }

  suspend fun grantCoins(amount: Long): PlayerProfile {
    if (amount <= 0L) return _profileFlow.value
    return updateProfile { current ->
      current.copy(coins = (current.coins + amount).coerceAtMost(MAX_STAT_CAP))
    }
  }

  suspend fun consumeLifeOnLoss(nowMs: Long = clock()): PlayerProfile =
    updateProfile { current ->
      val curLives = current.lives.toInt().coerceIn(0, PlayerProfile.MAX_LIVES)
      if (curLives <= 0) return@updateProfile current
      val nextLives = (curLives - 1).coerceAtLeast(0).toLong()
      val nextAnchor =
        if (curLives == PlayerProfile.MAX_LIVES) nowMs else current.lastLivesRefillEpochMs
      current.copy(lives = nextLives, lastLivesRefillEpochMs = nextAnchor)
    }

  suspend fun reconcileLives(newLives: Long, newAnchorMs: Long): PlayerProfile =
    updateProfile { current ->
      current.copy(
        lives = newLives.coerceIn(0L, PlayerProfile.MAX_LIVES.toLong()),
        lastLivesRefillEpochMs = newAnchorMs.coerceAtLeast(0L),
      )
    }

  suspend fun refillLivesFull(nowMs: Long = clock()): PlayerProfile =
    updateProfile { current ->
      current.copy(
        lives = PlayerProfile.MAX_LIVES.toLong(),
        lastLivesRefillEpochMs = nowMs,
      )
    }

  suspend fun decrementHint(): PlayerProfile =
    updateProfile { current ->
      current.copy(hintsRemaining = (current.hintsRemaining - 1L).coerceAtLeast(0L))
    }

  suspend fun selectSkin(skinId: String): PlayerProfile =
    updateProfile { current ->
      current.copy(selectedSkinId = skinId.trim().take(40).ifEmpty { PlayerProfile.DEFAULT_SKIN_ID })
    }

  suspend fun setSfxEnabled(enabled: Boolean): PlayerProfile =
    updateProfile { it.copy(sfxEnabled = enabled) }

  suspend fun setBgmEnabled(enabled: Boolean): PlayerProfile =
    updateProfile { it.copy(bgmEnabled = enabled) }

  suspend fun setVibrationEnabled(enabled: Boolean): PlayerProfile =
    updateProfile { it.copy(vibrationEnabled = enabled) }

  suspend fun setDarkMode(enabled: Boolean): PlayerProfile =
    updateProfile { it.copy(darkMode = enabled) }

  suspend fun recordLevelWin(
    mode: String,
    levelNumber: Int,
    stars: Int,
    mistakes: Int,
    durationSeconds: Int,
    arrowsCleared: Int,
    coinsEarned: Long,
    scoreEarned: Int,
    isBonusLevel: Boolean,
    entryId: String? = null,
  ): String {
    updateProfile { current ->
      val nextTotalArrows = (current.totalArrowsCleared + arrowsCleared.toLong()).coerceAtMost(MAX_STAT_CAP)
      val nextCoins = (current.coins + coinsEarned).coerceAtMost(MAX_STAT_CAP)
      if (isBonusLevel) {
        current.copy(totalArrowsCleared = nextTotalArrows, coins = nextCoins)
      } else if (mode == "classic") {
        val nextClassic = maxOf(current.classicLevel.toInt(), levelNumber + 1).coerceIn(1, 9999).toLong()
        val highest = maxOf(current.classicHighestCleared.toInt(), levelNumber).coerceIn(0, 9999).toLong()
        val bonusHint = if (levelNumber % 3 == 0) 1L else 0L
        current.copy(
          totalArrowsCleared = nextTotalArrows,
          coins = nextCoins,
          classicLevel = nextClassic,
          classicHighestCleared = highest,
          hintsRemaining = (current.hintsRemaining + bonusHint).coerceIn(0L, 999L),
        )
      } else {
        val nextFall = maxOf(current.fallStage.toInt(), levelNumber + 1).coerceIn(1, 9999).toLong()
        val bestScore = maxOf(current.fallHighScore, scoreEarned.toLong()).coerceIn(0L, MAX_STAT_CAP)
        current.copy(
          totalArrowsCleared = nextTotalArrows,
          coins = nextCoins,
          fallStage = nextFall,
          fallHighScore = bestScore,
        )
      }
    }

    return recordLevelCompletion(
      mode = mode,
      levelNumber = levelNumber,
      stars = stars,
      mistakes = mistakes,
      durationSeconds = durationSeconds,
      arrowsCleared = arrowsCleared,
      entryId = entryId,
    )
  }

  suspend fun recordLevelCompletion(
    mode: String,
    levelNumber: Int,
    stars: Int,
    mistakes: Int,
    durationSeconds: Int,
    arrowsCleared: Int,
    entryId: String? = null,
  ): String {
    val safeEntryId =
      entryId?.takeIf { it.isNotBlank() } ?: "rec_${UUID.randomUUID().toString().replace("-", "")}"
    val now = clock()
    val record =
      LevelRecord(
        id = safeEntryId,
        userId = userId,
        mode = if (mode == "fall") "fall" else "classic",
        levelNumber = levelNumber.coerceIn(1, 9999).toLong(),
        stars = stars.coerceIn(1, 3).toLong(),
        mistakes = mistakes.coerceIn(0, 10).toLong(),
        durationSeconds = durationSeconds.coerceIn(0, 86400).toLong(),
        arrowsCleared = arrowsCleared.coerceIn(1, 5000).toLong(),
        createdAtMs = now,
      )

    mutex.withLock {
      val updatedHistory = (listOf(record) + _localHistoryFlow.value).distinctBy { it.id }.take(50)
      _localHistoryFlow.value = updatedHistory
      writeLocalHistoryAtomically(updatedHistory)
    }

    val firestore = db
    if (firestore != null) {
      val docRef =
        firestore.collection("users").document(userId).collection("level_history").document(safeEntryId)
      val payload =
        mapOf(
          "userId" to userId,
          "mode" to record.mode,
          "levelNumber" to record.levelNumber,
          "stars" to record.stars,
          "mistakes" to record.mistakes,
          "durationSeconds" to record.durationSeconds,
          "arrowsCleared" to record.arrowsCleared,
          "createdAt" to FieldValue.serverTimestamp(),
        )
      try {
        docRef.set(payload).await()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.CREATE, docRef.path)
        Log.w(TAG, "Deferred Firestore level_history sync for $safeEntryId", e)
      }
    }

    return safeEntryId
  }

  fun observeRecentHistory(limitCount: Long = 25L): Flow<List<LevelRecord>> {
    val firestore = db ?: return _localHistoryFlow.map { it.take(limitCount.toInt()) }
    return flow {
      val collectionRef = firestore.collection("users").document(userId).collection("level_history")
      emitAll(
        collectionRef
          .orderBy("createdAt", Query.Direction.DESCENDING)
          .limit(limitCount)
          .snapshots()
          .map { snapshot ->
            val remote =
              snapshot.toObjects(
                LevelRecord::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
              )
            if (remote.isNotEmpty()) remote else _localHistoryFlow.value.take(limitCount.toInt())
          }
          .catch { error ->
            val firestoreError =
              generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Falling back to local history stream", firestoreError ?: error)
            emit(_localHistoryFlow.value.take(limitCount.toInt()))
          }
      )
    }
  }

  /**
   * Synchronously flushes the current in-memory profile to Firestore, returning a [Result]
   * with any underlying exception preserved.
   */
  suspend fun syncToFirestoreResult(): Result<Unit> {
    val firestore = db ?: return Result.failure(IllegalStateException("Firestore instance is null"))
    val current = _profileFlow.value
    val docRef = firestore.collection("users").document(userId)
    val payload = buildFirestorePayload(current, includeCreatedAt = true)
    return try {
      docRef.set(payload, SetOptions.merge()).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path)
      Result.failure(e)
    }
  }

  /**
   * Synchronously flushes the current in-memory profile to Firestore when invoked by tests
   * or explicit callers expecting immediate completion.
   */
  suspend fun syncToFirestoreNow(): Boolean = syncToFirestoreResult().isSuccess

  private fun scheduleFirestoreSync(profile: PlayerProfile, isCreate: Boolean) {
    val firestore = db ?: return
    syncScope.launch {
      val docRef = firestore.collection("users").document(profile.userId)
      val payload = buildFirestorePayload(profile, includeCreatedAt = isCreate)
      try {
        docRef.set(payload, SetOptions.merge()).await()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, docRef.path)
        Log.w(TAG, "Background Firestore profile sync deferred", e)
      }
    }
  }

  private suspend fun fetchRemoteProfileSafely(): PlayerProfile? {
    val firestore = db ?: return null
    val docRef = firestore.collection("users").document(userId)
    return try {
      withTimeoutOrNull(REMOTE_FETCH_TIMEOUT_MS) {
        val snapshot = docRef.get().await()
        if (snapshot.exists()) {
          snapshotToPlayerProfile(snapshot, fallbackUserId = userId, nowMs = clock())
        } else {
          null
        }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.w(TAG, "Unable to fetch remote profile on startup; continuing with local profile", e)
      null
    }
  }

  private fun loadInitialSnapshotSync(): PlayerProfile {
    val now = clock()
    return readAndMigrateLocalProfile(now)?.profile ?: createFreshProfile(userId, now)
  }

  private fun createFreshProfile(uid: String, nowMs: Long): PlayerProfile {
    return PlayerProfile(
      schemaVersion = PlayerProfile.CURRENT_SCHEMA_VERSION,
      id = uid,
      userId = uid,
      createdAt = nowMs,
      lastActiveAt = nowMs,
      displayName = "Player",
      lastLivesRefillEpochMs = nowMs,
      totalSessions = 0L,
    )
  }

  private fun sanitizeProfileUpdate(
    previous: PlayerProfile,
    candidate: PlayerProfile,
    nowMs: Long,
  ): PlayerProfile {
    val immutableCreatedAt = if (previous.createdAt > 0L) previous.createdAt else nowMs
    return candidate.copy(
      schemaVersion = maxOf(previous.schemaVersion, PlayerProfile.CURRENT_SCHEMA_VERSION),
      id = userId,
      userId = userId,
      createdAt = immutableCreatedAt,
      lastActiveAt = maxOf(immutableCreatedAt, nowMs),
      displayName = candidate.displayName.trim().ifEmpty { "Player" }.take(80),
      classicLevel = candidate.classicLevel.coerceIn(1L, 9999L),
      classicHighestCleared = candidate.classicHighestCleared.coerceIn(0L, 9999L),
      fallStage = candidate.fallStage.coerceIn(1L, 9999L),
      fallHighScore = candidate.fallHighScore.coerceIn(0L, MAX_STAT_CAP),
      selectedSkinId =
        candidate.selectedSkinId.trim().take(40).ifEmpty { PlayerProfile.DEFAULT_SKIN_ID },
      unlockedSkins =
        candidate.unlockedSkins.ifEmpty { PlayerProfile.ALL_SKIN_IDS }.distinct().take(20),
      lives = candidate.lives.coerceIn(0L, PlayerProfile.MAX_LIVES.toLong()),
      lastLivesRefillEpochMs = candidate.lastLivesRefillEpochMs.coerceAtLeast(0L),
      hintsRemaining = candidate.hintsRemaining.coerceIn(0L, 999L),
      totalArrowsCleared = candidate.totalArrowsCleared.coerceIn(0L, MAX_STAT_CAP),
      coins = candidate.coins.coerceIn(0L, MAX_STAT_CAP),
      redeemedPromos = candidate.redeemedPromos.map { it.trim().uppercase() }.filter { it.isNotEmpty() }.distinct().take(50),
      totalSessions = candidate.totalSessions.coerceIn(0L, MAX_STAT_CAP),
      totalPlayTimeSeconds = candidate.totalPlayTimeSeconds.coerceIn(0L, MAX_STAT_CAP),
      rewardedAdsWatched = candidate.rewardedAdsWatched.coerceIn(0L, MAX_STAT_CAP),
      interstitialAdsWatched = candidate.interstitialAdsWatched.coerceIn(0L, MAX_STAT_CAP),
      lastRewardedAdAtMs = candidate.lastRewardedAdAtMs.coerceAtLeast(0L),
      lastInterstitialAdAtMs = candidate.lastInterstitialAdAtMs.coerceAtLeast(0L),
    )
  }

  private fun readAndMigrateLocalProfile(nowMs: Long): DecodedProfileResult? {
    val candidates = listOf(profileFile, profileBackupFile)
    for (candidateFile in candidates) {
      if (!candidateFile.exists() || candidateFile.length() == 0L) continue
      try {
        val atomicFile = AtomicFile(candidateFile)
        val raw = String(atomicFile.readFully(), StandardCharsets.UTF_8)
        if (raw.isBlank()) continue
        return ProfileJsonCodec.decodeAndMigrate(raw, expectedUserId = userId, nowMs = nowMs)
      } catch (e: Exception) {
        Log.w(TAG, "Failed to read profile from ${candidateFile.name}, checking fallback", e)
      }
    }
    return null
  }

  private fun writeLocalProfileAtomically(profile: PlayerProfile) {
    filesDir.mkdirs()
    val jsonBytes = ProfileJsonCodec.encode(profile).toByteArray(StandardCharsets.UTF_8)
    writeAtomicWithSync(profileFile, jsonBytes)
    // Maintain a secondary backup copy after primary atomic swap succeeds
    runCatching { writeAtomicWithSync(profileBackupFile, jsonBytes) }
  }

  private fun readLocalHistorySync(): List<LevelRecord> {
    if (!historyFile.exists() || historyFile.length() == 0L) return emptyList()
    return try {
      val raw = String(AtomicFile(historyFile).readFully(), StandardCharsets.UTF_8)
      ProfileJsonCodec.decodeHistory(raw, userId)
    } catch (e: Exception) {
      Log.w(TAG, "Failed to read local level history", e)
      emptyList()
    }
  }

  private fun writeLocalHistoryAtomically(records: List<LevelRecord>) {
    filesDir.mkdirs()
    val jsonBytes = ProfileJsonCodec.encodeHistory(records).toByteArray(StandardCharsets.UTF_8)
    writeAtomicWithSync(historyFile, jsonBytes)
  }

  private fun writeAtomicWithSync(targetFile: File, bytes: ByteArray) {
    val atomicFile = AtomicFile(targetFile)
    var stream: FileOutputStream? = null
    try {
      stream = atomicFile.startWrite()
      stream.write(bytes)
      stream.flush()
      stream.fd.sync()
      atomicFile.finishWrite(stream)
    } catch (e: Exception) {
      if (stream != null) {
        atomicFile.failWrite(stream)
      }
      throw e
    }
  }

  private fun buildFirestorePayload(profile: PlayerProfile, includeCreatedAt: Boolean): Map<String, Any> {
    val map =
      mutableMapOf<String, Any>(
        "schemaVersion" to profile.schemaVersion.toLong(),
        "userId" to profile.userId,
        "lastActiveAt" to profile.lastActiveAt,
        "displayName" to profile.displayName,
        "classicLevel" to profile.classicLevel,
        "classicHighestCleared" to profile.classicHighestCleared,
        "fallStage" to profile.fallStage,
        "fallHighScore" to profile.fallHighScore,
        "selectedSkinId" to profile.selectedSkinId,
        "unlockedSkins" to profile.unlockedSkins,
        "lives" to profile.lives,
        "lastLivesRefillEpochMs" to profile.lastLivesRefillEpochMs,
        "hintsRemaining" to profile.hintsRemaining,
        "sfxEnabled" to profile.sfxEnabled,
        "bgmEnabled" to profile.bgmEnabled,
        "vibrationEnabled" to profile.vibrationEnabled,
        "darkMode" to profile.darkMode,
        "totalArrowsCleared" to profile.totalArrowsCleared,
        "coins" to profile.coins,
        "redeemedPromos" to profile.redeemedPromos,
        "totalSessions" to profile.totalSessions,
        "totalPlayTimeSeconds" to profile.totalPlayTimeSeconds,
        "rewardedAdsWatched" to profile.rewardedAdsWatched,
        "interstitialAdsWatched" to profile.interstitialAdsWatched,
        "lastRewardedAdAtMs" to profile.lastRewardedAdAtMs,
        "lastInterstitialAdAtMs" to profile.lastInterstitialAdAtMs,
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    if (includeCreatedAt) {
      map["createdAt"] = profile.createdAt
    } else {
      map["createdAt"] = profile.createdAt
    }
    return map
  }

  private fun snapshotToPlayerProfile(
    snapshot: DocumentSnapshot,
    fallbackUserId: String,
    nowMs: Long,
  ): PlayerProfile {
    val schema = (snapshot.getLong("schemaVersion") ?: 0L).toInt()
    val createdAtLong =
      snapshot.getLong("createdAt")
        ?: snapshot.getTimestamp("createdAt")?.toDate()?.time
        ?: nowMs
    val lastActiveAtLong =
      snapshot.getLong("lastActiveAt")
        ?: snapshot.getTimestamp("updatedAt")?.toDate()?.time
        ?: nowMs
    val livesVal = snapshot.getLong("lives") ?: snapshot.getLong("energy") ?: PlayerProfile.MAX_LIVES.toLong()
    val refillVal =
      snapshot.getLong("lastLivesRefillEpochMs")
        ?: snapshot.getLong("lastEnergyRefillEpochMs")
        ?: nowMs
    @Suppress("UNCHECKED_CAST")
    val skins =
      (snapshot.get("unlockedSkins") as? List<*>)?.mapNotNull { it as? String }
        ?: PlayerProfile.ALL_SKIN_IDS
    val promos =
      (snapshot.get("redeemedPromos") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

    return PlayerProfile(
      schemaVersion = maxOf(schema, PlayerProfile.CURRENT_SCHEMA_VERSION),
      id = fallbackUserId,
      userId = fallbackUserId,
      createdAt = createdAtLong,
      lastActiveAt = lastActiveAtLong,
      displayName = snapshot.getString("displayName") ?: "Player",
      classicLevel = snapshot.getLong("classicLevel") ?: 1L,
      classicHighestCleared = snapshot.getLong("classicHighestCleared") ?: 0L,
      fallStage = snapshot.getLong("fallStage") ?: 1L,
      fallHighScore = snapshot.getLong("fallHighScore") ?: 0L,
      selectedSkinId = snapshot.getString("selectedSkinId") ?: PlayerProfile.DEFAULT_SKIN_ID,
      unlockedSkins = skins.ifEmpty { PlayerProfile.ALL_SKIN_IDS },
      lives = livesVal.coerceIn(0L, PlayerProfile.MAX_LIVES.toLong()),
      lastLivesRefillEpochMs = refillVal,
      hintsRemaining = snapshot.getLong("hintsRemaining") ?: 3L,
      sfxEnabled = snapshot.getBoolean("sfxEnabled") ?: true,
      bgmEnabled = snapshot.getBoolean("bgmEnabled") ?: false,
      vibrationEnabled = snapshot.getBoolean("vibrationEnabled") ?: true,
      darkMode = snapshot.getBoolean("darkMode") ?: false,
      totalArrowsCleared = snapshot.getLong("totalArrowsCleared") ?: 0L,
      coins = snapshot.getLong("coins") ?: 50L,
      redeemedPromos = promos,
      totalSessions = snapshot.getLong("totalSessions") ?: 0L,
      totalPlayTimeSeconds = snapshot.getLong("totalPlayTimeSeconds") ?: 0L,
      rewardedAdsWatched = snapshot.getLong("rewardedAdsWatched") ?: 0L,
      interstitialAdsWatched = snapshot.getLong("interstitialAdsWatched") ?: 0L,
      lastRewardedAdAtMs = snapshot.getLong("lastRewardedAdAtMs") ?: 0L,
      lastInterstitialAdAtMs = snapshot.getLong("lastInterstitialAdAtMs") ?: 0L,
    )
  }

  private data class DecodedProfileResult(
    val profile: PlayerProfile,
    val wasMigrated: Boolean,
  )

  private object ProfileJsonCodec {
    fun encode(profile: PlayerProfile): String {
      val root = JSONObject()
      root.put("schemaVersion", profile.schemaVersion)
      root.put("userId", profile.userId)
      root.put("createdAt", profile.createdAt)
      root.put("lastActiveAt", profile.lastActiveAt)
      root.put("displayName", profile.displayName)
      root.put("classicLevel", profile.classicLevel)
      root.put("classicHighestCleared", profile.classicHighestCleared)
      root.put("fallStage", profile.fallStage)
      root.put("fallHighScore", profile.fallHighScore)
      root.put("selectedSkinId", profile.selectedSkinId)
      root.put("unlockedSkins", JSONArray(profile.unlockedSkins))
      root.put("lives", profile.lives)
      root.put("lastLivesRefillEpochMs", profile.lastLivesRefillEpochMs)
      root.put("hintsRemaining", profile.hintsRemaining)
      root.put("sfxEnabled", profile.sfxEnabled)
      root.put("bgmEnabled", profile.bgmEnabled)
      root.put("vibrationEnabled", profile.vibrationEnabled)
      root.put("darkMode", profile.darkMode)
      root.put("totalArrowsCleared", profile.totalArrowsCleared)
      root.put("coins", profile.coins)
      root.put("redeemedPromos", JSONArray(profile.redeemedPromos))
      root.put("totalSessions", profile.totalSessions)
      root.put("totalPlayTimeSeconds", profile.totalPlayTimeSeconds)
      root.put("rewardedAdsWatched", profile.rewardedAdsWatched)
      root.put("interstitialAdsWatched", profile.interstitialAdsWatched)
      root.put("lastRewardedAdAtMs", profile.lastRewardedAdAtMs)
      root.put("lastInterstitialAdAtMs", profile.lastInterstitialAdAtMs)
      return root.toString()
    }

    fun decodeAndMigrate(
      rawJson: String,
      expectedUserId: String,
      nowMs: Long,
    ): DecodedProfileResult {
      val json = JSONObject(rawJson)
      val sourceSchema = json.optInt("schemaVersion", 0)
      var migrated = sourceSchema < PlayerProfile.CURRENT_SCHEMA_VERSION

      // Migrate legacy schema v0 fields ("energy" -> "lives", "lastEnergyRefillEpochMs" -> "lastLivesRefillEpochMs")
      val livesValue =
        if (json.has("lives")) {
          json.optLong("lives", PlayerProfile.MAX_LIVES.toLong())
        } else {
          migrated = true
          json.optLong("energy", PlayerProfile.MAX_LIVES.toLong())
        }
      val refillValue =
        if (json.has("lastLivesRefillEpochMs")) {
          json.optLong("lastLivesRefillEpochMs", nowMs)
        } else {
          migrated = true
          json.optLong("lastEnergyRefillEpochMs", nowMs)
        }

      val createdAtValue = json.optLong("createdAt", nowMs).let { if (it > 0L) it else nowMs }
      val lastActiveValue = json.optLong("lastActiveAt", nowMs).let { if (it > 0L) it else nowMs }

      val skins = mutableListOf<String>()
      val skinsArray = json.optJSONArray("unlockedSkins")
      if (skinsArray != null) {
        for (i in 0 until skinsArray.length()) {
          val s = skinsArray.optString(i).trim()
          if (s.isNotEmpty()) skins.add(s)
        }
      }
      if (skins.isEmpty()) skins.addAll(PlayerProfile.ALL_SKIN_IDS)

      val promos = mutableListOf<String>()
      val promosArray = json.optJSONArray("redeemedPromos")
      if (promosArray != null) {
        for (i in 0 until promosArray.length()) {
          val p = promosArray.optString(i).trim().uppercase()
          if (p.isNotEmpty()) promos.add(p)
        }
      }

      val profile =
        PlayerProfile(
          schemaVersion = PlayerProfile.CURRENT_SCHEMA_VERSION,
          id = expectedUserId,
          userId = expectedUserId,
          createdAt = createdAtValue,
          lastActiveAt = lastActiveValue,
          displayName = json.optString("displayName", "Player").ifBlank { "Player" },
          classicLevel = json.optLong("classicLevel", 1L).coerceIn(1L, 9999L),
          classicHighestCleared = json.optLong("classicHighestCleared", 0L).coerceIn(0L, 9999L),
          fallStage = json.optLong("fallStage", 1L).coerceIn(1L, 9999L),
          fallHighScore = json.optLong("fallHighScore", 0L).coerceAtLeast(0L),
          selectedSkinId = json.optString("selectedSkinId", PlayerProfile.DEFAULT_SKIN_ID),
          unlockedSkins = skins.distinct().take(20),
          lives = livesValue.coerceIn(0L, PlayerProfile.MAX_LIVES.toLong()),
          lastLivesRefillEpochMs = refillValue.coerceAtLeast(0L),
          hintsRemaining = json.optLong("hintsRemaining", 3L).coerceIn(0L, 999L),
          sfxEnabled = json.optBoolean("sfxEnabled", true),
          bgmEnabled = json.optBoolean("bgmEnabled", false),
          vibrationEnabled = json.optBoolean("vibrationEnabled", true),
          darkMode = json.optBoolean("darkMode", false),
          totalArrowsCleared = json.optLong("totalArrowsCleared", 0L).coerceAtLeast(0L),
          coins = json.optLong("coins", 50L).coerceAtLeast(0L),
          redeemedPromos = promos.distinct().take(50),
          totalSessions = json.optLong("totalSessions", 0L).coerceAtLeast(0L),
          totalPlayTimeSeconds = json.optLong("totalPlayTimeSeconds", 0L).coerceAtLeast(0L),
          rewardedAdsWatched = json.optLong("rewardedAdsWatched", 0L).coerceAtLeast(0L),
          interstitialAdsWatched = json.optLong("interstitialAdsWatched", 0L).coerceAtLeast(0L),
          lastRewardedAdAtMs = json.optLong("lastRewardedAdAtMs", 0L).coerceAtLeast(0L),
          lastInterstitialAdAtMs = json.optLong("lastInterstitialAdAtMs", 0L).coerceAtLeast(0L),
        )
      return DecodedProfileResult(profile = profile, wasMigrated = migrated)
    }

    fun encodeHistory(records: List<LevelRecord>): String {
      val array = JSONArray()
      records.forEach { rec ->
        array.put(
          JSONObject().apply {
            put("id", rec.id)
            put("userId", rec.userId)
            put("mode", rec.mode)
            put("levelNumber", rec.levelNumber)
            put("stars", rec.stars)
            put("mistakes", rec.mistakes)
            put("durationSeconds", rec.durationSeconds)
            put("arrowsCleared", rec.arrowsCleared)
            put("createdAtMs", rec.createdAtMs)
          }
        )
      }
      return array.toString()
    }

    fun decodeHistory(raw: String, expectedUserId: String): List<LevelRecord> {
      val array = JSONArray(raw)
      val out = ArrayList<LevelRecord>(array.length())
      for (i in 0 until array.length()) {
        val obj = array.optJSONObject(i) ?: continue
        out.add(
          LevelRecord(
            id = obj.optString("id"),
            userId = expectedUserId,
            mode = obj.optString("mode", "classic"),
            levelNumber = obj.optLong("levelNumber", 1L),
            stars = obj.optLong("stars", 3L),
            mistakes = obj.optLong("mistakes", 0L),
            durationSeconds = obj.optLong("durationSeconds", 0L),
            arrowsCleared = obj.optLong("arrowsCleared", 1L),
            createdAtMs = obj.optLong("createdAtMs", 0L),
          )
        )
      }
      return out
    }
  }

  companion object {
    private const val TAG = "UserRepository"
    private const val MAX_STAT_CAP = 100_000_000L
    private const val REMOTE_FETCH_TIMEOUT_MS = 2500L

    private fun createDefaultFirestore(context: Context): FirebaseFirestore? {
      return try {
        val databaseId = context.applicationContext.getString(R.string.firestore_database_id)
        FirebaseFirestore.getInstance(databaseId)
      } catch (e: Exception) {
        Log.w(TAG, "Firestore unavailable, operating with local atomic profile store only", e)
        null
      }
    }
  }
}
