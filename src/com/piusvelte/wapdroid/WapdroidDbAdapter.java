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
	private static final String ID_TYPE = " integer primary key autoincrement, ";
	public static final String WAPDROID_NETWORKS = "networks";
	public static final String NETWORKS_SSID = "SSID";
	public static final String WAPDROID_CELLS = "cells";
	public static final String CELLS_CID = "CID";
	public static final String CELLS_LAC = "LAC";
	public static final String WAPDROID_PAIRS = "pairs";
	public static final String PAIRS_NETWORK = "network";
	public static final String PAIRS_CELL = "cell";
	
	private DatabaseHelper mDbHelper;
	private SQLiteDatabase mDb;

    public final Context mContext;
    
    private static class DatabaseHelper extends SQLiteOpenHelper {
        DatabaseHelper(Context context) {
            super(context, WAPDROID, null, 2);}
        @Override
        public void onCreate(SQLiteDatabase database) {
            database.execSQL("create table "
            		+ WAPDROID_NETWORKS + " ("
            		+ TABLE_ID + ID_TYPE
            		+ NETWORKS_SSID + " text not null);");
            database.execSQL("create table "
            		+ WAPDROID_CELLS + " ("
            		+ TABLE_ID + ID_TYPE
            		+ CELLS_CID + " integer, "
            		+ CELLS_LAC + " integer);");
            database.execSQL("create table "
            		+ WAPDROID_PAIRS + " ("
            		+ TABLE_ID + ID_TYPE
            		+ PAIRS_NETWORK + " integer, "
            		+ PAIRS_CELL + " integer);");}
        @Override
        public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
            database.execSQL(DROP + WAPDROID_NETWORKS + ";");
            database.execSQL(DROP + WAPDROID_CELLS + ";");
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
    
    public void resetDatabase() {
        mDb.execSQL(DROP + WAPDROID_NETWORKS + ";");
        mDb.execSQL(DROP + WAPDROID_CELLS + ";");
        mDb.execSQL(DROP + WAPDROID_PAIRS + ";");
        mDb.execSQL("create table "
        		+ WAPDROID_NETWORKS + " ("
        		+ TABLE_ID + ID_TYPE
        		+ NETWORKS_SSID + " text not null);");
        mDb.execSQL("create table "
        		+ WAPDROID_CELLS + " ("
        		+ TABLE_ID + ID_TYPE
        		+ CELLS_CID + " integer, "
        		+ CELLS_LAC + " integer);");
        mDb.execSQL("create table "
        		+ WAPDROID_PAIRS + " ("
        		+ TABLE_ID + ID_TYPE
        		+ PAIRS_NETWORK + " integer, "
        		+ PAIRS_CELL + " integer);");}

    public int fetchNetwork(String mSSID) {
    	long mNetwork;
    	Cursor c = mDb.rawQuery("SELECT _id FROM " + WAPDROID_NETWORKS + " WHERE " + NETWORKS_SSID + "=\"" + mSSID + "\"", null);
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
    	return mDb.rawQuery("SELECT " + TABLE_ID + ", " + NETWORKS_SSID + " FROM " + WAPDROID_NETWORKS, null);}
    
    public Cursor fetchCellByCIDLAC(int mCID, int mLAC) {
    	return mDb.rawQuery("SELECT " + TABLE_ID + " FROM " + WAPDROID_CELLS + " WHERE " + CELLS_CID + "=" + mCID + " AND " + CELLS_LAC + "=" + mLAC, null);}
    
    public int fetchCell(int mCID, int mLAC) {
    	long mCell;
    	Cursor c = fetchCellByCIDLAC(mCID, mLAC);
    	if (c.getCount() > 0) {
    		c.moveToFirst();
    		mCell = c.getInt(c.getColumnIndex(TABLE_ID));}
    	else {
    		ContentValues initialValues = new ContentValues();
        	initialValues.put(CELLS_CID, mCID);
        	initialValues.put(CELLS_LAC, mLAC);
    		mCell = mDb.insert(WAPDROID_CELLS, null, initialValues);}
    	c.close();
    	return (int) mCell;}
    
    public Cursor fetchCellsByNetwork(int mNetwork) {
    	return mDb.rawQuery("SELECT " + WAPDROID_CELLS + "." + TABLE_ID + ", " + CELLS_CID + ", " + CELLS_LAC
    			+ " FROM " + WAPDROID_CELLS
    			+ " JOIN " + WAPDROID_PAIRS + " ON (" + WAPDROID_CELLS + "." + TABLE_ID + "=" + WAPDROID_PAIRS + "." + PAIRS_CELL
    			+ ") WHERE " + WAPDROID_PAIRS + "." + PAIRS_NETWORK + "=" + mNetwork, null);}

    public Cursor fetchCellById(int mCell) {
    	return mDb.rawQuery("SELECT " + TABLE_ID + ", " + CELLS_CID + ", " + CELLS_LAC
    			+ " FROM " + WAPDROID_CELLS
    			+ " WHERE " + TABLE_ID + "=" + mCell, null);}

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
    
    public void pairCell(String mSSID, int mCID, int mLAC) {
    	int mNetwork = fetchNetwork(mSSID);
    	int mCell = fetchCell(mCID, mLAC);
    	long result;
    	Cursor c = fetchPairsByNetworkCell(mNetwork, mCell);
    	if (c.getCount() > 0) {
    		result = 1;}
    	else {
    		ContentValues initialValues = new ContentValues();
    		initialValues.put(PAIRS_NETWORK, mNetwork);
    		initialValues.put(PAIRS_CELL, mCell);
    		result = mDb.insert(WAPDROID_PAIRS, null, initialValues);}}
    
    public boolean inRange(int mCID, int mLAC) {
    	boolean range = false;
    	Cursor c = fetchCellByCIDLAC(mCID, mLAC);
    	if (c.getCount() > 0) {
    		range = true;}
    	c.close();
    	return range;}
    
    public boolean deleteNetwork(int mNetwork) {
    	Cursor c = fetchPairsByNetwork(mNetwork);
    	if (c.getCount() > 0) {
    		int mPair;
    		int mCell;
    		boolean deleted;
    		c.moveToFirst();
    		while (!c.isAfterLast()) {
    			mPair = c.getInt(c.getColumnIndex(TABLE_ID));
    			mCell = c.getInt(c.getColumnIndex(PAIRS_CELL));
    			deletePair(mPair);
    			if (fetchPairsByCell(mCell).getCount() == 0) {
    				deleted = mDb.delete(WAPDROID_CELLS, TABLE_ID + "=" + mCell, null) > 0;}
    			c.moveToNext();}}
        return mDb.delete(WAPDROID_NETWORKS, TABLE_ID + "=" + mNetwork, null) > 0;}

    public boolean deleteCell(int mNetwork, int mCell) {
    	deletePairByNetworkCell(mNetwork, mCell);
    	Cursor c = fetchPairsByCell(mCell);
    	if (c.getCount() > 0) {
    		return true;}
    	else {
    		return mDb.delete(WAPDROID_CELLS, TABLE_ID + "=" + mCell, null) > 0;}}
    
    public boolean deletePair(int mPair) {
    	return mDb.delete(WAPDROID_PAIRS, TABLE_ID + "=" + mPair, null) > 0;}

    public boolean deletePairByNetworkCell(int mNetwork, int mCell) {
    	return mDb.delete(WAPDROID_PAIRS, PAIRS_NETWORK + "=" + mNetwork + " AND " + PAIRS_CELL + "=" + mCell, null) > 0;}}