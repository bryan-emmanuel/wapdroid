package com.piusvelte.wapdroid;

import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ScreenReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
		if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
			WapdroidService ws = (WapdroidService) context;
			ws.mAlarmMgr.cancel(ws.mPendingIntent);
			ManageWakeLocks.release();
			context.startService(new Intent(context, WapdroidService.class));
		} else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
			WapdroidService ws = (WapdroidService) context;
			ws.mManualOverride = false;
			if (ws.mInterval > 0) ws.mAlarmMgr.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + ws.mInterval, ws.mPendingIntent);
		}
	}
}
