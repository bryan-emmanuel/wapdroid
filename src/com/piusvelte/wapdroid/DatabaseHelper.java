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

import static com.piusvelte.wapdroid.App.UNKNOWN_CID;
import static com.piusvelte.wapdroid.App.UNKNOWN_RSSI;
import static com.piusvelte.wapdroid.App.TABLE_ID;
import static com.piusvelte.wapdroid.App.TABLE_NETWORKS;
import static com.piusvelte.wapdroid.App.NETWORKS_SSID;
import static com.piusvelte.wapdroid.App.NETWORKS_BSSID;
import static com.piusvelte.wapdroid.App.TABLE_CELLS;
import static com.piusvelte.wapdroid.App.CELLS_CID;
import static com.piusvelte.wapdroid.App.TABLE_LOCATIONS;
import static com.piusvelte.wapdroid.App.LOCATIONS_LAC;
import static com.piusvelte.wapdroid.App.TABLE_PAIRS;
import static com.piusvelte.wapdroid.App.PAIRS_CELL;
import static com.piusvelte.wapdroid.App.PAIRS_NETWORK;
import static com.piusvelte.wapdroid.App.CELLS_LOCATION;
import static com.piusvelte.wapdroid.App.PAIRS_RSSI_MIN;
import static com.piusvelte.wapdroid.App.PAIRS_RSSI_MAX;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {
	public static final String DATABASE_NAME = "wapdroid";
	private static final int DATABASE_VERSION = 6;
	private static final String create = "create table if not exists ";
	private static final String createTemp =  "create temporary table ";
	private static final String drop = "drop table if exists ";

	public DatabaseHelper(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}

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
	
}
