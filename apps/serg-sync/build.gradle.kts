plugins {
    id("buildsrc.convention.kotlin-jvm")
    application
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    implementation(project(":tjenestespesifikasjoner:serg"))
    implementation(project(":libs:ktor-utils"))
    implementation(project(":libs:kotlin-utils"))
    implementation(libs.bundles.kotlinxEcosystem)
    implementation(libs.kotliQuery)
    implementation(libs.flyway)
    implementation(libs.flyway.postgresql)
    implementation(libs.hikari)
    implementation(libs.postgresql)

    testImplementation(libs.bundles.testEcosystem)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.junitApi)
    testImplementation(libs.assertk)
    testRuntimeOnly(libs.junitPlatformLauncher)
}

application {
    mainClass = "no.kartverket.matrikkel.MainKt"
}
