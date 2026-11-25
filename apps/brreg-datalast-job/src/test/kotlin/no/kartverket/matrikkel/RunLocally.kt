package no.kartverket.matrikkel

import no.kartverket.matrikkel.test.TestEnv

fun main() {
    TestEnv.load("docker/brreg-datalast.env")
    runBrregDatalastJob()
}