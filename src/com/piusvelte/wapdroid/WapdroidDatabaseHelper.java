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

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class WapdroidDatabaseHelper extends SQLiteOpenHelper {
	private static final String DATABASE_NAME = "wapdroid";
	private static final int DATABASE_VERSION = 7;
	public static final String TAG = "Wapdroid";
	public static final String TABLE_NETWORKS = "networks";
	public static final String TABLE_CELLS = "cells";
	public static final String TABLE_PAIRS = "pairs";
	public static final String TABLE_LOCATIONS = "locations";
	public static final String VIEW_RANGES = "ranges";
	public static final int UNKNOWN_CID = -1;
	public static final int UNKNOWN_RSSI = 99;
	public static final String _ID = "_id";
	public static final String SSID = "SSID";
	public static final String BSSID = "BSSID";
	public static final String CID = "CID";
	public static final String LOCATION = "location";
	public static final String CELL = "cell";
	public static final String NETWORK = "network";
	public static final String RSSI_MIN = "RSSI_min";
	public static final String RSSI_MAX = "RSSI_max";
	public static final String LAC = "LAC";

	public WapdroidDatabaseHelper(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL("create table if not exists " + TABLE_NETWORKS + " ("
				+ _ID + " integer primary key autoincrement, "
				+ SSID + " text not null, "
				+ BSSID + " text not null);");
		db.execSQL("create table if not exists " + TABLE_CELLS + " ("
				+ _ID + " integer primary key autoincrement, "
				+ CID + " integer, location integer);");
		db.execSQL("create table if not exists " + TABLE_PAIRS + " ("
				+ _ID + " integer primary key autoincrement, "
				+ CELL + " integer, "
				+ NETWORK + " integer, "
				+ RSSI_MIN + " integer, "
				+ RSSI_MAX + " integer);");
		db.execSQL("create table if not exists " + TABLE_LOCATIONS + " ("
				+ _ID + " integer primary key autoincrement, "
				+ LAC + " integer);");
		db.execSQL("create view if not exists " + VIEW_RANGES + " as select "
				+ TABLE_PAIRS + "." + _ID + " as " + _ID
				+ "," + RSSI_MAX
				+ "," + RSSI_MIN
				+ "," + CID
				+ "," + LAC
				+ "," + LOCATION
				+ "," + SSID
				+ "," + BSSID
				+ " from " + TABLE_PAIRS
				+ " left join " + TABLE_CELLS + " on " + TABLE_CELLS + "." + _ID + "=" + CELL
				+ " left join " + TABLE_LOCATIONS + " on " + TABLE_LOCATIONS + "." + _ID + "=" + LOCATION
				+ " left join " + TABLE_NETWORKS + " on " + TABLE_NETWORKS + "." + _ID + "=" + NETWORK + ";");
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		if (oldVersion < 2) {
			// add BSSID
			db.execSQL("drop table if exists " + TABLE_NETWORKS + "_bkp;");
			db.execSQL("create temporary table " + TABLE_NETWORKS + "_bkp as select * from " + TABLE_NETWORKS + ";");
			db.execSQL("drop table if exists " + TABLE_NETWORKS + ";");
			db.execSQL("create table if not exists " + TABLE_NETWORKS + " (_id  integer primary key autoincrement, "
					+ SSID + " text not null, "
					+ BSSID + " text not null);");
			db.execSQL("insert into " + TABLE_NETWORKS + " select " + _ID + ", " + SSID + ", \"\"" + " from " + TABLE_NETWORKS + "_bkp;");
			db.execSQL("drop table if exists " + TABLE_NETWORKS + "_bkp;");
		}
		if (oldVersion < 3) {
			// add locations
			db.execSQL("create table if not exists " + TABLE_LOCATIONS + " (_id  integer primary key autoincrement, "
					+ LAC + " integer);");
			// first backup cells to create pairs
			db.execSQL("drop table if exists " + TABLE_CELLS + "_bkp;");
			db.execSQL("create temporary table " + TABLE_CELLS + "_bkp as select * from " + TABLE_CELLS + ";");
			// update cells, dropping network column, making unique
			db.execSQL("drop table if exists " + TABLE_CELLS + ";");
			db.execSQL("create table if not exists " + TABLE_CELLS + " (_id  integer primary key autoincrement, " + CID + " integer, location integer);");
			db.execSQL("insert into " + TABLE_CELLS + " (" + CID + ", " + LOCATION
					+ ") select " + CID + ", " + UNKNOWN_CID + " from " + TABLE_CELLS + "_bkp group by " + CID + ";");
			// create pairs
			db.execSQL("create table if not exists " + TABLE_PAIRS + " (_id  integer primary key autoincrement, cell integer, network integer, " + RSSI_MIN + " integer, " + RSSI_MAX + " integer);");
			db.execSQL("insert into " + TABLE_PAIRS
					+ " (" + CELL + ", " + NETWORK + ", " + RSSI_MIN + ", " + RSSI_MAX
					+ ") select " + TABLE_CELLS + "." + _ID + ", " + TABLE_CELLS + "_bkp." + NETWORK + ", " + UNKNOWN_RSSI + ", " + UNKNOWN_RSSI
					+ " from " + TABLE_CELLS + "_bkp"
					+ " left join " + TABLE_CELLS + " on " + TABLE_CELLS + "_bkp." + CID + "=" + TABLE_CELLS + "." + CID + ";");
			db.execSQL("drop table if exists " + TABLE_CELLS + "_bkp;");	
		}
		if (oldVersion < 4) {
			// clean lac=0 locations
			Cursor locations = db.rawQuery("select " + _ID + " from " + TABLE_LOCATIONS + " where " + LAC + "=0", null);
			if (locations.getCount() > 0) {
				locations.moveToFirst();
				int index = locations.getColumnIndex(_ID);
				while (!locations.isAfterLast()) {
					int location = locations.getInt(index);
					// clean pairs
					db.execSQL("delete from " + TABLE_PAIRS + " where " + _ID + " in (select " + TABLE_PAIRS + "." + _ID + " as " + _ID + " from " + TABLE_PAIRS
							+ " left join " + TABLE_CELLS + " on " + CELL + "=" + TABLE_CELLS + "." + _ID
							+ " where " + LOCATION + "=" + location + ");");
					// clean cells
					db.execSQL("delete from " + TABLE_CELLS + " where " + LOCATION + "=" + location + ";");
					locations.moveToNext();
				}
				// clean locations
				db.execSQL("delete from " + TABLE_LOCATIONS + " where " + LAC + "=0;");
			}			
		}
		if (oldVersion < 5) {
			// fix bad rssi values
			db.execSQL("update " + TABLE_PAIRS + " set " + RSSI_MIN + "=-1*" + RSSI_MIN + " where " + RSSI_MIN + " >0 and " + RSSI_MIN + " !=" + UNKNOWN_RSSI + ";");
			db.execSQL("update " + TABLE_PAIRS + " set " + RSSI_MAX + "=-1*" + RSSI_MAX + " where " + RSSI_MAX + " >0 and " + RSSI_MAX + " !=" + UNKNOWN_RSSI + ";");			
		}
		if (oldVersion < 6) {
			// revert incorrect unknown rssi's
			db.execSQL("update " + TABLE_PAIRS + " set " + RSSI_MIN + "=99," + RSSI_MAX + "=99 where " + RSSI_MAX + "<" + RSSI_MIN + " and RSSI_max=-85;");			
		}
		if (oldVersion < 7) {
			db.execSQL("create view if not exists " + VIEW_RANGES + " as select "
					+ TABLE_PAIRS + "." + _ID + " as " + _ID
					+ "," + RSSI_MAX
					+ "," + RSSI_MIN
					+ "," + CID
					+ "," + LAC
					+ "," + LOCATION
					+ "," + NETWORK
					+ "," + SSID
					+ "," + BSSID
					+ " from " + TABLE_PAIRS
					+ " left join " + TABLE_CELLS + " on " + TABLE_CELLS + "." + _ID + "=" + CELL
					+ " left join " + TABLE_LOCATIONS + " on " + TABLE_LOCATIONS + "." + _ID + "=" + LOCATION
					+ " left join " + TABLE_NETWORKS + " on " + TABLE_NETWORKS + "." + _ID + "=" + NETWORK + ";");
		}
	}
}
