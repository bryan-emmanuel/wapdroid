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

import java.util.List;

import com.piusvelte.wapdroid.R;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.telephony.NeighboringCellInfo;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.CompoundButton.OnCheckedChangeListener;

public class Wapdroid extends Activity {
	public WapdroidDbAdapter mDbHelper;
	public ManageWifi mWifiHelper;
	public ManageLocation mLocationHelper;
	public static final int MANAGE_ID = Menu.FIRST;
	public static final int RESET_ID = Menu.FIRST + 1;
	private TextView field_CID, field_LAC, field_MNC, field_MCC, field_RSSI, field_wifiState;
	private CheckBox checkbox_wifiState, checkbox_wapdroidState;
	private static final String mPreferenceManageWifi = "manageWifi";
	private boolean mManageWifi;
	private static final String PREF_FILE_NAME = "wapdroid";
	private SharedPreferences mPreferences;
	private SharedPreferences.Editor mEditor;
	private TelephonyManager mTeleManager;
	private GsmCellLocation mGsmCellLocation;
	private int mCID = -1, mLAC = -1, mRSSI = -1;
	private String mMNC = null, mMCC = null;
	private List<NeighboringCellInfo> mNeighboringCells;
	private WifiManager mWifiManager;
	private WifiInfo mWifiInfo;
	private int mWifiState, mWifiDisabling, mWifiEnabling, mWifiEnabled, mWifiUnknown;
	private String mSSID = null;
	private static final String CONNECTEDTO = "connected to ";
	private static final String ENABLED = "enabled";
	private static final String ENABLING = "enabling";
	private static final String DISABLED = "disabled";
	private static final String DISABLING = "disabling";
		
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
    	init();}
	
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	boolean result = super.onCreateOptionsMenu(menu);
    	menu.add(0, MANAGE_ID, 0, R.string.menu_manageNetworks);
    	menu.add(0, RESET_ID, 0, R.string.menu_resetPeregrine);
    	return result;}

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	switch (item.getItemId()) {
    	case MANAGE_ID:
    		manageNetworks();
    		return true;
    	case RESET_ID:
    		mDbHelper.resetDatabase();
    		return true;}
        return super.onOptionsItemSelected(item);}
    
    private void manageNetworks() {
    	Intent intent = new Intent(this, ManageNetworks.class);
    	startActivity(intent);}
    
    private void init() {
    	field_CID = (TextView) findViewById(R.id.field_CID);
    	field_LAC = (TextView) findViewById(R.id.field_LAC);
    	field_MNC = (TextView) findViewById(R.id.field_MNC);
    	field_MCC = (TextView) findViewById(R.id.field_MCC);
    	field_RSSI = (TextView) findViewById(R.id.field_RSSI);
    	field_wifiState = (TextView) findViewById(R.id.field_wifiState);
    	checkbox_wifiState = (CheckBox) findViewById(R.id.checkbox_wifiState);
    	checkbox_wapdroidState = (CheckBox) findViewById(R.id.checkbox_wapdroidState);
    	mPreferences = getSharedPreferences(PREF_FILE_NAME, MODE_PRIVATE);
    	mEditor = mPreferences.edit();
    	mManageWifi = mPreferences.getBoolean(mPreferenceManageWifi, true);
    	checkbox_wapdroidState.setChecked(mManageWifi);
    	checkbox_wapdroidState.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				mManageWifi = isChecked;
				mEditor.putBoolean(mPreferenceManageWifi, mManageWifi);
				mEditor.commit();}});
    	mWifiDisabling = WifiManager.WIFI_STATE_DISABLING;
    	mWifiEnabling = WifiManager.WIFI_STATE_ENABLING;
    	mWifiEnabled = WifiManager.WIFI_STATE_ENABLED;
    	mWifiUnknown = WifiManager.WIFI_STATE_UNKNOWN;
    	mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
    	mWifiState = mWifiManager.getWifiState();
		mWifiInfo = mWifiManager.getConnectionInfo();
		mSSID = mWifiInfo.getSSID();
		field_wifiState.setText(mSSID != null ?
				CONNECTEDTO + mSSID
				: (mWifiState == mWifiEnabled ?
						ENABLED
						: (mWifiState == mWifiEnabling ?
								ENABLING
								: (mWifiState == mWifiDisabling ?
										DISABLING
										: DISABLED))));
    	checkbox_wifiState.setChecked(mWifiState == mWifiEnabled);
    	checkbox_wifiState.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				mWifiManager.setWifiEnabled(isChecked);}});
    	mDbHelper = new WapdroidDbAdapter(this);
		mDbHelper.open();
    	mWifiHelper = new ManageWifi(this);
    	registerReceiver(mWifiHelper, new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION));
    	registerReceiver(mWifiHelper, new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION));
    	mLocationHelper = new ManageLocation(this);
		mTeleManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		mMNC = mTeleManager.getNetworkOperatorName();
		mMCC = mTeleManager.getNetworkCountryIso();
		mGsmCellLocation = (GsmCellLocation) mTeleManager.getCellLocation();
		mCID = mGsmCellLocation.getCid();
		mLAC = mGsmCellLocation.getLac();
    	mNeighboringCells = mTeleManager.getNeighboringCellInfo();
		field_CID.setText((String) "" + mCID);
		field_LAC.setText((String) "" + mLAC);
		field_MNC.setText((String) "" + mMNC);
		field_MCC.setText((String) "" + mMCC);
		field_RSSI.setText((String) "" + mRSSI + "dBm");
    	mTeleManager.listen(mLocationHelper, PhoneStateListener.LISTEN_CELL_LOCATION ^ PhoneStateListener.LISTEN_SIGNAL_STRENGTH);}
    
    public boolean hasCell() {
    	return (mCID > 0) && (mLAC > 0) && (mMNC != null) && (mMNC != "") && (mMCC != null) && (mMCC != "") && (mRSSI > 0);}
        
    public void locationChanged(int mCell, int mLocation) {
    	mCID = mCell;
    	mLAC = mLocation;
		mMNC = mTeleManager.getNetworkOperatorName();
		mMCC = mTeleManager.getNetworkCountryIso();
		field_CID.setText((String) "" + mCID);
		field_LAC.setText((String) "" + mLAC);
		field_MNC.setText((String) "" + mMNC);
		field_MCC.setText((String) "" + mMCC);
    	mNeighboringCells = mTeleManager.getNeighboringCellInfo();
		manageWifi();}
    
    public void signalChanged(int mASU) {
		//phone state intent receiver: 0-31, for GSM, dBm = -113 + 2 * asu
    	mRSSI = mASU;
		field_RSSI.setText((String) "" + (-113 + 2 * mRSSI) + "dBm");
    	mNeighboringCells = mTeleManager.getNeighboringCellInfo();
		manageWifi();}
    
    public void updateRange() {
		mDbHelper.updateCellRange(mSSID, mCID, mLAC, mMNC, mMCC, mRSSI);
		int mNeighborCID;
		int mNeighborRSSI;
		for (NeighboringCellInfo n : mNeighboringCells) {
			mNeighborCID = n.getCid();
			mNeighborRSSI = convertRSSIToASU(n.getRssi());
			if ((mCID > 0) && (mRSSI > 0)) {
				mDbHelper.updateNeighborRange(mSSID, mNeighborCID, mNeighborRSSI);}}}
    
    public void networkChanged(NetworkInfo mNetworkInfo) {
    	if (mNetworkInfo.isConnected()) {
    		mWifiInfo = mWifiManager.getConnectionInfo();
    		mSSID = mWifiInfo.getSSID();}
    	else {
    		mSSID = null;}
    	wifiChanged();}
    
	public void stateChanged(int mState) {
    	if (mState != mWifiUnknown) {
    		mWifiState = mState;}
		wifiChanged();}
	
	public void wifiChanged() {
		if (mWifiState == mWifiEnabled) {
			if (mSSID != null) {
				field_wifiState.setText(CONNECTEDTO + mSSID);
				if (hasCell()) {
			    	updateRange();}}
			else {
				field_wifiState.setText(ENABLED);}}
		else {
			field_wifiState.setText((mWifiState == mWifiEnabling ?
					ENABLING
					: (mWifiState == mWifiDisabling ?
							DISABLING
							: DISABLED)));}}

    public int convertRSSIToASU(int mSignal) {
		/*
		 * ASU is reported by the signal strength with a range of 0-31
		 * RSSI = -113 + 2 * ASU
		 * NeighboringCellInfo reports a positive RSSI
		 * and needs to be converted to ASU
		 * ASU = ((-1 * RSSI) + 113) / 2
		 */
    	return mSignal > 31 ? Math.round(((-1 * mSignal) + 113) / 2) : mSignal;}
    
    public void manageWifi() {
		if (hasCell()) {
			if ((mWifiState == mWifiEnabled) && (mSSID != null)) {
				updateRange();}
			else if (mManageWifi) {
				boolean mInRange = false;
				// coarse range check
				Cursor c = mDbHelper.cellsInRange(mCID, mRSSI);
				if (c.getCount() > 0) {
					mInRange = true;
					boolean mCheckNeighbors = true;
					String mNetworkColIdx = WapdroidDbAdapter.CELLS_NETWORK;
					c.moveToFirst();
					while (mCheckNeighbors && !c.isAfterLast()) {
						mCheckNeighbors = mDbHelper.hasNeighbors(c.getInt(c.getColumnIndex(mNetworkColIdx)));
						c.moveToNext();}
					// if there are neighbors for all networks in range, then perform fine range checking
					if (mCheckNeighbors) {
						int mNeighborCID, mNeighborRSSI;
						for (NeighboringCellInfo n : mNeighboringCells) {
							mNeighborCID = n.getCid();
							mNeighborRSSI = convertRSSIToASU(n.getRssi());
							if (mInRange && (mNeighborCID > 0) && (mNeighborRSSI > 0)) {
								mInRange = mDbHelper.neighborInRange(mNeighborCID, mNeighborRSSI);}}}}
				c.close();
				if (mInRange ^ (mWifiState == mWifiEnabled)) {
					checkbox_wifiState.setChecked((mWifiState != mWifiEnabled));}}}
		else if (mManageWifi && (mWifiState == mWifiEnabled) && (mSSID == null)) {
			checkbox_wifiState.setChecked(false);}}}