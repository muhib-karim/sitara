plugins {
    alias(libs.plugins.android.library)

}

android {
    namespace = "app.sitara.music.core.player"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions { unitTests { isIncludeAndroidResources = true } }
}

dependencies {
    api(project(":core:model"))
    api(project(":source:api"))
    api(libs.media3.exoplayer)
    api(libs.media3.session)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
}
