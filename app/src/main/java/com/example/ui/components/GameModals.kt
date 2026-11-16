package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.LevelRecord
import com.example.data.model.PlayerProfile
import com.example.game.model.ArrowSkin
import com.example.game.model.GameMode
import com.example.game.viewmodel.LevelResult
import com.example.ui.theme.ArcadeAmber
import com.example.ui.theme.ArcadeGold
import com.example.ui.theme.GlassAccentBlue
import com.example.ui.theme.GlassAccentMint
import com.example.ui.theme.GlassGoldWarm
import com.example.ui.theme.GlassInkMuted
import com.example.ui.theme.GlassInkPrimary
import com.example.ui.theme.GlassInkSecondary
import com.example.ui.theme.GlassRose
import com.example.ui.theme.HeartRed
import com.example.ui.theme.LogoGoldBottom
import com.example.ui.theme.LogoGoldTop
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonLime
import com.example.ui.theme.PlayGreenBottom
import com.example.ui.theme.PlayGreenTop
import kotlinx.coroutines.delay

/**
 * Settings dialog featuring audio/haptic/theme toggles, Promo & Privacy shortcuts,
 * and a read-only Support Player ID card with one-tap copy.
 */
@Composable
fun SettingsDialog(
  profile: PlayerProfile,
  inGame: Boolean,
  onToggleSfx: (Boolean) -> Unit,
  onToggleBgm: (Boolean) -> Unit,
  onToggleVibration: (Boolean) -> Unit,
  onToggleDark: (Boolean) -> Unit,
  onOpenPromoCode: () -> Unit,
  onOpenPrivacyConsent: () -> Unit,
  onRestartLevel: () -> Unit,
  onGoHome: () -> Unit,
  onDismiss: () -> Unit,
) {
  val darkTheme = profile.darkMode
  val inkPrimary = if (darkTheme) Color.White else GlassInkPrimary
  val inkSecondary = if (darkTheme) Color.White.copy(alpha = 0.85f) else GlassInkSecondary
  @Suppress("DEPRECATION")
  val clipboardManager = LocalClipboardManager.current
  var copiedId by remember { mutableStateOf(false) }

  LaunchedEffect(copiedId) {
    if (copiedId) {
      delay(2000L)
      copiedId = false
    }
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp)
        .glassPanel(darkTheme = darkTheme, cornerRadius = 24.dp, elevation = 18.dp, highOpacity = true)
        .padding(18.dp)
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.fillMaxWidth()) {
          if (darkTheme) {
            OutlinedGameText(
              text = "SETTINGS",
              fontSize = 22.sp,
              fillColor = Color.White,
              outlineColor = Color(0xFF0A142E),
              outlineWidth = 6f,
              modifier = Modifier.align(Alignment.Center),
            )
          } else {
            Text(
              text = "SETTINGS",
              style = MaterialTheme.typography.headlineMedium.copy(fontSize = 21.sp),
              color = GlassInkPrimary,
              modifier = Modifier.align(Alignment.Center),
            )
          }
          IconButton(
            onClick = onDismiss,
            modifier = Modifier
              .align(Alignment.CenterEnd)
              .size(48.dp)
              .testTag("settings_close_button"),
          ) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "Close Settings",
              tint = inkPrimary,
              modifier = Modifier.size(26.dp),
            )
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        GlassInsetCard(
          darkTheme = darkTheme,
          cornerRadius = 16.dp,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
          ) {
            SettingsToggleRow(
              icon = Icons.Default.MusicNote,
              label = "SFX",
              checked = profile.sfxEnabled,
              darkTheme = darkTheme,
              testTag = "toggle_sfx",
              onCheckedChange = onToggleSfx,
            )
            SettingsToggleRow(
              icon = Icons.AutoMirrored.Filled.VolumeUp,
              label = "BGM",
              checked = profile.bgmEnabled,
              darkTheme = darkTheme,
              testTag = "toggle_bgm",
              onCheckedChange = onToggleBgm,
            )
            SettingsToggleRow(
              icon = Icons.Default.Vibration,
              label = "Vibration",
              checked = profile.vibrationEnabled,
              darkTheme = darkTheme,
              testTag = "toggle_vibration",
              onCheckedChange = onToggleVibration,
            )
            SettingsToggleRow(
              icon = Icons.Default.DarkMode,
              label = "Dark",
              checked = profile.darkMode,
              darkTheme = darkTheme,
              activeTrack = Color(0xFF6A4C93),
              inactiveTrack = if (darkTheme) Color(0xFF5D4B8A) else Color(0xFFC2CEE8),
              testTag = "toggle_dark",
              onCheckedChange = onToggleDark,
            )
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Promo code and Ad Privacy actions
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Button(
            onClick = {
              onDismiss()
              onOpenPromoCode()
            },
            colors = ButtonDefaults.buttonColors(containerColor = ArcadeAmber),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .weight(1f)
              .height(48.dp)
              .testTag("settings_promo_button"),
          ) {
            Icon(Icons.Default.ConfirmationNumber, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFF1F1100))
            Spacer(Modifier.width(6.dp))
            Text("Promo", fontWeight = FontWeight.Bold, color = Color(0xFF1F1100), fontSize = 13.sp)
          }
          Button(
            onClick = {
              onDismiss()
              onOpenPrivacyConsent()
            },
            colors = ButtonDefaults.buttonColors(
              containerColor = if (darkTheme) Color(0xFF283A6E) else GlassAccentBlue,
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .weight(1f)
              .height(48.dp)
              .testTag("settings_privacy_button"),
          ) {
            Icon(Icons.Default.PrivacyTip, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
            Spacer(Modifier.width(6.dp))
            Text("Ad Privacy", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
          }
        }

        if (inGame) {
          Spacer(modifier = Modifier.height(10.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Button(
              onClick = {
                onDismiss()
                onRestartLevel()
              },
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3E6AE1)),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("settings_restart_button"),
            ) {
              Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text("Restart", fontWeight = FontWeight.Bold)
            }
            Button(
              onClick = {
                onDismiss()
                onGoHome()
              },
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE55934)),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("settings_home_button"),
            ) {
              Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text("Home", fontWeight = FontWeight.Bold)
            }
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Immutable anonymous Player ID card for support (view + copy only)
        GlassInsetCard(
          darkTheme = darkTheme,
          cornerRadius = 14.dp,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Default.Badge,
                  contentDescription = null,
                  tint = if (darkTheme) NeonCyan else GlassAccentBlue,
                  modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                  text = "Player ID (Support)",
                  style = MaterialTheme.typography.labelMedium,
                  color = inkSecondary,
                  fontWeight = FontWeight.SemiBold,
                )
              }
              Spacer(Modifier.height(3.dp))
              Text(
                text = profile.userId,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = inkPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("settings_player_id_text"),
              )
            }

            OutlinedButton(
              onClick = {
                clipboardManager.setText(AnnotatedString(profile.userId))
                copiedId = true
              },
              shape = RoundedCornerShape(10.dp),
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
              border = BorderStroke(
                1.dp,
                if (copiedId) {
                  if (darkTheme) NeonLime else GlassAccentMint
                } else {
                  if (darkTheme) Color.White.copy(alpha = 0.55f) else Color(0xFFB3C3E6)
                },
              ),
              colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (darkTheme) Color.Transparent else Color.White.copy(alpha = 0.72f),
                contentColor = if (copiedId) (if (darkTheme) NeonLime else GlassAccentMint) else inkPrimary,
              ),
              modifier = Modifier
                .height(40.dp)
                .testTag("settings_copy_id_button"),
            ) {
              Icon(
                imageVector = if (copiedId) Icons.Default.Check else Icons.Default.ContentCopy,
                contentDescription = "Copy Player ID",
                modifier = Modifier.size(15.dp),
              )
              Spacer(Modifier.width(5.dp))
              Text(
                text = if (copiedId) "Copied" else "Copy",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SettingsToggleRow(
  icon: ImageVector,
  label: String,
  checked: Boolean,
  darkTheme: Boolean,
  testTag: String,
  activeTrack: Color = GlassAccentMint,
  inactiveTrack: Color = if (darkTheme) Color(0xFF5D6B94) else Color(0xFFC2CEE8),
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = label,
      tint = if (darkTheme) NeonCyan else GlassAccentBlue,
      modifier = Modifier.size(24.dp),
    )
    Spacer(modifier = Modifier.width(12.dp))
    if (darkTheme) {
      OutlinedGameText(
        text = label,
        fontSize = 17.sp,
        fillColor = Color.White,
        outlineColor = Color(0xFF141E3A),
        outlineWidth = 5f,
        modifier = Modifier.weight(1f),
      )
    } else {
      Text(
        text = label,
        style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
        color = GlassInkPrimary,
        modifier = Modifier.weight(1f),
      )
    }

    val trackColor by animateColorAsState(if (checked) activeTrack else inactiveTrack, label = "track")
    val thumbOffset by animateDpAsState(if (checked) 32.dp else 2.dp, label = "thumb")

    Box(
      modifier = Modifier
        .size(width = 64.dp, height = 34.dp)
        .clip(RoundedCornerShape(50))
        .background(trackColor)
        .clickable { onCheckedChange(!checked) }
        .testTag(testTag),
      contentAlignment = Alignment.CenterStart,
    ) {
      Box(
        modifier = Modifier
          .offset(x = thumbOffset)
          .size(30.dp)
          .shadow(2.dp, CircleShape)
          .clip(CircleShape)
          .background(Color.White)
          .border(1.dp, Color(0xFFD5E0F6), CircleShape),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = "|||",
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold,
          color = Color(0xFF6B7AA0),
        )
      }
    }
  }
}

/** Skin selection dialog with White Glassmorphism grid cards. */
@Composable
fun SkinSelectorDialog(
  selectedSkin: ArrowSkin,
  darkTheme: Boolean,
  onSelectSkin: (ArrowSkin) -> Unit,
  onDismiss: () -> Unit,
) {
  val inkPrimary = if (darkTheme) Color.White else GlassInkPrimary

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp)
        .glassPanel(darkTheme = darkTheme, cornerRadius = 24.dp, elevation = 18.dp, highOpacity = true)
        .padding(16.dp)
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.fillMaxWidth()) {
          if (darkTheme) {
            OutlinedGameText(
              text = "SKINS",
              fontSize = 22.sp,
              fillColor = Color.White,
              outlineColor = Color(0xFF0A142E),
              outlineWidth = 6f,
              modifier = Modifier.align(Alignment.Center),
            )
          } else {
            Text(
              text = "SKINS",
              style = MaterialTheme.typography.headlineMedium.copy(fontSize = 21.sp),
              color = GlassInkPrimary,
              modifier = Modifier.align(Alignment.Center),
            )
          }
          IconButton(
            onClick = onDismiss,
            modifier = Modifier
              .align(Alignment.CenterEnd)
              .size(48.dp)
              .testTag("skin_close_button"),
          ) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "Close Skin Selector",
              tint = inkPrimary,
              modifier = Modifier.size(26.dp),
            )
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyVerticalGrid(
          columns = GridCells.Fixed(3),
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 460.dp),
          contentPadding = PaddingValues(4.dp),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          items(ArrowSkin.entries) { skin ->
            val isEquipped = skin == selectedSkin
            Column(
              modifier = Modifier
                .glassPanel(
                  darkTheme = darkTheme,
                  cornerRadius = 14.dp,
                  elevation = 4.dp,
                  highOpacity = true,
                  accentBorder = if (isEquipped) (if (darkTheme) NeonLime else GlassAccentMint) else null,
                )
                .padding(8.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .height(74.dp)
                  .clip(RoundedCornerShape(10.dp))
                  .background(if (darkTheme) Color(0xFF0E1426) else Color(0xFFF3F7FF))
                  .border(1.dp, if (darkTheme) Color.Transparent else Color(0xFFE0EAFA), RoundedCornerShape(10.dp))
                  .padding(6.dp)
              ) {
                SkinPreviewCanvas(skin = skin, darkTheme = darkTheme)
              }
              Spacer(modifier = Modifier.height(5.dp))
              Text(
                text = skin.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = inkPrimary,
                maxLines = 1,
                textAlign = TextAlign.Center,
              )
              Spacer(modifier = Modifier.height(6.dp))
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .height(34.dp)
                  .clip(RoundedCornerShape(9.dp))
                  .background(
                    if (isEquipped) {
                      Brush.verticalGradient(listOf(Color(0xFF00C2FF), GlassAccentBlue))
                    } else {
                      Brush.verticalGradient(listOf(PlayGreenTop, PlayGreenBottom))
                    }
                  )
                  .clickable { onSelectSkin(skin) }
                  .testTag("skin_use_${skin.id}"),
                contentAlignment = Alignment.Center,
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  if (isEquipped) {
                    Icon(
                      imageVector = Icons.Default.Check,
                      contentDescription = null,
                      tint = Color.White,
                      modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(3.dp))
                  }
                  Text(
                    text = if (isEquipped) "Using" else "Use",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

/** Level selection dialog showing unlocked levels and earned stars. */
@Composable
fun LevelSelectDialog(
  currentLevel: Int,
  highestCleared: Int,
  history: List<LevelRecord>,
  darkTheme: Boolean,
  onSelectLevel: (Int) -> Unit,
  onDismiss: () -> Unit,
) {
  val inkPrimary = if (darkTheme) Color.White else GlassInkPrimary
  val maxSelectable = maxOf(currentLevel, highestCleared + 1, 5).coerceAtLeast(1)
  val totalDisplay = ((maxSelectable + 11) / 12 * 12).coerceAtLeast(24)

  val starsByLevel =
    history
      .filter { it.mode == "classic" }
      .groupBy { it.levelNumber.toInt() }
      .mapValues { (_, recs) -> recs.maxOf { it.stars.toInt() } }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp)
        .glassPanel(darkTheme = darkTheme, cornerRadius = 24.dp, elevation = 18.dp, highOpacity = true)
        .padding(16.dp)
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.fillMaxWidth()) {
          if (darkTheme) {
            OutlinedGameText(
              text = "LEVELS",
              fontSize = 22.sp,
              fillColor = Color.White,
              outlineColor = Color(0xFF0A142E),
              outlineWidth = 6f,
              modifier = Modifier.align(Alignment.Center),
            )
          } else {
            Text(
              text = "LEVELS",
              style = MaterialTheme.typography.headlineMedium.copy(fontSize = 21.sp),
              color = GlassInkPrimary,
              modifier = Modifier.align(Alignment.Center),
            )
          }
          IconButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.CenterEnd).size(48.dp),
          ) {
            Icon(Icons.Default.Close, contentDescription = "Close Levels", tint = inkPrimary)
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyVerticalGrid(
          columns = GridCells.Fixed(4),
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 360.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items((1..totalDisplay).toList()) { lvl ->
            val unlocked = lvl <= maxOf(currentLevel, highestCleared + 1)
            val isCurrent = lvl == currentLevel
            val stars = starsByLevel[lvl] ?: 0
            Column(
              modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(
                  when {
                    isCurrent -> GlassAccentMint
                    unlocked && darkTheme -> Color(0xFF1B2440)
                    unlocked -> Color.White.copy(alpha = 0.90f)
                    darkTheme -> Color(0x731B2440)
                    else -> Color.White.copy(alpha = 0.45f)
                  }
                )
                .border(
                  width = 1.dp,
                  color =
                    when {
                      isCurrent -> Color.White.copy(alpha = 0.7f)
                      unlocked && !darkTheme -> Color(0xFFD5E3FB)
                      !darkTheme -> Color(0x99DCE6F9)
                      else -> Color(0x2200F0FF)
                    },
                  shape = RoundedCornerShape(12.dp),
                )
                .clickable(enabled = unlocked) { onSelectLevel(lvl) }
                .padding(vertical = 10.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Text(
                text = "$lvl",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color =
                  when {
                    isCurrent -> Color.White
                    unlocked -> inkPrimary
                    else -> GlassInkMuted
                  },
              )
              if (stars > 0) {
                Row(horizontalArrangement = Arrangement.Center) {
                  repeat(stars) {
                    Icon(
                      imageVector = Icons.Default.Star,
                      contentDescription = null,
                      tint = LogoGoldBottom,
                      modifier = Modifier.size(12.dp),
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

/** Win & Game Over dialog featuring Revive and Double Coins Rewarded Ad offers. */
@Composable
fun LevelResultDialog(
  result: LevelResult,
  darkTheme: Boolean,
  isLongSessionBoost: Boolean,
  onClaimDoubleCoins: () -> Unit,
  onNextLevel: () -> Unit,
  onRetry: () -> Unit,
  onRevive: () -> Unit,
  onHome: () -> Unit,
) {
  val inkPrimary = if (darkTheme) Color.White else GlassInkPrimary

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
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        when (result) {
          is LevelResult.Won -> {
            val title =
              when {
                result.isBonusLevel -> "BONUS CLEARED!"
                result.mode == GameMode.CLASSIC -> "LVL ${result.levelNumber} CLEARED!"
                else -> "STAGE ${result.levelNumber} CLEARED!"
              }
            if (darkTheme) {
              OutlinedGameText(
                text = title,
                fontSize = 20.sp,
                fillColor = Color.White,
                outlineColor = Color(0xFF0A142E),
                outlineWidth = 6f,
              )
            } else {
              Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 20.sp),
                color = GlassInkPrimary,
                textAlign = TextAlign.Center,
              )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              for (i in 1..3) {
                Icon(
                  imageVector = Icons.Default.Star,
                  contentDescription = null,
                  tint = if (i <= result.stars) LogoGoldTop else Color(0xFFB5C3E0),
                  modifier = Modifier.size(40.dp),
                )
              }
            }
            Spacer(Modifier.height(12.dp))
            GlassInsetCard(
              darkTheme = darkTheme,
              cornerRadius = 14.dp,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                StatRow("Arrows Escaped", "${result.arrowsCleared}", darkTheme)
                StatRow("Mistakes", "${result.mistakes}", darkTheme)
                StatRow("Time", "${result.durationSeconds}s", darkTheme)
                StatRow("Coins Earned", "+${result.coinsEarned}", darkTheme, highlightColor = if (darkTheme) ArcadeGold else GlassGoldWarm)
                if (result.mode == GameMode.FALL) {
                  StatRow("Fall Score", "${result.scoreEarned}", darkTheme, highlightColor = if (darkTheme) NeonCyan else GlassAccentBlue)
                }
              }
            }

            Spacer(Modifier.height(12.dp))

            // Rewarded Ad offer: 2x coins (+25 extra during long sessions)
            if (!result.doubleCoinsClaimed) {
              val ctaText =
                if (isLongSessionBoost) {
                  "2X COINS +25 BOOST (AD)"
                } else {
                  "2X COINS (WATCH AD)"
                }
              GlossyGreenButton(
                text = ctaText,
                onClick = onClaimDoubleCoins,
                height = 50.dp,
                fontSize = 13.sp,
                topColor = ArcadeGold,
                bottomColor = ArcadeAmber,
                lipColor = Color(0xFF9E5A00),
                outlineColor = Color(0xFF3A1E00),
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("win_double_coins_button"),
              )
              Spacer(Modifier.height(10.dp))
            } else {
              PillBadge(
                text = "2x Reward Claimed!",
                backgroundColor = Color(0xFF147D3B),
                borderColor = NeonLime,
              )
              Spacer(Modifier.height(10.dp))
            }

            GlossyGreenButton(
              text = if (result.mode == GameMode.CLASSIC) "NEXT LEVEL" else "NEXT STAGE",
              onClick = onNextLevel,
              height = 54.dp,
              fontSize = 18.sp,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("win_next_button"),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
              onClick = onHome,
              shape = RoundedCornerShape(12.dp),
              border = BorderStroke(1.5.dp, if (darkTheme) Color.White else Color(0xFFB5C7EA)),
              colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (darkTheme) Color.Transparent else Color.White.copy(alpha = 0.65f),
                contentColor = inkPrimary,
              ),
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("win_home_button"),
            ) {
              Text("Home", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
          }

          is LevelResult.Lost -> {
            if (darkTheme) {
              OutlinedGameText(
                text = "GAME OVER!",
                fontSize = 24.sp,
                fillColor = Color.White,
                outlineColor = Color(0xFF6B0F26),
                outlineWidth = 6f,
              )
            } else {
              Text(
                text = "GAME OVER!",
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 23.sp),
                color = GlassRose,
              )
            }
            Spacer(Modifier.height(10.dp))
            GlassInsetCard(
              darkTheme = darkTheme,
              cornerRadius = 14.dp,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                text = "Out of lives! Watch a rewarded ad to revive with +1 heart or restart the level.",
                style = MaterialTheme.typography.bodyMedium,
                color = if (darkTheme) Color.White else GlassInkSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp),
              )
            }
            Spacer(Modifier.height(14.dp))
            GlossyGreenButton(
              text = "REVIVE (+1 HEART AD)",
              onClick = onRevive,
              height = 54.dp,
              fontSize = 15.sp,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("revive_button"),
            )
            Spacer(Modifier.height(10.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = HeartRed),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                  .weight(1f)
                  .height(48.dp)
                  .testTag("retry_button"),
              ) {
                Text("Restart", fontWeight = FontWeight.Bold, color = Color.White)
              }
              OutlinedButton(
                onClick = onHome,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.5.dp, if (darkTheme) Color.White else Color(0xFFB5C7EA)),
                colors = ButtonDefaults.outlinedButtonColors(
                  containerColor = if (darkTheme) Color.Transparent else Color.White.copy(alpha = 0.65f),
                  contentColor = inkPrimary,
                ),
                modifier = Modifier
                  .weight(1f)
                  .height(48.dp)
                  .testTag("loss_home_button"),
              ) {
                Text("Home", fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun StatRow(
  label: String,
  value: String,
  darkTheme: Boolean,
  highlightColor: Color? = null,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyMedium,
      color = if (darkTheme) Color(0xFFA9B6DC) else GlassInkSecondary,
    )
    Text(
      text = value,
      style = MaterialTheme.typography.titleMedium,
      color = highlightColor ?: if (darkTheme) Color.White else GlassInkPrimary,
    )
  }
}
