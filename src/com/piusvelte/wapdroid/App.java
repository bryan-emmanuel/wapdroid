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

import android.app.Application;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

public class App extends Application {
	private DatabaseHelper mDbHelper;
	public SQLiteDatabase mDb;
	public static final String TAG = "Wapdroid";
	public static final String TABLE_ID = "_id";
	public static final String TABLE_CODE = "code";
	public static final String TABLE_NETWORKS = "networks";
	public static final String NETWORKS_SSID = "SSID";
	public static final String NETWORKS_BSSID = "BSSID";
	public static final String TABLE_CELLS = "cells";
	public static final String CELLS_CID = "CID";
	public static final String STATUS = "status";
	public static final int FILTER_ALL = 0;
	public static final int FILTER_INRANGE = 1;
	public static final int FILTER_OUTRANGE = 2;
	public static final int FILTER_CONNECTED = 3;
	public static final String TABLE_LOCATIONS = "locations";
	public static final String LOCATIONS_LAC = "LAC";
	public static final String TABLE_PAIRS = "pairs";
	public static final String PAIRS_CELL = "cell";
	public static final String PAIRS_NETWORK = "network";
	public static final String CELLS_LOCATION = "location";
	public static final String PAIRS_RSSI_MIN = "RSSI_min";
	public static final String PAIRS_RSSI_MAX = "RSSI_max";
	public static final int UNKNOWN_CID = -1;
	public static final int UNKNOWN_RSSI = 99;

	@Override
	public void onCreate() {
		super.onCreate();
		mDbHelper = new DatabaseHelper(getApplicationContext());
		try {
			mDb = mDbHelper.getWritableDatabase();
		} catch (SQLException se) {
			Log.e(TAG,"unexpected " + se);
		}
	}

	@Override
	public void onTerminate() {
		if (mDb.isOpen()) mDb.close();
		mDbHelper.close();
		super.onTerminate();
	}

	public void createPair(int cid, int lac, int network, int rssi) {
		int cell, pair, location;
		// select or insert location
		if (lac > 0) {
			Cursor c = mDb.rawQuery("select " + TABLE_ID + " from " + TABLE_LOCATIONS + " where " + LOCATIONS_LAC + "=" + lac, null);
			if (c.getCount() > 0) {
				c.moveToFirst();
				location = c.getInt(c.getColumnIndex(TABLE_ID));
			} else {
				ContentValues cv = new ContentValues();
				cv.put(LOCATIONS_LAC, lac);
				location = (int) mDb.insert(TABLE_LOCATIONS, null, cv);
			}
			c.close();
		} else location = UNKNOWN_CID;
		// if location==-1, then match only on cid, otherwise match on location or -1
		// select or insert cell
		Cursor c = mDb.rawQuery("select " + TABLE_ID + ", " + CELLS_LOCATION + " from " + TABLE_CELLS + " where " + CELLS_CID + "=" + cid + (location == UNKNOWN_CID ? "" : " and (" + CELLS_LOCATION + "=" + UNKNOWN_CID + " or " + CELLS_LOCATION + "=" + location + ")"), null);
		if (c.getCount() > 0) {
			c.moveToFirst();
			cell = c.getInt(c.getColumnIndex(TABLE_ID));
			if ((location != UNKNOWN_CID) && (c.getInt(c.getColumnIndex(CELLS_LOCATION)) == UNKNOWN_CID)){
				// update the location
				ContentValues cv = new ContentValues();
				cv.put(CELLS_LOCATION, location);
				mDb.update(TABLE_CELLS, cv, TABLE_ID + "=" + cell, null);
			}
		} else {
			ContentValues cv = new ContentValues();
			cv.put(CELLS_CID, cid);
			cv.put(CELLS_LOCATION, location);
			cell = (int) mDb.insert(TABLE_CELLS, null, cv);
		}
		c.close();
		// select and update or insert pair
		c = mDb.rawQuery("select " + TABLE_ID + ", " + PAIRS_RSSI_MIN + ", " + PAIRS_RSSI_MAX + " from " + TABLE_PAIRS + " where " + PAIRS_CELL + "=" + cell + " and " + PAIRS_NETWORK + "=" + network, null);
		if (c.getCount() > 0) {
			if (rssi != UNKNOWN_RSSI) {
				c.moveToFirst();
				pair = c.getInt(c.getColumnIndex(TABLE_ID));
				int rssi_min = c.getInt(c.getColumnIndex(PAIRS_RSSI_MIN));
				int rssi_max = c.getInt(c.getColumnIndex(PAIRS_RSSI_MAX));
				boolean update = false;
				ContentValues cv = new ContentValues();
				if (rssi_min > rssi) {
					update = true;
					cv.put(PAIRS_RSSI_MIN, rssi);
				} else if ((rssi_max == UNKNOWN_RSSI) || (rssi_max < rssi)) {
					update = true;
					cv.put(PAIRS_RSSI_MAX, rssi);
				}
				if (update) mDb.update(TABLE_PAIRS, cv, TABLE_ID + "=" + pair, null);
			}
		} else {
			ContentValues cv = new ContentValues();
			cv.put(PAIRS_CELL, cell);
			cv.put(PAIRS_NETWORK, network);
			cv.put(PAIRS_RSSI_MIN, rssi);
			cv.put(PAIRS_RSSI_MAX, rssi);
			mDb.insert(TABLE_PAIRS, null, cv);
		}
		c.close();
	}

	public boolean cellInRange(int cid, int lac, int rssi) {
		boolean inRange;
		Cursor c = mDb.rawQuery("select " + TABLE_CELLS + "." + TABLE_ID + " as " + TABLE_ID + ", " + CELLS_LOCATION + (rssi != UNKNOWN_RSSI ? ", (select min(" + PAIRS_RSSI_MIN + ") from " + TABLE_PAIRS + " where " + PAIRS_CELL + "=" + TABLE_CELLS + "." + TABLE_ID + ") as " + PAIRS_RSSI_MIN + ", (select max(" + PAIRS_RSSI_MAX + ") from " + TABLE_PAIRS + " where " + PAIRS_CELL + "=" + TABLE_CELLS + "." + TABLE_ID + ") as " + PAIRS_RSSI_MAX : "") + " from " + TABLE_CELLS + " left outer join " + TABLE_LOCATIONS + " on " + CELLS_LOCATION + "=" + TABLE_LOCATIONS + "." + TABLE_ID + " where "+ CELLS_CID + "=" + cid + " and (" + LOCATIONS_LAC + "=" + lac + " or " + CELLS_LOCATION + "=" + UNKNOWN_CID + ")"
				+ (rssi == UNKNOWN_RSSI ? "" : " and (((" + PAIRS_RSSI_MIN + "=" + UNKNOWN_RSSI + ") or (" + PAIRS_RSSI_MIN + "<=" + rssi + ")) and (" + PAIRS_RSSI_MAX + ">=" + rssi + "))"), null);
		inRange = (c.getCount() > 0);
		if (inRange && (lac > 0)) {
			// check LAC, as this is a new column
			c.moveToFirst();
			if (c.isNull(c.getColumnIndex(CELLS_LOCATION))) {
				// select or insert location
				int location;
				if (lac > 0) {
					Cursor l = mDb.rawQuery("select " + TABLE_ID + " from " + TABLE_LOCATIONS + " where " + LOCATIONS_LAC + "=" + lac, null);
					if (l.getCount() > 0) {
						l.moveToFirst();
						location = l.getInt(l.getColumnIndex(TABLE_ID));
					} else {
						ContentValues cv = new ContentValues();
						cv.put(LOCATIONS_LAC, lac);
						location = (int) mDb.insert(TABLE_LOCATIONS, null, cv);
					}
					l.close();
				} else location = UNKNOWN_CID;
				ContentValues cv = new ContentValues();
				cv.put(CELLS_LOCATION, location);
				mDb.update(TABLE_CELLS, cv, TABLE_ID + "=" + c.getInt(c.getColumnIndex(TABLE_ID)), null);
			}
		}
		c.close();
		return inRange;
	}
	public Cursor fetchNetworks(int filter, String bssid, String cells) {
		return mDb.rawQuery("select " + TABLE_NETWORKS + "." + TABLE_ID + " as " + TABLE_ID + ", " + NETWORKS_SSID + ", " + NETWORKS_BSSID + ", "
				+ ((filter == FILTER_ALL) ?
						("case when " + NETWORKS_BSSID + "='" + bssid + "' then '" + getResources().getString(R.string.connected)
								+ "' else (case when " + TABLE_NETWORKS + "." + TABLE_ID + " in (select " + PAIRS_NETWORK
								+ " from " + TABLE_PAIRS + ", " + TABLE_CELLS + ", " + TABLE_LOCATIONS
								+ " where " + PAIRS_CELL + "=" + TABLE_CELLS + "." + TABLE_ID
								+ " and " + CELLS_LOCATION + "=" + TABLE_LOCATIONS + "." + TABLE_ID
								+ " and (" + cells + "))" + " then '" + getResources().getString(R.string.withinarea)
								+ "' else '" + getResources().getString(R.string.outofarea) + "' end) end")
								: "'" + (getResources().getString(filter == FILTER_CONNECTED ? R.string.connected : filter == FILTER_INRANGE ? R.string.withinarea : R.string.outofarea) + "'"))
								+ " as " + STATUS
								+ " from " + TABLE_NETWORKS
								+ (filter != FILTER_ALL ?
										" where "
										+ (filter == FILTER_CONNECTED ?
												NETWORKS_BSSID + "='" + bssid + "'"
												: TABLE_NETWORKS + "." + TABLE_ID + (filter == FILTER_OUTRANGE ? " NOT" : "") + " in (select " + PAIRS_NETWORK
												+ " from " + TABLE_PAIRS + ", " + TABLE_CELLS + ", " + TABLE_LOCATIONS
												+ " where " + PAIRS_CELL + "=" + TABLE_CELLS + "." + TABLE_ID
												+ " and " + CELLS_LOCATION + "=" + TABLE_LOCATIONS + "." + TABLE_ID
												+ " and (" + cells + "))")
												: " order by " + STATUS), null);
	}

	public Cursor fetchNetworkData(int network) {
		return mDb.rawQuery("select " + TABLE_PAIRS + "." + TABLE_ID + " as " + TABLE_ID + ", " + NETWORKS_SSID + ", " + NETWORKS_BSSID + ", " + CELLS_CID + ", " + LOCATIONS_LAC + ", " + PAIRS_RSSI_MIN + ", " + PAIRS_RSSI_MAX
				+ " from " + TABLE_PAIRS + ", " + TABLE_NETWORKS + ", " + TABLE_CELLS + ", " + TABLE_LOCATIONS
				+ " where " + PAIRS_NETWORK + "=" + TABLE_NETWORKS + "." + TABLE_ID
				+ " and " + PAIRS_CELL + "=" + TABLE_CELLS + "." + TABLE_ID
				+ " and " + CELLS_LOCATION + "=" + TABLE_LOCATIONS + "." + TABLE_ID
				+ " and " + PAIRS_NETWORK + "=" + network, null);
	}

	public Cursor fetchPairData(int pair) {
		return mDb.rawQuery("select " + TABLE_PAIRS + "." + TABLE_ID + " as " + TABLE_ID + ", " + NETWORKS_SSID + ", " + NETWORKS_BSSID + ", " + CELLS_CID + ", " + LOCATIONS_LAC + ", " + PAIRS_RSSI_MIN + ", " + PAIRS_RSSI_MAX
				+ " from " + TABLE_PAIRS + ", " + TABLE_NETWORKS + ", " + TABLE_CELLS + ", " + TABLE_LOCATIONS
				+ " where " + PAIRS_NETWORK + "=" + TABLE_NETWORKS + "." + TABLE_ID
				+ " and " + PAIRS_CELL + "=" + TABLE_CELLS + "." + TABLE_ID
				+ " and " + CELLS_LOCATION + "=" + TABLE_LOCATIONS + "." + TABLE_ID
				+ " and " + TABLE_PAIRS + "." + TABLE_ID + "=" + pair, null);
	}

	public Cursor fetchPairsByNetwork(int network) {
		return mDb.rawQuery("select " + TABLE_PAIRS + "." + TABLE_ID + " as " + TABLE_ID + ", " + CELLS_CID
				+ " from " + TABLE_PAIRS + ", " + TABLE_CELLS
				+ " where " + PAIRS_CELL + "=" + TABLE_CELLS + "." + TABLE_ID
				+ " and "+ PAIRS_NETWORK + "=" + network, null);
	}

	public Cursor fetchPairsByNetworkFilter(int filter, int network, int cid, String cells) {
		return mDb.rawQuery("select " + TABLE_PAIRS + "." + TABLE_ID + " as " + TABLE_ID + ", " + CELLS_CID + ", "
				+ "case when " + LOCATIONS_LAC + "=" + UNKNOWN_CID + " then '" + getResources().getString(R.string.unknown) + "' else " + LOCATIONS_LAC + " end as " + LOCATIONS_LAC + ", "
				+ "case when " + PAIRS_RSSI_MIN + "=" + UNKNOWN_RSSI + " then '" + getResources().getString(R.string.unknown) + "' else (" + PAIRS_RSSI_MIN + "||'" + getResources().getString(R.string.colon) + "'||" + PAIRS_RSSI_MAX + "||'" + getResources().getString(R.string.dbm) + "') end as " + PAIRS_RSSI_MIN + ", "
				+ ((filter == FILTER_ALL) ?
						("case when " + CELLS_CID + "='" + cid + "' then '" + getResources().getString(R.string.connected)
								+ "' else (case when " + TABLE_CELLS + "." + TABLE_ID + " in (select " + TABLE_CELLS + "." + TABLE_ID
								+ " from " + TABLE_PAIRS + ", " + TABLE_CELLS + ", " + TABLE_LOCATIONS
								+ " where " + PAIRS_CELL + "=" + TABLE_CELLS + "." + TABLE_ID
								+ " and " + CELLS_LOCATION + "=" + TABLE_LOCATIONS + "." + TABLE_ID
								+ " and " + PAIRS_NETWORK + "=" + network
								+ " and " + cells + ")" + " then '" + getResources().getString(R.string.withinarea)
								+ "' else '" + getResources().getString(R.string.outofarea) + "' end) end as ")
								: "'" + (getResources().getString(filter == FILTER_CONNECTED ? R.string.connected : filter == FILTER_INRANGE ? R.string.withinarea : R.string.outofarea) + "' as "))
								+ STATUS
								+ " from " + TABLE_PAIRS
								+ " left join " + TABLE_CELLS
								+ " on " + PAIRS_CELL + "=" + TABLE_CELLS + "." + TABLE_ID
								+ " left outer join " + TABLE_LOCATIONS
								+ " on " + CELLS_LOCATION + "=" + TABLE_LOCATIONS + "." + TABLE_ID
								+ " where "+ PAIRS_NETWORK + "=" + network
								+ (filter != FILTER_ALL ?
										" and "
										+ (filter == FILTER_CONNECTED ?
												CELLS_CID + "='" + cid + "'"
												: TABLE_CELLS + "." + TABLE_ID + (filter == FILTER_OUTRANGE ? " NOT" : "") + " in (select " + TABLE_CELLS + "." + TABLE_ID
												+ " from " + TABLE_PAIRS + ", " + TABLE_CELLS + ", " + TABLE_LOCATIONS
												+ " where " + PAIRS_CELL + "=" + TABLE_CELLS + "." + TABLE_ID
												+ " and " + CELLS_LOCATION + "=" + TABLE_LOCATIONS + "." + TABLE_ID
												+ " and " + PAIRS_NETWORK + "=" + network
												+ " and " + cells + ")")
												: " order by " + STATUS), null);
	}

	public void deleteNetwork(int network) {
		mDb.delete(TABLE_NETWORKS, TABLE_ID + "=" + network, null);
		mDb.delete(TABLE_PAIRS, PAIRS_NETWORK + "=" + network, null);
		cleanCellsLocations();
	}

	public void deletePair(int network, int pair) {
		// get paired cell and location
		mDb.delete(TABLE_PAIRS, TABLE_ID + "=" + pair, null);
		Cursor n = fetchPairsByNetwork(network);
		if (n.getCount() == 0) mDb.delete(TABLE_NETWORKS, TABLE_ID + "=" + network, null);
		n.close();
		cleanCellsLocations();
	}

	public void cleanCellsLocations() {
		Cursor c = mDb.rawQuery("select " + TABLE_ID + ", " + CELLS_LOCATION + " from " + TABLE_CELLS, null);
		if (c.getCount() > 0) {
			c.moveToFirst();
			while (!c.isAfterLast()) {
				int cell = c.getInt(c.getColumnIndex(TABLE_ID));
				Cursor p = mDb.rawQuery("select " + TABLE_ID + " from " + TABLE_PAIRS + " where " + PAIRS_CELL + "=" + cell, null);
				if (p.getCount() == 0) {
					mDb.delete(TABLE_CELLS, TABLE_ID + "=" + cell, null);
					int location = c.getInt(c.getColumnIndex(CELLS_LOCATION));
					Cursor l = mDb.rawQuery("select " + TABLE_ID + " from " + TABLE_CELLS + " where " + CELLS_LOCATION + "=" + location, null);
					if (l.getCount() == 0) mDb.delete(TABLE_LOCATIONS, TABLE_ID + "=" + location, null);
					l.close();
				}
				p.close();
				c.moveToNext();
			}
		}
		c.close();
	}

}
