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

import com.admob.android.ads.AdListener;
import com.admob.android.ads.AdView;
import com.piusvelte.wapdroid.Wapdroid.Networks;
import com.piusvelte.wapdroid.Wapdroid.Ranges;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.AdapterView.AdapterContextMenuInfo;

public class ManageData extends ListActivity implements ServiceConnection, AdListener {
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
	
	private final SimpleCursorAdapter.ViewBinder mViewBinder = new SimpleCursorAdapter.ViewBinder() {
		@Override
		public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
			if (columnIndex == cursor.getColumnIndex(Networks.MANAGE)) {
				((CheckBox) view).setChecked(cursor.getInt(columnIndex) == 1);
				return true;
			}
			return false;
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Bundle extras = getIntent().getExtras();
		if (extras != null) {
			mNetwork = extras.getInt(WapdroidProvider.TABLE_NETWORKS);
			mCid = extras.getInt(Wapdroid.Cells.CID);
			mBssid = extras.getString(Networks.BSSID);
			mCells = extras.getString(WapdroidProvider.TABLE_CELLS);
		}
		setContentView(mNetwork == 0 ? R.layout.networks_list : R.layout.cells_list);
		final ListView listView = getListView();
		registerForContextMenu(listView);
//		listView.setItemsCanFocus(false);
//		listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
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
		if (mNetwork == 0) {
			menu.add(0, MANAGE_ID, 0, String.format(getString(R.string.manage), getString(R.string.cell)));
			menu.add(0, MAP_ID, 0, String.format(getString(R.string.map), getString(R.string.network)));
			menu.add(0, DELETE_ID, 0, String.format(getString(R.string.forget), getString(R.string.network)));
		} else {
			menu.add(0, MAP_ID, 0, String.format(getString(R.string.map), getString(R.string.cell)));
			menu.add(0, DELETE_ID, 0, String.format(getString(R.string.forget), getString(R.string.cell)));
		}
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
		Log.v("ManageData","onListItemClick");
		Log.v("ManageData","checkbox:"+R.id.network_manage);
		Log.v("ManageData","view:"+view.getId());
		Log.v("ManageData","found"+view.findViewById(R.id.network_manage));
		if (mNetwork == 0) {
			final CharSequence[] items = {String.format(getString(R.string.manage), getString(R.string.cell)), String.format(getString(R.string.map), getString(R.string.network)), String.format(getString(R.string.forget), getString(R.string.network)), getString(android.R.string.cancel)};
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
			final CharSequence[] items = {String.format(getString(R.string.map), getString(R.string.cell)), String.format(getString(R.string.forget), getString(R.string.cell)), getString(android.R.string.cancel)};
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
			startActivity((new Intent(this, ManageData.class)).putExtra(WapdroidProvider.TABLE_NETWORKS, id).putExtra(WapdroidProvider.TABLE_CELLS, mCells));
			return;
		case MAP_ID:
			// open gmaps
			if (mNetwork != 0) startActivity((new Intent(this, MapData.class)).putExtra(WapdroidProvider.TABLE_NETWORKS, (int) (mNetwork == 0 ? id : mNetwork)).putExtra(MapData.OPERATOR, mOperator).putExtra(WapdroidProvider.TABLE_PAIRS, id));
			else startActivity((new Intent(this, MapData.class)).putExtra(WapdroidProvider.TABLE_NETWORKS, (int) (mNetwork == 0 ? id : mNetwork)).putExtra(MapData.OPERATOR, mOperator));
			return;
		case DELETE_ID:
			if (mNetwork == 0) {
				this.getContentResolver().delete(Networks.CONTENT_URI, Networks._ID + "=" + id, null);
				this.getContentResolver().delete(Wapdroid.Pairs.CONTENT_URI, Wapdroid.Pairs.NETWORK + "=" + id, null);
			} else {
				this.getContentResolver().delete(Wapdroid.Pairs.CONTENT_URI, Wapdroid.Pairs._ID + "=" + id, null);
				Cursor n = this.getContentResolver().query(Wapdroid.Pairs.CONTENT_URI, new String[]{Wapdroid.Pairs._ID}, Wapdroid.Pairs.NETWORK + "=" + mNetwork, null, null);
				if (n.getCount() == 0) this.getContentResolver().delete(Wapdroid.Pairs.CONTENT_URI, Wapdroid.Pairs._ID + "=" + mNetwork, null);
				n.close();
			}
			Cursor c = this.getContentResolver().query(Wapdroid.Cells.CONTENT_URI, new String[]{Wapdroid.Cells._ID, Wapdroid.Cells.LOCATION}, null, null, null);
			if (c.moveToFirst()) {
				int[] index = {c.getColumnIndex(Wapdroid.Cells._ID), c.getColumnIndex(Wapdroid.Cells.LOCATION)};
				while (!c.isAfterLast()) {
					int cell = c.getInt(index[0]);
					Cursor p = this.getContentResolver().query(Wapdroid.Pairs.CONTENT_URI, new String[]{Wapdroid.Pairs._ID}, Wapdroid.Pairs.CELL + "=" + cell, null, null);
					if (p.getCount() == 0) {
						this.getContentResolver().delete(Wapdroid.Cells.CONTENT_URI, Wapdroid.Cells._ID + "=" + cell, null);
						int location = c.getInt(index[1]);
						Cursor l = this.getContentResolver().query(Wapdroid.Cells.CONTENT_URI, new String[]{Wapdroid.Cells.LOCATION}, Wapdroid.Cells.LOCATION + "=" + location, null, null);
						if (l.getCount() == 0) this.getContentResolver().delete(Wapdroid.Locations.CONTENT_URI, Wapdroid.Locations._ID + "=" + location, null);
						l.close();
					}
					p.close();
					c.moveToNext();
				}
			}
			c.close();
			listData();
			return;
		case CANCEL_ID:
			return;
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
								"case when " + Networks.BSSID + "='" + mBssid + "' then '" + r.getString(R.string.connected)
								+ "' when " + Networks._ID + " in (select " + Wapdroid.Ranges.NETWORK + " from " + WapdroidProvider.VIEW_RANGES + " where" + mCells + ") then '" + r.getString(R.string.withinarea)
								+ "' else '" + r.getString(R.string.outofarea) + "' end"
								: "'" + r.getString(mFilter == FILTER_CONNECTED ?
										R.string.connected
										: (mFilter == FILTER_INRANGE ?
												R.string.withinarea :
													R.string.outofarea)) + "'") + " as " + STATUS
						, Networks.MANAGE},
						(mFilter == FILTER_ALL ? null
								: (mFilter == FILTER_CONNECTED ?
										Networks.BSSID + "=?"
										: Networks._ID + (mFilter == FILTER_OUTRANGE ?
												" NOT"
												: "") + " in (select " + Wapdroid.Ranges.NETWORK
												+ " from " + WapdroidProvider.VIEW_RANGES + " where" + mCells + ")"))
												, (mFilter == FILTER_CONNECTED ? new String[]{mBssid} : null), STATUS);
				SimpleCursorAdapter sca = new SimpleCursorAdapter(this,
						R.layout.network_row,
						c,
						new String[] {Wapdroid.Ranges.CID, Wapdroid.Ranges.LAC, Wapdroid.Ranges.RSSI_MIN, STATUS},
						new int[] {R.id.cell_row_CID, R.id.cell_row_LAC, R.id.cell_row_range, R.id.cell_row_status});
				sca.setViewBinder(mViewBinder);
				setListAdapter(sca);
			} else {
				c = this.managedQuery(
						Ranges.CONTENT_URI
						, new String[]{Ranges._ID, Ranges.CID, "case when " + Ranges.LAC + "=" + Wapdroid.UNKNOWN_CID + " then '" + r.getString(R.string.unknown) + "' else " + Ranges.LAC + " end as " + Ranges.LAC + ",case when " + Ranges.RSSI_MIN + "=" + Wapdroid.UNKNOWN_RSSI + " or " + Ranges.RSSI_MAX + "=" + Wapdroid.UNKNOWN_RSSI + " then '" + r.getString(R.string.unknown) + "' else (" + Ranges.RSSI_MIN + "||'" + r.getString(R.string.colon) + "'||" + Ranges.RSSI_MAX + "||'" + r.getString(R.string.dbm) + "') end as " + Ranges.RSSI_MIN + ","
								+ (mFilter == FILTER_ALL ?
										"case when " + Ranges.CID + "='" + mCid + "' then '" + r.getString(R.string.connected)
										+ "' when " + Ranges.CELL + " in (select " + Ranges.CELL + " from " + WapdroidProvider.VIEW_RANGES + " where " + Ranges.NETWORK + "=" + mNetwork + " and" + mCells + ")" + " then '" + r.getString(R.string.withinarea)
										+ "' else '" + r.getString(R.string.outofarea) + "' end"
										: "'" + r.getString(mFilter == FILTER_CONNECTED ?
												R.string.connected
												: (mFilter == FILTER_INRANGE ?
														R.string.withinarea
														: R.string.outofarea)) + "'") + " as " + STATUS
						}, Ranges.NETWORK + "=?" + (mFilter == FILTER_ALL ? "" :
							" and " + (mFilter == FILTER_CONNECTED ?
									Ranges.CID + "=?"
									: Ranges.CELL + (mFilter == FILTER_OUTRANGE ?
											" NOT"
											: "") + " in (select " + Ranges.CELL
											+ " from " + WapdroidProvider.VIEW_RANGES
											+ " where " + Ranges.NETWORK + "=" + mNetwork + " and"
											+ mCells + ")"))
											, (mFilter == FILTER_CONNECTED ? new String[]{Integer.toString(mNetwork), Integer.toString(mCid)} : new String[]{Integer.toString(mNetwork)}), STATUS);
				setListAdapter(new SimpleCursorAdapter(this,
						R.layout.cell_row,
						c,
						new String[] {Ranges.CID, Ranges.LAC, Ranges.RSSI_MIN, STATUS},
						new int[] {R.id.cell_row_CID, R.id.cell_row_LAC, R.id.cell_row_range, R.id.cell_row_status}));
			}
		}
	}

	@Override
	public void onFailedToReceiveAd(AdView arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onFailedToReceiveRefreshedAd(AdView arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onReceiveAd(AdView arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onReceiveRefreshedAd(AdView arg0) {
		// TODO Auto-generated method stub
		
	}
}