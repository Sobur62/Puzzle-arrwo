package com.example.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.base.FirestoreEmulatorTestBase
import com.example.security.AnonymousIdentityStore
import com.google.firebase.firestore.FirebaseFirestoreException
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserRepositoryRuleTest : FirestoreEmulatorTestBase() {

  private fun newIsolatedRepo(): Pair<UserRepository, String> {
    val context = ApplicationProvider.getApplicationContext<Context>()
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
    val identityStore = AnonymousIdentityStore(context)
    val uid = identityStore.getOrCreateAnonymousUserId()
    val filesDir = File(context.filesDir, "rule_test_${UUID.randomUUID()}").apply { mkdirs() }
    val repo = UserRepository(filesDir = filesDir, identityStore = identityStore, db = firestore)
    return repo to uid
  }

  @Test
  fun loadOrCreateAndSync_validUuidV4_persistsAndObservesHistory() = runBlocking {
    val (repo, uid) = newIsolatedRepo()
    val profile = withTimeout(DEFAULT_TIMEOUT_MS) { repo.loadOrCreateProfile(incrementSession = true) }
    assertEquals(uid, profile.userId)
    withTimeout(DEFAULT_TIMEOUT_MS) { repo.syncToFirestoreResult().getOrThrow() }

    val snapshot =
      withTimeout(DEFAULT_TIMEOUT_MS) { firestore.collection("users").document(uid).get().await() }
    assertTrue(snapshot.exists())
    assertEquals(uid, snapshot.getString("userId"))

    val recId = "rec_${UUID.randomUUID().toString().replace("-", "")}"
    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.recordLevelWin(
        mode = "classic",
        levelNumber = 1,
        stars = 3,
        mistakes = 0,
        durationSeconds = 18,
        arrowsCleared = 14,
        coinsEarned = 30L,
        scoreEarned = 0,
        isBonusLevel = false,
        entryId = recId,
      )
    }

    val history =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeRecentHistory().first { list -> list.any { it.id == recId } }
      }
    assertTrue(history.any { it.id == recId && it.stars == 3L })
  }

  @Test
  fun usersCollectionList_isDeniedToPreventEnumeration() = runBlocking {
    val (repo, _) = newIsolatedRepo()
    withTimeout(DEFAULT_TIMEOUT_MS) { repo.loadOrCreateProfile(incrementSession = true) }
    withTimeout(DEFAULT_TIMEOUT_MS) { repo.syncToFirestoreNow() }

    val exception =
      try {
        withTimeout(DEFAULT_TIMEOUT_MS) { firestore.collection("users").get().await() }
        null
      } catch (e: Exception) {
        e
      }
    val firestoreException =
      checkNotNull(exception?.unwrapFirestoreException()) {
        "Expected nested FirebaseFirestoreException, got $exception"
      }
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
  }

  private companion object {
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 3000L
  }
}
