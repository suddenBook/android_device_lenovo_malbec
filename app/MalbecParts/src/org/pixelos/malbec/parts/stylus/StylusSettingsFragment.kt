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

        for ((prefKey, _) in Constants.ALL_TRIGGERS) {
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

    /** VID/PID of the folio, matched the same way the pen is. */
    private fun isFolioAttached(): Boolean {
        val im = requireContext().getSystemService(android.hardware.input.InputManager::class.java)
            ?: return false
        return im.inputDeviceIds.any { id ->
            val d = im.getInputDevice(id) ?: return@any false
            d.vendorId == Constants.PEN_VENDOR_ID && d.productId == Constants.KEYBOARD_PRODUCT_ID
        }
    }

    private fun refreshSummaries() {
        val context = requireContext()

        for ((prefKey, _) in Constants.ALL_TRIGGERS) {
            val action = GestureBinder.actionFor(context, prefKey)
            findPreference<Preference>(prefKey)?.summary = getString(action.labelRes)
        }

        // Same rule as the pen category above, for the same reason: "does this
        // tablet have a folio" rather than "is it docked right now". Undocking
        // the keyboard must not make the owner's key assignments disappear from
        // Settings while the bindings themselves stay alive in the framework's
        // input_gestures.xml. InputDevice answers the first question only while
        // docked, so the answer is remembered the first time.
        val prefs = GestureBinder.prefs(context)
        if (isFolioAttached() && !prefs.getBoolean(Constants.PREF_FOLIO_EVER_SEEN, false)) {
            prefs.edit().putBoolean(Constants.PREF_FOLIO_EVER_SEEN, true).apply()
        }
        findPreference<PreferenceCategory>(Constants.PREF_CAT_KEYBOARD_KEYS)?.isVisible =
            isFolioAttached() || prefs.getBoolean(Constants.PREF_FOLIO_EVER_SEEN, false)

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

        // The button bindings only mean anything once a pen exists. Hiding the
        // category rather than showing five dead rows is the same thing Settings
        // does with its own stylus preferences.
        //
        // ⚠️ "Once a pen exists", not "while Bluetooth is on".
        // StylusMetadataTagger.hasBondedPen returns false whenever the adapter is
        // off (:107) — it has to, because bondedDevices is unreadable then. Using
        // it alone made the user's whole configuration vanish from Settings the
        // moment they turned Bluetooth off, while the bindings themselves kept
        // living in the framework's per-user input_gestures.xml and kept working
        // the instant the pen came back. PREF_PEN_LAST_SEEN is written on every
        // connect and disconnect (:173, :177), so it answers "has this device
        // ever had a pen" without needing the adapter.
        val everSeenPen =
            GestureBinder.prefs(context).getLong(Constants.PREF_PEN_LAST_SEEN, 0L) != 0L
        findPreference<PreferenceCategory>(Constants.PREF_CAT_PEN_BUTTONS)?.isVisible =
            StylusMetadataTagger.hasBondedPen(context) || everSeenPen
    }
}
