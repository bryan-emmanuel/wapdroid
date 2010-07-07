package com.piusvelte.wapdroid;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ScreenReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
		if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
			((WapdroidService) context).cancelAlarm();
			ManageWakeLocks.release();
			context.startService(new Intent(context, WapdroidService.class));
		}
		else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
			WapdroidService ws = (WapdroidService) context;
			ws.setManualOverride(false);
			if (ws.getInterval() > 0) ws.setAlarm();
		}
	}
}
