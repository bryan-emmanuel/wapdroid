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
import android.graphics.Color;
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
	private TextView field_currentSSID;
	private Button button_wifiState;
	private Button button_cellLocation;
	private TelephonyManager teleManager;
	private CellStateListener cellStateListener;
	private GsmCellLocation gsmCellLocation;
	private static final String TURN = "turn";
	private static final String TURNING = "turning";
	private static final String WIFI = " wifi ";
	private static final String ON = "on";
	private static final String OFF = "off";
		
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
    	field_currentCID = (TextView) findViewById(R.id.field_currentCID);
    	field_currentLAC = (TextView) findViewById(R.id.field_currentLAC);
    	field_currentSSID = (TextView) findViewById(R.id.field_currentSSID);
    	button_wifiState = (Button) findViewById(R.id.button_wifiState);
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
				button_wifiState.setText(TURNING + WIFI + (wifiManager.isWifiEnabled() ? OFF : ON));
				wifiManager.setWifiEnabled(wifiManager.isWifiEnabled() ? false : true);}});
    	
    	button_cellLocation.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				CellLocation.requestLocationUpdate();}});

    	onWifiChanged();}
	
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
    
    private void onWifiChanged() {
   		field_currentSSID.setText(wifiManager.getConnectionInfo().getSSID());
		button_wifiState.setText(TURN + WIFI + (wifiManager.isWifiEnabled() ? OFF : ON));
		button_wifiState.setBackgroundColor(wifiManager.isWifiEnabled() ? Color.GREEN : Color.RED);
    	CellLocation.requestLocationUpdate();}

    public class WifiChangedReceiver extends BroadcastReceiver {
    	@Override
    	public void onReceive(Context context, Intent intent) {
  			onWifiChanged();}}
    
    public class CellStateListener extends PhoneStateListener {
    	@Override
    	public void onCellLocationChanged(CellLocation location) {
    		super.onCellLocationChanged(location);
        	gsmCellLocation = (GsmCellLocation) teleManager.getCellLocation();
    		field_currentCID.setText((String) "" + gsmCellLocation.getCid());
    		field_currentLAC.setText((String) "" + gsmCellLocation.getLac());
    		if ((gsmCellLocation.getCid() > 0) && (gsmCellLocation.getLac() > 0)) {
    			if (wifiManager.getConnectionInfo().getSSID() != null) {
    				mDbHelper.pairCell(wifiManager.getConnectionInfo().getSSID(), gsmCellLocation.getCid(), gsmCellLocation.getLac());}
    			else if (mDbHelper.inRange(gsmCellLocation.getCid(), gsmCellLocation.getLac()) ^ wifiManager.isWifiEnabled()) {
    				wifiManager.setWifiEnabled(wifiManager.isWifiEnabled() ? false : true);}}
    		else if (wifiManager.isWifiEnabled() && (wifiManager.getConnectionInfo().getSSID() == null)) {
    			wifiManager.setWifiEnabled(false);}}}}