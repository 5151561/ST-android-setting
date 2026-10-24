plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.screenshot)
    alias(libs.plugins.dropshots)
    id("jacoco")
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
        testInstrumentationRunner = "io.github.sanitised.st.STTestRunner"

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

    experimentalProperties["android.experimental.enableScreenshotTest"] = true

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
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
}

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")
    description = "Generates JaCoCo HTML and XML coverage reports for debug unit tests."
    group = "verification"

    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    val fileFilter = listOf(
        "**/R.class",
        "**/R$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Test*.*",
        "android/**/*.*",
        "**/*_HiltModules*.*",
        "**/*_Factory*.*",
        "**/*_MembersInjector*.*",
        "**/Hilt_*.*",
        "**/dagger/**"
    )

    val debugTree = fileTree("${layout.buildDirectory.get().asFile}/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes") {
        exclude(fileFilter)
    }
    val javacTree = fileTree("${layout.buildDirectory.get().asFile}/intermediates/javac/debug/compileDebugJavaWithJavac/classes") {
        exclude(fileFilter)
    }

    sourceDirectories.setFrom(files("$projectDir/src/main/java"))
    classDirectories.setFrom(files(debugTree, javacTree))
    executionData.setFrom(
        fileTree(layout.buildDirectory) {
            include(
                "jacoco/testDebugUnitTest.exec",
                "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec"
            )
        }
    )
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
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.google.material)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.commons.compress)
    implementation(libs.snakeyaml)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    // Coil 3 默认不带网络层,加载 http(s) 图片必须显式引入 OkHttp 网络组件。
    implementation(libs.coil.network.okhttp)

    // Navigation Compose & Navigation 3
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigationevent.compose)

    // Hilt Dependency Injection
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // OkHttp for TavernApiAdapter
    implementation(libs.okhttp)

    // Local Unit & Robolectric UI Testing
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.org.json)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.mockk)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.android.compiler)

    // Instrumented & Device Screenshot Testing
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.dropshots)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.android.compiler)

    // Compose Preview Screenshot Testing
    screenshotTestImplementation(libs.screenshot.validation.api)
    screenshotTestImplementation(libs.androidx.compose.ui.tooling)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
