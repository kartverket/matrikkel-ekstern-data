import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
    id("java-library")
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.openapi.generator)
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    api(libs.bundles.kotlinxEcosystem)
    api(libs.bundles.ktorClientEcosystem)
    api(libs.okhttp)
    api(project(":tjenestespesifikasjoner:openapi-infrastructure"))
}

tasks.register<GenerateTask>("generateForHendelser") {
    val specFile = file("openapi-hendelser.json")
    inputSpec = specFile.toURI().toString()
    outputDir =
        layout.buildDirectory
            .dir("generated/hendelser")
            .get()
            .asFile.path
    skipValidateSpec = true
    generateModelDocumentation = false
    generateModelTests = false
    generateApiDocumentation = false
    generateApiTests = false

    generatorName = "kotlin"
    library = "jvm-okhttp4"
    configOptions.put("dateLibrary", "java8")
    configOptions.put("serializationLibrary", "jackson")
    typeMappings.put("string+date-time", "LocalDateTime")
    apiPackage = "no.kartverket.tjenestespesifikasjoner.serg.hendelser.apis"
    modelPackage = "no.kartverket.tjenestespesifikasjoner.serg.hendelser.models"

    globalProperties.put("apis", "")
    globalProperties.put("models", "")
    globalProperties.put("supportingFiles", "false")
}

tasks.register<GenerateTask>("generateForFormueobjekt") {
    val specFile = file("openapi-formueobjekt.json")
    inputSpec = specFile.toURI().toString()
    outputDir =
        layout.buildDirectory
            .dir("generated/formueobjekt")
            .get()
            .asFile.path
    skipValidateSpec = true
    generateModelDocumentation = false
    generateModelTests = false
    generateApiDocumentation = false
    generateApiTests = false

    generatorName = "kotlin"
    library = "jvm-okhttp4"
    configOptions.put("dateLibrary", "java8")
    configOptions.put("serializationLibrary", "jackson")
    typeMappings.put("string+date-time", "LocalDateTime")
    apiPackage = "no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.apis"
    modelPackage = "no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.models"

    globalProperties.put("apis", "")
    globalProperties.put("models", "")
    globalProperties.put("supportingFiles", "false")
}

sourceSets {
    main {
        kotlin {
            srcDir(layout.buildDirectory.dir("generated/hendelser/src/main/kotlin"))
            srcDir(layout.buildDirectory.dir("generated/formueobjekt/src/main/kotlin"))
        }
    }
}

tasks.named("compileKotlin").configure {
    dependsOn(tasks.named("generateForHendelser"))
    dependsOn(tasks.named("generateForFormueobjekt"))
}
