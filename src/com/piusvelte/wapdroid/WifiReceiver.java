package com.piusvelte.wapdroid;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;

public class WifiReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
		if (intent.getAction().equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
			WapdroidService ws = (WapdroidService) context;
			if (!ManageWakeLocks.hasLock()) {
				ManageWakeLocks.acquire(context);
				ws.setRelease(true);
			}
			ws.wifiStateChanged(intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 4));
			if (ws.getRelease()) ManageWakeLocks.release();
		}
	}		
}
