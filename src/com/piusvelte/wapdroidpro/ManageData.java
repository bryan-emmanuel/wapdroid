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

import static com.piusvelte.wapdroidpro.Wapdroid.UNKNOWN_CID;
import static com.piusvelte.wapdroidpro.Wapdroid.UNKNOWN_RSSI;

import com.piusvelte.wapdroidpro.IWapdroidService;
import com.piusvelte.wapdroidpro.IWapdroidUI;
import com.piusvelte.wapdroidpro.R;
import com.piusvelte.wapdroidpro.Wapdroid.Cells;
import com.piusvelte.wapdroidpro.Wapdroid.Locations;
import com.piusvelte.wapdroidpro.Wapdroid.Networks;
import com.piusvelte.wapdroidpro.Wapdroid.Pairs;
import com.piusvelte.wapdroidpro.Wapdroid.Ranges;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.AdapterView.AdapterContextMenuInfo;

public class ManageData extends ListActivity implements ServiceConnection {
	long mNetwork = 0;
	int mCid;
	private static final int MANAGE_ID = 0;
	private static final int MANAGE_NETWORK_OR_CELL_ID = 1;
	private static final int MAP_ID = 2;
	private static final int DELETE_ID = Menu.FIRST;
	private static final int CANCEL_ID = Menu.FIRST + 1;
	private static final int REFRESH_ID = Menu.FIRST;
	private static final int FILTER_ID = Menu.FIRST + 1;
	private static final String STATUS = "status";
	private static final int FILTER_ALL = 0;
	private static final int FILTER_INRANGE = 1;
	private static final int FILTER_OUTRANGE = 2;
	private static final int FILTER_CONNECTED = 3;
	private int mFilter = FILTER_ALL;
	String mCells = "",
	mOperator = "",
	mBssid = "",
	mSsid = "";
	public IWapdroidService mIService;
	private IWapdroidUI.Stub mWapdroidUI = new IWapdroidUI.Stub() {
		public void setCellInfo(int cid, int lac) throws RemoteException {
			mCid = cid;
		}

		public void setWifiInfo(int state, String ssid, String bssid) throws RemoteException {
			mSsid = ssid;
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

	private final SimpleCursorAdapter.ViewBinder mViewBinder = new SimpleCursorAdapter.ViewBinder() {
		@Override
		public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
			if (columnIndex == cursor.getColumnIndex(Networks.MANAGE)) {
				((CheckBox) view).setChecked(cursor.getInt(columnIndex) == 1);
				return true;
			} else if (columnIndex == cursor.getColumnIndex(Ranges.MANAGE_CELL)) {
				((CheckBox) view).setChecked(cursor.getInt(columnIndex) == 1);
				return true;
			} else return false;
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Bundle extras = getIntent().getExtras();
		if (extras != null) {
			mNetwork = extras.getLong(WapdroidProvider.TABLE_NETWORKS);
			mCid = extras.getInt(Cells.CID);
			mSsid = extras.getString(Networks.SSID);
			mBssid = extras.getString(Networks.BSSID);
			mCells = extras.getString(WapdroidProvider.TABLE_CELLS);
		}
		setContentView(mNetwork == 0 ? R.layout.networks_list : R.layout.cells_list);
		registerForContextMenu(getListView());
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
		menu.add(0, REFRESH_ID, 0, String.format(getString(R.string.refresh), getString(mNetwork == 0 ? R.string.network : R.string.cell))).setIcon(android.R.drawable.ic_menu_rotate);
		menu.add(0, FILTER_ID, 0, R.string.menu_filter).setIcon(android.R.drawable.ic_menu_agenda);
		return result;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case REFRESH_ID:
			this.listData();
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
							listData();
						}});
			dialog.show();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, view, menuInfo);
		menu.add(0, DELETE_ID, 0, String.format(getString(R.string.forget), getString(mNetwork == 0 ? R.string.network : R.string.cell)));
		menu.add(0, CANCEL_ID, 0, android.R.string.cancel);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		int id = (int) ((AdapterContextMenuInfo) item.getMenuInfo()).id;
		if (item.getItemId() == DELETE_ID) {
			if (mNetwork == 0) {
				this.getContentResolver().delete(Networks.CONTENT_URI, Networks._ID + "=" + id, null);
				this.getContentResolver().delete(Pairs.CONTENT_URI, Pairs.NETWORK + "=" + id, null);
			} else {
				this.getContentResolver().delete(Pairs.CONTENT_URI, Pairs._ID + "=" + id, null);
				Cursor n = this.getContentResolver().query(Pairs.CONTENT_URI, new String[]{Pairs._ID}, Pairs.NETWORK + "=" + mNetwork, null, null);
				if (n.getCount() == 0) this.getContentResolver().delete(Pairs.CONTENT_URI, Pairs._ID + "=" + mNetwork, null);
				n.close();
			}
			Cursor c = this.getContentResolver().query(Cells.CONTENT_URI, new String[]{Cells._ID, Cells.LOCATION}, null, null, null);
			if (c.moveToFirst()) {
				int[] index = {c.getColumnIndex(Cells._ID), c.getColumnIndex(Cells.LOCATION)};
				while (!c.isAfterLast()) {
					int cell = c.getInt(index[0]);
					Cursor p = this.getContentResolver().query(Pairs.CONTENT_URI, new String[]{Pairs._ID}, Pairs.CELL + "=" + cell, null, null);
					if (p.getCount() == 0) {
						this.getContentResolver().delete(Cells.CONTENT_URI, Cells._ID + "=" + cell, null);
						int location = c.getInt(index[1]);
						Cursor l = this.getContentResolver().query(Cells.CONTENT_URI, new String[]{Cells.LOCATION}, Cells.LOCATION + "=" + location, null, null);
						if (l.getCount() == 0) this.getContentResolver().delete(Locations.CONTENT_URI, Locations._ID + "=" + location, null);
						l.close();
					}
					p.close();
					c.moveToNext();
				}
			}
			c.close();
		}
		return super.onContextItemSelected(item);
	}

	@Override
	protected void onListItemClick(ListView list, final View view, int position, final long id) {
		super.onListItemClick(list, view, position, id);
		if (mNetwork == 0) {
			final CharSequence[] items = {String.format(getString(R.string.manage), getString(R.string.cell)), String.format(getString(((CheckBox) view.findViewById(R.id.network_manage)).isChecked() ? R.string.ignore_item : R.string.manage_item), getString(R.string.network)), String.format(getString(R.string.map), getString(R.string.network)), getString(android.R.string.cancel)};
			AlertDialog.Builder dialog = new AlertDialog.Builder(this);
			dialog.setItems(items, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.cancel();
					switch(which) {
					case MANAGE_ID:
						startActivity((new Intent(ManageData.this, ManageData.class)).putExtra(WapdroidProvider.TABLE_NETWORKS, id).putExtra(WapdroidProvider.TABLE_CELLS, mCells));
						return;
					case MANAGE_NETWORK_OR_CELL_ID:
						// toggle the manage checkbox
						ContentValues values = new ContentValues();
						values.put(Networks.MANAGE, ((CheckBox) view.findViewById(R.id.network_manage)).isChecked() ? 0 : 1);
						ManageData.this.getContentResolver().update(Networks.CONTENT_URI, values, Networks._ID + "=?", new String[] {Long.toString(id)});
						return;
					case MAP_ID:
						// open gmaps
						startActivity((new Intent(ManageData.this, MapData.class)).putExtra(WapdroidProvider.TABLE_NETWORKS, id).putExtra(MapData.OPERATOR, mOperator));
						return;
					}
				}
			});
			dialog.show();
		} else {
			final CharSequence[] items = {String.format(getString(((CheckBox) view.findViewById(R.id.cell_manage)).isChecked() ? R.string.ignore_item : R.string.manage_item), getString(R.string.cell)), String.format(getString(R.string.map), getString(R.string.cell)), getString(android.R.string.cancel)};
			AlertDialog.Builder dialog = new AlertDialog.Builder(this);
			dialog.setItems(items, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.cancel();
					which++; // correct the id offset, as there is no MANAGE_ID for cells
					switch(which) {
					case MANAGE_NETWORK_OR_CELL_ID:
						// toggle the manage checkbox
						ContentValues values = new ContentValues();
						values.put(Pairs.MANAGE_CELL, ((CheckBox) view.findViewById(R.id.cell_manage)).isChecked() ? 0 : 1);
						ManageData.this.getContentResolver().update(Pairs.CONTENT_URI, values, Pairs._ID + "=?", new String[] {Long.toString(id)});
						return;
					case MAP_ID:
						startActivity((new Intent(ManageData.this, MapData.class)).putExtra(WapdroidProvider.TABLE_NETWORKS, mNetwork).putExtra(MapData.OPERATOR, mOperator).putExtra(WapdroidProvider.TABLE_PAIRS, id));
						return;
					}
				}
			});
			dialog.show();
		}
	}

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
		if (mCells.length() > 0) {
			Resources r = getResources();
			Cursor c;
			if (mNetwork == 0) {
				c = this.managedQuery(
						Networks.CONTENT_URI,
						new String[]{Networks._ID, Networks.SSID, Networks.BSSID, (mFilter == FILTER_ALL ?
								"case when " + Networks.SSID + "='" + mSsid + "' and " + Networks.BSSID + "='" + mBssid + "' then '" + r.getString(R.string.connected)
								+ "' when " + Networks._ID + " in (select " + Ranges.NETWORK + " from " + WapdroidProvider.VIEW_RANGES + " where" + mCells + ") then '" + r.getString(R.string.withinarea)
								+ "' else '" + r.getString(R.string.outofarea) + "' end"
								: "'" + r.getString(mFilter == FILTER_CONNECTED ?
										R.string.connected
										: (mFilter == FILTER_INRANGE ?
												R.string.withinarea :
													R.string.outofarea)) + "'") + " as " + STATUS
													, Networks.MANAGE},
													(mFilter == FILTER_ALL ? null
															: (mFilter == FILTER_CONNECTED ?
																	Networks.SSID + "=? and "
																	+ Networks.BSSID + "=?"
																	: Networks._ID + (mFilter == FILTER_OUTRANGE ?
																			" NOT"
																			: "") + " in (select " + Ranges.NETWORK
																			+ " from " + WapdroidProvider.VIEW_RANGES + " where" + mCells + ")"))
																			, (mFilter == FILTER_CONNECTED ? new String[]{mSsid, mBssid} : null), STATUS);
				SimpleCursorAdapter sca = new SimpleCursorAdapter(this,
						R.layout.network_row,
						c,
						new String[] {Networks.SSID, Networks.BSSID, STATUS, Networks.MANAGE},
						new int[] {R.id.network_row_SSID, R.id.network_row_BSSID, R.id.network_row_status, R.id.network_manage});
				sca.setViewBinder(mViewBinder);
				setListAdapter(sca);
			} else {
				c = this.managedQuery(
						Ranges.CONTENT_URI
						, new String[]{Ranges._ID, Ranges.CID, "case when " + Ranges.LAC + "=" + UNKNOWN_CID + " then '" + r.getString(R.string.unknown) + "' else " + Ranges.LAC + " end as " + Ranges.LAC + ",case when " + Ranges.RSSI_MIN + "=" + UNKNOWN_RSSI + " or " + Ranges.RSSI_MAX + "=" + UNKNOWN_RSSI + " then '" + r.getString(R.string.unknown) + "' else (" + Ranges.RSSI_MIN + "||'" + r.getString(R.string.colon) + "'||" + Ranges.RSSI_MAX + "||'" + r.getString(R.string.dbm) + "') end as " + Ranges.RSSI_MIN + ","
								+ (mFilter == FILTER_ALL ?
										"case when " + Ranges.CID + "='" + mCid + "' then '" + r.getString(R.string.connected)
										+ "' when " + Ranges.CELL + " in (select " + Ranges.CELL + " from " + WapdroidProvider.VIEW_RANGES + " where " + Ranges.NETWORK + "=" + mNetwork + " and" + mCells + ")" + " then '" + r.getString(R.string.withinarea)
										+ "' else '" + r.getString(R.string.outofarea) + "' end"
										: "'" + r.getString(mFilter == FILTER_CONNECTED ?
												R.string.connected
												: (mFilter == FILTER_INRANGE ?
														R.string.withinarea
														: R.string.outofarea)) + "'") + " as " + STATUS
														, Ranges.MANAGE_CELL}, Ranges.NETWORK + "=?" + (mFilter == FILTER_ALL ? "" :
															" and " + (mFilter == FILTER_CONNECTED ?
																	Ranges.CID + "=?"
																	: Ranges.CELL + (mFilter == FILTER_OUTRANGE ?
																			" NOT"
																			: "") + " in (select " + Ranges.CELL
																			+ " from " + WapdroidProvider.VIEW_RANGES
																			+ " where " + Ranges.NETWORK + "=" + mNetwork + " and"
																			+ mCells + ")"))
																			, (mFilter == FILTER_CONNECTED ? new String[]{Long.toString(mNetwork), Integer.toString(mCid)} : new String[]{Long.toString(mNetwork)}), STATUS);
				SimpleCursorAdapter sca = new SimpleCursorAdapter(this,
						R.layout.cell_row,
						c,
						new String[] {Ranges.CID, Ranges.LAC, Ranges.RSSI_MIN, STATUS, Ranges.MANAGE_CELL},
						new int[] {R.id.cell_row_CID, R.id.cell_row_LAC, R.id.cell_row_range, R.id.cell_row_status, R.id.cell_manage});
				sca.setViewBinder(mViewBinder);
				setListAdapter(sca);
			}
		}
	}
}