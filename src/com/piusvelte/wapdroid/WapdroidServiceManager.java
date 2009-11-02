/*
 * Wapdroid - Android Location based Wifi Manager
 * Copyright (C) 2009 Bryan Emmanuel
 * 
 * This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.

 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *  
 *  Bryan Emmanuel piusvelte@gmail.com
 */

package com.piusvelte.wapdroid;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class WapdroidServiceManager extends BroadcastReceiver {
	private static final String mPreferenceManageWifi = "manageWifi";
	private static final String PREF_FILE_NAME = "wapdroid";
	private SharedPreferences mPreferences;

	@Override
	public void onReceive(Context context, Intent intent) {
		if ("android.intent.action.BOOT_COMPLETED".equals(intent.getAction())) {
			// check if Wapdroid is enabled, and start the service
			mPreferences = context.getSharedPreferences(PREF_FILE_NAME, WapdroidUI.MODE_PRIVATE);
			if (mPreferences.getBoolean(mPreferenceManageWifi, true)) {
				context.startService(new Intent().setComponent(new ComponentName(context.getPackageName(), WapdroidService.class.getName())));}}}}
