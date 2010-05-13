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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class WapdroidDbAdapter {
	private static final String DATABASE_NAME = "wapdroid";
	private static final int DATABASE_VERSION = 3;
	private static final String DROP = "drop table if exists ";
	public static final String TABLE_ID = "_id";
	public static final String TABLE_CODE = "code";
	private static final String ID_TYPE = " integer primary key autoincrement, ";
	public static final String TABLE_NETWORKS = "networks";
	public static final String NETWORKS_SSID = "SSID";
	public static final String NETWORKS_BSSID = "BSSID";
	public static final String TABLE_CELLS = "cells";
	public static final String CELLS_CID = "CID";
	public static final String STATUS = "status";
	public static final int FILTER_ALL = 0;
	public static final int FILTER_INRANGE = 1;
	public static final int FILTER_OUTRANGE = 2;
	public static final String TABLE_LOCATIONS = "locations";
	public static final String LOCATIONS_LAC = "LAC";
	public static final String TABLE_PAIRS = "pairs";
	public static final String PAIRS_CELL = "cell";
	public static final String PAIRS_NETWORK = "network";
	public static final String CELLS_LOCATION = "location";
	private static final String TAG = "WapdroidDbAdapter";
	
	private static final String CREATE_NETWORKS = "create table "
		+ TABLE_NETWORKS + " ("
		+ TABLE_ID + ID_TYPE
		+ NETWORKS_SSID + " text not null, "
		+ NETWORKS_BSSID + " text not null);";
	private static final String CREATE_CELLS = "create table "
		+ TABLE_CELLS + " ("
		+ TABLE_ID + ID_TYPE
		+ CELLS_CID + " integer, "
		+ CELLS_LOCATION + " integer);";
	private static final String CREATE_PAIRS = "create table "
		+ TABLE_PAIRS + " ("
		+ TABLE_ID + ID_TYPE
		+ PAIRS_CELL + " integer, "
		+ PAIRS_NETWORK + " integer);";
	private static final String CREATE_LOCATIONS = "create table "
		+ TABLE_LOCATIONS + " ("
		+ TABLE_ID + ID_TYPE
		+ LOCATIONS_LAC + " integer);";
	
	private DatabaseHelper mDbHelper;
	private SQLiteDatabase mDb;

    public final Context mContext;
    
    private static class DatabaseHelper extends SQLiteOpenHelper {
        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);}
        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(CREATE_NETWORKS);
            db.execSQL(CREATE_CELLS);
            db.execSQL(CREATE_PAIRS);
            db.execSQL(CREATE_LOCATIONS);}
        
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
    			db.execSQL(DROP + TABLE_NETWORKS + "_bkp;");}
        	if (oldVersion < 3) {
        		// add locations
        		db.execSQL(CREATE_LOCATIONS);
        		// first backup cells to create pairs
        		db.execSQL(DROP + TABLE_CELLS + "_bkp");
    			db.execSQL("create temporary table " + TABLE_CELLS + "_bkp as select * from " + TABLE_CELLS + ";");
            	// update cells, dropping network column, making unique
        		db.execSQL(DROP + TABLE_CELLS + ";");
        		db.execSQL(CREATE_CELLS);
        		db.execSQL("insert into " + TABLE_CELLS + " select " + TABLE_ID + ", " + CELLS_CID + ", null from " + TABLE_CELLS + "_bkp group by " + CELLS_CID);
        		// create pairs
        		db.execSQL(CREATE_PAIRS);
        		db.execSQL("insert into " + TABLE_PAIRS
        				+ " (" + PAIRS_CELL + ", " + PAIRS_NETWORK
        				+ ") select " + TABLE_CELLS + "." + CELLS_CID + ", " + TABLE_CELLS
        				+ "_bkp.network from " + TABLE_CELLS + ", " + TABLE_CELLS
        				+ "_bkp where " + TABLE_CELLS + "." + TABLE_ID + "=" + TABLE_CELLS + "_bkp." + TABLE_ID);
            	db.execSQL(DROP + TABLE_CELLS + "_bkp");}}}
        
    public WapdroidDbAdapter(Context context) {
        this.mContext = context;}
    
    public WapdroidDbAdapter open() throws SQLException {
        mDbHelper = new DatabaseHelper(mContext);
        mDb = mDbHelper.getWritableDatabase();
        return this;}

    public void close() {
        mDbHelper.close();}
        
    public void createTables() {
        mDb.execSQL(CREATE_NETWORKS);
        mDb.execSQL(CREATE_CELLS);
        mDb.execSQL(CREATE_PAIRS);
        mDb.execSQL(CREATE_LOCATIONS);}
    
    public String tableColAs(String table, String column) {
    	return table + "." + column + " as " + column;}
        
    public int fetchNetworkOrCreate(String SSID, String BSSID) {
    	int network = -1;
    	String ssid = "", bssid = "";
		ContentValues values = new ContentValues();
		// upgrading, BSSID may not be set yet
    	Cursor c = mDb.rawQuery("select " + TABLE_ID + ", " + NETWORKS_SSID + ", " + NETWORKS_BSSID + " from " + TABLE_NETWORKS + " where " + NETWORKS_BSSID + "=\"" + BSSID + "\" OR (" + NETWORKS_SSID + "=\"" + SSID + "\" and " + NETWORKS_BSSID + "=\"\")", null);
    	if (c.getCount() > 0) {
    		c.moveToFirst();
    		network = c.getInt(c.getColumnIndex(TABLE_ID));
    		ssid = c.getString(c.getColumnIndex(NETWORKS_SSID));
    		bssid = c.getString(c.getColumnIndex(NETWORKS_BSSID));
    		if (bssid.equals("")) {
        		values.put(NETWORKS_BSSID, BSSID);
        		mDb.update(TABLE_NETWORKS, values, TABLE_ID + "=" + network, null);}
    		else if (!ssid.equals(SSID)) {
        		values.put(NETWORKS_SSID, SSID);
        		mDb.update(TABLE_NETWORKS, values, TABLE_ID + "=" + network, null);}}
    	else {
    		values.put(NETWORKS_SSID, SSID);
    		values.put(NETWORKS_BSSID, BSSID);
    		network = (int) mDb.insert(TABLE_NETWORKS, null, values);}
    	c.close();
    	return network;}

    public Cursor fetchNetworks(int filter, String set) {
    	Log.v(TAG, "fetchNetworks:");
    	Log.v(TAG, "select " + tableColAs(TABLE_NETWORKS, TABLE_ID) + ", "
   	   			+ tableColAs(TABLE_NETWORKS, NETWORKS_SSID) + ", "
   	   			+ tableColAs(TABLE_NETWORKS, NETWORKS_BSSID) + ", "
   	   			+ "CASE WHEN " + TABLE_NETWORKS + "." + TABLE_ID
   	   			+ " in (select " + TABLE_PAIRS + "." + PAIRS_NETWORK
   	   			+ " from " + TABLE_PAIRS + ", " + TABLE_CELLS
   	   			+ " where " + TABLE_PAIRS + "." + PAIRS_CELL + "=" + TABLE_CELLS + "." + CELLS_CID
   	   			+ " and " + CELLS_CID + " in (" + set
   	   			+ ")) then '" + mContext.getString(R.string.withinarea)
   				+ "' else '" + mContext.getString(R.string.outofarea) + "' end as " + STATUS);
   		Log.v(TAG, " from " + TABLE_NETWORKS
   	   	    	+ ((filter != FILTER_ALL) ?
   	   	    		(", " + TABLE_PAIRS + ", " + TABLE_CELLS
   	   	    		+ " where " + TABLE_NETWORKS + "." + TABLE_ID + " = " + TABLE_PAIRS + "." + PAIRS_NETWORK
   	   	    		+ " and " + TABLE_PAIRS + "." + PAIRS_CELL + "=" + TABLE_CELLS + "." + TABLE_ID
   	   	    		+ " and " + TABLE_CELLS + "." + CELLS_CID + (filter == FILTER_OUTRANGE ? " NOT" : "") + " in (" + set + ")")
   	   	    	: ""));
   		return mDb.rawQuery("select " + tableColAs(TABLE_NETWORKS, TABLE_ID) + ", "
   	   			+ tableColAs(TABLE_NETWORKS, NETWORKS_SSID) + ", "
   	   			+ tableColAs(TABLE_NETWORKS, NETWORKS_BSSID) + ", "
   	   			+ "CASE WHEN " + TABLE_NETWORKS + "." + TABLE_ID
   	   			+ " in (select " + TABLE_PAIRS + "." + PAIRS_NETWORK
   	   			+ " from " + TABLE_PAIRS + ", " + TABLE_CELLS
   	   			+ " where " + TABLE_PAIRS + "." + PAIRS_CELL + "=" + TABLE_CELLS + "." + CELLS_CID
   	   			+ " and " + CELLS_CID + " in (" + set
   	   			+ ")) then '" + mContext.getString(R.string.withinarea)
   				+ "' else '" + mContext.getString(R.string.outofarea) + "' end as " + STATUS
   	   	    	+ " from " + TABLE_NETWORKS
   	   	    	+ ((filter != FILTER_ALL) ?
   	   	    		(", " + TABLE_PAIRS + ", " + TABLE_CELLS
   	   	    		+ " where " + TABLE_NETWORKS + "." + TABLE_ID + " = " + TABLE_PAIRS + "." + PAIRS_NETWORK
   	   	    		+ " and " + TABLE_PAIRS + "." + PAIRS_CELL + "=" + TABLE_CELLS + "." + TABLE_ID
   	   	    		+ " and " + TABLE_CELLS + "." + CELLS_CID + (filter == FILTER_OUTRANGE ? " NOT" : "") + " in (" + set + ")")
   	   	    	: ""), null);}
    
    public int fetchLocationOrCreate(int LAC) {
    	int location = -1;
    	Cursor c = mDb.rawQuery("select " + TABLE_ID + " from " + TABLE_LOCATIONS + " where " + LOCATIONS_LAC + "=" + LAC, null);
    	if (c.getCount() > 0) {
    		c.moveToFirst();
    		location = c.getInt(c.getColumnIndex(TABLE_ID));}
    	else {
    		ContentValues values = new ContentValues();
    		values.put(LOCATIONS_LAC, LAC);
    		location = (int) mDb.insert(TABLE_LOCATIONS, null, values);}
    	c.close();
    	return location;}
    
    public int fetchCellOrCreate(int CID, int location) {
    	int cell = -1;
    	Cursor c = mDb.rawQuery("select " + TABLE_ID + " from " + TABLE_CELLS + " where " + CELLS_CID + "=" + CID + " and " + CELLS_LOCATION + "=" + location, null);
    	if (c.getCount() > 0) {
    		c.moveToFirst();
    		cell = c.getInt(c.getColumnIndex(TABLE_ID));}
    	else {
    		ContentValues values = new ContentValues();
        	values.put(CELLS_CID, CID);
        	values.put(CELLS_LOCATION, location);
    		cell = (int) mDb.insert(TABLE_CELLS, null, values);}
    	c.close();
    	return cell;}
    
    public int fetchPair(int cell, int network) {
    	int pair = -1;
    	Cursor c = mDb.rawQuery("select " + TABLE_ID + " from " + TABLE_PAIRS + " where " + PAIRS_CELL + "=" + cell + " and " + PAIRS_NETWORK + "=" + network, null);
    	if (c.getCount() > 0) {
    		c.moveToFirst();
    		pair = c.getInt(c.getColumnIndex(TABLE_ID));}
    	c.close();
    	return pair;}
    
    public Cursor fetchNetworkData(int network) {
    	return mDb.rawQuery("select " + tableColAs(TABLE_NETWORKS, NETWORKS_SSID) + tableColAs(TABLE_CELLS, CELLS_CID) + ", " + tableColAs(TABLE_LOCATIONS, LOCATIONS_LAC)
    		+ " from " + TABLE_PAIRS + ", " + TABLE_NETWORKS + ", " + TABLE_CELLS + ", " + TABLE_LOCATIONS
    		+ " where " + TABLE_PAIRS + "." + PAIRS_NETWORK + "=" + TABLE_NETWORKS + "." + TABLE_ID
    		+ " and " + TABLE_PAIRS + "." + PAIRS_CELL + "=" + TABLE_CELLS + "." + TABLE_ID
    		+ " and " + TABLE_CELLS + "." + CELLS_LOCATION + "=" + TABLE_LOCATIONS + "." + TABLE_ID
    		+ " and " + TABLE_PAIRS + "." + PAIRS_NETWORK + "=" + network, null);}
    
    public Cursor fetchCellData(int network, int cell) {
    	return mDb.rawQuery("select " + tableColAs(TABLE_NETWORKS, NETWORKS_SSID) + tableColAs(TABLE_CELLS, CELLS_CID) + ", " + tableColAs(TABLE_LOCATIONS, LOCATIONS_LAC)
        		+ " from " + TABLE_PAIRS + ", " + TABLE_NETWORKS + ", " + TABLE_CELLS + ", " + TABLE_LOCATIONS
        		+ " where " + TABLE_PAIRS + "." + PAIRS_NETWORK + "=" + TABLE_NETWORKS + "." + TABLE_ID
        		+ " and " + TABLE_PAIRS + "." + PAIRS_CELL + "=" + TABLE_CELLS + "." + TABLE_ID
        		+ " and " + TABLE_CELLS + "." + CELLS_LOCATION + "=" + TABLE_LOCATIONS + "." + TABLE_ID
        		+ " and " + TABLE_PAIRS + "." + PAIRS_NETWORK + "=" + network
        		+ " and " + TABLE_PAIRS + "." + PAIRS_CELL + "=" + cell, null);}

    public Cursor fetchPairsByNetwork(int network) {
    	Log.v(TAG,"fetchPairs: "+"select " + tableColAs(TABLE_PAIRS, TABLE_ID) + ", " + tableColAs(TABLE_CELLS, CELLS_CID)
    		+ " from " + TABLE_PAIRS + ", " + TABLE_CELLS
    		+ " where " + TABLE_PAIRS + "." + PAIRS_CELL + "=" + TABLE_CELLS + "." + TABLE_ID
    		+ " and "+ PAIRS_NETWORK + "=" + network);
    	return mDb.rawQuery("select " + tableColAs(TABLE_PAIRS, TABLE_ID) + ", " + tableColAs(TABLE_CELLS, CELLS_CID)
    		+ " from " + TABLE_PAIRS + ", " + TABLE_CELLS
    		+ " where " + TABLE_PAIRS + "." + PAIRS_CELL + "=" + TABLE_CELLS + "." + TABLE_ID
    		+ " and "+ PAIRS_NETWORK + "=" + network, null);}
    
    public Cursor fetchPairsByNetworkFilter(int network, int filter, String set) {
    	return mDb.rawQuery("select " + tableColAs(TABLE_PAIRS, TABLE_ID) + ", " + tableColAs(TABLE_CELLS, CELLS_CID) + ", " + tableColAs(TABLE_LOCATIONS, LOCATIONS_LAC) + ", "
    			+ ((filter == FILTER_ALL) ?
    				("CASE WHEN " + TABLE_CELLS + "." + CELLS_CID + " in (" + set + ") then '"
    					+ mContext.getString(R.string.withinarea)
    					+ "' else '" + mContext.getString(R.string.outofarea) + "' end as ")
    				: "'" + (mContext.getString(filter == FILTER_INRANGE ?
    						R.string.accessible
    						: R.string.inaccessible) + "' as "))
    			+ STATUS
    			+ " from " + TABLE_PAIRS + ", " + TABLE_CELLS + ", " + TABLE_LOCATIONS
    			+ " where " + TABLE_PAIRS + "." + PAIRS_CELL + "=" + TABLE_CELLS + "." + TABLE_ID
    			+ " and " + TABLE_CELLS + "." + CELLS_LOCATION + "=" + TABLE_LOCATIONS + "." + TABLE_ID
    			+ " and "+ PAIRS_NETWORK + "=" + network
       	    	+ ((filter != FILTER_ALL) ?
       	   	    		(" and " + TABLE_CELLS + "." + CELLS_CID + (filter == FILTER_OUTRANGE ? " not" : "") + " in (" + set + ")")
       	   	    		: ""), null);}
    
    public int updateNetworkRange(String SSID, String BSSID, int CID, int LAC) {
    	int network = fetchNetworkOrCreate(SSID, BSSID);
    	int location = fetchLocationOrCreate(LAC);
    	int cell = fetchCellOrCreate(CID, location);
    	int pair = fetchPair(cell, network);
    	if (pair < 0) {
    		ContentValues initialValues = new ContentValues();
        	initialValues.put(PAIRS_CELL, cell);
        	initialValues.put(PAIRS_NETWORK, network);
    		cell = (int) mDb.insert(TABLE_PAIRS, null, initialValues);}
    	return network;}
    
    public void updateNetworkNeighbor(int network, int CID, int LAC) {
    	int location = fetchLocationOrCreate(LAC);
    	int cell = fetchCellOrCreate(CID, location);
    	int pair = fetchPair(cell, network);
    	if (pair < 0) {
    		ContentValues initialValues = new ContentValues();
        	initialValues.put(PAIRS_CELL, cell);
        	initialValues.put(PAIRS_NETWORK, network);
    		cell = (int) mDb.insert(TABLE_PAIRS, null, initialValues);}}
    
    public boolean cellInRange(int CID, int LAC) {
    	boolean inRange = false;
    	Cursor c = mDb.rawQuery("select " + TABLE_ID + ", " + CELLS_LOCATION
				+ " from " + TABLE_CELLS
				+ " where " + CELLS_CID + "=" + CID, null);
   		inRange = (c.getCount() > 0);
   		if (inRange) {
   			// check LAC, as this is a new column
   			c.moveToFirst();
   			int location = c.getInt(c.getColumnIndex(CELLS_LOCATION));
   			Log.v(TAG,"lac check " + location);
   			if (!(location > -1)) {
   				Log.v(TAG, "adding lac");
   				location = fetchLocationOrCreate(LAC);
   				ContentValues values = new ContentValues();
   				int cell = c.getInt(c.getColumnIndex(TABLE_ID));
   				values.put(CELLS_LOCATION, location);
        		mDb.update(TABLE_CELLS, values, TABLE_ID + "=" + cell, null);}}
	    c.close();
    	return inRange;}
    
    public void cleanCellsLocations(int cell, int location) {
		Cursor c = mDb.rawQuery("select " + TABLE_ID + " from " + TABLE_PAIRS + " where " + PAIRS_CELL + "=" + cell, null);
		if (c.getCount() == 0) {
			// delete this cell
			// get any unused locations
			mDb.delete(TABLE_CELLS, TABLE_ID + "=" + cell, null);
			Cursor l = mDb.rawQuery("select " + TABLE_ID + " from " + TABLE_CELLS + " where " + CELLS_LOCATION + "=" + location, null);
			if (l.getCount() == 0) mDb.delete(TABLE_LOCATIONS, TABLE_ID + "=" + location, null);
			l.close();}
		c.close();}
    
	public void deleteNetwork(int network) {
		// get cells paired with the network
		Cursor p = mDb.rawQuery("select " + tableColAs(TABLE_PAIRS, PAIRS_CELL) + tableColAs(TABLE_CELLS, CELLS_LOCATION)
				+ " from " + TABLE_PAIRS + ", " + TABLE_CELLS
				+ " where " + TABLE_PAIRS + "." + PAIRS_CELL + "=" + TABLE_CELLS + "." + TABLE_ID
				+ " and " + TABLE_PAIRS + "." + PAIRS_NETWORK + "=" + network, null);
		mDb.delete(TABLE_NETWORKS, TABLE_ID + "=" + network, null);
		mDb.delete(TABLE_PAIRS, PAIRS_NETWORK + "=" + network, null);
		// delete any unpaired cells
		if (p.getCount() > 0) {
			p.moveToFirst();
			while (!p.isAfterLast()) {
				cleanCellsLocations(p.getInt(p.getColumnIndex(PAIRS_CELL)), p.getInt(p.getColumnIndex(CELLS_LOCATION)));
				p.moveToNext();}}
		p.close();}

    public void deletePair(int network, int pair) {
		// get paired cell and location
		Cursor p = mDb.rawQuery("select " + tableColAs(TABLE_PAIRS, PAIRS_CELL) + tableColAs(TABLE_CELLS, CELLS_LOCATION)
				+ " from " + TABLE_PAIRS + ", " + TABLE_CELLS
				+ " where " + TABLE_PAIRS + "." + PAIRS_CELL + "=" + TABLE_CELLS + "." + TABLE_ID
				+ " and " + TABLE_PAIRS + "." + TABLE_ID + "=" + pair, null);
		if (p.getCount() > 0) {
			p.moveToFirst();
			cleanCellsLocations(p.getInt(p.getColumnIndex(PAIRS_CELL)), p.getInt(p.getColumnIndex(CELLS_LOCATION)));}
		mDb.delete(TABLE_PAIRS, TABLE_ID + "=" + pair, null);
		Cursor c = fetchPairsByNetwork(network);
    	if (c.getCount() == 0) mDb.delete(TABLE_NETWORKS, TABLE_ID + "=" + network, null);
    	c.close();}}