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
    inputSpec = specFile
    outputDir = generatedDir.get().asFile
    skipValidateSpec = true
    generateModelDocumentation = false
    generateModelTests = false
    generateApiDocumentation = false
    generateApiTests = false

    generatorName = "kotlin"
    library = "jvm-okhttp4"
    configOptions.put("dateLibrary", "java8")
    configOptions.put("serializationLibrary", "jackson")

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
