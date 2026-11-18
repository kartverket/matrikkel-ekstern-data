plugins {
    id("buildsrc.convention.kotlin-jvm")
    application
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    implementation(project(":libs:app-utils"))
    implementation(project(":libs:logging"))
    implementation(project(":tjenestespesifikasjoner:brreg"))
    implementation(project(":libs:domene:brreg-kontrakt"))


    implementation(libs.logbackClassic)
    implementation(libs.logstash)
    implementation(libs.kotlinUtils)
    implementation(libs.tokenClient)
    implementation(libs.kafkaLight)

    testImplementation(project(":libs:test-utils"))
    testImplementation(libs.bundles.testEcosystem)
}

application {
    mainClass = "no.kartverket.matrikkel.MainKt"
}