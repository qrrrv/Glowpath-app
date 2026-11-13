plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.musicplayer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.musicplayer"
        minSdk = 26 // Как ты просил изначально
        targetSdk = 36
        versionCode = 8
        versionName = "1.0-http"

        // Embed the identity of this APK, not the latest build available online.
        val revision = providers.environmentVariable("GITHUB_SHA").orNull.orEmpty()
            .takeIf { it.matches(Regex("[a-fA-F0-9]{40}")) }.orEmpty()
        val buildNumber = providers.environmentVariable("GITHUB_RUN_NUMBER").orNull.orEmpty()
            .takeIf { it.matches(Regex("[0-9]+")) }.orEmpty()
        buildConfigField("String", "SOURCE_REVISION", "\"$revision\"")
        buildConfigField("String", "CI_BUILD_NUMBER", "\"$buildNumber\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val stableKeystorePath = System.getenv("GLOWPATH_KEYSTORE_PATH").orEmpty()
    val stableKeystorePassword = System.getenv("GLOWPATH_KEYSTORE_PASSWORD").orEmpty()
    val stableKeyAlias = System.getenv("GLOWPATH_KEY_ALIAS").orEmpty()
    val stableKeyPassword = System.getenv("GLOWPATH_KEY_PASSWORD").orEmpty()

    signingConfigs {
        if (stableKeystorePath.isNotBlank()) {
            create("glowpathStable") {
                storeFile = file(stableKeystorePath)
                storePassword = stableKeystorePassword
                keyAlias = stableKeyAlias
                keyPassword = stableKeyPassword
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions.unitTests.isIncludeAndroidResources = true

    // В Kotlin 2.0.21 блок composeOptions больше НЕ нужен, 
    // так как используется специальный плагин Compose Compiler.

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    buildTypes {
        release {
            // Release = R8 (инлайнинг, удаление мёртвого кода) + не-debuggable ART.
            // Именно эту сборку надо мерить на FPS: debug-сборка Compose заметно медленнее.
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Подписываем тем же ключом, что и debug — APK ставится поверх без удаления данных
            if (stableKeystorePath.isNotBlank()) {
                signingConfig = signingConfigs.getByName("glowpathStable")
            }
        }
        debug {
            isMinifyEnabled = false
            if (stableKeystorePath.isNotBlank()) {
                signingConfig = signingConfigs.getByName("glowpathStable")
            }
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.10.0")

    // Устанавливает baseline profile (baseline-prof.txt) при первом запуске — AOT-компиляция кода приложения
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    // Compose BOM — обновлён для совместимости с material3 1.5.x (Expressive)
    implementation(platform("androidx.compose:compose-bom:2025.05.00"))

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")

    // Иконки и дополнительные функции
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("io.coil-kt:coil-compose:2.7.0")
    // Coil disk cache support (okio)
    implementation("com.squareup.okio:okio:3.9.0")

    // Jsoup — HTML parser for hitmos.me online search
    implementation("org.jsoup:jsoup:1.18.1")
    implementation("androidx.compose.ui:ui-text-google-fonts")
    implementation("androidx.navigation:navigation-compose:2.8.7")

    // Материал дизайн (XML поддержка)
    implementation("com.google.android.material:material:1.12.0")

    // Media Session — для уведомления с кнопками управления
    implementation("androidx.media:media:1.7.0")

    // Palette API — извлечение доминирующего цвета из обложки альбома
    implementation("androidx.palette:palette-ktx:1.0.0")

    // Haze — hardware-accelerated glassmorphism blur
    implementation("dev.chrisbanes.haze:haze:1.3.1")
    implementation("dev.chrisbanes.haze:haze-materials:1.3.1")

    // Liquid Glass — стеклянное меню с рефракцией (JitPack)
    implementation("com.github.QWEA0:liquidglass:v2.0.11")

    // Lottie — JSON анимации
    implementation("com.airbnb.android:lottie-compose:6.4.0")

    // Local HTTP server for Hikka / external now-playing
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
}
