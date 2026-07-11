plugins {
    id("com.android.application") version "9.2.1" apply false
    // AGP 9 内置 Kotlin 支持(built-in Kotlin),不再应用 org.jetbrains.kotlin.android。
    // Compose 编译器插件版本需与 AGP 内嵌的 Kotlin Gradle Plugin 版本一致(AGP 9.2 为 2.3.10)。
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.10" apply false
    // serialization 编译器插件同样跟随 AGP 内嵌 Kotlin 版本。
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.10" apply false
}
