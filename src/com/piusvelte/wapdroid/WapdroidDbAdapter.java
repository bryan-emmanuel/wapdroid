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
	public static final String WAPDROID_LOCATIONS = "locations";
	public static final String WAPDROID_CARRIERS = "carriers";
	public static final String WAPDROID_COUNTRIES = "countries";
	public static final String WAPDROID_PAIRS = "pairs";
	public static final String PAIRS_NETWORK = "network";
	public static final String PAIRS_CELL = "cell";
	
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
		+ CELLS_MCC + " integer);";
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
	public static final String CREATE_PAIRS = "create table "
		+ WAPDROID_PAIRS + " ("
		+ TABLE_ID + ID_TYPE
		+ PAIRS_NETWORK + " integer, "
		+ PAIRS_CELL + " integer);";
	
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
            database.execSQL(CREATE_COUNTRIES);
            database.execSQL(CREATE_PAIRS);}
        
        @Override
        public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
            database.execSQL(DROP + WAPDROID_NETWORKS + ";");
            database.execSQL(DROP + WAPDROID_CELLS + ";");
            database.execSQL(DROP + WAPDROID_LOCATIONS + ";");
            database.execSQL(DROP + WAPDROID_CARRIERS + ";");
            database.execSQL(DROP + WAPDROID_COUNTRIES + ";");
            database.execSQL(DROP + WAPDROID_PAIRS + ";");
            onCreate(database);}}
    
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
        mDb.execSQL(CREATE_LOCATIONS);
        mDb.execSQL(CREATE_CARRIERS);
        mDb.execSQL(CREATE_COUNTRIES);
        mDb.execSQL(CREATE_PAIRS);}
    
    public void resetDatabase() {
        mDb.execSQL(DROP + WAPDROID_NETWORKS + ";");
        mDb.execSQL(DROP + WAPDROID_CELLS + ";");
        mDb.execSQL(DROP + WAPDROID_LOCATIONS + ";");
        mDb.execSQL(DROP + WAPDROID_CARRIERS + ";");
        mDb.execSQL(DROP + WAPDROID_COUNTRIES + ";");
        mDb.execSQL(DROP + WAPDROID_PAIRS + ";");
        createTables();}
    
    public int fetchNetworkOrCreate(String mSSID) {
    	long mNetwork;
    	Cursor c = mDb.rawQuery("SELECT " + TABLE_ID
    			+ " FROM " + WAPDROID_NETWORKS
    			+ " WHERE " + NETWORKS_SSID + "=\"" + mSSID + "\"", null);
    	if (c.getCount() > 0) {
    		c.moveToFirst();
    		mNetwork = c.getInt(c.getColumnIndex(TABLE_ID));}
    	else {
    		ContentValues initialValues = new ContentValues();
    		initialValues.put(NETWORKS_SSID, mSSID);
    		mNetwork = mDb.insert(WAPDROID_NETWORKS, null, initialValues);}
    	c.close();
    	return (int) mNetwork;}

    public Cursor fetchNetworks() {
    	return mDb.rawQuery("SELECT " + TABLE_ID + ", " + NETWORKS_SSID
    			+ " FROM " + WAPDROID_NETWORKS, null);}
    
    public Cursor fetchCell(int mCID, int mLAC, String mMNC, String mMCC) {
    	return mDb.rawQuery("SELECT " + WAPDROID_CELLS + "." + TABLE_ID
    			+ " FROM " + WAPDROID_CELLS
    			+ " JOIN " + WAPDROID_LOCATIONS + " ON (" + WAPDROID_CELLS + "." + CELLS_LAC + "=" + WAPDROID_LOCATIONS + "." + TABLE_ID
    			+ ") JOIN " + WAPDROID_CARRIERS + " ON (" + WAPDROID_CELLS + "." + CELLS_MNC + "=" + WAPDROID_CARRIERS + "." + TABLE_ID
    			+ ") JOIN " + WAPDROID_COUNTRIES + " ON (" + WAPDROID_CELLS + "." + CELLS_MCC + "=" + WAPDROID_COUNTRIES + "." + TABLE_ID
    			+ ") WHERE " + CELLS_CID + "=" + mCID
    			+ " AND " + WAPDROID_LOCATIONS + "." + TABLE_CODE + "=" + mLAC
    			+ " AND " + WAPDROID_CARRIERS + "." + TABLE_CODE + "=\"" + mMNC
    			+ "\" AND " + WAPDROID_COUNTRIES + "." + TABLE_CODE + "=\"" + mMCC + "\"", null);}
    
    public Cursor fetchCellInfo(int mCell) {
    	return mDb.rawQuery("SELECT " + CELLS_LAC + ", " + CELLS_MNC + ", " + CELLS_MCC
    			+ " FROM " + WAPDROID_CELLS
    			+ " WHERE " + TABLE_ID + "=" + mCell, null);}
    
    public Cursor fetchLocation(int mLAC) {
    	return mDb.rawQuery("SELECT " + TABLE_ID
    			+ " FROM " + WAPDROID_LOCATIONS
    			+ " WHERE " + TABLE_CODE + "=" + mLAC, null);}

    public Cursor fetchCarrier(String mMNC) {
    	return mDb.rawQuery("SELECT " + TABLE_ID
    			+ " FROM " + WAPDROID_CARRIERS
    			+ " WHERE " + TABLE_CODE + "=\"" + mMNC + "\"", null);}

    public Cursor fetchCountry(String mMCC) {
    	return mDb.rawQuery("SELECT " + TABLE_ID
    			+ " FROM " + WAPDROID_COUNTRIES
    			+ " WHERE " + TABLE_CODE + "=\"" + mMCC + "\"", null);}

    public int fetchLocationOrCreate(int mLAC) {
    	long mLocation;
    	Cursor c = fetchLocation(mLAC);
    	if (c.getCount() > 0) {
    		c.moveToFirst();
    		mLocation = c.getInt(c.getColumnIndex(TABLE_ID));}
    	else {
    		ContentValues initialValues = new ContentValues();
        	initialValues.put(TABLE_CODE, mLAC);
    		mLocation = mDb.insert(WAPDROID_LOCATIONS, null, initialValues);}
    	c.close();
    	return (int) mLocation;}

    public int fetchCarrierOrCreate(String mMNC) {
    	long mCarrier;
    	Cursor c = fetchCarrier(mMNC);
    	if (c.getCount() > 0) {
    		c.moveToFirst();
    		mCarrier = c.getInt(c.getColumnIndex(TABLE_ID));}
    	else {
    		ContentValues initialValues = new ContentValues();
        	initialValues.put(TABLE_CODE, mMNC);
    		mCarrier = mDb.insert(WAPDROID_CARRIERS, null, initialValues);}
    	c.close();
    	return (int) mCarrier;}

    public int fetchCountryOrCreate(String mMCC) {
    	long mCountry;
    	Cursor c = fetchCountry(mMCC);
    	if (c.getCount() > 0) {
    		c.moveToFirst();
    		mCountry = c.getInt(c.getColumnIndex(TABLE_ID));}
    	else {
    		ContentValues initialValues = new ContentValues();
        	initialValues.put(TABLE_CODE, mMCC);
    		mCountry = mDb.insert(WAPDROID_COUNTRIES, null, initialValues);}
    	c.close();
    	return (int) mCountry;}
    
    public int fetchCellOrCreate(int mCID, int mLAC, String mMNC, String mMCC) {
    	long mCell;
    	int mLocation;
    	int mCarrier;
    	int mCountry;    	
    	Cursor c = fetchCell(mCID, mLAC, mMNC, mMCC);
    	if (c.getCount() > 0) {
    		c.moveToFirst();
    		mCell = c.getInt(c.getColumnIndex(TABLE_ID));}
    	else {
        	mLocation = fetchLocationOrCreate(mLAC);
        	mCarrier = fetchCarrierOrCreate(mMNC);
        	mCountry = fetchCountryOrCreate(mMCC); 
    		ContentValues initialValues = new ContentValues();
        	initialValues.put(CELLS_CID, mCID);
        	initialValues.put(CELLS_LAC, mLocation);
        	initialValues.put(CELLS_MNC, mCarrier);
        	initialValues.put(CELLS_MCC, mCountry);
    		mCell = mDb.insert(WAPDROID_CELLS, null, initialValues);}
    	c.close();
    	return (int) mCell;}
    
    public Cursor fetchCellsByNetwork(int mNetwork) {
    	return mDb.rawQuery("SELECT " + WAPDROID_CELLS + "." + TABLE_ID + ", " + CELLS_CID + ", "
    			+ WAPDROID_LOCATIONS + "." + TABLE_CODE + " AS " + CELLS_LAC + ", "
    			+ WAPDROID_CARRIERS + "." + TABLE_CODE + " AS " + CELLS_MNC + ", "
    			+ WAPDROID_COUNTRIES + "." + TABLE_CODE + " AS " + CELLS_MCC + " FROM " + WAPDROID_CELLS
    			+ " JOIN " + WAPDROID_PAIRS + " ON (" + WAPDROID_CELLS + "." + TABLE_ID + "=" + WAPDROID_PAIRS + "." + PAIRS_CELL
    			+ ") JOIN " + WAPDROID_LOCATIONS + " ON (" + WAPDROID_CELLS + "." + CELLS_LAC + "=" + WAPDROID_LOCATIONS + "." + TABLE_ID
    			+ ") JOIN " + WAPDROID_CARRIERS + " ON (" + WAPDROID_CELLS + "." + CELLS_MNC + "=" + WAPDROID_CARRIERS + "." + TABLE_ID
    			+ ") JOIN " + WAPDROID_COUNTRIES + " ON (" + WAPDROID_CELLS + "." + CELLS_MCC + "=" + WAPDROID_COUNTRIES + "." + TABLE_ID
    			+ ") WHERE " + WAPDROID_PAIRS + "." + PAIRS_NETWORK + "=" + mNetwork, null);}

    public Cursor fetchCellById(int mCell) {
    	return mDb.rawQuery("SELECT " + TABLE_ID + ", " + CELLS_CID + ", " + CELLS_LAC + ", " + CELLS_MNC + ", " + CELLS_MCC
    			+ " FROM " + WAPDROID_CELLS
    			+ " WHERE " + TABLE_ID + "=" + mCell, null);}

    public Cursor fetchCellByCID(int mCID) {
    	return mDb.rawQuery("SELECT " + TABLE_ID
    			+ " FROM " + WAPDROID_CELLS
    			+ " WHERE " + CELLS_CID + "=" + mCID, null);}
    
    public Cursor fetchCellsByLAC(int mLAC) {
    	return mDb.rawQuery("SELECT " + WAPDROID_CELLS + "." + TABLE_ID
    			+ " FROM " + WAPDROID_CELLS
    			+ " JOIN " + WAPDROID_LOCATIONS + " ON (" + WAPDROID_CELLS + "." + CELLS_LAC + "=" + WAPDROID_LOCATIONS + "." + TABLE_ID
    			+ ") WHERE " + WAPDROID_LOCATIONS + "." + TABLE_CODE + "=" + mLAC, null);}
    
    public Cursor fetchCellsByMNC(int mMNC) {
    	return mDb.rawQuery("SELECT " + WAPDROID_CELLS + "." + TABLE_ID
    			+ " FROM " + WAPDROID_CELLS
    			+ " JOIN " + WAPDROID_CARRIERS + " ON (" + WAPDROID_CELLS + "." + CELLS_MNC + "=" + WAPDROID_CARRIERS + "." + TABLE_ID
    			+ ") WHERE " + WAPDROID_CARRIERS + "." + TABLE_CODE + "=" + mMNC, null);}
    
    public Cursor fetchCellsByMCC(int mMCC) {
    	return mDb.rawQuery("SELECT " + WAPDROID_CELLS + "." + TABLE_ID
    			+ " FROM " + WAPDROID_CELLS
    			+ " JOIN " + WAPDROID_COUNTRIES + " ON (" + WAPDROID_CELLS + "." + CELLS_MCC + "=" + WAPDROID_COUNTRIES + "." + TABLE_ID
    			+ ") WHERE " + WAPDROID_COUNTRIES + "." + TABLE_CODE + "=" + mMCC, null);}
    
    public Cursor fetchPairsByNetwork(int mNetwork) {
    	return mDb.rawQuery("SELECT " + TABLE_ID + ", " + PAIRS_CELL
    			+ " FROM " + WAPDROID_PAIRS
    			+ " WHERE " + PAIRS_NETWORK + "=" + mNetwork, null);}
    
    public Cursor fetchPairsByCell(int mCell) {
    	return mDb.rawQuery("SELECT " + TABLE_ID + ", " + PAIRS_NETWORK
    			+ " FROM " + WAPDROID_PAIRS
    			+ " WHERE " + PAIRS_CELL + "=" + mCell, null);}
    
    public Cursor fetchPairsByNetworkCell(int mNetwork, int mCell) {
    	return mDb.rawQuery("SELECT " + TABLE_ID
    			+ " FROM " + WAPDROID_PAIRS
    			+ " WHERE " + PAIRS_NETWORK + "=" + mNetwork + " AND " + PAIRS_CELL + "=" + mCell, null);}
    
    public void pairCell(String mSSID, int mCID, int mLAC, String mMNC, String mMCC) {
    	int mNetwork = fetchNetworkOrCreate(mSSID);
    	int mCell = fetchCellOrCreate(mCID, mLAC, mMNC, mMCC);
    	Cursor c = fetchPairsByNetworkCell(mNetwork, mCell);
    	if (c.getCount() <= 0) {
    		ContentValues initialValues = new ContentValues();
    		initialValues.put(PAIRS_NETWORK, mNetwork);
    		initialValues.put(PAIRS_CELL, mCell);
    		mDb.insert(WAPDROID_PAIRS, null, initialValues);}
    	c.close();}
    
    public boolean inRange(int mCID, int mLAC, String mMNC, String mMCC) {
    	boolean range = false;
    	Cursor c = fetchCell(mCID, mLAC, mMNC, mMCC);
    	if (c.getCount() > 0) {
    		range = true;}
    	c.close();
    	return range;}
    
    public void deleteNetwork(int mNetwork) {
    	Cursor c = fetchPairsByNetwork(mNetwork);
    	if (c.getCount() > 0) {
    		int mPair;
    		int mCell;
    		c.moveToFirst();
    		while (!c.isAfterLast()) {
    			mPair = c.getInt(c.getColumnIndex(TABLE_ID));
    			mCell = c.getInt(c.getColumnIndex(PAIRS_CELL));
    			deletePair(mPair);
    			cleanNetworkPairs(mCell);
    			c.moveToNext();}}
    	c.close();
        mDb.delete(WAPDROID_NETWORKS, TABLE_ID + "=" + mNetwork, null);}
    
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
    		cleanLocation(mLocation);
    		cleanCarrier(mCarrier);
    		cleanCountry(mCountry);}}
	
	public void cleanNetworkPairs(int mCell) {
		Cursor c = fetchPairsByCell(mCell);
		if (c.getCount() == 0) {
			mDb.delete(WAPDROID_CELLS, TABLE_ID + "=" + mCell, null);
			cleanCell(mCell);}
		c.close();}

	public void cleanCellPairs(int mNetwork) {
		Cursor c = fetchPairsByNetwork(mNetwork);
		if (c.getCount() == 0) {
			mDb.delete(WAPDROID_NETWORKS, TABLE_ID + "=" + mNetwork, null);}
		c.close();}

    public void deleteCell(int mCell) {
    	Cursor c = fetchPairsByCell(mCell);
    	if (c.getCount() > 0) {
    		int mPair;
    		int mNetwork;
    		c.moveToFirst();
    		while (!c.isAfterLast()) {
    			mPair = c.getInt(c.getColumnIndex(TABLE_ID));
    			mNetwork = c.getInt(c.getColumnIndex(PAIRS_NETWORK));
    			deletePair(mPair);
    			cleanCellPairs(mNetwork);
    			c.moveToNext();}}
    	c.close();
		mDb.delete(WAPDROID_CELLS, TABLE_ID + "=" + mCell, null);
    	cleanCell(mCell);}
    
    public void deletePair(int mPair) {
    	mDb.delete(WAPDROID_PAIRS, TABLE_ID + "=" + mPair, null);}}