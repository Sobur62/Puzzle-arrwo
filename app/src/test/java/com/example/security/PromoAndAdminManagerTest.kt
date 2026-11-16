package com.example.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class PromoAndAdminManagerTest {

  private lateinit var store: EncryptedAdConfigStore

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    store = EncryptedAdConfigStore(context)
    store.resetFailedAttempts()
  }

  @Test
  fun adminCode_matchesViaSha256Hash_andOpensAdminPanel() {
    assertTrue(PromoAndAdminManager.isAdminSecretMatch("admin_3922"))
    assertFalse(PromoAndAdminManager.isAdminSecretMatch("admin_0000"))

    val result =
      PromoAndAdminManager.evaluateInput(
        rawInput = "admin_3922",
        alreadyRedeemedCodes = emptySet(),
        store = store,
      )
    assertTrue(result is PromoAndAdminManager.CodeCheckResult.OpenAdminPanel)
  }

  @Test
  fun promoCode_redeemsOnceOnly_andBlocksReuse() {
    val first =
      PromoAndAdminManager.evaluateInput(
        rawInput = "PIXEL100",
        alreadyRedeemedCodes = emptySet(),
        store = store,
      )
    assertTrue(first is PromoAndAdminManager.CodeCheckResult.PromoApplied)

    val second =
      PromoAndAdminManager.evaluateInput(
        rawInput = "pixel100",
        alreadyRedeemedCodes = setOf("PIXEL100"),
        store = store,
      )
    assertTrue(second is PromoAndAdminManager.CodeCheckResult.AlreadyRedeemed)
  }

  @Test
  fun fiveConsecutiveInvalidAttempts_triggersFiveMinuteLockout() {
    var now = 1_000_000L
    for (attempt in 1..4) {
      val res =
        PromoAndAdminManager.evaluateInput("WRONG_$attempt", emptySet(), store, nowMs = now)
      assertTrue(res is PromoAndAdminManager.CodeCheckResult.InvalidCode)
      assertEquals(5 - attempt, (res as PromoAndAdminManager.CodeCheckResult.InvalidCode).remainingAttempts)
    }

    val fifth = PromoAndAdminManager.evaluateInput("WRONG_5", emptySet(), store, nowMs = now)
    assertTrue(fifth is PromoAndAdminManager.CodeCheckResult.LockedOut)

    // Even valid admin code is blocked during lockout window
    val duringLock =
      PromoAndAdminManager.evaluateInput("admin_3922", emptySet(), store, nowMs = now + 60_000L)
    assertTrue(duringLock is PromoAndAdminManager.CodeCheckResult.LockedOut)

    // After 5 minutes lockout clears
    val afterLock =
      PromoAndAdminManager.evaluateInput(
        "admin_3922",
        emptySet(),
        store,
        nowMs = now + PromoAndAdminManager.LOCKOUT_DURATION_MS + 10L,
      )
    assertTrue(afterLock is PromoAndAdminManager.CodeCheckResult.OpenAdminPanel)
  }

  @Test
  fun gameIdValidation_allowsOnlySixToTenDigits() {
    assertTrue(PromoAndAdminManager.isValidUnityGameId("5712345"))
    assertFalse(PromoAndAdminManager.isValidUnityGameId("123"))
    assertFalse(PromoAndAdminManager.isValidUnityGameId("5712abc"))
    assertFalse(PromoAndAdminManager.isValidUnityGameId(""))
  }
}
