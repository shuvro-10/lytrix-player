# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# 🔴 NewPipe Extractor Rules (Don't Delete)
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**

# 🔴 Rhino JavaScript Engine Rules (For PO Token)
-keep class org.mozilla.javascript.** { *; }
-dontwarn org.mozilla.javascript.**
-keep class org.mozilla.classfile.ClassFileWriter

# 🔴 Protobuf Missing Class Warning Ignore
-dontwarn com.google.protobuf.**
-keep class com.google.protobuf.** { *; }

# 🔴 Rhino Script Engine Warning Ignore
-dontwarn javax.script.**
-dontwarn java.beans.**
-dontwarn org.mozilla.javascript.**

# 🔴 Disable class merging in R8 to prevent ART VerifyError
-optimizations !class/merging/*

# 🔴 Keep Compose UI classes safe
-keep class com.musicplayer.lytrix.ui.** { *; }
-keep class com.musicplayer.lytrix.ui.PlayerScreenKt { *; }

# 🔴 jaudiotagger Audio Metadata Rules
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.swing.**
-dontwarn org.jaudiotagger.test.**
-keep class org.jaudiotagger.** { *; }

# 🔴 TorrentStream & libtorrent4j & NanoHTTPD & Media3 FFmpeg Rules
-keep class org.libtorrent4j.** { *; }
-keep class org.libtorrent4j.swig.** { *; }
-dontwarn org.libtorrent4j.**
-dontwarn fi.iki.elonen.**
-keep class androidx.media3.decoder.ffmpeg.** { *; }
-dontwarn androidx.media3.decoder.ffmpeg.**




