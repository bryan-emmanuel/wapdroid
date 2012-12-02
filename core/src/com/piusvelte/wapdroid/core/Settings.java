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
package com.piusvelte.wapdroid.core;

import com.piusvelte.wapdroid.core.R;

import android.app.AlertDialog;
import android.app.Dialog;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceActivity;

public class Settings extends PreferenceActivity implements OnSharedPreferenceChangeListener {
	private SharedPreferences mSharedPreferences;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getPreferenceManager().setSharedPreferencesName(getString(R.string.key_preferences));
		addPreferencesFromResource(R.xml.preferences);
		mSharedPreferences = getSharedPreferences(getString(R.string.key_preferences), MODE_PRIVATE);
	}

	@Override
	public void onPause() {
		super.onPause();
		mSharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
	}

	@Override
	public void onResume() {
		super.onResume();
		mSharedPreferences.registerOnSharedPreferenceChangeListener(this);
	}

	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (key.equals(getString(R.string.key_manageWifi))) {
			if (sharedPreferences.getBoolean(key, false)) {
				this.startService(Wapdroid.getPackageIntent(this, WapdroidService.class));
				(new AlertDialog.Builder(this)
				.setMessage(R.string.background_info)
				.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.cancel();
						startActivity(new Intent(android.provider.Settings.ACTION_WIFI_IP_SETTINGS));
					}
				}))
				.show();
			} else this.stopService(Wapdroid.getPackageIntent(this, WapdroidService.class));
			// update widgets
			AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
			this.sendBroadcast(Wapdroid.getPackageIntent(this, WapdroidWidget.class).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetManager.getAppWidgetIds(new ComponentName(this, WapdroidWidget.class))));
		} else if (key.equals(getString(R.string.key_wifi_sleep_screen))) {
			if (sharedPreferences.getBoolean(key, false)) {
				(new AlertDialog.Builder(this)
				.setTitle(R.string.pref_wifi_sleep)
				.setMessage(getSleepPolicyMessage(sharedPreferences.getBoolean(getString(R.string.key_wifi_sleep_mob_net), false), sharedPreferences.getBoolean(getString(R.string.key_wifi_sleep_charging), false)))
				.setCancelable(true)
				.setNegativeButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.cancel();
					}
				}))
				.show();
			}
		} else if (key.equals(getString(R.string.key_wifi_sleep_mob_net))) {
			if (sharedPreferences.getBoolean(key, false)) {
				(new AlertDialog.Builder(this)
				.setTitle(R.string.pref_wifi_sleep)
				.setMessage(getSleepPolicyMessage(true, sharedPreferences.getBoolean(getString(R.string.key_wifi_sleep_charging), false)))
				.setCancelable(true)
				.setNegativeButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.cancel();
					}
				}))
				.show();
			}
		} else if (key.equals(getString(R.string.key_wifi_sleep_charging))) {
			if (sharedPreferences.getBoolean(key, false)) {
				(new AlertDialog.Builder(this)
				.setTitle(R.string.pref_wifi_sleep)
				.setMessage(getSleepPolicyMessage(sharedPreferences.getBoolean(getString(R.string.key_wifi_sleep_mob_net), false), true))
				.setCancelable(true)
				.setNegativeButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.cancel();
					}
				}))
				.show();
			}
		} else if (key.equals(getString(R.string.key_wifi_override_charging))) {
			if (sharedPreferences.getBoolean(key, false)) {
				(new AlertDialog.Builder(this)
				.setTitle(R.string.pref_overrides)
				.setMessage(String.format(getString(R.string.msg_wifi_override), getString(R.string.msg_wifi_override_charging)))
				.setCancelable(true)
				.setNegativeButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.cancel();
					}
				}))
				.show();
			}
		}
	}

	private String getSleepPolicyMessage(boolean mob_net, boolean charging) {
		return String.format(getString(R.string.msg_wifi_sleep), mob_net ?
				String.format(charging ?
						String.format(getString(R.string.msg_wifi_sleep_charging), getString(R.string.msg_wifi_sleep_mob_net))
						: getString(R.string.msg_wifi_sleep_mob_net), getString(R.string.msg_wifi_sleep_screen))
						: charging ?
								String.format(getString(R.string.msg_wifi_sleep_charging), getString(R.string.msg_wifi_sleep_screen))
								: getString(R.string.msg_wifi_sleep_screen));
	}
}