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

package com.piusvelte.wapdroid;

import static com.piusvelte.wapdroid.WapdroidService.FILTER_ALL;
import static com.piusvelte.wapdroid.WapdroidService.FILTER_CONNECTED;
import static com.piusvelte.wapdroid.WapdroidService.FILTER_INRANGE;
import static com.piusvelte.wapdroid.WapdroidService.FILTER_OUTRANGE;
import static com.piusvelte.wapdroid.WapdroidService.STATUS;
import static com.piusvelte.wapdroid.WapdroidService.UNKNOWN_CID;
import static com.piusvelte.wapdroid.WapdroidService.UNKNOWN_RSSI;
import static com.piusvelte.wapdroid.WapdroidService.TABLE_ID;
import static com.piusvelte.wapdroid.WapdroidService.TABLE_NETWORKS;
import static com.piusvelte.wapdroid.WapdroidService.NETWORKS_SSID;
import static com.piusvelte.wapdroid.WapdroidService.NETWORKS_BSSID;
import static com.piusvelte.wapdroid.WapdroidService.TABLE_CELLS;
import static com.piusvelte.wapdroid.WapdroidService.CELLS_CID;
import static com.piusvelte.wapdroid.WapdroidService.TABLE_LOCATIONS;
import static com.piusvelte.wapdroid.WapdroidService.LOCATIONS_LAC;
import static com.piusvelte.wapdroid.WapdroidService.TABLE_PAIRS;
import static com.piusvelte.wapdroid.WapdroidService.PAIRS_CELL;
import static com.piusvelte.wapdroid.WapdroidService.PAIRS_NETWORK;
import static com.piusvelte.wapdroid.WapdroidService.CELLS_LOCATION;
import static com.piusvelte.wapdroid.WapdroidService.PAIRS_RSSI_MIN;
import static com.piusvelte.wapdroid.WapdroidService.PAIRS_RSSI_MAX;
import static com.piusvelte.wapdroid.WapdroidService.TAG;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

class DatabaseAdapter {
	private static final String DATABASE_NAME = "wapdroid";
	private static final int DATABASE_VERSION = 6;
	static SQLiteOpenHelper mDatabaseHelper;
	static SQLiteDatabase mDatabase;
	static String connected;
	static String withinarea;
	static String outofarea;
	static String unknown;
	static String colon;
	static String dbm;
	public DatabaseAdapter(Context context) {
		connected = context.getResources().getString(R.string.connected);
		withinarea = context.getResources().getString(R.string.withinarea);
		outofarea = context.getResources().getString(R.string.outofarea);
		unknown = context.getResources().getString(R.string.unknown);
		colon = context.getResources().getString(R.string.colon);
		dbm = context.getResources().getString(R.string.dbm);
		mDatabaseHelper = new SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
			private static final String create = "create table if not exists ";
			private static final String createTemp =  "create temporary table ";
			private static final String drop = "drop table if exists ";
			
			@Override
			public void onCreate(SQLiteDatabase db) {
				db.execSQL(create + TABLE_NETWORKS + " (_id  integer primary key autoincrement, " + NETWORKS_SSID + " text not null, " + NETWORKS_BSSID + " text not null);");
				db.execSQL(create + TABLE_CELLS + " (_id  integer primary key autoincrement, " + CELLS_CID + " integer, location integer);");
				db.execSQL(create + TABLE_PAIRS + " (_id  integer primary key autoincrement, cell integer, network integer, " + PAIRS_RSSI_MIN + " integer, " + PAIRS_RSSI_MAX + " integer);");
				db.execSQL(create + TABLE_LOCATIONS + " (_id  integer primary key autoincrement, " + LOCATIONS_LAC + " integer);");
			}

			@Override
			public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
				if (oldVersion < 2) {
					// add BSSID
					db.execSQL(drop + TABLE_NETWORKS + "_bkp;");
					db.execSQL(createTemp + TABLE_NETWORKS + "_bkp as select * from " + TABLE_NETWORKS + ";");
					db.execSQL(drop + TABLE_NETWORKS + ";");
					db.execSQL(create + TABLE_NETWORKS + " (_id  integer primary key autoincrement, " + NETWORKS_SSID + " text not null, " + NETWORKS_BSSID + " text not null);");
					db.execSQL("insert into " + TABLE_NETWORKS + " select " + TABLE_ID + ", " + NETWORKS_SSID + ", \"\"" + " from " + TABLE_NETWORKS + "_bkp;");
					db.execSQL(drop + TABLE_NETWORKS + "_bkp;");
				}
				if (oldVersion < 3) {
					// add locations
					db.execSQL(create + TABLE_LOCATIONS + " (_id  integer primary key autoincrement, " + LOCATIONS_LAC + " integer);");
					// first backup cells to create pairs
					db.execSQL(drop + TABLE_CELLS + "_bkp;");
					db.execSQL(createTemp + TABLE_CELLS + "_bkp as select * from " + TABLE_CELLS + ";");
					// update cells, dropping network column, making unique
					db.execSQL(drop + TABLE_CELLS + ";");
					db.execSQL(create + TABLE_CELLS + " (_id  integer primary key autoincrement, " + CELLS_CID + " integer, location integer);");
					db.execSQL("insert into " + TABLE_CELLS + " (" + CELLS_CID + ", " + CELLS_LOCATION
							+ ") select " + CELLS_CID + ", " + UNKNOWN_CID + " from " + TABLE_CELLS + "_bkp group by " + CELLS_CID + ";");
					// create pairs
					db.execSQL(create + TABLE_PAIRS + " (_id  integer primary key autoincrement, cell integer, network integer, " + PAIRS_RSSI_MIN + " integer, " + PAIRS_RSSI_MAX + " integer);");
					db.execSQL("insert into " + TABLE_PAIRS
							+ " (" + PAIRS_CELL + ", " + PAIRS_NETWORK + ", " + PAIRS_RSSI_MIN + ", " + PAIRS_RSSI_MAX
							+ ") select " + TABLE_CELLS + "." + TABLE_ID + ", " + TABLE_CELLS + "_bkp." + PAIRS_NETWORK + ", " + UNKNOWN_RSSI + ", " + UNKNOWN_RSSI
							+ " from " + TABLE_CELLS + "_bkp"
							+ " left join " + TABLE_CELLS + " on " + TABLE_CELLS + "_bkp." + CELLS_CID + "=" + TABLE_CELLS + "." + CELLS_CID + ";");
					db.execSQL(drop + TABLE_CELLS + "_bkp;");			
				}
				if (oldVersion < 4) {
					// clean lac=0 locations
					Cursor locations = db.rawQuery("select " + TABLE_ID + " from " + TABLE_LOCATIONS + " where " + LOCATIONS_LAC + "=0", null);
					if (locations.getCount() > 0) {
						locations.moveToFirst();
						while (!locations.isAfterLast()) {
							int location = locations.getInt(locations.getColumnIndex(TABLE_ID));
							// clean pairs
							db.execSQL("delete from " + TABLE_PAIRS + " where " + TABLE_ID + " in (select " + TABLE_PAIRS + "." + TABLE_ID + " as " + TABLE_ID + " from " + TABLE_PAIRS
									+ " left join " + TABLE_CELLS + " on " + PAIRS_CELL + "=" + TABLE_CELLS + "." + TABLE_ID
									+ " where " + CELLS_LOCATION + "=" + location + ");");
							// clean cells
							db.execSQL("delete from " + TABLE_CELLS + " where " + CELLS_LOCATION + "=" + location + ";");
							locations.moveToNext();
						}
						// clean locations
						db.execSQL("delete from " + TABLE_LOCATIONS + " where " + LOCATIONS_LAC + "=0;");
					}			
				}
				if (oldVersion < 5) {
					// fix bad rssi values
					db.execSQL("update pairs set " + PAIRS_RSSI_MIN + "=-1*" + PAIRS_RSSI_MIN + " where " + PAIRS_RSSI_MIN + " >0 and " + PAIRS_RSSI_MIN + " !=" + UNKNOWN_RSSI + ";");
					db.execSQL("update pairs set " + PAIRS_RSSI_MAX + "=-1*" + PAIRS_RSSI_MAX + " where " + PAIRS_RSSI_MAX + " >0 and " + PAIRS_RSSI_MAX + " !=" + UNKNOWN_RSSI + ";");			
				}
				if (oldVersion < 6) {
					// revert incorrect unknown rssi's
					db.execSQL("update pairs set " + PAIRS_RSSI_MIN + "=99," + PAIRS_RSSI_MAX + "=99 where " + PAIRS_RSSI_MAX + "<" + PAIRS_RSSI_MIN + " and RSSI_max=-85;");			
				}
			}
		};
		try {
			mDatabase = mDatabaseHelper.getWritableDatabase();
		} catch (SQLException se) {
			Log.e(TAG,"unexpected " + se);
		}
	}
	
	void open() {
		try {
			mDatabase = mDatabaseHelper.getWritableDatabase();
		} catch (SQLException se) {
			Log.e(TAG,"unexpected " + se);
			mDatabase = null;
		}
	}
	
	void close() {
		if (mDatabase.isOpen()) mDatabase.close();	
	}
	
	void closeHelper() {
		mDatabaseHelper.close();
	}

	int fetchNetwork(String ssid, String bssid) {
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
	
	Cursor fetchNetworks(int filter, String bssid, String cells) {
		return mDatabase.rawQuery("select " + TABLE_NETWORKS + "." + TABLE_ID + " as " + TABLE_ID + ", " + NETWORKS_SSID + ", " + NETWORKS_BSSID + ", "
			+ ((filter == FILTER_ALL) ?
					("case when " + NETWORKS_BSSID + "='" + bssid + "' then '" + connected
							+ "' else (case when " + TABLE_NETWORKS + "." + TABLE_ID + " in (select " + PAIRS_NETWORK
							+ " from " + TABLE_PAIRS + ", " + TABLE_CELLS + ", " + TABLE_LOCATIONS
							+ " where " + PAIRS_CELL + "=" + TABLE_CELLS + "." + TABLE_ID
							+ " and " + CELLS_LOCATION + "=" + TABLE_LOCATIONS + "." + TABLE_ID
							+ " and (" + cells + "))" + " then '" + withinarea
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
											+ " and (" + cells + "))")
											: " order by " + STATUS), null);		
	}
	
	Cursor fetchPairsByNetworkFilter(int filter, int network, int cid, String cells) {
		return mDatabase.rawQuery("select " + TABLE_PAIRS + "." + TABLE_ID + " as " + TABLE_ID + ", " + CELLS_CID + ", "
				+ "case when " + LOCATIONS_LAC + "=" + UNKNOWN_CID + " then '" + unknown + "' else " + LOCATIONS_LAC + " end as " + LOCATIONS_LAC + ", "
				+ "case when " + PAIRS_RSSI_MIN + "=" + UNKNOWN_RSSI + " then '" + unknown + "' else (" + PAIRS_RSSI_MIN + "||'" + colon + "'||" + PAIRS_RSSI_MAX + "||'" + dbm + "') end as " + PAIRS_RSSI_MIN + ", "
				+ ((filter == FILTER_ALL) ?
						("case when " + CELLS_CID + "='" + cid + "' then '" + connected
								+ "' else (case when " + TABLE_CELLS + "." + TABLE_ID + " in (select " + TABLE_CELLS + "." + TABLE_ID
								+ " from " + TABLE_PAIRS + ", " + TABLE_CELLS + ", " + TABLE_LOCATIONS
								+ " where " + PAIRS_CELL + "=" + TABLE_CELLS + "." + TABLE_ID
								+ " and " + CELLS_LOCATION + "=" + TABLE_LOCATIONS + "." + TABLE_ID
								+ " and " + PAIRS_NETWORK + "=" + network
								+ " and " + cells + ")" + " then '" + withinarea
								+ "' else '" + outofarea + "' end) end as ")
								: "'" + ((filter == FILTER_CONNECTED ? connected : filter == FILTER_INRANGE ? withinarea : outofarea) + "' as "))
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
	
	boolean cellInRange(int cid, int lac, int rssi) {
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
	
	void createPair(int cid, int lac, int network, int rssi) {
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
	
	public void cleanCellsLocations() {
		Cursor c = mDatabase.rawQuery("select " + TABLE_ID + ", " + CELLS_LOCATION + " from " + TABLE_CELLS, null);
		if (c.getCount() > 0) {
			c.moveToFirst();
			while (!c.isAfterLast()) {
				int cell = c.getInt(c.getColumnIndex(TABLE_ID));
				Cursor p = mDatabase.rawQuery("select " + TABLE_ID + " from " + TABLE_PAIRS + " where " + PAIRS_CELL + "=" + cell, null);
				if (p.getCount() == 0) {
					mDatabase.delete(TABLE_CELLS, TABLE_ID + "=" + cell, null);
					int location = c.getInt(c.getColumnIndex(CELLS_LOCATION));
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
	
	public Cursor fetchData(String column, int value) {
		return mDatabase.rawQuery("select " + TABLE_PAIRS + "." + TABLE_ID + " as " + TABLE_ID + ", " + NETWORKS_SSID + ", " + NETWORKS_BSSID + ", " + CELLS_CID + ", " + LOCATIONS_LAC + ", " + PAIRS_RSSI_MIN + ", " + PAIRS_RSSI_MAX
				+ " from " + TABLE_PAIRS + ", " + TABLE_NETWORKS + ", " + TABLE_CELLS + ", " + TABLE_LOCATIONS
				+ " where " + PAIRS_NETWORK + "=" + TABLE_NETWORKS + "." + TABLE_ID
				+ " and " + PAIRS_CELL + "=" + TABLE_CELLS + "." + TABLE_ID
				+ " and " + CELLS_LOCATION + "=" + TABLE_LOCATIONS + "." + TABLE_ID
				+ " and " + column + "=" + value, null);
	}
	
	public void deleteNetwork(int network) {
		mDatabase.delete(TABLE_NETWORKS, TABLE_ID + "=" + network, null);
		mDatabase.delete(TABLE_PAIRS, PAIRS_NETWORK + "=" + network, null);
		cleanCellsLocations();
	}
	
	public void deletePair(int network, int pair) {
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
