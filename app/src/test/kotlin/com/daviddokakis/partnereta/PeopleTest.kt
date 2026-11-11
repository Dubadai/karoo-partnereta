package com.daviddokakis.partnereta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PeopleTest {
    private val alex = Person(slot = 1, name = "Alex", code = "AbCd1234", colour = "cyan", showRoute = false)
    private val robin = Person(slot = 3, name = "Robin", code = "XyZ98765", colour = "green", sharedDots = true, distanceLeft = false)

    @Test fun roundTrip() {
        assertEquals(listOf(alex, robin), People.decode(People.encode(listOf(robin, alex)).let { People.encode(People.decode(it)) }))
    }

    @Test fun badJsonGivesNobody() {
        assertEquals(emptyList<Person>(), People.decode("not json"))
        assertEquals(emptyList<Person>(), People.decode("""[{"slot":9,"name":"x"}]"""))
    }

    @Test fun garminPeopleFromZeroTwoZeroAreDropped() {
        val stored = """[{"slot":1,"name":"Alex","code":"AbCd1234","source":"KAROO"},{"slot":2,"name":"Robin","code":"https://livetrack.garmin.com/robin","source":"GARMIN"}]"""
        assertEquals(listOf(1), People.decode(stored).map { it.slot })
    }

    @Test fun newPersonTakesFirstFreeSlotAndColour() {
        val next = People.newPerson(listOf(alex, robin))!!
        assertEquals(2, next.slot) // slot 2 is free: nobody moves up when someone is removed
        assertTrue(next.colour != "cyan" && next.colour != "green")
        val full = (1..4).map { Person(slot = it, name = "P$it", code = null) }
        assertNull(People.newPerson(full))
    }
}
