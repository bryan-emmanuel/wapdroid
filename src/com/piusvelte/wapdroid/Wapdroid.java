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
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.telephony.CellLocation;
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
	private WapdroidDbAdapter mDbHelper;
	private WifiManager wifiManager;
	public static final int MANAGE_ID = Menu.FIRST;
	public static final int LOCATE_ID = Menu.FIRST + 1;
	public static final int RESET_ID = Menu.FIRST + 2;
	private TextView field_CID;
	private TextView field_LAC;
	private TextView field_MNC;
	private TextView field_MCC;
	private TextView field_RSSI;
	private TextView field_wifiState;
	private CheckBox checkbox_wifiState;
	private CheckBox checkbox_wapdroidState;
	private TelephonyManager teleManager;
	private GsmCellLocation gsmCellLocation;
	private static final String CONNECTEDTO = "connected to ";
	private static final String ENABLED = "enabled";
	private static final String ENABLING = "enabling";
	private static final String DISABLED = "disabled";
	private static final String DISABLING = "disabling";
	private boolean wapdroidEnabled = true;
	private int wifiState;
	private int wifiEnabled;
	private int wifiEnabling;
	private int wifiDisabling;
	private String mSSID = null;
	private int mCID = -1;
	private int mLAC = -1;
	private String mMNC = null;
	private String mMCC = null;
	private int mRSSI = -1;
		
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
    	wifiEnabled = WifiManager.WIFI_STATE_ENABLED;
    	wifiEnabling = WifiManager.WIFI_STATE_ENABLING;
    	wifiDisabling = WifiManager.WIFI_STATE_DISABLING;
    	mDbHelper = new WapdroidDbAdapter(this);
		mDbHelper.open();
    	mDbHelper.upgradeAddRSSI();
    	wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
    	registerReceiver(new WifiChangedReceiver(), new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION));
		teleManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
    	teleManager.listen(new PhoneStateChangedListener(), PhoneStateListener.LISTEN_CELL_LOCATION ^ PhoneStateListener.LISTEN_SIGNAL_STRENGTH);
    	checkbox_wifiState.setChecked(wifiManager.getWifiState() == wifiEnabled);
    	checkbox_wifiState.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				wifiManager.setWifiEnabled(isChecked);}});
    	checkbox_wapdroidState.setChecked(wapdroidEnabled);
    	checkbox_wapdroidState.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				wapdroidEnabled = isChecked;}});
    	initWifi();
		CellLocation.requestLocationUpdate();}
        
    private void initWifi() {
    	wifiState = wifiManager.getWifiState();
    	if (wifiState == wifiEnabled) {
    		mSSID = wifiManager.getConnectionInfo().getSSID();}
    	else {
    		mSSID = null;}
		field_wifiState.setText(
				(mSSID != null ? CONNECTEDTO + mSSID :
					(wifiState == wifiEnabled ? ENABLED :
						(wifiState == wifiEnabling ? ENABLING :
							(wifiState == wifiDisabling ? DISABLING : DISABLED)))));}
    
    public boolean hasCell() {
    	return ((mCID > 0) && (mLAC > 0) && (mMNC != null) && (mMCC != null));}
    
    public boolean hasPair() {
    	return ((mSSID != null) && hasCell());}
    
    public void manageWifi() {
		if (hasCell()) {
			if (mSSID != null) {
				mDbHelper.pairCell(mSSID, mCID, mLAC, mMNC, mMCC, mRSSI);}
			else if (wapdroidEnabled && (mDbHelper.inRange(mCID, mLAC, mMNC, mMCC, mRSSI) ^ (wifiState == wifiEnabled))) {
				checkbox_wifiState.setChecked((wifiState == wifiEnabled) ? false : true);}}
		else if (wapdroidEnabled && (wifiState == wifiEnabled) && (mSSID == null)) {
			checkbox_wifiState.setChecked(false);}}
    
    public class WifiChangedReceiver extends BroadcastReceiver {
    	@Override
    	public void onReceive(Context context, Intent intent) {
        	initWifi();
    		if (hasPair()) {
    			mDbHelper.pairCell(mSSID, mCID, mLAC, mMNC, mMCC, mRSSI);}}}
    
    public class PhoneStateChangedListener extends PhoneStateListener {
    	@Override
    	public void onSignalStrengthChanged(int asu) {
    		super.onSignalStrengthChanged(asu);
    		mRSSI = asu;
    		//phonestateintentreciever: 0-31, for GSM, dBm=-113+2*asu
    		field_RSSI.setText((String) "" + (-113 + 2 * mRSSI) + "dBm");
    		manageWifi();}
    	@Override
    	public void onCellLocationChanged(CellLocation location) {
    		super.onCellLocationChanged(location);
        	gsmCellLocation = (GsmCellLocation) teleManager.getCellLocation();
        	mCID = gsmCellLocation.getCid();
        	mLAC = gsmCellLocation.getLac();
        	mMNC = teleManager.getNetworkOperatorName();
        	mMCC = teleManager.getNetworkCountryIso();
    		field_CID.setText((String) "" + mCID);
    		field_LAC.setText((String) "" + mLAC);
    		field_MNC.setText((String) "" + mMNC);
    		field_MCC.setText((String) "" + mMCC);
    		manageWifi();}}}