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

import static com.piusvelte.wapdroid.WapdroidService.WAKE_SERVICE;
import static com.piusvelte.wapdroid.WapdroidService.LISTEN_SIGNAL_STRENGTHS;
import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.RemoteException;
import android.telephony.PhoneStateListener;

public class Receiver extends BroadcastReceiver {
	private static final String BATTERY_EXTRA_LEVEL = "level";
	private static final String BATTERY_EXTRA_SCALE = "scale";
	private static final String BATTERY_EXTRA_PLUGGED = "plugged";

	@Override
	public void onReceive(Context context, Intent intent) {
		if (intent.getAction().equals(WAKE_SERVICE)) {
			ManageWakeLocks.acquire(context);
			context.startService(new Intent(context, WapdroidService.class));
		} else if (intent.getAction().equals(Intent.ACTION_BATTERY_CHANGED)) {
			WapdroidService ws = (WapdroidService) context;
			// override low battery when charging
			if (intent.getIntExtra(BATTERY_EXTRA_PLUGGED, 0) != 0) {
				// plugged in
				ws.mBatteryLimit = 0;
			} else {
				// unplugged
				SharedPreferences sp = (SharedPreferences) context.getSharedPreferences(context.getString(R.string.key_preferences), WapdroidService.MODE_PRIVATE);
				if (sp.getBoolean(context.getString(R.string.key_battery_override), false)) ws.mBatteryLimit = Integer.parseInt((String) sp.getString(context.getString(R.string.key_battery_percentage), "30"));
			}
			int currentBattPerc = Math.round(intent.getIntExtra(BATTERY_EXTRA_LEVEL, 0) * 100 / intent.getIntExtra(BATTERY_EXTRA_SCALE, 100));
			// check the threshold
			if (ws.mManageWifi && !ws.mManualOverride && (currentBattPerc < ws.mBatteryLimit) && (ws.mLastBattPerc >= ws.mBatteryLimit)) {
				ws.mWifiManager.setWifiEnabled(false);
				ws.mTeleManager.listen(ws.mPhoneListener, PhoneStateListener.LISTEN_NONE);
			} else if ((currentBattPerc >= ws.mBatteryLimit) && (ws.mLastBattPerc < ws.mBatteryLimit)) ws.mTeleManager.listen(ws.mPhoneListener, (PhoneStateListener.LISTEN_CELL_LOCATION | PhoneStateListener.LISTEN_SIGNAL_STRENGTH | LISTEN_SIGNAL_STRENGTHS));
			ws.mLastBattPerc = currentBattPerc;
			if (ws.mWapdroidUI != null) {
				try {
					ws.mWapdroidUI.setBattery(currentBattPerc);
				} catch (RemoteException e) {};
			}
		} else if (intent.getAction().equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
			// grab a lock to wait for a cell change occur
			WapdroidService ws = (WapdroidService) context;
			// a connection was gained or lost
			if (!ManageWakeLocks.hasLock()) {
				ManageWakeLocks.acquire(context);
				ws.mAlarmMgr.cancel(ws.mPendingIntent);
			}
			ws.networkStateChanged();
		} else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
			WapdroidService ws = (WapdroidService) context;
			ws.mAlarmMgr.cancel(ws.mPendingIntent);
			ManageWakeLocks.release();
			context.startService(new Intent(context, WapdroidService.class));
		} else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
			WapdroidService ws = (WapdroidService) context;
			ws.mManualOverride = false;
			if (ws.mInterval > 0) ws.mAlarmMgr.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + ws.mInterval, ws.mPendingIntent);
		} else if (intent.getAction().equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
			// grab a lock to create notification
			WapdroidService ws = (WapdroidService) context;
			if (!ManageWakeLocks.hasLock()) {
				ManageWakeLocks.acquire(context);
				ws.mAlarmMgr.cancel(ws.mPendingIntent);
			}
			ws.wifiStateChanged(intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 4));
		}
	}
}