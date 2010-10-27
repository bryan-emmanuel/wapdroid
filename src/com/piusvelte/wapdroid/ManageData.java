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

import static com.piusvelte.wapdroid.WapdroidDatabaseHelper.TABLE_NETWORKS;
import static com.piusvelte.wapdroid.WapdroidDatabaseHelper.TABLE_CELLS;
import static com.piusvelte.wapdroid.WapdroidDatabaseHelper.TABLE_LOCATIONS;
import static com.piusvelte.wapdroid.WapdroidDatabaseHelper.TABLE_PAIRS;
import static com.piusvelte.wapdroid.WapdroidDatabaseHelper.VIEW_RANGES;
import static com.piusvelte.wapdroid.WapdroidDatabaseHelper.UNKNOWN_CID;
import static com.piusvelte.wapdroid.WapdroidDatabaseHelper.UNKNOWN_RSSI;
import static com.piusvelte.wapdroid.WapdroidDatabaseHelper.NETWORK;
import static com.piusvelte.wapdroid.WapdroidDatabaseHelper._ID;
import static com.piusvelte.wapdroid.WapdroidDatabaseHelper.BSSID;
import static com.piusvelte.wapdroid.WapdroidDatabaseHelper.CELL;
import static com.piusvelte.wapdroid.WapdroidDatabaseHelper.CID;
import static com.piusvelte.wapdroid.WapdroidDatabaseHelper.LAC;
import static com.piusvelte.wapdroid.WapdroidDatabaseHelper.LOCATION;
import static com.piusvelte.wapdroid.WapdroidDatabaseHelper.RSSI_MAX;
import static com.piusvelte.wapdroid.WapdroidDatabaseHelper.RSSI_MIN;
import static com.piusvelte.wapdroid.WapdroidDatabaseHelper.SSID;
import static com.piusvelte.wapdroid.WapdroidDatabaseHelper.TAG;

import com.admob.android.ads.AdListener;
import com.admob.android.ads.AdView;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.AdapterView.AdapterContextMenuInfo;

public class ManageData extends ListActivity implements AdListener, ServiceConnection {
	int mNetwork = 0, mCid;
	private static final int MANAGE_ID = Menu.FIRST;
	private static final int MAP_ID = Menu.FIRST + 1;
	private static final int DELETE_ID = Menu.FIRST + 2;
	private static final int CANCEL_ID = Menu.FIRST + 3;
	private static final int REFRESH_ID = Menu.FIRST + 4;
	private static final int FILTER_ID = Menu.FIRST + 5;
	private static final String STATUS = "status";
	private static final int FILTER_ALL = 0;
	private static final int FILTER_INRANGE = 1;
	private static final int FILTER_OUTRANGE = 2;
	private static final int FILTER_CONNECTED = 3;
	private int mFilter = FILTER_ALL;
	String mCells = "",
	mOperator = "",
	mBssid = "";
	public IWapdroidService mIService;
	private Context mContext;
	private WapdroidDatabaseHelper mWapdroidDatabaseHelper;
	private Cursor mCursor;
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
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mContext = this;
		Bundle extras = getIntent().getExtras();
		if (extras != null) {
			mNetwork = extras.getInt(TABLE_NETWORKS);
			mCid = extras.getInt(CID);
			mBssid = extras.getString(BSSID);
		}
		setContentView(mNetwork == 0 ? R.layout.networks_list : R.layout.cells_list);
		registerForContextMenu(getListView());
		mWapdroidDatabaseHelper = new WapdroidDatabaseHelper(this);
	}

	@Override
	protected void onResume() {
		super.onResume();
		SharedPreferences prefs = getSharedPreferences(getString(R.string.key_preferences), MODE_PRIVATE);
		if (prefs.getBoolean(getString(R.string.key_manageWifi), true)) startService(new Intent(this, WapdroidService.class));
		bindService(new Intent(this, WapdroidService.class), this, BIND_AUTO_CREATE);
		listData();
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (mIService != null) {
			try {
				mIService.setCallback(null);
			} catch (RemoteException e) {}
		}
		unbindService(this);
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
			mCursor.requery();
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
						@Override
						public void onClick(DialogInterface dialog, int which) {
							dialog.dismiss();
							mFilter = Integer.parseInt(getResources().getStringArray(R.array.filter_values)[which]);
							stopManagingCursor(mCursor);
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
		menu.add(0, CANCEL_ID, 0, android.R.string.cancel);
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
			final CharSequence[] items = {getString(R.string.menu_manageCells), getString(R.string.map_network), getString(R.string.menu_deleteNetwork), getString(android.R.string.cancel)};
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
		} else {
			final CharSequence[] items = {getString(R.string.map_cell), getString(R.string.menu_deleteCell), getString(android.R.string.cancel)};
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
		switch(action) {
		case MANAGE_ID:
			startActivity((new Intent(this, ManageData.class)).putExtra(TABLE_NETWORKS, id));
			return;
		case MAP_ID:
			// open gmaps
			if (mNetwork != 0) startActivity((new Intent(this, MapData.class)).putExtra(TABLE_NETWORKS, (int) (mNetwork == 0 ? id : mNetwork)).putExtra(MapData.OPERATOR, mOperator).putExtra(TABLE_PAIRS, id));
			else startActivity((new Intent(this, MapData.class)).putExtra(TABLE_NETWORKS, (int) (mNetwork == 0 ? id : mNetwork)).putExtra(MapData.OPERATOR, mOperator));
			return;
		case DELETE_ID:
			SQLiteDatabase db = mWapdroidDatabaseHelper.getWritableDatabase();
			if (mNetwork == 0) {
				Log.v(TAG, "delete "+id);
				db.delete(TABLE_NETWORKS, _ID + "=" + id, null);
				db.delete(TABLE_PAIRS, NETWORK + "=" + id, null);
			} else {
				db.delete(TABLE_PAIRS, _ID + "=" + id, null);
				Cursor n = db.query(TABLE_PAIRS, new String[]{_ID}, NETWORK + "=" + mNetwork, null, null, null, null);
				if (n.getCount() == 0) db.delete(TABLE_PAIRS, _ID + "=" + mNetwork, null);
				n.close();
			}
			Cursor c = db.query(TABLE_CELLS, new String[]{_ID, LOCATION}, null, null, null, null, null);
			if (c.getCount() > 0) {
				c.moveToFirst();
				int[] index = {c.getColumnIndex(_ID), c.getColumnIndex(LOCATION)};
				while (!c.isAfterLast()) {
					int cell = c.getInt(index[0]);
					Cursor p = db.query(TABLE_PAIRS, new String[]{_ID}, CELL + "=" + cell, null, null, null, null);
					if (p.getCount() == 0) {
						db.delete(TABLE_CELLS, _ID + "=" + cell, null);
						int location = c.getInt(index[1]);
						Cursor l = db.query(TABLE_CELLS, new String[]{LOCATION}, LOCATION + "=" + location, null, null, null, null);
						if (l.getCount() == 0) db.delete(TABLE_LOCATIONS, _ID + "=" + location, null);
						l.close();
					}
					p.close();
					c.moveToNext();
				}
			}
			c.close();
			db.close();
			listData();
			return;
		case CANCEL_ID:
			return;
		}
	}

	@Override
	public void onFailedToReceiveAd(AdView arg0) {}

	@Override
	public void onFailedToReceiveRefreshedAd(AdView arg0) {}

	@Override
	public void onReceiveAd(AdView arg0) {}

	@Override
	public void onReceiveRefreshedAd(AdView arg0) {}

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

	private void listData() {
		Resources r = getResources();
		SQLiteDatabase db = mWapdroidDatabaseHelper.getWritableDatabase();
		if (mNetwork == 0) mCursor = db.query(TABLE_NETWORKS,
				mFilter == FILTER_ALL ?
						new String[]{_ID,
				SSID,
				BSSID, "case when " + BSSID + "='" + mBssid
				+ "' then '" + r.getString(R.string.connected)
				+ "' else (case when "
				+ _ID + " in (select " + NETWORK	+ " from " + TABLE_PAIRS + ", " + TABLE_CELLS + ", " + TABLE_LOCATIONS + " where " + CELL + "=" + TABLE_CELLS + "." + _ID + " and " + LOCATION + "=" + TABLE_LOCATIONS + "." + _ID + mCells
				+ ") then '" + r.getString(R.string.withinarea)
				+ "' else '" + r.getString(R.string.outofarea) + "' end) end as " + STATUS}
		: new String[]{_ID,
							SSID,
							BSSID,
							r.getString(mFilter == FILTER_CONNECTED ? R.string.connected : (mFilter == FILTER_INRANGE ? R.string.withinarea : R.string.outofarea)) + " as " + STATUS},
							(mFilter == FILTER_CONNECTED ? BSSID + "='" + mBssid + "'"
									: (mFilter == FILTER_OUTRANGE ? _ID + " NOT" : _ID) + " in (select " + NETWORK
									+ " from " + TABLE_PAIRS + ", " + TABLE_CELLS + ", " + TABLE_LOCATIONS
									+ " where " + CELL + "=" + TABLE_CELLS + "." + _ID
									+ " and " + LOCATION + "=" + TABLE_LOCATIONS + "." + _ID
									+ mCells + ")"), null, null, null, STATUS);
		else {
			mCursor = db.query(VIEW_RANGES, mFilter == FILTER_ALL ? new String[]{_ID,
					CID,
					"case when " + LAC + "=" + UNKNOWN_CID + " then '" + r.getString(R.string.unknown) + "' else " + LAC + " end as " + LAC,
					"case when " + RSSI_MIN + "=" + UNKNOWN_RSSI + " or " + RSSI_MAX + "=" + UNKNOWN_RSSI + " then '" + r.getString(R.string.unknown) + "' else (" + RSSI_MIN + "||'" + r.getString(R.string.colon) + "'||" + RSSI_MAX + "||'" + r.getString(R.string.dbm) + "') end as " + RSSI_MIN,
					"case when " + CID + "='" + mCid + "' then '" + r.getString(R.string.connected)
					+ "' else (case when " + CELL + " in (select "
					+ TABLE_CELLS + "." + _ID
					+ " from " + TABLE_PAIRS + ", " + TABLE_CELLS + ", " + TABLE_LOCATIONS
					+ " where " + CELL + "=" + TABLE_CELLS + "." + _ID
					+ " and " + LOCATION + "=" + TABLE_LOCATIONS + "." + _ID
					+ " and " + NETWORK + "=" + mNetwork
					+ mCells + ")" + " then '" + r.getString(R.string.withinarea)
					+ "' else '" + r.getString(R.string.outofarea) + "' end) end as " + STATUS}
			: new String[]{_ID,
				CID,
				"case when " + LAC + "=" + UNKNOWN_CID + " then '" + r.getString(R.string.unknown) + "' else " + LAC + " end as " + LAC,
				"case when " + RSSI_MIN + "=" + UNKNOWN_RSSI + " or " + RSSI_MAX + "=" + UNKNOWN_RSSI + " then '" + r.getString(R.string.unknown) + "' else (" + RSSI_MIN + "||'" + r.getString(R.string.colon) + "'||" + RSSI_MAX + "||'" + r.getString(R.string.dbm) + "') end as " + RSSI_MIN,
				r.getString(mFilter == FILTER_CONNECTED ? R.string.connected : (mFilter == FILTER_INRANGE ? R.string.withinarea : R.string.outofarea)) + " as " + STATUS},
				NETWORK + "=" + mNetwork
				+ " and " + (mFilter == FILTER_CONNECTED ? CID + "=" + mCid + ""
						: (mFilter == FILTER_OUTRANGE ? CID + " NOT" : CID) + " in (select " + CID
						+ " from " + TABLE_PAIRS + ", " + TABLE_CELLS + ", " + TABLE_LOCATIONS
						+ " where " + CELL + "=" + TABLE_CELLS + "." + _ID
						+ " and " + LOCATION + "=" + TABLE_LOCATIONS + "." + _ID
						+ " and " + NETWORK + "=" + mNetwork
						+ mCells + ")"), null, null, null, STATUS);
		}
		startManagingCursor(mCursor);
		setListAdapter(mNetwork == 0 ?
				new SimpleCursorAdapter(mContext,
						R.layout.network_row,
						mCursor,
						new String[] {SSID, BSSID, STATUS},
						new int[] {R.id.network_row_SSID, R.id.network_row_BSSID, R.id.network_row_status})
		: new SimpleCursorAdapter(mContext,
				R.layout.cell_row,
				mCursor,
				new String[] {CID, LAC, RSSI_MIN, STATUS},
				new int[] {R.id.cell_row_CID, R.id.cell_row_LAC, R.id.cell_row_range, R.id.cell_row_status}));
		db.close();
	}
}