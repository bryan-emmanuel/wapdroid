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

package com.piusvelte.wapdroidpro;

import static com.piusvelte.wapdroidpro.Wapdroid.UNKNOWN_RSSI;

import com.piusvelte.wapdroidpro.IWapdroidService;
import com.piusvelte.wapdroidpro.IWapdroidUI;
import com.piusvelte.wapdroidpro.R;
import com.piusvelte.wapdroidpro.Wapdroid.Cells;
import com.piusvelte.wapdroidpro.Wapdroid.Networks;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

public class WapdroidUI extends Activity implements ServiceConnection, DialogInterface.OnClickListener {
	public static final int MANAGE_ID = Menu.FIRST;
	public static final int SETTINGS_ID = Menu.FIRST + 1;
	public static final int WIFI_ID = Menu.FIRST + 2;
	public static final int ABOUT_ID = Menu.FIRST + 3;
	private TextView field_CID,
	field_wifiState, 
	field_wifiBSSID,
	field_signal,
	field_battery,
	field_LAC;
	private String mBssid = "",
	mCells = "",
	mSsid = "";
	private int mCid = 0;
	public IWapdroidService mIService;

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
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		boolean result = super.onCreateOptionsMenu(menu);
		menu.add(0, MANAGE_ID, 0, String.format(getString(R.string.manage), getString(R.string.network))).setIcon(android.R.drawable.ic_menu_manage);
		menu.add(0, SETTINGS_ID, 0, R.string.menu_settings).setIcon(android.R.drawable.ic_menu_preferences);
		menu.add(0, WIFI_ID, 0, R.string.label_WIFI).setIcon(android.R.drawable.ic_menu_manage);
		menu.add(0, ABOUT_ID, 0, R.string.label_about).setIcon(android.R.drawable.ic_menu_more);
		return result;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case MANAGE_ID:
			startActivity((new Intent(this, ManageData.class)).putExtra(Networks.SSID, mSsid).putExtra(Networks.BSSID, mBssid).putExtra(Cells.CID, mCid).putExtra(WapdroidProvider.TABLE_CELLS, mCells));
			return true;
		case SETTINGS_ID:
			startActivity(new Intent(this, Settings.class));
			return true;
		case WIFI_ID:
			SharedPreferences sp = (SharedPreferences) getSharedPreferences(getString(R.string.key_preferences), WapdroidService.MODE_PRIVATE);
			SharedPreferences.Editor spe = sp.edit();
			spe.putBoolean(getString(R.string.key_manual_override), true);
			spe.commit();
			startActivity(new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS));
			return true;
		case ABOUT_ID:
			Dialog dialog = new Dialog(this);
			dialog.setContentView(R.layout.about);
			dialog.setTitle(R.string.label_about);
			dialog.show();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onPause() {
		super.onPause();
		if (mIService != null) {
			try {
				mIService.setCallback(null);
			} catch (RemoteException e) {}
		}
		unbindService(this);
	}

	@Override
	public void onResume() {
		super.onResume();
		SharedPreferences sp = (SharedPreferences) getSharedPreferences(getString(R.string.key_preferences), WapdroidService.MODE_PRIVATE);
		SharedPreferences.Editor spe = sp.edit();
		spe.putBoolean(getString(R.string.key_manual_override), false);
		spe.commit();
		if (sp.getBoolean(getString(R.string.key_manageWifi), false)) startService(new Intent(this, WapdroidService.class));
		else {
			AlertDialog.Builder dialog = new AlertDialog.Builder(this);
			dialog.setMessage(R.string.service_info);
			dialog.setNegativeButton(android.R.string.ok, this);
			dialog.show();			
		}
		bindService(new Intent(this, WapdroidService.class), this, BIND_AUTO_CREATE);
	}

	private IWapdroidUI.Stub mWapdroidUI = new IWapdroidUI.Stub() {
		public void setCellInfo(int cid, int lac) throws RemoteException {
			mCid = cid;
			field_CID.setText(Integer.toString(cid));
			field_LAC.setText(Integer.toString(lac));
		}

		public void setWifiInfo(int state, String ssid, String bssid)
		throws RemoteException {
			mSsid = ssid;
			mBssid = bssid;
			if (state == WifiManager.WIFI_STATE_ENABLED) {
				if (ssid != null) {
					field_wifiState.setText(ssid);
					field_wifiBSSID.setText(bssid);
				} else {
					field_wifiState.setText(getString(R.string.label_enabled));
					field_wifiBSSID.setText("");
				}
			} else if (state != WifiManager.WIFI_STATE_UNKNOWN) {
				field_wifiState.setText((state == WifiManager.WIFI_STATE_ENABLING ?
						getString(R.string.label_enabling)
						: (state == WifiManager.WIFI_STATE_DISABLING ?
								getString(R.string.label_disabling)
								: getString(R.string.label_disabled))));
				field_wifiBSSID.setText("");
			}
		}

		public void setSignalStrength(int rssi) throws RemoteException {
			field_signal.setText((rssi != UNKNOWN_RSSI ? (Integer.toString(rssi) + getString(R.string.dbm)) : getString(R.string.scanning)));
		}

		public void setBattery(int batteryPercentage) throws RemoteException {
			field_battery.setText(Integer.toString(batteryPercentage) + "%");
		}

		public void setCells(String cells) throws RemoteException {
			mCells = cells;
		}

		public void setOperator(String operator)
		throws RemoteException {}
	};

	@Override
	public void onServiceConnected(ComponentName name, IBinder service) {
		mIService = IWapdroidService.Stub.asInterface((IBinder) service);
		if (mWapdroidUI != null) {
			try {
				mIService.setCallback(mWapdroidUI.asBinder());
			} catch (RemoteException e) {}
		}
	}

	@Override
	public void onServiceDisconnected(ComponentName name) {
		mIService = null;
	}
	
	@Override
	public void onClick(DialogInterface dialog, int which) {
		dialog.cancel();
	}
}