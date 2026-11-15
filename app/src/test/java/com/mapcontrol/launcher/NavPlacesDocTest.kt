package com.mapcontrol.launcher

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavPlacesDocTest {

    private fun parse(json: String, errors: MutableList<String> = mutableListOf()) = NavPlacesDoc.parse(JSONObject(json), errors)

    @Test
    fun writesPresentPlacesDeletesNullsAndLeavesMissingAlone() {
        val c = parse("""{"home":{"lat":41.012345,"lon":29.0,"label":"  Kadıköy "},"work":null}""")!!
        assertEquals(NavPlacesDoc.Place(41.012345, 29.0, "Kadıköy"), c["home"])
        assertTrue(c.containsKey("work"))
        assertNull(c["work"])
        assertFalse(parse("""{"home":null}""")!!.containsKey("work"))
        assertTrue(parse("{}")!!.isEmpty())
    }

    @Test
    fun labelIsOptionalAndEmptyMeansNone() {
        assertNull(parse("""{"home":{"lat":1,"lon":2}}""")!!["home"]!!.label)
        assertNull(parse("""{"home":{"lat":1,"lon":2,"label":"  "}}""")!!["home"]!!.label)
        assertNull(parse("""{"home":{"lat":1,"lon":2,"label":null}}""")!!["home"]!!.label)
    }

    @Test
    fun unknownPlacesFromANewerPhoneAreIgnored() {
        val c = parse("""{"school":{"lat":1,"lon":2},"work":{"lat":3,"lon":4}}""")!!
        assertEquals(setOf("work"), c.keys)
    }

    @Test
    fun anyInvalidPlaceRejectsTheWholeDocument() {
        fun rejected(json: String): List<String> {
            val errors = mutableListOf<String>()
            assertNull(json, parse(json, errors))
            return errors
        }
        assertEquals(listOf("home"), rejected("""{"home":{"lat":91,"lon":0},"work":{"lat":1,"lon":2}}"""))
        assertEquals(listOf("work"), rejected("""{"work":{"lat":1,"lon":-180.5}}"""))
        assertEquals(listOf("home"), rejected("""{"home":{"lat":"41.0","lon":29}}"""))
        assertEquals(listOf("home"), rejected("""{"home":{"lon":29}}"""))
        assertEquals(listOf("home"), rejected("""{"home":"41.0, 29.0"}"""))
        assertEquals(listOf("home"), rejected("""{"home":{"lat":1,"lon":2,"label":"a\u0007b"}}"""))
        assertEquals(listOf("home"), rejected("""{"home":{"lat":1,"lon":2,"label":"${"x".repeat(61)}"}}"""))
        assertEquals(listOf("home"), rejected("""{"home":{"lat":1,"lon":2,"label":5}}"""))
        assertEquals(listOf("home", "work"), rejected("""{"home":{"lat":1},"work":[]}"""))
    }

    @Test
    fun serializesBothPlacesWithNullForUnset() {
        val json = NavPlacesDoc.toJson(mapOf("home" to NavPlacesDoc.Place(41.5, 29.25, "Ev adı"), "work" to null))
        assertEquals(41.5, json.getJSONObject("home").getDouble("lat"), 0.0)
        assertEquals("Ev adı", json.getJSONObject("home").getString("label"))
        assertTrue(json.isNull("work"))
        assertFalse(NavPlacesDoc.toJson(mapOf("home" to NavPlacesDoc.Place(1.0, 2.0, null))).getJSONObject("home").has("label"))
        // Kendi çıktısı yeniden okunabilir.
        assertEquals(NavPlacesDoc.Place(41.5, 29.25, "Ev adı"), parse(json.toString())!!["home"])
    }
}
