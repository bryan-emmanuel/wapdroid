/*
 * Wapdroid - Android Location based Wifi Manager
 * Copyright (C) 2012 Bryan Emmanuel
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

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.CursorAdapter;
import android.support.v4.widget.SimpleCursorAdapter;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.CheckBox;
import android.widget.ListView;

import com.piusvelte.wapdroid.Wapdroid.Networks;
import com.piusvelte.wapdroid.Wapdroid.Pairs;
import com.piusvelte.wapdroid.Wapdroid.Ranges;

public class ManageData extends ListFragment implements LoaderManager.LoaderCallbacks<Cursor> {

    private static final int DATA_LOADER = 0;

    public static final String PROJECTION = "projection";
    public static final String SELECTION = "selection";
    public static final String SELECTION_ARGS = "selectionArgs";

    private static final String STATUS = "status";
    private String mBssid;
    private String mSsid;

    private final SimpleCursorAdapter.ViewBinder mViewBinder = new SimpleCursorAdapter.ViewBinder() {
        @Override
        public boolean setViewValue(View view, final Cursor cursor, int columnIndex) {
            if (columnIndex == cursor.getColumnIndex(Networks.MANAGE)) {
                final long id = cursor.getLong(cursor.getColumnIndex(Networks._ID));
                CheckBox isManaging = (CheckBox) view;
                isManaging.setChecked(cursor.getInt(columnIndex) == 1);
                isManaging.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ContentValues values = new ContentValues();
                        values.put(Networks.MANAGE, ((CheckBox) v).isChecked() ? 1 : 0);
                        getActivity().getContentResolver().update(Networks.CONTENT_URI, values, Networks._ID + "=?", new String[]{Long.toString(id)});
                        BackupManager.dataChanged(getActivity());
                    }
                });
                return true;
            } else if (columnIndex == cursor.getColumnIndex(Ranges.MANAGE_CELL)) {
                final long id = cursor.getLong(cursor.getColumnIndex(Pairs._ID));
                CheckBox isManaging = (CheckBox) view;
                isManaging.setChecked(cursor.getInt(columnIndex) == 1);
                isManaging.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ContentValues values = new ContentValues();
                        values.put(Pairs.MANAGE_CELL, ((CheckBox) v).isChecked() ? 1 : 0);
                        getActivity().getContentResolver().update(Pairs.CONTENT_URI, values, Pairs._ID + "=?", new String[]{Long.toString(id)});
                        BackupManager.dataChanged(getActivity());
                    }
                });
                return true;
            }

            return false;
        }
    };

    public void setWifi(String ssid, String bssid) {
        mSsid = ssid;
        mBssid = bssid;
        getLoaderManager().restartLoader(DATA_LOADER, getCursorArguments(), this);
    }

    public static ManageData newInstance(String ssid, String bssid) {
        ManageData manageData = new ManageData();
        Bundle arguments = new Bundle();
        arguments.putString(Networks.SSID, ssid);
        arguments.putString(Networks.BSSID, bssid);
        manageData.setArguments(arguments);
        return manageData;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);

        Bundle arguments = getArguments();
        mSsid = arguments.getString(Networks.SSID);
        mBssid = arguments.getString(Networks.BSSID);

        View rootView = inflater.inflate(R.layout.networks_list, container, false);

        Wapdroid.setupBannerAd(rootView);
        setHasOptionsMenu(true);
        return rootView;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        SimpleCursorAdapter adapter;

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            adapter = new SimpleCursorAdapter(getActivity(),
                    R.layout.network_row,
                    null,
                    new String[]{Networks.SSID, Networks.BSSID, STATUS, Networks.MANAGE},
                    new int[]{R.id.network_row_SSID, R.id.network_row_BSSID, R.id.network_row_status, R.id.network_manage});
        } else {
            adapter = new SimpleCursorAdapter(getActivity(),
                    R.layout.network_row,
                    null,
                    new String[]{Networks.SSID, Networks.BSSID, STATUS, Networks.MANAGE},
                    new int[]{R.id.network_row_SSID, R.id.network_row_BSSID, R.id.network_row_status, R.id.network_manage},
                    CursorAdapter.FLAG_REGISTER_CONTENT_OBSERVER);
        }

        adapter.setViewBinder(mViewBinder);
        setListAdapter(adapter);
        getLoaderManager().initLoader(DATA_LOADER, getCursorArguments(), this);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.networks, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.menu_refresh) {
            getLoaderManager().restartLoader(DATA_LOADER, getCursorArguments(), this);
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, view, menuInfo);
        getActivity().getMenuInflater().inflate(R.menu.data, menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        int id = (int) ((AdapterContextMenuInfo) item.getMenuInfo()).id;

        if (item.getItemId() == R.id.menu_remove) {
            ContentResolver contentResolver = getActivity().getContentResolver();
            contentResolver.delete(Networks.CONTENT_URI, Networks._ID + "=?", new String[]{String.valueOf(id)});
            BackupManager.dataChanged(getActivity());
        }

        return super.onContextItemSelected(item);
    }

    @Override
    public void onListItemClick(ListView list, final View view, int position, final long id) {
        super.onListItemClick(list, view, position, id);
        // TODO previously displayed cells, which are no longer used since geofencing
    }

    private Bundle getCursorArguments() {
        Bundle args = new Bundle();
        args.putStringArray(PROJECTION, getNetworksProjection());
        args.putString(SELECTION, null);
        args.putStringArray(SELECTION_ARGS, null);
        return args;
    }

    private String[] getNetworksProjection() {
        return new String[]{Networks._ID,
                Networks.SSID,
                Networks.BSSID,
                "CASE WHEN " + Networks.SSID + "='" + mSsid + "' OR " + Networks.BSSID + "='" + mBssid + "' THEN '" + getString(R.string.connected) + "' ELSE '' END AS " + STATUS,
                Networks.MANAGE};
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        switch (id) {
            case DATA_LOADER:
                return new CursorLoader(getActivity(),
                        Networks.CONTENT_URI,
                        args.getStringArray(PROJECTION),
                        args.getString(SELECTION),
                        args.getStringArray(SELECTION_ARGS),
                        STATUS);

            default:
                return null;
        }
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        SimpleCursorAdapter adapter = (SimpleCursorAdapter) getListAdapter();
        adapter.swapCursor(data);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        SimpleCursorAdapter adapter = (SimpleCursorAdapter) getListAdapter();
        adapter.swapCursor(null);
    }
}
