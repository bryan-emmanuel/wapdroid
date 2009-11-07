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

import com.piusvelte.wapdroid.R;

import android.app.ListActivity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
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
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.networks_list);
		mDbHelper = new WapdroidDbAdapter(this);
		mDbHelper.open();
        registerForContextMenu(getListView());}

	@Override
	protected void onDestroy() {
		super.onDestroy();
    	if (mDbHelper != null) {
    		mDbHelper.close();
    		mDbHelper = null;}}
	
	@Override
	protected void onResume() {
		super.onResume();
		listNetworks();}
	
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	boolean result = super.onCreateOptionsMenu(menu);
    	menu.add(0, REFRESH_ID, 0, R.string.menu_refreshNetworks);
    	return result;}

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	switch (item.getItemId()) {
    	case REFRESH_ID:
    		listNetworks();
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
			listNetworks();
			return true;}
		return super.onContextItemSelected(item);}
    
    @Override
    protected void onListItemClick(ListView list, View view, int position, long id) {
    	super.onListItemClick(list, view, position, id);
    	Intent intent = new Intent(this, ManageCells.class);
    	intent.putExtra(WapdroidDbAdapter.TABLE_ID, (int) id);
    	startActivity(intent);}
    
    public void listNetworks() {
        Cursor c = mDbHelper.fetchNetworks();
        startManagingCursor(c);
        SimpleCursorAdapter networks = new SimpleCursorAdapter(this,
        		R.layout.network_row,
        		c,
        		new String[] {WapdroidDbAdapter.NETWORKS_SSID},
        		new int[] {R.id.network_row_SSID});
        setListAdapter(networks);}}