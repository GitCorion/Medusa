import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.androidx.baselineprofile)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.ksp)
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
    namespace = "tv.medusa.app"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        targetSdk = 35
        versionCode = 106
        versionName = "2.2.59"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Claves para TMDB/Trakt. Los valores reales se inyectan en build-time vía variables de
        // entorno o keystore.properties (gitignored); aquí solo hay defaults vacíos. Si falta una
        // clave, el campo queda vacío y la funcionalidad correspondiente se desactiva en runtime.
        buildConfigField("String", "DEFAULT_TMDB_API_KEY", "\"${envOr("TMDB_API_KEY", "")}\"")
        buildConfigField("String", "DEFAULT_TRAKT_CLIENT_ID", "\"${envOr("TRAKT_CLIENT_ID", "")}\"")
        buildConfigField("String", "DEFAULT_TRAKT_CLIENT_SECRET", "\"${envOr("TRAKT_CLIENT_SECRET", "")}\"")
    }

    flavorDimensions += "distribution"

    productFlavors {
        create("beta") {
            dimension = "distribution"
            // Package propio: la beta y la versión de Play conviven en el mismo dispositivo,
            // con datos, firma y actualizaciones independientes.
            applicationIdSuffix = ".beta"
            // Offset de 1.000.000 para distinguir el canal en soporte/Crashlytics.
            versionCode = 1_000_106
            versionNameSuffix = "-beta"
            buildConfigField("boolean", "ENABLE_IN_APP_UPDATER", "true")
        }
        create("play") {
            dimension = "distribution"
            applicationId = "tv.medusa.app"
            // Usa el versionCode base de `defaultConfig` (93, 94…): nunca actualiza fuera de Play.
            buildConfigField("boolean", "ENABLE_IN_APP_UPDATER", "false")
        }
    }

    signingConfigs {
        create("release") {
            // Sin fallback a debug.keystore: si no hay keystore de release configurado, el build
            // de release debe fallar en lugar de firmarse con la clave de debug (prohibido en Play).
            val storeFilePath = envOr("KEYSTORE_PATH", "")
            storeFile = if (storeFilePath.isNotBlank()) {
                if (file(storeFilePath).exists()) file(storeFilePath) else rootProject.file(storeFilePath)
            } else {
                rootProject.file("release.keystore") // no existirá -> error claro al firmar release
            }
            storePassword = envOr("KEYSTORE_PASSWORD", "")
            keyAlias = envOr("KEY_ALIAS", "")
            keyPassword = envOr("KEY_PASSWORD", "")
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
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
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
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
    implementation(libs.media3.session)
    implementation(libs.media3.hls)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.ffmpeg.decoder)

    // Dependency Injection (Hilt)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler)
    ksp(libs.hilt.androidx.compiler)

    // Firebase (Analytics, Crashlytics)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)

    // Google Play In-App Billing (Medusa Pro)
    implementation(libs.play.billing)

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

// ─── Firma de release: fallo temprano y con mensaje claro ────────────────────
// Evita que un `assembleRelease`/`bundleRelease` se firme silenciosamente con una
// clave incorrecta cuando falta keystore.properties. Se comprueba en configuración
// a partir de los nombres de tarea solicitados; los builds de debug no se ven afectados.
val buildingRelease = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }
if (buildingRelease && envOr("KEYSTORE_PATH", "").isBlank()) {
    throw GradleException(
        "Firma de release no configurada. Añade KEYSTORE_PATH, KEYSTORE_PASSWORD, " +
        "KEY_ALIAS y KEY_PASSWORD a keystore.properties (o como variables de entorno) " +
        "antes de compilar el release."
    )
}
