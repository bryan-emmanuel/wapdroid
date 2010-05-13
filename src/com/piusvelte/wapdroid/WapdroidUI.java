/*
 * Wapdroid - Android Location based Wifi Manager
 * Copyright (C) 2009 Bryan Emmanuel
 * 
 * This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.

 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *  
 *  Bryan Emmanuel piusvelte@gmail.com
 */

package com.piusvelte.wapdroid;

import com.piusvelte.wapdroid.R;

import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.telephony.CellLocation;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

public class WapdroidUI extends Activity {
	public static final int MANAGE_ID = Menu.FIRST;
	public static final int SETTINGS_ID = Menu.FIRST + 1;
	public static final int WIFI_ID = Menu.FIRST + 2;
	public static final int ABOUT_ID = Menu.FIRST + 3;
	private TextView field_CID, field_MNC, field_MCC, field_wifiState, field_wifiBSSID;
	private boolean mWifiIsEnabled = false;
	private WapdroidWifiReceiver mWifiReceiver;
	private WifiManager mWifiManager;
	private int mWifiState;
	private String mSSID, mBSSID;
	private static final String WIFI_CHANGE = WifiManager.WIFI_STATE_CHANGED_ACTION;
	private static final String NETWORK_CHANGE = WifiManager.NETWORK_STATE_CHANGED_ACTION;
	private static final int mWifiDisabling = WifiManager.WIFI_STATE_DISABLING;
	private static final int mWifiEnabling = WifiManager.WIFI_STATE_ENABLING;
	private static final int mWifiEnabled = WifiManager.WIFI_STATE_ENABLED;
	private static final int mWifiUnknown = WifiManager.WIFI_STATE_UNKNOWN;
	private TelephonyManager mTeleManager;
	private final PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
    	public void onCellLocationChanged(CellLocation location) {
    		checkLocation(location);}};
		
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        /*
         * manual control of wifi is enabled through the UI, but listen for changes made by the service
         */
        setContentView(R.layout.main);
        field_CID = (TextView) findViewById(R.id.field_CID);
		field_MNC = (TextView) findViewById(R.id.field_MNC);
		field_MCC = (TextView) findViewById(R.id.field_MCC);
		field_wifiState = (TextView) findViewById(R.id.field_wifiState);
		field_wifiBSSID = (TextView) findViewById(R.id.field_wifiBSSID);
		mWifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
		mWifiReceiver = new WapdroidWifiReceiver();
		mTeleManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);}
	
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	boolean result = super.onCreateOptionsMenu(menu);
    	menu.add(0, MANAGE_ID, 0, R.string.menu_manageNetworks).setIcon(android.R.drawable.ic_menu_manage);
    	menu.add(0, SETTINGS_ID, 0, R.string.menu_settings).setIcon(android.R.drawable.ic_menu_preferences);
    	menu.add(0, WIFI_ID, 0, R.string.label_WIFI).setIcon(android.R.drawable.ic_menu_manage);
    	menu.add(0, ABOUT_ID, 0, R.string.label_about).setIcon(android.R.drawable.ic_menu_more);
    	return result;}

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	Intent intent;
    	switch (item.getItemId()) {
    	case MANAGE_ID:
        	intent = new Intent(this, ManageData.class);
        	startActivity(intent);
    		return true;
    	case SETTINGS_ID:
    		startActivity(new Intent(this, Settings.class));
    		return true;
    	case WIFI_ID:
			startActivity(new Intent().setComponent(new ComponentName("com.android.settings", "com.android.settings.wifi.WifiSettings")));
			return true;
    	case ABOUT_ID:
    		Dialog dialog = new Dialog(this);
            dialog.setContentView(R.layout.about);
            dialog.setTitle(R.string.label_about);
            Button donate = (Button) dialog.findViewById(R.id.button_donate);
            donate.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					startActivity(new Intent(Intent.ACTION_VIEW).setData(Uri.parse("http://www.piusvelte.com?p=wapdroid")));}});
            dialog.show();
    		return true;}
        return super.onOptionsItemSelected(item);}
    
    @Override
    public void onPause() {
    	super.onPause();
   		unregisterReceiver(mWifiReceiver);
   		mTeleManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);}
    
    @Override
    public void onResume() {
    	super.onResume();
    	SharedPreferences prefs = getSharedPreferences(getString(R.string.key_preferences), MODE_PRIVATE);
        if (prefs.getBoolean(getString(R.string.key_manageWifi), true)) startService(new Intent(this, WapdroidService.class));
		mWifiState = mWifiManager.getWifiState();
		mWifiIsEnabled = (mWifiState == mWifiEnabled);
		if (mWifiIsEnabled) setWifiInfo(mWifiManager.getConnectionInfo());
		else clearWifiInfo();
		wifiChanged();
    	IntentFilter intentfilter = new IntentFilter();
    	intentfilter.addAction(WIFI_CHANGE);
    	intentfilter.addAction(NETWORK_CHANGE);
    	registerReceiver(mWifiReceiver, intentfilter);
		checkLocation(mTeleManager.getCellLocation());}
    
	private void wifiChanged() {
		if (mWifiIsEnabled) {
			if (mSSID != null) {
				field_wifiState.setText(mSSID);
				field_wifiBSSID.setText(mBSSID);}
			else {
				field_wifiState.setText(getString(R.string.label_enabled));
				field_wifiBSSID.setText("");}}
		else {
			field_wifiState.setText((mWifiState == mWifiEnabling ?
					getString(R.string.label_enabling)
					: (mWifiState == mWifiDisabling ?
							getString(R.string.label_disabling)
							: getString(R.string.label_disabled))));
			field_wifiBSSID.setText("");}}
	
	private void setWifiInfo(WifiInfo info) {
		mSSID = info.getSSID();
		mBSSID = info.getBSSID();}
    
    private void clearWifiInfo() {
		mSSID = null;
		mBSSID = null;}
    
    public class WapdroidWifiReceiver extends BroadcastReceiver {    	
    	@Override
    	public void onReceive(Context context, Intent intent) {
    		if (intent.getAction().equals(WIFI_CHANGE)){
    			int mState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 4);
    	    	if (mState != mWifiUnknown) {
    	    		mWifiState = mState;
    	    		mWifiIsEnabled = (mState == mWifiEnabled);
    	    		if (!mWifiIsEnabled) clearWifiInfo();}
    			wifiChanged();}
    		else if (intent.getAction().equals(NETWORK_CHANGE)) {
    			NetworkInfo info = (NetworkInfo) intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
    	    	if (info.isConnected()) setWifiInfo(mWifiManager.getConnectionInfo());
    	    	else clearWifiInfo();
    			wifiChanged();}}}
    
    private void checkLocation(CellLocation location) {
    	int cid = -1;
   		if (mTeleManager.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM) cid = ((GsmCellLocation) location).getCid();
       	else if (mTeleManager.getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA) {
    		// check the phone type, cdma is not available before API 2.0, so use a wrapper
       		try {
       			cid = (new CdmaCellLocationWrapper(location)).getBaseStationId();}
       		catch (Throwable t) {
       			cid = -1;}}
   		if (cid > 0) field_CID.setText(Integer.toString(cid));
   		field_MNC.setText(mTeleManager.getNetworkOperatorName());
   		field_MCC.setText(mTeleManager.getNetworkCountryIso());
   		mTeleManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CELL_LOCATION);}}