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

import java.lang.reflect.Array;

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
	public static final String WAPDROID_NETWORKS = "networks";
	public static final String NETWORKS_SSID = "SSID";
	public static final String NETWORKS_BSSID = "BSSID";
	public static final String WAPDROID_CELLS = "cells";
	public static final String CELLS_CID = "CID";
	public static final String CELLS_NETWORK = "network";
	public static final String STATUS = "status";
	
	private static final String CREATE_NETWORKS = "create table "
		+ WAPDROID_NETWORKS + " ("
		+ TABLE_ID + ID_TYPE
		+ NETWORKS_SSID + " text not null, "
		+ NETWORKS_BSSID + " text not null);";
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
        	if (oldVersion < 2) {
        		// add BSSID
    			database.execSQL(DROP + WAPDROID_NETWORKS + "_bkp;");
    			database.execSQL("create temporary table " + WAPDROID_NETWORKS + "_bkp AS SELECT * FROM " + WAPDROID_NETWORKS + ";");
    			database.execSQL(DROP + WAPDROID_NETWORKS + ";");
    			database.execSQL(CREATE_NETWORKS);
    			database.execSQL("INSERT INTO " + WAPDROID_NETWORKS + " SELECT "
        				+ TABLE_ID + ", " + NETWORKS_SSID + ", \"\""
        				+ " FROM " + WAPDROID_NETWORKS + "_bkp;");
    			database.execSQL(DROP + WAPDROID_NETWORKS + "_bkp;");}}}
    
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
    	Cursor c = mDb.rawQuery("SELECT " + TABLE_ID + ", " + NETWORKS_SSID + ", " + NETWORKS_BSSID + " FROM " + WAPDROID_NETWORKS + " WHERE " + NETWORKS_BSSID + "=\"" + BSSID + "\" OR (" + NETWORKS_SSID + "=\"" + SSID + "\" AND " + NETWORKS_BSSID + "=\"\")", null);
    	if (c.getCount() > 0) {
    		c.moveToFirst();
    		network = c.getInt(c.getColumnIndex(TABLE_ID));
    		ssid = c.getString(c.getColumnIndex(NETWORKS_SSID));
    		bssid = c.getString(c.getColumnIndex(NETWORKS_BSSID));
    		if (bssid.equals("")) {
        		initialValues.put(NETWORKS_BSSID, BSSID);
        		mDb.update(WAPDROID_NETWORKS, initialValues, TABLE_ID + "=" + network, null);}
    		else if (!ssid.equals(SSID)) {
        		initialValues.put(NETWORKS_SSID, SSID);
        		mDb.update(WAPDROID_NETWORKS, initialValues, TABLE_ID + "=" + network, null);}}
    	else {
    		initialValues.put(NETWORKS_SSID, SSID);
    		initialValues.put(NETWORKS_BSSID, BSSID);
    		network = (int) mDb.insert(WAPDROID_NETWORKS, null, initialValues);}
    	c.close();
    	return network;}

    public Cursor fetchNetworks(int filter, int[] cells) {
    	// filter using connected & neighboring cells
    	if ((filter == 1) && (cells != null)) {
    		String cells_query = "";
    		for (int c = 0; c < cells.length; c++) {
    			if (c != 0) cells_query += " OR ";
    			cells_query += CELLS_CID + "=" + cells[c];}    		
    		return mDb.rawQuery("SELECT " + TABLE_ID + ", " + NETWORKS_SSID + ", " + NETWORKS_BSSID
    	    			+ " FROM " + WAPDROID_NETWORKS
    	    			+ " WHERE IN(SELECT "
    	    			+ CELLS_NETWORK + " FROM "
    	    			+ WAPDROID_CELLS + " WHERE "
    	    			+ cells_query + ")", null);}
    	else return mDb.rawQuery("SELECT " + TABLE_ID + ", " + NETWORKS_SSID + ", " + NETWORKS_BSSID
    			+ " FROM " + WAPDROID_NETWORKS, null);}
    
    public int fetchCell(int CID, int network) {
    	int cell = -1;
    	Cursor c = mDb.rawQuery("SELECT " + TABLE_ID
    			+ " FROM " + WAPDROID_CELLS	+ " WHERE " + CELLS_CID + "=" + CID
    			+ " AND " + CELLS_NETWORK + "=" + network, null);
    	if (c.getCount() > 0) {
    		c.moveToFirst();
    		cell = c.getInt(c.getColumnIndex(TABLE_ID));}
    	c.close();
    	return cell;}
    
    public Cursor fetchCellsByNetwork(int network, int filter, int[] cells) {
    	// filter using connected & neighboring cells
    	if ((filter == 1) && (cells != null)) {
    		String cells_query = "";
    		for (int c = 0; c < cells.length; c++) {
    			if (c != 0) cells_query += " OR ";
    			cells_query += CELLS_CID + "=" + cells[c];}    		
    		return mDb.rawQuery("SELECT " + WAPDROID_CELLS + "." + TABLE_ID + ", " + CELLS_CID
        			+ " FROM " + WAPDROID_CELLS
        			+ " WHERE " + CELLS_NETWORK + "=" + network
        			+ " AND (" + cells_query + ")"
        			+ " ORDER BY " + CELLS_CID, null);}
    	else return mDb.rawQuery("SELECT " + WAPDROID_CELLS + "." + TABLE_ID + ", " + CELLS_CID
    			+ " FROM " + WAPDROID_CELLS
    			+ " WHERE " + CELLS_NETWORK + "=" + network
    			+ " ORDER BY " + CELLS_CID, null);}
    
    public int updateCellRange(String SSID, String BSSID, int CID) {
    	int network = fetchNetworkOrCreate(SSID, BSSID);
    	int cell = fetchCell(CID, network);
    	if (cell < 0) {
    		ContentValues initialValues = new ContentValues();
        	initialValues.put(CELLS_CID, CID);
        	initialValues.put(CELLS_NETWORK, network);
    		cell = (int) mDb.insert(WAPDROID_CELLS, null, initialValues);}
    	return network;}
    
    public void updateCellNeighbor(int network, int CID) {
    	int cell = fetchCell(CID, network);
    	if (cell < 0) {
    		ContentValues initialValues = new ContentValues();
        	initialValues.put(CELLS_CID, CID);
        	initialValues.put(CELLS_NETWORK, network);
    		cell = (int) mDb.insert(WAPDROID_CELLS, null, initialValues);}}
    
    public boolean cellInRange(int CID) {
    	boolean inRange = false;
    	Cursor c = mDb.rawQuery("SELECT " + TABLE_ID
				+ ", " + CELLS_NETWORK
				+ " FROM " + WAPDROID_CELLS
				+ " WHERE " + CELLS_CID + "=" + CID, null);;
   		inRange = (c.getCount() > 0);
	    c.close();
    	return inRange;}
    
	public void deleteNetwork(int network) {
		mDb.delete(WAPDROID_CELLS, CELLS_NETWORK + "=" + network, null);
		mDb.delete(WAPDROID_NETWORKS, TABLE_ID + "=" + network, null);}

    public void deleteCell(int network, int cell) {
		mDb.delete(WAPDROID_CELLS, TABLE_ID + "=" + cell + " AND " + CELLS_NETWORK + "=" + network, null);
		Cursor c = fetchCellsByNetwork(network, 0, null);// filter All
    	if (c.getCount() == 0) {
    		mDb.delete(WAPDROID_NETWORKS, TABLE_ID + "=" + network, null);}
    	c.close();}}