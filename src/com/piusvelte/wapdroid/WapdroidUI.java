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
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.CompoundButton.OnCheckedChangeListener;

public class WapdroidUI extends Activity {
	public static final int MANAGE_ID = Menu.FIRST;
	public static final int SETTINGS_ID = Menu.FIRST + 1;
	private static final int SETTINGS_REQUEST_ID = 0;
	private TextView field_CID, field_MNC, field_MCC, label_CID, label_MNC, label_MCC, field_wifiState, field_wifiBSSID, label_wifiState, label_wifiBSSID;
	private CheckBox checkbox_wifiState, checkbox_wapdroidState;
	private static final String PREFERENCE_MANAGE = WapdroidService.PREFERENCE_MANAGE;
	private static final String PREFERENCE_NOTIFY = WapdroidService.PREFERENCE_NOTIFY;
	private static final String PREFERENCE_VIBRATE = WapdroidService.PREFERENCE_VIBRATE;
	private static final String PREFERENCE_LED = WapdroidService.PREFERENCE_LED;
	private static final String PREFERENCE_RINGTONE = WapdroidService.PREFERENCE_RINGTONE;
	private static final String PREF_FILE_NAME = WapdroidService.PREF_FILE_NAME;
	private SharedPreferences mPreferences;
	private SharedPreferences.Editor mEditor;
	private boolean mWapdroidEnabled = true, mWifiIsEnabled = false;
	private WapdroidWifiReceiver mWifiReceiver = null;
	private WapdroidDbAdapter mDbHelper = null;
	private WifiManager mWifiManager;
	private WifiInfo mWifiInfo;
	private int mWifiState;
	private String mSSID = null, mBSSID;
	private WapdroidServiceConnection mWapdroidServiceConnection;
	private IWapdroidService mWapdroidService;
	private static final String WIFI_CHANGE = WifiManager.WIFI_STATE_CHANGED_ACTION;
	private static final String NETWORK_CHANGE = WifiManager.NETWORK_STATE_CHANGED_ACTION;
	private static final int mWifiDisabling = WifiManager.WIFI_STATE_DISABLING;
	private static final int mWifiEnabling = WifiManager.WIFI_STATE_ENABLING;
	private static final int mWifiEnabled = WifiManager.WIFI_STATE_ENABLED;
	private static final int mWifiUnknown = WifiManager.WIFI_STATE_UNKNOWN;
		
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        /*
         * manual control of wifi is enabled through the UI, but listen for changes made by the service
         */
        setContentView(R.layout.main);
		mPreferences = getSharedPreferences(PREF_FILE_NAME, MODE_PRIVATE);
		mEditor = mPreferences.edit();
		mWapdroidEnabled = mPreferences.getBoolean(PREFERENCE_MANAGE, true);
		label_CID = (TextView) findViewById(R.id.label_CID);
    	field_CID = (TextView) findViewById(R.id.field_CID);
    	label_MNC = (TextView) findViewById(R.id.label_MNC);
    	field_MNC = (TextView) findViewById(R.id.field_MNC);
    	label_MCC = (TextView) findViewById(R.id.label_MCC);
    	field_MCC = (TextView) findViewById(R.id.field_MCC);
    	label_wifiState = (TextView) findViewById(R.id.label_wifiState);
    	field_wifiState = (TextView) findViewById(R.id.field_wifiState);
    	label_wifiBSSID = (TextView) findViewById(R.id.label_wifiBSSID);
    	field_wifiBSSID = (TextView) findViewById(R.id.field_wifiBSSID);
    	checkbox_wapdroidState = (CheckBox) findViewById(R.id.checkbox_wapdroidState);
    	checkbox_wapdroidState.setChecked(mWapdroidEnabled);
    	checkbox_wapdroidState.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				mWapdroidEnabled = isChecked;
				mEditor.putBoolean(PREFERENCE_MANAGE, isChecked);
				mEditor.commit();
				manageService();}});
		mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		mWifiIsEnabled = mWifiManager.isWifiEnabled();
		checkbox_wifiState = (CheckBox) findViewById(R.id.checkbox_wifiState);
    	checkbox_wifiState.setChecked(mWifiIsEnabled);
    	checkbox_wifiState.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				if ((isChecked && !mWifiIsEnabled && (mWifiState != mWifiEnabling)) || (!isChecked && mWifiIsEnabled)) {
					mWifiManager.setWifiEnabled(isChecked);}}});}
	
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	boolean result = super.onCreateOptionsMenu(menu);
    	menu.add(0, MANAGE_ID, 0, R.string.menu_manageNetworks).setIcon(android.R.drawable.ic_menu_manage);
    	menu.add(0, SETTINGS_ID, 0, R.string.menu_settings).setIcon(android.R.drawable.ic_menu_preferences);
    	return result;}

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	Intent intent;
    	switch (item.getItemId()) {
    	case MANAGE_ID:
        	intent = new Intent(this, ManageNetworks.class);
        	startActivity(intent);
    		return true;
    	case SETTINGS_ID:
        	intent = new Intent(this, Settings.class);
        	intent.putExtra(PREFERENCE_NOTIFY, mPreferences.getBoolean(PREFERENCE_NOTIFY, true));
        	intent.putExtra(PREFERENCE_VIBRATE, mPreferences.getBoolean(PREFERENCE_VIBRATE, false));
        	intent.putExtra(PREFERENCE_LED, mPreferences.getBoolean(PREFERENCE_LED, false));
        	intent.putExtra(PREFERENCE_RINGTONE, mPreferences.getBoolean(PREFERENCE_RINGTONE, false));
        	startActivityForResult(intent, SETTINGS_REQUEST_ID);
    		return true;}
        return super.onOptionsItemSelected(item);}
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    	super.onActivityResult(requestCode, resultCode, data);
		switch(requestCode) {
		case SETTINGS_REQUEST_ID:
	    	if (resultCode == RESULT_OK) {
	    		Bundle extras = data.getExtras();
				mEditor.putBoolean(PREFERENCE_NOTIFY, extras.getBoolean(PREFERENCE_NOTIFY));
				mEditor.putBoolean(PREFERENCE_VIBRATE, extras.getBoolean(PREFERENCE_VIBRATE));
				mEditor.putBoolean(PREFERENCE_LED, extras.getBoolean(PREFERENCE_LED));
				mEditor.putBoolean(PREFERENCE_RINGTONE, extras.getBoolean(PREFERENCE_RINGTONE));
				mEditor.commit();
				break;}}}
    
    @Override
    public void onPause() {
    	super.onPause();
    	if (mDbHelper != null) {
    		mDbHelper.close();
    		mDbHelper = null;}
    	if (mWifiReceiver != null) {
    		unregisterReceiver(mWifiReceiver);
    		mWifiReceiver = null;}
    	releaseService();}
    
    @Override
    public void onResume() {
    	super.onResume();
    	if (mWifiReceiver == null) {
    		IntentFilter intentfilter = new IntentFilter();
    		intentfilter.addAction(WIFI_CHANGE);
    		intentfilter.addAction(NETWORK_CHANGE);
    		registerReceiver(mWifiReceiver = new WapdroidWifiReceiver(), intentfilter);}
    	manageService();}
    
	private void wifiChanged() {
		if (mWifiIsEnabled) {
	    	checkbox_wifiState.setChecked(true);
			if (mSSID != null) {
				label_wifiState.setText(getString(R.string.label_connectedto));
				field_wifiState.setText(mSSID);
				label_wifiBSSID.setText(getString(R.string.label_BSSID));
				field_wifiBSSID.setText(mBSSID);}
			else {
				label_wifiState.setText(getString(R.string.label_enabled));
				field_wifiState.setText("");
				label_wifiBSSID.setText("");
				field_wifiBSSID.setText("");}}
		else {
	    	checkbox_wifiState.setChecked(false);
			label_wifiState.setText((mWifiState == mWifiEnabling ?
					getString(R.string.label_enabling)
					: (mWifiState == mWifiDisabling ?
							getString(R.string.label_disabling)
							: getString(R.string.label_disabled))));
			field_wifiState.setText("");
			label_wifiBSSID.setText("");
			field_wifiBSSID.setText("");}}
	
	private void releaseService() {
		if (mWapdroidServiceConnection != null) {
			if (mWapdroidService != null) {
				try {
					mWapdroidService.setCallback(null);}
				catch (RemoteException e) {}
				mWapdroidService = null;}
			unbindService(mWapdroidServiceConnection);
			mWapdroidServiceConnection = null;}
		stopService(new Intent(this, WapdroidService.class));}
    
    private void manageService() {
		if (mWapdroidEnabled) {
			if (mWapdroidServiceConnection == null) {
				mWapdroidServiceConnection = new WapdroidServiceConnection();
				bindService(new Intent(this, WapdroidService.class), mWapdroidServiceConnection, Context.BIND_AUTO_CREATE);}}
		else {
			releaseService();
	    	field_CID.setText("");
	    	field_MNC.setText("");
	    	field_MCC.setText("");}
    	label_CID.setEnabled(mWapdroidEnabled);
    	field_CID.setEnabled(mWapdroidEnabled);
    	label_MNC.setEnabled(mWapdroidEnabled);
    	field_MNC.setEnabled(mWapdroidEnabled);
    	label_MCC.setEnabled(mWapdroidEnabled);
    	field_MCC.setEnabled(mWapdroidEnabled);}
    
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
    	    		if (!mWifiIsEnabled) {
        	    		clearWifiInfo();}}
    			wifiChanged();}
    		else if (intent.getAction().equals(NETWORK_CHANGE)) {
    			NetworkInfo mNetworkInfo = (NetworkInfo) intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
    	    	if (mNetworkInfo.isConnected()) {
    	    		mWifiInfo = mWifiManager.getConnectionInfo();
    	    		mSSID = mWifiInfo.getSSID();
    	    		mBSSID = mWifiInfo.getBSSID();}
    	    	else {
    	    		clearWifiInfo();}
    			wifiChanged();}}}
    
    private IWapdroidUI.Stub mWapdroidUI = new IWapdroidUI.Stub() {
		public void setCellLocation(String mCID, String mMNC, String mMCC) throws RemoteException {
	    	field_CID.setText(mCID);
	    	field_MNC.setText(mMNC);
	    	field_MCC.setText(mMCC);}

		public void newCell(String cell) throws RemoteException {
			Toast.makeText(WapdroidUI.this, cell, Toast.LENGTH_SHORT).show();}};
    
	public class WapdroidServiceConnection implements ServiceConnection {
		public void onServiceConnected(ComponentName className, IBinder boundService) {
			mWapdroidService = IWapdroidService.Stub.asInterface((IBinder) boundService);
			try {
				mWapdroidService.setCallback(mWapdroidUI.asBinder());}
			catch (RemoteException e) {}}
		
		public void onServiceDisconnected(ComponentName className) {
			mWapdroidService = null;}}}