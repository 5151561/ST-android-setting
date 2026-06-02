package io.github.sanitised.st.chat.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeGroupGeneratorTest {

    private val members = listOf("aria.png", "eleanor.png", "kael.png")

    @Test
    fun manualStrategyReturnsNullSoCallerMustNameTheSpeaker() {
        assertNull(
            pickGroupSpeaker(members, disabledMembers = emptySet(), lastSpeakerAvatar = null, activationStrategy = 2)
        )
    }

    @Test
    fun listStrategyRotatesToNextMemberAfterLastSpeaker() {
        assertEquals(
            "eleanor.png",
            pickGroupSpeaker(members, emptySet(), lastSpeakerAvatar = "aria.png", activationStrategy = 1)
        )
        // Wraps around at the end of the list.
        assertEquals(
            "aria.png",
            pickGroupSpeaker(members, emptySet(), lastSpeakerAvatar = "kael.png", activationStrategy = 1)
        )
    }

    @Test
    fun naturalStrategyStartsFromFirstMemberWhenNoPriorSpeaker() {
        assertEquals(
            "aria.png",
            pickGroupSpeaker(members, emptySet(), lastSpeakerAvatar = null, activationStrategy = 0)
        )
    }

    @Test
    fun disabledMembersAreNeverSelected() {
        // aria is muted; rotation after eleanor should skip back to eleanor's next eligible (kael).
        val pick = pickGroupSpeaker(
            members,
            disabledMembers = setOf("aria.png"),
            lastSpeakerAvatar = "eleanor.png",
            activationStrategy = 1
        )
        assertEquals("kael.png", pick)
    }

    @Test
    fun pooledStrategyPicksAnEligibleMemberUsingProvidedRandom() {
        val pick = pickGroupSpeaker(
            members,
            disabledMembers = setOf("aria.png"),
            lastSpeakerAvatar = null,
            activationStrategy = 3,
            random = { 0.99 } // forces the last eligible index
        )
        assertEquals("kael.png", pick)
        assertTrue(pick in members)
    }

    @Test
    fun refusesToRepeatTheSoleEligibleSpeakerWhenSelfResponsesOff() {
        // Only kael is eligible (others muted) and kael just spoke; with self
        // responses off the picker must refuse rather than repeat kael.
        assertNull(
            pickGroupSpeaker(
                members,
                disabledMembers = setOf("aria.png", "eleanor.png"),
                lastSpeakerAvatar = "kael.png",
                activationStrategy = 1,
                allowSelfResponses = false
            )
        )
    }

    @Test
    fun pooledExcludesLastSpeakerWhenSelfResponsesOff() {
        // eligible = [aria, eleanor, kael], last = aria. With self off and random
        // forced to index 0, the pool excludes aria so eleanor is chosen.
        val pick = pickGroupSpeaker(
            members,
            disabledMembers = emptySet(),
            lastSpeakerAvatar = "aria.png",
            activationStrategy = 3,
            allowSelfResponses = false,
            random = { 0.0 }
        )
        assertEquals("eleanor.png", pick)
    }

    @Test
    fun listRotationStillAdvancesWhenSelfResponsesOff() {
        assertEquals(
            "eleanor.png",
            pickGroupSpeaker(
                members,
                disabledMembers = emptySet(),
                lastSpeakerAvatar = "aria.png",
                activationStrategy = 1,
                allowSelfResponses = false
            )
        )
    }

    @Test
    fun returnsNullWhenAllMembersAreDisabled() {
        assertNull(
            pickGroupSpeaker(members, disabledMembers = members.toSet(), lastSpeakerAvatar = null, activationStrategy = 1)
        )
    }
}
