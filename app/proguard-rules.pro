# Targeted R8 keep rules — minimal ruleset on top of proguard-android-optimize.txt and
# library consumer rules (Room, Coroutines, Compose, Media3, Coil, AndroidX, OkHttp, AAPT2).

# Gson-serialized models without @SerializedName annotations (only these classes are read/written
# via Gson; the other 17 classes in org.fossify.gallery.models.* are Room/Kotlin models and stay
# free for full R8 shrinking, inlining, and obfuscation).
-keep class org.fossify.gallery.models.AlbumCover { *; }
-keep class org.fossify.gallery.helpers.FolderPair { *; }
-keep class org.fossify.gallery.helpers.SettingsBackupHelper$Entry { *; }
-keep class org.fossify.gallery.helpers.SettingsBackupHelper$BundleEnvelope { *; }

# Upstream Fossify Commons models & XML-inflated views (commons 6.1.6 does not bundle full rules)
-keep class org.fossify.commons.models.** { *; }
-keepclassmembers class org.fossify.commons.views.** {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Local JNI photo filter bridge (src/main/jni) and JXL native decoder
-keep class com.zomato.photofilters.imageprocessors.NativeImageProcessor { *; }
-keep class com.awxkee.jxlcoder.** { *; }

# Reprint loads fingerprint modules reflectively via Reprint.initialize()
-keep class com.github.ajalt.reprint.module.** { *; }

-dontwarn android.graphics.Canvas
-dontwarn org.fossify.**
