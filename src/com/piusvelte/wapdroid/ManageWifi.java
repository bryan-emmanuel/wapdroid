package com.piusvelte.wapdroid;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;

public class ManageWifi extends BroadcastReceiver {
	private Wapdroid mWapdroid;
	
	public ManageWifi(Wapdroid wapdroid) {
		mWapdroid = wapdroid;}
	
	@Override
	public void onReceive(Context context, Intent intent) {
		if (intent.getAction().equals(WifiManager.WIFI_STATE_CHANGED_ACTION)){
			mWapdroid.stateChanged(intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 4));}
		else if (intent.getAction().equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
			mWapdroid.networkChanged((NetworkInfo) intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO));}}}
