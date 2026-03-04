plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    implementation(project(":libs:logging"))
    api(libs.nimbusOidcSDK)
    api(libs.caffeine)

    testImplementation(libs.bundles.testEcosystem)
    testRuntimeOnly(libs.junitPlatformLauncher)
}