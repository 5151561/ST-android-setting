# ── JavaScript bridge ─────────────────────────────────────────────────────────
# @JavascriptInterface methods are called by the WebView JS engine reflectively;
# R8 cannot trace these call sites and would otherwise strip them.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ── SnakeYAML ─────────────────────────────────────────────────────────────────
# This app only calls yaml.load<Any?>() and yaml.dump(Map); JavaBean
# introspection is never triggered. Keep only the two classes directly
# instantiated in app code; R8 traces the reachable call graph from there.
-keep class org.yaml.snakeyaml.Yaml { *; }
-keep class org.yaml.snakeyaml.DumperOptions { *; }
-keep class org.yaml.snakeyaml.DumperOptions$** { *; }
# Yaml's default constructor creates a Representer, which references
# PropertyUtils, which references java.beans.* — absent from Android's
# bootclasspath. That code path is dead for Map/List loads, but the class-file
# reference exists. Suppress the missing-class diagnostic.
-dontwarn java.beans.**

# ── OkHttp / Okio ─────────────────────────────────────────────────────────────
# OkHttp 4.x ships consumer rules inside its AAR, but suppress any residual
# warnings that surface when those rules interact with R8's strict mode.
-dontwarn okhttp3.**
-dontwarn okio.**

# ── Apache Commons Compress ───────────────────────────────────────────────────
# commons-compress references optional third-party compression backends (zstd-jni,
# XZ/tukaani, Brotli) that this app does not depend on. Those code paths are dead
# for the formats we use, but the class-file references remain and trip R8's
# missing-class check. Suppress the diagnostics.
-dontwarn com.github.luben.zstd.**
-dontwarn org.tukaani.xz.**
-dontwarn org.brotli.dec.**

# ── Debug symbols ─────────────────────────────────────────────────────────────
# Retain enough information for readable crash stack traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
