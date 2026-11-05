plugins {
    id("com.android.application") version "8.9.1" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
    // JVM screenshot tests of the Kash design-system components (test-only).
    id("app.cash.paparazzi") version "1.3.5" apply false
    // Uploads the R8 mapping so Sentry crash reports show real class names.
    id("io.sentry.android.gradle") version "6.23.0" apply false
}
