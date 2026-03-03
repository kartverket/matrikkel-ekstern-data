plugins {
    id("buildsrc.convention.kotlin-jvm")
    application
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    implementation(project(":libs:ktor-utils"))
    implementation(project(":libs:kotlin-utils"))
    implementation(libs.bundles.ktorEcosystem)
    implementation(libs.bundles.kotlinxEcosystem)
    implementation(libs.logbackClassic)

    implementation(project(":tjenestespesifikasjoner:serg"))

    testImplementation(libs.bundles.testEcosystem)
    testRuntimeOnly(libs.junitPlatformLauncher)
}

application {
    mainClass = "no.utgdev.serg.mock.AppKt"
}
