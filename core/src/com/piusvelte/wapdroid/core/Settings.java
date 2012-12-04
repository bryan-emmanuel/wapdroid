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

import java.io.File;

import com.piusvelte.wapdroid.core.R;

import android.app.AlertDialog;
import android.app.Dialog;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceActivity;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

public class Settings extends PreferenceActivity implements OnSharedPreferenceChangeListener, OnClickListener {
	private SharedPreferences mSharedPreferences;
	private Button mBtn_SendLog;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getPreferenceManager().setSharedPreferencesName(getString(R.string.key_preferences));
		addPreferencesFromResource(R.xml.preferences);
		setContentView(R.layout.settings);
		mSharedPreferences = getSharedPreferences(getString(R.string.key_preferences), MODE_PRIVATE);
		mBtn_SendLog = (Button) findViewById(R.id.send_log);
		mBtn_SendLog.setOnClickListener(this);
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
				startService(Wapdroid.getPackageIntent(this, WapdroidService.class));
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
			} else
				stopService(Wapdroid.getPackageIntent(this, WapdroidService.class));
			// update widgets
			AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
			sendBroadcast(Wapdroid.getPackageIntent(this, WapdroidWidget.class).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetManager.getAppWidgetIds(new ComponentName(this, WapdroidWidget.class))));
		} else if (key.equals(getString(R.string.key_wifi_sleep_screen))) {
			if (sharedPreferences.getBoolean(key, false))
				showAlertMessage(R.string.pref_wifi_sleep, getSleepPolicyMessage(sharedPreferences.getBoolean(getString(R.string.key_wifi_sleep_mob_net), false), sharedPreferences.getBoolean(getString(R.string.key_wifi_sleep_charging), false)));
		} else if (key.equals(getString(R.string.key_wifi_sleep_mob_net))) {
			if (sharedPreferences.getBoolean(key, false))
				showAlertMessage(R.string.pref_wifi_sleep, getSleepPolicyMessage(true, sharedPreferences.getBoolean(getString(R.string.key_wifi_sleep_charging), false)));
		} else if (key.equals(getString(R.string.key_wifi_sleep_charging))) {
			if (sharedPreferences.getBoolean(key, false))
				showAlertMessage(R.string.pref_wifi_sleep, getSleepPolicyMessage(sharedPreferences.getBoolean(getString(R.string.key_wifi_sleep_mob_net), false), true));
		} else if (key.equals(getString(R.string.key_wifi_override_charging))) {
			if (sharedPreferences.getBoolean(key, false))
				showAlertMessage(R.string.pref_overrides, String.format(getString(R.string.msg_wifi_override), getString(R.string.msg_wifi_override_charging)));
		} else if (key.equals(getString(R.string.key_logging))) {
			if (sharedPreferences.getBoolean(key, false))
				showAlertMessage(R.string.pref_logging, getString(R.string.msg_logging));
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
	
	private void showAlertMessage(int title, String message) {
		(new AlertDialog.Builder(this)
		.setTitle(title)
		.setMessage(message)
		.setCancelable(true)
		.setNegativeButton(android.R.string.ok, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.cancel();
			}
		}))
		.show();
	}

	@Override
	public void onClick(View v) {
		if (v == mBtn_SendLog) {
			if (mSharedPreferences.getBoolean(getString(R.string.key_logging), false))
				showAlertMessage(R.string.label_send_log, getString(R.string.msg_stop_logging));
			else {
				File logFile = new File(Environment.getExternalStorageDirectory().getPath() + "/wapdroid/wapdroid.log");
				if (logFile.exists()) {
					Intent emailIntent = new Intent(android.content.Intent.ACTION_SEND);
					emailIntent.setType("text/plain");
					emailIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name));
					emailIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse("file://" + Environment.getExternalStorageDirectory().getPath() + "/wapdroid/wapdroid.log"));
					startActivity(Intent.createChooser(emailIntent, getString(R.string.label_send_log)));
				} else
					showAlertMessage(R.string.label_send_log, getString(R.string.msg_no_log));
			}
		}
	}
}