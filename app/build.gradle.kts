import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.androidx.baselineprofile)
    id("com.google.devtools.ksp")
}

val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) {
        load(file.inputStream())
    }
}

fun envOr(key: String, default: String): String =
    System.getenv(key)?.takeIf { it.isNotBlank() } ?: keystoreProperties.getProperty(key) ?: default

android {
    namespace = "com.example.tujelly"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        targetSdk = 35
        versionCode = 71
        versionName = "2.2.24"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Claves por defecto para TMDB/Trakt. Se pueden sobreescribir en build-time mediante
        // variables de entorno o en keystore.properties (p. ej. en CI con secrets), sin tocar el código.
        buildConfigField("String", "DEFAULT_TMDB_API_KEY", "\"${envOr("TMDB_API_KEY", "82dee22856e0d0ac5f767ec6fb845efc")}\"")
        buildConfigField("String", "DEFAULT_TRAKT_CLIENT_ID", "\"${envOr("TRAKT_CLIENT_ID", "***REMOVED***")}\"")
        buildConfigField("String", "DEFAULT_TRAKT_CLIENT_SECRET", "\"${envOr("TRAKT_CLIENT_SECRET", "***REMOVED***")}\"")
    }

    flavorDimensions += "distribution"

    productFlavors {
        create("beta") {
            dimension = "distribution"
            applicationId = "com.example.tujelly"
            buildConfigField("boolean", "ENABLE_IN_APP_UPDATER", "true")
        }
        create("play") {
            dimension = "distribution"
            applicationId = "tv.medusa.app"
            buildConfigField("boolean", "ENABLE_IN_APP_UPDATER", "false")
        }
    }

    signingConfigs {
        create("release") {
            val storeFilePath = envOr("KEYSTORE_PATH", "debug.keystore")
            storeFile = if (file(storeFilePath).exists()) file(storeFilePath) else rootProject.file(storeFilePath)
            storePassword = envOr("KEYSTORE_PASSWORD", "android")
            keyAlias = envOr("KEY_ALIAS", "androiddebugkey")
            keyPassword = envOr("KEY_PASSWORD", "android")
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
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
    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        // Los esquemas exportados por Room se copian a los assets de androidTest para que
        // MigrationTestHelper pueda validar las migraciones.
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

ksp {
    // Exporta el esquema de Room a app/schemas (necesario para los tests de migración).
    arg("room.schemaLocation", "$projectDir/schemas")
}

baselineProfile {
    // Guarda los perfiles generados en el árbol de fuentes (app/src/<variant>/generated/baselineProfiles).
    saveInSrc = true
    // Optimización de layout de dex para el hot-path de arranque.
    dexLayoutOptimization = true
}

dependencies {
    baselineProfile(project(":baselineprofile"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended:1.7.5")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)

    // Networking & Serialization
    implementation(libs.retrofit)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.kotlinx.serialization)

    // Local Storage & Background Work
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)

    // Navigation & Images
    implementation(libs.navigation.compose)
    implementation(libs.coil.compose)

    // Media Player
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.hls)
    implementation(libs.media3.ffmpeg.decoder)

    // Dependency Injection (Hilt)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler)
    ksp(libs.hilt.androidx.compiler)

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
