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
	
	private DatabaseHelper mDbHelper;
	private SQLiteDatabase mDb;

    public final Context mContext;
    
    private static class DatabaseHelper extends SQLiteOpenHelper {
        DatabaseHelper(Context context) {
            super(context, "wapdroid", null, 2);}
        @Override
        public void onCreate(SQLiteDatabase database) {
            database.execSQL("create table networks (_id integer primary key autoincrement, SSID text not null);");
            database.execSQL("create table cells (_id integer primary key autoincrement, CID integer, LAC integer);");
            database.execSQL("create table pairs (_id integer primary key autoincrement, network integer, cell integer);");}
        @Override
        public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
            database.execSQL("DROP TABLE IF EXISTS networks;");
            database.execSQL("DROP TABLE IF EXISTS cells;");
            database.execSQL("DROP TABLE IF EXISTS pairs;");
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
    	mDb.execSQL("DROP TABLE IF EXISTS networks;");
    	mDb.execSQL("DROP TABLE IF EXISTS cells;");
    	mDb.execSQL("DROP TABLE IF EXISTS pairs;");
        mDb.execSQL("create table networks (_id integer primary key autoincrement, SSID text not null);");
        mDb.execSQL("create table cells (_id integer primary key autoincrement, CID integer, LAC integer);");
        mDb.execSQL("create table pairs (_id integer primary key autoincrement, network integer, cell integer);");}

    public int fetchNetwork(String mSSID) {
    	long mNetwork;
    	Cursor c = mDb.rawQuery("SELECT _id FROM networks WHERE SSID=\"" + mSSID + "\"", null);
    	if (c.getCount() > 0) {
    		c.moveToFirst();
    		mNetwork = c.getInt(c.getColumnIndex("_id"));}
    	else {
    		ContentValues initialValues = new ContentValues();
    		initialValues.put("SSID", mSSID);
    		mNetwork = mDb.insert("networks", null, initialValues);}
    	c.close();
    	return (int) mNetwork;}

    public Cursor fetchNetworks() {
    	return mDb.rawQuery("SELECT _id, SSID FROM networks", null);}
    
    public int fetchCell(int mCID, int mLAC) {
    	long mCell;
    	Cursor c = mDb.rawQuery("SELECT _id FROM cells WHERE CID=" + mCID + " AND LAC=" + mLAC, null);
    	if (c.getCount() > 0) {
    		c.moveToFirst();
    		mCell = c.getInt(c.getColumnIndex("_id"));}
    	else {
    		ContentValues initialValues = new ContentValues();
        	initialValues.put("CID", mCID);
        	initialValues.put("LAC", mLAC);
    		mCell = mDb.insert("cells", null, initialValues);}
    	c.close();
    	return (int) mCell;}
    
    public Cursor fetchCellsByNetwork(int mNetwork) {
    	return mDb.rawQuery("SELECT cells._id, CID, LAC FROM cells JOIN pairs ON (cells._id=pairs.cell) WHERE pairs.network=" + mNetwork, null);}

    public Cursor fetchCellById(int mCell) {
    	return mDb.rawQuery("SELECT _id, CID, LAC FROM cells WHERE _id=" + mCell, null);}

    public Cursor fetchPairsByNetwork(int mNetwork) {
    	return mDb.rawQuery("SELECT _id, cell FROM pairs WHERE network=" + mNetwork, null);}
    
    public Cursor fetchPairsByCell(int mCell) {
    	return mDb.rawQuery("SELECT _id, network FROM pairs WHERE cell=" + mCell, null);}
    
    public Cursor fetchPairsByNetworkCell(int mNetwork, int mCell) {
    	return mDb.rawQuery("SELECT _id FROM pairs WHERE network=" + mNetwork + " AND cell=" + mCell, null);}
    
    public void pairCell(String mSSID, int mCID, int mLAC) {
    	int mNetwork = fetchNetwork(mSSID);
    	int mCell = fetchCell(mCID, mLAC);
    	long result;
    	Cursor c = mDb.rawQuery("SELECT _id FROM pairs WHERE network=" + mNetwork + " AND cell=" + mCell, null);
    	if (c.getCount() > 0) {
    		result = 1;}
    	else {
    		ContentValues initialValues = new ContentValues();
    		initialValues.put("network", mNetwork);
    		initialValues.put("cell", mCell);
    		result = mDb.insert("pairs", null, initialValues);}}
    
    public boolean inRange(int mCID, int mLAC) {
    	boolean range = false;
    	Cursor c = mDb.rawQuery("SELECT _id FROM cells WHERE CID=" + mCID + " AND LAC=" + mLAC, null);
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
    			mPair = c.getInt(c.getColumnIndex("_id"));
    			mCell = c.getInt(c.getColumnIndex("cell"));
    			deletePair(mPair);
    			if (fetchPairsByCell(mCell).getCount() == 0) {
    				deleted = mDb.delete("cells", "_id=" + mCell, null) > 0;}
    			c.moveToNext();}}
        return mDb.delete("networks", "_id=" + mNetwork, null) > 0;}

    public boolean deleteCell(int mNetwork, int mCell) {
    	deletePairByNetworkCell(mNetwork, mCell);
    	Cursor c = fetchPairsByCell(mCell);
    	if (c.getCount() > 0) {
    		return true;}
    	else {
    		return mDb.delete("cells", "_id=" + mCell, null) > 0;}}
    
    public boolean deletePair(int mPair) {
    	return mDb.delete("pairs", "_id=" + mPair, null) > 0;}

    public boolean deletePairByNetworkCell(int mNetwork, int mCell) {
    	return mDb.delete("pairs", "network=" + mNetwork + " AND cell=" + mCell, null) > 0;}}