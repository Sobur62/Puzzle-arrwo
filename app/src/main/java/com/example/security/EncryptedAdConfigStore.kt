package com.example.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.ads.AdPlatform

/**
 * Stores app-level Unity Ads configuration (Game ID, store platform, test mode),
 * GDPR/CCPA privacy consent flags, promo lockout state, and local promo cache in EncryptedSharedPreferences.
 * Intentionally separated from PlayerProfile.
 */
class EncryptedAdConfigStore(context: Context) {

  private val appContext = context.applicationContext

  private val prefs: SharedPreferences by lazy {
    try {
      val masterKey =
        MasterKey.Builder(appContext)
          .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
          .build()
      EncryptedSharedPreferences.create(
        appContext,
        PREF_FILE_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
      )
    } catch (e: Exception) {
      // Safe fallback if Android Keystore is unavailable or reset on a device or test runner
      Log.e(TAG, "EncryptedSharedPreferences init failed, using fallback", e)
      appContext.getSharedPreferences(PREF_FILE_FALLBACK, Context.MODE_PRIVATE)
    }
  }

  /** Reads the saved Unity Game ID, or returns an empty string if none is configured. */
  fun getSavedGameId(): String = prefs.getString(KEY_GAME_ID, "")?.trim().orEmpty()

  /** Reads the configured store platform. */
  fun getPlatform(): AdPlatform = AdPlatform.fromId(prefs.getString(KEY_PLATFORM, AdPlatform.GOOGLE_PLAY.id))

  /** Reads Test Mode flag (defaults to true; switch to false in Admin Panel before release). */
  fun isTestMode(): Boolean = prefs.getBoolean(KEY_TEST_MODE, true)

  /** Persists Unity Game ID, platform, and Test Mode from the Admin Panel. */
  fun saveAdConfiguration(gameId: String, platform: AdPlatform, testMode: Boolean) {
    prefs
      .edit()
      .putString(KEY_GAME_ID, gameId.trim())
      .putString(KEY_PLATFORM, platform.id)
      .putBoolean(KEY_TEST_MODE, testMode)
      .apply()
  }

  /** Reads and persists GDPR and CCPA user consent preferences. */
  fun hasSetConsent(): Boolean = prefs.contains(KEY_GDPR_CONSENT)
  fun getGdprConsent(): Boolean = prefs.getBoolean(KEY_GDPR_CONSENT, true)
  fun getCcpaOptIn(): Boolean = prefs.getBoolean(KEY_CCPA_OPT_IN, true)

  fun saveConsent(gdprConsent: Boolean, ccpaOptIn: Boolean) {
    prefs
      .edit()
      .putBoolean(KEY_GDPR_CONSENT, gdprConsent)
      .putBoolean(KEY_CCPA_OPT_IN, ccpaOptIn)
      .apply()
  }

  /** Manages failed promo code attempt counters and the 5-minute lockout timestamp. */
  fun getFailedAttempts(): Int = prefs.getInt(KEY_FAILED_ATTEMPTS, 0)
  fun getLockoutUntilMs(): Long = prefs.getLong(KEY_LOCKOUT_UNTIL_MS, 0L)

  fun setFailedAttemptState(attempts: Int, lockoutUntilMs: Long) {
    prefs
      .edit()
      .putInt(KEY_FAILED_ATTEMPTS, attempts)
      .putLong(KEY_LOCKOUT_UNTIL_MS, lockoutUntilMs)
      .apply()
  }

  fun resetFailedAttempts() {
    prefs
      .edit()
      .putInt(KEY_FAILED_ATTEMPTS, 0)
      .putLong(KEY_LOCKOUT_UNTIL_MS, 0L)
      .apply()
  }

  /** Local cache of redeemed promo codes to reinforce single-use promo enforcement. */
  fun getLocalRedeemedPromos(): Set<String> =
    prefs.getStringSet(KEY_REDEEMED_PROMOS, emptySet()) ?: emptySet()

  fun markPromoRedeemed(codeUpper: String) {
    val updated = getLocalRedeemedPromos().toMutableSet().apply { add(codeUpper) }
    prefs.edit().putStringSet(KEY_REDEEMED_PROMOS, updated).apply()
  }

  private companion object {
    const val TAG = "EncryptedAdStore"
    const val PREF_FILE_NAME = "pixel_arrow_secure_ad_prefs"
    const val PREF_FILE_FALLBACK = "pixel_arrow_ad_prefs_fallback"
    const val KEY_GAME_ID = "unity_game_id"
    const val KEY_PLATFORM = "ad_platform"
    const val KEY_TEST_MODE = "unity_test_mode"
    const val KEY_GDPR_CONSENT = "gdpr_consent"
    const val KEY_CCPA_OPT_IN = "ccpa_opt_in"
    const val KEY_FAILED_ATTEMPTS = "promo_failed_attempts"
    const val KEY_LOCKOUT_UNTIL_MS = "promo_lockout_until_ms"
    const val KEY_REDEEMED_PROMOS = "local_redeemed_promos"
  }
}
