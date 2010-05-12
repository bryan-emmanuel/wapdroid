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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.piusvelte.wapdroid.R;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.telephony.CellLocation;
import android.telephony.NeighboringCellInfo;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.AdapterView.AdapterContextMenuInfo;

public class ManageData extends ListActivity {
	private WapdroidDbAdapter mDbHelper;
	private int mNetwork = -1, mCid = -1;
	private static final int REFRESH_ID = Menu.FIRST;
    private static final int DELETE_ID = Menu.FIRST + 1;
    private static final int FILTER_ID = Menu.FIRST + 2;
    private static final int GEO_ID = Menu.FIRST + 3;
    private static int mFilter = WapdroidDbAdapter.FILTER_ALL;
    private AlertDialog mAlertDialog;
	private TelephonyManager mTeleManager;
	private List<NeighboringCellInfo> mNeighboringCells;
	private final PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
    	public void onCellLocationChanged(CellLocation location) {
    		checkLocation(location);}};
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Bundle extras = getIntent().getExtras();
		if (extras != null) mNetwork = extras.getInt(WapdroidDbAdapter.TABLE_ID);
		setContentView(mNetwork == -1 ? R.layout.networks_list : R.layout.cells_list);
        registerForContextMenu(getListView());
        mTeleManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        mDbHelper = new WapdroidDbAdapter(this);}
	
    @Override
    public void onPause() {
    	super.onPause();
   		mDbHelper.close();
   		mTeleManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);}
    
	@Override
	protected void onResume() {
		super.onResume();
		mDbHelper.open();
		checkLocation(mTeleManager.getCellLocation());
		try {
			listData();}
		catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();}}
	
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	boolean result = super.onCreateOptionsMenu(menu);
    	menu.add(0, REFRESH_ID, 0, R.string.menu_refreshNetworks).setIcon(android.R.drawable.ic_menu_rotate);
    	menu.add(0, FILTER_ID, 0, R.string.menu_filter).setIcon(android.R.drawable.ic_menu_agenda);
    	return result;}

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	switch (item.getItemId()) {
    	case REFRESH_ID:
    		try {
				listData();}
    		catch (RemoteException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();}
    		return true;
    	case FILTER_ID:
    		/* filter options */
    		String[] filters = getResources().getStringArray(R.array.filter_values);
    		int which = 0;
    		for (int f = 0; f < filters.length; f++) {
    			if (mFilter == Integer.parseInt(filters[f])) {
    				which = f;
    				break;}}
    		Builder b = new AlertDialog.Builder(this);
    		b.setSingleChoiceItems(
    				R.array.filter_entries,
    				which,
    				new DialogInterface.OnClickListener() {
    					//@Override
    					public void onClick(DialogInterface dialog, int which) {
    						mAlertDialog.dismiss();
    						mFilter = Integer.parseInt(getResources().getStringArray(R.array.filter_values)[which]);
    						try {
								listData();}
    						catch (RemoteException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();}}});
    		mAlertDialog = b.create();
    		mAlertDialog.show();
    		return true;}
        return super.onOptionsItemSelected(item);}
	
    @Override
	public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, view, menuInfo);
		menu.add(0, DELETE_ID, 0, mNetwork == -1 ? R.string.menu_deleteNetwork : R.string.menu_deleteCell);
		menu.add(0, GEO_ID, 0, R.string.map);}

    @Override
	public boolean onContextItemSelected(MenuItem item) {
    	AdapterContextMenuInfo info;
		switch(item.getItemId()) {
		case DELETE_ID:
			info = (AdapterContextMenuInfo) item.getMenuInfo();
			if (mNetwork == -1) mDbHelper.deleteNetwork((int) info.id);
			else mDbHelper.deleteCell(mNetwork, (int) info.id);
			try {
				listData();}
			catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();}
			return true;
		case GEO_ID:
			// open gmaps
			info = (AdapterContextMenuInfo) item.getMenuInfo();
    		String operator = mTeleManager.getNetworkOperator();
			JSONObject query = new JSONObject();
			try {
				query.put("version", "1.1.0");
				query.put("host", "maps.google.com");
				android.util.Log.v("Wapdroid", "home_mobile_country_code:" + operator.substring(0, 3));
				query.put("home_mobile_country_code", operator.substring(0, 3));
				android.util.Log.v("Wapdroid", "home_mobile_country_code:" + operator.substring(0, 3));
				query.put("home_mobile_network_code", operator.substring(3));
				android.util.Log.v("Wapdroid", "home_mobile_network_code:" + operator.substring(3));
				query.put("carrier", mTeleManager.getNetworkOperatorName());
				android.util.Log.v("Wapdroid", "carrier:" + mTeleManager.getNetworkOperatorName());}
			catch (JSONException e) {}
			Cursor c = mNetwork == -1 ? mDbHelper.fetchNetworkData((int) info.id) : mDbHelper.fetchCellData((int) info.id);
	    	if (c.getCount() > 0) {
	    		c.moveToFirst();
	    		while (!c.isAfterLast()) {
	    			JSONObject tower = new JSONObject();
	    			try {
	    				tower.put("cell_id", c.getInt(c.getColumnIndex(WapdroidDbAdapter.CELLS_CID)));
	    				android.util.Log.v("Wapdroid", "cell_id:" + c.getInt(c.getColumnIndex(WapdroidDbAdapter.CELLS_CID)));
	    				tower.put("location_area_code", c.getInt(c.getColumnIndex(WapdroidDbAdapter.CELLS_LAC)));
	    				android.util.Log.v("Wapdroid", "location_area_code:" + c.getInt(c.getColumnIndex(WapdroidDbAdapter.CELLS_LAC)));
	    				tower.put("mobile_country_code", operator.substring(0, 3));
	    				android.util.Log.v("Wapdroid", "mobile_country_code:" + operator.substring(0, 3));
	    				tower.put("mobile_network_code", operator.substring(3));
	    				android.util.Log.v("Wapdroid", "mobile_network_code:" + operator.substring(3));
	    				query.accumulate("cell_towers", tower);}
	    			catch (JSONException e) {}
	    			c.moveToNext();}}
	    	c.close();
	    	String lat = "", lon = "";
	    	JSONTokener jsontokener = new JSONTokener(post(query));
	    	jsontokener.skipPast("latitude\":");
	    	try {
	    		lat = coordinate(jsontokener.nextValue().toString());}
	    	catch (JSONException e) {}
	    	jsontokener.skipPast("longitude\":");
	    	try {
	    		lon = coordinate(jsontokener.nextValue().toString());}
	    	catch (JSONException e) {}
			android.util.Log.v("Wapdroid", "geo:" + lat + "," + lon);
			startActivity(new Intent(Intent.ACTION_VIEW).setData(Uri.parse("geo:" + lat + "," + lon)));
			return true;}
		return super.onContextItemSelected(item);}
    
    @Override
    protected void onListItemClick(ListView list, View view, int position, long id) {
    	super.onListItemClick(list, view, position, id);
    	if (mNetwork == -1) {
    		Intent intent = new Intent(this, ManageData.class);
    		intent.putExtra(WapdroidDbAdapter.TABLE_ID, (int) id);
    		startActivity(intent);}}
    
    public String coordinate(String value) {
    	return new String(value.getBytes(), 0, value.length() -1);}
    
    public void listData() throws RemoteException {
    	// filter results
    	String cellsSet = "";
   		if (mCid > 0) {
   			cellsSet = "'" + Integer.toString(mCid) + "'";
   			if (!mNeighboringCells.isEmpty()) {
   				for (NeighboringCellInfo n : mNeighboringCells) cellsSet += ",'" + Integer.toString(n.getCid()) + "'";}}
    	Cursor c = mNetwork == -1 ? mDbHelper.fetchNetworks(mFilter, cellsSet) : mDbHelper.fetchCellsByNetworkFilter(mNetwork, mFilter, cellsSet);
        startManagingCursor(c);
        SimpleCursorAdapter data = mNetwork == -1 ?
        		new SimpleCursorAdapter(this,
        				R.layout.network_row,
        				c,
        				new String[] {WapdroidDbAdapter.NETWORKS_SSID, WapdroidDbAdapter.NETWORKS_BSSID, WapdroidDbAdapter.STATUS},
        				new int[] {R.id.network_row_SSID, R.id.network_row_BSSID, R.id.network_row_status})
        		: new SimpleCursorAdapter(this,
        				R.layout.cell_row,
        				c,
        				new String[] {WapdroidDbAdapter.CELLS_CID, WapdroidDbAdapter.STATUS},
        				new int[] {R.id.cell_row_CID, R.id.cell_row_status});
        setListAdapter(data);}

	public String post(JSONObject query) {
		String response = "";
		DefaultHttpClient httpClient = new DefaultHttpClient();
		ResponseHandler <String> responseHandler = new BasicResponseHandler();
		HttpPost postMethod = new HttpPost("https://www.google.com/loc/json");
		try {
			postMethod.setEntity(new StringEntity(query.toString()));}
		catch (UnsupportedEncodingException e) {}
		postMethod.setHeader("Accept", "application/json");
		postMethod.setHeader("Content-type", "application/json");
		try {
			response = httpClient.execute(postMethod, responseHandler);}
		catch (ClientProtocolException e) {}
		catch (IOException e) {}
		return response;}
    
    private void checkLocation(CellLocation location) {
   		if (mTeleManager.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM) {
   			mCid = ((GsmCellLocation) location).getCid();}
       	else if (mTeleManager.getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA) {
    		// check the phone type, cdma is not available before API 2.0, so use a wrapper
       		try {
       			mCid = (new CdmaCellLocationWrapper(location)).getBaseStationId();}
       		catch (Throwable t) {
       			mCid = -1;}}
   		mNeighboringCells = mTeleManager.getNeighboringCellInfo();
   		mTeleManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CELL_LOCATION);}}