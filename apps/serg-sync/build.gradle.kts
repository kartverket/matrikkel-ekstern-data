plugins {
    id("buildsrc.convention.kotlin-jvm")
    application
}

dependencies {
    implementation(project(":tjenestespesifikasjoner:serg"))
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
