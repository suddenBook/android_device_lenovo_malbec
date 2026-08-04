/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.pixelos.malbec.parts.keyboard

import android.os.Bundle
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity
import org.pixelos.malbec.parts.MalbecPartsService

/**
 * Settings > System > Keyboard > Physical keyboard > Advanced keyboard settings.
 *
 * Reached through the RemotePreference that AOSP Settings already ships at
 * res/xml/physical_keyboard_settings.xml:83-89 — it advertises the intent action
 * org.lineageos.settings.device.ADVANCED_KEYBOARD_SETTINGS and hides itself when
 * nothing answers. Declaring that action in our manifest is the entire
 * integration; no Settings source change.
 */
class KeyboardSettingsActivity : CollapsingToolbarBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MalbecPartsService.sync(this)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(
                    com.android.settingslib.collapsingtoolbar.R.id.content_frame,
                    KeyboardSettingsFragment(),
                    TAG,
                )
                .commit()
        }
    }

    companion object {
        private const val TAG = "KeyboardSettings"
    }
}
