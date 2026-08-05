# ==========================================
# AutoBuy Assistant — ProGuard Rules
# ==========================================

# --- Kotlin ---
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes SourceFile,LineNumberTable
-keep class kotlin.Metadata { *; }

# --- Hilt / Dagger ---
-keepnames @dagger.hilt.android.lifecycle.HiltViewModel class * extends androidx.lifecycle.ViewModel
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# --- Room ---
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers @androidx.room.Entity class * { *; }

# --- Moshi ---
-keep class com.squareup.moshi.** { *; }
-keepclassmembers class ** {
    @com.squareup.moshi.FromJson *;
    @com.squareup.moshi.ToJson *;
}
-keep @com.squareup.moshi.JsonClass class *

# --- Google Tink ---
-keep class com.google.crypto.tink.** { *; }

# --- ML Kit ---
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.vision.** { *; }

# --- OkHttp ---
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# --- Accessibility Service (절대 난독화 금지) ---
-keep class com.autobuy.core.accessibility.AutoBuyAccessibilityService { *; }
-keep class com.autobuy.core.accessibility.AutoBuyForegroundService { *; }

# --- Security (암호화 클래스 보호) ---
-keep class com.autobuy.core.security.** { *; }

# --- Recipe Data Models ---
-keep class com.autobuy.core.data.model.** { *; }

# --- 일반 난독화 설정 ---
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose

# --- 디버그 정보 제거 (릴리스) ---
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
