package com.example.ui.components

import android.app.Activity
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ads.AdManager
import com.example.ads.AdRuntimeStatus
import com.example.ui.theme.GlassRimHighlight
import com.example.ui.theme.GlassRimIce
import com.example.ui.theme.GlassWhiteHigh
import com.example.ui.theme.GlassWhiteMid
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.PixelPanelDark
import com.unity3d.services.banners.BannerView

// Displays Unity Ads BannerView; renders and loads nothing unless a Game ID is saved
@Composable
fun UnityBannerAdBar(
  adManager: AdManager,
  adStatus: AdRuntimeStatus,
  placementId: String,
  modifier: Modifier = Modifier,
  darkTheme: Boolean = false,
) {
  // Skip rendering when no Game ID is saved or SDK is not yet initialized
  if (adStatus.savedGameId.isBlank() || !adStatus.isInitialized) {
    return
  }

  val context = LocalContext.current
  val activity = context as? Activity ?: return
  var loadedBanner by remember(adStatus.savedGameId, placementId, adStatus.testMode) {
    mutableStateOf<BannerView?>(null)
  }
  var bannerFailed by remember(adStatus.savedGameId, placementId, adStatus.testMode) {
    mutableStateOf(false)
  }

  // Destroy old BannerView when leaving screen or changing Game ID to avoid leaks
  DisposableEffect(activity, adStatus.savedGameId, placementId, adStatus.isInitialized) {
    bannerFailed = false
    loadedBanner = null
    val pendingBanner =
      adManager.createAndLoadBanner(
        activity = activity,
        placementId = placementId,
        onBannerLoaded = { view ->
          bannerFailed = false
          loadedBanner = view
        },
        onBannerFailed = {
          loadedBanner = null
          bannerFailed = true
        },
      )
    onDispose {
      pendingBanner?.destroy()
      loadedBanner = null
    }
  }

  val currentBanner = loadedBanner
  if (currentBanner == null || bannerFailed) {
    return
  }

  Box(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 4.dp)
      .glassPanel(darkTheme = darkTheme, cornerRadius = 12.dp, elevation = 6.dp)
      .height(54.dp)
      .testTag("unity_banner_container"),
    contentAlignment = Alignment.Center,
  ) {
    AndroidView(
      factory = { ctx ->
        FrameLayout(ctx).apply {
          layoutParams =
            ViewGroup.LayoutParams(
              ViewGroup.LayoutParams.MATCH_PARENT,
              ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
      },
      update = { container ->
        val parent = currentBanner.parent as? ViewGroup
        if (parent !== container) {
          parent?.removeView(currentBanner)
          container.removeAllViews()
          container.addView(currentBanner)
        }
      },
    )
  }
}
