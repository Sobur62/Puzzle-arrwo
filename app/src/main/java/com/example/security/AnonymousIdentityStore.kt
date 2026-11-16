package com.example.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.Locale
import java.util.UUID

/**
 * Persists an immutable, privacy-preserving anonymous UUID v4 in EncryptedSharedPreferences.
 * Never collects or derives device identifiers (no Android ID, IMEI, Advertising ID, or email).
 */
class AnonymousIdentityStore(context: Context) {

  private val appContext = context.applicationContext
  private val lock = Any()

  @Volatile private var cachedUserId: String? = null

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
      Log.e(TAG, "EncryptedSharedPreferences init failed for identity store, using fallback", e)
      appContext.getSharedPreferences(PREF_FILE_FALLBACK, Context.MODE_PRIVATE)
    }
  }

  /**
   * Returns the persisted UUID v4 on subsequent launches, or generates and synchronously commits
   * a fresh random UUID v4 on the very first launch.
   */
  fun getOrCreateAnonymousUserId(): String {
    cachedUserId?.let { return it }
    synchronized(lock) {
      cachedUserId?.let { return it }
      val stored = prefs.getString(KEY_ANONYMOUS_USER_ID, null)?.trim()?.lowercase(Locale.US)
      if (stored != null && isValidUuidV4(stored)) {
        cachedUserId = stored
        return stored
      }
      val generated = UUID.randomUUID().toString().lowercase(Locale.US)
      prefs.edit().putString(KEY_ANONYMOUS_USER_ID, generated).commit()
      cachedUserId = generated
      return generated
    }
  }

  /** Returns the existing UUID v4 if already created, or null if not yet initialized. */
  fun peekExistingUserId(): String? {
    cachedUserId?.let { return it }
    synchronized(lock) {
      val stored = prefs.getString(KEY_ANONYMOUS_USER_ID, null)?.trim()?.lowercase(Locale.US)
      return if (stored != null && isValidUuidV4(stored)) {
        cachedUserId = stored
        stored
      } else {
        null
      }
    }
  }

  companion object {
    private const val TAG = "AnonymousIdentityStore"
    private const val PREF_FILE_NAME = "pixel_arrow_anonymous_identity"
    private const val PREF_FILE_FALLBACK = "pixel_arrow_anonymous_identity_fallback"
    private const val KEY_ANONYMOUS_USER_ID = "anonymous_user_uuid_v4"

    private val UUID_V4_REGEX =
      Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")

    fun isValidUuidV4(candidate: String): Boolean =
      UUID_V4_REGEX.matches(candidate.trim().lowercase(Locale.US))
  }
}
