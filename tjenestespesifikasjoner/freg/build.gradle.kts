import org.gradle.kotlin.dsl.register
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

openApiGenerate {
    val specFile = file("openapi-fixed.yaml")
    inputSpec = specFile
    outputDir =
        layout.buildDirectory
            .dir("generated/freg")
            .get()
            .asFile
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
    apiPackage = "no.kartverket.tjenestespesifikasjoner.freg.apis"
    modelPackage = "no.kartverket.tjenestespesifikasjoner.freg.models"

    globalProperties.put("apis", "")
    globalProperties.put("models", "")
    globalProperties.put("supportingFiles", "false")
}

sourceSets {
    main {
        kotlin {
            srcDir(layout.buildDirectory.dir("generated/freg/src/main/kotlin"))
        }
    }
}

tasks.named("compileKotlin").configure {
    dependsOn(tasks.openApiGenerate)
}
