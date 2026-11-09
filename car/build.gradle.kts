plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    // Not for screenshots: Paparazzi's layoutlib gives the template tests a real Android framework on
    // the JVM without downloading a Robolectric SDK image.
    alias(libs.plugins.paparazzi)
}

android {
    namespace = "app.opentaiment.car"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(project(":feature:vehicle"))
    implementation(libs.androidx.car.app)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.car.app.testing)
    testImplementation(libs.kotlinx.coroutines.test)
}

// car-app-testing pulls in Robolectric; its native runtime (~100 MB) isn't needed here.
configurations.matching { it.name.contains("UnitTest") }.configureEach {
    exclude(group = "org.robolectric", module = "nativeruntime-dist-compat")
}
