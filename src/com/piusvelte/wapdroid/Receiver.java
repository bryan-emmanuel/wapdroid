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
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.RemoteException;
import android.telephony.TelephonyManager;

public class Receiver extends BroadcastReceiver {
	private static final String BATTERY_EXTRA_LEVEL = "level";
	private static final String BATTERY_EXTRA_SCALE = "scale";
	private static final String BATTERY_EXTRA_PLUGGED = "plugged";

	@Override
	public void onReceive(Context context, Intent intent) {
		if (intent.getAction().equals(Intent.ACTION_BATTERY_CHANGED)) {
			// override low battery when charging
			WapdroidService ws = (WapdroidService) context;
			if (intent.getIntExtra(BATTERY_EXTRA_PLUGGED, 0) != 0) ws.mBatteryLimit = 0;
			else {
				// unplugged
				SharedPreferences sp = (SharedPreferences) context.getSharedPreferences(context.getString(R.string.key_preferences), WapdroidService.MODE_PRIVATE);
				if (sp.getBoolean(context.getString(R.string.key_battery_override), false)) ws.mBatteryLimit = Integer.parseInt((String) sp.getString(context.getString(R.string.key_battery_percentage), "30"));
			}
			int currentBattPerc = Math.round(intent.getIntExtra(BATTERY_EXTRA_LEVEL, 0) * 100 / intent.getIntExtra(BATTERY_EXTRA_SCALE, 100));
			// check the threshold
			if (ws.mManageWifi && !ws.mManualOverride && (currentBattPerc < ws.mBatteryLimit) && (ws.mLastBattPerc >= ws.mBatteryLimit)) {
				((WifiManager) ws.getSystemService(Context.WIFI_SERVICE)).setWifiEnabled(false);
				ws.ignorePhone();
			} else if ((currentBattPerc >= ws.mBatteryLimit) && (ws.mLastBattPerc < ws.mBatteryLimit)) ws.listenPhone();
			ws.mLastBattPerc = currentBattPerc;
			if (ws.mWapdroidUI != null) {
				try {
					ws.mWapdroidUI.setBattery(currentBattPerc);
				} catch (RemoteException e) {};
			}
		} else if (intent.getAction().equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
			// grab a lock to wait for a cell change occur
			// a connection was gained or lost
			WapdroidService ws = (WapdroidService) context;
			if (!ManageWakeLocks.hasLock()) {
				ManageWakeLocks.acquire(context);
				ws.cancelAlarm();
			}
			ws.wifiConnection((WifiManager) ws.getSystemService(Context.WIFI_SERVICE));
			ws.getCellInfo(((TelephonyManager) ws.getSystemService(Context.TELEPHONY_SERVICE)).getCellLocation());
			if (ws.mWapdroidUI != null) {
				try {
					ws.mWapdroidUI.setWifiInfo(ws.mLastWifiState, ws.mSsid, ws.mBssid);
				} catch (RemoteException e) {}
			}
		} else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
			WapdroidService ws = (WapdroidService) context;
			ws.cancelAlarm();
			ManageWakeLocks.release();
			context.startService(new Intent(context, WapdroidService.class));
		} else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
			WapdroidService ws = (WapdroidService) context;
			ws.mManualOverride = false;
			if (ws.mInterval > 0) ws.setAlarm();
		} else if (intent.getAction().equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
			// grab a lock to create notification
			WapdroidService ws = (WapdroidService) context;
			if (!ManageWakeLocks.hasLock()) {
				ManageWakeLocks.acquire(context);
				ws.cancelAlarm();
			}
			/*
			 * get wifi state
			 * initially, lastWifiState is unknown, otherwise state is evaluated either enabled or not
			 * when wifi enabled, register network receiver
			 * when wifi not enabled, unregister network receiver
			 */
			ws.wifiState(intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 4));
			// a lock was only needed to send the notification, no cell changes need to be evaluated until a network state change occurs
			if (ManageWakeLocks.hasLock()) {
				if (ws.mInterval > 0) ws.setAlarm();
				ManageWakeLocks.release();
			}
		}
	}

}
