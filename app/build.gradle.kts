plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

val fallbackVersionName = "0.4.0"

android {
    namespace = "io.github.sanitised.st"
    compileSdk = 37

    fun envOrProp(name: String): String? =
        (findProperty(name) as String?)?.takeIf { it.isNotBlank() }
            ?: System.getenv(name)?.takeIf { it.isNotBlank() }

    fun firstNonBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() }

    val releaseStoreFile = envOrProp("RELEASE_STORE_FILE")
    val releaseStorePassword = envOrProp("RELEASE_STORE_PASSWORD")
    val releaseKeyAlias = envOrProp("RELEASE_KEY_ALIAS")
    val releaseKeyPassword = firstNonBlank(envOrProp("RELEASE_KEY_PASSWORD"), releaseStorePassword)
    val releaseSigningAvailable = !releaseStoreFile.isNullOrBlank()
        && !releaseStorePassword.isNullOrBlank()
        && !releaseKeyAlias.isNullOrBlank()

    defaultConfig {
        applicationId = "io.github.sanitised.st"
        minSdk = 26
        targetSdk = 36

        val githubTag = run {
            val refType = System.getenv("GITHUB_REF_TYPE")
            val refName = System.getenv("GITHUB_REF_NAME")
            val ref = System.getenv("GITHUB_REF")
            when {
                refType == "tag" && !refName.isNullOrBlank() -> refName
                ref?.startsWith("refs/tags/") == true -> ref.removePrefix("refs/tags/")
                else -> null
            }
        }

        val versionNameOverride = firstNonBlank(
            envOrProp("VERSION_NAME"),
            githubTag
        )?.removePrefix("v")

        val versionCodeOverride = firstNonBlank(
            envOrProp("VERSION_CODE"),
            System.getenv("GITHUB_RUN_NUMBER")
        )

        versionCode = versionCodeOverride?.toIntOrNull() ?: 2
        versionName = versionNameOverride ?: fallbackVersionName
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            if (releaseSigningAvailable) {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            versionNameSuffix = "-dev"
            applicationIdSuffix = ".dev"
            resValue("string", "app_name", "ST dev")
        }
        release {
            isMinifyEnabled = true
            if (releaseSigningAvailable) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // built-in Kotlin 下 jvmTarget 默认跟随 compileOptions.targetCompatibility(17),
    // Compose 编译器由 org.jetbrains.kotlin.plugin.compose 插件接管,无需 composeOptions。
    buildFeatures {
        compose = true
        // AGP 9 起 resValues 默认关闭;debug 变体的 resValue("app_name") 需要它。
        resValues = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }

    testOptions {
        unitTests.all {
            val hostReleaseDir = rootProject.file("rust/tt-android-jni/target/release").absolutePath
            val hostDebugDir = rootProject.file("rust/tt-android-jni/target/debug").absolutePath
            val existingLibPath = System.getProperty("java.library.path").orEmpty()
            val combinedPath = listOf(hostReleaseDir, hostDebugDir, existingLibPath)
                .filter { it.isNotBlank() }
                .joinToString(File.pathSeparator)
            it.systemProperty("java.library.path", combinedPath)
        }
    }
}

val buildRustJniHost by tasks.registering(Exec::class) {
    description = "Builds libtauritavern_jni for the host OS (for JVM unit tests) and stages default assets."
    group = "build"
    workingDir = rootProject.projectDir
    commandLine("bash", "ci/scripts/build_rust_jni.sh", "host")
}

val buildRustJniAndroid by tasks.registering(Exec::class) {
    description = "Builds libtauritavern_jni.so for Android ABIs (if NDK is present) and stages default assets."
    group = "build"
    workingDir = rootProject.projectDir
    commandLine("bash", "ci/scripts/build_rust_jni.sh", "all")
}

tasks.withType<Test>().configureEach {
    dependsOn(buildRustJniHost)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(buildRustJniAndroid)
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("org.apache.commons:commons-compress:1.28.0")
    implementation("org.yaml:snakeyaml:2.6")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("io.coil-kt.coil3:coil-compose:3.5.0")
    // Coil 3 默认不带网络层,加载 http(s) 图片必须显式引入 OkHttp 网络组件。
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.5.0")

    // Navigation Compose
    implementation("androidx.navigation:navigation-compose:2.9.8")

    // Hilt Dependency Injection
    implementation("com.google.dagger:hilt-android:2.60.1")
    ksp("com.google.dagger:hilt-compiler:2.60.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.3.0")

    // OkHttp for TavernApiAdapter
    implementation("com.squareup.okhttp3:okhttp:5.4.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:5.4.0")
    testImplementation("org.json:json:20240303")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
