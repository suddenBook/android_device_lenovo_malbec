/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.malbec.parts.parallelwindow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ParallelWindowMutationConfirmationTest {

    @Test
    fun confirmParallelWindowMutation_matchingValueIsConfirmed() {
        assertEquals(
            ParallelWindowMutationConfirmation.CONFIRMED,
            confirmParallelWindowMutation(requestedEnabled = true, observedEnabled = true),
        )
        assertEquals(
            ParallelWindowMutationConfirmation.CONFIRMED,
            confirmParallelWindowMutation(requestedEnabled = false, observedEnabled = false),
        )
    }

    @Test
    fun confirmParallelWindowMutation_differentValueIsMismatched() {
        assertEquals(
            ParallelWindowMutationConfirmation.MISMATCHED,
            confirmParallelWindowMutation(requestedEnabled = true, observedEnabled = false),
        )
    }

    @Test
    fun confirmParallelWindowMutation_missingTargetIsUnavailable() {
        assertEquals(
            ParallelWindowMutationConfirmation.UNAVAILABLE,
            confirmParallelWindowMutation(requestedEnabled = true, observedEnabled = null),
        )
    }

    /**
     * The restart classifier exists to stop the UI claiming a kill it did not
     * get, so the cases that matter are the two that must NOT reach APPLIED.
     * killBackgroundProcesses is a void call that silently does nothing for a
     * foreground or visible app, and the process list can also simply fail to
     * read — neither is evidence.
     */
    @Test
    fun parallelWindowRestartOutcome_onlyAProvenDeadProcessIsApplied() {
        assertEquals(
            ParallelWindowRestartOutcome.APPLIED,
            parallelWindowRestartOutcome(killAttempted = true, stillRunning = false),
        )
    }

    @Test
    fun parallelWindowRestartOutcome_survivingProcessFallsBackToNextStart() {
        assertEquals(
            ParallelWindowRestartOutcome.NEXT_START,
            parallelWindowRestartOutcome(killAttempted = true, stillRunning = true),
        )
    }

    @Test
    fun parallelWindowRestartOutcome_unreadableProcessListIsNotEvidence() {
        // The dangerous direction: an unknown must not be optimistic, because
        // NEXT_START is true whether or not the app died and APPLIED is not.
        assertEquals(
            ParallelWindowRestartOutcome.NEXT_START,
            parallelWindowRestartOutcome(killAttempted = true, stillRunning = null),
        )
    }

    @Test
    fun parallelWindowRestartOutcome_masterSwitchNeverClaimsARestart() {
        // changedApp == null (the master) never attempts a kill, and must not be
        // reported as one however the process list happens to read.
        for (stillRunning in listOf(null, true, false)) {
            assertEquals(
                ParallelWindowRestartOutcome.NOT_ATTEMPTED,
                parallelWindowRestartOutcome(killAttempted = false, stillRunning = stillRunning),
            )
        }
    }

    @Test
    fun coordinateParallelWindowMutation_falseSetterStillRereadsAndConfirms() {
        var rereadCalled = false
        val freshPage = TestPage(enabled = true)

        val result = coordinateParallelWindowMutation(
            lastAuthoritativePage = TestPage(enabled = false),
            requestedEnabled = true,
            write = { false },
            reread = {
                rereadCalled = true
                freshPage
            },
            observeEnabled = TestPage::enabled,
        )

        assertTrue(rereadCalled)
        assertEquals(ParallelWindowMutationConfirmation.CONFIRMED, result.confirmation)
        assertSame(freshPage, result.authoritativePage)
        assertEquals(false, result.writeAccepted)
        assertNull(result.writeFailure)
        assertNull(result.readFailure)
    }

    @Test
    fun coordinateParallelWindowMutation_throwingSetterStillRereadsAndConfirms() {
        val writeFailure = IllegalStateException("reply lost")
        var rereadCalled = false
        val freshPage = TestPage(enabled = false)

        val result = coordinateParallelWindowMutation(
            lastAuthoritativePage = TestPage(enabled = true),
            requestedEnabled = false,
            write = { throw writeFailure },
            reread = {
                rereadCalled = true
                freshPage
            },
            observeEnabled = TestPage::enabled,
        )

        assertTrue(rereadCalled)
        assertEquals(ParallelWindowMutationConfirmation.CONFIRMED, result.confirmation)
        assertSame(freshPage, result.authoritativePage)
        assertNull(result.writeAccepted)
        assertSame(writeFailure, result.writeFailure)
        assertNull(result.readFailure)
    }

    @Test
    fun coordinateParallelWindowMutation_mismatchRejectsWithFreshPage() {
        val freshPage = TestPage(enabled = false)

        val result = coordinateParallelWindowMutation(
            lastAuthoritativePage = TestPage(enabled = true),
            requestedEnabled = true,
            write = { true },
            reread = { freshPage },
            observeEnabled = TestPage::enabled,
        )

        assertEquals(ParallelWindowMutationConfirmation.MISMATCHED, result.confirmation)
        assertSame(freshPage, result.authoritativePage)
        assertEquals(true, result.writeAccepted)
        assertNull(result.writeFailure)
        assertNull(result.readFailure)
    }

    @Test
    fun coordinateParallelWindowMutation_rereadFailureRetainsLastAuthoritativePage() {
        val lastPage = TestPage(enabled = false)
        val readFailure = IllegalStateException("provider unavailable")

        val result = coordinateParallelWindowMutation(
            lastAuthoritativePage = lastPage,
            requestedEnabled = true,
            write = { true },
            reread = { throw readFailure },
            observeEnabled = TestPage::enabled,
        )

        assertEquals(ParallelWindowMutationConfirmation.UNAVAILABLE, result.confirmation)
        assertSame(lastPage, result.authoritativePage)
        assertEquals(true, result.writeAccepted)
        assertNull(result.writeFailure)
        assertSame(readFailure, result.readFailure)
    }

    private data class TestPage(val enabled: Boolean)
}
