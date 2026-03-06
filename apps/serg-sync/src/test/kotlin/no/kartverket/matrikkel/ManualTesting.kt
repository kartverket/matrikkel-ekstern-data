package no.kartverket.matrikkel

import no.kartverket.matrikkel.config.Configuration

fun main() {
    Env.load("docker/idea.private.env")
    val config = Configuration()
    val services = Services(config)

    val hendelser = services.hendelserApi.hentHendelserFormuesobjektFastEiendom(
        fraSekvensnummer = 11290,
        antall = 10
    ).hendelser

    println(hendelser)
}