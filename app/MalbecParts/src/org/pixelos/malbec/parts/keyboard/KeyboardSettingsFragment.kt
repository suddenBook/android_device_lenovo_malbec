/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.pixelos.malbec.parts.keyboard

import android.content.Intent
import android.os.Bundle
import androidx.preference.Preference
import com.android.settingslib.widget.SettingsBasePreferenceFragment
import org.pixelos.malbec.parts.Constants
import org.pixelos.malbec.parts.R
import org.pixelos.malbec.parts.gesture.ActionPickerActivity
import org.pixelos.malbec.parts.gesture.GestureBinder

/**
 * The two customisable keys on the Lenovo folio.
 *
 * Exactly two, and that is not a simplification: stock's own capability table
 * for this keyboard
 * (work/unpacked/parts/system/usr/kb-type-config/lenovo_keyboard.xml, entry
 * 17ef:62b2) declares support_diy_app1, support_diy_app2 and support_diy_ai and
 * nothing else — every other key in the function row is fixed behaviour in the
 * keyboard's own firmware. The AI key has no HID usage in stock's keylayout, so
 * it cannot be routed until one is observed on the wire.
 */
class KeyboardSettingsFragment : SettingsBasePreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.keyboard_settings, rootKey)

        for ((prefKey, _) in Constants.KEYBOARD_KEYS) {
            findPreference<Preference>(prefKey)?.setOnPreferenceClickListener { pref ->
                startActivity(
                    Intent(requireContext(), ActionPickerActivity::class.java)
                        .putExtra(ActionPickerActivity.EXTRA_PREF_KEY, prefKey)
                        .putExtra(ActionPickerActivity.EXTRA_TITLE, pref.title?.toString())
                )
                true
            }
        }
    }

    override fun onResume() {
        super.onResume()
        for ((prefKey, _) in Constants.KEYBOARD_KEYS) {
            val action = GestureBinder.actionFor(requireContext(), prefKey)
            findPreference<Preference>(prefKey)?.summary = getString(action.labelRes)
        }
    }
}
