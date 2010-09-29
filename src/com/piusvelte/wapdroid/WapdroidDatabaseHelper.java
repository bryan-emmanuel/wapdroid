package com.piusvelte.wapdroid;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.piusvelte.wapdroid.Wapdroid.Cells;
import com.piusvelte.wapdroid.Wapdroid.Locations;
import com.piusvelte.wapdroid.Wapdroid.Networks;
import com.piusvelte.wapdroid.Wapdroid.Pairs;
import com.piusvelte.wapdroid.Wapdroid.Ranges;

public class WapdroidDatabaseHelper extends SQLiteOpenHelper {
	private static final String DATABASE_NAME = "wapdroid";
	private static final int DATABASE_VERSION = 7;
	public static final String TABLE_NETWORKS = "networks";
	public static final String TABLE_CELLS = "cells";
	public static final String TABLE_PAIRS = "pairs";
	public static final String TABLE_LOCATIONS = "locations";
	public static final String VIEW_RANGES = "ranges";
	public static final int UNKNOWN_CID = -1;
	public static final int UNKNOWN_RSSI = 99;

	public WapdroidDatabaseHelper(Context context) {
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
		db.execSQL("create view if not exists " + VIEW_RANGES + " as select "
				+ TABLE_PAIRS + "." + Pairs._ID + " as " + Ranges._ID
				+ "," + Pairs.RSSI_MAX
				+ "," + Pairs.RSSI_MIN
				+ "," + Cells.CID
				+ "," + Locations.LAC
				+ "," + Cells.LOCATION
				+ "," + Networks.SSID
				+ "," + Networks.BSSID
				+ " from " + TABLE_PAIRS
				+ " left join " + TABLE_CELLS + " on " + TABLE_CELLS + "." + Cells._ID + "=" + Pairs.CELL
				+ " left join " + TABLE_LOCATIONS + " on " + TABLE_LOCATIONS + "." + Locations._ID + "=" + Cells.LOCATION
				+ " left join " + TABLE_NETWORKS + " on " + TABLE_NETWORKS + "." + Networks._ID + "=" + Pairs.NETWORK + ";");
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
		if (oldVersion < 7) {
			db.execSQL("create view if not exists " + VIEW_RANGES + " as select "
					+ TABLE_PAIRS + "." + Pairs._ID + " as " + Ranges._ID
					+ "," + Pairs.RSSI_MAX
					+ "," + Pairs.RSSI_MIN
					+ "," + Cells.CID
					+ "," + Locations.LAC
					+ "," + Cells.LOCATION
					+ "," + Pairs.NETWORK
					+ "," + Networks.SSID
					+ "," + Networks.BSSID
					+ " from " + TABLE_PAIRS
					+ " left join " + TABLE_CELLS + " on " + TABLE_CELLS + "." + Cells._ID + "=" + Pairs.CELL
					+ " left join " + TABLE_LOCATIONS + " on " + TABLE_LOCATIONS + "." + Locations._ID + "=" + Cells.LOCATION
					+ " left join " + TABLE_NETWORKS + " on " + TABLE_NETWORKS + "." + Networks._ID + "=" + Pairs.NETWORK + ";");
		}
	}
}
