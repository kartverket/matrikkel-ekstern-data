plugins {
    id("java-library")
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.openapi.generator)
}

dependencies {
    api(libs.bundles.kotlinxEcosystem)
    api(libs.bundles.ktorClientEcosystem)
    api(libs.okhttp)
}

val generatedDir = layout.buildDirectory.dir("generated/infra")
openApiGenerate {
    val specFile = file("placeholder.yaml")
    inputSpec = specFile.toURI().toString()
    outputDir = generatedDir.get().asFile.path
    skipValidateSpec = true
    generateModelDocumentation = false
    generateModelTests = false
    generateApiDocumentation = false
    generateApiTests = false

    generatorName = "kotlin"
    library = "jvm-ktor"
    configOptions.put("dateLibrary", "java8")
    configOptions.put("serializationLibrary", "kotlinx_serialization")

    globalProperties.put("apis", "false")
    globalProperties.put("models", "false")
    globalProperties.put("supportingFiles", "")
}

sourceSets {
    main {
        kotlin {
            srcDir(generatedDir.map { it.dir("src/main/kotlin").asFile })
        }
    }
}

tasks.named("compileKotlin").configure {
    dependsOn(tasks.openApiGenerate)
}
