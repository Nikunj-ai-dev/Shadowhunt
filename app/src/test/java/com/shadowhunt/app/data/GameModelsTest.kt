package com.shadowhunt.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameModelsTest {
    @Test
    fun roomCodeIsSixCharactersAndUsesUnambiguousAlphabet() {
        repeat(100) {
            val code = makeRoomCode()
            assertEquals(6, code.length)
            assertTrue(code.all { it in "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" })
        }
    }

    @Test
    fun distanceBetweenSameCoordinateIsZero() {
        val point = GeoPoint(28.3671, 79.4304)
        assertEquals(0.0, distanceMeters(point, point), 0.001)
    }

    @Test
    fun oneDegreeLatitudeIsApproximately111Point2Kilometres() {
        val north = GeoPoint(1.0, 0.0)
        val south = GeoPoint(0.0, 0.0)
        assertEquals(111_195.0, distanceMeters(north, south), 250.0)
    }
}
