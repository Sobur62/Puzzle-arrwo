package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ads.AdConfig
import com.example.ads.AdPlatform
import com.example.ads.AdRuntimeStatus
import com.example.game.viewmodel.PromoUiState
import com.example.security.PromoAndAdminManager
import com.example.ui.theme.ArcadeGold
import com.example.ui.theme.GlassAccentBlue
import com.example.ui.theme.GlassAccentMint
import com.example.ui.theme.GlassGoldWarm
import com.example.ui.theme.GlassInkPrimary
import com.example.ui.theme.GlassInkSecondary
import com.example.ui.theme.GlassRose
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonLime
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.PixelCardDark

/** Promo Code Redemption Dialog supporting White Glassmorphism and Dark Neon themes. */
@Composable
fun PromoCodeDialog(
  promoState: PromoUiState,
  darkTheme: Boolean,
  onSubmitCode: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  var codeText by remember { mutableStateOf("") }
  @Suppress("DEPRECATION")
  val clipboardManager = LocalClipboardManager.current
  val isLockedOut = promoState.lockoutSecondsRemaining > 0
  val inkPrimary = if (darkTheme) Color.White else GlassInkPrimary
  val inkSecondary = if (darkTheme) Color(0xFFB5C4EC) else GlassInkSecondary

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 22.dp)
        .glassPanel(darkTheme = darkTheme, cornerRadius = 24.dp, elevation = 18.dp, highOpacity = true)
        .padding(18.dp)
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.fillMaxWidth()) {
          Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Default.ConfirmationNumber,
              contentDescription = null,
              tint = if (darkTheme) ArcadeGold else GlassGoldWarm,
              modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(8.dp))
            if (darkTheme) {
              OutlinedGameText(
                text = "PROMO CODE",
                fontSize = 20.sp,
                fillColor = Color.White,
                outlineColor = Color(0xFF0A142E),
              )
            } else {
              Text(
                text = "PROMO CODE",
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 20.sp),
                color = GlassInkPrimary,
              )
            }
          }
          IconButton(
            onClick = onDismiss,
            modifier = Modifier
              .align(Alignment.CenterEnd)
              .size(48.dp)
              .testTag("promo_close_button"),
          ) {
            Icon(Icons.Default.Close, contentDescription = "Close Promo", tint = inkPrimary)
          }
        }

        Spacer(Modifier.height(10.dp))

        GlassInsetCard(
          darkTheme = darkTheme,
          cornerRadius = 16.dp,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Text(
              text = "Enter or paste a reward promo code to claim Pixel Coins & Hints.",
              style = MaterialTheme.typography.bodyMedium,
              color = inkSecondary,
            )

            OutlinedTextField(
              value = codeText,
              onValueChange = { codeText = it },
              singleLine = true,
              enabled = !isLockedOut,
              label = { Text("Promo Code") },
              placeholder = { Text("Enter code...") },
              trailingIcon = {
                IconButton(
                  onClick = {
                    val clip = clipboardManager.getText()?.text
                    if (!clip.isNullOrBlank()) {
                      codeText = clip.trim()
                    }
                  },
                  enabled = !isLockedOut,
                  modifier = Modifier.testTag("promo_paste_button"),
                ) {
                  Icon(
                    imageVector = Icons.Default.ContentPaste,
                    contentDescription = "Paste Code",
                    tint = if (darkTheme) NeonCyan else GlassAccentBlue,
                  )
                }
              },
              keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
              colors =
                OutlinedTextFieldDefaults.colors(
                  focusedBorderColor = if (darkTheme) NeonCyan else GlassAccentBlue,
                  unfocusedBorderColor = if (darkTheme) Color(0xFF4D5E94) else Color(0xFFC2D1EE),
                  focusedTextColor = inkPrimary,
                  unfocusedTextColor = inkPrimary,
                  focusedLabelColor = if (darkTheme) NeonCyan else GlassAccentBlue,
                ),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("promo_code_input"),
            )

            if (isLockedOut) {
              val m = promoState.lockoutSecondsRemaining / 60
              val s = promoState.lockoutSecondsRemaining % 60
              Text(
                text = "Locked out due to repeated invalid codes. Try again in %02d:%02d".format(m, s),
                color = if (darkTheme) NeonMagenta else GlassRose,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
              )
            } else if (promoState.message != null) {
              Text(
                text = promoState.message,
                color =
                  if (promoState.isError) {
                    if (darkTheme) NeonMagenta else GlassRose
                  } else {
                    if (darkTheme) NeonLime else GlassAccentMint
                  },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
              )
            }
          }
        }

        Spacer(Modifier.height(14.dp))

        Button(
          onClick = { onSubmitCode(codeText) },
          enabled = !isLockedOut && codeText.isNotBlank(),
          colors = ButtonDefaults.buttonColors(containerColor = GlassAccentMint),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("promo_apply_button"),
        ) {
          Text("Apply Code", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
        }
      }
    }
  }
}

/** Hidden Admin Panel — opens only when the SHA-256 admin secret hash matches. */
@Composable
fun AdminPanelDialog(
  adStatus: AdRuntimeStatus,
  sessionSummary: String,
  darkTheme: Boolean = false,
  onSaveConfig: (gameId: String, platform: AdPlatform, testMode: Boolean) -> Boolean,
  onDismiss: () -> Unit,
) {
  var gameIdInput by remember(adStatus.savedGameId) { mutableStateOf(adStatus.savedGameId) }
  var selectedPlatform by remember(adStatus.platform) { mutableStateOf(adStatus.platform) }
  var testMode by remember(adStatus.testMode) { mutableStateOf(adStatus.testMode) }
  var validationError by remember { mutableStateOf<String?>(null) }

  val isValidInput = PromoAndAdminManager.isValidUnityGameId(gameIdInput)
  val placements = AdConfig.placementsFor(selectedPlatform)
  val inkPrimary = if (darkTheme) Color.White else GlassInkPrimary
  val inkSecondary = if (darkTheme) Color(0xFFB5C4EC) else GlassInkSecondary

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 18.dp)
        .glassPanel(
          darkTheme = darkTheme,
          cornerRadius = 24.dp,
          elevation = 20.dp,
          highOpacity = true,
          accentBorder = ArcadeGold.copy(alpha = 0.85f),
        )
        .padding(18.dp)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        // Header
        Box(modifier = Modifier.fillMaxWidth()) {
          Row(
            modifier = Modifier.align(Alignment.CenterStart),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Default.AdminPanelSettings,
              contentDescription = null,
              tint = if (darkTheme) ArcadeGold else GlassGoldWarm,
              modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.width(8.dp))
            if (darkTheme) {
              OutlinedGameText(
                text = "ADMIN PANEL",
                fontSize = 18.sp,
                fillColor = ArcadeGold,
                outlineColor = Color.Black,
              )
            } else {
              Text(
                text = "ADMIN PANEL",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                color = GlassInkPrimary,
              )
            }
          }
          IconButton(
            onClick = onDismiss,
            modifier = Modifier
              .align(Alignment.CenterEnd)
              .size(48.dp)
              .testTag("admin_close_button"),
          ) {
            Icon(Icons.Default.Close, contentDescription = "Close Admin Panel", tint = inkPrimary)
          }
        }

        // Current saved Unity Ads status card
        GlassInsetCard(
          darkTheme = darkTheme,
          cornerRadius = 14.dp,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Text(
              text = "Saved Game ID: ${adStatus.savedGameId.ifBlank { "None (Ads Disabled)" }}",
              color =
                if (adStatus.savedGameId.isBlank()) {
                  if (darkTheme) NeonMagenta else GlassRose
                } else {
                  if (darkTheme) NeonLime else GlassAccentMint
                },
              fontWeight = FontWeight.Bold,
              fontSize = 14.sp,
            )
            Text(
              text = "SDK Status: ${
                when {
                  adStatus.savedGameId.isBlank() -> "Waiting for Game ID"
                  adStatus.isInitialized -> "Initialized (${adStatus.platform.displayName})"
                  adStatus.isInitializing -> "Initializing..."
                  else -> adStatus.lastError ?: "Not Initialized"
                }
              }",
              color = inkSecondary,
              fontSize = 12.sp,
            )
            Text(
              text = "Placements: ${placements.rewardedId} / ${placements.interstitialId} / ${placements.bannerId}",
              color = if (darkTheme) Color(0xFF8FA3D9) else GlassInkSecondary,
              fontSize = 11.sp,
            )
            Text(
              text = sessionSummary,
              color = if (darkTheme) NeonCyan else GlassAccentBlue,
              fontSize = 11.sp,
              fontWeight = FontWeight.SemiBold,
            )
          }
        }

        // Store platform selector (Google Play / Apple App Store)
        Text(
          text = "Select Store Platform",
          color = inkPrimary,
          fontWeight = FontWeight.Bold,
          fontSize = 14.sp,
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          AdPlatform.entries.forEach { platform ->
            val selected = platform == selectedPlatform
            Box(
              modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                  when {
                    selected && darkTheme -> NeonCyan.copy(alpha = 0.2f)
                    selected -> GlassAccentBlue.copy(alpha = 0.14f)
                    darkTheme -> PixelCardDark
                    else -> Color.White.copy(alpha = 0.85f)
                  }
                )
                .border(
                  width = if (selected) 2.dp else 1.dp,
                  color =
                    when {
                      selected && darkTheme -> NeonCyan
                      selected -> GlassAccentBlue
                      darkTheme -> Color(0xFF405080)
                      else -> Color(0xFFD0DEF7)
                    },
                  shape = RoundedCornerShape(12.dp),
                )
                .clickable { selectedPlatform = platform }
                .testTag("admin_platform_${platform.id}"),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = platform.displayName,
                color =
                  when {
                    selected && darkTheme -> NeonCyan
                    selected -> GlassAccentBlue
                    else -> inkSecondary
                  },
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
              )
            }
          }
        }

        // Unity Game ID input (6 to 10 numeric digits only)
        OutlinedTextField(
          value = gameIdInput,
          onValueChange = { raw ->
            val filtered = raw.filter { it.isDigit() }.take(10)
            gameIdInput = filtered
            validationError = null
          },
          singleLine = true,
          label = { Text("Unity Game ID (6-10 digits)") },
          placeholder = { Text("e.g. 5742918") },
          isError = (gameIdInput.isNotEmpty() && !isValidInput) || validationError != null,
          supportingText = {
            val msg =
              validationError
                ?: if (gameIdInput.isNotEmpty() && !isValidInput) {
                  "Game ID must be 6 to 10 numeric digits"
                } else {
                  "Stored securely in EncryptedSharedPreferences"
                }
            Text(msg)
          },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
          colors =
            OutlinedTextFieldDefaults.colors(
              focusedBorderColor = if (darkTheme) ArcadeGold else GlassAccentBlue,
              unfocusedBorderColor = if (darkTheme) Color(0xFF4D5E94) else Color(0xFFC2D1EE),
              focusedTextColor = inkPrimary,
              unfocusedTextColor = inkPrimary,
              focusedLabelColor = if (darkTheme) ArcadeGold else GlassAccentBlue,
            ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("admin_game_id_input"),
        )

        // Test Mode toggle
        GlassInsetCard(
          darkTheme = darkTheme,
          cornerRadius = 14.dp,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
              Text("Unity Ads Test Mode", color = inkPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Default.WarningAmber,
                  contentDescription = null,
                  tint = if (testMode) (if (darkTheme) ArcadeGold else GlassGoldWarm) else GlassAccentMint,
                  modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                  text = if (testMode) "ON — Turn OFF before production release" else "OFF — Live Production Ads enabled",
                  color = if (testMode) (if (darkTheme) ArcadeGold else GlassGoldWarm) else GlassAccentMint,
                  fontSize = 11.sp,
                )
              }
            }
            Switch(
              checked = testMode,
              onCheckedChange = { testMode = it },
              colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = GlassGoldWarm),
              modifier = Modifier.testTag("admin_test_mode_switch"),
            )
          }
        }

        // Save button
        Button(
          onClick = {
            if (!isValidInput) {
              validationError = "Please enter a valid 6-10 digit Unity Game ID."
            } else {
              val saved = onSaveConfig(gameIdInput, selectedPlatform, testMode)
              if (saved) {
                onDismiss()
              } else {
                validationError = "Invalid Unity Game ID format."
              }
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = GlassAccentMint),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("admin_save_button"),
        ) {
          Text(
            text = "Save & Initialize Ads",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
          )
        }
      }
    }
  }
}

/** GDPR / CCPA privacy consent dialog for ad personalization metadata. */
@Composable
fun PrivacyConsentDialog(
  initialGdpr: Boolean,
  initialCcpa: Boolean,
  darkTheme: Boolean = false,
  onSaveConsent: (gdprConsent: Boolean, ccpaOptIn: Boolean) -> Unit,
) {
  var gdpr by remember(initialGdpr) { mutableStateOf(initialGdpr) }
  var ccpa by remember(initialCcpa) { mutableStateOf(initialCcpa) }
  val inkPrimary = if (darkTheme) Color.White else GlassInkPrimary
  val inkSecondary = if (darkTheme) Color(0xFFD6E0FF) else GlassInkSecondary

  Dialog(
    onDismissRequest = {},
    properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .glassPanel(darkTheme = darkTheme, cornerRadius = 24.dp, elevation = 18.dp, highOpacity = true)
        .padding(18.dp)
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.PrivacyTip,
            contentDescription = null,
            tint = if (darkTheme) NeonCyan else GlassAccentBlue,
            modifier = Modifier.size(24.dp),
          )
          Spacer(Modifier.width(8.dp))
          if (darkTheme) {
            OutlinedGameText(
              text = "AD PRIVACY",
              fontSize = 20.sp,
              fillColor = Color.White,
              outlineColor = Color(0xFF0A142E),
            )
          } else {
            Text(
              text = "AD PRIVACY",
              style = MaterialTheme.typography.headlineMedium.copy(fontSize = 20.sp),
              color = GlassInkPrimary,
            )
          }
        }

        Text(
          text = "Pixel Arrow respects your privacy (GDPR / CCPA). Choose how ads and rewards are personalized:",
          style = MaterialTheme.typography.bodyMedium,
          color = inkSecondary,
        )

        GlassInsetCard(
          darkTheme = darkTheme,
          cornerRadius = 12.dp,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("GDPR Personalized Ads", color = inkPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Switch(checked = gdpr, onCheckedChange = { gdpr = it })
          }
        }

        GlassInsetCard(
          darkTheme = darkTheme,
          cornerRadius = 12.dp,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("CCPA Data Sharing Opt-In", color = inkPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Switch(checked = ccpa, onCheckedChange = { ccpa = it })
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Button(
            onClick = { onSaveConsent(gdpr, ccpa) },
            colors = ButtonDefaults.buttonColors(
              containerColor = if (darkTheme) PixelCardDark else Color.White.copy(alpha = 0.85f),
            ),
            border = BorderStroke(1.dp, if (darkTheme) NeonCyan else Color(0xFFB5C7EA)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f).height(48.dp),
          ) {
            Text("Save Choice", color = inkPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
          }
          Button(
            onClick = { onSaveConsent(true, true) },
            colors = ButtonDefaults.buttonColors(containerColor = GlassAccentMint),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f).height(48.dp).testTag("privacy_accept_all_button"),
          ) {
            Text("Accept All", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
          }
        }
      }
    }
  }
}
