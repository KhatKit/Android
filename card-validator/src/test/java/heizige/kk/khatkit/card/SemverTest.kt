package heizige.kk.khatkit.card

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SemverTest {

    @Test
    fun parsesVersions() {
        assertEquals(Semver.Version(1, 2, 3), Semver.parse("1.2.3"))
        assertEquals(Semver.Version(1, 2, 0), Semver.parse("v1.2"))
        assertEquals(Semver.Version(0, 1, 0), Semver.parse("^0.1.0"))
    }

    @Test
    fun caretKeepsMajor() {
        assertTrue(Semver.satisfies("1.4.0", "^1.2.0"))
        assertFalse(Semver.satisfies("2.0.0", "^1.2.0"))
        assertFalse(Semver.satisfies("1.1.9", "^1.2.0"))
    }

    @Test
    fun tildeKeepsMinor() {
        assertTrue(Semver.satisfies("1.2.9", "~1.2.0"))
        assertFalse(Semver.satisfies("1.3.0", "~1.2.0"))
    }

    @Test
    fun comparators() {
        assertTrue(Semver.satisfies("1.5.0", ">=1.2.0"))
        assertFalse(Semver.satisfies("1.1.0", ">=1.2.0"))
        assertTrue(Semver.satisfies("0.9.0", "<1.0.0"))
    }

    @Test
    fun wildcardAndExact() {
        assertTrue(Semver.satisfies("9.9.9", "*"))
        assertTrue(Semver.satisfies("1.2.3", "=1.2.3"))
        assertFalse(Semver.satisfies("1.2.4", "=1.2.3"))
    }

    @Test
    fun orAndAnd() {
        assertTrue(Semver.satisfies("0.5.0", "^1.0.0 || ^0.5.0"))
        assertTrue(Semver.satisfies("1.3.0", ">=1.2.0 <2.0.0"))
        assertFalse(Semver.satisfies("2.1.0", ">=1.2.0 <2.0.0"))
    }
}
