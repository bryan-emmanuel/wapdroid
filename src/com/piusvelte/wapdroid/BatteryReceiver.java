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
		if (intent.getAction().equals(Intent.ACTION_BATTERY_CHANGED) && (context != null)) {
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
