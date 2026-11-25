package no.kartverket.matrikkel

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

fun Clock.currentDate(): LocalDate = this.todayIn(TimeZone.of("Oslo/Europe"))