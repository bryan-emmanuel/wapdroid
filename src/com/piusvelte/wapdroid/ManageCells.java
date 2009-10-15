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

public class ManageCells extends ListActivity {
	private WapdroidDbAdapter mDbHelper;
	private int mNetwork;
    private static final int REFRESH_ID = Menu.FIRST;
    private static final int DELETE_ID = Menu.FIRST + 1;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.cells_list);
		mDbHelper = new WapdroidDbAdapter(this);
		mDbHelper.open();
		Bundle extras = getIntent().getExtras();
		if (extras != null) {
		    mNetwork = extras.getInt(WapdroidDbAdapter.TABLE_ID);}
		listCells();
        registerForContextMenu(getListView());}
	
	@Override
	protected void onPause() {
		super.onPause();
		mDbHelper.close();}
	
	@Override
	protected void onResume() {
		super.onResume();
		mDbHelper.open();}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		mDbHelper.close();}
	
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	boolean result = super.onCreateOptionsMenu(menu);
    	menu.add(0, REFRESH_ID, 0, R.string.menu_refreshCells);
    	return result;}
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	switch (item.getItemId()) {
    	case REFRESH_ID:
    		listCells();
    		return true;}
        return super.onOptionsItemSelected(item);}
    
    @Override
	public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, view, menuInfo);
		menu.add(0, DELETE_ID, 0, R.string.menu_deleteCell);}
    
    @Override
	public boolean onContextItemSelected(MenuItem item) {
    	AdapterContextMenuInfo info;
		switch(item.getItemId()) {
		case DELETE_ID:
			info = (AdapterContextMenuInfo) item.getMenuInfo();
			mDbHelper.deleteCell((int) info.id);
			listCells();
			return true;}
		return super.onContextItemSelected(item);}
    
    @Override
    protected void onListItemClick(ListView list, View view, int position, long id) {
    	super.onListItemClick(list, view, position, id);}

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
    	super.onActivityResult(requestCode, resultCode, intent);}
   
   public void listCells() {
        Cursor c = mDbHelper.fetchCellsByNetwork(mNetwork);
        startManagingCursor(c);
        SimpleCursorAdapter cells = new SimpleCursorAdapter(this,
        		R.layout.cell_row,
        		c,
        		new String[]{WapdroidDbAdapter.CELLS_CID, WapdroidDbAdapter.CELLS_MNC, WapdroidDbAdapter.CELLS_MCC, WapdroidDbAdapter.CELLS_LAC, WapdroidDbAdapter.CELLS_RSSI},
        		new int[]{R.id.cell_row_CID, R.id.cell_row_MNC, R.id.cell_row_MCC, R.id.cell_row_LAC, R.id.cell_row_RSSI});
        setListAdapter(cells);}}
