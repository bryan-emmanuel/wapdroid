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
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *  
 *  Bryan Emmanuel piusvelte@gmail.com
 */

package com.piusvelte.wapdroid;

import static com.piusvelte.wapdroid.WapdroidService.LISTEN_SIGNAL_STRENGTHS;
import static com.piusvelte.wapdroid.WapdroidService.mApi7;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.RemoteException;
import android.telephony.PhoneStateListener;

public class BatteryReceiver extends BroadcastReceiver {
	private static final String BATTERY_EXTRA_LEVEL = "level";
	private static final String BATTERY_EXTRA_SCALE = "scale";

	@Override
	public void onReceive(Context context, Intent intent) {
		if (intent.getAction().equals(Intent.ACTION_BATTERY_CHANGED)) {
			int currentBattPerc = Math.round(intent.getIntExtra(BATTERY_EXTRA_LEVEL, 0) * 100 / intent.getIntExtra(BATTERY_EXTRA_SCALE, 100));
			WapdroidService ws = (WapdroidService) context;
			// check the threshold
			if (ws.mManageWifi && !ws.mManualOverride && (currentBattPerc < ws.mBatteryLimit) && (ws.mLastBattPerc >= ws.mBatteryLimit)) {
				ws.setWifiState(false);
				if (ws.mPhoneListener != null) {
					ws.mTeleManager.listen(ws.mPhoneListener, PhoneStateListener.LISTEN_NONE);
					ws.mPhoneListener = null;;
				}
			} else if ((currentBattPerc >= ws.mBatteryLimit) && (ws.mLastBattPerc < ws.mBatteryLimit) && (ws.mPhoneListener == null)) ws.mTeleManager.listen(ws.mPhoneListener = (mApi7 ? (new PhoneListenerApi7(ws.mService)) : (new PhoneListenerApi3(ws.mService))), (PhoneStateListener.LISTEN_CELL_LOCATION | PhoneStateListener.LISTEN_SIGNAL_STRENGTH | LISTEN_SIGNAL_STRENGTHS));
			ws.mLastBattPerc = currentBattPerc;
			if (ws.mWapdroidUI != null) {
				try {
					ws.mWapdroidUI.setBattery(currentBattPerc);
				} catch (RemoteException e) {};
			}
		}
	}
}
