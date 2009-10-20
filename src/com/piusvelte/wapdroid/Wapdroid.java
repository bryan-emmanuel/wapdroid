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
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.telephony.CellLocation;
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
	public static final int LOCATE_ID = Menu.FIRST + 1;
	public static final int RESET_ID = Menu.FIRST + 2;
	public TextView field_CID;
	public TextView field_LAC;
	public TextView field_MNC;
	public TextView field_MCC;
	public TextView field_RSSI;
	private TextView field_wifiState;
	private CheckBox checkbox_wifiState;
	private CheckBox checkbox_wapdroidState;
	private static final String mPreferenceManageWifi = "manageWifi";
	private boolean mManageWifi;
	private static final String PREF_FILE_NAME = "wapdroid";
	private SharedPreferences mPreferences;
	private SharedPreferences.Editor mEditor;
		
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
    	init();}
	
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	boolean result = super.onCreateOptionsMenu(menu);
    	menu.add(0, MANAGE_ID, 0, R.string.menu_manageNetworks);
    	menu.add(0, LOCATE_ID, 0, R.string.menu_updateLocation);
    	menu.add(0, RESET_ID, 0, R.string.menu_resetPeregrine);
    	return result;}

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	switch (item.getItemId()) {
    	case MANAGE_ID:
    		manageNetworks();
    		return true;
    	case LOCATE_ID:
			CellLocation.requestLocationUpdate();
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
    	mDbHelper = new WapdroidDbAdapter(this);
		mDbHelper.open();
    	mDbHelper.upgradeDatabase();
    	mWifiHelper = new ManageWifi(this);
    	mLocationHelper = new ManageLocation(this);
    	checkbox_wifiState.setChecked(mWifiHelper.isEnabled());
    	checkbox_wifiState.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				mWifiHelper.setEnabled(isChecked);}});
    	checkbox_wapdroidState.setChecked(mManageWifi);
    	checkbox_wapdroidState.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				mManageWifi = isChecked;
				mEditor.putBoolean(mPreferenceManageWifi, mManageWifi);
				mEditor.commit();}});
		CellLocation.requestLocationUpdate();}
        
    public boolean hasCellAndConnection() {
    	return (mWifiHelper.isConnected() && mLocationHelper.hasCell());}
    
    public void recordCell(String mSSID) {
		mDbHelper.fetchCellOrCreate(mSSID, mLocationHelper.getCID(), mLocationHelper.getLAC(), mLocationHelper.getMNC(), mLocationHelper.getMCC(), mLocationHelper.getRSSI());}
    
    public void updateLocation(int mCID, int mLAC, String mMNC, String mMCC) {
		field_CID.setText((String) "" + mCID);
		field_LAC.setText((String) "" + mLAC);
		field_MNC.setText((String) "" + mMNC);
		field_MCC.setText((String) "" + mMCC);}
    
    public void updateRSSI(int mRSSI) {
		//phone state intent receiver: 0-31, for GSM, dBm = -113 + 2 * asu
		field_RSSI.setText((String) "" + (-113 + 2 * mRSSI) + "dBm");}

	public void updateWifiState(String mState) {
		field_wifiState.setText(mState);}
	
    public void manageWifi(int mCID, int mLAC, String mMNC, String mMCC, int mRSSI) {
		boolean mWifiEnabled = mWifiHelper.isEnabled();
		if (mLocationHelper.hasCell()) {
			String mSSID = mWifiHelper.getSSID();
			if (mSSID != null) {
				mDbHelper.fetchCellOrCreate(mSSID, mCID, mLAC, mMNC, mMCC, mRSSI);}
			else if (mManageWifi && (mDbHelper.inRange(mCID, mLAC, mMNC, mMCC, mRSSI) ^ mWifiEnabled)) {
				checkbox_wifiState.setChecked(mWifiEnabled ? false : true);}}
		else if (mManageWifi && mWifiEnabled && !mWifiHelper.isConnected()) {
			checkbox_wifiState.setChecked(false);}}}