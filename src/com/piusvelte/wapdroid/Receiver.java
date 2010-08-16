package com.piusvelte.wapdroid;

import static android.content.Intent.ACTION_BOOT_COMPLETED;
import static android.content.Intent.ACTION_PACKAGE_ADDED;
import static android.content.Intent.ACTION_PACKAGE_REPLACED;
import static com.piusvelte.wapdroid.WapdroidService.WAKE_SERVICE;
import static com.piusvelte.wapdroid.WapdroidService.mApi7;
import static com.piusvelte.wapdroid.WapdroidService.LISTEN_SIGNAL_STRENGTHS;
import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.RemoteException;
import android.telephony.PhoneStateListener;

public class Receiver extends BroadcastReceiver {
	private static final String BATTERY_EXTRA_LEVEL = "level";
	private static final String BATTERY_EXTRA_SCALE = "scale";
	private static final String BATTERY_EXTRA_PLUGGED = "plugged";

	@Override
	public void onReceive(Context context, Intent intent) {
		// on boot, or package upgrade, start the service
		if (intent.getAction().equals(ACTION_BOOT_COMPLETED) || intent.getAction().equals(ACTION_PACKAGE_ADDED) || intent.getAction().equals(ACTION_PACKAGE_REPLACED)) {
			SharedPreferences sp = context.getSharedPreferences(context.getString(R.string.key_preferences), WapdroidService.MODE_PRIVATE);
			if (sp.getBoolean(context.getString(R.string.key_manageWifi), true)) {
				ManageWakeLocks.acquire(context);
				context.startService(new Intent(context, WapdroidService.class));
			}
		} else if (intent.getAction().equals(WAKE_SERVICE)) {
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
				if (ws.mPhoneListener != null) {
					ws.mTeleManager.listen(ws.mPhoneListener, PhoneStateListener.LISTEN_NONE);
					ws.mPhoneListener = null;;
				}
			} else if ((currentBattPerc >= ws.mBatteryLimit) && (ws.mLastBattPerc < ws.mBatteryLimit) && (ws.mPhoneListener == null)) ws.mTeleManager.listen(ws.mPhoneListener = (mApi7 ? (new PhoneListenerApi7(ws.mContext)) : (new PhoneListenerApi3(ws.mContext))), (PhoneStateListener.LISTEN_CELL_LOCATION | PhoneStateListener.LISTEN_SIGNAL_STRENGTH | LISTEN_SIGNAL_STRENGTHS));
			ws.mLastBattPerc = currentBattPerc;
			if (ws.mWapdroidUI != null) {
				try {
					ws.mWapdroidUI.setBattery(currentBattPerc);
				} catch (RemoteException e) {};
			}
		} else if (intent.getAction().equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
			NetworkInfo ni = (NetworkInfo) intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
			WapdroidService ws = (WapdroidService) context;
//			if (ni.isConnected() ^ (ws.mSsid != null)) {
				// a connection was gained or lost
				if (!ManageWakeLocks.hasLock()) {
					ManageWakeLocks.acquire(context);
					ws.mRelease = true;
				}
				//ws.mConnected = ni.isConnected();
				ws.networkStateChanged();
				if (ws.mRelease) {
					// if connection was lost, check cells, otherwise, release
					if (!ni.isConnected()) {
						ws.mAlarmMgr.cancel(ws.mPendingIntent);
						context.startService(new Intent(context, WapdroidService.class));
					} else ws.release();
				}
//			}
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
			WapdroidService ws = (WapdroidService) context;
			if (!ManageWakeLocks.hasLock()) {
				ManageWakeLocks.acquire(context);
				ws.mRelease = true;
			}
			ws.wifiStateChanged(intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 4));
			if (ws.mRelease) ManageWakeLocks.release();
		}
	}
}