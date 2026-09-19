# Spindle Lite ProGuard / R8 Rules
# Target: Minimal APK footprint (< 1.8 MB), Zero Reflection Overhead

# Keep data models
-keepclassmembers class com.hana.spindle.lite.db.Track { *; }
-keepclassmembers class com.hana.spindle.lite.audio.PlaybackState { *; }
-keepclassmembers class com.hana.spindle.lite.util.AudioHeaderParser* { *; }
-keepclassmembers class com.hana.spindle.lite.util.DeviceNameFormatter { *; }

# Keep Custom Views instantiated from XML
-keep public class com.hana.spindle.lite.ui.LiteDeckView {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Keep BroadcastReceivers
-keep public class com.hana.spindle.lite.receiver.HardwareButtonReceiver { *; }
-keep public class com.hana.spindle.lite.receiver.NoisyAudioReceiver { *; }

# R8 Optimization
-allowaccessmodification
-repackageclasses 'com.hana.spindle.lite.min'
