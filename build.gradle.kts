plugins {
    id("com.android.application") version "8.3.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("com.google.devtools.ksp") version "1.9.22-1.0.17" apply false
    // JVM screenshot tests of the Kash design-system components (test-only).
    id("app.cash.paparazzi") version "1.3.3" apply false
}
