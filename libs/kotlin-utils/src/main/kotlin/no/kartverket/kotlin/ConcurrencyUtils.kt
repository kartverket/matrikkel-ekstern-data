package no.kartverket.kotlin

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit


suspend fun <TIn, TOut> List<TIn>.forEachInParallell(
    parallellism: Int,
    fn: suspend (element: TIn, index: Int) -> TOut
) {
    this.mapInParallell(parallellism, fn)
}

suspend fun <TIn, TOut> List<TIn>.mapInParallell(
    parallellism: Int,
    fn: suspend (element: TIn, index: Int) -> TOut
): List<TOut> {
    return coroutineScope {
        require(parallellism > 0) {
            "parallellism must be > 0"
        }

        val semaphore = Semaphore(permits = parallellism)
        mapIndexed { index, value ->
            async {
                semaphore.withPermit {
                    fn(value, index)
                }
            }
        }.awaitAll()
    }
}
