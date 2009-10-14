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
	private WifiChangedReceiver wifiChangedReceiver;
	public static final int MANAGE_ID = Menu.FIRST;
	public static final int LOCATE_ID = Menu.FIRST + 1;
	public static final int RESET_ID = Menu.FIRST + 2;
	private TextView field_currentCID;
	private TextView field_currentLAC;
	private TextView field_currentMNC;
	private TextView field_currentMCC;
	private TextView field_wifiState;
	private CheckBox checkbox_wifiState;
	private CheckBox checkbox_wapdroidState;
	private TelephonyManager teleManager;
	private CellStateListener cellStateListener;
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
	private String SSID = "";
	private int CID = -1;
	private int LAC = -1;
	private String MNC = "";
	private String MCC = "";
		
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
    	field_currentCID = (TextView) findViewById(R.id.field_currentCID);
    	field_currentLAC = (TextView) findViewById(R.id.field_currentLAC);
    	field_currentMNC = (TextView) findViewById(R.id.field_currentMNC);
    	field_currentMCC = (TextView) findViewById(R.id.field_currentMCC);
    	field_wifiState = (TextView) findViewById(R.id.field_wifiState);
    	checkbox_wifiState = (CheckBox) findViewById(R.id.checkbox_wifiState);
    	checkbox_wapdroidState = (CheckBox) findViewById(R.id.checkbox_wapdroidState);
    	wifiEnabled = WifiManager.WIFI_STATE_ENABLED;
    	wifiEnabling = WifiManager.WIFI_STATE_ENABLING;
    	wifiDisabling = WifiManager.WIFI_STATE_DISABLING;
    	mDbHelper = new WapdroidDbAdapter(this);
		mDbHelper.open();
    	wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
    	wifiChangedReceiver = new WifiChangedReceiver();
    	registerReceiver(wifiChangedReceiver, new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION));
		teleManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
    	cellStateListener = new CellStateListener();
    	teleManager.listen(cellStateListener, PhoneStateListener.LISTEN_CELL_LOCATION);
    	checkbox_wifiState.setChecked(wifiManager.getWifiState() == wifiEnabled);
    	checkbox_wifiState.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				wifiManager.setWifiEnabled(isChecked);}});
    	checkbox_wapdroidState.setChecked(wapdroidEnabled);
    	checkbox_wapdroidState.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				wapdroidEnabled = isChecked;}});
    	initCell();
    	initWifi();
    	if (mDbHelper.inRange(CID, LAC, MNC, MCC) ^ (wifiState == wifiEnabled)) {
    		checkbox_wifiState.setChecked((wifiState == wifiEnabled) ? false : true);}}
    
    private void initCell() {
    	gsmCellLocation = (GsmCellLocation) teleManager.getCellLocation();
    	CID = gsmCellLocation.getCid();
    	LAC = gsmCellLocation.getLac();
    	MNC = teleManager.getNetworkOperatorName();
    	MCC = teleManager.getNetworkCountryIso();
		field_currentCID.setText((String) "" + CID);
		field_currentLAC.setText((String) "" + LAC);
		field_currentMNC.setText((String) "" + MNC);
		field_currentMCC.setText((String) "" + MCC);}
    
    private void initWifi() {
    	wifiState = wifiManager.getWifiState();
    	if (wifiState == wifiEnabled) {
    		SSID = wifiManager.getConnectionInfo().getSSID();}
    	else {
    		SSID = null;}
		field_wifiState.setText(
				(SSID != null ? CONNECTEDTO + SSID :
					(wifiState == wifiEnabled ? ENABLED :
						(wifiState == wifiEnabling ? ENABLING :
							(wifiState == wifiDisabling ? DISABLING : DISABLED)))));}
        
    public class WifiChangedReceiver extends BroadcastReceiver {
    	@Override
    	public void onReceive(Context context, Intent intent) {
        	initWifi();
    		if ((SSID != null) && (CID > 0) && (LAC > 0) && (MNC != null) && (MCC != null)) {
    			mDbHelper.pairCell(SSID, CID, LAC, MNC, MCC);}}}
    
    public class CellStateListener extends PhoneStateListener {
    	@Override
    	public void onCellLocationChanged(CellLocation location) {
    		super.onCellLocationChanged(location);
    		initCell();
    		if ((CID > 0) && (LAC > 0)) {
    			if (SSID != null) {
    				mDbHelper.pairCell(SSID, CID, LAC, MNC, MCC);}
    			else if (wapdroidEnabled && (mDbHelper.inRange(CID, LAC, MNC, MCC) ^ (wifiState == wifiEnabled))) {
    				checkbox_wifiState.setChecked((wifiState == wifiEnabled) ? false : true);}}
    		else if (wapdroidEnabled && (wifiState == wifiEnabled) && (SSID == null)) {
    			checkbox_wifiState.setChecked(false);}}}}