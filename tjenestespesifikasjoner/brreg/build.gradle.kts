import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode

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

    testImplementation(libs.bundles.testEcosystem)
    testRuntimeOnly(libs.junitPlatformLauncher)
}

/**
 * brreg sitt API returnerer `oneOf [Enhet, SlettetEnhet]` (diskriminert på `respons_klasse`) som inline-skjema.
 * openapi-generator (kotlin/jackson) genererer da et interface som `Enhet`/`SlettetEnhet` ikke implementerer.
 * https://github.com/OpenAPITools/openapi-generator/issues/22013
 *
 * Patchen skriver dette om til arv via `allOf` fra et navngitt baseskjema med diskriminator,
 * som generatoren støtter: `data class Enhet(...) : HentEnhet200Response`.
 * `openapi.json` beholdes uendret slik brreg publiserer den.
 */
object OASPatch {
    fun patch(input: File, output: File) {
        val mapper = ObjectMapper()
        val spec = mapper.readTree(input) as ObjectNode

        fixUnionIssue(spec, "/enhetsregisteret/api/enheter/{enhetorgnr}", "HentEnhet200Response")
        fixUnionIssue(spec, "/enhetsregisteret/api/underenheter/{underenhetorgnr}", "HentUnderenhet200Response")

        output.apply {
            parentFile.mkdirs()
            writeText(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(spec))
        }
    }

    private fun fixUnionIssue(spec: ObjectNode, path: String, baseName: String) {
        val schemas = spec.withObjectProperty("components").withObjectProperty("schemas")
        val content = spec.at("/paths/${path.replace("/", "~1")}/get/responses/200/content")
        check(content.isObject && !content.isEmpty) { "Fant ikke 200-respons for $path i openapi.json" }
        val mapping = content.first().at("/schema/discriminator/mapping")
        check(content.first().at("/schema/discriminator/propertyName").asText() == "respons_klasse" && mapping.isObject) {
            "Forventet inline oneOf med diskriminator 'respons_klasse' for $path. Er spec-en endret? Vurder om patchen fortsatt trengs."
        }
        check(!schemas.has(baseName)) { "Skjema $baseName finnes allerede i openapi.json" }

        schemas.putObject(baseName).apply {
            put("type", "object")
            putArray("required").add("respons_klasse")
            putObject("properties").putObject("respons_klasse").put("type", "string")
            putObject("discriminator").put("propertyName", "respons_klasse").set<JsonNode>("mapping", mapping.deepCopy())
        }
        mapping.properties().forEach { (_, ref) ->
            val subtypeName = ref.asText().removePrefix("#/components/schemas/")
            val subtype = schemas.get(subtypeName) as ObjectNode
            val properties = subtype.get("properties") as ObjectNode
            properties.remove("respons_klasse")

            // Når skjemaet får allOf, prefikser generatoren inline-modeller med "AllOf" (EnhetAllOfPostadresse).
            // Løfter dem derfor ut som egne skjema med navnet de ellers ville fått (EnhetPostadresse).
            properties.properties().toList()
                .filter { (_, schema) -> schema.has("properties") }
                .forEach { (name, schema) ->
                    val inlineName = subtypeName + name.trimStart('_').replaceFirstChar { it.uppercase() }
                    check(!schemas.has(inlineName)) { "Skjema $inlineName finnes allerede i openapi.json" }
                    schemas.set<JsonNode>(inlineName, schema)
                    properties.putObject(name).put("\$ref", "#/components/schemas/$inlineName")
                }

            subtype.putArray("allOf").addObject().put("\$ref", "#/components/schemas/$baseName")
        }
        content.forEach { mediaType ->
            (mediaType as ObjectNode)
                .putObject("schema")
                .put("\$ref", "#/components/schemas/$baseName")
        }
    }
}

val patchedSpecFile = layout.buildDirectory.file("patched-spec/openapi.json")
val patchOpenApiSpec = tasks.register("patchOpenApiSpec") {
    val input = file("openapi.json")
    val output = patchedSpecFile
    inputs.file(input)
    outputs.file(output)

    doLast {
        OASPatch.patch(
            input = input,
            output = output.get().asFile
        )
    }
}

openApiGenerate {
    inputSpec = patchedSpecFile
    outputDir =
        layout.buildDirectory
            .dir("generated/brreg")
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
    apiPackage = "no.kartverket.tjenestespesifikasjoner.brreg.apis"
    modelPackage = "no.kartverket.tjenestespesifikasjoner.brreg.models"

    globalProperties.put("apis", "")
    globalProperties.put("models", "")
    globalProperties.put("supportingFiles", "false")
}

sourceSets {
    main {
        kotlin {
            srcDir(layout.buildDirectory.dir("generated/brreg/src/main/kotlin"))
        }
    }
}

tasks.openApiGenerate.configure {
    dependsOn(patchOpenApiSpec)
}

tasks.named("compileKotlin").configure {
    dependsOn(tasks.openApiGenerate)
}
