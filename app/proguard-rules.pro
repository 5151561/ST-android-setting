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

# ── Debug symbols ─────────────────────────────────────────────────────────────
# Retain enough information for readable crash stack traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
