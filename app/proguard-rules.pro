# =========================================================================
# Spindle DAP Launcher - ProGuard & R8 Optimization Rules
# Optimized for Low-RAM DAP Hardware (<25MB Heap, <8MB APK footprint)
# =========================================================================

# 1. Custom Canvas Views (Inflated dynamically by LayoutInflater from XML)
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(...);
}
-keep class com.hana.spindle.ui.cassette.** { *; }
-keep class com.hana.spindle.ui.eq.** { *; }
-keep class com.hana.spindle.ui.catalog.** { *; }

# 2. Room Database Models & DAOs
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.Entity class * { *; }
-keep class com.hana.spindle.data.db.** { *; }
-dontwarn androidx.room.paging.**

# 3. AndroidX Media3 Audio Architecture
-keep class androidx.media3.session.** { *; }
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.extractor.** { *; }
-keep class androidx.media3.common.** { *; }
-dontwarn androidx.media3.**

# 4. Spindle Audio Engine & DSP Controllers
-keep class com.hana.spindle.playback.** { *; }
-keep class com.hana.spindle.theme.** { *; }
-keep class com.hana.spindle.SpindleApp { *; }

# 5. Lockscreen & Home AppWidget
-keep class com.hana.spindle.widget.** { *; }

# 6. Kotlin Coroutines Service Loading
-dontwarn kotlinx.coroutines.**
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# 7. Strip verbose/debug logs in release builds to eliminate string allocations
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}
