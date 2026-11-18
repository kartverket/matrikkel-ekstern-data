package no.kartverket.matrikkel.test

import java.util.Properties
import kotlin.io.path.Path
import kotlin.io.path.reader

object TestEnv {
    fun load(file: String) {
        val env = Properties()
        env.load(Path(file).reader())

        env.entries.forEach { entry ->
            System.setProperty(entry.key.toString(), entry.value.toString())
        }
    }
}