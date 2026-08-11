/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.malbec.parts.parallelwindow

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import android.window.ParallelWindowControlState
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.SwitchPreferenceCompat
import com.android.settingslib.widget.SettingsBasePreferenceFragment
import java.text.Collator
import java.util.Comparator
import java.util.concurrent.Executors
import org.lineageos.malbec.parts.R

/**
 * A projection of framework-owned state. The preferences on this page never
 * persist themselves and every successful write is followed by a fresh Binder
 * read before the displayed switch changes.
 */
class ParallelWindowSettingsFragment : SettingsBasePreferenceFragment() {
    private val backend = ParallelWindowBackend()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var operationGeneration = 0
    private var currentPage: PageData? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.parallel_window_settings, rootKey)
        statusPreference().apply {
            isVisible = false
            setOnPreferenceClickListener {
                refreshFromFramework()
                true
            }
        }

        masterPreference().setOnPreferenceChangeListener { _, value ->
            val enabled = value as? Boolean ?: return@setOnPreferenceChangeListener false
            mutate(
                changedApp = null,
                requestedEnabled = enabled,
                change = { backend.setUserEnabled(enabled) },
            )
            // Binder state is the source of truth. renderPage() changes the
            // switch only after the post-write read confirms this value.
            false
        }
    }

    override fun onResume() {
        super.onResume()
        refreshFromFramework()
    }

    override fun onDestroyView() {
        // Work already in flight may finish, but its generation can no longer
        // touch the destroyed preference hierarchy.
        operationGeneration++
        currentPage = null
        super.onDestroyView()
    }

    private fun refreshFromFramework() {
        val context = requireContext().applicationContext
        val generation = beginOperation(clearApps = true)
        BACKGROUND_EXECUTOR.execute {
            val outcome = try {
                LoadOutcome.Success(loadPageData(context))
            } catch (exception: Exception) {
                Log.e(TAG, "Unable to load parallel-window controls", exception)
                LoadOutcome.Failure
            }
            mainHandler.post {
                if (!isAdded || generation != operationGeneration) return@post
                when (outcome) {
                    is LoadOutcome.Success -> renderPage(outcome.page)
                    LoadOutcome.Failure -> showLoadFailure()
                }
            }
        }
    }

    private fun mutate(
        changedApp: PageApp?,
        requestedEnabled: Boolean,
        change: () -> Boolean,
    ) {
        val context = requireContext().applicationContext
        val lastAuthoritativePage = currentPage
        val generation = beginOperation(clearApps = false)
        val target = changedApp?.row?.packageName ?: "user master"
        BACKGROUND_EXECUTOR.execute {
            val result = coordinateParallelWindowMutation(
                lastAuthoritativePage = lastAuthoritativePage,
                requestedEnabled = requestedEnabled,
                write = change,
                reread = { loadPageData(context) },
                observeEnabled = { page -> observedEnabled(page, changedApp) },
            )
            logMutationDiagnostics(result, target, requestedEnabled)
            val outcome = when (result.confirmation) {
                ParallelWindowMutationConfirmation.CONFIRMED ->
                    result.authoritativePage?.let(MutationOutcome::Saved)
                        ?: MutationOutcome.Unconfirmed(lastAuthoritativePage)
                ParallelWindowMutationConfirmation.MISMATCHED ->
                    result.authoritativePage?.let(MutationOutcome::Rejected)
                        ?: MutationOutcome.Unconfirmed(lastAuthoritativePage)
                ParallelWindowMutationConfirmation.UNAVAILABLE ->
                    MutationOutcome.Unconfirmed(result.authoritativePage)
            }
            mainHandler.post {
                if (!isAdded || generation != operationGeneration) return@post
                when (outcome) {
                    is MutationOutcome.Saved -> {
                        renderPage(outcome.page)
                        showNextProcessNotice(changedApp)
                    }
                    is MutationOutcome.Rejected -> showRejectedMutation(outcome.page)
                    is MutationOutcome.Unconfirmed -> showUnconfirmedMutation(outcome.page)
                }
            }
        }
    }

    private fun observedEnabled(
        page: PageData,
        changedApp: PageApp?,
    ): Boolean? = if (changedApp == null) {
        page.state.userEnabled
    } else {
        page.apps
            .firstOrNull { app -> app.row.packageName == changedApp.row.packageName }
            ?.row
            ?.enabled
    }

    private fun logMutationDiagnostics(
        result: ParallelWindowMutationResult<PageData>,
        target: String,
        requestedEnabled: Boolean,
    ) {
        if (result.writeAccepted == false) {
            Log.w(
                TAG,
                "Write for $target returned false; requested=$requestedEnabled, " +
                    "reread=${result.confirmation}",
            )
        }
        result.writeFailure?.let { exception ->
            Log.e(
                TAG,
                "Write for $target failed; requested=$requestedEnabled, " +
                    "reread=${result.confirmation}",
                exception,
            )
        }
        result.readFailure?.let { exception ->
            Log.e(
                TAG,
                "Unable to confirm write for $target requested=$requestedEnabled",
                exception,
            )
        }
        when (result.confirmation) {
            ParallelWindowMutationConfirmation.CONFIRMED -> Unit
            ParallelWindowMutationConfirmation.MISMATCHED ->
                Log.w(TAG, "Write for $target did not take effect")
            ParallelWindowMutationConfirmation.UNAVAILABLE -> if (result.readFailure == null) {
                Log.w(TAG, "Write for $target cannot be confirmed because it disappeared")
            }
        }
    }

    private fun beginOperation(clearApps: Boolean): Int {
        val generation = ++operationGeneration
        statusPreference().isVisible = false
        masterPreference().isEnabled = false
        setAppPreferencesEnabled(false)
        if (clearApps) showLoading()
        return generation
    }

    private fun loadPageData(context: Context): PageData {
        val state = backend.getControlState()
        val packageManager = context.packageManager
        val eligiblePackages = state.packageNames.toHashSet()
        // One PackageManager query, even if a malformed service response ever
        // contains an unexpectedly large list. Calling getApplicationInfo once
        // per eligible package would turn that contract violation into a burst
        // of package-manager Binder transactions.
        val loadedApps = packageManager
            .getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
            .asSequence()
            .filter { info -> info.packageName in eligiblePackages }
            .map { info -> loadInstalledApp(packageManager, info) }
            .toList()

        val locale = context.resources.configuration.locales[0]
        val collator = Collator.getInstance(locale)
        val rows = ParallelWindowAppListModel.buildRows(
            eligiblePackages = eligiblePackages,
            disabledPackages = state.disabledPackages.asList(),
            installedApps = loadedApps.map { app ->
                InstalledParallelWindowApp(app.packageName, app.label)
            },
            labelComparator = Comparator { left, right -> collator.compare(left, right) },
        )
        val loadedByPackage = loadedApps.associateBy { it.packageName }
        return PageData(
            state = state,
            apps = rows.map { row -> PageApp(row, loadedByPackage[row.packageName]?.icon) },
        )
    }

    private fun loadInstalledApp(
        packageManager: PackageManager,
        info: ApplicationInfo,
    ): LoadedApp {
        val packageName = info.packageName
        val label = try {
            info.loadLabel(packageManager).toString().ifBlank { packageName }
        } catch (exception: RuntimeException) {
            Log.w(TAG, "Unable to load label for $packageName", exception)
            packageName
        }
        val icon = try {
            info.loadIcon(packageManager)
        } catch (exception: RuntimeException) {
            Log.w(TAG, "Unable to load icon for $packageName", exception)
            null
        }
        return LoadedApp(packageName, label, icon)
    }

    private fun renderPage(page: PageData) {
        currentPage = page
        statusPreference().isVisible = false

        val state = page.state
        masterPreference().apply {
            // This switch represents the caller user's stored choice. The
            // operator property is a separate hard gate: when it is off, keep
            // the choice visible but make it unavailable instead of pretending
            // the user's setting was rewritten.
            isChecked = state.userEnabled
            isEnabled = state.platformEnabled
            summary = getString(
                when {
                    !state.platformEnabled -> R.string.parallel_window_master_summary_platform_off
                    state.userEnabled -> R.string.parallel_window_master_summary_on
                    else -> R.string.parallel_window_master_summary_off
                },
            )
        }

        val appsEnabled = state.platformEnabled && state.userEnabled
        appCategory().removeAll()
        if (page.apps.isEmpty()) {
            appCategory().addPreference(messagePreference(R.string.parallel_window_no_apps))
            return
        }
        page.apps.forEach { app ->
            appCategory().addPreference(appPreference(app, appsEnabled))
        }
    }

    private fun appPreference(app: PageApp, controlsEnabled: Boolean) =
        SwitchPreferenceCompat(requireContext()).apply {
            key = "$KEY_APP_PREFIX${app.row.packageName}"
            title = app.row.label
            summary = app.row.packageName
            icon = app.icon
            isPersistent = false
            isChecked = app.row.enabled
            isEnabled = controlsEnabled
            setOnPreferenceChangeListener { _, value ->
                val enabled = value as? Boolean ?: return@setOnPreferenceChangeListener false
                mutate(
                    changedApp = app,
                    requestedEnabled = enabled,
                    change = { backend.setPackageEnabled(app.row.packageName, enabled) },
                )
                false
            }
        }

    private fun showRejectedMutation(page: PageData?) {
        showMutationError(
            page ?: currentPage,
            R.string.parallel_window_save_error_title,
            R.string.parallel_window_save_error_summary,
        )
    }

    private fun showUnconfirmedMutation(page: PageData?) {
        // Prefer a post-write page when one was read. Otherwise keep the last
        // confirmed page: a RemoteException does not prove whether the remote
        // side committed the write, so claiming either value would be a lie.
        showMutationError(
            page ?: currentPage,
            R.string.parallel_window_confirm_error_title,
            R.string.parallel_window_confirm_error_summary,
        )
    }

    private fun showMutationError(page: PageData?, titleRes: Int, summaryRes: Int) {
        page?.let(::renderPage) ?: showLoading()
        statusPreference().apply {
            title = getString(titleRes)
            summary = getString(summaryRes)
            isVisible = true
        }
        Toast.makeText(
            requireContext(),
            titleRes,
            Toast.LENGTH_LONG,
        ).show()
    }

    private fun showLoadFailure() {
        currentPage = null
        masterPreference().apply {
            isChecked = false
            isEnabled = false
        }
        appCategory().apply {
            removeAll()
            addPreference(messagePreference(R.string.parallel_window_error_summary))
        }
        statusPreference().apply {
            title = getString(R.string.parallel_window_error_title)
            summary = getString(R.string.parallel_window_error_summary)
            isVisible = true
        }
        Toast.makeText(
            requireContext(),
            R.string.parallel_window_error_title,
            Toast.LENGTH_LONG,
        ).show()
    }

    private fun showNextProcessNotice(changedApp: PageApp?) {
        val builder = AlertDialog.Builder(requireContext())
            .setTitle(R.string.parallel_window_change_title)
        if (changedApp == null) {
            builder
                .setMessage(R.string.parallel_window_master_change_message)
                .setPositiveButton(android.R.string.ok, null)
        } else {
            builder
                .setMessage(
                    getString(R.string.parallel_window_change_message, changedApp.row.label),
                )
                .setNegativeButton(R.string.parallel_window_later, null)
                .setPositiveButton(R.string.parallel_window_app_info) { _, _ ->
                    openAppInfo(changedApp.row.packageName)
                }
        }
        builder.show()
    }

    private fun openAppInfo(packageName: String) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        )
        try {
            startActivity(intent)
        } catch (exception: RuntimeException) {
            Log.e(TAG, "Unable to open app info for $packageName", exception)
            Toast.makeText(
                requireContext(),
                R.string.parallel_window_error_title,
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun showLoading() {
        appCategory().apply {
            removeAll()
            addPreference(messagePreference(R.string.parallel_window_loading))
        }
    }

    private fun messagePreference(titleRes: Int) = Preference(requireContext()).apply {
        title = getString(titleRes)
        isSelectable = false
        isPersistent = false
    }

    private fun setAppPreferencesEnabled(enabled: Boolean) {
        val category = appCategory()
        for (index in 0 until category.preferenceCount) {
            category.getPreference(index).isEnabled = enabled
        }
    }

    private fun masterPreference(): SwitchPreferenceCompat =
        requireNotNull(findPreference(KEY_MASTER))

    private fun statusPreference(): Preference = requireNotNull(findPreference(KEY_STATUS))

    private fun appCategory(): PreferenceCategory = requireNotNull(findPreference(KEY_APPS))

    private data class LoadedApp(
        val packageName: String,
        val label: String,
        val icon: Drawable?,
    )

    private data class PageApp(
        val row: ParallelWindowAppRow,
        val icon: Drawable?,
    )

    private data class PageData(
        val state: ParallelWindowControlState,
        val apps: List<PageApp>,
    )

    private sealed interface LoadOutcome {
        data class Success(val page: PageData) : LoadOutcome
        object Failure : LoadOutcome
    }

    private sealed interface MutationOutcome {
        data class Saved(val page: PageData) : MutationOutcome
        data class Rejected(val page: PageData) : MutationOutcome
        data class Unconfirmed(val page: PageData?) : MutationOutcome
    }

    private companion object {
        // Keep each mutation and its confirming reread ahead of any lifecycle
        // refresh queued after it, including across fragment recreation.
        val BACKGROUND_EXECUTOR = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "MalbecParts-parallel-window")
        }

        const val TAG = "ParallelWindowSettings"
        const val KEY_MASTER = "parallel_window_master"
        const val KEY_STATUS = "parallel_window_status"
        const val KEY_APPS = "parallel_window_apps"
        const val KEY_APP_PREFIX = "parallel_window_app:"
    }
}
