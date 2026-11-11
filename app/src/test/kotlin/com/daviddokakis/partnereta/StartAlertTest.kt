package com.daviddokakis.partnereta

import com.daviddokakis.partnereta.PartnerView.Status.LIVE
import com.daviddokakis.partnereta.PartnerView.Status.NOT_RIDING
import com.daviddokakis.partnereta.PartnerView.Status.STALE
import com.daviddokakis.partnereta.PartnerView.Status.WAITING
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StartAlertTest {
    @Test fun firesOnceWhenTheyStartDuringYourRide() {
        val r = StartAlertRule()
        assertFalse(r.update(true, NOT_RIDING))
        assertFalse(r.update(true, NOT_RIDING))
        assertTrue(r.update(true, LIVE))
        assertFalse(r.update(true, LIVE))
        assertFalse(r.update(true, NOT_RIDING))
        assertFalse(r.update(true, LIVE))
    }

    @Test fun quietWhenTheyWereAlreadyRiding() {
        val r = StartAlertRule()
        assertFalse(r.update(true, STALE))
        assertFalse(r.update(true, LIVE))
    }

    @Test fun waitsForTheirLinkBeforeDeciding() {
        val r = StartAlertRule()
        assertFalse(r.update(true, WAITING))
        assertFalse(r.update(true, NOT_RIDING))
        assertTrue(r.update(true, LIVE))
    }

    @Test fun nothingWhileYouAreNotRecordingAndRearmsNextRide() {
        val r = StartAlertRule()
        assertFalse(r.update(false, NOT_RIDING))
        assertFalse(r.update(false, LIVE))
        assertFalse(r.update(true, NOT_RIDING))
        assertTrue(r.update(true, LIVE))
        assertFalse(r.update(false, NOT_RIDING))
        assertFalse(r.update(true, NOT_RIDING))
        assertTrue(r.update(true, LIVE))
    }

    @Test fun text() {
        assertEquals("38.2 km route\nto Hornsgatan 12", StartAlert.text(38_210.0, 900.0, "Hornsgatan 12"))
        assertEquals("38.2 km route", StartAlert.text(38_210.0, null, null))
        assertEquals("No route loaded\n12.4 km away", StartAlert.text(null, 12_400.0, null))
        assertEquals("No route loaded", StartAlert.text(null, null, "x"))
    }

    @Test fun address() {
        val body = """{"address":{"house_number":"12","road":"Hornsgatan","suburb":"Södermalm","city":"Stockholm"}}"""
        assertEquals("Hornsgatan 12", FinishAddress.parse(body))
        assertEquals("Strandvägen", FinishAddress.parse("""{"address":{"road":"Strandvägen","city":"Stockholm"}}"""))
        assertEquals("Ytterby", FinishAddress.parse("""{"address":{"village":"Ytterby"}}"""))
        assertNull(FinishAddress.parse("""{"error":"Unable to geocode"}"""))
        assertNull(FinishAddress.parse("not json"))
    }

    @Test fun personKeepsTheSwitch() {
        val p = Person(slot = 2, name = "Alex", code = "AbCd1234", startAlert = false)
        assertEquals(false, People.decode(People.encode(listOf(p))).single().startAlert)
        assertEquals(true, People.decode("""[{"slot":1,"name":"Alex"}]""").single().startAlert)
    }

    @Test fun urlHasNoMoreThanThePoint() {
        assertEquals(
            "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=59.31700&lon=18.05000&zoom=18&addressdetails=1&accept-language=sv",
            FinishAddress.url(LatLng(59.317, 18.05), "sv"),
        )
    }
}
