/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.pixelos.malbec.parts.stylus

import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.SwitchPreferenceCompat
import com.android.settingslib.widget.SettingsBasePreferenceFragment
import org.pixelos.malbec.parts.Constants
import org.pixelos.malbec.parts.MalbecPartsService
import org.pixelos.malbec.parts.R
import org.pixelos.malbec.parts.display.PenModeController
import org.pixelos.malbec.parts.gesture.ActionPickerActivity
import org.pixelos.malbec.parts.gesture.GestureBinder

class StylusSettingsFragment : SettingsBasePreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.stylus_settings, rootKey)

        for ((prefKey, _) in Constants.PEN_BUTTONS) {
            findPreference<Preference>(prefKey)?.setOnPreferenceClickListener { pref ->
                startActivity(
                    Intent(requireContext(), ActionPickerActivity::class.java)
                        .putExtra(ActionPickerActivity.EXTRA_PREF_KEY, prefKey)
                        .putExtra(ActionPickerActivity.EXTRA_TITLE, pref.title?.toString())
                )
                true
            }
        }

        findPreference<ListPreference>(Constants.PREF_TOUCH_MODE)?.apply {
            value = PenModeController.currentMode(requireContext())
            setOnPreferenceChangeListener { _, newValue ->
                PenModeController.setMode(requireContext(), newValue as String)
                // The summary is "%s", so let the framework redraw it after the
                // value lands.
                true
            }
        }

        findPreference<SwitchPreferenceCompat>(Constants.PREF_PEN_LOST_ALERT)
            ?.setOnPreferenceChangeListener { _, _ ->
                // The watcher reads the preference on each event, so nothing has
                // to be pushed. Just make sure the service is up.
                MalbecPartsService.sync(requireContext())
                true
            }
    }

    override fun onResume() {
        super.onResume()
        refreshSummaries()
    }

    private fun refreshSummaries() {
        val context = requireContext()

        for ((prefKey, _) in Constants.PEN_BUTTONS) {
            val action = GestureBinder.actionFor(context, prefKey)
            findPreference<Preference>(prefKey)?.summary = getString(action.labelRes)
        }

        findPreference<ListPreference>(Constants.PREF_TOUCH_MODE)?.let {
            it.value = PenModeController.currentMode(context)
        }

        // "Last seen" under the alert switch, the way stock's PrefLostAlert does
        // it. Absent rather than "never" when there is nothing to say.
        val lastSeen = GestureBinder.prefs(context).getLong(Constants.PREF_PEN_LAST_SEEN, 0L)
        findPreference<SwitchPreferenceCompat>(Constants.PREF_PEN_LOST_ALERT)?.summary =
            if (lastSeen > 0L) {
                getString(
                    R.string.pen_lost_alert_summary_last_seen,
                    DateUtils.getRelativeTimeSpanString(
                        lastSeen,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS,
                    ),
                )
            } else {
                getString(R.string.pen_lost_alert_summary)
            }

        // The button bindings only mean anything while a pen exists. Hiding the
        // category rather than showing five dead rows is the same thing Settings
        // does with its own stylus preferences.
        findPreference<PreferenceCategory>(Constants.PREF_CAT_PEN_BUTTONS)?.isVisible =
            StylusMetadataTagger.hasBondedPen(context)
    }
}
