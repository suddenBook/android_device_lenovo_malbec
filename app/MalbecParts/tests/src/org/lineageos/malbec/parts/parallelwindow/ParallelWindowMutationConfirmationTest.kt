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
