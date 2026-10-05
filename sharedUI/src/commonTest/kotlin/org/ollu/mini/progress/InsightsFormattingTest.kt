package org.ollu.mini.progress

import kotlin.test.Test
import kotlin.test.assertEquals

class InsightsFormattingTest {

    @Test
    fun `russian plural picks the right form`() {
        fun form(n: Int) = russianPlural(n, "попытка", "попытки", "попыток")

        assertEquals("попыток", form(0))
        assertEquals("попытка", form(1))
        assertEquals("попытки", form(2))
        assertEquals("попытки", form(4))
        assertEquals("попыток", form(5))
        assertEquals("попыток", form(10))
        // The teens are the exception every naive implementation gets wrong.
        assertEquals("попыток", form(11))
        assertEquals("попыток", form(12))
        assertEquals("попыток", form(14))
        assertEquals("попытка", form(21))
        assertEquals("попытки", form(22))
        assertEquals("попыток", form(25))
        assertEquals("попыток", form(111))
        assertEquals("попытка", form(101))
    }

    @Test
    fun `durations read in the largest sensible unit`() {
        assertEquals("<1 мин", formatDuration(0))
        assertEquals("<1 мин", formatDuration(30_000))
        assertEquals("5 мин", formatDuration(5 * 60_000L))
        assertEquals("59 мин", formatDuration(59 * 60_000L))
        assertEquals("1 ч", formatDuration(60 * 60_000L))
        assertEquals("3 ч", formatDuration(190 * 60_000L))
    }

    @Test
    fun `relative time is phrased naturally`() {
        val minute = 60_000L
        val hour   = 60 * minute
        val day    = 24 * hour

        assertEquals("только что", relativeTime(10_000))
        assertEquals("5 минут назад", relativeTime(5 * minute))
        assertEquals("1 минуту назад", relativeTime(minute))
        assertEquals("2 часа назад", relativeTime(2 * hour))
        assertEquals("1 день назад", relativeTime(day))
        assertEquals("3 дня назад", relativeTime(3 * day))
        assertEquals("давно", relativeTime(60 * day))
    }

    @Test
    fun `attempt counts are labelled`() {
        assertEquals("1 попытка", pluralAttempts(1))
        assertEquals("3 попытки", pluralAttempts(3))
        assertEquals("7 попыток", pluralAttempts(7))
    }
}
