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

import static com.piusvelte.wapdroid.Wapdroid.AUTHORITY;
import static com.piusvelte.wapdroid.WapdroidDatabaseHelper.TABLE_CELLS;
import static com.piusvelte.wapdroid.WapdroidDatabaseHelper.TABLE_LOCATIONS;
import static com.piusvelte.wapdroid.WapdroidDatabaseHelper.TABLE_NETWORKS;
import static com.piusvelte.wapdroid.WapdroidDatabaseHelper.TABLE_PAIRS;
import static com.piusvelte.wapdroid.WapdroidDatabaseHelper.VIEW_RANGES;
//import static com.piusvelte.wapdroid.Wapdroid.TAG;

import java.util.HashMap;

import com.piusvelte.wapdroid.WapdroidDatabaseHelper;
import com.piusvelte.wapdroid.Wapdroid.Cells;
import com.piusvelte.wapdroid.Wapdroid.Locations;
import com.piusvelte.wapdroid.Wapdroid.Networks;
import com.piusvelte.wapdroid.Wapdroid.Pairs;
import com.piusvelte.wapdroid.Wapdroid.Ranges;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;

public class WapdroidContentProvider extends ContentProvider {
	private static final int NETWORKS = 1;
	private static final int CELLS = 2;
	private static final int PAIRS = 3;
	private static final int LOCATIONS = 4;
	private static final int RANGES = 5;
	private static HashMap<String, String> networksProjectionMap;
	private static HashMap<String, String> cellsProjectionMap;
	private static HashMap<String, String> pairsProjectionMap;
	private static HashMap<String, String> locationsProjectionMap;
	private static HashMap<String, String> rangesProjectionMap;

	private static UriMatcher sUriMatcher;

	private WapdroidDatabaseHelper mOpenHelper;

	@Override
	public boolean onCreate() {
		mOpenHelper = new WapdroidDatabaseHelper(getContext());
		return true;
	}

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		SQLiteDatabase db = mOpenHelper.getWritableDatabase();
		switch (sUriMatcher.match(uri)) {
		case NETWORKS:
			return db.delete(TABLE_NETWORKS, selection, selectionArgs);
		case CELLS:
			return db.delete(TABLE_CELLS, selection, selectionArgs);
		case PAIRS:
			return db.delete(TABLE_PAIRS, selection, selectionArgs);
		case LOCATIONS:
			return db.delete(TABLE_LOCATIONS, selection, selectionArgs);
		}
		return 0;
	}

	@Override
	public String getType(Uri uri) {
		switch (sUriMatcher.match(uri)) {
		case NETWORKS:
			return Networks.CONTENT_TYPE;
		case CELLS:
			return Cells.CONTENT_TYPE;
		case PAIRS:
			return Pairs.CONTENT_TYPE;
		case LOCATIONS:
			return Locations.CONTENT_TYPE;
		case RANGES:
			return Ranges.CONTENT_TYPE;
		}
		return null;
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		SQLiteDatabase db = mOpenHelper.getWritableDatabase();
		long rowId = 0;
		switch (sUriMatcher.match(uri)) {
		case NETWORKS:
			rowId = db.insert(TABLE_NETWORKS, Networks.SSID, values);
			if (rowId > 0) {
				Uri networkUri = ContentUris.withAppendedId(Networks.CONTENT_URI, rowId);
				getContext().getContentResolver().notifyChange(networkUri, null);
				return networkUri;
			} else return null;
		case CELLS:
			rowId = db.insert(TABLE_CELLS, Cells.CID, values);
			if (rowId > 0) {
				Uri cellUri = ContentUris.withAppendedId(Cells.CONTENT_URI, rowId);
				getContext().getContentResolver().notifyChange(cellUri, null);
				return cellUri;
			} else return null;
		case PAIRS:
			rowId = db.insert(TABLE_PAIRS, Pairs.CELL, values);
			if (rowId > 0) {
				Uri pairUri = ContentUris.withAppendedId(Pairs.CONTENT_URI, rowId);
				getContext().getContentResolver().notifyChange(pairUri, null);
				return pairUri;
			} else return null;
		case LOCATIONS:
			rowId = db.insert(TABLE_LOCATIONS, Locations.LAC, values);
			if (rowId > 0) {
				Uri locationUri = ContentUris.withAppendedId(Networks.CONTENT_URI, rowId);
				getContext().getContentResolver().notifyChange(locationUri, null);
				return locationUri;
			} else return null;
		}
		return null;
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
		case PAIRS:
			qb.setTables(TABLE_PAIRS);
			qb.setProjectionMap(pairsProjectionMap);
			break;
		case LOCATIONS:
			qb.setTables(TABLE_LOCATIONS);
			qb.setProjectionMap(locationsProjectionMap);
			break;
		case RANGES:
			qb.setTables(VIEW_RANGES);
			qb.setProjectionMap(rangesProjectionMap);
			break;
		}
		SQLiteDatabase db = mOpenHelper.getReadableDatabase();
		Cursor c = qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);
		c.setNotificationUri(getContext().getContentResolver(), uri);
		return c;
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection,
			String[] selectionArgs) {
		SQLiteDatabase db = mOpenHelper.getWritableDatabase();
		int count = 0;
		switch (sUriMatcher.match(uri)) {
		case NETWORKS:
			count = db.update(TABLE_NETWORKS, values, selection, selectionArgs);
			break;
		case CELLS:
			count = db.update(TABLE_CELLS, values, selection, selectionArgs);
			break;
		case PAIRS:
			count = db.update(TABLE_PAIRS, values, selection, selectionArgs);
			break;
		case LOCATIONS:
			count = db.update(TABLE_LOCATIONS, values, selection, selectionArgs);
			break;	
		}
		getContext().getContentResolver().notifyChange(uri, null);
		return count;
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

		rangesProjectionMap = new HashMap<String, String>();
		rangesProjectionMap.put(Ranges._ID, Ranges._ID);
		rangesProjectionMap.put(Ranges.CID, Ranges.CID);
		rangesProjectionMap.put(Ranges.LAC, Ranges.LAC);
		rangesProjectionMap.put(Ranges.RSSI_MAX, Ranges.RSSI_MAX);
		rangesProjectionMap.put(Ranges.RSSI_MIN, Ranges.RSSI_MIN);
		rangesProjectionMap.put(Ranges.LOCATION, Ranges.LOCATION);
		rangesProjectionMap.put(Ranges.NETWORK, Ranges.NETWORK);
		rangesProjectionMap.put(Ranges.SSID, Ranges.SSID);
		rangesProjectionMap.put(Ranges.BSSID, Ranges.BSSID);
	}

}
