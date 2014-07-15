/*
 * Wapdroid - Android Location based Wifi Manager
 * Copyright (C) 2012 Bryan Emmanuel
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
    private Dialog mDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getPreferenceManager().setSharedPreferencesName(getString(R.string.key_preferences));
        addPreferencesFromResource(R.xml.preferences);
        setContentView(R.layout.settings);
        Wapdroid.setupBannerAd(this);
        mSharedPreferences = getSharedPreferences(getString(R.string.key_preferences), MODE_PRIVATE);
    }

    @Override
    public void onPause() {
        if (mDialog != null) mDialog.dismiss();
        mSharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
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
                if (mDialog != null) mDialog.dismiss();

                mDialog = (new AlertDialog.Builder(this)
                        .setMessage(R.string.background_info)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();
                                startActivity(new Intent(android.provider.Settings.ACTION_WIFI_IP_SETTINGS));
                            }
                        }))
                        .create();
                mDialog.show();
            } else
                stopService(Wapdroid.getPackageIntent(this, WapdroidService.class));
            // update widgets
            android.util.Log.d("Bryan", "settings trigger update");
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
        if (mDialog != null) mDialog.dismiss();

        mDialog = (new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(true)
                .setNegativeButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                }))
                .create();
        mDialog.show();
    }
}
