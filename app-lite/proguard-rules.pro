# Spindle Lite ProGuard Rules
# Target: Minimal APK footprint (< 1.8 MB)

# Keep data models
-keepclassmembers class com.hana.spindle.lite.db.Track { *; }
-keepclassmembers class com.hana.spindle.lite.audio.PlaybackState { *; }

# Optimization settings
-repackageclasses 'com.hana.spindle.lite.min'
-allowaccessmodification
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
