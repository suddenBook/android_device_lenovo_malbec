/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.pixelos.malbec.parts.gesture

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity
import com.android.settingslib.widget.SelectorWithWidgetPreference
import org.pixelos.malbec.parts.R

/**
 * "What should this key do?" — one picker, used by all seven call sites (five
 * pen buttons, two keyboard app keys).
 *
 * The layout is a radio list, which is what AOSP itself uses for the same
 * question in Settings > System > Touchpad > Three-finger tap
 * (TouchpadThreeFingerTapSelector.java:81-89). Using SettingsLib's
 * SelectorWithWidgetPreference rather than a plain ListPreference dialog is what
 * makes it read as a Settings page rather than a pop-up: a full screen with the
 * same collapsing title as everything around it.
 */
class ActionPickerActivity : CollapsingToolbarBaseActivity() {

    companion object {
        const val EXTRA_PREF_KEY = "pref_key"
        const val EXTRA_TITLE = "title"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.getStringExtra(EXTRA_TITLE)?.let { title = it }
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(
                    com.android.settingslib.collapsingtoolbar.R.id.content_frame,
                    ActionPickerFragment().apply { arguments = intent.extras },
                )
                .commit()
        }
    }
}

class ActionPickerFragment : PreferenceFragmentCompat() {

    private val prefKey by lazy {
        requireArguments().getString(ActionPickerActivity.EXTRA_PREF_KEY)!!
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceScreen = preferenceManager.createPreferenceScreen(requireContext())
        buildActionList()
    }

    private fun buildActionList() {
        val screen = preferenceScreen
        screen.removeAll()

        val current = GestureBinder.actionFor(requireContext(), prefKey)
        val currentIsApp = current.id.startsWith(GestureAction.LAUNCH_APP_PREFIX)

        for (action in GestureAction.CATALOGUE) {
            val pref = SelectorWithWidgetPreference(requireContext()).apply {
                key = action.id
                title = getString(action.labelRes)
                isChecked = if (action.id == GestureAction.ID_LAUNCH_APP) {
                    currentIsApp
                } else {
                    action.id == current.id
                }
                if (action.id == GestureAction.ID_LAUNCH_APP && currentIsApp) {
                    summary = appLabelFor(current.id)
                }
                setOnPreferenceClickListener {
                    if (action.id == GestureAction.ID_LAUNCH_APP) {
                        buildAppList()
                    } else {
                        GestureBinder.setAction(requireContext(), prefKey, action.id)
                        requireActivity().setResult(Activity.RESULT_OK)
                        requireActivity().finish()
                    }
                    true
                }
            }
            screen.addPreference(pref)
        }
    }

    private fun buildAppList() {
        val screen: PreferenceScreen = preferenceScreen
        screen.removeAll()

        val category = PreferenceCategory(requireContext()).apply {
            title = getString(R.string.action_launch_app)
        }
        screen.addPreference(category)

        for (info in launchableApps()) {
            val pref = Preference(requireContext()).apply {
                title = info.loadLabel(requireContext().packageManager)
                icon = info.loadIcon(requireContext().packageManager)
                setOnPreferenceClickListener {
                    GestureBinder.setAction(
                        requireContext(),
                        prefKey,
                        GestureAction.launchAppId(
                            info.activityInfo.packageName,
                            info.activityInfo.name,
                        ),
                    )
                    requireActivity().setResult(Activity.RESULT_OK)
                    requireActivity().finish()
                    true
                }
            }
            category.addPreference(pref)
        }
    }

    private fun launchableApps(): List<ResolveInfo> {
        val pm = requireContext().packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
    }

    private fun appLabelFor(actionId: String): CharSequence? {
        val flat = actionId.removePrefix(GestureAction.LAUNCH_APP_PREFIX)
        val pkg = flat.substringBefore('/')
        return try {
            val pm = requireContext().packageManager
            pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0)).loadLabel(pm)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }
}
