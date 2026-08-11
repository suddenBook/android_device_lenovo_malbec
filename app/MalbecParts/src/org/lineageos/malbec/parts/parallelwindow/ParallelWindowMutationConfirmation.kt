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
