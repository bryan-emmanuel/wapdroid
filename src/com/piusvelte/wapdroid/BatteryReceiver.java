package com.piusvelte.wapdroid;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BatteryReceiver extends BroadcastReceiver {
	private static final String BATTERY_EXTRA_LEVEL = "level";
	private static final String BATTERY_EXTRA_SCALE = "scale";

	@Override
	public void onReceive(Context context, Intent intent) {
		if (intent.getAction().equals(Intent.ACTION_BATTERY_CHANGED)) {
			int currectBattPerc = Math.round(intent.getIntExtra(BATTERY_EXTRA_LEVEL, 0) * 100 / intent.getIntExtra(BATTERY_EXTRA_SCALE, 100));
			WapdroidService ws = (WapdroidService) context;
			// check if the threshold was crossed
			if (ws.isManageWifi() && !ws.isManualOverride() && (currectBattPerc < ws.getBatteryLimit()) && (ws.getLastBattPerc() >= ws.getBatteryLimit())) {
				ws.setWifiState(false);
				if (ws.hasPhoneListener()) ws.destroyPhoneListener();
			} else if ((currectBattPerc >= ws.getBatteryLimit()) && (ws.getLastBattPerc() < ws.getBatteryLimit()) && !ws.hasPhoneListener()) ws.createPhoneListener();
			ws.setLastBattPerc(currectBattPerc);			
			if (ws.hasUi()) ws.setUiBatt(currectBattPerc);
		}
	}
}
