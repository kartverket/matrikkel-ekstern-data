package no.kartverket.ktor

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.kartverket.kotlin.SelftestGenerator

object Selftest {
    class Config(
        appname: String = "Not set",
        version: String = "Not set",
        var contextpath: String = "",
    ) : SelftestGenerator.Config(appname, version)

    val Plugin =
        createApplicationPlugin("Selftest", ::Config) {
            val config = pluginConfig
            val selftest = SelftestGenerator.getInstance(pluginConfig)
            with(application) {
                routing {
                    route(config.contextpath) {
                        route("internal") {
                            get("isAlive") {
                                if (selftest.isAlive()) {
                                    call.respondText("Alive")
                                } else {
                                    call.respondText("Not alive", status = HttpStatusCode.InternalServerError)
                                }
                            }

                            get("isReady") {
                                if (selftest.isReady()) {
                                    call.respondText("Ready")
                                } else {
                                    call.respondText("Not ready", status = HttpStatusCode.InternalServerError)
                                }
                            }

                            get("selftest") {
                                call.respondText(selftest.scrape())
                            }
                        }
                    }
                }
            }
        }
}