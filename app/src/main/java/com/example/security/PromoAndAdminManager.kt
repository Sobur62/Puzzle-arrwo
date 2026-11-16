package com.example.security

import java.security.MessageDigest

/**
 * Security manager for promo code validation and hidden Admin Panel verification.
 */
object PromoAndAdminManager {

  // SHA-256 digest of the secret admin code (never stored in plaintext or editable from UI)
  private const val ADMIN_SECRET_SHA256_HEX =
    "80b069fd647615df30849f338d75b4f15600ffa55fb19729091a93485728e224"

  const val MAX_FAILED_ATTEMPTS = 5
  const val LOCKOUT_DURATION_MS = 5 * 60 * 1000L // 5-minute lockout

  data class PromoReward(
    val code: String,
    val coins: Long,
    val hints: Long,
    val description: String,
  )

  // Configured promo code catalog
  private val promoCatalog =
    mapOf(
      "PIXEL100" to PromoReward("PIXEL100", coins = 100L, hints = 0L, description = "+100 Pixel Coins"),
      "ARROW50" to PromoReward("ARROW50", coins = 50L, hints = 1L, description = "+50 Coins & +1 Hint"),
      "NEON2026" to PromoReward("NEON2026", coins = 150L, hints = 1L, description = "+150 Coins & +1 Hint"),
      "BONUS777" to PromoReward("BONUS777", coins = 200L, hints = 2L, description = "+200 Coins & +2 Hints"),
    )

  sealed interface CodeCheckResult {
    data object OpenAdminPanel : CodeCheckResult
    data class PromoApplied(val reward: PromoReward) : CodeCheckResult
    data class AlreadyRedeemed(val code: String) : CodeCheckResult
    data class InvalidCode(val remainingAttempts: Int) : CodeCheckResult
    data class LockedOut(val remainingSeconds: Int) : CodeCheckResult
  }

  /** Computes the lowercase hex SHA-256 hash of the given string. */
  fun sha256Hex(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
  }

  /** Verifies the admin secret code using constant-time byte comparison. */
  fun isAdminSecretMatch(rawInput: String): Boolean {
    val candidateHash = sha256Hex(rawInput.trim()).toByteArray(Charsets.UTF_8)
    val expectedHash = ADMIN_SECRET_SHA256_HEX.toByteArray(Charsets.UTF_8)
    return MessageDigest.isEqual(candidateHash, expectedHash)
  }

  /** Validates that a Unity Game ID consists strictly of 6 to 10 numeric digits. */
  fun isValidUnityGameId(gameId: String): Boolean {
    val trimmed = gameId.trim()
    return trimmed.length in 6..10 && trimmed.all { it.isDigit() }
  }

  /** Evaluates a promo or admin code with a 5-attempt / 5-minute lockout policy. */
  fun evaluateInput(
    rawInput: String,
    alreadyRedeemedCodes: Set<String>,
    store: EncryptedAdConfigStore,
    nowMs: Long = System.currentTimeMillis(),
  ): CodeCheckResult {
    val lockoutUntil = store.getLockoutUntilMs()
    if (lockoutUntil > nowMs) {
      val remSec = ((lockoutUntil - nowMs) / 1000L).toInt().coerceAtLeast(1)
      return CodeCheckResult.LockedOut(remSec)
    }

    val trimmed = rawInput.trim()
    if (trimmed.isEmpty()) {
      return CodeCheckResult.InvalidCode(
        remainingAttempts = (MAX_FAILED_ATTEMPTS - store.getFailedAttempts()).coerceAtLeast(1)
      )
    }

    // 1. Check against SHA-256 admin secret hash first
    if (isAdminSecretMatch(trimmed)) {
      store.resetFailedAttempts()
      return CodeCheckResult.OpenAdminPanel
    }

    // 2. Match against single-use promo codes (case-insensitive)
    val upper = trimmed.uppercase()
    val reward = promoCatalog[upper]
    if (reward != null) {
      if (upper in alreadyRedeemedCodes || upper in store.getLocalRedeemedPromos()) {
        return CodeCheckResult.AlreadyRedeemed(upper)
      }
      store.resetFailedAttempts()
      store.markPromoRedeemed(upper)
      return CodeCheckResult.PromoApplied(reward)
    }

    // 3. Invalid code: increment failed attempts and trigger 5-minute lockout on 5th miss
    val nextAttempts = store.getFailedAttempts() + 1
    return if (nextAttempts >= MAX_FAILED_ATTEMPTS) {
      val until = nowMs + LOCKOUT_DURATION_MS
      store.setFailedAttemptState(0, until)
      CodeCheckResult.LockedOut((LOCKOUT_DURATION_MS / 1000L).toInt())
    } else {
      store.setFailedAttemptState(nextAttempts, 0L)
      CodeCheckResult.InvalidCode(remainingAttempts = MAX_FAILED_ATTEMPTS - nextAttempts)
    }
  }
}
