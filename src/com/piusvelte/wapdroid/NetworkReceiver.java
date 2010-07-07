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
						ws.cancelAlarm();
						context.startService(new Intent(context, WapdroidService.class));
					} else ws.release();
				}
			}
		}
	}		
}
