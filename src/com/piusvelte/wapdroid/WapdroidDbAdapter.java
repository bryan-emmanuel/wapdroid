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
	private static final int DATABASE_VERSION = 2;
	private static final String DROP = "DROP TABLE IF EXISTS ";
	public static final String TABLE_ID = "_id";
	public static final String TABLE_CODE = "code";
	private static final String ID_TYPE = " integer primary key autoincrement, ";
	public static final String TABLE_NETWORKS = "networks";
	public static final String NETWORKS_SSID = "SSID";
	public static final String NETWORKS_BSSID = "BSSID";
	public static final String TABLE_CELLS = "cells";
	public static final String CELLS_CID = "CID";
	public static final String CELLS_NETWORK = "network";
	public static final String STATUS = "status";
	public static final int FILTER_ALL = 0;
	public static final int FILTER_INRANGE = 1;
	public static final int FILTER_OUTRANGE = 2;
	
	private static final String CREATE_NETWORKS = "create table "
		+ TABLE_NETWORKS + " ("
		+ TABLE_ID + ID_TYPE
		+ NETWORKS_SSID + " text not null, "
		+ NETWORKS_BSSID + " text not null);";
	private static final String CREATE_CELLS = "create table "
		+ TABLE_CELLS + " ("
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
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(CREATE_NETWORKS);
            db.execSQL(CREATE_CELLS);}
        
        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        	if (oldVersion < 2) {
        		// add BSSID
    			db.execSQL(DROP + TABLE_NETWORKS + "_bkp;");
    			db.execSQL("create temporary table " + TABLE_NETWORKS + "_bkp AS SELECT * FROM " + TABLE_NETWORKS + ";");
    			db.execSQL(DROP + TABLE_NETWORKS + ";");
    			db.execSQL(CREATE_NETWORKS);
    			db.execSQL("INSERT INTO " + TABLE_NETWORKS + " SELECT "
        				+ TABLE_ID + ", " + NETWORKS_SSID + ", \"\""
        				+ " FROM " + TABLE_NETWORKS + "_bkp;");
    			db.execSQL(DROP + TABLE_NETWORKS + "_bkp;");}}}
        
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
        
    public int fetchNetworkOrCreate(String SSID, String BSSID) {
    	int network = -1;
    	String ssid = "", bssid = "";
		ContentValues initialValues = new ContentValues();
		// upgrading, BSSID may not be set yet
    	Cursor c = mDb.rawQuery("SELECT " + TABLE_ID + ", " + NETWORKS_SSID + ", " + NETWORKS_BSSID + " FROM " + TABLE_NETWORKS + " WHERE " + NETWORKS_BSSID + "=\"" + BSSID + "\" OR (" + NETWORKS_SSID + "=\"" + SSID + "\" AND " + NETWORKS_BSSID + "=\"\")", null);
    	if (c.getCount() > 0) {
    		c.moveToFirst();
    		network = c.getInt(c.getColumnIndex(TABLE_ID));
    		ssid = c.getString(c.getColumnIndex(NETWORKS_SSID));
    		bssid = c.getString(c.getColumnIndex(NETWORKS_BSSID));
    		if (bssid.equals("")) {
        		initialValues.put(NETWORKS_BSSID, BSSID);
        		mDb.update(TABLE_NETWORKS, initialValues, TABLE_ID + "=" + network, null);}
    		else if (!ssid.equals(SSID)) {
        		initialValues.put(NETWORKS_SSID, SSID);
        		mDb.update(TABLE_NETWORKS, initialValues, TABLE_ID + "=" + network, null);}}
    	else {
    		initialValues.put(NETWORKS_SSID, SSID);
    		initialValues.put(NETWORKS_BSSID, BSSID);
    		network = (int) mDb.insert(TABLE_NETWORKS, null, initialValues);}
    	c.close();
    	return network;}

    public Cursor fetchNetworks(int filter, String set) {
   		return mDb.rawQuery("SELECT " + TABLE_NETWORKS + "." + TABLE_ID + " AS " + TABLE_ID + ", "
   	   			+ TABLE_NETWORKS + "." + NETWORKS_SSID + " AS " + NETWORKS_SSID + ", "
   	   			+ TABLE_NETWORKS + "." + NETWORKS_BSSID + " AS " + NETWORKS_BSSID + ", "
   	   			+ "CASE WHEN " + TABLE_NETWORKS + "." + TABLE_ID
   	   			+ " IN (SELECT " + CELLS_NETWORK
   	   			+ " FROM " + TABLE_CELLS
   	   			+ " WHERE " + CELLS_CID + " IN (" + set
   	   			+ ")) THEN '" + mContext.getString(R.string.accessible)
   				+ "' ELSE '" + mContext.getString(R.string.inaccessible) + "' END AS " + STATUS
   	   	    	+ " FROM " + TABLE_NETWORKS
   	   	    	+ ((filter != FILTER_ALL) ?
   	   	    		(", " + TABLE_CELLS
   	   	    		+ " WHERE " + TABLE_NETWORKS + "." + TABLE_ID + " = " + TABLE_CELLS + "." + TABLE_ID
   	   	    		+ " AND " + TABLE_CELLS + "." + CELLS_CID + (filter == FILTER_OUTRANGE ? " NOT" : "") + " IN (" + set + ")")
   	   	    		: ""), null);}
    
    public int fetchCell(int CID, int network) {
    	int cell = -1;
    	Cursor c = mDb.rawQuery("SELECT " + TABLE_ID
    			+ " FROM " + TABLE_CELLS	+ " WHERE " + CELLS_CID + "=" + CID
    			+ " AND " + CELLS_NETWORK + "=" + network, null);
    	if (c.getCount() > 0) {
    		c.moveToFirst();
    		cell = c.getInt(c.getColumnIndex(TABLE_ID));}
    	c.close();
    	return cell;}

    public Cursor fetchCellsByNetwork(int network) {
    	return mDb.rawQuery("SELECT " + TABLE_ID + ", " + CELLS_CID
    		+ " FROM " + TABLE_CELLS
    		+ " WHERE " + CELLS_NETWORK + "=" + network
    		+ " ORDER BY " + CELLS_CID, null);}
    
    public Cursor fetchCellsByNetworkFilter(int network, int filter, String set) {
    	return mDb.rawQuery("SELECT " + TABLE_CELLS + "." + TABLE_ID + ", " + CELLS_CID + ", "
    			+ ((filter == FILTER_ALL) ?
    				("CASE WHEN " + CELLS_CID + " IN (" + set + ") THEN '"
    					+ mContext.getString(R.string.accessible)
    					+ "' ELSE '" + mContext.getString(R.string.inaccessible) + "' END AS ")
    				: "'" + (mContext.getString(filter == FILTER_INRANGE ?
    						R.string.accessible
    						: R.string.inaccessible) + "' AS "))
    			+ STATUS
    			+ " FROM " + TABLE_CELLS
    			+ " WHERE " + CELLS_NETWORK + "=" + network
       	    	+ ((filter != FILTER_ALL) ?
       	   	    		(" AND " + CELLS_CID + (filter == FILTER_OUTRANGE ? " NOT" : "") + " IN (" + set + ")")
       	   	    		: "")
    			+ " ORDER BY " + CELLS_CID, null);}
    
    public int updateCellRange(String SSID, String BSSID, int CID) {
    	int network = fetchNetworkOrCreate(SSID, BSSID);
    	int cell = fetchCell(CID, network);
    	if (cell < 0) {
    		ContentValues initialValues = new ContentValues();
        	initialValues.put(CELLS_CID, CID);
        	initialValues.put(CELLS_NETWORK, network);
    		cell = (int) mDb.insert(TABLE_CELLS, null, initialValues);}
    	return network;}
    
    public void updateCellNeighbor(int network, int CID) {
    	int cell = fetchCell(CID, network);
    	if (cell < 0) {
    		ContentValues initialValues = new ContentValues();
        	initialValues.put(CELLS_CID, CID);
        	initialValues.put(CELLS_NETWORK, network);
    		cell = (int) mDb.insert(TABLE_CELLS, null, initialValues);}}
    
    public boolean cellInRange(int CID) {
    	boolean inRange = false;
    	Cursor c = mDb.rawQuery("SELECT " + TABLE_ID
				+ ", " + CELLS_NETWORK
				+ " FROM " + TABLE_CELLS
				+ " WHERE " + CELLS_CID + "=" + CID, null);;
   		inRange = (c.getCount() > 0);
	    c.close();
    	return inRange;}
    
	public void deleteNetwork(int network) {
		mDb.delete(TABLE_CELLS, CELLS_NETWORK + "=" + network, null);
		mDb.delete(TABLE_NETWORKS, TABLE_ID + "=" + network, null);}

    public void deleteCell(int network, int cell) {
		mDb.delete(TABLE_CELLS, TABLE_ID + "=" + cell + " AND " + CELLS_NETWORK + "=" + network, null);
		Cursor c = fetchCellsByNetwork(network);// filter All
    	if (c.getCount() == 0) {
    		mDb.delete(TABLE_NETWORKS, TABLE_ID + "=" + network, null);}
    	c.close();}}