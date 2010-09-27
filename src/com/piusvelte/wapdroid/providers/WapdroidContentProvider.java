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
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *  
 *  Bryan Emmanuel piusvelte@gmail.com
 */

package com.piusvelte.wapdroid.providers;

import static com.piusvelte.wapdroid.Wapdroid.TAG;
import static com.piusvelte.wapdroid.Wapdroid.AUTHORITY;

import java.util.HashMap;

import com.piusvelte.wapdroid.R;
import com.piusvelte.wapdroid.Wapdroid;
import com.piusvelte.wapdroid.R.string;
import com.piusvelte.wapdroid.Wapdroid.Cells;
import com.piusvelte.wapdroid.Wapdroid.Locations;
import com.piusvelte.wapdroid.Wapdroid.Networks;
import com.piusvelte.wapdroid.Wapdroid.Pairs;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.net.Uri;
import android.util.Log;

public class WapdroidContentProvider extends ContentProvider {
	private static final String DATABASE_NAME = "wapdroid";
	private static final int DATABASE_VERSION = 6;
	public static final String TABLE_NETWORKS = "networks";
	public static final String TABLE_CELLS = "cells";
	public static final String TABLE_PAIRS = "pairs";
	public static final String TABLE_LOCATIONS = "locations";
	private static final int NETWORKS = 1;
	private static final int CELLS = 2;
	private static final int PAIRS = 3;
	private static final int LOCATIONS = 4;
	private static HashMap<String, String> networksProjectionMap;
	private static HashMap<String, String> cellsProjectionMap;
	private static HashMap<String, String> pairsProjectionMap;
	private static HashMap<String, String> locationsProjectionMap;
	public static final int UNKNOWN_CID = -1;
	public static final int UNKNOWN_RSSI = 99;
	
	private static UriMatcher sUriMatcher;
	
	private static class DatabaseHelper extends SQLiteOpenHelper {

		public DatabaseHelper(Context context) {
			super(context, DATABASE_NAME, null, DATABASE_VERSION);
		}
		
		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL("create table if not exists " + TABLE_NETWORKS + " ("
					+ Networks._ID + " integer primary key autoincrement, "
					+ Networks.SSID + " text not null, "
					+ Networks.BSSID + " text not null);");
			db.execSQL("create table if not exists " + TABLE_CELLS + " ("
					+ Cells._ID + " integer primary key autoincrement, "
					+ Cells.CID + " integer, location integer);");
			db.execSQL("create table if not exists " + TABLE_PAIRS + " ("
					+ Pairs._ID + " integer primary key autoincrement, "
					+ Pairs.CELL + " integer, "
					+ Pairs.NETWORK + " integer, "
					+ Pairs.RSSI_MIN + " integer, "
					+ Pairs.RSSI_MAX + " integer);");
			db.execSQL("create table if not exists " + TABLE_LOCATIONS + " ("
					+ Locations._ID + " integer primary key autoincrement, "
					+ Locations.LAC + " integer);");
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			if (oldVersion < 2) {
				// add BSSID
				db.execSQL("drop table if exists " + TABLE_NETWORKS + "_bkp;");
				db.execSQL("create temporary table " + TABLE_NETWORKS + "_bkp as select * from " + TABLE_NETWORKS + ";");
				db.execSQL("drop table if exists " + TABLE_NETWORKS + ";");
				db.execSQL("create table if not exists " + TABLE_NETWORKS + " (_id  integer primary key autoincrement, "
						+ Networks.SSID + " text not null, "
						+ Networks.BSSID + " text not null);");
				db.execSQL("insert into " + TABLE_NETWORKS + " select " + Networks._ID + ", " + Networks.SSID + ", \"\"" + " from " + TABLE_NETWORKS + "_bkp;");
				db.execSQL("drop table if exists " + TABLE_NETWORKS + "_bkp;");
			}
			if (oldVersion < 3) {
				// add locations
				db.execSQL("create table if not exists " + TABLE_LOCATIONS + " (_id  integer primary key autoincrement, "
						+ Locations.LAC + " integer);");
				// first backup cells to create pairs
				db.execSQL("drop table if exists " + TABLE_CELLS + "_bkp;");
				db.execSQL("create temporary table " + TABLE_CELLS + "_bkp as select * from " + TABLE_CELLS + ";");
				// update cells, dropping network column, making unique
				db.execSQL("drop table if exists " + TABLE_CELLS + ";");
				db.execSQL("create table if not exists " + TABLE_CELLS + " (_id  integer primary key autoincrement, " + Cells.CID + " integer, location integer);");
				db.execSQL("insert into " + TABLE_CELLS + " (" + Cells.CID + ", " + Cells.LOCATION
						+ ") select " + Cells.CID + ", " + UNKNOWN_CID + " from " + TABLE_CELLS + "_bkp group by " + Cells.CID + ";");
				// create pairs
				db.execSQL("create table if not exists " + TABLE_PAIRS + " (_id  integer primary key autoincrement, cell integer, network integer, " + Pairs.RSSI_MIN + " integer, " + Pairs.RSSI_MAX + " integer);");
				db.execSQL("insert into " + TABLE_PAIRS
						+ " (" + Pairs.CELL + ", " + Pairs.NETWORK + ", " + Pairs.RSSI_MIN + ", " + Pairs.RSSI_MAX
						+ ") select " + TABLE_CELLS + "." + Pairs._ID + ", " + TABLE_CELLS + "_bkp." + Pairs.NETWORK + ", " + UNKNOWN_RSSI + ", " + UNKNOWN_RSSI
						+ " from " + TABLE_CELLS + "_bkp"
						+ " left join " + TABLE_CELLS + " on " + TABLE_CELLS + "_bkp." + Cells.CID + "=" + TABLE_CELLS + "." + Cells.CID + ";");
				db.execSQL("drop table if exists " + TABLE_CELLS + "_bkp;");	
			}
			if (oldVersion < 4) {
				// clean lac=0 locations
				Cursor locations = db.rawQuery("select " + Locations._ID + " from " + TABLE_LOCATIONS + " where " + Locations.LAC + "=0", null);
				if (locations.getCount() > 0) {
					locations.moveToFirst();
					int index = locations.getColumnIndex(Locations._ID);
					while (!locations.isAfterLast()) {
						int location = locations.getInt(index);
						// clean pairs
						db.execSQL("delete from " + TABLE_PAIRS + " where " + Pairs._ID + " in (select " + TABLE_PAIRS + "." + Pairs._ID + " as " + Pairs._ID + " from " + TABLE_PAIRS
								+ " left join " + TABLE_CELLS + " on " + Pairs.CELL + "=" + TABLE_CELLS + "." + Cells._ID
								+ " where " + Cells.LOCATION + "=" + location + ");");
						// clean cells
						db.execSQL("delete from " + TABLE_CELLS + " where " + Cells.LOCATION + "=" + location + ";");
						locations.moveToNext();
					}
					// clean locations
					db.execSQL("delete from " + TABLE_LOCATIONS + " where " + Locations.LAC + "=0;");
				}			
			}
			if (oldVersion < 5) {
				// fix bad rssi values
				db.execSQL("update " + TABLE_PAIRS + " set " + Pairs.RSSI_MIN + "=-1*" + Pairs.RSSI_MIN + " where " + Pairs.RSSI_MIN + " >0 and " + Pairs.RSSI_MIN + " !=" + UNKNOWN_RSSI + ";");
				db.execSQL("update " + TABLE_PAIRS + " set " + Pairs.RSSI_MAX + "=-1*" + Pairs.RSSI_MAX + " where " + Pairs.RSSI_MAX + " >0 and " + Pairs.RSSI_MAX + " !=" + UNKNOWN_RSSI + ";");			
			}
			if (oldVersion < 6) {
				// revert incorrect unknown rssi's
				db.execSQL("update " + TABLE_PAIRS + " set " + Pairs.RSSI_MIN + "=99," + Pairs.RSSI_MAX + "=99 where " + Pairs.RSSI_MAX + "<" + Pairs.RSSI_MIN + " and RSSI_max=-85;");			
			}
		}		
	}
	
	private DatabaseHelper mOpenHelper;

    @Override
    public boolean onCreate() {
        mOpenHelper = new DatabaseHelper(getContext());
        return true;
    }

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		switch (sUriMatcher.match(uri)) {
		case NETWORKS:
			break;
		case CELLS:
			break;
		case PAIRS:
			break;
		case LOCATIONS:
			break;		
		}
		return 0;
	}

	@Override
	public String getType(Uri uri) {
		switch (sUriMatcher.match(uri)) {
		
		}
		return null;
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		switch (sUriMatcher.match(uri)) {
		case NETWORKS:
			break;
		case CELLS:
			break;
		case PAIRS:
			break;
		case LOCATIONS:
			break;	
		}
		return null;
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {
		switch (sUriMatcher.match(uri)) {
		case NETWORKS:
			break;
		case CELLS:
			break;
		case PAIRS:
			break;
		case LOCATIONS:
			break;	
		}
		return null;
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection,
			String[] selectionArgs) {
		switch (sUriMatcher.match(uri)) {
		case NETWORKS:
			break;
		case CELLS:
			break;
		case PAIRS:
			break;
		case LOCATIONS:
			break;	
		}
		return 0;
	}
	
	static {
		sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
		sUriMatcher.addURI(AUTHORITY, TABLE_NETWORKS, NETWORKS);
		sUriMatcher.addURI(AUTHORITY, TABLE_CELLS, CELLS);
		sUriMatcher.addURI(AUTHORITY, TABLE_PAIRS, PAIRS);
		sUriMatcher.addURI(AUTHORITY, TABLE_LOCATIONS, LOCATIONS);
		
		networksProjectionMap = new HashMap<String, String>();
		networksProjectionMap.put(Networks._ID, Networks._ID);
		networksProjectionMap.put(Networks.SSID, Networks.SSID);
		networksProjectionMap.put(Networks.BSSID, Networks.BSSID);
		
		cellsProjectionMap = new HashMap<String, String>();
		cellsProjectionMap.put(Cells._ID, Cells._ID);
		cellsProjectionMap.put(Cells.CID, Cells.CID);
		cellsProjectionMap.put(Cells.LOCATION, Cells.LOCATION);
		
		pairsProjectionMap = new HashMap<String, String>();
		pairsProjectionMap.put(Pairs._ID, Pairs._ID);
		pairsProjectionMap.put(Pairs.CELL, Pairs.CELL);
		pairsProjectionMap.put(Pairs.NETWORK, Pairs.NETWORK);
		pairsProjectionMap.put(Pairs.RSSI_MIN, Pairs.RSSI_MIN);
		pairsProjectionMap.put(Pairs.RSSI_MAX, Pairs.RSSI_MAX);
		
		locationsProjectionMap = new HashMap<String, String>();
		locationsProjectionMap.put(Locations._ID, Locations._ID);
		locationsProjectionMap.put(Locations.LAC, Locations.LAC);
	}
	
	
	
	
	static final String STATUS = "status";
	static final int FILTER_ALL = 0;
	static final int FILTER_INRANGE = 1;
	static final int FILTER_OUTRANGE = 2;
	static final int FILTER_CONNECTED = 3;
	static String connected;
	static String withinarea;
	static String outofarea;
	static String unknown;
	static String colon;
	static String dbm;
	
	public int xfetchNetwork(String ssid, String bssid) {
		int network = UNKNOWN_CID;
		Cursor c = mDatabase.rawQuery("select " + TABLE_ID + ", " + NETWORKS_SSID + ", " + NETWORKS_BSSID + " from " + TABLE_NETWORKS + " where " + NETWORKS_SSID + "=\"" + ssid + "\" and (" + NETWORKS_BSSID + "=\"" + bssid + "\" or " + NETWORKS_BSSID + "=\"\")", null);
		if (c.getCount() > 0) {
			// ssid matches, only concerned if bssid is empty
			c.moveToFirst();
			network = c.getInt(c.getColumnIndex(TABLE_ID));
			if (c.getString(c.getColumnIndex(NETWORKS_BSSID)).equals("")) mDatabase.execSQL("update " + TABLE_NETWORKS + " set " + NETWORKS_BSSID + "='" + bssid + "' where " + TABLE_ID + "=" + network + ";");
		} else {
			ContentValues cv = new ContentValues();
			cv.put(NETWORKS_SSID, ssid);
			cv.put(NETWORKS_BSSID, bssid);
			network = (int) mDatabase.insert(TABLE_NETWORKS, null, cv);
		}
		c.close();
		return network;
	}

	public Cursor xfetchNetworks(int filter, String bssid, String cells) {
		return mDatabase.rawQuery("select " + TABLE_NETWORKS + "." + TABLE_ID + " as " + TABLE_ID + ", " + NETWORKS_SSID + ", " + NETWORKS_BSSID + ", "
				+ ((filter == FILTER_ALL) ?
						("case when " + NETWORKS_BSSID + "='" + bssid + "' then '" + connected
								+ "' else (case when " + TABLE_NETWORKS + "." + TABLE_ID + " in (select " + PAIRS_NETWORK
								+ " from " + TABLE_PAIRS + ", " + TABLE_CELLS + ", " + TABLE_LOCATIONS
								+ " where " + PAIRS_CELL + "=" + TABLE_CELLS + "." + TABLE_ID
								+ " and " + CELLS_LOCATION + "=" + TABLE_LOCATIONS + "." + TABLE_ID
								+ cells + ")" + " then '" + withinarea
								+ "' else '" + outofarea + "' end) end")
								: "'" + (filter == FILTER_CONNECTED ? connected : filter == FILTER_INRANGE ? withinarea : outofarea) + "'")
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
												+ cells + ")")
												: " order by " + STATUS), null);		
	}

	public Cursor xfetchPairsByNetworkFilter(int filter, int network, int cid, String cells) {
		return mDatabase.rawQuery("select "
				+ TABLE_PAIRS + "." + TABLE_ID + " as " + TABLE_ID + ", "
				+ CELLS_CID + ", "
				+ "case when " + LOCATIONS_LAC + "=" + UNKNOWN_CID + " then '" + unknown + "' else " + LOCATIONS_LAC + " end as " + LOCATIONS_LAC + ", "
				+ "case when " + PAIRS_RSSI_MIN + "=" + UNKNOWN_RSSI + " or " + PAIRS_RSSI_MAX + "=" + UNKNOWN_RSSI + " then '" + unknown + "' else (" + PAIRS_RSSI_MIN + "||'" + colon + "'||" + PAIRS_RSSI_MAX + "||'" + dbm + "') end as " + PAIRS_RSSI_MIN + ", "
				+ ((filter == FILTER_ALL) ?
						("case when " + CELLS_CID + "='" + cid + "' then '" + connected
								+ "' else (case when " + TABLE_CELLS + "." + TABLE_ID + " in (select "
								+ TABLE_CELLS + "." + TABLE_ID
								+ " from " + TABLE_PAIRS + ", " + TABLE_CELLS + ", " + TABLE_LOCATIONS
								+ " where " + PAIRS_CELL + "=" + TABLE_CELLS + "." + TABLE_ID
								+ " and " + CELLS_LOCATION + "=" + TABLE_LOCATIONS + "." + TABLE_ID
								+ " and " + PAIRS_NETWORK + "=" + network
								+ cells + ")" + " then '" + withinarea
								+ "' else '" + outofarea + "' end) end as ")
								: "'" + ((filter == FILTER_CONNECTED ? connected : filter == FILTER_INRANGE ? withinarea : outofarea) + "' as ")) + STATUS
								+ " from " + TABLE_PAIRS
								+ " left join " + TABLE_CELLS
								+ " on " + PAIRS_CELL + "=" + TABLE_CELLS + "." + TABLE_ID
								+ " left outer join " + TABLE_LOCATIONS
								+ " on " + CELLS_LOCATION + "=" + TABLE_LOCATIONS + "." + TABLE_ID
								+ " where "+ PAIRS_NETWORK + "=" + network
								+ (filter != FILTER_ALL ?
										" and " + (filter == FILTER_CONNECTED ?
												CELLS_CID + "='" + cid + "'"
												: TABLE_CELLS + "." + TABLE_ID + (filter == FILTER_OUTRANGE ? " NOT" : "") + " in (select "
												+ TABLE_CELLS + "." + TABLE_ID
												+ " from " + TABLE_PAIRS + ", " + TABLE_CELLS + ", " + TABLE_LOCATIONS
												+ " where " + PAIRS_CELL + "=" + TABLE_CELLS + "." + TABLE_ID
												+ " and " + CELLS_LOCATION + "=" + TABLE_LOCATIONS + "." + TABLE_ID
												+ " and " + PAIRS_NETWORK + "=" + network
												+ cells + ")")
												: " order by " + STATUS), null);
	}

	public boolean xcellInRange(int cid, int lac, int rssi) {
		Cursor c = mDatabase.rawQuery("select " + TABLE_CELLS + "." + TABLE_ID + " as " + TABLE_ID + ", " + CELLS_LOCATION + (rssi != UNKNOWN_RSSI ? ", (select min(" + PAIRS_RSSI_MIN + ") from " + TABLE_PAIRS + " where " + PAIRS_CELL + "=" + TABLE_CELLS + "." + TABLE_ID + ") as " + PAIRS_RSSI_MIN + ", (select max(" + PAIRS_RSSI_MAX + ") from " + TABLE_PAIRS + " where " + PAIRS_CELL + "=" + TABLE_CELLS + "." + TABLE_ID + ") as " + PAIRS_RSSI_MAX : "") + " from " + TABLE_CELLS + " left outer join " + TABLE_LOCATIONS + " on " + CELLS_LOCATION + "=" + TABLE_LOCATIONS + "." + TABLE_ID + " where "+ CELLS_CID + "=" + cid + " and (" + LOCATIONS_LAC + "=" + lac + " or " + CELLS_LOCATION + "=" + UNKNOWN_CID + ")"
				+ (rssi == UNKNOWN_RSSI ? "" : " and (((" + PAIRS_RSSI_MIN + "=" + UNKNOWN_RSSI + ") or (" + PAIRS_RSSI_MIN + "<=" + rssi + ")) and (" + PAIRS_RSSI_MAX + ">=" + rssi + "))"), null);
		boolean inRange = (c.getCount() > 0);
		if (inRange && (lac > 0)) {
			// check LAC, as this is a new column
			c.moveToFirst();
			if (c.isNull(c.getColumnIndex(CELLS_LOCATION))) {
				// select or insert location
				int location;
				if (lac > 0) {
					Cursor l = mDatabase.rawQuery("select " + TABLE_ID + " from " + TABLE_LOCATIONS + " where " + LOCATIONS_LAC + "=" + lac, null);
					if (l.getCount() > 0) {
						l.moveToFirst();
						location = l.getInt(l.getColumnIndex(TABLE_ID));
					} else {
						ContentValues cv = new ContentValues();
						cv.put(LOCATIONS_LAC, lac);
						location = (int) mDatabase.insert(TABLE_LOCATIONS, null, cv);
					}
					l.close();
				} else location = UNKNOWN_CID;
				mDatabase.execSQL("update " + TABLE_CELLS + " set " + CELLS_LOCATION + "=" + location + " where " + TABLE_ID + "=" + c.getInt(c.getColumnIndex(TABLE_ID)) + ";");
			}
		}
		c.close();
		return inRange;		
	}

	public void xcreatePair(int cid, int lac, int network, int rssi) {
		int cell, pair, location;
		// select or insert location
		if (lac > 0) {
			Cursor c = mDatabase.rawQuery("select " + TABLE_ID + " from " + TABLE_LOCATIONS + " where " + LOCATIONS_LAC + "=" + lac, null);
			if (c.getCount() > 0) {
				c.moveToFirst();
				location = c.getInt(c.getColumnIndex(TABLE_ID));
			} else {
				ContentValues cv = new ContentValues();
				cv.put(LOCATIONS_LAC, lac);
				location = (int) mDatabase.insert(TABLE_LOCATIONS, null, cv);
			}
			c.close();
		} else location = UNKNOWN_CID;
		// if location==-1, then match only on cid, otherwise match on location or -1
		// select or insert cell
		Cursor c = mDatabase.rawQuery("select " + TABLE_ID + ", " + CELLS_LOCATION + " from " + TABLE_CELLS + " where " + CELLS_CID + "=" + cid + (location == UNKNOWN_CID ? "" : " and (" + CELLS_LOCATION + "=" + UNKNOWN_CID + " or " + CELLS_LOCATION + "=" + location + ")"), null);
		if (c.getCount() > 0) {
			c.moveToFirst();
			cell = c.getInt(c.getColumnIndex(TABLE_ID));
			if ((location != UNKNOWN_CID) && (c.getInt(c.getColumnIndex(CELLS_LOCATION)) == UNKNOWN_CID)) mDatabase.execSQL("update " + TABLE_CELLS + " set " + CELLS_LOCATION + "=" + location + " where " + TABLE_ID + "=" + cell + ";");
		} else {
			ContentValues cv = new ContentValues();
			cv.put(CELLS_CID, cid);
			cv.put(CELLS_LOCATION, location);
			cell = (int) mDatabase.insert(TABLE_CELLS, null, cv);
		}
		c.close();
		// select and update or insert pair
		c = mDatabase.rawQuery("select " + TABLE_ID + ", " + PAIRS_RSSI_MIN + ", " + PAIRS_RSSI_MAX + " from " + TABLE_PAIRS + " where " + PAIRS_CELL + "=" + cell + " and " + PAIRS_NETWORK + "=" + network, null);
		if (c.getCount() > 0) {
			if (rssi != UNKNOWN_RSSI) {
				c.moveToFirst();
				pair = c.getInt(c.getColumnIndex(TABLE_ID));
				int rssi_min = c.getInt(c.getColumnIndex(PAIRS_RSSI_MIN));
				int rssi_max = c.getInt(c.getColumnIndex(PAIRS_RSSI_MAX));
				if (rssi_min > rssi) mDatabase.execSQL("update " + TABLE_PAIRS + " set " + PAIRS_RSSI_MIN + "=" + rssi + " where " + TABLE_ID + "=" + pair + ";");
				else if ((rssi_max == UNKNOWN_RSSI) || (rssi_max < rssi)) mDatabase.execSQL("update " + TABLE_PAIRS + " set " + PAIRS_RSSI_MAX + "=" + rssi + " where " + TABLE_ID + "=" + pair + ";");
			}
		} else mDatabase.execSQL("insert into " + TABLE_PAIRS + " (" + PAIRS_CELL + "," + PAIRS_NETWORK + "," + PAIRS_RSSI_MIN + "," + PAIRS_RSSI_MAX + ") values (" + cell + "," + network + "," + rssi + "," + rssi + ");");
		c.close();		
	}

	public void xcleanCellsLocations() {
		Cursor c = mDatabase.rawQuery("select " + TABLE_ID + ", " + CELLS_LOCATION + " from " + TABLE_CELLS, null);
		if (c.getCount() > 0) {
			c.moveToFirst();
			int[] index = {c.getColumnIndex(TABLE_ID), c.getColumnIndex(CELLS_LOCATION)};
			while (!c.isAfterLast()) {
				int cell = c.getInt(index[0]);
				Cursor p = mDatabase.rawQuery("select " + TABLE_ID + " from " + TABLE_PAIRS + " where " + PAIRS_CELL + "=" + cell, null);
				if (p.getCount() == 0) {
					mDatabase.delete(TABLE_CELLS, TABLE_ID + "=" + cell, null);
					int location = c.getInt(index[1]);
					Cursor l = mDatabase.rawQuery("select " + TABLE_ID + " from " + TABLE_CELLS + " where " + CELLS_LOCATION + "=" + location, null);
					if (l.getCount() == 0) mDatabase.delete(TABLE_LOCATIONS, TABLE_ID + "=" + location, null);
					l.close();
				}
				p.close();
				c.moveToNext();
			}
		}
		c.close();
	}

	public Cursor xfetchData(String column, int value) {
		return mDatabase.rawQuery("select " + TABLE_PAIRS + "." + TABLE_ID + " as " + TABLE_ID + ", " + NETWORKS_SSID + ", " + NETWORKS_BSSID + ", " + CELLS_CID + ", " + LOCATIONS_LAC + ", " + PAIRS_RSSI_MIN + ", " + PAIRS_RSSI_MAX
				+ " from " + TABLE_PAIRS + ", " + TABLE_NETWORKS + ", " + TABLE_CELLS + ", " + TABLE_LOCATIONS
				+ " where " + PAIRS_NETWORK + "=" + TABLE_NETWORKS + "." + TABLE_ID
				+ " and " + PAIRS_CELL + "=" + TABLE_CELLS + "." + TABLE_ID
				+ " and " + CELLS_LOCATION + "=" + TABLE_LOCATIONS + "." + TABLE_ID
				+ " and " + column + "=" + value, null);
	}

	public void xdeleteNetwork(int network) {
		mDatabase.delete(TABLE_NETWORKS, TABLE_ID + "=" + network, null);
		mDatabase.delete(TABLE_PAIRS, PAIRS_NETWORK + "=" + network, null);
		cleanCellsLocations();
	}

	public void xdeletePair(int network, int pair) {
		mDatabase.delete(TABLE_PAIRS, TABLE_ID + "=" + pair, null);
		Cursor n = mDatabase.rawQuery("select " + TABLE_PAIRS + "." + TABLE_ID + " as " + TABLE_ID + ", " + CELLS_CID
				+ " from " + TABLE_PAIRS + ", " + TABLE_CELLS
				+ " where " + PAIRS_CELL + "=" + TABLE_CELLS + "." + TABLE_ID
				+ " and "+ PAIRS_NETWORK + "=" + network, null);
		if (n.getCount() == 0) mDatabase.delete(TABLE_NETWORKS, TABLE_ID + "=" + network, null);
		n.close();
		cleanCellsLocations();		
	}

}
