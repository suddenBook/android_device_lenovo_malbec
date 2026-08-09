/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.malbec.parts.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * A marker, not a receiver. It exists only so that this package is discoverable
 * as the launcher's layout customisation provider.
 *
 * Launcher3's Partner.findSystemApk (util/Partner.java:103-115) locates the
 * provider with
 *
 *     pm.queryBroadcastReceivers(new Intent(ACTION), MATCH_SYSTEM_ONLY)
 *
 * where ACTION is `android.autoinstalls.config.action.PLAY_AUTO_INSTALL`, and then
 * uses nothing from the ResolveInfo except `activityInfo.packageName` — it
 * immediately calls getResourcesForApplication() and reads
 * res/xml/default_layout_6x5_h6.xml. So the receiver is never sent anything: that
 * action is a marker and nobody broadcasts it.
 *
 * It is still a real class rather than a manifest entry pointing at nothing,
 * because a manifest naming a class that does not exist is a trap for whoever
 * reads it next, and because onReceive() then documents itself: if this ever
 * fires, something is wrong and doing nothing is the correct response.
 *
 * android:exported="false" in the manifest is deliberate and safe.
 * queryBroadcastReceivers matches on the intent filter only; `exported` is
 * enforced at send time in ActivityManagerService, not at query time. So the
 * launcher can still find us and no other uid can poke this.
 */
class LauncherLayoutMarker : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit
}
