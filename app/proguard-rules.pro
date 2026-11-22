# Targeted R8 keep rules — replaces the historical `-keep class org.fossify.** { *; }`
# wildcard that previously prevented R8 from shrinking/inlining app and commons code.

# Gson-serialized and Java Serializable data models (no @SerializedName annotations)
-keep class org.fossify.gallery.models.** { *; }
-keep class org.fossify.commons.models.** { *; }
-keep class org.fossify.gallery.helpers.FolderPair { *; }
-keep class org.fossify.gallery.helpers.SettingsBackupHelper$Entry { *; }
-keep class org.fossify.gallery.helpers.SettingsBackupHelper$BundleEnvelope { *; }

# Keep custom View constructors for XML inflation alongside aapt_rules.txt
-keepclassmembers class org.fossify.gallery.views.** {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}
-keepclassmembers class org.fossify.commons.views.** {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Keep local JNI photo filter bridge (src/main/jni) and JXL native decoder
-keep class com.zomato.photofilters.imageprocessors.NativeImageProcessor { *; }
-keep class com.awxkee.jxlcoder.** { *; }

-dontwarn android.graphics.Canvas
-dontwarn org.fossify.**

# OkHttp
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
-dontwarn org.codehaus.mojo.animal_sniffer.*
-dontwarn okhttp3.internal.platform.ConscryptPlatform

-keepclassmembers class * implements android.os.Parcelable {
    static ** CREATOR;
}

# RenderScript / JNI
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class androidx.renderscript.** { *; }

# Reprint
-keep class com.github.ajalt.reprint.module.** { *; }
