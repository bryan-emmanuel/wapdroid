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

import static com.piusvelte.wapdroid.WapdroidService.CELLS_CID;
import static com.piusvelte.wapdroid.WapdroidService.TABLE_PAIRS;
import static com.piusvelte.wapdroid.WapdroidService.TABLE_NETWORKS;
import static com.piusvelte.wapdroid.WapdroidService.TABLE_CELLS;
import static com.piusvelte.wapdroid.WapdroidService.NETWORKS_SSID;
import static com.piusvelte.wapdroid.WapdroidService.NETWORKS_BSSID;
import static com.piusvelte.wapdroid.WapdroidService.LOCATIONS_LAC;
import static com.piusvelte.wapdroid.WapdroidService.PAIRS_RSSI_MIN;
import static com.piusvelte.wapdroid.WapdroidService.FILTER_ALL;
import static com.piusvelte.wapdroid.WapdroidService.STATUS;

import com.admob.android.ads.AdListener;
import com.admob.android.ads.AdView;
import com.piusvelte.wapdroid.R;

import android.app.AlertDialog;
import android.app.ListActivity;
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

public class ManageData extends ListActivity implements AdListener {
	private UIDbAdapter mDbHelper;
	private int mNetwork = 0, mCid;
	private static final int MANAGE_ID = Menu.FIRST;
	private static final int MAP_ID = Menu.FIRST + 1;
	private static final int DELETE_ID = Menu.FIRST + 2;
	private static final int CANCEL_ID = Menu.FIRST + 3;
	private static final int REFRESH_ID = Menu.FIRST + 4;
	private static final int FILTER_ID = Menu.FIRST + 5;
	private int mFilter = FILTER_ALL;
	private String mCells = "", mOperator = "", mBssid = "";
	private ServiceConn mServiceConn;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Bundle extras = getIntent().getExtras();
		if (extras != null) {
			mNetwork = extras.getInt(TABLE_NETWORKS);
			mCid = extras.getInt(CELLS_CID);
			mBssid = extras.getString(NETWORKS_BSSID);
			mCells = extras.getString(TABLE_CELLS);
		}
		setContentView(mNetwork == 0 ? R.layout.networks_list : R.layout.cells_list);
		registerForContextMenu(getListView());
		mDbHelper = new UIDbAdapter(this);
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
		} catch (RemoteException e) {
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
				} catch (RemoteException e) {}
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
			} catch (RemoteException e1) {
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
			AlertDialog.Builder dialog = new AlertDialog.Builder(this);
			dialog.setSingleChoiceItems(
					R.array.filter_entries,
					which,
					new DialogInterface.OnClickListener() {
						//@Override
						public void onClick(DialogInterface dialog, int which) {
							dialog.dismiss();
							mFilter = Integer.parseInt(getResources().getStringArray(R.array.filter_values)[which]);
							try {
								listData();
							} catch (RemoteException e) {
								e.printStackTrace();
							}
						}});
			dialog.show();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, view, menuInfo);
		if (mNetwork == 0) menu.add(0, MANAGE_ID, 0, R.string.menu_manageCells);
		menu.add(0, MAP_ID, 0, mNetwork == 0 ? R.string.map_network : R.string.map_cell);
		menu.add(0, DELETE_ID, 0, mNetwork == 0 ? R.string.menu_deleteNetwork : R.string.menu_deleteCell);
		menu.add(0, CANCEL_ID, 0, R.string.cancel);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		itemAction(item.getItemId(), (int) ((AdapterContextMenuInfo) item.getMenuInfo()).id);
		return super.onContextItemSelected(item);
	}

	@Override
	protected void onListItemClick(ListView list, View view, int position, long id) {
		super.onListItemClick(list, view, position, id);
		final int item = (int) id;
		if (mNetwork == 0) {
			final CharSequence[] items = {getString(R.string.menu_manageCells), getString(R.string.map_network), getString(R.string.menu_deleteNetwork), getString(R.string.cancel)};
			AlertDialog.Builder dialog = new AlertDialog.Builder(this);
			dialog.setItems(items, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.cancel();
					// +1 to reindex
					itemAction(which + 1, item);
				}
			});
			dialog.show();
		}
		else {
			final CharSequence[] items = {getString(R.string.map_cell), getString(R.string.menu_deleteCell), getString(R.string.cancel)};
			AlertDialog.Builder dialog = new AlertDialog.Builder(this);
			dialog.setItems(items, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.cancel();
					// +2 to reindex and as LIST_ID isn't an option here
					itemAction(which + 2, item);
				}
			});
			dialog.show();
		}
	}
	
	public void itemAction(int action, int id) {
		Intent intent;
		switch(action) {
		case MANAGE_ID:
			intent = new Intent(this, ManageData.class);
			intent.putExtra(TABLE_NETWORKS, id);
			intent.putExtra(TABLE_CELLS, mCells);
			startActivity(intent);
			return;
		case MAP_ID:
			// open gmaps
			intent = new Intent(this, MapData.class);
			intent.putExtra(TABLE_NETWORKS, (int) (mNetwork == 0 ? id : mNetwork));
			if (mNetwork != 0) intent.putExtra(TABLE_PAIRS, id);
			intent.putExtra(MapData.OPERATOR, mOperator);
			startActivity(intent);
			return;
		case DELETE_ID:
			if (mNetwork == 0) mDbHelper.deleteNetwork(id);
			else mDbHelper.deletePair(mNetwork, id);
			try {
				listData();
			}
			catch (RemoteException e) {
				e.printStackTrace();
			}
			return;
		case CANCEL_ID:
			return;
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
						new String[] {NETWORKS_SSID, NETWORKS_BSSID, STATUS},
						new int[] {R.id.network_row_SSID, R.id.network_row_BSSID, R.id.network_row_status})
		: new SimpleCursorAdapter(this,
				R.layout.cell_row,
				c,
				new String[] {CELLS_CID, LOCATIONS_LAC, PAIRS_RSSI_MIN, STATUS},
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
	};

	@Override
	public void onFailedToReceiveAd(AdView arg0) {}

	@Override
	public void onFailedToReceiveRefreshedAd(AdView arg0) {}

	@Override
	public void onReceiveAd(AdView arg0) {}

	@Override
	public void onReceiveRefreshedAd(AdView arg0) {}
}