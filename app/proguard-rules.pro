# Spindle R8 Shrinking & Optimization Rules

# Keep Room database models and schemas
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Keep Media3 session & exoplayer components
-keep class androidx.media3.session.** { *; }
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.common.** { *; }

# Keep Spindle data entities and themes
-keep class com.hana.spindle.data.db.** { *; }
-keep class com.hana.spindle.theme.** { *; }

# Strip unnecessary debug logs in release builds to keep APK < 6.5MB
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}
