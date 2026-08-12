/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.malbec.parts.parallelwindow

enum class ParallelWindowMutationConfirmation {
    CONFIRMED,
    MISMATCHED,
    UNAVAILABLE,
}

/**
 * What actually happened to the changed app's process, after a confirmed write.
 *
 * ── Why there is anything to classify at all ──────────────────────────────
 *
 * A parallel-window rule is read ONCE per app process and then latched, on both
 * sides: [ParallelWindowProcessState.latch] gives the process one immutable
 * decision, and WindowOrganizerController's resizeability gate latches the same
 * package decision server-side and deliberately refuses to consult live settings
 * (so that a TaskFragment can never be granted to a process that was not
 * configured to route it). The only un-latch is
 * resetParallelWindowConfigForNewProcess(), called from preBindApplication —
 * i.e. bound to a NEW process, by construction.
 *
 * So a running app cannot pick up a rule change, and killing it is not a
 * shortcut, it is the mechanism.
 *
 * ── Why the answer is not simply "killed" ─────────────────────────────────
 *
 * ActivityManager#killBackgroundProcesses is deliberately weaker than
 * forceStopPackage: it does not set FLAG_STOPPED, so the app keeps its alarms,
 * jobs, sync, widgets and saved instance state, and the user comes back to the
 * screen they left. The price is that it passes ProcessList.SERVICE_ADJ (500) as
 * a floor, and ProcessList skips anything below it — so an app that is in the
 * foreground, visible, or holding a foreground service is NOT killed, the call
 * returns void, and nothing anywhere says so.
 *
 * ⚠️ That silence is the thing this enum exists to prevent. This feature's
 * documents, its shell surface and its framework comments are unusually careful
 * to state exactly what did and did not happen — see the framework commit "say
 * what was applied, not that nothing was". A UI that killed the app *sometimes*
 * and always claimed success would be a regression in the one dimension this
 * code cares about most, even though it would look like an improvement.
 */
enum class ParallelWindowRestartOutcome {
    /** No process of the app survived: the new rule is in force now. */
    APPLIED,

    /**
     * A process survived, or could not be proven gone. Either way the honest
     * claim is the weaker one — and it is true in BOTH cases, because an app
     * that *was* killed also picks the rule up when it next starts.
     */
    NEXT_START,

    /** No kill was attempted: the master switch, or the kill itself threw. */
    NOT_ATTEMPTED,
}

/**
 * Classifies a restart attempt. Pure, so the honesty rule above is testable
 * without a device.
 *
 * [stillRunning] is null when the process list could not be read. That is
 * treated exactly like "still running": [ParallelWindowRestartOutcome.APPLIED]
 * is the strong claim and only evidence may produce it.
 */
fun parallelWindowRestartOutcome(
    killAttempted: Boolean,
    stillRunning: Boolean?,
): ParallelWindowRestartOutcome = when {
    !killAttempted -> ParallelWindowRestartOutcome.NOT_ATTEMPTED
    stillRunning == false -> ParallelWindowRestartOutcome.APPLIED
    else -> ParallelWindowRestartOutcome.NEXT_START
}

/**
 * Result of a write followed by an authoritative framework reread.
 *
 * [writeAccepted] is null when the setter threw. It is diagnostic only: the
 * reread remains authoritative because a false return or a lost Binder reply
 * does not prove that the remote side failed to commit the value.
 */
data class ParallelWindowMutationResult<Page>(
    val confirmation: ParallelWindowMutationConfirmation,
    val authoritativePage: Page?,
    val writeAccepted: Boolean?,
    val writeFailure: Exception?,
    val readFailure: Exception?,
)

/**
 * Compares a requested value with the post-write framework projection.
 *
 * A null observation means the target app left the installed-and-eligible
 * intersection before the reread, so the write can no longer be confirmed.
 */
fun confirmParallelWindowMutation(
    requestedEnabled: Boolean,
    observedEnabled: Boolean?,
): ParallelWindowMutationConfirmation = when (observedEnabled) {
    null -> ParallelWindowMutationConfirmation.UNAVAILABLE
    requestedEnabled -> ParallelWindowMutationConfirmation.CONFIRMED
    else -> ParallelWindowMutationConfirmation.MISMATCHED
}

/**
 * Performs a mutation and always rereads the framework projection afterwards.
 *
 * The setter's return value and exception are retained for diagnostics but do
 * not decide success. Only the fresh projection can confirm or reject the
 * request. When that projection cannot be read, callers keep displaying the
 * last authoritative page rather than inventing a value.
 */
fun <Page> coordinateParallelWindowMutation(
    lastAuthoritativePage: Page?,
    requestedEnabled: Boolean,
    write: () -> Boolean,
    reread: () -> Page,
    observeEnabled: (Page) -> Boolean?,
): ParallelWindowMutationResult<Page> {
    var writeAccepted: Boolean? = null
    var writeFailure: Exception? = null
    try {
        writeAccepted = write()
    } catch (exception: Exception) {
        writeFailure = exception
    }

    val page = try {
        reread()
    } catch (exception: Exception) {
        return ParallelWindowMutationResult(
            confirmation = ParallelWindowMutationConfirmation.UNAVAILABLE,
            authoritativePage = lastAuthoritativePage,
            writeAccepted = writeAccepted,
            writeFailure = writeFailure,
            readFailure = exception,
        )
    }

    val observedEnabled = try {
        observeEnabled(page)
    } catch (exception: Exception) {
        return ParallelWindowMutationResult(
            confirmation = ParallelWindowMutationConfirmation.UNAVAILABLE,
            authoritativePage = page,
            writeAccepted = writeAccepted,
            writeFailure = writeFailure,
            readFailure = exception,
        )
    }
    return ParallelWindowMutationResult(
        confirmation = confirmParallelWindowMutation(requestedEnabled, observedEnabled),
        authoritativePage = page,
        writeAccepted = writeAccepted,
        writeFailure = writeFailure,
        readFailure = null,
    )
}
