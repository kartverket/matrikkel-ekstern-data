plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    implementation(libs.kafkaLight)
    implementation(libs.tokenClient)
    implementation(libs.ktorUtils)
    implementation(libs.kotlinUtils)
    implementation(libs.bundles.kotlinxEcosystem)

    implementation(libs.kotliQuery)
    implementation(libs.flyway)
    implementation(libs.flyway.postgresql)
    implementation(libs.hikari)
    implementation(libs.postgresql)

    testImplementation(libs.bundles.testEcosystem)
    testRuntimeOnly(libs.junitPlatformLauncher)
}