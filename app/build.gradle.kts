import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// 发布签名配置存放在 local.properties（不入库）：flikky.keystore.path / flikky.keystore.password / flikky.key.alias / flikky.key.password
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.example.flikky"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.flikky"
        minSdk = 33
        targetSdk = 36
        // versionCode 公式 major*10000+minor*100+patch，与 versionName 对应且单调递增。
        versionCode = 11900
        versionName = "1.19.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        val keystorePath = localProperties.getProperty("flikky.keystore.path")
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = localProperties.getProperty("flikky.keystore.password")
                keyAlias = localProperties.getProperty("flikky.key.alias")
                keyPassword = localProperties.getProperty("flikky.key.password")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 无 keystore 的机器仍可构建（产物为 unsigned），签名机自动出正式签名包。
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        // AGP 8 起 BuildConfig 默认不生成。开它是为了让 versionName 有唯一事实源：
        // 发版只改本文件的 versionName，浏览器「关于」面板的版本号自动跟随
        // （PeerInfoDto.appVersion ← BuildConfig.VERSION_NAME）。
        buildConfig = true
    }
    androidResources {
        generateLocaleConfig = true
    }
    packaging {
        resources {
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties",
            )
        }
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.coil.compose)
    implementation(libs.coil.video)
    implementation(libs.material.kolor) {
        exclude(group = "org.jetbrains.compose.runtime")
        exclude(group = "org.jetbrains.compose.ui")
        exclude(group = "org.jetbrains.compose.material3")
    }

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.cio)
    testImplementation(libs.ktor.client.websockets)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.ktor.client.cio)
    androidTestImplementation(libs.ktor.client.websockets)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

/**
 * v1.19.0: 浏览器端测试（Node 内建 node:test + 两个自研 DOM 脚本）纳入 `check`。
 * 它们不是 JVM 测试，Gradle 只做一层包装 —— 但必须进门禁，
 * 否则 `testDebugUnitTest` 全绿会给出「web 也没坏」的错觉。
 */
tasks.register<Exec>("webTest") {
    group = "verification"
    description = "Runs the browser-side test suite (node scripts/test-web.mjs)"
    workingDir = rootProject.projectDir
    commandLine("node", "scripts/test-web.mjs")
}

tasks.named("check") { dependsOn("webTest") }
