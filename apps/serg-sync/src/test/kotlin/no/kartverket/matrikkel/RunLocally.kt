package no.kartverket.matrikkel

import java.util.Properties
import kotlin.io.path.Path
import kotlin.io.path.reader


class Env {
    companion object {
        fun load(file: String) {
            val env = Properties()
            env.load(Path(file).reader())

            env.entries.forEach { entry ->
                System.setProperty(entry.key.toString(), entry.value.toString())
            }
        }
    }
}

fun main() {
    Env.load("docker/idea.private.env")
    runApplication()
}