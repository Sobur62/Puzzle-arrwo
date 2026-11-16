package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ads.AdManager
import com.example.ads.AdRuntimeStatus
import com.example.data.model.PlayerProfile
import com.example.game.viewmodel.LivesDisplayState
import com.example.ui.components.ArrowPuzzleLogo
import com.example.ui.components.GlossyGreenButton
import com.example.ui.components.GooglyMascotIcon
import com.example.ui.components.OutlinedGameText
import com.example.ui.components.PillBadge
import com.example.ui.components.TiledArrowBackground
import com.example.ui.components.UnityBannerAdBar
import com.example.ui.components.glassPanel
import com.example.ui.theme.ArcadeAmber
import com.example.ui.theme.ArcadeGold
import com.example.ui.theme.BadgeRed
import com.example.ui.theme.GlassAccentBlue
import com.example.ui.theme.GlassAccentCyan
import com.example.ui.theme.GlassAccentMint
import com.example.ui.theme.GlassGoldWarm
import com.example.ui.theme.GlassInkPrimary
import com.example.ui.theme.GlassInkSecondary
import com.example.ui.theme.LogoGoldTop
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonLime
import com.example.ui.theme.NeonMagenta

/**
 * Pixel Arrow Home Screen supporting White Glassmorphism (default) and Dark Neon themes.
 */
@Composable
fun HomeScreen(
  profile: PlayerProfile,
  livesState: LivesDisplayState,
  adManager: AdManager,
  adStatus: AdRuntimeStatus,
  bannerPlacementId: String,
  isLongSessionBoost: Boolean,
  onPlayClassic: () -> Unit,
  onPlayFallMode: () -> Unit,
  onPlayBonusLevel: () -> Unit,
  onOpenPromoCode: () -> Unit,
  onOpenSettings: () -> Unit,
  onOpenSkins: () -> Unit,
  onOpenLevels: () -> Unit,
  onToggleQuickSound: () -> Unit,
  onRefillLives: () -> Unit,
) {
  val darkTheme = profile.darkMode
  val soundActive = profile.sfxEnabled || profile.bgmEnabled
  val inkPrimary = if (darkTheme) Color.White else GlassInkPrimary
  val inkSecondary = if (darkTheme) Color(0xFFD6DCFF) else GlassInkSecondary
  val subPillBg = if (darkTheme) Color(0x55000000) else Color(0xCCEAF1FF)
  val subPillBorder = if (darkTheme) NeonCyan.copy(alpha = 0.25f) else Color(0xB8FFFFFF)

  Box(modifier = Modifier.fillMaxSize()) {
    TiledArrowBackground(darkTheme = darkTheme)

    Column(
      modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding(),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        // Top bar: Settings + Promo (left) and Sound, Coins, Lives (right)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.Top,
        ) {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
              modifier = Modifier
                .size(48.dp)
                .glassPanel(darkTheme = darkTheme, cornerRadius = 14.dp, elevation = 8.dp)
                .clickable(onClick = onOpenSettings)
                .testTag("home_settings_button"),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = inkPrimary,
                modifier = Modifier.size(24.dp),
              )
            }

            Box(
              modifier = Modifier
                .height(48.dp)
                .glassPanel(
                  darkTheme = darkTheme,
                  cornerRadius = 14.dp,
                  elevation = 8.dp,
                  accentBorder = if (darkTheme) ArcadeGold.copy(alpha = 0.7f) else null,
                )
                .clickable(onClick = onOpenPromoCode)
                .padding(horizontal = 12.dp)
                .testTag("home_promo_button"),
              contentAlignment = Alignment.Center,
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Default.ConfirmationNumber,
                  contentDescription = "Promo Code",
                  tint = if (darkTheme) ArcadeGold else GlassGoldWarm,
                  modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                  text = "Promo",
                  color = inkPrimary,
                  fontWeight = FontWeight.Bold,
                  fontSize = 12.sp,
                )
              }
            }
          }

          Column(horizontalAlignment = Alignment.End) {
            Row(
              modifier = Modifier
                .glassPanel(darkTheme = darkTheme, cornerRadius = 16.dp, elevation = 8.dp)
                .padding(horizontal = 10.dp, vertical = 7.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = if (soundActive) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                contentDescription = "Toggle Sound",
                tint = inkPrimary,
                modifier = Modifier
                  .size(22.dp)
                  .clickable(onClick = onToggleQuickSound)
                  .testTag("home_quick_sound_button"),
              )

              // Coins balance pill
              Row(
                modifier = Modifier
                  .clip(RoundedCornerShape(10.dp))
                  .background(subPillBg)
                  .border(1.dp, subPillBorder, RoundedCornerShape(10.dp))
                  .padding(horizontal = 8.dp, vertical = 4.dp)
                  .testTag("home_coins_pill"),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                  imageVector = Icons.Default.MonetizationOn,
                  contentDescription = "Coins",
                  tint = if (darkTheme) ArcadeGold else ArcadeAmber,
                  modifier = Modifier.size(17.dp),
                )
                Spacer(Modifier.width(3.dp))
                Text(
                  text = "${profile.coins}",
                  color = if (darkTheme) ArcadeGold else GlassGoldWarm,
                  fontWeight = FontWeight.Bold,
                  fontSize = 13.sp,
                )
              }

              // Lives / energy pill
              Row(
                modifier = Modifier
                  .clip(RoundedCornerShape(10.dp))
                  .background(subPillBg)
                  .border(1.dp, subPillBorder, RoundedCornerShape(10.dp))
                  .clickable(onClick = onRefillLives)
                  .padding(horizontal = 8.dp, vertical = 4.dp)
                  .testTag("home_energy_pill"),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                  imageVector = Icons.Default.Bolt,
                  contentDescription = "Lives",
                  tint = if (darkTheme) LogoGoldTop else GlassAccentBlue,
                  modifier = Modifier.size(17.dp),
                )
                Spacer(Modifier.width(2.dp))
                Text(
                  text = "${livesState.lives}",
                  color = inkPrimary,
                  fontWeight = FontWeight.Bold,
                  fontSize = 13.sp,
                )
              }
            }

            if (livesState.lives < PlayerProfile.MAX_LIVES && livesState.refillSecondsRemaining > 0) {
              val mins = livesState.refillSecondsRemaining / 60
              val secs = livesState.refillSecondsRemaining % 60
              Text(
                text = "%02d:%02d".format(mins, secs),
                color = if (darkTheme) NeonCyan else GlassAccentBlue,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp, end = 6.dp),
              )
            }
          }
        }

        Spacer(modifier = Modifier.weight(0.18f))

        ArrowPuzzleLogo()

        if (profile.fallHighScore > 0 || profile.totalArrowsCleared > 0) {
          Spacer(modifier = Modifier.height(12.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (profile.totalArrowsCleared > 0) {
              PillBadge(
                text = "${profile.totalArrowsCleared} Cleared",
                darkTheme = darkTheme,
                borderColor = if (darkTheme) NeonCyan.copy(alpha = 0.5f) else GlassAccentCyan.copy(alpha = 0.4f),
              )
            }
            if (profile.fallHighScore > 0) {
              PillBadge(
                text = "Fall Best ${profile.fallHighScore}",
                darkTheme = darkTheme,
                borderColor = if (darkTheme) NeonLime.copy(alpha = 0.6f) else GlassAccentMint.copy(alpha = 0.45f),
              )
            }
          }
        }

        Spacer(modifier = Modifier.weight(0.24f))

        // Bonus Level Rewarded Ad offer card (+60 Coins reward)
        Row(
          modifier = Modifier
            .widthIn(max = 360.dp)
            .fillMaxWidth()
            .glassPanel(
              darkTheme = darkTheme,
              cornerRadius = 18.dp,
              elevation = 10.dp,
              highOpacity = true,
              accentBorder = if (darkTheme) ArcadeGold.copy(alpha = 0.8f) else ArcadeAmber.copy(alpha = 0.55f),
            )
            .then(
              if (darkTheme) {
                Modifier.background(
                  Brush.horizontalGradient(listOf(Color(0x55281948), Color(0x553E1C5A)))
                )
              } else {
                Modifier.background(
                  Brush.horizontalGradient(listOf(Color(0x22FFE8B0), Color(0x18FFFFFF)))
                )
              }
            )
            .clickable(onClick = onPlayBonusLevel)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("home_bonus_level_button"),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(if (darkTheme) Color(0x33FFD700) else Color(0x28FFB300)),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = if (darkTheme) ArcadeGold else GlassGoldWarm,
                modifier = Modifier.size(22.dp),
              )
            }
            Spacer(Modifier.width(10.dp))
            Column {
              Text(
                text = "BONUS LEVEL (+60 COINS)",
                color = if (darkTheme) ArcadeGold else GlassGoldWarm,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
              )
              Text(
                text = if (isLongSessionBoost) "Session Boost Active: +25 Extra!" else "Watch Rewarded Ad or 25 Coins",
                color = if (isLongSessionBoost) (if (darkTheme) NeonLime else GlassAccentMint) else inkSecondary,
                fontSize = 11.sp,
              )
            }
          }
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(10.dp))
              .background(Brush.verticalGradient(listOf(LogoGoldTop, ArcadeAmber)))
              .padding(horizontal = 12.dp, vertical = 6.dp),
          ) {
            Text(
              text = "GO",
              color = Color(0xFF2B1600),
              fontWeight = FontWeight.Bold,
              fontSize = 12.sp,
            )
          }
        }

        Spacer(modifier = Modifier.weight(0.24f))

        // Current level badge (tapping opens level selector)
        PillBadge(
          text = "Level ${profile.classicLevel}",
          darkTheme = darkTheme,
          borderColor = if (darkTheme) NeonCyan.copy(alpha = 0.6f) else GlassAccentBlue.copy(alpha = 0.35f),
          modifier = Modifier
            .clickable(onClick = onOpenLevels)
            .testTag("home_level_badge"),
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Bottom primary action dock: Fall Mode + Play + Skin
        Row(
          modifier = Modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.Bottom,
        ) {
          // Fall Mode button
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
              .clickable(onClick = onPlayFallMode)
              .testTag("home_fall_mode_button"),
          ) {
            Box(
              modifier = Modifier
                .size(64.dp)
                .glassPanel(
                  darkTheme = darkTheme,
                  cornerRadius = 18.dp,
                  elevation = 10.dp,
                  highOpacity = true,
                ),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Default.ArrowDownward,
                contentDescription = "Fall Mode",
                tint = if (darkTheme) NeonCyan else GlassAccentBlue,
                modifier = Modifier.size(34.dp),
              )
            }
            if (darkTheme) {
              OutlinedGameText(
                text = "FALL",
                fontSize = 12.sp,
                fillColor = Color.White,
                outlineColor = Color(0xFF0A142E),
                outlineWidth = 5f,
                modifier = Modifier.offset(y = (-6).dp),
              )
            } else {
              Box(
                modifier = Modifier
                  .offset(y = (-7).dp)
                  .glassPanel(darkTheme = false, cornerRadius = 8.dp, elevation = 4.dp, highOpacity = true)
                  .padding(horizontal = 8.dp, vertical = 2.dp),
              ) {
                Text(
                  text = "FALL",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  color = GlassInkPrimary,
                )
              }
            }
          }

          // Primary pixel-green Play button
          GlossyGreenButton(
            text = "PLAY",
            onClick = onPlayClassic,
            height = 64.dp,
            fontSize = 24.sp,
            modifier = Modifier
              .weight(1f)
              .padding(horizontal = 12.dp, vertical = 8.dp)
              .testTag("home_play_button"),
          )

          // Skin selector button ("Free" badge + mascot)
          Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
              .clickable(onClick = onOpenSkins)
              .testTag("home_skin_button"),
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              modifier = Modifier.padding(top = 12.dp),
            ) {
              Box(
                modifier = Modifier
                  .size(64.dp)
                  .glassPanel(
                    darkTheme = darkTheme,
                    cornerRadius = 18.dp,
                    elevation = 10.dp,
                    highOpacity = true,
                    accentBorder = if (darkTheme) NeonMagenta.copy(alpha = 0.7f) else null,
                  ),
                contentAlignment = Alignment.BottomCenter,
              ) {
                GooglyMascotIcon(modifier = Modifier.size(54.dp))
              }
              if (darkTheme) {
                OutlinedGameText(
                  text = "SKIN",
                  fontSize = 12.sp,
                  fillColor = Color.White,
                  outlineColor = Color(0xFF0A142E),
                  outlineWidth = 5f,
                  modifier = Modifier.offset(y = (-6).dp),
                )
              } else {
                Box(
                  modifier = Modifier
                    .offset(y = (-7).dp)
                    .glassPanel(darkTheme = false, cornerRadius = 8.dp, elevation = 4.dp, highOpacity = true)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                  Text(
                    text = "SKIN",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = GlassInkPrimary,
                  )
                }
              }
            }

            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(BadgeRed)
                .border(1.dp, Color.White, RoundedCornerShape(8.dp))
                .padding(horizontal = 9.dp, vertical = 1.dp),
            ) {
              Text(
                text = "Free",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
              )
            }
          }
        }
      }

      // Unity Ads Banner placement (automatically hidden when no Game ID is configured)
      UnityBannerAdBar(
        adManager = adManager,
        adStatus = adStatus,
        placementId = bannerPlacementId,
        darkTheme = darkTheme,
      )
    }
  }
}
