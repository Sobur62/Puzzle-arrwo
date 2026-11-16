package com.example.game.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ads.AdManager
import com.example.ads.AdPlatform
import com.example.ads.AdRuntimeStatus
import com.example.ads.AdStrategyManager
import com.example.data.model.LevelRecord
import com.example.data.model.PlayerProfile
import com.example.data.repository.UserRepository
import com.example.game.audio.SoundAndHapticsManager
import com.example.game.engine.LevelGenerator
import com.example.game.engine.ProceduralLevelGenerator
import com.example.game.model.ArrowEntity
import com.example.game.model.ArrowSkin
import com.example.game.model.GameMode
import com.example.game.model.GridPoint
import com.example.security.EncryptedAdConfigStore
import com.example.security.PromoAndAdminManager
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class AppScreen {
  HOME,
  PLAYING,
}

data class ExitingArrowState(
  val arrow: ArrowEntity,
  val startedAtMs: Long,
  val durationMs: Long,
)

data class BlockedArrowFeedback(
  val arrowId: Int,
  val blockPoint: GridPoint,
  val startedAtMs: Long,
)

sealed interface LevelResult {
  data class Won(
    val mode: GameMode,
    val levelNumber: Int,
    val stars: Int,
    val mistakes: Int,
    val durationSeconds: Int,
    val arrowsCleared: Int,
    val scoreEarned: Int,
    val coinsEarned: Long,
    val isBonusLevel: Boolean = false,
    val doubleCoinsClaimed: Boolean = false,
  ) : LevelResult

  data class Lost(
    val mode: GameMode,
    val levelNumber: Int,
    val isBonusLevel: Boolean = false,
  ) : LevelResult
}

data class BoardSessionState(
  val mode: GameMode = GameMode.CLASSIC,
  val levelNumber: Int = 1,
  val isBonusLevel: Boolean = false,
  val cols: Int = 6,
  val rows: Int = 6,
  val arrows: List<ArrowEntity> = emptyList(),
  val totalArrows: Int = 0,
  val exitingArrows: List<ExitingArrowState> = emptyList(),
  val gravityDropVersion: Int = 0,
  val gravityDeltas: Map<Int, Int> = emptyMap(),
  val blockedFeedback: BlockedArrowFeedback? = null,
  val hintArrowId: Int? = null,
  val focusPointRequest: Pair<Long, GridPoint>? = null,
  val hearts: Int = 3,
  val mistakes: Int = 0,
  val fallScore: Int = 0,
  val comboCount: Int = 0,
  val elapsedSeconds: Int = 0,
  val result: LevelResult? = null,
)

data class LivesDisplayState(
  val lives: Int = PlayerProfile.MAX_LIVES,
  val refillSecondsRemaining: Int = 0,
)

data class PromoUiState(
  val message: String? = null,
  val isError: Boolean = false,
  val lockoutSecondsRemaining: Int = 0,
)

/**
 * Primary ViewModel coordinating gameplay state, Unity Ads strategy, promo/admin verification,
 * and anonymous player progression exclusively via [UserRepository].
 */
class ArrowPuzzleViewModel(
  private val repository: UserRepository,
  private val soundManager: SoundAndHapticsManager,
  private val adManager: AdManager,
  private val adStrategy: AdStrategyManager,
  private val adConfigStore: EncryptedAdConfigStore,
  private val levelGenerator: LevelGenerator = ProceduralLevelGenerator(),
) : ViewModel() {

  private val _screen = MutableStateFlow(AppScreen.HOME)
  val screen: StateFlow<AppScreen> = _screen.asStateFlow()

  private val _boardState = MutableStateFlow(BoardSessionState())
  val boardState: StateFlow<BoardSessionState> = _boardState.asStateFlow()

  private val _livesState = MutableStateFlow(LivesDisplayState())
  val livesState: StateFlow<LivesDisplayState> = _livesState.asStateFlow()

  private val _promoState = MutableStateFlow(PromoUiState())
  val promoState: StateFlow<PromoUiState> = _promoState.asStateFlow()

  private val _openAdminEvent = MutableStateFlow(false)
  val openAdminEvent: StateFlow<Boolean> = _openAdminEvent.asStateFlow()

  private val _showConsentDialog = MutableStateFlow(!adConfigStore.hasSetConsent())
  val showConsentDialog: StateFlow<Boolean> = _showConsentDialog.asStateFlow()

  private val _statusBanner = MutableStateFlow<String?>(null)
  val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

  val adStatus: StateFlow<AdRuntimeStatus> = adManager.status

  val profileState: StateFlow<PlayerProfile> = repository.profileFlow

  val recentHistoryState: StateFlow<List<LevelRecord>> =
    repository
      .observeRecentHistory(limitCount = 25L)
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        Log.w(TAG, "Error observing level history", firestoreError ?: error)
        emit(emptyList())
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList(),
      )

  private var timerJob: Job? = null
  private var isForegroundActive: Boolean = true
  private var accumulatedPlaySeconds: Long = 0L

  init {
    viewModelScope.launch {
      runCatching { repository.loadOrCreateProfile(incrementSession = true) }
        .onFailure { Log.w(TAG, "Failed to load or initialize player profile", it) }
    }

    viewModelScope.launch {
      profileState.collect { profile ->
        soundManager.sfxEnabled = profile.sfxEnabled
        soundManager.vibrationEnabled = profile.vibrationEnabled
        soundManager.setBgmActive(profile.bgmEnabled)
        reconcileLives(profile)
      }
    }

    // Periodic 1-second loop for life refill countdown, promo lockout timer, and playtime tracking
    viewModelScope.launch {
      while (isActive) {
        delay(1000L)
        reconcileLives(profileState.value)

        if (isForegroundActive) {
          accumulatedPlaySeconds++
          if (accumulatedPlaySeconds >= PLAY_TIME_FLUSH_INTERVAL_SEC) {
            val delta = accumulatedPlaySeconds
            accumulatedPlaySeconds = 0L
            runCatching { repository.addPlayTimeSeconds(delta) }
          }
        }

        val lockUntil = adConfigStore.getLockoutUntilMs()
        val now = System.currentTimeMillis()
        if (lockUntil > now) {
          val rem = ((lockUntil - now) / 1000L).toInt().coerceAtLeast(1)
          _promoState.update { it.copy(lockoutSecondsRemaining = rem) }
        } else if (_promoState.value.lockoutSecondsRemaining > 0) {
          _promoState.update { it.copy(lockoutSecondsRemaining = 0, message = null) }
        }
      }
    }
  }

  fun onAppForegroundChanged(inForeground: Boolean) {
    isForegroundActive = inForeground
    if (!inForeground) {
      flushAccumulatedPlayTime()
    }
  }

  private fun flushAccumulatedPlayTime() {
    val delta = accumulatedPlaySeconds
    if (delta <= 0L) return
    accumulatedPlaySeconds = 0L
    viewModelScope.launch {
      runCatching { repository.addPlayTimeSeconds(delta) }
    }
  }

  private fun reconcileLives(profile: PlayerProfile) {
    val currentLives = profile.lives.toInt().coerceIn(0, PlayerProfile.MAX_LIVES)
    if (currentLives >= PlayerProfile.MAX_LIVES) {
      _livesState.value =
        LivesDisplayState(lives = PlayerProfile.MAX_LIVES, refillSecondsRemaining = 0)
      return
    }
    val nowMs = System.currentTimeMillis()
    val anchorMs =
      if (profile.lastLivesRefillEpochMs > 0L) profile.lastLivesRefillEpochMs else nowMs
    val elapsedMs = (nowMs - anchorMs).coerceAtLeast(0L)
    val gained = (elapsedMs / LIVES_REFILL_MS).toInt()
    if (gained > 0) {
      val newLives = (currentLives + gained).coerceAtMost(PlayerProfile.MAX_LIVES)
      val newAnchor =
        if (newLives >= PlayerProfile.MAX_LIVES) nowMs else anchorMs + gained * LIVES_REFILL_MS
      _livesState.value =
        LivesDisplayState(
          lives = newLives,
          refillSecondsRemaining =
            if (newLives >= PlayerProfile.MAX_LIVES) 0
            else ((LIVES_REFILL_MS - (nowMs - newAnchor)) / 1000L).toInt().coerceAtLeast(1),
        )
      viewModelScope.launch {
        runCatching { repository.reconcileLives(newLives.toLong(), newAnchor) }
      }
    } else {
      val remSeconds = ((LIVES_REFILL_MS - elapsedMs) / 1000L).toInt().coerceAtLeast(1)
      _livesState.value =
        LivesDisplayState(lives = currentLives, refillSecondsRemaining = remSeconds)
    }
  }

  fun startClassicLevel(levelOverride: Int? = null) {
    val level = (levelOverride ?: profileState.value.classicLevel.toInt()).coerceAtLeast(1)
    launchBoard(GameMode.CLASSIC, level, carryFallScore = 0, isBonusLevel = false)
  }

  fun startFallMode(stageOverride: Int? = null, carryFallScore: Int = 0) {
    val stage = (stageOverride ?: profileState.value.fallStage.toInt()).coerceAtLeast(1)
    launchBoard(GameMode.FALL, stage, carryFallScore = carryFallScore, isBonusLevel = false)
  }

  /** Launches a Bonus Level (unlockable via Rewarded Ad or coins). */
  fun startBonusLevel() {
    val bonusStage = (profileState.value.classicLevel.toInt() + 7).coerceAtLeast(8)
    launchBoard(GameMode.CLASSIC, bonusStage, carryFallScore = 0, isBonusLevel = true)
  }

  fun unlockBonusLevelWithRewardedAd() {
    val rewardedId = adStrategy.getRewardedPlacementId()
    if (adManager.isReady(rewardedId)) {
      adManager.showAd(
        placementId = rewardedId,
        onComplete = { rewarded ->
          if (rewarded) {
            recordRewardedAdWatched()
            startBonusLevel()
          } else {
            _statusBanner.value = "Complete the ad to unlock the Bonus Level!"
          }
        },
        onUnavailable = {
          // Graceful fallback so players are never blocked when inventory is unavailable
          _statusBanner.value = "Ad unavailable right now — Bonus Level opened!"
          startBonusLevel()
        },
      )
    } else {
      // Trigger background preload and fall back to coin unlock or complimentary play
      adManager.loadAd(rewardedId)
      val coins = profileState.value.coins
      if (coins >= 25L) {
        viewModelScope.launch {
          runCatching { repository.spendCoins(25L) }
        }
        _statusBanner.value = "Ad loading in background · Unlocked Bonus Level (-25 Coins)"
        startBonusLevel()
      } else {
        _statusBanner.value = "Bonus Level opened! (Ad loading in background)"
        startBonusLevel()
      }
    }
  }

  private fun launchBoard(
    mode: GameMode,
    levelNumber: Int,
    carryFallScore: Int,
    isBonusLevel: Boolean,
  ) {
    soundManager.playClickSfx()
    val puzzle =
      levelGenerator.generateLevel(
        mode = mode,
        levelNumber = levelNumber,
        isBonusLevel = isBonusLevel,
      )
    _boardState.value =
      BoardSessionState(
        mode = mode,
        levelNumber = levelNumber,
        isBonusLevel = isBonusLevel,
        cols = puzzle.cols,
        rows = puzzle.rows,
        arrows = puzzle.arrows,
        totalArrows = puzzle.arrows.size,
        hearts = 3,
        mistakes = 0,
        fallScore = carryFallScore,
      )
    _screen.value = AppScreen.PLAYING
    startLevelTimer()
  }

  private fun startLevelTimer() {
    timerJob?.cancel()
    timerJob =
      viewModelScope.launch {
        while (isActive) {
          delay(1000L)
          val current = _boardState.value
          if (_screen.value == AppScreen.PLAYING && current.result == null) {
            _boardState.update { it.copy(elapsedSeconds = it.elapsedSeconds + 1) }
          }
        }
      }
  }

  fun goHome() {
    soundManager.playClickSfx()
    timerJob?.cancel()
    flushAccumulatedPlayTime()
    _screen.value = AppScreen.HOME
  }

  fun restartCurrentLevel() {
    val current = _boardState.value
    launchBoard(
      mode = current.mode,
      levelNumber = current.levelNumber,
      carryFallScore = 0,
      isBonusLevel = current.isBonusLevel,
    )
  }

  fun onArrowTapped(arrowId: Int) {
    val state = _boardState.value
    if (state.result != null) return
    val arrow = state.arrows.firstOrNull { it.id == arrowId } ?: return

    val blockingPoint =
      levelGenerator.findBlockingPoint(arrow, state.arrows, state.cols, state.rows)

    if (blockingPoint == null) {
      soundManager.playEscapeSfx()
      val remainingAfterRemove = state.arrows.filter { it.id != arrowId }
      val nowMs = System.currentTimeMillis()
      val exitDurationMs = (260L + arrow.cells.size * 18L).coerceIn(280L, 580L)
      val exitingItem =
        ExitingArrowState(arrow = arrow, startedAtMs = nowMs, durationMs = exitDurationMs)

      val nextCombo = state.comboCount + 1
      val pointsAdded =
        if (state.mode == GameMode.FALL) 10 + (nextCombo * 5).coerceAtMost(50) else 0
      val nextScore = state.fallScore + pointsAdded

      val finalRemaining: List<ArrowEntity>
      val dropDeltas = HashMap<Int, Int>()
      var nextDropVersion = state.gravityDropVersion

      if (state.mode == GameMode.FALL && remainingAfterRemove.isNotEmpty()) {
        val afterGravity = levelGenerator.applyGravity(remainingAfterRemove, state.rows)
        val prevTopY = remainingAfterRemove.associate { it.id to it.cells.minOf { c -> c.y } }
        var anyFell = false
        for (moved in afterGravity) {
          val oldY = prevTopY[moved.id] ?: continue
          val newY = moved.cells.minOf { it.y }
          val delta = newY - oldY
          if (delta > 0) {
            dropDeltas[moved.id] = delta
            anyFell = true
          }
        }
        if (anyFell) {
          nextDropVersion++
          soundManager.playGravityDropSfx()
        }
        finalRemaining = afterGravity
      } else {
        finalRemaining = remainingAfterRemove
      }

      val cleanedExiting =
        (state.exitingArrows.filter { nowMs - it.startedAtMs < it.durationMs + 150L }) +
          exitingItem

      if (finalRemaining.isEmpty()) {
        timerJob?.cancel()
        soundManager.playWinSfx()
        adStrategy.onLevelCleared()

        val stars =
          when {
            state.mistakes == 0 -> 3
            state.mistakes == 1 -> 2
            else -> 1
          }
        val baseCoins = if (state.isBonusLevel) 60L else (15L + stars * 5L)
        val stageBonus = if (state.mode == GameMode.FALL) 100 + state.levelNumber * 25 else 0
        val finalScore = nextScore + stageBonus

        val result =
          LevelResult.Won(
            mode = state.mode,
            levelNumber = state.levelNumber,
            stars = stars,
            mistakes = state.mistakes,
            durationSeconds = state.elapsedSeconds,
            arrowsCleared = state.totalArrows,
            scoreEarned = finalScore,
            coinsEarned = baseCoins,
            isBonusLevel = state.isBonusLevel,
          )
        _boardState.update {
          it.copy(
            arrows = emptyList(),
            exitingArrows = cleanedExiting,
            hintArrowId = null,
            blockedFeedback = null,
            fallScore = finalScore,
            comboCount = nextCombo,
            result = result,
          )
        }
        persistLevelWin(result)
      } else {
        _boardState.update {
          it.copy(
            arrows = finalRemaining,
            exitingArrows = cleanedExiting,
            gravityDropVersion = nextDropVersion,
            gravityDeltas = dropDeltas,
            hintArrowId = if (it.hintArrowId == arrowId) null else it.hintArrowId,
            blockedFeedback = null,
            fallScore = nextScore,
            comboCount = nextCombo,
          )
        }
      }
    } else {
      soundManager.playBlockedSfx()
      val newHearts = (state.hearts - 1).coerceAtLeast(0)
      val newMistakes = state.mistakes + 1
      val nowMs = System.currentTimeMillis()
      val feedback =
        BlockedArrowFeedback(arrowId = arrowId, blockPoint = blockingPoint, startedAtMs = nowMs)

      if (newHearts <= 0) {
        timerJob?.cancel()
        consumeOneLifeOnLoss()
        _boardState.update {
          it.copy(
            hearts = 0,
            mistakes = newMistakes,
            comboCount = 0,
            blockedFeedback = feedback,
            result =
              LevelResult.Lost(
                mode = it.mode,
                levelNumber = it.levelNumber,
                isBonusLevel = it.isBonusLevel,
              ),
          )
        }
      } else {
        _boardState.update {
          it.copy(
            hearts = newHearts,
            mistakes = newMistakes,
            comboCount = 0,
            blockedFeedback = feedback,
          )
        }
      }
    }
  }

  private fun consumeOneLifeOnLoss() {
    viewModelScope.launch {
      runCatching { repository.consumeLifeOnLoss() }
        .onFailure { Log.w(TAG, "Failed to update lives on loss", it) }
    }
  }

  private fun persistLevelWin(win: LevelResult.Won) {
    viewModelScope.launch {
      runCatching {
        repository.recordLevelWin(
          mode = if (win.mode == GameMode.CLASSIC) "classic" else "fall",
          levelNumber = win.levelNumber,
          stars = win.stars,
          mistakes = win.mistakes,
          durationSeconds = win.durationSeconds,
          arrowsCleared = win.arrowsCleared,
          coinsEarned = win.coinsEarned,
          scoreEarned = win.scoreEarned,
          isBonusLevel = win.isBonusLevel,
        )
      }.onFailure { Log.w(TAG, "Failed to persist level completion", it) }
    }
  }

  /** Claims 2x coins via Rewarded Ad (+25 bonus coins during long sessions). */
  fun claimDoubleCoinsViaRewardedAd() {
    val won = _boardState.value.result as? LevelResult.Won ?: return
    if (won.doubleCoinsClaimed) return

    val sessionBoost = if (adStrategy.isLongSessionBoostActive(profileState.value)) 25L else 0L
    val extraCoins = won.coinsEarned + sessionBoost
    val rewardedId = adStrategy.getRewardedPlacementId()

    if (adManager.isReady(rewardedId)) {
      adManager.showAd(
        placementId = rewardedId,
        onComplete = { rewarded ->
          if (rewarded) {
            recordRewardedAdWatched()
            grantDoubleCoins(won, extraCoins)
          }
        },
        onUnavailable = {
          _statusBanner.value = "Ad unavailable right now — retrying in background."
        },
      )
    } else {
      adManager.loadAd(rewardedId)
      _statusBanner.value = "Ad not ready yet — retrying in background."
    }
  }

  private fun grantDoubleCoins(won: LevelResult.Won, extraCoins: Long) {
    _boardState.update {
      it.copy(
        result =
          won.copy(
            coinsEarned = won.coinsEarned + extraCoins,
            doubleCoinsClaimed = true,
          )
      )
    }
    viewModelScope.launch {
      runCatching { repository.grantCoins(extraCoins) }
    }
  }

  /** Revives with +1 heart via Rewarded Ad (graceful coin or free fallback if unavailable). */
  fun reviveWithRewardedAd() {
    val state = _boardState.value
    if (state.result !is LevelResult.Lost) return
    val rewardedId = adStrategy.getRewardedPlacementId()

    if (adManager.isReady(rewardedId)) {
      adManager.showAd(
        placementId = rewardedId,
        onComplete = { rewarded ->
          if (rewarded) {
            recordRewardedAdWatched()
            applyReviveHeart()
          }
        },
        onUnavailable = {
          _statusBanner.value = "Ad unavailable — Free Revive granted!"
          applyReviveHeart()
        },
      )
    } else {
      adManager.loadAd(rewardedId)
      val coins = profileState.value.coins
      if (coins >= 20L) {
        viewModelScope.launch {
          runCatching { repository.spendCoins(20L) }
        }
        _statusBanner.value = "Ad loading · Revived using 20 Coins"
        applyReviveHeart()
      } else {
        _statusBanner.value = "Ad unavailable · Free emergency revive granted!"
        applyReviveHeart()
      }
    }
  }

  private fun applyReviveHeart() {
    soundManager.playClickSfx()
    _boardState.update {
      it.copy(hearts = 1, blockedFeedback = null, result = null)
    }
    startLevelTimer()
  }

  /**
   * Advances to the next level, checking interstitial frequency capping & 90-second cooldown
   * backed by the persisted [PlayerProfile] timestamps.
   */
  fun advanceToNextLevel() {
    val state = _boardState.value
    val goNext = {
      if (state.isBonusLevel) {
        launchBoard(
          GameMode.CLASSIC,
          profileState.value.classicLevel.toInt(),
          carryFallScore = 0,
          isBonusLevel = false,
        )
      } else if (state.mode == GameMode.CLASSIC) {
        launchBoard(GameMode.CLASSIC, state.levelNumber + 1, carryFallScore = 0, isBonusLevel = false)
      } else {
        launchBoard(
          GameMode.FALL,
          state.levelNumber + 1,
          carryFallScore = state.fallScore,
          isBonusLevel = false,
        )
      }
    }

    if (adStrategy.shouldShowInterstitialOnLevelComplete(profileState.value)) {
      val interstitialId = adStrategy.getInterstitialPlacementId()
      adManager.showAd(
        placementId = interstitialId,
        onComplete = {
          recordInterstitialAdWatched()
          goNext()
        },
        onUnavailable = { goNext() },
      )
    } else {
      goNext()
    }
  }

  fun triggerHint() {
    val state = _boardState.value
    if (state.result != null || state.arrows.isEmpty()) return

    val currentHints = profileState.value.hintsRemaining
    if (currentHints > 0L) {
      revealHintArrow(state, decrementHint = true)
      return
    }

    // Attempt Rewarded Ad for a free hint when charges are empty; gracefully grant if unavailable
    val rewardedId = adStrategy.getRewardedPlacementId()
    if (adManager.isReady(rewardedId)) {
      adManager.showAd(
        placementId = rewardedId,
        onComplete = { rewarded ->
          if (rewarded) {
            recordRewardedAdWatched()
            revealHintArrow(state, decrementHint = false)
          }
        },
        onUnavailable = {
          _statusBanner.value = "Ad unavailable · Free Hint granted!"
          revealHintArrow(state, decrementHint = false)
        },
      )
    } else {
      adManager.loadAd(rewardedId)
      _statusBanner.value = "Free Hint granted! (Ad preloading)"
      revealHintArrow(state, decrementHint = false)
    }
  }

  private fun revealHintArrow(state: BoardSessionState, decrementHint: Boolean) {
    val hintArrow =
      levelGenerator.findHintArrow(state.mode, state.arrows, state.cols, state.rows)
        ?: return
    soundManager.playHintSfx()
    val nowMs = System.currentTimeMillis()
    _boardState.update {
      it.copy(
        hintArrowId = hintArrow.id,
        focusPointRequest = nowMs to hintArrow.head,
      )
    }
    if (decrementHint) {
      viewModelScope.launch {
        runCatching { repository.decrementHint() }
      }
    }
  }

  private fun recordRewardedAdWatched() {
    adStrategy.onRewardedAdCompleted()
    viewModelScope.launch {
      runCatching { repository.recordAdWatched(isRewarded = true) }
    }
  }

  private fun recordInterstitialAdWatched() {
    adStrategy.markInterstitialShown()
    viewModelScope.launch {
      runCatching { repository.recordAdWatched(isRewarded = false) }
    }
  }

  /** Verifies promo codes or the secret SHA-256 admin panel code. */
  fun submitPromoOrAdminCode(rawInput: String) {
    val profile = profileState.value
    val result =
      PromoAndAdminManager.evaluateInput(
        rawInput = rawInput,
        alreadyRedeemedCodes = profile.redeemedPromos.toSet(),
        store = adConfigStore,
      )

    when (result) {
      is PromoAndAdminManager.CodeCheckResult.OpenAdminPanel -> {
        soundManager.playHintSfx()
        _promoState.value = PromoUiState(message = "Admin access verified", isError = false)
        _openAdminEvent.value = true
      }
      is PromoAndAdminManager.CodeCheckResult.PromoApplied -> {
        val reward = result.reward
        viewModelScope.launch {
          val applied =
            runCatching {
              repository.redeemPromoAtomic(
                codeUpper = reward.code,
                rewardCoins = reward.coins,
                rewardHints = reward.hints,
              )
            }.getOrDefault(false)

          if (applied) {
            soundManager.playWinSfx()
            _promoState.value =
              PromoUiState(message = "Promo applied! ${reward.description}", isError = false)
          } else {
            soundManager.playBlockedSfx()
            _promoState.value =
              PromoUiState(message = "Code ${reward.code} has already been redeemed.", isError = true)
          }
        }
      }
      is PromoAndAdminManager.CodeCheckResult.AlreadyRedeemed -> {
        soundManager.playBlockedSfx()
        _promoState.value =
          PromoUiState(message = "Code ${result.code} has already been redeemed.", isError = true)
      }
      is PromoAndAdminManager.CodeCheckResult.InvalidCode -> {
        soundManager.playBlockedSfx()
        _promoState.value =
          PromoUiState(
            message = "Invalid promo code. ${result.remainingAttempts} attempts left.",
            isError = true,
          )
      }
      is PromoAndAdminManager.CodeCheckResult.LockedOut -> {
        soundManager.playBlockedSfx()
        _promoState.value =
          PromoUiState(
            message = "Too many invalid attempts. Locked for 5 minutes.",
            isError = true,
            lockoutSecondsRemaining = result.remainingSeconds,
          )
      }
    }
  }

  fun clearPromoMessage() {
    _promoState.update { it.copy(message = null, isError = false) }
  }

  fun consumeAdminOpenEvent() {
    _openAdminEvent.value = false
  }

  /** Saves app-level Unity Ads config from Admin Panel and re-initializes the SDK. */
  fun saveAdminAdConfig(gameId: String, platform: AdPlatform, testMode: Boolean): Boolean {
    if (!PromoAndAdminManager.isValidUnityGameId(gameId)) {
      return false
    }
    adConfigStore.saveAdConfiguration(gameId = gameId.trim(), platform = platform, testMode = testMode)
    adManager.initializeFromSavedConfig()
    _statusBanner.value = "Saved Game ID ${gameId.trim()} (${platform.displayName}, Test=$testMode)"
    return true
  }

  /** Saves GDPR / CCPA privacy consent metadata. */
  fun savePrivacyConsent(gdprConsent: Boolean, ccpaOptIn: Boolean) {
    adManager.applyPrivacyConsent(gdprConsent, ccpaOptIn)
    _showConsentDialog.value = false
  }

  fun openPrivacyConsentDialog() {
    _showConsentDialog.value = true
  }

  fun isLongSessionBoostActive(): Boolean = adStrategy.isLongSessionBoostActive(profileState.value)
  fun getBannerPlacementId(): String = adStrategy.getBannerPlacementId()
  fun getSessionSummary(): String = adStrategy.getSessionSummary(profileState.value)

  fun refillLivesFree() {
    soundManager.playHintSfx()
    viewModelScope.launch {
      runCatching { repository.refillLivesFull() }
    }
  }

  fun selectSkin(skin: ArrowSkin) {
    soundManager.playClickSfx()
    viewModelScope.launch {
      runCatching { repository.selectSkin(skin.id) }
        .onFailure { Log.w(TAG, "Failed to update skin", it) }
    }
  }

  fun setSfxEnabled(enabled: Boolean) {
    soundManager.sfxEnabled = enabled
    if (enabled) soundManager.playClickSfx()
    viewModelScope.launch { runCatching { repository.setSfxEnabled(enabled) } }
  }

  fun setBgmEnabled(enabled: Boolean) {
    soundManager.playClickSfx()
    soundManager.setBgmActive(enabled)
    viewModelScope.launch { runCatching { repository.setBgmEnabled(enabled) } }
  }

  fun setVibrationEnabled(enabled: Boolean) {
    soundManager.vibrationEnabled = enabled
    soundManager.playClickSfx()
    viewModelScope.launch { runCatching { repository.setVibrationEnabled(enabled) } }
  }

  fun setDarkMode(enabled: Boolean) {
    soundManager.playClickSfx()
    viewModelScope.launch { runCatching { repository.setDarkMode(enabled) } }
  }

  fun dismissBanner() {
    _statusBanner.value = null
  }

  override fun onCleared() {
    flushAccumulatedPlayTime()
    super.onCleared()
  }

  private companion object {
    const val TAG = "PixelArrowVM"
    const val LIVES_REFILL_MS = 5 * 60 * 1000L
    const val PLAY_TIME_FLUSH_INTERVAL_SEC = 10L
  }
}
