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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;

public class NetworkReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
		if (intent.getAction().equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
			NetworkInfo i = (NetworkInfo) intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
			WapdroidService ws = (WapdroidService) context;
			if (i.isConnected() ^ (ws.getSsid() != null)) {
				// a connection was gained or lost
				if (!ManageWakeLocks.hasLock()) {
					ManageWakeLocks.acquire(context);
					ws.setRelease(true);
				}
				ws.networkStateChanged(i.isConnected());
				if (ws.getRelease()) {
					// if connection was lost, check cells, otherwise, release
					if (!i.isConnected()) {
						ws.mAlarmMgr.cancel(ws.mPendingIntent);
						context.startService(new Intent(context, WapdroidService.class));
					} else ws.release();
				}
			}
		}
	}		
}
