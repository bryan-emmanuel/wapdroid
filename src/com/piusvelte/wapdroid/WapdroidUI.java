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

import com.admob.android.ads.AdListener;
import com.admob.android.ads.AdView;
import com.piusvelte.wapdroid.R;

import android.app.Activity;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

public class WapdroidUI extends Activity implements AdListener {
	public static final int MANAGE_ID = Menu.FIRST;
	public static final int SETTINGS_ID = Menu.FIRST + 1;
	public static final int WIFI_ID = Menu.FIRST + 2;
	public static final int ABOUT_ID = Menu.FIRST + 3;
	private TextView field_CID, field_wifiState, field_wifiBSSID, field_signal, field_battery, field_LAC, field_status;
	private ServiceConn mServiceConn;
	private String mCells = "";
		
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        field_CID = (TextView) findViewById(R.id.field_CID);
		field_wifiState = (TextView) findViewById(R.id.field_wifiState);
		field_wifiBSSID = (TextView) findViewById(R.id.field_wifiBSSID);
		field_signal = (TextView) findViewById(R.id.field_signal);
		field_battery = (TextView) findViewById(R.id.field_battery);
		field_LAC = (TextView) findViewById(R.id.field_LAC);
		field_status = (TextView) findViewById(R.id.field_status);}
	
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
    	switch (item.getItemId()) {
    	case MANAGE_ID:
    		Intent intent = new Intent(this, ManageData.class);
    		intent.putExtra(WapdroidDbAdapter.TABLE_CELLS, mCells);
        	startActivity(intent);
    		return true;
    	case SETTINGS_ID:
    		startActivity(new Intent(this, Settings.class));
    		return true;
    	case WIFI_ID:
    		try {
    			mServiceConn.mIService.suspendWifiControl();}
    		catch (RemoteException e) {}
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
        if (mServiceConn != null) {
            if (mServiceConn.mIService != null) {
                try {
                        mServiceConn.mIService.setCallback(null);}
                catch (RemoteException e) {}}
            unbindService(mServiceConn);
            mServiceConn = null;}}
    
    @Override
    public void onResume() {
    	super.onResume();
    	SharedPreferences prefs = getSharedPreferences(getString(R.string.key_preferences), MODE_PRIVATE);
        if (prefs.getBoolean(getString(R.string.key_manageWifi), true)) startService(new Intent(this, WapdroidService.class));
        mServiceConn = new ServiceConn(mWapdroidUI);
        bindService(new Intent(this, WapdroidService.class), mServiceConn, BIND_AUTO_CREATE);}

    private IWapdroidUI.Stub mWapdroidUI = new IWapdroidUI.Stub() {
		public void setCellInfo(int cid, int lac) throws RemoteException {
	   		field_CID.setText(Integer.toString(cid));
	   		field_LAC.setText(Integer.toString(lac));}
		
		public void setWifiInfo(int state, String ssid, String bssid)
				throws RemoteException {
			if (state == WifiManager.WIFI_STATE_ENABLED) {
				if (ssid != null) {
					field_wifiState.setText(ssid);
					field_wifiBSSID.setText(bssid);}
				else {
					field_wifiState.setText(getString(R.string.label_enabled));
					field_wifiBSSID.setText("");}}
			else if (state != WifiManager.WIFI_STATE_UNKNOWN) {
				field_wifiState.setText((state == WifiManager.WIFI_STATE_ENABLING ?
						getString(R.string.label_enabling)
						: (state == WifiManager.WIFI_STATE_DISABLING ?
								getString(R.string.label_disabling)
								: getString(R.string.label_disabled))));
				field_wifiBSSID.setText("");}}
		
		public void setSignalStrength(int rssi) throws RemoteException {
			field_signal.setText((rssi != WapdroidDbAdapter.UNKNOWN_RSSI ? (Integer.toString(rssi) + getString(R.string.dbm)) : getString(R.string.scanning)));}

		public void setBattery(int batteryPercentage) throws RemoteException {
			field_battery.setText(Integer.toString(batteryPercentage) + "%");}

		public void setCells(String cells) throws RemoteException {
			mCells = cells;}

		public void setOperator(String operator)
				throws RemoteException {}

		public void inRange(boolean inrange) throws RemoteException {
			field_status.setText(getString(inrange ? R.string.withinarea : R.string.outofarea));}};

	@Override
	public void onFailedToReceiveAd(AdView arg0) {}

	@Override
	public void onFailedToReceiveRefreshedAd(AdView arg0) {}

	@Override
	public void onReceiveAd(AdView arg0) {}

	@Override
	public void onReceiveRefreshedAd(AdView arg0) {}}