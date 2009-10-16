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
	public static final String TAG = "WapdroidDbAdapter";
	private static final String DROP = "DROP TABLE IF EXISTS ";
	public static final String WAPDROID = "wapdroid";
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
            super(context, WAPDROID, null, 2);}
        @Override
        public void onCreate(SQLiteDatabase database) {
            database.execSQL(CREATE_NETWORKS);
            database.execSQL(CREATE_CELLS);
            database.execSQL(CREATE_LOCATIONS);
            database.execSQL(CREATE_CARRIERS);
            database.execSQL(CREATE_COUNTRIES);}
        
        @Override
        public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
            database.execSQL(DROP + WAPDROID_NETWORKS + ";");
            database.execSQL(DROP + WAPDROID_CELLS + ";");
            database.execSQL(DROP + WAPDROID_LOCATIONS + ";");
            database.execSQL(DROP + WAPDROID_CARRIERS + ";");
            database.execSQL(DROP + WAPDROID_COUNTRIES + ";");
            onCreate(database);}}
    
    public WapdroidDbAdapter(Context context) {
        this.mContext = context;}
    
    public WapdroidDbAdapter open() throws SQLException {
        mDbHelper = new DatabaseHelper(mContext);
        mDb = mDbHelper.getWritableDatabase();
        return this;}

    public void close() {
        mDbHelper.close();}
    
    public boolean queryMaster(String mTable, String mSQL) {
    	boolean pass = false;
    	Cursor c = mDb.rawQuery("SELECT tbl_name FROM sqlite_master WHERE tbl_name=\"" + mTable + (mSQL == null ? "\"" : ("\" AND sql LIKE \"%" + mSQL + "%\"")), null);
    	if (c.getCount() > 0) {
    		pass = true;}
    	c.close();
    	return pass;}
    
    public void upgradeTable(String mTable, String mCreate, String mInsert) {
		mDb.execSQL(DROP + mTable + "_bkp;");
		mDb.execSQL("create temporary table " + mTable + "_bkp AS SELECT * FROM " + mTable + ";");
		mDb.execSQL(DROP + mTable + ";");
		mDb.execSQL(mCreate);
		mDb.execSQL(mInsert);
		mDb.execSQL(DROP + mTable + "_bkp;");}
    
    public void upgradeDatabase() {
    	if (queryMaster(WAPDROID_CELLS, "RSSI")) {
    		if (!queryMaster(WAPDROID_CELLS, CELLS_MAXRSSI)) {
	    		upgradeTable(WAPDROID_CELLS, CREATE_CELLS, "INSERT INTO " + WAPDROID_CELLS + " SELECT "
	    				+ TABLE_ID + ", " + CELLS_CID + ", " + CELLS_LAC + ", " + CELLS_MNC + ", " + CELLS_MCC + ", RSSI, RSSI, -1"
	    				+ " FROM " + WAPDROID_CELLS + "_bkp;");}}
    	else {
    		upgradeTable(WAPDROID_CELLS, CREATE_CELLS, "INSERT INTO " + WAPDROID_CELLS + " SELECT "
    				+ TABLE_ID + ", " + CELLS_CID + ", " + CELLS_LAC + ", " + CELLS_MNC + ", " + CELLS_MCC + ", -1, -1, -1"
    				+ " FROM " + WAPDROID_CELLS + "_bkp;");}
    	/*
    	 * merge the pairs into cells
    	 * cells to network is now a one to one relationship, due to the use of RSSI
    	 */
    	if (queryMaster("pairs", null)) {
    		Cursor n = mDb.rawQuery("SELECT " + TABLE_ID + " FROM " + WAPDROID_NETWORKS, null);
	    	if (n.getCount() > 0) {
	    		n.moveToFirst();
	    		int mNetwork;
	    		Cursor c = null;
    			ContentValues cellValues;
	    		while (!n.isAfterLast()) {
	    			// for each network, get paired cells
	    			mNetwork = n.getInt(n.getColumnIndex(TABLE_ID));
	    			c = mDb.rawQuery("SELECT " + WAPDROID_CELLS + "." + TABLE_ID + " AS " + TABLE_ID + ", " + CELLS_CID + ", " + CELLS_LAC + ", " + CELLS_MNC + ", " + CELLS_MCC + ", " + CELLS_MAXRSSI + ", " + CELLS_MINRSSI + ", " + WAPDROID_CELLS + "." + CELLS_NETWORK + " AS " + CELLS_NETWORK
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
		        	    		mDb.insert(WAPDROID_CELLS, null, cellValues);}
	        				else {
		        				cellValues.put(CELLS_NETWORK, mNetwork);
			    				mDb.update(WAPDROID_CELLS, cellValues, TABLE_ID + "=" + c.getInt(c.getColumnIndex(TABLE_ID)), null);}
	    					c.moveToNext();}}
	    			n.moveToNext();}
	    		if (c != null) {
	    		c.close();}}
	    	n.close();
	    	mDb.execSQL(DROP + "pairs;");}}
    
    public void createTables() {
        mDb.execSQL(CREATE_NETWORKS);
        mDb.execSQL(CREATE_CELLS);
        mDb.execSQL(CREATE_LOCATIONS);
        mDb.execSQL(CREATE_CARRIERS);
        mDb.execSQL(CREATE_COUNTRIES);}
    
    public void resetDatabase() {
        mDb.execSQL(DROP + WAPDROID_NETWORKS + ";");
        mDb.execSQL(DROP + WAPDROID_CELLS + ";");
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
    	Cursor c = mDb.rawQuery("SELECT " + WAPDROID_CELLS + "." + TABLE_ID
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
    			+ ") WHERE " + WAPDROID_CELLS + "." + CELLS_NETWORK + "=" + mNetwork, null);}
    
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
    
    public void fetchCellOrCreate(String mSSID, int mCID, int mLAC, String mMNC, String mMCC, int mRSSI) {
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
   	    	Cursor c = mDb.rawQuery("SELECT " + CELLS_MAXRSSI + ", " + CELLS_MINRSSI
   	    			+ " FROM " + WAPDROID_CELLS
   	    			+ " WHERE " + TABLE_ID + "=" + mCell
    				+ " AND (" + CELLS_MAXRSSI + "<" + mRSSI
    				+ " OR (" + CELLS_MINRSSI
    				+ "=-1 OR " + CELLS_MINRSSI + ">" + mRSSI + "))", null);
   	    	if (c.getCount() > 0) {
   	    		c.moveToFirst();
    	    	int mMaxRSSI = c.getInt(c.getColumnIndex(CELLS_MAXRSSI));
    	    	int mMinRSSI = c.getInt(c.getColumnIndex(CELLS_MINRSSI));
    			ContentValues updateValues = new ContentValues();
    			if (mMaxRSSI < mRSSI) {
    				updateValues.put(CELLS_MAXRSSI, mRSSI);}
    			if ((mMinRSSI == -1) || (mMinRSSI > mRSSI)) {
    				updateValues.put(CELLS_MINRSSI, mRSSI);}
    			mDb.update(WAPDROID_CELLS, updateValues, TABLE_ID + "=" + mCell, null);}
   	    	c.close();}}
    
    public boolean inRange(int mCID, int mLAC, String mMNC, String mMCC, int mRSSI) {
    	boolean range = false;
    	int mLocation = fetchLocation(mLAC);
    	int mCarrier = fetchCarrier(mMNC);
    	int mCountry = fetchCountry(mMCC);
    	if ((mLocation > 0) && (mCarrier > 0) && (mCountry > 0)) {
    		Cursor c = mDb.rawQuery("SELECT MAX(" + CELLS_MAXRSSI + "), MIN(" + CELLS_MINRSSI
    				+ ") FROM " + WAPDROID_CELLS
    				+ " WHERE " + CELLS_CID + "=" + mCID
    				+ " AND " + CELLS_LAC + "=" + mLocation
    				+ " AND " + CELLS_MNC + "=" + mCarrier
    				+ " AND " + CELLS_MCC + "=" + mCountry
    				+ " AND " + CELLS_MAXRSSI + ">=" + mRSSI
   					+ " AND " + CELLS_MINRSSI + "<=" + mRSSI, null);
    		if (c.getCount() > 0) {
    			c.moveToFirst();
    			int mMaxRSSI = c.getInt(c.getColumnIndex(CELLS_MAXRSSI));
    			int mMinRSSI = c.getInt(c.getColumnIndex(CELLS_MINRSSI));
    			range = ((mMaxRSSI == -1) || ((mMaxRSSI >= mRSSI) && (mMinRSSI <= mRSSI)));}
    		c.close();}
    	return range;}
    
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
		mDb.delete(WAPDROID_NETWORKS, TABLE_ID + "=" + mNetwork, null);}

    public void deleteCell(int mNetwork, int mCell) {
    	cleanCell(mCell);
		Cursor c = fetchCellsToDelete(mNetwork);
    	if (c.getCount() == 0) {
    		mDb.delete(WAPDROID_NETWORKS, TABLE_ID + "=" + mNetwork, null);}
    	c.close();}}