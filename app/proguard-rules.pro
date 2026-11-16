# ==============================================================================
# Pixel Arrow - ProGuard / R8 Release Rules
# ==============================================================================

# --- Unity Ads SDK Rules ---
-keepattributes SourceFile,LineNumberTable
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.unity3d.ads.** { *; }
-keep class com.unity3d.services.** { *; }
-dontwarn com.unity3d.**
-dontwarn com.google.ads.**

# --- AndroidX Security Crypto (EncryptedSharedPreferences) ---
-keep class androidx.security.crypto.** { *; }
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# --- Firebase & Firestore Data Models ---
-keepattributes Signature
-keepattributes *Annotation*
-keepclassmembers class com.example.data.model.** { *; }
-keep class com.example.data.model.** { *; }

# --- Kotlin Coroutines ---
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.flow.**
