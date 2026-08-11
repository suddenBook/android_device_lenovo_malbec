/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.malbec.parts.parallelwindow

import java.util.Comparator
import org.junit.Assert.assertEquals
import org.junit.Test

class ParallelWindowAppListModelTest {

    private val caseInsensitiveLabelComparator = Comparator<String> { left, right ->
        left.compareTo(right, ignoreCase = true)
    }

    @Test
    fun buildRows_keepsOnlyInstalledEligiblePackages() {
        val rows = ParallelWindowAppListModel.buildRows(
            eligiblePackages = listOf("supported.installed", "supported.missing"),
            disabledPackages = emptyList(),
            installedApps = listOf(
                InstalledParallelWindowApp("supported.installed", "Supported"),
                InstalledParallelWindowApp("unsupported.installed", "Unsupported"),
            ),
            labelComparator = caseInsensitiveLabelComparator,
        )

        assertEquals(
            listOf(ParallelWindowAppRow("supported.installed", "Supported", true)),
            rows,
        )
    }

    @Test
    fun buildRows_keepsDefaultOffPackageVisibleAndUnchecked() {
        val rows = ParallelWindowAppListModel.buildRows(
            eligiblePackages = listOf("source.default.off"),
            disabledPackages = listOf("source.default.off"),
            installedApps = listOf(
                InstalledParallelWindowApp("source.default.off", "Source default off"),
            ),
            labelComparator = caseInsensitiveLabelComparator,
        )

        assertEquals(
            listOf(ParallelWindowAppRow("source.default.off", "Source default off", false)),
            rows,
        )
    }

    @Test
    fun buildRows_sortsByLocalizedLabelThenPackageAndDeduplicatesInput() {
        val rows = ParallelWindowAppListModel.buildRows(
            eligiblePackages = listOf("z.second", "a.alpha", "z.first", "z.first"),
            disabledPackages = listOf("not.eligible", "z.second"),
            installedApps = listOf(
                InstalledParallelWindowApp("z.second", "Zulu"),
                InstalledParallelWindowApp("z.first", "Zulu"),
                InstalledParallelWindowApp("a.alpha", "alpha"),
                InstalledParallelWindowApp("z.first", "duplicate must not win"),
            ),
            labelComparator = caseInsensitiveLabelComparator,
        )

        assertEquals(
            listOf(
                ParallelWindowAppRow("a.alpha", "alpha", true),
                ParallelWindowAppRow("z.first", "Zulu", true),
                ParallelWindowAppRow("z.second", "Zulu", false),
            ),
            rows,
        )
    }
}
