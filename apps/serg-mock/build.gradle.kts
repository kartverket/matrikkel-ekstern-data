plugins {
    id("buildsrc.convention.kotlin-jvm")
    application
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    implementation(project(":libs:ktor-utils"))
    implementation(project(":libs:kotlin-utils"))
    implementation(project(":libs:logging"))
    implementation(libs.bundles.ktorEcosystem)
    implementation(libs.bundles.kotlinxEcosystem)

    implementation(project(":tjenestespesifikasjoner:serg"))

    testImplementation(libs.bundles.testEcosystem)
    testRuntimeOnly(libs.junitPlatformLauncher)
}

application {
    mainClass = "no.kartverket.serg.mock.AppKt"
}
