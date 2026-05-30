# ── JavaScript bridge ─────────────────────────────────────────────────────────
# @JavascriptInterface methods are called by the WebView JS engine reflectively;
# R8 cannot trace these call sites and would otherwise strip them.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ── SnakeYAML ─────────────────────────────────────────────────────────────────
# SnakeYAML instantiates its own constructors, representers, and resolvers
# reflectively at runtime. Preserve the entire library so yaml.load() and
# yaml.dump() continue to work after shrinking.
-keep class org.yaml.snakeyaml.** { *; }

# ── OkHttp / Okio ─────────────────────────────────────────────────────────────
# OkHttp 4.x ships consumer rules inside its AAR, but suppress any residual
# warnings that surface when those rules interact with R8's strict mode.
-dontwarn okhttp3.**
-dontwarn okio.**

# ── Debug symbols ─────────────────────────────────────────────────────────────
# Retain enough information for readable crash stack traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
