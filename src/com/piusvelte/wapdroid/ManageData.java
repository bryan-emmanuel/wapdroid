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

import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.os.RemoteException;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.AdapterView.AdapterContextMenuInfo;

public class ManageData extends ListActivity {
	private WapdroidDbAdapter mDbHelper;
	private int mNetwork = 0, mCid;
	private static final int REFRESH_ID = Menu.FIRST;
	private static final int DELETE_ID = Menu.FIRST + 1;
	private static final int GEO_ID = Menu.FIRST + 2;
	private static final int FILTER_ID = Menu.FIRST + 3;
	private int mFilter = WapdroidDbAdapter.FILTER_ALL;
	private AlertDialog mAlertDialog;
	private String mCells = "", mOperator = "", mBssid = "";
	private ServiceConn mServiceConn;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Bundle extras = getIntent().getExtras();
		if (extras != null) {
			mNetwork = extras.getInt(WapdroidDbAdapter.TABLE_ID);
			mCid = extras.getInt(WapdroidDbAdapter.CELLS_CID);
			mBssid = extras.getString(WapdroidDbAdapter.NETWORKS_BSSID);
			mCells = extras.getString(WapdroidDbAdapter.TABLE_CELLS);
		}
		setContentView(mNetwork == 0 ? R.layout.networks_list : R.layout.cells_list);
		registerForContextMenu(getListView());
		mDbHelper = new WapdroidDbAdapter(this);
	}

	@Override
	protected void onResume() {
		super.onResume();
		mDbHelper.open();
		SharedPreferences prefs = getSharedPreferences(getString(R.string.key_preferences), MODE_PRIVATE);
		if (prefs.getBoolean(getString(R.string.key_manageWifi), true)) startService(new Intent(this, WapdroidService.class));
		mServiceConn = new ServiceConn(mWapdroidUI);
		bindService(new Intent(this, WapdroidService.class), mServiceConn, BIND_AUTO_CREATE);
		try {
			listData();
		}
		catch (RemoteException e) {
			e.printStackTrace();
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (mServiceConn != null) {
			if (mServiceConn.mIService != null) {
				try {
					mServiceConn.mIService.setCallback(null);
				}
				catch (RemoteException e) {}
			}
			unbindService(mServiceConn);
			mServiceConn = null;
		}
		mDbHelper.close();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		boolean result = super.onCreateOptionsMenu(menu);
		menu.add(0, REFRESH_ID, 0, R.string.menu_refreshNetworks).setIcon(android.R.drawable.ic_menu_rotate);
		menu.add(0, FILTER_ID, 0, R.string.menu_filter).setIcon(android.R.drawable.ic_menu_agenda);
		return result;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case REFRESH_ID:
			try {
				listData();
			}
			catch (RemoteException e1) {
				e1.printStackTrace();
			}
			return true;
		case FILTER_ID:
			/* filter options */
			String[] options = getResources().getStringArray(R.array.filter_values);
			int which = 0;
			for (int o = 0; o < options.length; o++) {
				if (mFilter == Integer.parseInt(options[o])) {
					which = o;
					break;
				}
			}
			Builder b = new AlertDialog.Builder(this);
			b.setSingleChoiceItems(
					R.array.filter_entries,
					which,
					new DialogInterface.OnClickListener() {
						//@Override
						public void onClick(DialogInterface dialog, int which) {
							mAlertDialog.dismiss();
							mFilter = Integer.parseInt(getResources().getStringArray(R.array.filter_values)[which]);
							try {
								listData();
							}
							catch (RemoteException e) {
								e.printStackTrace();
							}
						}});
			mAlertDialog = b.create();
			mAlertDialog.show();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, view, menuInfo);
		menu.add(0, GEO_ID, 0, R.string.map);
		menu.add(0, DELETE_ID, 0, mNetwork == 0 ? R.string.menu_deleteNetwork : R.string.menu_deleteCell);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info;
		switch(item.getItemId()) {
		case GEO_ID:
			// open gmaps
			info = (AdapterContextMenuInfo) item.getMenuInfo();
			Intent intent = new Intent(this, MapData.class);
			intent.putExtra(WapdroidDbAdapter.PAIRS_NETWORK, (int) (mNetwork == 0 ? info.id : mNetwork));
			if (mNetwork != 0) intent.putExtra(WapdroidDbAdapter.PAIRS_CELL, (int) info.id);
			intent.putExtra(MapData.OPERATOR, mOperator);
			startActivity(intent);
			return true;
		case DELETE_ID:
			info = (AdapterContextMenuInfo) item.getMenuInfo();
			if (mNetwork == 0) mDbHelper.deleteNetwork((int) info.id);
			else mDbHelper.deletePair(mNetwork, (int) info.id);
			try {
				listData();
			}
			catch (RemoteException e) {
				e.printStackTrace();
			}
			return true;
		}
		return super.onContextItemSelected(item);
	}

	@Override
	protected void onListItemClick(ListView list, View view, int position, long id) {
		super.onListItemClick(list, view, position, id);
		if (mNetwork == 0) {
			Intent intent = new Intent(this, ManageData.class);
			intent.putExtra(WapdroidDbAdapter.TABLE_ID, (int) id);
			intent.putExtra(WapdroidDbAdapter.TABLE_CELLS, mCells);
			startActivity(intent);
		}
	}

	public void listData() throws RemoteException {
		// filter results
		Cursor c = mNetwork == 0 ? mDbHelper.fetchNetworks(mFilter, mBssid, mCells) : mDbHelper.fetchPairsByNetworkFilter(mFilter, mNetwork, mCid, mCells);
		startManagingCursor(c);
		SimpleCursorAdapter data = mNetwork == 0 ?
				new SimpleCursorAdapter(this,
						R.layout.network_row,
						c,
						new String[] {WapdroidDbAdapter.NETWORKS_SSID, WapdroidDbAdapter.NETWORKS_BSSID, WapdroidDbAdapter.STATUS},
						new int[] {R.id.network_row_SSID, R.id.network_row_BSSID, R.id.network_row_status})
		: new SimpleCursorAdapter(this,
				R.layout.cell_row,
				c,
				new String[] {WapdroidDbAdapter.CELLS_CID, WapdroidDbAdapter.LOCATIONS_LAC, WapdroidDbAdapter.PAIRS_RSSI_MIN, WapdroidDbAdapter.STATUS},
				new int[] {R.id.cell_row_CID, R.id.cell_row_LAC, R.id.cell_row_range, R.id.cell_row_status});
				setListAdapter(data);
	}

	private IWapdroidUI.Stub mWapdroidUI = new IWapdroidUI.Stub() {
		public void setCellInfo(int cid, int lac) throws RemoteException {
			mCid = cid;
		}

		public void setWifiInfo(int state, String ssid, String bssid) throws RemoteException {
			mBssid = bssid;
		}

		public void setSignalStrength(int rssi) throws RemoteException {}

		public void setOperator(String operator) throws RemoteException {
			mOperator = operator;
		}

		public void setBattery(int batteryPercentage) throws RemoteException {}

		public void setCells(String cells) throws RemoteException {
			mCells = cells;
		}

		public void inRange(boolean inrange) throws RemoteException {}
	};
}