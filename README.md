# Pixel Arrow

**Pixel Arrow** is an arcade pixel-art puzzle game built with **Kotlin**, **Jetpack Compose**, **MVVM**, **Firebase Firestore + Google Sign-In**, and the official **Unity Ads SDK (`com.unity3d.ads`)**.

---

## Key Features

- **Dark Neon Pixel-Art Gameplay**: Crisp pixel-grid canvas, smooth arrow slide/bounce/gravity animations, pinch-to-zoom & pan, 9 unlockable/selectable arrow skins, sound effects, background music toggle, and haptic feedback.
- **Classic Mode, Fall Mode & Bonus Levels**: Reverse-peeling solvable puzzles, gravity-drop cascades in Fall Mode, and high-reward Bonus Levels.
- **Official Unity Ads Integration (`AdManager`)**:
  - `AdManager` (`com.example.ads.AdManager`) implements `IUnityAdsInitializationListener`, `IUnityAdsLoadListener`, `IUnityAdsShowListener`, and `BannerView.Listener`.
  - Centralized placement configuration in `AdConfig.kt` (`Rewarded_Android`/`Rewarded_iOS`, `Interstitial_Android`/`Interstitial_iOS`, `Banner_Android`/`Banner_iOS`).
  - **Zero hardcoded Game IDs**: Initializes strictly from `EncryptedSharedPreferences`. If no Game ID is saved, no ads load or display.
  - **GDPR & CCPA Consent**: Built-in `PrivacyConsentDialog` writes `gdpr.consent` and `privacy.consent` `MetaData` before initialization.
- **High-Value Ad Strategy (`AdStrategyManager`)**:
  - **Rewarded Ads prioritized**: Revive (`+1 Heart`), Double Coins (`2x Coins`, plus `+25` session boost after 3+ minutes or 3+ clears), Free Hint, and Bonus Level unlock.
  - **Respectful Interstitial pacing**: Triggered only at natural level completions, every 3rd cleared level, with a minimum **90-second cooldown** and per-session cap—never mid-level.
  - **Non-blocking graceful fallbacks**: Automatic background retries with backoff if ads fail to load.
- **Promo Code & SHA-256 Hidden Admin Panel**:
  - Supports single-use reward codes (`PIXEL100`, `ARROW50`, `NEON2026`, `BONUS777`).
  - Secret admin unlock code is stored **exclusively as a SHA-256 hash** (never in plaintext and not editable in UI).
  - Entering 5 consecutive invalid codes triggers a **5-minute lockout**.
  - Admin Panel enables switching Store Platform (`Google Play` / `Apple App Store`), configuring the 6–10 digit Unity Game ID, and toggling Test Mode.

---

## Unity Ads Dashboard Checklist (উচ্চ আয়ের জন্য সেটআপ চেকলিস্ট)

অধিক eCPM এবং নিরাপদ ইউজার এক্সপেরিয়েন্সের জন্য Unity Monetization Dashboard-এ নিচের সেটিংসগুলো নিশ্চিত করুন:

1. **Floor Price / eCPM Waterfall সেটিংস**:
   - **Ad Units & Placements** সেকশনে `Rewarded_Android`, `Interstitial_Android`, এবং `Banner_Android`-এর জন্য **Auto-eCPM** চালু রাখুন অথবা **Sort by eCPM (Target / Floor Price)** ওয়াটারফল কনফিগার করুন:
     - *Tier 1 (High Floor)*: High eCPM (যেমন Tier-1 দেশগুলোর জন্য `$12–$20+` Rewarded, `$8–$12` Interstitial)
     - *Tier 2 (Medium Floor)*: `$4–$8`
     - *Tier 3 (Backfill / Auto)*: No floor (১০০% fill rate নিশ্চিত করতে)।
   - Unity LevelPlay বা Mediation ব্যবহার করলে একাধিক নেটওয়ার্ক (Unity Ads, AdMob, AppLovin, ironSource) একসাথে ওয়াটারফল ও বিডিংয়ে যোগ করুন।
2. **নিম্নমানের ও অপ্রাসঙ্গিক অ্যাড ক্যাটাগরি ব্লক করা**:
   - **Ad Controls / Age & Category Filtering**-এ গিয়ে নিম্নমানের বা গেমারদের জন্য বিরক্তিকর ক্যাটাগরি ব্লক করুন:
     - Gambling / Betting, Dating, Alcohol/Tobacco, Sensitive Political/Religious ads, এবং Misleading "Fake Playable" ads ব্লক করুন।
     - আপনার গেমের রেটিং অনুযায়ী **Age Designation** (যেমন General / 12+) সেট করুন।
3. **Rewarded Placement অপটিমাইজেশন**:
   - `Rewarded_Android` (এবং iOS-এ `Rewarded_iOS`) প্লেসমেন্টে **Allow Skip = Disabled** রাখুন যাতে পুরো ভিডিও না দেখে কেউ কয়েন/রিভাইভ না পায়।
   - **Video + Playable End-Card** ফরম্যাট চালু রাখুন—পাজল গেমে Playable Ad-এর কনভার্সন ও eCPM সবচেয়ে বেশি।
   - উচ্চ রিওয়ার্ডেড প্লেসমেন্ট (`2x Coins`, `Bonus Level`, `Revive`) সবার আগে প্রিলোড ও প্রায়োরিটাইজ করা হয়েছে।
4. **Banner ও Interstitial সেফটি**:
   - `Banner_Android`-এর Refresh Rate `30s–45s` রাখুন।
   - Interstitial Pacing ড্যাশবোর্ড ও অ্যাপ লেভেল (৯০ সেকেন্ড cooldown + প্রতি ৩ লেভেল পর) দুই জায়গায় সিঙ্ক রাখুন।

---

## Step-by-Step Release Guide (রিলিজ ও প্রোডাকশন গাইড)

1. **Unity Dashboard-এ অ্যাপ রেজিস্টার করুন**:
   - [Unity Cloud Dashboard](https://dashboard.unity3d.com/) > **Monetization** > **Projects** > **Add Project**।
   - Google Play Store (এবং প্রয়োজনে Apple App Store)-এর জন্য অ্যাপ যোগ করুন।
   - **Project Settings / Ad Units** থেকে আপনার **Unity Game ID** (৬–১০ ডিজিটের সংখ্যা) এবং প্লেসমেন্ট আইডি (`Rewarded_Android`, `Interstitial_Android`, `Banner_Android`) নিশ্চিত করুন।
2. **অ্যাপের ভেতর Game ID সেট করুন (কোড পরিবর্তন ছাড়াই)**:
   - অ্যাপ চালু করে **Promo** বাটন বা **Settings > Promo** খুলুন।
   - আপনার গোপন অ্যাডমিন কোড দিয়ে হিডেন **Admin Panel** ওপেন করুন।
   - **Google Play** সিলেক্ট করুন এবং আপনার কপি করা **Unity Game ID** লিখুন।
3. **রিলিজের আগে Test Mode বন্ধ করুন (গুরুত্বপূর্ণ!)**:
   - টেস্টিং শেষে প্রোডাকশন APK / AAB বানানোর আগে বা রিভিউয়ে পাঠানোর আগে Admin Panel-এ **Unity Ads Test Mode = OFF** করুন এবং **Save & Initialize Ads** চাপুন।
   - অথবা প্রোডাকশন বিল্ডের আগে টেস্ট ডিভাইস থেকেই Test Mode অফ করে ভেরিফাই করে নিন।
   - *(নোট: যেহেতু নতুন ইউজারদের ডিভাইসে Admin Panel ব্যবহার না করা পর্যন্ত Game ID এনক্রিপ্টেড স্টোরেজে থাকে না, তাই প্রোডাকশন বিল্ডের জন্য স্টোর-রেডি ডিস্ট্রিবিউশনে Admin Panel বা প্রথম কনফিগারেশন থেকে Game ID ও `testMode = false` সেট করে ভেরিফাই করুন।)*
4. **ProGuard / R8 এবং Release Signing**:
   - `app/proguard-rules.pro`-তে Unity Ads, `EncryptedSharedPreferences` ও Firestore মডেলগুলোর জন্য প্রয়োজনীয় `-keep` রুল যোগ করা আছে।
   - `app/build.gradle.kts`-এ `isMinifyEnabled = true` চালু করে `.aab` (Android App Bundle) জেনারেট করুন।
5. **Play Store Compliance যাচাই**:
   - Google Play Console-এ **App Content > Data Safety & Advertising ID (AD_ID)** ফর্মে Unity Ads ও Google Sign-In ডাটা ডিক্লারেশন পূরণ করুন।
   - অ্যাপের ভেতরে GDPR / CCPA Privacy Dialog (`Settings > Ad Privacy`) সঠিকভাবে কাজ করছে কি না তা টেস্ট করুন।
