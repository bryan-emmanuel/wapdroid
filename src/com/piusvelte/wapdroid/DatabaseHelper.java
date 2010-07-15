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

import static com.piusvelte.wapdroid.WapdroidDbAdapter.CREATE_CELLS;
import static com.piusvelte.wapdroid.WapdroidDbAdapter.CREATE_LOCATIONS;
import static com.piusvelte.wapdroid.WapdroidDbAdapter.CREATE_NETWORKS;
import static com.piusvelte.wapdroid.WapdroidDbAdapter.CREATE_PAIRS;
import static com.piusvelte.wapdroid.WapdroidDbAdapter.TABLE_NETWORKS;
import static com.piusvelte.wapdroid.WapdroidDbAdapter.TABLE_ID;
import static com.piusvelte.wapdroid.WapdroidDbAdapter.NETWORKS_SSID;
import static com.piusvelte.wapdroid.WapdroidDbAdapter.TABLE_CELLS;
import static com.piusvelte.wapdroid.WapdroidDbAdapter.CELLS_CID;
import static com.piusvelte.wapdroid.WapdroidDbAdapter.CELLS_LOCATION;
import static com.piusvelte.wapdroid.WapdroidDbAdapter.UNKNOWN_CID;
import static com.piusvelte.wapdroid.WapdroidDbAdapter.UNKNOWN_RSSI;
import static com.piusvelte.wapdroid.WapdroidDbAdapter.TABLE_PAIRS;
import static com.piusvelte.wapdroid.WapdroidDbAdapter.PAIRS_CELL;
import static com.piusvelte.wapdroid.WapdroidDbAdapter.PAIRS_NETWORK;
import static com.piusvelte.wapdroid.WapdroidDbAdapter.PAIRS_RSSI_MAX;
import static com.piusvelte.wapdroid.WapdroidDbAdapter.PAIRS_RSSI_MIN;
import static com.piusvelte.wapdroid.WapdroidDbAdapter.TABLE_LOCATIONS;
import static com.piusvelte.wapdroid.WapdroidDbAdapter.LOCATIONS_LAC;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {
	public static final String DATABASE_NAME = "wapdroid";
	private static final int DATABASE_VERSION = 4;
	private static final String DROP = "drop table if exists ";

	DatabaseHelper(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL(CREATE_NETWORKS);
		db.execSQL(CREATE_CELLS);
		db.execSQL(CREATE_PAIRS);
		db.execSQL(CREATE_LOCATIONS);
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		if (oldVersion < 2) {
			// add BSSID
			db.execSQL(DROP + TABLE_NETWORKS + "_bkp;");
			db.execSQL("create temporary table " + TABLE_NETWORKS + "_bkp as select * from " + TABLE_NETWORKS + ";");
			db.execSQL(DROP + TABLE_NETWORKS + ";");
			db.execSQL(CREATE_NETWORKS);
			db.execSQL("insert into " + TABLE_NETWORKS + " select "
					+ TABLE_ID + ", " + NETWORKS_SSID + ", \"\""
					+ " from " + TABLE_NETWORKS + "_bkp;");
			db.execSQL(DROP + TABLE_NETWORKS + "_bkp;");
		}
		if (oldVersion < 3) {
			// add locations
			db.execSQL(CREATE_LOCATIONS);
			// first backup cells to create pairs
			db.execSQL(DROP + TABLE_CELLS + "_bkp;");
			db.execSQL("create temporary table " + TABLE_CELLS + "_bkp as select * from " + TABLE_CELLS + ";");
			// update cells, dropping network column, making unique
			db.execSQL(DROP + TABLE_CELLS + ";");
			db.execSQL(CREATE_CELLS);
			db.execSQL("insert into " + TABLE_CELLS + " (" + CELLS_CID + ", " + CELLS_LOCATION
					+ ") select " + CELLS_CID + ", " + UNKNOWN_CID + " from " + TABLE_CELLS + "_bkp group by " + CELLS_CID + ";");
			// create pairs
			db.execSQL(CREATE_PAIRS);
			db.execSQL("insert into " + TABLE_PAIRS
					+ " (" + PAIRS_CELL + ", " + PAIRS_NETWORK + ", " + PAIRS_RSSI_MIN + ", " + PAIRS_RSSI_MAX
					+ ") select " + TABLE_CELLS + "." + TABLE_ID + ", " + TABLE_CELLS + "_bkp." + PAIRS_NETWORK + ", " + UNKNOWN_RSSI + ", " + UNKNOWN_RSSI
					+ " from " + TABLE_CELLS + "_bkp"
					+ " left join " + TABLE_CELLS + " on " + TABLE_CELLS + "_bkp." + CELLS_CID + "=" + TABLE_CELLS + "." + CELLS_CID + ";");
			db.execSQL(DROP + TABLE_CELLS + "_bkp;");
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
	}
}
