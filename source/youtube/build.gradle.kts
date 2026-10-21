import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

tasks.test {
    useJUnitPlatform { excludeTags("network") }
}

// Live YouTube checks stay out of `test`, so the unit suite never needs the network. CI runs this in a non-blocking job.
tasks.register<Test>("networkSmokeTest") {
    description = "Resolves the acceptance track against live YouTube."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform { includeTags("network") }
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
    outputs.upToDateWhen { false }
    outputs.cacheIf { false }
}

dependencies {
    api(project(":source:api"))
    // The lyrics cache and LRC parsing YouTube Music's lyrics share with LRCLIB.
    implementation(project(":source:lyrics"))
    implementation(libs.newpipe.extractor)
    implementation(libs.okhttp)
    implementation(libs.nanojson)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.junit5.bom))
    testImplementation(libs.junit5.jupiter)
    testRuntimeOnly(libs.junit5.launcher)
}
