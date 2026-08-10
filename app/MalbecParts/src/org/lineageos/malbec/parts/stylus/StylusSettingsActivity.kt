/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.malbec.parts.stylus

import android.os.Bundle
import com.android.settingslib.activityembedding.ActivityEmbeddingUtils
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
        hideNavigateUpWhenEmbedded()
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

    /**
     * ★ Take the toolbar's back arrow away when this page is embedded in
     * Settings' two-pane split — which, on this tablet, is always.
     *
     * [CollapsingToolbarBaseActivity] turns the Up affordance on unconditionally
     * (`CollapsingToolbarDelegate.onCreateView` :182-189). Settings does not: it
     * asks `ActivityEmbeddingUtils.shouldHideNavigateUpButton(this,
     * isSecondLayerPage)` first (`SettingsActivity.java:408-418`), and hides it
     * on a second-layer page that is embedded, because the list it would go back
     * to is already on screen in the other pane. That is why every other row on
     * the homepage opens a page with no back arrow and this one had one.
     *
     * It was not only inconsistent, it was wrong. `onNavigateUp` finishes this
     * activity, and the split pair rule Settings registers for injected
     * top-level tiles — `DashboardFeatureProviderImpl.java:233-240` ->
     * `registerTwoPanePairRuleForSettingsHome(..., clearTop = true)`, which is
     * `finishPrimaryWithSecondary = ADJACENT` — then tears the homepage down with
     * it. Measured: tapping it closed Settings entirely and landed on the
     * launcher. AOSP's own second-layer pages do the same on system Back (also
     * measured, on Display), so the behaviour is correct and the *button* was
     * the defect.
     *
     * `isSecondLayerPage = true` unconditionally: all three doors into this page
     * are second-layer (top-level tile, Connected devices, Physical keyboard).
     *
     * Not embedded — a window too narrow for the split, or a launch from
     * somewhere that is not Settings — leaves the arrow exactly where it was,
     * because then it is the only way back.
     */
    private fun hideNavigateUpWhenEmbedded() {
        if (!ActivityEmbeddingUtils.shouldHideNavigateUpButton(this, true)) {
            return
        }
        actionBar?.apply {
            setDisplayHomeAsUpEnabled(false)
            setHomeButtonEnabled(false)
        }
    }

    companion object {
        private const val TAG = "StylusSettings"
    }
}
