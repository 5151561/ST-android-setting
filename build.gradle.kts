plugins {
    alias(libs.plugins.android.application) apply false
    // AGP 9 内置 Kotlin 支持(built-in Kotlin),不再应用 org.jetbrains.kotlin.android。
    // Compose 编译器插件版本需与 AGP 内嵌的 Kotlin Gradle Plugin 版本一致(AGP 9.2 为 2.3.10)。
    alias(libs.plugins.kotlin.plugin.compose) apply false
    // serialization 编译器插件同样跟随 AGP 内嵌 Kotlin 版本。
    alias(libs.plugins.kotlin.plugin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.screenshot) apply false
    alias(libs.plugins.dropshots) apply false
}
