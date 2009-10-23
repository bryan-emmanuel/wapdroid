package com.piusvelte.wapdroid;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;

public class ManageWifi extends BroadcastReceiver {
	private Wapdroid mWapdroid;
	private WifiManager wifiManager;
	private int wifiState;
	private int wifiEnabled;
	private int wifiEnabling;
	private int wifiDisabling;
	private String mSSID = null;
	private static final String CONNECTEDTO = "connected to ";
	private static final String ENABLED = "enabled";
	private static final String ENABLING = "enabling";
	private static final String DISABLED = "disabled";
	private static final String DISABLING = "disabling";
	
	public ManageWifi(Wapdroid wapdroid) {
    	wifiEnabled = WifiManager.WIFI_STATE_ENABLED;
    	wifiEnabling = WifiManager.WIFI_STATE_ENABLING;
    	wifiDisabling = WifiManager.WIFI_STATE_DISABLING;
		mWapdroid = wapdroid;
    	wifiManager = (WifiManager) mWapdroid.getSystemService(Context.WIFI_SERVICE);
    	mWapdroid.registerReceiver(this, new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION));
    	mWapdroid.registerReceiver(this, new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION));
    	initWifi();}
	
	@Override
	public void onReceive(Context context, Intent intent) {
		initWifi();
		if ((mSSID != null) && (mSSID != "")) {
			mWapdroid.recordNetwork(mSSID);}}

    public void initWifi() {
    	wifiState = wifiManager.getWifiState();
    	mSSID = wifiManager.getConnectionInfo().getSSID();
		mWapdroid.updateWifiState(
				((mSSID != null) && (mSSID != "") ? CONNECTEDTO + mSSID :
					(wifiState == wifiEnabled ? ENABLED :
						(wifiState == wifiEnabling ? ENABLING :
							(wifiState == wifiDisabling ? DISABLING : DISABLED)))));}
	
	public boolean isEnabled() {
		return wifiManager.isWifiEnabled();}
	
	public boolean isConnected() {
		return (mSSID != null) && (mSSID != "");}
	
	public void setEnabled(boolean mEnable) {
		wifiManager.setWifiEnabled(mEnable);}
	
	public String getSSID() {
		return mSSID;}}
