/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.malbec.parts.parallelwindow

import android.os.Bundle
import com.android.settingslib.activityembedding.ActivityEmbeddingUtils
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity

/** Settings > Apps entry point for per-user parallel-window controls. */
class ParallelWindowSettingsActivity : CollapsingToolbarBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideNavigateUpWhenEmbedded()
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(
                    com.android.settingslib.collapsingtoolbar.R.id.content_frame,
                    ParallelWindowSettingsFragment(),
                    TAG,
                )
                .commit()
        }
    }

    private fun hideNavigateUpWhenEmbedded() {
        if (!ActivityEmbeddingUtils.shouldHideNavigateUpButton(this, true)) return
        actionBar?.apply {
            setDisplayHomeAsUpEnabled(false)
            setHomeButtonEnabled(false)
        }
    }

    private companion object {
        const val TAG = "ParallelWindowSettings"
    }
}
