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
	private static final int DATABASE_VERSION = 12;
	private static final String DROP = "DROP TABLE IF EXISTS ";
	public static final String TABLE_ID = "_id";
	public static final String TABLE_CODE = "code";
	private static final String ID_TYPE = " integer primary key autoincrement, ";
	public static final String WAPDROID_NETWORKS = "networks";
	public static final String NETWORKS_SSID = "SSID";
	public static final String WAPDROID_CELLS = "cells";
	public static final String CELLS_CID = "CID";
	public static final String CELLS_LAC = "LAC";
	public static final String CELLS_MNC = "MNC";
	public static final String CELLS_MCC = "MCC";
	public static final String CELLS_MAXRSSI = "maxRSSI";
	public static final String CELLS_MINRSSI = "minRSSI";
	public static final String CELLS_NETWORK = "network";
	public static final String WAPDROID_LOCATIONS = "locations";
	public static final String WAPDROID_CARRIERS = "carriers";
	public static final String WAPDROID_COUNTRIES = "countries";
	public static final String WAPDROID_NEIGHBORS = "neighbors";
	
	private static final String CREATE_NETWORKS = "create table "
		+ WAPDROID_NETWORKS + " ("
		+ TABLE_ID + ID_TYPE
		+ NETWORKS_SSID + " text not null);";
	private static final String CREATE_CELLS = "create table "
		+ WAPDROID_CELLS + " ("
		+ TABLE_ID + ID_TYPE
		+ CELLS_CID + " integer, "
		+ CELLS_LAC + " integer, "
		+ CELLS_MNC + " integer, "
		+ CELLS_MCC + " integer, "
		+ CELLS_MAXRSSI + " integer, "
		+ CELLS_MINRSSI + " integer, "
		+ CELLS_NETWORK + " integer);";
	private static final String CREATE_NEIGHBORS = "create table "
		+ WAPDROID_NEIGHBORS + " ("
		+ TABLE_ID + ID_TYPE
		+ CELLS_CID + " integer, "
		+ CELLS_NETWORK + " integer, "
		+ CELLS_MAXRSSI + " integer, "
		+ CELLS_MINRSSI + " integer);";
	private static final String CREATE_LOCATIONS = "create table "
		+ WAPDROID_LOCATIONS + " ("
		+ TABLE_ID + ID_TYPE
		+ TABLE_CODE + " integer);";
	private static final String CREATE_CARRIERS = "create table "
		+ WAPDROID_CARRIERS + " ("
		+ TABLE_ID + ID_TYPE
		+ TABLE_CODE + " text not null);";
	private static final String CREATE_COUNTRIES = "create table "
		+ WAPDROID_COUNTRIES + " ("
		+ TABLE_ID + ID_TYPE
		+ TABLE_CODE + " text not null);";
	
	private DatabaseHelper mDbHelper;
	private SQLiteDatabase mDb;

    public final Context mContext;
    
    private static class DatabaseHelper extends SQLiteOpenHelper {
        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);}
        @Override
        public void onCreate(SQLiteDatabase database) {
            database.execSQL(CREATE_NETWORKS);
            database.execSQL(CREATE_CELLS);
            database.execSQL(CREATE_NEIGHBORS);
            database.execSQL(CREATE_LOCATIONS);
            database.execSQL(CREATE_CARRIERS);
            database.execSQL(CREATE_COUNTRIES);}
        
        @Override
        public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        	if (oldVersion < 4) {
	        	Cursor c = database.rawQuery("SELECT tbl_name FROM sqlite_master WHERE tbl_name=\"" + WAPDROID_CELLS + "\" AND sql LIKE \"%RSSI%\"", null);
	        	if (c.getCount() > 0) {
	            	c = database.rawQuery("SELECT tbl_name FROM sqlite_master WHERE tbl_name=\"" + WAPDROID_CELLS + "\" AND sql LIKE \"%" + CELLS_MAXRSSI + "%\"", null);
	        		if (c.getCount() < 1) {
	        			database.execSQL(DROP + WAPDROID_CELLS + "_bkp;");
	        			database.execSQL("create temporary table " + WAPDROID_CELLS + "_bkp AS SELECT * FROM " + WAPDROID_CELLS + ";");
	        			database.execSQL(DROP + WAPDROID_CELLS + ";");
	        			database.execSQL(CREATE_CELLS);
	        			database.execSQL("INSERT INTO " + WAPDROID_CELLS + " SELECT "
	    	    				+ TABLE_ID + ", " + CELLS_CID + ", " + CELLS_LAC + ", " + CELLS_MNC + ", " + CELLS_MCC + ", RSSI, RSSI, -1"
	    	    				+ " FROM " + WAPDROID_CELLS + "_bkp;");
	        			database.execSQL(DROP + WAPDROID_CELLS + "_bkp;");}}
	        	else {
	    			database.execSQL(DROP + WAPDROID_CELLS + "_bkp;");
	    			database.execSQL("create temporary table " + WAPDROID_CELLS + "_bkp AS SELECT * FROM " + WAPDROID_CELLS + ";");
	    			database.execSQL(DROP + WAPDROID_CELLS + ";");
	    			database.execSQL(CREATE_CELLS);
	    			database.execSQL("INSERT INTO " + WAPDROID_CELLS + " SELECT "
	        				+ TABLE_ID + ", " + CELLS_CID + ", " + CELLS_LAC + ", " + CELLS_MNC + ", " + CELLS_MCC + ", -1, -1, -1"
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
	    	    			c = database.rawQuery("SELECT " + WAPDROID_CELLS + "." + TABLE_ID + " AS " + TABLE_ID + ", " + CELLS_CID + ", " + CELLS_LAC + ", " + CELLS_MNC + ", " + CELLS_MCC + ", " + CELLS_MAXRSSI + ", " + CELLS_MINRSSI + ", " + WAPDROID_CELLS + "." + CELLS_NETWORK + " AS " + CELLS_NETWORK
	    	    					+ " FROM " + WAPDROID_CELLS
	    	    					+ " JOIN pairs ON (" + WAPDROID_CELLS + "." + TABLE_ID + "=pairs.cell)"
	    	    					+ " WHERE pairs.network=" + mNetwork, null);
	    	    			if (c.getCount() > 0) {
	    	    				c.moveToFirst();
	    	    				while (!c.isAfterLast()) {
	    	        				cellValues = new ContentValues();
	    	        				if (c.getInt(c.getColumnIndex(CELLS_NETWORK)) > 0) {
	    	        					cellValues.put(CELLS_CID, c.getInt(c.getColumnIndex(CELLS_CID)));
	    	        					cellValues.put(CELLS_LAC, c.getInt(c.getColumnIndex(CELLS_LAC)));
	    	        					cellValues.put(CELLS_MNC, c.getInt(c.getColumnIndex(CELLS_MNC)));
	    	        					cellValues.put(CELLS_MCC, c.getInt(c.getColumnIndex(CELLS_MCC)));
	    	        					cellValues.put(CELLS_MAXRSSI, c.getInt(c.getColumnIndex(CELLS_MAXRSSI)));
	    	        		        	cellValues.put(CELLS_MINRSSI, c.getInt(c.getColumnIndex(CELLS_MINRSSI)));
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
        		Cursor c = database.query(WAPDROID_CELLS, new String[] {TABLE_ID, CELLS_MAXRSSI, CELLS_MINRSSI}, CELLS_MAXRSSI + ">31 OR " + CELLS_MINRSSI + ">31", null, null, null, null);
        		if (c.getCount() > 0) {
        			int mCell;
        			int mMaxRSSI;
        			int mMinRSSI;
        			c.moveToFirst();
        			while (!c.isAfterLast()) {
            	    	mCell = c.getInt(c.getColumnIndex(TABLE_ID));
            	    	mMaxRSSI = c.getInt(c.getColumnIndex(CELLS_MAXRSSI));
            	    	mMinRSSI = c.getInt(c.getColumnIndex(CELLS_MINRSSI));
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
            				updateValues.put(CELLS_MINRSSI, mMinRSSI);}
            			if (mMaxRSSI > 31) {
            				mMaxRSSI = Math.round(((-1 * mMaxRSSI) + 113) / 2);
            				// of course, it's likely that the max is now smaller than the min
            				updateValues.put(CELLS_MAXRSSI, (mMaxRSSI < mMinRSSI ? mMinRSSI : mMaxRSSI));}
            			database.update(WAPDROID_CELLS, updateValues, TABLE_ID + "=" + mCell, null);
        				c.moveToNext();}}
        		c.close();}
        	if (oldVersion < 10) {
            	// clean up junk data
	        	Cursor c = database.query(WAPDROID_CARRIERS, new String[] {TABLE_ID}, TABLE_CODE + "=\"\"", null, null, null, null);
	        	if (c.getCount() > 0) {
	        		int mCarrier;
	        		c.moveToFirst();
	        		while (!c.isAfterLast()) {
	        			mCarrier = c.getInt(c.getColumnIndex(TABLE_ID));
	        			database.delete(WAPDROID_CELLS, CELLS_MNC + "=" + mCarrier, null);
	            		database.delete(WAPDROID_CARRIERS, TABLE_ID + "=" + mCarrier, null);
	        			c.moveToNext();}}
	        	c = database.query(WAPDROID_COUNTRIES, new String[] {TABLE_ID}, TABLE_CODE + "=\"\"", null, null, null, null);
	        	if (c.getCount() > 0) {
	        		int mCountry;
	        		c.moveToFirst();
	        		while (!c.isAfterLast()) {
	        			mCountry = c.getInt(c.getColumnIndex(TABLE_ID));
	        			database.delete(WAPDROID_CELLS, CELLS_MCC + "=" + mCountry, null);
	            		database.delete(WAPDROID_COUNTRIES, TABLE_ID + "=" + mCountry, null);
	        			c.moveToNext();}}
	        	c.close();}
        	if (oldVersion < 13) {
                database.execSQL(DROP + WAPDROID_NEIGHBORS + ";");
                database.execSQL(CREATE_NEIGHBORS);}}}
    
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
        mDb.execSQL(CREATE_NEIGHBORS);
        mDb.execSQL(CREATE_LOCATIONS);
        mDb.execSQL(CREATE_CARRIERS);
        mDb.execSQL(CREATE_COUNTRIES);}
    
    public void resetDatabase() {
        mDb.execSQL(DROP + WAPDROID_NETWORKS + ";");
        mDb.execSQL(DROP + WAPDROID_CELLS + ";");
        mDb.execSQL(DROP + WAPDROID_NEIGHBORS + ";");
        mDb.execSQL(DROP + WAPDROID_LOCATIONS + ";");
        mDb.execSQL(DROP + WAPDROID_CARRIERS + ";");
        mDb.execSQL(DROP + WAPDROID_COUNTRIES + ";");
        createTables();}
    
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
    
    public int fetchCell(int mCID, int mLAC, int mMNC, int mMCC, int mNetwork) {
    	int mCell = -1;
    	Cursor c = mDb.rawQuery("SELECT " + TABLE_ID
    			+ " FROM " + WAPDROID_CELLS	+ " WHERE " + CELLS_CID + "=" + mCID
    			+ " AND " + CELLS_LAC + "=" + mLAC
    			+ " AND " + CELLS_MNC + "=" + mMNC
    			+ " AND " + CELLS_MCC + "=" + mMCC
    			+ " AND " + CELLS_NETWORK + "=" + mNetwork, null);
    	if (c.getCount() > 0) {
    		c.moveToFirst();
    		mCell = c.getInt(c.getColumnIndex(TABLE_ID));}
    	c.close();
    	return mCell;}
    
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
    
    public Cursor fetchCellInfo(int mCell) {
    	return mDb.rawQuery("SELECT " + CELLS_LAC + ", " + CELLS_MNC + ", " + CELLS_MCC
    			+ " FROM " + WAPDROID_CELLS
    			+ " WHERE " + TABLE_ID + "=" + mCell, null);}
    
    public int fetchLocation(int mLAC) {
    	int mLocation = -1;
       	Cursor c = mDb.rawQuery("SELECT " + TABLE_ID + " FROM " + WAPDROID_LOCATIONS + " WHERE " + TABLE_CODE + "=" + mLAC, null);
        if (c.getCount() > 0) {
        	c.moveToFirst();
        	mLocation = c.getInt(c.getColumnIndex(TABLE_ID));}
        c.close();
    	return mLocation;}

    public int fetchLocationOrCreate(int mLAC) {
    	int mLocation = fetchLocation(mLAC);
    	if (mLocation < 0) {
    		ContentValues initialValues = new ContentValues();
        	initialValues.put(TABLE_CODE, mLAC);
    		mLocation = (int) mDb.insert(WAPDROID_LOCATIONS, null, initialValues);}
    	return mLocation;}
    
    public int fetchCarrier(String mMNC) {
    	int mCarrier = -1;
       	Cursor c = mDb.rawQuery("SELECT " + TABLE_ID + " FROM " + WAPDROID_CARRIERS + " WHERE " + TABLE_CODE + "=\"" + mMNC + "\"", null);
        if (c.getCount() > 0) {
        	c.moveToFirst();
        	mCarrier = c.getInt(c.getColumnIndex(TABLE_ID));}
        c.close();
    	return mCarrier;}

    public int fetchCarrierOrCreate(String mMNC) {
    	int mCarrier = fetchCarrier(mMNC);
    	if (mCarrier < 0) {
    		ContentValues initialValues = new ContentValues();
        	initialValues.put(TABLE_CODE, mMNC);
    		mCarrier = (int) mDb.insert(WAPDROID_CARRIERS, null, initialValues);}
    	return mCarrier;}

    public int fetchCountry(String mMCC) {
    	int mCountry = -1;
       	Cursor c = mDb.rawQuery("SELECT " + TABLE_ID + " FROM " + WAPDROID_COUNTRIES + " WHERE " + TABLE_CODE + "=\"" + mMCC + "\"", null);
        if (c.getCount() > 0) {
        	c.moveToFirst();
        	mCountry = c.getInt(c.getColumnIndex(TABLE_ID));}
        c.close();
    	return mCountry;}

    public int fetchCountryOrCreate(String mMCC) {
    	int mCountry = fetchCountry(mMCC);
    	if (mCountry < 0) {
    		ContentValues initialValues = new ContentValues();
        	initialValues.put(TABLE_CODE, mMCC);
    		mCountry = (int) mDb.insert(WAPDROID_COUNTRIES, null, initialValues);}
    	return mCountry;}
    
    public Cursor fetchCellsByNetwork(int mNetwork) {
    	return mDb.rawQuery("SELECT " + WAPDROID_CELLS + "." + TABLE_ID + ", " + CELLS_CID + ", "
    			+ WAPDROID_CARRIERS + "." + TABLE_CODE + " AS " + CELLS_MNC + ", "
    			+ WAPDROID_COUNTRIES + "." + TABLE_CODE + " AS " + CELLS_MCC + ", "
    			+ WAPDROID_LOCATIONS + "." + TABLE_CODE + " AS " + CELLS_LAC + ", (-113 + 2 * "
    			+ CELLS_MINRSSI + ")||\"dBm\" AS " + CELLS_MINRSSI + ", (-113 + 2 * "
    			+ CELLS_MAXRSSI + ")||\"dBm\" AS " + CELLS_MAXRSSI
    			+ " FROM " + WAPDROID_CELLS
    			+ " JOIN " + WAPDROID_CARRIERS + " ON (" + WAPDROID_CELLS + "." + CELLS_MNC + "=" + WAPDROID_CARRIERS + "." + TABLE_ID
    			+ ") JOIN " + WAPDROID_COUNTRIES + " ON (" + WAPDROID_CELLS + "." + CELLS_MCC + "=" + WAPDROID_COUNTRIES + "." + TABLE_ID
    			+ ") JOIN " + WAPDROID_LOCATIONS + " ON (" + WAPDROID_CELLS + "." + CELLS_LAC + "=" + WAPDROID_LOCATIONS + "." + TABLE_ID
    			+ ") WHERE " + WAPDROID_CELLS + "." + CELLS_NETWORK + "=" + mNetwork
    			+ " ORDER BY " + CELLS_CID, null);}
    
    public Cursor fetchCellsByLAC(int mLAC) {
    	return mDb.rawQuery("SELECT " + TABLE_ID
    			+ " FROM " + WAPDROID_CELLS
    			+ " WHERE " + CELLS_LAC + "=" + mLAC, null);}
    
    public Cursor fetchCellsByMNC(int mMNC) {
    	return mDb.rawQuery("SELECT " + TABLE_ID
    			+ " FROM " + WAPDROID_CELLS
    			+ " WHERE " + CELLS_MNC + "=" + mMNC, null);}
    
    public Cursor fetchCellsByMCC(int mMCC) {
    	return mDb.rawQuery("SELECT " + TABLE_ID
    			+ " FROM " + WAPDROID_CELLS
    			+ " WHERE " + CELLS_MCC + "=" + mMCC, null);}
    
    public int fetchNeighbor(int mNetwork, int mCID) {
    	int mNeighbor = -1;
    	Cursor c = mDb.rawQuery("SELECT " + TABLE_ID
    			+ " FROM " + WAPDROID_NEIGHBORS
    			+ " WHERE " + CELLS_CID + "=" + mCID
    			+ " AND " + CELLS_NETWORK + "=" + mNetwork, null);
    	if (c.getCount() > 0) {
    		c.moveToFirst();
    		mNeighbor = c.getInt(c.getColumnIndex(TABLE_ID));}
    	c.close();
    	return mNeighbor;}
    
    public void updateRange(String mTable, int mId, int mRSSI) {
    	Cursor c = mDb.rawQuery("SELECT " + TABLE_ID + ", " + CELLS_MAXRSSI + ", " + CELLS_MINRSSI
    			+ " FROM " + mTable
    			+ " WHERE " + TABLE_ID + "=" + mId
				+ " AND (" + CELLS_MAXRSSI + "<" + mRSSI
				+ " OR " + CELLS_MINRSSI + ">" + mRSSI + ")", null);
    	if (c.getCount() > 0) {
    		c.moveToFirst();
	    	int mMaxRSSI = c.getInt(c.getColumnIndex(CELLS_MAXRSSI));
	    	int mMinRSSI = c.getInt(c.getColumnIndex(CELLS_MINRSSI));
			ContentValues updateValues = new ContentValues();
			if (mMaxRSSI < mRSSI) {
				updateValues.put(CELLS_MAXRSSI, mRSSI);}
			if (mMinRSSI > mRSSI) {
				updateValues.put(CELLS_MINRSSI, mRSSI);}
			mDb.update(mTable, updateValues, TABLE_ID + "=" + mId, null);}
	    	c.close();}
    
    public void updateNeighborRange(String mSSID, int mCID, int mRSSI) {
    	int mNetwork = fetchNetwork(mSSID);
       	int mNeighbor = fetchNeighbor(mNetwork, mCID);
    	if (mNeighbor < 0) {
    		ContentValues initialValues = new ContentValues();
        	initialValues.put(CELLS_CID, mCID);
        	initialValues.put(CELLS_MAXRSSI, mRSSI);
        	initialValues.put(CELLS_MINRSSI, mRSSI);
        	initialValues.put(CELLS_NETWORK, mNetwork);
    		mNeighbor = (int) mDb.insert(WAPDROID_NEIGHBORS, null, initialValues);}
    	else {
    		updateRange(WAPDROID_NEIGHBORS, mNeighbor, mRSSI);}}
    
    public void updateCellRange(String mSSID, int mCID, int mLAC, String mMNC, String mMCC, int mRSSI) {
    	int mLocation = fetchLocationOrCreate(mLAC);
    	int mCarrier = fetchCarrierOrCreate(mMNC);
    	int mCountry = fetchCountryOrCreate(mMCC);
    	int mNetwork = fetchNetworkOrCreate(mSSID);
    	int mCell = fetchCell(mCID, mLocation, mCarrier, mCountry, mNetwork);
    	if (mCell < 0) {
    		ContentValues initialValues = new ContentValues();
        	initialValues.put(CELLS_CID, mCID);
        	initialValues.put(CELLS_LAC, mLocation);
        	initialValues.put(CELLS_MNC, mCarrier);
        	initialValues.put(CELLS_MCC, mCountry);
        	initialValues.put(CELLS_MAXRSSI, mRSSI);
        	initialValues.put(CELLS_MINRSSI, mRSSI);
        	initialValues.put(CELLS_NETWORK, mNetwork);
    		mCell = (int) mDb.insert(WAPDROID_CELLS, null, initialValues);}
    	else {
    		updateRange(WAPDROID_CELLS, mCell, mRSSI);}}
    
    public boolean neighborInRange(int mCID, int mRSSI) {
    	Cursor c = mDb.rawQuery("SELECT " + TABLE_ID
				+ " FROM " + WAPDROID_NEIGHBORS
    			+ " WHERE " + CELLS_CID + "=" + mCID
				+ " AND ((" + CELLS_MAXRSSI + ">=" + mRSSI
				+ " AND " + CELLS_MINRSSI + "<=" + mRSSI + ") OR ("
				+ CELLS_MAXRSSI + "=" + CELLS_MINRSSI + "))", null);
    	boolean mInRange = (c.getCount() > 0);
	    c.close();
    	return mInRange;}
    
    public boolean hasNeighbors(int mNetwork) {
    	boolean mNeighbors = false;
		Cursor c = mDb.rawQuery("SELECT " + TABLE_ID
				+ " FROM " + WAPDROID_NEIGHBORS
    			+ " WHERE " + CELLS_NETWORK + "=" + mNetwork, null);
    	mNeighbors = (c.getCount() > 0);
    	c.close();
    	return mNeighbors;}
    
    public Cursor cellsInRange(int mCID, int mRSSI) {
		return mDb.rawQuery("SELECT " + TABLE_ID
				+ ", " + CELLS_NETWORK
				+ " FROM " + WAPDROID_CELLS
				+ " WHERE " + CELLS_CID + "=" + mCID
				+ " AND ((" + CELLS_MAXRSSI + ">=" + mRSSI
				+ " AND " + CELLS_MINRSSI + "<=" + mRSSI + ") OR ("
				+ CELLS_MAXRSSI + "=" + CELLS_MINRSSI + "))", null);}
    
    public void cleanLocation(int mLocation) {
		Cursor c = fetchCellsByLAC(mLocation);
		if (c.getCount() == 0) {
			mDb.delete(WAPDROID_LOCATIONS, TABLE_ID + "=" + mLocation, null);}
		c.close();}
    
    public void cleanCarrier(int mCarrier) {
		Cursor c = fetchCellsByMNC(mCarrier);
		if (c.getCount() == 0) {
			mDb.delete(WAPDROID_CARRIERS, TABLE_ID + "=" + mCarrier, null);}
		c.close();}
    
    public void cleanCountry(int mCountry) {
		Cursor c = fetchCellsByMCC(mCountry);
		if (c.getCount() == 0) {
			mDb.delete(WAPDROID_COUNTRIES, TABLE_ID + "=" + mCountry, null);}
		c.close();}
    
	public void cleanCell(int mCell) {
    	Cursor c = fetchCellInfo(mCell);
    	if (c.getCount() > 0) {
    		c.moveToFirst();
    		int mLocation = c.getInt(c.getColumnIndex(CELLS_LAC));
    		int mCarrier = c.getInt(c.getColumnIndex(CELLS_MNC));
    		int mCountry = c.getInt(c.getColumnIndex(CELLS_MCC));
    		mDb.delete(WAPDROID_CELLS, TABLE_ID + "=" + mCell, null);
    		cleanLocation(mLocation);
    		cleanCarrier(mCarrier);
    		cleanCountry(mCountry);}
    	c.close();}
	
	public Cursor fetchCellsToDelete(int mNetwork) {
		return mDb.rawQuery("SELECT " + TABLE_ID + " FROM " + WAPDROID_CELLS + " WHERE " + CELLS_NETWORK + "=" + mNetwork, null);}
	
	public void deleteNetwork(int mNetwork) {
		Cursor c = fetchCellsToDelete(mNetwork);
		if (c.getCount() > 0) {
			c.moveToFirst();
			while (!c.isAfterLast()) {
				cleanCell(c.getInt(c.getColumnIndex(TABLE_ID)));
				c.moveToNext();}}
		c.close();
		mDb.delete(WAPDROID_NEIGHBORS, CELLS_NETWORK + "=" + mNetwork, null);
		mDb.delete(WAPDROID_NETWORKS, TABLE_ID + "=" + mNetwork, null);}

    public void deleteCell(int mNetwork, int mCell) {
    	cleanCell(mCell);
		Cursor c = fetchCellsToDelete(mNetwork);
    	if (c.getCount() == 0) {
    		mDb.delete(WAPDROID_NEIGHBORS, CELLS_NETWORK + "=" + mNetwork, null);
    		mDb.delete(WAPDROID_NETWORKS, TABLE_ID + "=" + mNetwork, null);}
    	c.close();}}