/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.pixelos.malbec.parts.stylus

import android.os.Bundle
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity
import org.pixelos.malbec.parts.MalbecPartsService

/**
 * Settings > Connected devices > "Stylus and folio keyboard".
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
