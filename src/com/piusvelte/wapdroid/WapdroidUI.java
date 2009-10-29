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
import android.widget.CompoundButton.OnCheckedChangeListener;

public class WapdroidUI extends Activity {
	public static final int MANAGE_ID = Menu.FIRST;
	public static final int RESET_ID = Menu.FIRST + 1;
	private TextView field_CID, field_LAC, field_MNC, field_MCC, field_RSSI, label_CID, label_LAC, label_MNC, label_MCC, label_RSSI, field_wifiState;
	private CheckBox checkbox_wifiState, checkbox_wapdroidState;
	private static final String mPreferenceManageWifi = "manageWifi";
	private static final String PREF_FILE_NAME = "wapdroid";
	private SharedPreferences mPreferences;
	private SharedPreferences.Editor mEditor;
	private boolean mWapdroidEnabled = true, mWifiIsEnabled = false;
	private WapdroidWifiReceiver mWifiReceiver = null;
	private WapdroidDbAdapter mDbHelper = null;
	private WifiManager mWifiManager;
	private WifiInfo mWifiInfo;
	private int mWifiState, mWifiDisabling, mWifiEnabling, mWifiEnabled, mWifiUnknown;
	private String mSSID = null;
	private WapdroidServiceConnection mWapdroidServiceConnection;
	private IWapdroidService mWapdroidService;
		
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
		mPreferences = getSharedPreferences(PREF_FILE_NAME, MODE_PRIVATE);
		mEditor = mPreferences.edit();
		mWapdroidEnabled = mPreferences.getBoolean(mPreferenceManageWifi, true);
		label_CID = (TextView) findViewById(R.id.label_CID);
    	field_CID = (TextView) findViewById(R.id.field_CID);
    	label_LAC = (TextView) findViewById(R.id.label_LAC);
    	field_LAC = (TextView) findViewById(R.id.field_LAC);
    	label_MNC = (TextView) findViewById(R.id.label_MNC);
    	field_MNC = (TextView) findViewById(R.id.field_MNC);
    	label_MCC = (TextView) findViewById(R.id.label_MCC);
    	field_MCC = (TextView) findViewById(R.id.field_MCC);
    	label_RSSI = (TextView) findViewById(R.id.label_RSSI);
    	field_RSSI = (TextView) findViewById(R.id.field_RSSI);
    	field_wifiState = (TextView) findViewById(R.id.field_wifiState);
    	checkbox_wifiState = (CheckBox) findViewById(R.id.checkbox_wifiState);
    	checkbox_wapdroidState = (CheckBox) findViewById(R.id.checkbox_wapdroidState);
    	checkbox_wapdroidState.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				mWapdroidEnabled = isChecked;
				mEditor.putBoolean(mPreferenceManageWifi, isChecked);
				manageService();}});
    	checkbox_wapdroidState.setChecked(mWapdroidEnabled);
    	checkbox_wifiState.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				if (isChecked ^ (mWifiIsEnabled || (mWifiState == mWifiEnabling))) {
					mWifiManager.setWifiEnabled(isChecked);}}});
    	checkbox_wifiState.setChecked(mWifiIsEnabled);
		mWifiDisabling = WifiManager.WIFI_STATE_DISABLING;
		mWifiEnabling = WifiManager.WIFI_STATE_ENABLING;
		mWifiEnabled = WifiManager.WIFI_STATE_ENABLED;
		mWifiUnknown = WifiManager.WIFI_STATE_UNKNOWN;
		mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
    	mWifiReceiver = new WapdroidWifiReceiver();
		registerReceiver(mWifiReceiver, new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION));
		registerReceiver(mWifiReceiver, new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION));
		manageService();}
	
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	boolean result = super.onCreateOptionsMenu(menu);
    	menu.add(0, MANAGE_ID, 0, R.string.menu_manageNetworks);
    	menu.add(0, RESET_ID, 0, R.string.menu_resetWapdroid);
    	return result;}

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	switch (item.getItemId()) {
    	case MANAGE_ID:
    		manageNetworks();
    		return true;
    	case RESET_ID:
    		if (mDbHelper == null) {
    			mDbHelper = new WapdroidDbAdapter(this);
    			mDbHelper.open();}
    		mDbHelper.resetDatabase();
    		return true;}
        return super.onOptionsItemSelected(item);}
    
    @Override
    public void onPause() {
    	super.onPause();
    	releaseResources();}
    
    @Override
    public void onResume() {
    	super.onResume();
    	acquireResources();}
    
    @Override
    public void onDestroy() {
    	super.onDestroy();
    	releaseResources();}
    
    private void acquireResources() {
    	if (mWifiReceiver == null) {
    		mWifiReceiver = new WapdroidWifiReceiver();
    		registerReceiver(mWifiReceiver, new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION));
    		registerReceiver(mWifiReceiver, new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION));}
    	manageService();}
    
    private void releaseResources() {
    	if (mDbHelper != null) {
    		mDbHelper.close();
    		mDbHelper = null;}
    	if (mWifiReceiver != null) {
    		unregisterReceiver(mWifiReceiver);
    		mWifiReceiver = null;}
    	if (mWapdroidServiceConnection != null) {
			if (mWapdroidService != null) {
				try {
					mWapdroidService.setCallback(null);}
				catch (RemoteException e) {}
				mWapdroidService = null;}
    		unbindService(mWapdroidServiceConnection);
    		mWapdroidServiceConnection = null;}}
    
    private void manageNetworks() {
    	Intent intent = new Intent(this, ManageNetworks.class);
    	startActivity(intent);}
    
	private void wifiChanged() {
		if (mWifiIsEnabled) {
	    	checkbox_wifiState.setChecked(true);
			if (mSSID != null) {
				field_wifiState.setText(getString(R.string.label_connectedto) + mSSID);}
			else {
				field_wifiState.setText(getString(R.string.label_enabled));}}
		else {
	    	checkbox_wifiState.setChecked(false);
			field_wifiState.setText((mWifiState == mWifiEnabling ?
					getString(R.string.label_enabling)
					: (mWifiState == mWifiDisabling ?
							getString(R.string.label_disabling)
							: getString(R.string.label_disabled))));}}
    
    private void manageService() {
    	int mColor = 0xFF909090;
		Intent i = new Intent();
		i.setClassName(this.getPackageName(), WapdroidService.class.getName());
		if (mWapdroidEnabled) {
    		startService(i);
			if (mWapdroidServiceConnection == null) {
				mWapdroidServiceConnection = new WapdroidServiceConnection();
				bindService(i, mWapdroidServiceConnection, Context.BIND_AUTO_CREATE);}}
		else {
        	mColor = 0xFF606060;
			if (mWapdroidServiceConnection != null) {
				if (mWapdroidService != null) {
					try {
						mWapdroidService.setCallback(null);}
					catch (RemoteException e) {}
					mWapdroidService = null;}
				unbindService(mWapdroidServiceConnection);
				mWapdroidServiceConnection = null;}
			stopService(i);
	    	field_CID.setText("");
	    	field_LAC.setText("");
	    	field_MNC.setText("");
	    	field_MCC.setText("");
	    	field_RSSI.setText("");}
    	label_CID.setTextColor(mColor);
    	field_CID.setTextColor(mColor);
    	label_LAC.setTextColor(mColor);
    	field_LAC.setTextColor(mColor);
    	label_MNC.setTextColor(mColor);
    	field_MNC.setTextColor(mColor);
    	label_MCC.setTextColor(mColor);
    	field_MCC.setTextColor(mColor);
    	label_RSSI.setTextColor(mColor);
    	field_RSSI.setTextColor(mColor);}
    
    public class WapdroidWifiReceiver extends BroadcastReceiver {    	
    	@Override
    	public void onReceive(Context context, Intent intent) {
    		if (intent.getAction().equals(WifiManager.WIFI_STATE_CHANGED_ACTION)){
    			int mState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 4);
    	    	if (mState != mWifiUnknown) {
    	    		mWifiState = mState;
    	    		mWifiIsEnabled = (mState == mWifiEnabled);
    	    		if (!mWifiIsEnabled) {
    	    			mSSID = null;}}
    			wifiChanged();}
    		else if (intent.getAction().equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
    			NetworkInfo mNetworkInfo = (NetworkInfo) intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
    	    	if (mNetworkInfo.isConnected()) {
    	    		mWifiInfo = mWifiManager.getConnectionInfo();
    	    		mSSID = mWifiInfo.getSSID();}
    	    	else {
    	    		mSSID = null;}
    			wifiChanged();}}}
    
    private IWapdroidUI.Stub mWapdroidUI = new IWapdroidUI.Stub() {
		public void locationChanged(String mCID, String mLAC, String mMNC, String mMCC) throws RemoteException {
	    	field_CID.setText(mCID);
	    	field_LAC.setText(mLAC);
	    	field_MNC.setText(mMNC);
	    	field_MCC.setText(mMCC);}
		public void signalChanged(String mRSSI) throws RemoteException {
	    	field_RSSI.setText(mRSSI);}};
    
	public class WapdroidServiceConnection implements ServiceConnection {
		public void onServiceConnected(ComponentName className, IBinder boundService) {
			mWapdroidService = IWapdroidService.Stub.asInterface((IBinder) boundService);
			try {
				mWapdroidService.setCallback(mWapdroidUI.asBinder());}
			catch (RemoteException e) {}}
		public void onServiceDisconnected(ComponentName className) {
			mWapdroidService = null;}}}