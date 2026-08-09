/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.malbec.parts.stylus

import android.os.Bundle
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity
import org.lineageos.malbec.parts.MalbecPartsService

/**
 * One page, three doors: Settings > "Device settings" (the top-level entry),
 * Settings > Connected devices > "Stylus and keyboard" (the activity-alias), and
 * Settings > System > Keyboard > Physical keyboard > "Advanced keyboard
 * settings". See AndroidManifest.xml for how each is declared.
 *
 * The heading follows the door, and that is deliberate rather than an oversight:
 * CollapsingToolbarBaseActivity titles itself from the launched component's
 * label, and for an alias that is the alias's label. So arriving from Connected
 * devices lands on "Stylus and keyboard" — the thing that was being looked for —
 * while the top-level entry reads "Device settings". Same content either way.
 *
 * CollapsingToolbarBaseActivity + Theme.SubSettingsBase.Expressive is what makes
 * this indistinguishable from a page inside Settings itself: same large title
 * that collapses on scroll, same typography, same colours from the dynamic
 * palette. Nothing here draws its own chrome.
 */
class StylusSettingsActivity : CollapsingToolbarBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MalbecPartsService.sync(this)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(
                    com.android.settingslib.collapsingtoolbar.R.id.content_frame,
                    StylusSettingsFragment(),
                    TAG,
                )
                .commit()
        }
    }

    companion object {
        private const val TAG = "StylusSettings"
    }
}
