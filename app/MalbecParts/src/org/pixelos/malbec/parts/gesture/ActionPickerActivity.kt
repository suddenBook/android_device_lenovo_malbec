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
import androidx.preference.PreferenceScreen
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity
import com.android.settingslib.widget.SettingsBasePreferenceFragment
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

/**
 * ⚠️ SettingsBasePreferenceFragment, not PreferenceFragmentCompat. That base is
 * what applies the Expressive look: SettingsBasePreferenceFragment.kt:33-45 calls
 * setDivider(null) and adds MarginItemDecoration, and :54-58 overrides
 * onCreateAdapter to return SettingsPreferenceGroupAdapter -- the adapter that
 * draws the rounded grouped preference cards. With the plain compat base this
 * page was a flat divider-separated list opened from a page of rounded cards,
 * which is exactly the "looks like it belongs in Settings" claim this app makes
 * about itself.
 */
class ActionPickerFragment : SettingsBasePreferenceFragment() {

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

        // isAvailable() drops entries that cannot fire on this device -- today
        // that is "Open notes", because android.app.role.NOTES has no holder
        // here. See GestureAction.isAvailable for the framework path that would
        // otherwise return a null intent and log one warning.
        for (action in GestureAction.CATALOGUE.filter {
            GestureAction.isAvailable(requireContext(), it)
        }) {
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

    /**
     * One resolved app, with its label and icon already loaded.
     *
     * The reason this type exists at all: loadLabel() and loadIcon() each open
     * the target APK's resources, and a tablet has 150-250 launcher entries.
     * Doing that inline while building preferences means 3 loads per app on
     * whatever thread we happen to be on — and the only thread we are ever on
     * here is the main one, inside a preference click handler.
     */
    private data class AppEntry(
        val info: ResolveInfo,
        val label: CharSequence,
        val icon: android.graphics.drawable.Drawable?,
    )

    private var appLoader: Thread? = null

    override fun onDestroyView() {
        super.onDestroyView()
        // The loader holds no reference to the fragment, but it can still be
        // mid-flight; the posted continuation checks isAdded before touching UI.
        appLoader = null
    }

    /**
     * "Open an app" — the app list, loaded off the main thread.
     *
     * ⚠️ This used to run entirely on the main thread: queryIntentActivities,
     * then a sortedBy that called loadLabel on every result, then a loop calling
     * loadLabel *again* plus loadIcon. That is 3 resource loads x N apps, and on
     * this device N is around 200. It never shipped in a state where anyone
     * pressed it — the build it went out in did not boot — so the ANR was found
     * by reading rather than by hitting it.
     */
    private fun buildAppList() {
        val screen: PreferenceScreen = preferenceScreen
        screen.removeAll()

        val category = PreferenceCategory(requireContext()).apply {
            title = getString(R.string.action_launch_app)
        }
        screen.addPreference(category)

        val loading = Preference(requireContext()).apply {
            title = getString(R.string.action_picker_loading)
            isSelectable = false
        }
        category.addPreference(loading)

        val pm = requireContext().packageManager
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        lateinit var thread: Thread
        thread = Thread {
            val entries = loadLaunchableApps(pm)
            handler.post {
                // Identity, not null: a second buildAppList() replaces appLoader,
                // and the older thread must not paint over the newer list.
                if (!isAdded || appLoader !== thread) return@post
                category.removePreference(loading)
                for (entry in entries) {
                    category.addPreference(appPreference(entry))
                }
            }
        }
        appLoader = thread
        thread.start()
    }

    private fun appPreference(entry: AppEntry) =
        Preference(requireContext()).apply {
            title = entry.label
            icon = entry.icon
            setOnPreferenceClickListener {
                GestureBinder.setAction(
                    requireContext(),
                    prefKey,
                    GestureAction.launchAppId(
                        entry.info.activityInfo.packageName,
                        entry.info.activityInfo.name,
                    ),
                )
                requireActivity().setResult(Activity.RESULT_OK)
                requireActivity().finish()
                true
            }
        }

    /** Runs on a worker thread. Loads each label exactly once, then sorts on it. */
    private fun loadLaunchableApps(pm: PackageManager): List<AppEntry> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
            .map { AppEntry(it, it.loadLabel(pm), runCatching { it.loadIcon(pm) }.getOrNull()) }
            .sortedBy { it.label.toString().lowercase() }
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
