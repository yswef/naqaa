package com.naqaa.app.prayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CitiesTest {

    @Test
    fun `search finds Yemen by English country name`() {
        val results = Cities.search("Yemen")

        assertTrue(results.isNotEmpty())
        assertTrue(results.all { it.countryEn == "Yemen" })
        assertTrue(results.any { it.id == "sanaa" })
        assertTrue(results.any { it.id == "aden" })
    }

    @Test
    fun `search finds Yemen by Arabic country name and ignores diacritics`() {
        val results = Cities.search("اليَمَن")

        assertTrue(results.isNotEmpty())
        assertTrue(results.all { it.countryAr == "اليمن" })
        assertTrue(results.any { it.id == "taiz" })
        assertTrue(results.any { it.id == "hudaydah" })
    }

    @Test
    fun `search accepts city and country together and normalizes Arabic alef`() {
        assertEquals(listOf("ibb"), Cities.search("اب اليمن").map { it.id })
        assertEquals(listOf("taiz"), Cities.search("TAIZ yEmEn").map { it.id })
    }

    @Test
    fun `city display label follows the selected language`() {
        val sanaa = Cities.byId("sanaa") ?: error("Sanaa is missing")

        assertEquals("Sanaa, Yemen", sanaa.displayLabel("en"))
        assertEquals("صنعاء، اليمن", sanaa.displayLabel("ar"))
    }

    @Test
    fun `nearest city includes the Yemen entries`() {
        assertEquals("taiz", Cities.nearest(13.5795, 44.0209).id)
    }
}
