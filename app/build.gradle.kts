// CI pipeline test
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("com.google.gms.google-services")
    id("com.google.firebase.appdistribution")
    id("com.google.firebase.crashlytics")
}

android {
    namespace = "com.averycorp.prismtask"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.averycorp.prismtask"
        minSdk = 26
        targetSdk = 35
        versionCode = 114
        versionName = "1.3.23"

        testInstrumentationRunner = "com.averycorp.prismtask.HiltTestRunner"
        vectorDrawables { useSupportLibrary = true }

        buildConfigField(
            "String",
            "WEB_CLIENT_ID",
            "\"${System.getenv("WEB_CLIENT_ID") ?: "403186103462-j09m2o9781jgnpb2eqotn65jdcg7qgqj.apps.googleusercontent.com"}\""
        )
    }

    val keystorePath = System.getenv("KEYSTORE_PATH")
    val hasReleaseSigning = keystorePath != null && file(keystorePath).exists()

    signingConfigs {
        // Override the default auto-generated ~/.android/debug.keystore with
        // a stable keystore committed to the repo. Without this, every CI
        // runner signs the debug APK with a freshly-generated key, so each
        // release has a different signature and the in-app updater's
        // installed-signature check rejects every update.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
                keyAlias = System.getenv("KEY_ALIAS") ?: "prismtask"
                keyPassword = System.getenv("KEY_PASSWORD") ?: ""
            }
        }
    }
    buildTypes {
        debug {
            // Point debug builds at the live Railway backend so that sideloaded
            // debug APKs can actually reach the Claude-Haiku parse endpoint.
            // Override with a `API_BASE_URL_DEBUG` env var if you need to
            // target a local FastAPI server from the emulator (use
            // "http://10.0.2.2:8000" for emulator → host loopback).
            val debugApiUrl = System.getenv("API_BASE_URL_DEBUG")
                ?: "https://averytask-production.up.railway.app"
            buildConfigField("String", "API_BASE_URL", "\"$debugApiUrl\"")
            // Speed up debug builds
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            firebaseAppDistribution {
                groups = "testers"
            }
        }
        release {
            buildConfigField("String", "API_BASE_URL", "\"https://averytask-production.up.railway.app\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes.getByName("release") {
        configure<com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension> {
            mappingFileUploadEnabled = true
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.jvmArgs("-Xshare:off")
            }
        }
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1,INDEX.LIST,DEPENDENCIES}" }
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

ksp {
    arg("dagger.hilt.disableModulesHaveInstallInCheck", "true")
}

// Copy built AAB files to the repository root
android.applicationVariants.all {
    val variant = this
    tasks.named("bundle${variant.name.replaceFirstChar { it.uppercase() }}").configure {
        doLast {
            val aabDir = file("${project.layout.buildDirectory.get()}/outputs/bundle/${variant.name}")
            aabDir.listFiles()?.filter { it.extension == "aab" }?.forEach { aab ->
                aab.copyTo(rootProject.layout.projectDirectory.file(aab.name).asFile, overwrite = true)
                println("Copied ${aab.name} to project root")
            }
        }
    }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // Room
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.59.2")
    ksp("com.google.dagger:hilt-compiler:2.59.2")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    // Gson
    implementation("com.google.code.gson:gson:2.11.0")

    // OkHttp
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Encrypted storage
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Reorderable (drag-to-reorder for LazyColumn)
    implementation("sh.calvin.reorderable:reorderable:2.4.3")

    // Glance Widgets
    implementation("androidx.glance:glance-appwidget:1.1.0")
    implementation("androidx.glance:glance-material3:1.1.0")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.6.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("com.google.firebase:firebase-crashlytics-ktx")

    // Credential Manager (Google Sign-In)
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Google Drive API
    implementation("com.google.android.gms:play-services-auth:21.3.0")
    implementation("com.google.api-client:google-api-client-android:2.7.2")
    implementation("com.google.apis:google-api-services-drive:v3-rev20241206-2.0.0")

    // Google Calendar API
    implementation("com.google.apis:google-api-services-calendar:v3-rev20241101-2.0.0")
    implementation("com.google.http-client:google-http-client-gson:1.45.3")

    // Google Play Billing
    implementation("com.android.billingclient:billing-ktx:7.1.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.room:room-testing:2.8.4")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("app.cash.turbine:turbine:1.1.0")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.room:room-testing:2.8.4")
    androidTestImplementation("androidx.arch.core:core-testing:2.2.0")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation("com.google.dagger:hilt-android-testing:2.59.2")
    androidTestImplementation("io.mockk:mockk-android:1.13.13")
    kspAndroidTest("com.google.dagger:hilt-compiler:2.59.2")

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}