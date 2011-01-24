/*
 * Wapdroid - Android Social Networking Widget
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

import java.util.HashMap;

import com.piusvelte.wapdroid.Wapdroid;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;

public class WapdroidProvider extends ContentProvider {

	public static final String AUTHORITY = "com.piusvelte.wapdroid.WapdroidProvider";

	private static final UriMatcher sUriMatcher;
	
	private static final String DATABASE_NAME = "wapdroid";
	private static final int DATABASE_VERSION = 8;
	
	public static final String TAG = "WapdroidProvider";
	
	public static final String TABLE_NETWORKS = "networks";
	private static final int NETWORKS = 0;
	private static HashMap<String, String> networksProjectionMap;
	
	public static final String TABLE_CELLS = "cells";
	private static final int CELLS = 1;
	private static HashMap<String, String> cellsProjectionMap;
	
	public static final String TABLE_PAIRS = "pairs";
	private static final int PAIRS = 2;
	private static HashMap<String, String> pairsProjectionMap;
	
	public static final String TABLE_LOCATIONS = "locations";
	private static final int LOCATIONS = 3;
	private static HashMap<String, String> locationsProjectionMap;
	
	public static final String VIEW_RANGES = "ranges";
	private static final int RANGES = 4;
	private static HashMap<String, String> rangesProjectionMap;
	
	private DatabaseHelper mDatabaseHelper;

	static {
		sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
		
		sUriMatcher.addURI(AUTHORITY, TABLE_NETWORKS, NETWORKS);
		networksProjectionMap = new HashMap<String, String>();
		networksProjectionMap.put(Wapdroid.Networks._ID, Wapdroid.Networks._ID);
		networksProjectionMap.put(Wapdroid.Networks.SSID, Wapdroid.Networks.SSID);
		networksProjectionMap.put(Wapdroid.Networks.BSSID, Wapdroid.Networks.BSSID);
		
		sUriMatcher.addURI(AUTHORITY, TABLE_CELLS, CELLS);
		cellsProjectionMap = new HashMap<String, String>();
		cellsProjectionMap.put(Wapdroid.Cells._ID, Wapdroid.Cells._ID);
		cellsProjectionMap.put(Wapdroid.Cells.CID, Wapdroid.Cells.CID);
		cellsProjectionMap.put(Wapdroid.Cells.LOCATION, Wapdroid.Cells.LOCATION);
		
		sUriMatcher.addURI(AUTHORITY, TABLE_PAIRS, PAIRS);
		pairsProjectionMap.put(Wapdroid.Pairs._ID, Wapdroid.Pairs._ID);
		pairsProjectionMap.put(Wapdroid.Pairs.CELL, Wapdroid.Pairs.CELL);
		pairsProjectionMap.put(Wapdroid.Pairs.NETWORK, Wapdroid.Pairs.NETWORK);
		pairsProjectionMap.put(Wapdroid.Pairs.RSSI_MAX, Wapdroid.Pairs.RSSI_MAX);
		pairsProjectionMap.put(Wapdroid.Pairs.RSSI_MIN, Wapdroid.Pairs.RSSI_MIN);
		
		sUriMatcher.addURI(AUTHORITY, TABLE_LOCATIONS, LOCATIONS);
		locationsProjectionMap.put(Wapdroid.Locations._ID, Wapdroid.Locations._ID);
		locationsProjectionMap.put(Wapdroid.Locations.LAC, Wapdroid.Locations.LAC);
		
		sUriMatcher.addURI(AUTHORITY, VIEW_RANGES, RANGES);
		rangesProjectionMap.put(Wapdroid.Ranges._ID, Wapdroid.Ranges._ID);
		rangesProjectionMap.put(Wapdroid.Ranges.BSSID, Wapdroid.Ranges.BSSID);
		rangesProjectionMap.put(Wapdroid.Ranges.CELL, Wapdroid.Ranges.CELL);
		rangesProjectionMap.put(Wapdroid.Ranges.CID, Wapdroid.Ranges.CID);
		rangesProjectionMap.put(Wapdroid.Ranges.LAC, Wapdroid.Ranges.LAC);
		rangesProjectionMap.put(Wapdroid.Ranges.LOCATION, Wapdroid.Ranges.LOCATION);
		rangesProjectionMap.put(Wapdroid.Ranges.NETWORK, Wapdroid.Ranges.NETWORK);
		rangesProjectionMap.put(Wapdroid.Ranges.RSSI_MAX, Wapdroid.Ranges.RSSI_MAX);
		rangesProjectionMap.put(Wapdroid.Ranges.RSSI_MIN, Wapdroid.Ranges.RSSI_MIN);
		rangesProjectionMap.put(Wapdroid.Ranges.SSID, Wapdroid.Ranges.SSID);
	}
	

	@Override
	public int delete(Uri arg0, String arg1, String[] arg2) {
		SQLiteDatabase db = mDatabaseHelper.getWritableDatabase();
		int count;
		switch (sUriMatcher.match(arg0)) {
		case NETWORKS:
			count = db.delete(TABLE_NETWORKS, arg1, arg2);
			break;
		case CELLS:
			count = db.delete(TABLE_CELLS, arg1, arg2);
			break;
		case LOCATIONS:
			count = db.delete(TABLE_LOCATIONS, arg1, arg2);
			break;
		case PAIRS:
			count = db.delete(TABLE_PAIRS, arg1, arg2);
			break;
		default:
			throw new IllegalArgumentException("Unknown URI " + arg0);
		}
		getContext().getContentResolver().notifyChange(arg0, null);
		return count;
	}

	@Override
	public String getType(Uri uri) {
		switch (sUriMatcher.match(uri)) {
		case NETWORKS:
			return Wapdroid.Networks.CONTENT_TYPE;
		case CELLS:
			return Wapdroid.Cells.CONTENT_TYPE;
		case LOCATIONS:
			return Wapdroid.Locations.CONTENT_TYPE;
		case PAIRS:
			return Wapdroid.Pairs.CONTENT_TYPE;
		case RANGES:
			return Wapdroid.Ranges.CONTENT_TYPE;
		default:
			throw new IllegalArgumentException("Unknown URI " + uri);
		}
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		SQLiteDatabase db = mDatabaseHelper.getWritableDatabase();
		long rowId;
		Uri returnUri;
		switch (sUriMatcher.match(uri)) {
		case NETWORKS:
			rowId = db.insert(TABLE_NETWORKS, Wapdroid.Networks._ID, values);
			returnUri = ContentUris.withAppendedId(Wapdroid.Networks.CONTENT_URI, rowId);
			getContext().getContentResolver().notifyChange(returnUri, null);
			break;
		case CELLS:
			rowId = db.insert(TABLE_CELLS, Wapdroid.Cells._ID, values);
			returnUri = ContentUris.withAppendedId(Wapdroid.Cells.CONTENT_URI, rowId);
			getContext().getContentResolver().notifyChange(returnUri, null);
			break;
		case LOCATIONS:
			rowId = db.insert(TABLE_LOCATIONS, Wapdroid.Locations._ID, values);
			returnUri = ContentUris.withAppendedId(Wapdroid.Locations.CONTENT_URI, rowId);
			getContext().getContentResolver().notifyChange(returnUri, null);
			break;
		case PAIRS:
			rowId = db.insert(TABLE_PAIRS, Wapdroid.Pairs._ID, values);
			returnUri = ContentUris.withAppendedId(Wapdroid.Pairs.CONTENT_URI, rowId);
			getContext().getContentResolver().notifyChange(returnUri, null);
			break;
		default:
			throw new IllegalArgumentException("Unknown URI " + uri);
		}
		return returnUri;
	}

	@Override
	public boolean onCreate() {
		mDatabaseHelper = new DatabaseHelper(getContext());
		return true;
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {
		SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
		switch (sUriMatcher.match(uri)) {
		case NETWORKS:
			qb.setTables(TABLE_NETWORKS);
			qb.setProjectionMap(networksProjectionMap);
			break;
		case CELLS:
			qb.setTables(TABLE_CELLS);
			qb.setProjectionMap(cellsProjectionMap);
			break;
		case LOCATIONS:
			qb.setTables(TABLE_LOCATIONS);
			qb.setProjectionMap(locationsProjectionMap);
			break;
		case PAIRS:
			qb.setTables(TABLE_PAIRS);
			qb.setProjectionMap(pairsProjectionMap);
			break;
		case RANGES:
			qb.setTables(VIEW_RANGES);
			qb.setProjectionMap(rangesProjectionMap);
			break;
		default:
			throw new IllegalArgumentException("Unknown URI " + uri);
		}
		SQLiteDatabase db = mDatabaseHelper.getReadableDatabase();
		Cursor c = qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);
		c.setNotificationUri(getContext().getContentResolver(), uri);
		return c;
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection,
			String[] selectionArgs) {
		SQLiteDatabase db = mDatabaseHelper.getWritableDatabase();
		int count;
		switch (sUriMatcher.match(uri)) {
		case NETWORKS:
			count = db.update(TABLE_NETWORKS, values, selection, selectionArgs);
			break;
		case CELLS:
			count = db.update(TABLE_CELLS, values, selection, selectionArgs);
			break;
		case LOCATIONS:
			count = db.update(TABLE_LOCATIONS, values, selection, selectionArgs);
			break;
		case PAIRS:
			count = db.update(TABLE_PAIRS, values, selection, selectionArgs);
			break;
		default:
			throw new IllegalArgumentException("Unknown URI " + uri);
		}
		getContext().getContentResolver().notifyChange(uri, null);
		return count;
	}
	
	public class DatabaseHelper extends SQLiteOpenHelper {

		public DatabaseHelper(Context context) {
			super(context, DATABASE_NAME, null, DATABASE_VERSION);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL("create table if not exists " + TABLE_NETWORKS + " ("
					+ Wapdroid.Networks._ID + " integer primary key autoincrement, "
					+ Wapdroid.Networks.SSID + " text not null, "
					+ Wapdroid.Networks.BSSID + " text not null);");
			db.execSQL("create table if not exists " + TABLE_CELLS + " ("
					+ Wapdroid.Cells._ID + " integer primary key autoincrement, "
					+ Wapdroid.Cells.CID + " integer, "
					+ Wapdroid.Cells.LOCATION + " integer);");
			db.execSQL("create table if not exists " + TABLE_PAIRS + " ("
					+ Wapdroid.Pairs._ID + " integer primary key autoincrement, "
					+ Wapdroid.Pairs.CELL + " integer, "
					+ Wapdroid.Pairs.NETWORK + " integer, "
					+ Wapdroid.Pairs.RSSI_MIN + " integer, "
					+ Wapdroid.Pairs.RSSI_MAX + " integer);");
			db.execSQL("create table if not exists " + TABLE_LOCATIONS + " ("
					+ Wapdroid.Locations._ID + " integer primary key autoincrement, "
					+ Wapdroid.Locations.LAC + " integer);");
			db.execSQL("create view if not exists " + VIEW_RANGES + " as select "
					+ TABLE_PAIRS + "." + Wapdroid.Ranges._ID + " as " + Wapdroid.Ranges._ID
					+ "," + Wapdroid.Ranges.RSSI_MAX
					+ "," + Wapdroid.Ranges.RSSI_MIN
					+ "," + Wapdroid.Ranges.CID
					+ "," + Wapdroid.Ranges.LAC
					+ "," + Wapdroid.Ranges.LOCATION
					+ "," + Wapdroid.Ranges.SSID
					+ "," + Wapdroid.Ranges.BSSID
					+ "," + Wapdroid.Ranges.CELL
					+ "," + Wapdroid.Ranges.NETWORK
					+ " from " + TABLE_PAIRS
					+ " left join " + TABLE_CELLS + " on " + TABLE_CELLS + "." + Wapdroid.Ranges._ID + "=" + Wapdroid.Ranges.CELL
					+ " left join " + TABLE_LOCATIONS + " on " + TABLE_LOCATIONS + "." + Wapdroid.Ranges._ID + "=" + Wapdroid.Ranges.LOCATION
					+ " left join " + TABLE_NETWORKS + " on " + TABLE_NETWORKS + "." + Wapdroid.Ranges._ID + "=" + Wapdroid.Ranges.NETWORK + ";");
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			if (oldVersion < 2) {
				// add BSSID
				db.execSQL("drop table if exists " + TABLE_NETWORKS + "_bkp;");
				db.execSQL("create temporary table " + TABLE_NETWORKS + "_bkp as select * from " + TABLE_NETWORKS + ";");
				db.execSQL("drop table if exists " + TABLE_NETWORKS + ";");
				db.execSQL("create table if not exists " + TABLE_NETWORKS + " (_id  integer primary key autoincrement, "
						+ Wapdroid.Networks.SSID + " text not null, "
						+ Wapdroid.Networks.BSSID + " text not null);");
				db.execSQL("insert into " + TABLE_NETWORKS + " select " + Wapdroid.Networks._ID + ", " + Wapdroid.Networks.SSID + ", \"\"" + " from " + TABLE_NETWORKS + "_bkp;");
				db.execSQL("drop table if exists " + TABLE_NETWORKS + "_bkp;");
			}
			if (oldVersion < 3) {
				// add locations
				db.execSQL("create table if not exists " + TABLE_LOCATIONS + " (_id  integer primary key autoincrement, "
						+ Wapdroid.Locations.LAC + " integer);");
				// first backup cells to create pairs
				db.execSQL("drop table if exists " + TABLE_CELLS + "_bkp;");
				db.execSQL("create temporary table " + TABLE_CELLS + "_bkp as select * from " + TABLE_CELLS + ";");
				// update cells, dropping network column, making unique
				db.execSQL("drop table if exists " + TABLE_CELLS + ";");
				db.execSQL("create table if not exists " + TABLE_CELLS + " (_id  integer primary key autoincrement, " + Wapdroid.Cells.CID + " integer, location integer);");
				db.execSQL("insert into " + TABLE_CELLS + " (" + Wapdroid.Cells.CID + ", " + Wapdroid.Ranges.LOCATION
						+ ") select " + Wapdroid.Cells.CID + ", " + Wapdroid.UNKNOWN_CID + " from " + TABLE_CELLS + "_bkp group by " + Wapdroid.Cells.CID + ";");
				// create pairs
				db.execSQL("create table if not exists " + TABLE_PAIRS + " (_id  integer primary key autoincrement, cell integer, network integer, " + Wapdroid.Pairs.RSSI_MIN + " integer, " + Wapdroid.Pairs.RSSI_MAX + " integer);");
				db.execSQL("insert into " + TABLE_PAIRS
						+ " (" + Wapdroid.Pairs.CELL + ", " + Wapdroid.Pairs.NETWORK + ", " + Wapdroid.Pairs.RSSI_MIN + ", " + Wapdroid.Pairs.RSSI_MAX
						+ ") select " + TABLE_CELLS + "." + Wapdroid.Cells._ID + ", " + TABLE_CELLS + "_bkp." + Wapdroid.Pairs.NETWORK + ", " + Wapdroid.UNKNOWN_RSSI + ", " + Wapdroid.UNKNOWN_RSSI
						+ " from " + TABLE_CELLS + "_bkp"
						+ " left join " + TABLE_CELLS + " on " + TABLE_CELLS + "_bkp." + Wapdroid.Cells.CID + "=" + TABLE_CELLS + "." + Wapdroid.Cells.CID + ";");
				db.execSQL("drop table if exists " + TABLE_CELLS + "_bkp;");	
			}
			if (oldVersion < 4) {
				// clean lac=0 locations
				Cursor locations = db.rawQuery("select " + Wapdroid.Locations._ID + " from " + TABLE_LOCATIONS + " where " + Wapdroid.Locations.LAC + "=0", null);
				if (locations.getCount() > 0) {
					locations.moveToFirst();
					int index = locations.getColumnIndex(Wapdroid.Locations._ID);
					while (!locations.isAfterLast()) {
						int location = locations.getInt(index);
						// clean pairs
						db.execSQL("delete from " + TABLE_PAIRS + " where " + Wapdroid.Pairs._ID + " in (select " + TABLE_PAIRS + "." + Wapdroid.Pairs._ID + " as " + Wapdroid.Pairs._ID + " from " + TABLE_PAIRS
								+ " left join " + TABLE_CELLS + " on " + Wapdroid.Pairs.CELL + "=" + TABLE_CELLS + "." + Wapdroid.Cells._ID
								+ " where " + Wapdroid.Ranges.LOCATION + "=" + location + ");");
						// clean cells
						db.execSQL("delete from " + TABLE_CELLS + " where " + Wapdroid.Ranges.LOCATION + "=" + location + ";");
						locations.moveToNext();
					}
					// clean locations
					db.execSQL("delete from " + TABLE_LOCATIONS + " where " + Wapdroid.Locations.LAC + "=0;");
				}			
			}
			if (oldVersion < 5) {
				// fix bad rssi values
				db.execSQL("update " + TABLE_PAIRS + " set " + Wapdroid.Pairs.RSSI_MIN + "=-1*" + Wapdroid.Pairs.RSSI_MIN + " where " + Wapdroid.Pairs.RSSI_MIN + " >0 and " + Wapdroid.Pairs.RSSI_MIN + " !=" + Wapdroid.UNKNOWN_RSSI + ";");
				db.execSQL("update " + TABLE_PAIRS + " set " + Wapdroid.Pairs.RSSI_MAX + "=-1*" + Wapdroid.Pairs.RSSI_MAX + " where " + Wapdroid.Pairs.RSSI_MAX + " >0 and " + Wapdroid.Pairs.RSSI_MAX + " !=" + Wapdroid.UNKNOWN_RSSI + ";");			
			}
			if (oldVersion < 6) {
				// revert incorrect unknown rssi's
				db.execSQL("update " + TABLE_PAIRS + " set " + Wapdroid.Pairs.RSSI_MIN + "=99," + Wapdroid.Pairs.RSSI_MAX + "=99 where " + Wapdroid.Pairs.RSSI_MAX + "<" + Wapdroid.Pairs.RSSI_MIN + " and RSSI_max=-85;");			
			}
			if (oldVersion < 7) {
				db.execSQL("create view if not exists " + VIEW_RANGES + " as select "
						+ TABLE_PAIRS + "." + Wapdroid.Ranges._ID + " as " + Wapdroid.Ranges._ID
						+ "," + Wapdroid.Ranges.RSSI_MAX
						+ "," + Wapdroid.Ranges.RSSI_MIN
						+ "," + Wapdroid.Ranges.CID
						+ "," + Wapdroid.Ranges.LAC
						+ "," + Wapdroid.Ranges.LOCATION
						+ "," + Wapdroid.Ranges.NETWORK
						+ "," + Wapdroid.Ranges.SSID
						+ "," + Wapdroid.Ranges.BSSID
						+ " from " + TABLE_PAIRS
						+ " left join " + TABLE_CELLS + " on " + TABLE_CELLS + "." + Wapdroid.Cells._ID + "=" + Wapdroid.Ranges.CELL
						+ " left join " + TABLE_LOCATIONS + " on " + TABLE_LOCATIONS + "." + Wapdroid.Locations._ID + "=" + Wapdroid.Ranges.LOCATION
						+ " left join " + TABLE_NETWORKS + " on " + TABLE_NETWORKS + "." + Wapdroid.Networks._ID + "=" + Wapdroid.Ranges.NETWORK + ";");
			}
			if (oldVersion < 8) {
				db.execSQL("drop view if exists " + VIEW_RANGES + ";");
				db.execSQL("create view if not exists " + VIEW_RANGES + " as select "
						+ TABLE_PAIRS + "." + Wapdroid.Ranges._ID + " as " + Wapdroid.Pairs._ID
						+ "," + Wapdroid.Pairs.RSSI_MAX
						+ "," + Wapdroid.Pairs.RSSI_MIN
						+ "," + Wapdroid.Cells.CID
						+ "," + Wapdroid.Locations.LAC
						+ "," + Wapdroid.Ranges.LOCATION
						+ "," + Wapdroid.Ranges.SSID
						+ "," + Wapdroid.Ranges.BSSID
						+ "," + Wapdroid.Ranges.CELL
						+ "," + Wapdroid.Ranges.NETWORK
						+ " from " + TABLE_PAIRS
						+ " left join " + TABLE_CELLS + " on " + TABLE_CELLS + "." + Wapdroid.Cells._ID + "=" + Wapdroid.Ranges.CELL
						+ " left join " + TABLE_LOCATIONS + " on " + TABLE_LOCATIONS + "." + Wapdroid.Locations._ID + "=" + Wapdroid.Ranges.LOCATION
						+ " left join " + TABLE_NETWORKS + " on " + TABLE_NETWORKS + "." + Wapdroid.Networks._ID + "=" + Wapdroid.Pairs.NETWORK + ";");
			}
		}
	}

}
