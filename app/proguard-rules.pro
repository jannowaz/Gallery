# Minimal R8 keep rules on top of proguard-android-optimize.txt and library consumer rules
# (Gson >= 2.11 @SerializedName, Room, Coroutines, Compose, Media3, Coil, AndroidX, AAPT2).
#
# - Gson models (AlbumCover, FolderPair, SettingsBackupHelper.Entry/BundleEnvelope) carry
#   @SerializedName + no-arg constructors and are preserved automatically by Gson's consumer rules.
# - Local JNI bridge (NativeImageProcessor) is preserved automatically by proguard-android-optimize's
#   `native <methods>` rule.
# - XML-inflated views (including Fossify Commons views) are preserved automatically by AAPT2.

# JXL native decoder (io.github.awxkee:jxl-coder:2.6.1 ships an empty proguard.txt and looks up
# Java/Kotlin bridge types from C++ JNI via FindClass).
-keep class com.awxkee.jxlcoder.** { *; }

# Reprint loads the Marshmallow fingerprint module reflectively via Class.forName in Reprint.initialize().
-keep class com.github.ajalt.reprint.module.marshmallow.MarshmallowReprintModule {
    public <init>(...);
}
