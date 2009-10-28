package com.piusvelte.wapdroid;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

public class WapdroidServiceManager extends BroadcastReceiver {
	private static final String TAG = "WapdroidServiceManager";
	private static final String mPreferenceManageWifi = "manageWifi";
	private static final String PREF_FILE_NAME = "wapdroid";
	private SharedPreferences mPreferences;

	@Override
	public void onReceive(Context context, Intent intent) {
		if ("android.intent.action.BOOT_COMPLETED".equals(intent.getAction())) {
			// check if Wapdroid is enabled, and start the service
			Log.v(TAG,"boot completed");
			mPreferences = context.getSharedPreferences(PREF_FILE_NAME, WapdroidUI.MODE_PRIVATE);
			if (mPreferences.getBoolean(mPreferenceManageWifi, true)) {
				ComponentName mWapdroidService = context.startService(new Intent().setComponent(new ComponentName(context.getPackageName(), WapdroidService.class.getName())));
				Log.v(TAG,"starting wapdroid service");}
		}}}
