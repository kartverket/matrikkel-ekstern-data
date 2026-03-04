plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    implementation(project(":libs:logging"))
    implementation(libs.nimbusOidcSDK)
    implementation(libs.caffeine)

    testImplementation(libs.bundles.testEcosystem)
    testRuntimeOnly(libs.junitPlatformLauncher)
}