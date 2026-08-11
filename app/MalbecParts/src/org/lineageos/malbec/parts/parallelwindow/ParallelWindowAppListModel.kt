/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.malbec.parts.parallelwindow

import java.util.Comparator

/** Package-manager data already resolved for the user who opened the page. */
data class InstalledParallelWindowApp(
    val packageName: String,
    val label: String,
)

/** Immutable UI state for one supported, installed application. */
data class ParallelWindowAppRow(
    val packageName: String,
    val label: String,
    val enabled: Boolean,
)

/**
 * Builds the application rows without depending on Android UI classes.
 *
 * The framework's eligible list and PackageManager's installed list are both
 * treated as authority: an app must occur in their intersection. This is the
 * boundary that ensures MalbecParts never exposes or changes an app outside the
 * device rule set. A disabled package stays in the result; its switch is
 * simply unchecked, including when that state came from the source default.
 */
object ParallelWindowAppListModel {
    fun buildRows(
        eligiblePackages: Collection<String>,
        disabledPackages: Collection<String>,
        installedApps: Collection<InstalledParallelWindowApp>,
        labelComparator: Comparator<String>,
    ): List<ParallelWindowAppRow> {
        val eligible = eligiblePackages.toHashSet()
        val disabled = disabledPackages.toHashSet()
        val installedByPackage = linkedMapOf<String, InstalledParallelWindowApp>()
        installedApps.forEach { app -> installedByPackage.putIfAbsent(app.packageName, app) }

        return installedByPackage.values
            .asSequence()
            .filter { it.packageName in eligible }
            .map { app ->
                ParallelWindowAppRow(
                    packageName = app.packageName,
                    label = app.label,
                    enabled = app.packageName !in disabled,
                )
            }
            .sortedWith { left, right ->
                val labelOrder = labelComparator.compare(left.label, right.label)
                if (labelOrder != 0) {
                    labelOrder
                } else {
                    left.packageName.compareTo(right.packageName)
                }
            }
            .toList()
    }
}
