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

public class WapdroidDbAdapter {
	private static final String DATABASE_NAME = "wapdroid";
	private static final int DATABASE_VERSION = 1;
	private static final String DROP = "DROP TABLE IF EXISTS ";
	public static final String TABLE_ID = "_id";
	public static final String TABLE_CODE = "code";
	private static final String ID_TYPE = " integer primary key autoincrement, ";
	public static final String WAPDROID_NETWORKS = "networks";
	public static final String NETWORKS_SSID = "SSID";
	public static final String WAPDROID_CELLS = "cells";
	public static final String CELLS_CID = "CID";
	public static final String CELLS_NETWORK = "network";
	
	private static final String CREATE_NETWORKS = "create table "
		+ WAPDROID_NETWORKS + " ("
		+ TABLE_ID + ID_TYPE
		+ NETWORKS_SSID + " text not null);";
	private static final String CREATE_CELLS = "create table "
		+ WAPDROID_CELLS + " ("
		+ TABLE_ID + ID_TYPE
		+ CELLS_CID + " integer, "
		+ CELLS_NETWORK + " integer);";
	
	private DatabaseHelper mDbHelper;
	private SQLiteDatabase mDb;

    public final Context mContext;
    
    private static class DatabaseHelper extends SQLiteOpenHelper {
        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);}
        @Override
        public void onCreate(SQLiteDatabase database) {
            database.execSQL(CREATE_NETWORKS);
            database.execSQL(CREATE_CELLS);}
        
        @Override
        public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        	if (oldVersion < 4) {
	        	Cursor c = database.rawQuery("SELECT tbl_name FROM sqlite_master WHERE tbl_name=\"" + WAPDROID_CELLS + "\" AND sql LIKE \"%RSSI%\"", null);
	        	if (c.getCount() > 0) {
	            	c = database.rawQuery("SELECT tbl_name FROM sqlite_master WHERE tbl_name=\"" + WAPDROID_CELLS + "\" AND sql LIKE \"%maxRSSI%\"", null);
	        		if (c.getCount() < 1) {
	        			database.execSQL(DROP + WAPDROID_CELLS + "_bkp;");
	        			database.execSQL("create temporary table " + WAPDROID_CELLS + "_bkp AS SELECT * FROM " + WAPDROID_CELLS + ";");
	        			database.execSQL(DROP + WAPDROID_CELLS + ";");
	        			database.execSQL(CREATE_CELLS);
	        			database.execSQL("INSERT INTO " + WAPDROID_CELLS + " SELECT "
	    	    				+ TABLE_ID + ", " + CELLS_CID + ", LAC, MNC, MCC, RSSI, RSSI, -1"
	    	    				+ " FROM " + WAPDROID_CELLS + "_bkp;");
	        			database.execSQL(DROP + WAPDROID_CELLS + "_bkp;");}}
	        	else {
	    			database.execSQL(DROP + WAPDROID_CELLS + "_bkp;");
	    			database.execSQL("create temporary table " + WAPDROID_CELLS + "_bkp AS SELECT * FROM " + WAPDROID_CELLS + ";");
	    			database.execSQL(DROP + WAPDROID_CELLS + ";");
	    			database.execSQL(CREATE_CELLS);
	    			database.execSQL("INSERT INTO " + WAPDROID_CELLS + " SELECT "
	        				+ TABLE_ID + ", " + CELLS_CID + ", LAC, MNC, MCC, -1, -1, -1"
	        				+ " FROM " + WAPDROID_CELLS + "_bkp;");
	    			database.execSQL(DROP + WAPDROID_CELLS + "_bkp;");}
	        	/*
	        	 * merge the pairs into cells
	        	 * cells to network is now a one to one relationship, due to the use of RSSI
	        	 */
	        	c = database.rawQuery("SELECT tbl_name FROM sqlite_master WHERE tbl_name=\"pairs\"", null);
	        	if (c.getCount() > 0) {
	        		Cursor n = database.rawQuery("SELECT " + TABLE_ID + " FROM " + WAPDROID_NETWORKS, null);
	    	    	if (n.getCount() > 0) {
	    	    		n.moveToFirst();
	    	    		int mNetwork;
	        			ContentValues cellValues;
	    	    		while (!n.isAfterLast()) {
	    	    			// for each network, get paired cells
	    	    			mNetwork = n.getInt(n.getColumnIndex(TABLE_ID));
	    	    			c = database.rawQuery("SELECT " + WAPDROID_CELLS + "." + TABLE_ID + " AS " + TABLE_ID + ", " + CELLS_CID + ", LAC, MNC, MCC, maxRSSI, minRSSI, " + WAPDROID_CELLS + "." + CELLS_NETWORK + " AS " + CELLS_NETWORK
	    	    					+ " FROM " + WAPDROID_CELLS
	    	    					+ " JOIN pairs ON (" + WAPDROID_CELLS + "." + TABLE_ID + "=pairs.cell)"
	    	    					+ " WHERE pairs.network=" + mNetwork, null);
	    	    			if (c.getCount() > 0) {
	    	    				c.moveToFirst();
	    	    				while (!c.isAfterLast()) {
	    	        				cellValues = new ContentValues();
	    	        				if (c.getInt(c.getColumnIndex(CELLS_NETWORK)) > 0) {
	    	        					cellValues.put(CELLS_CID, c.getInt(c.getColumnIndex(CELLS_CID)));
	    	        					cellValues.put("LAC", c.getInt(c.getColumnIndex("LAC")));
	    	        					cellValues.put("MNC", c.getInt(c.getColumnIndex("MNC")));
	    	        					cellValues.put("MCC", c.getInt(c.getColumnIndex("MCC")));
	    	        					cellValues.put("maxRSSI", c.getInt(c.getColumnIndex("maxRSSI")));
	    	        		        	cellValues.put("minRSSI", c.getInt(c.getColumnIndex("minRSSI")));
	    		        				cellValues.put(CELLS_NETWORK, mNetwork);
	    		        				database.insert(WAPDROID_CELLS, null, cellValues);}
	    	        				else {
	    		        				cellValues.put(CELLS_NETWORK, mNetwork);
	    		        				database.update(WAPDROID_CELLS, cellValues, TABLE_ID + "=" + c.getInt(c.getColumnIndex(TABLE_ID)), null);}
	    	    					c.moveToNext();}}
	    	    			n.moveToNext();}}
	    	    	n.close();
	    	    	database.execSQL(DROP + "pairs;");}
	        	c.close();}
        	if (oldVersion < 8) {
            	// fix the RSSI values from neighboring cell info
        		Cursor c = database.query(WAPDROID_CELLS, new String[] {TABLE_ID, "maxRSSI", "minRSSI"}, "maxRSSI" + ">31 OR minRSSI>31", null, null, null, null);
        		if (c.getCount() > 0) {
        			int mCell;
        			int mMaxRSSI;
        			int mMinRSSI;
        			c.moveToFirst();
        			while (!c.isAfterLast()) {
            	    	mCell = c.getInt(c.getColumnIndex(TABLE_ID));
            	    	mMaxRSSI = c.getInt(c.getColumnIndex("maxRSSI"));
            	    	mMinRSSI = c.getInt(c.getColumnIndex("minRSSI"));
            			ContentValues updateValues = new ContentValues();
            			/*
            			 * ASU is reported by the signal strength with a range of 0-31
            			 * RSSI = -113 + 2 * ASU
            			 * NeighboringCellInfo reports a positive RSSI
            			 * and needs to be converted to ASU
            			 * ASU = ((-1 * RSSI) + 113) / 2
            			 */
            			if (mMinRSSI > 31) {
            				mMinRSSI = Math.round(((-1 * mMaxRSSI) + 113) / 2);
            				updateValues.put("minRSSI", mMinRSSI);}
            			if (mMaxRSSI > 31) {
            				mMaxRSSI = Math.round(((-1 * mMaxRSSI) + 113) / 2);
            				// of course, it's likely that the max is now smaller than the min
            				updateValues.put("maxRSSI", (mMaxRSSI < mMinRSSI ? mMinRSSI : mMaxRSSI));}
            			database.update(WAPDROID_CELLS, updateValues, TABLE_ID + "=" + mCell, null);
        				c.moveToNext();}}
        		c.close();}
        	if (oldVersion < 10) {
            	// clean up junk data
	        	Cursor c = database.query("carriers", new String[] {TABLE_ID}, TABLE_CODE + "=\"\"", null, null, null, null);
	        	if (c.getCount() > 0) {
	        		int mCarrier;
	        		c.moveToFirst();
	        		while (!c.isAfterLast()) {
	        			mCarrier = c.getInt(c.getColumnIndex(TABLE_ID));
	        			database.delete(WAPDROID_CELLS, "MNC=" + mCarrier, null);
	            		database.delete("carriers", TABLE_ID + "=" + mCarrier, null);
	        			c.moveToNext();}}
	        	c = database.query("countries", new String[] {TABLE_ID}, TABLE_CODE + "=\"\"", null, null, null, null);
	        	if (c.getCount() > 0) {
	        		int mCountry;
	        		c.moveToFirst();
	        		while (!c.isAfterLast()) {
	        			mCountry = c.getInt(c.getColumnIndex(TABLE_ID));
	        			database.delete(WAPDROID_CELLS, "MCC=" + mCountry, null);
	            		database.delete("countries", TABLE_ID + "=" + mCountry, null);
	        			c.moveToNext();}}
	        	c.close();}
        	if (oldVersion < 14) {
        		// drop LAC, MNC, MCC, maxRSSI, minRSSI columns from cells
    			database.execSQL(DROP + WAPDROID_CELLS + "_bkp;");
    			database.execSQL("create temporary table " + WAPDROID_CELLS + "_bkp AS SELECT * FROM " + WAPDROID_CELLS + ";");
    			database.execSQL(DROP + WAPDROID_CELLS + ";");
    			database.execSQL(CREATE_CELLS);
    			database.execSQL("INSERT INTO " + WAPDROID_CELLS + " SELECT "
        				+ TABLE_ID + ", " + CELLS_CID + ", " + CELLS_NETWORK
        				+ " FROM " + WAPDROID_CELLS + "_bkp;");
    			database.execSQL(DROP + WAPDROID_CELLS + "_bkp;");
    			// merge neighbors into cells and then drop neighbors
	        	Cursor c = database.query("neighbors", new String[] {TABLE_ID, CELLS_CID, CELLS_NETWORK}, null, null, null, null, null);
	        	if (c.getCount() > 0) {
	        		int cid, network;
	        		Cursor n = null;
	        		ContentValues contentValues;
	        		c.moveToFirst();
	        		while (!c.isAfterLast()) {
	        			cid = c.getInt(c.getColumnIndex(CELLS_CID));
	        			network = c.getInt(c.getColumnIndex(CELLS_NETWORK));
	        			n = database.query(WAPDROID_CELLS, new String[] {TABLE_ID}, CELLS_CID + "=" + cid + " AND " + CELLS_NETWORK + "=" + network, null, null, null, null);
	        			if (n.getCount() == 0) {
	        				contentValues = new ContentValues();
	        				if (c.getInt(c.getColumnIndex(CELLS_NETWORK)) > 0) {
	        					contentValues.put(CELLS_CID, cid);
	        					contentValues.put(CELLS_NETWORK, network);
		        				database.insert(WAPDROID_CELLS, null, contentValues);}}
	        			c.moveToNext();}
	        		if (n != null) {
	        			n.close();}}
	        	c.close();
    			database.execSQL(DROP + "neighbors;");}}}
    
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
        mDb.execSQL(CREATE_CELLS);}
    
    public int fetchNetwork(String mSSID) {
    	int mNetwork = -1;
    	Cursor c = mDb.rawQuery("SELECT " + TABLE_ID + " FROM " + WAPDROID_NETWORKS + " WHERE " + NETWORKS_SSID + "=\"" + mSSID + "\"", null);
    	if (c.getCount() > 0) {
    		c.moveToFirst();
    		mNetwork = c.getInt(c.getColumnIndex(TABLE_ID));}
    	c.close();
    	return mNetwork;}
    
    public int fetchNetworkOrCreate(String mSSID) {
    	int mNetwork = fetchNetwork(mSSID);
    	if (mNetwork < 0) {
    		ContentValues initialValues = new ContentValues();
    		initialValues.put(NETWORKS_SSID, mSSID);
    		mNetwork = (int) mDb.insert(WAPDROID_NETWORKS, null, initialValues);}
    	return mNetwork;}

    public Cursor fetchNetworks() {
    	return mDb.rawQuery("SELECT " + TABLE_ID + ", " + NETWORKS_SSID
    			+ " FROM " + WAPDROID_NETWORKS, null);}
    
    public int fetchCell(int mCID, int mNetwork) {
    	int mCell = -1;
    	Cursor c = mDb.rawQuery("SELECT " + TABLE_ID
    			+ " FROM " + WAPDROID_CELLS	+ " WHERE " + CELLS_CID + "=" + mCID
    			+ " AND " + CELLS_NETWORK + "=" + mNetwork, null);
    	if (c.getCount() > 0) {
    		c.moveToFirst();
    		mCell = c.getInt(c.getColumnIndex(TABLE_ID));}
    	c.close();
    	return mCell;}
    
    public Cursor fetchCellsByNetwork(int mNetwork) {
    	return mDb.rawQuery("SELECT " + WAPDROID_CELLS + "." + TABLE_ID + ", " + CELLS_CID
    			+ " FROM " + WAPDROID_CELLS
    			+ " WHERE " + CELLS_NETWORK + "=" + mNetwork
    			+ " ORDER BY " + CELLS_CID, null);}
    
    public void updateCellRange(String mSSID, int mCID) {
    	int mNetwork = fetchNetworkOrCreate(mSSID);
    	int mCell = fetchCell(mCID, mNetwork);
    	if (mCell < 0) {
    		ContentValues initialValues = new ContentValues();
        	initialValues.put(CELLS_CID, mCID);
        	initialValues.put(CELLS_NETWORK, mNetwork);
    		mCell = (int) mDb.insert(WAPDROID_CELLS, null, initialValues);}}
    
    public boolean cellInRange(int mCID) {
    	boolean inRange = false;
    	Cursor c = mDb.rawQuery("SELECT " + TABLE_ID
				+ ", " + CELLS_NETWORK
				+ " FROM " + WAPDROID_CELLS
				+ " WHERE " + CELLS_CID + "=" + mCID, null);;
   		inRange = (c.getCount() > 0);
	    c.close();
    	return inRange;}
    
	public void deleteNetwork(int mNetwork) {
		mDb.delete(WAPDROID_CELLS, CELLS_NETWORK + "=" + mNetwork, null);
		mDb.delete(WAPDROID_NETWORKS, TABLE_ID + "=" + mNetwork, null);}

    public void deleteCell(int mNetwork, int mCell) {
		mDb.delete(WAPDROID_CELLS, CELLS_NETWORK + "=" + mNetwork, null);
		Cursor c = fetchCellsByNetwork(mNetwork);
    	if (c.getCount() == 0) {
    		mDb.delete(WAPDROID_NETWORKS, TABLE_ID + "=" + mNetwork, null);}
    	c.close();}}