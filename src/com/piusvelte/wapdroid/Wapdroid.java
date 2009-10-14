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
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class Wapdroid extends Activity {
	private WapdroidDbAdapter mDbHelper;
	private WifiManager wifiManager;
	private WifiChangedReceiver wifiChangedReceiver;
	public static final int MANAGE_ID = Menu.FIRST;
	public static final int RESET_ID = Menu.FIRST + 1;
	private TextView field_currentCID;
	private TextView field_currentLAC;
	private TextView field_currentMNC;
	private TextView field_currentMCC;
	private TextView field_currentSSID;
	private Button button_wifiState;
	private Button button_wapdroidState;
	private Button button_cellLocation;
	private TelephonyManager teleManager;
	private CellStateListener cellStateListener;
	private GsmCellLocation gsmCellLocation;
	private static final String ENABLE = "enable";
	private static final String ENABLING = "enabling";
	private static final String DISABLE = "disable";
	private static final String DISABLING = "disabling";
	private static final String WAPDROID = " Wapdroid";
	private static final String WIFI = " WiFi";
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
    	field_currentCID = (TextView) findViewById(R.id.field_currentCID);
    	field_currentLAC = (TextView) findViewById(R.id.field_currentLAC);
    	field_currentMNC = (TextView) findViewById(R.id.field_currentMNC);
    	field_currentMCC = (TextView) findViewById(R.id.field_currentMCC);
    	field_currentSSID = (TextView) findViewById(R.id.field_currentSSID);
    	button_wifiState = (Button) findViewById(R.id.button_wifiState);
    	button_wapdroidState = (Button) findViewById(R.id.button_wapdroidState);
    	button_cellLocation = (Button) findViewById(R.id.button_cellLocation);
    	mDbHelper = new WapdroidDbAdapter(this);
		mDbHelper.open();
    	wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
    	wifiChangedReceiver = new WifiChangedReceiver();
    	registerReceiver(wifiChangedReceiver, new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION));
		teleManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
    	cellStateListener = new CellStateListener();
    	teleManager.listen(cellStateListener, PhoneStateListener.LISTEN_CELL_LOCATION);
     	button_wifiState.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				wifiManager.setWifiEnabled((wifiState == wifiEnabled) ? false : true);}});
     	button_wapdroidState.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				wapdroidEnabled = wapdroidEnabled ? false : true;
				wapdroidStatusChanged();}});
    	button_cellLocation.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				CellLocation.requestLocationUpdate();}});
    	wifiEnabled = WifiManager.WIFI_STATE_ENABLED;
    	wifiEnabling = WifiManager.WIFI_STATE_ENABLING;
    	wifiDisabling = WifiManager.WIFI_STATE_DISABLING;
    	wapdroidStatusChanged();
    	initCell();
    	initWifi();
    	if (mDbHelper.inRange(CID, LAC, MNC, MCC) ^ (wifiState == wifiEnabled)) {
			wifiManager.setWifiEnabled((wifiState == wifiEnabled) ? false : true);}}
    
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
   		field_currentSSID.setText(SSID);
		button_wifiState.setText(
				(wifiState == wifiEnabled ? DISABLE : 
					(wifiState == wifiEnabling ? ENABLING :
						(wifiState == wifiDisabling ? DISABLING : ENABLE))) + WIFI);
		button_wifiState.setBackgroundResource((wifiState == wifiEnabled) ? R.drawable.buttongreen : R.drawable.buttonblue);}
    
    private void wapdroidStatusChanged() {
		button_wapdroidState.setText((wapdroidEnabled ? DISABLE : ENABLE) + WAPDROID);
		button_wapdroidState.setBackgroundResource(wapdroidEnabled ? R.drawable.buttongreen : R.drawable.buttonblue);}
    
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
    				wifiManager.setWifiEnabled((wifiState == wifiEnabled) ? false : true);}}
    		else if (wapdroidEnabled && (wifiState == wifiEnabled) && (SSID == null)) {
    			wifiManager.setWifiEnabled(false);}}}}