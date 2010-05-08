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

import java.util.List;

import com.piusvelte.wapdroid.R;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
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

public class ManageNetworks extends ListActivity {
	private WapdroidDbAdapter mDbHelper;
	public static final int REFRESH_ID = Menu.FIRST;
    private static final int DELETE_ID = Menu.FIRST + 1;
    private static final int FILTER_ID = Menu.FIRST + 2;
    private static int mFilter = WapdroidDbAdapter.FILTER_ALL;
    private AlertDialog mAlertDialog;
	private TelephonyManager mTeleManager;
	private String mCellsSet = "";
	private final PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
    	public void onCellLocationChanged(CellLocation location) {
    		checkLocation(location);}};
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.networks_list);
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
			listNetworks();}
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
				listNetworks();}
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
    					@Override
    					public void onClick(DialogInterface dialog, int which) {
    						mAlertDialog.dismiss();
    						mFilter = Integer.parseInt(getResources().getStringArray(R.array.filter_values)[which]);
    						try {
								listNetworks();}
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
		menu.add(0, DELETE_ID, 0, R.string.menu_deleteNetwork);}

    @Override
	public boolean onContextItemSelected(MenuItem item) {
    	AdapterContextMenuInfo info;
		switch(item.getItemId()) {
		case DELETE_ID:
			info = (AdapterContextMenuInfo) item.getMenuInfo();
			mDbHelper.deleteNetwork((int) info.id);
			try {
				listNetworks();}
			catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();}
			return true;}
		return super.onContextItemSelected(item);}
    
    @Override
    protected void onListItemClick(ListView list, View view, int position, long id) {
    	super.onListItemClick(list, view, position, id);
    	Intent intent = new Intent(this, ManageCells.class);
    	intent.putExtra(WapdroidDbAdapter.TABLE_ID, (int) id);
    	startActivity(intent);}
    
    public void listNetworks() throws RemoteException {
    	// filter results
        Cursor c = mDbHelper.fetchNetworks(mFilter, mCellsSet);
        startManagingCursor(c);
        SimpleCursorAdapter networks = new SimpleCursorAdapter(this,
        		R.layout.network_row,
        		c,
        		new String[] {WapdroidDbAdapter.NETWORKS_SSID, WapdroidDbAdapter.NETWORKS_BSSID, WapdroidDbAdapter.STATUS},
        		new int[] {R.id.network_row_SSID, R.id.network_row_BSSID, R.id.network_row_status});
        setListAdapter(networks);}
    
    private void checkLocation(CellLocation location) {
    	int cid = -1;
   		if (mTeleManager.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM) {
   			cid = ((GsmCellLocation) location).getCid();}
       	else if (mTeleManager.getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA) {
    		// check the phone type, cdma is not available before API 2.0, so use a wrapper
       		try {
       			cid = (new CdmaCellLocationWrapper(location)).getBaseStationId();}
       		catch (Throwable t) {
       			cid = -1;}}
   		List<NeighboringCellInfo> neighboringCells = mTeleManager.getNeighboringCellInfo();
   		if (cid > 0) {
   			mCellsSet = "'" + Integer.toString(cid) + "'";
   			if (!neighboringCells.isEmpty()) {
   				for (NeighboringCellInfo n : neighboringCells) mCellsSet += ",'" + Integer.toString(n.getCid()) + "'";}}
   		else mTeleManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CELL_LOCATION);}}