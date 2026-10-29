import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("app.cash.paparazzi")
}

val localProperties = Properties().apply {
    val propsFile = rootProject.file("local.properties")
    if (propsFile.exists()) {
        propsFile.inputStream().use { load(it) }
    }
}

fun buildConfigString(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
    namespace = "com.financeapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "et.kash.app"
        minSdk = 23
        targetSdk = 36
        versionCode = 34
        versionName = "1.0.14"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Supabase config (set these in local.properties; do not commit secrets)
        buildConfigField(
            "String",
            "SUPABASE_URL",
            buildConfigString(localProperties.getProperty("supabase.url", ""))
        )
        buildConfigField(
            "String",
            "SUPABASE_ANON_KEY",
            buildConfigString(localProperties.getProperty("supabase.anonKey", ""))
        )
        buildConfigField(
            "String",
            "GOOGLE_WEB_CLIENT_ID",
            buildConfigString(localProperties.getProperty("google.webClientId", ""))
        )
    }

    // Release-signing config — paths + creds come from local.properties so
    // they stay out of the repo. The keystore itself lives at
    // ~/.keystores/finance-lore-release.jks (gitignored by being outside the
    // project entirely). Falls back to a no-op block if the props are absent
    // so a clean checkout can still produce a debug build.
    signingConfigs {
        create("release") {
            val keystorePath  = localProperties.getProperty("keystore.path", "")
            val keystorePwd   = localProperties.getProperty("keystore.password", "")
            val keystoreAlias = localProperties.getProperty("keystore.alias", "")
            if (keystorePath.isNotBlank() && keystorePwd.isNotBlank() && keystoreAlias.isNotBlank()) {
                storeFile = file(keystorePath)
                storePassword = keystorePwd
                keyAlias = keystoreAlias
                keyPassword = keystorePwd
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Only attach the signing config when the keystore is actually
            // configured — otherwise Gradle will fail on a clean checkout
            // that hasn't been set up with `keystore.path` in local.properties.
            val keystorePath = localProperties.getProperty("keystore.path", "")
            if (keystorePath.isNotBlank() && file(keystorePath).exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// Paparazzi finds the SDK through ANDROID_HOME; point it at the one in local.properties.
tasks.withType<Test>().configureEach {
    environment("ANDROID_HOME", android.sdkDirectory.absolutePath)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.google.android.gms:play-services-auth:21.0.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("androidx.credentials:credentials:1.2.1")
    implementation("androidx.credentials:credentials-play-services-auth:1.2.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    // Paparazzi's layoutlib needs the JVM Guava variant, not the Android one.
    constraints {
        testImplementation("com.google.guava:guava") {
            attributes {
                attribute(
                    TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
                    objects.named(TargetJvmEnvironment::class.java, TargetJvmEnvironment.STANDARD_JVM)
                )
            }
        }
    }
}
