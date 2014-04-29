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
package com.piusvelte.wapdroid.core;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.text.TextUtils;
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
import android.widget.SimpleCursorAdapter;

import com.google.android.gms.ads.AdView;
import com.piusvelte.wapdroid.core.Wapdroid.Cells;
import com.piusvelte.wapdroid.core.Wapdroid.Networks;
import com.piusvelte.wapdroid.core.Wapdroid.Pairs;
import com.piusvelte.wapdroid.core.Wapdroid.Ranges;

import static com.piusvelte.wapdroid.core.Wapdroid.UNKNOWN_CID;
import static com.piusvelte.wapdroid.core.Wapdroid.UNKNOWN_RSSI;

public class ManageData extends ListFragment implements LoaderManager.LoaderCallbacks<Cursor> {

    private static final int DATA_LOADER = 0;

    public static final int NETWORK_ALL = 0;
    public static final int CID_ALL = 0;

    public static final String PROJECTION = "projection";
    public static final String SELECTION = "selection";
    public static final String SELECTION_ARGS = "selectionArgs";

    private long mNetwork = NETWORK_ALL;
    private long mCid = CID_ALL;
    private static final String STATUS = "status";
    private int mFilter = R.id.menu_filter_all;
    private String mCells;
    private String mBssid;
    private String mSsid;
    private SimpleCursorAdapter mCursorAdapter;
    private ManageDataListener mListener;

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
                        values.put(Networks.MANAGE, ((CheckBox) v).isChecked() ? 0 : 1);
                        getActivity().getContentResolver().update(Networks.getContentUri(getActivity()), values, Networks._ID + "=?", new String[]{Long.toString(id)});
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
                        values.put(Pairs.MANAGE_CELL, ((CheckBox) v).isChecked() ? 0 : 1);
                        getActivity().getContentResolver().update(Pairs.getContentUri(getActivity()), values, Pairs._ID + "=?", new String[]{Long.toString(id)});
                    }
                });
                return true;
            }

            return false;
        }
    };

    public void setCells(String cells) {
        mCells = cells;
        getLoaderManager().restartLoader(DATA_LOADER, getCursorArguments(), this);
    }

    public void setWifi(String ssid, String bssid) {
        mSsid = ssid;
        mBssid = bssid;
        getLoaderManager().restartLoader(DATA_LOADER, getCursorArguments(), this);
    }

    public void setWifiAndCells(String ssid, String bssid, String cells) {
        mSsid = ssid;
        mBssid = bssid;
        setCells(cells);
    }

    public interface ManageDataListener {
        public void onManageNetwork(long network);
    }

    public static ManageData newInstance(long network, long cid, String ssid, String bssid, String cells) {
        ManageData manageData = new ManageData();
        Bundle arguments = new Bundle();
        arguments.putLong(WapdroidProvider.TABLE_NETWORKS, network);
        arguments.putLong(Cells.CID, cid);
        arguments.putString(Networks.SSID, ssid);
        arguments.putString(Networks.BSSID, bssid);
        arguments.putString(WapdroidProvider.TABLE_CELLS, cells);
        manageData.setArguments(arguments);
        return manageData;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);

        Bundle arguments = getArguments();
        mNetwork = arguments.getLong(WapdroidProvider.TABLE_NETWORKS);
        mCid = arguments.getLong(Cells.CID);
        mSsid = arguments.getString(Networks.SSID);
        mBssid = arguments.getString(Networks.BSSID);
        mCells = arguments.getString(WapdroidProvider.TABLE_CELLS);

        View rootView;

        if (mNetwork == NETWORK_ALL) {
            rootView = inflater.inflate(R.layout.networks_list, container, false);
            mCursorAdapter = new SimpleCursorAdapter(getActivity(),
                    R.layout.network_row,
                    null,
                    new String[]{Networks.SSID, Networks.BSSID, STATUS, Networks.MANAGE},
                    new int[]{R.id.network_row_SSID, R.id.network_row_BSSID, R.id.network_row_status, R.id.network_manage});
        } else {
            rootView = inflater.inflate(R.layout.cells_list, container, false);
            mCursorAdapter = new SimpleCursorAdapter(getActivity(),
                    R.layout.cell_row,
                    null,
                    new String[]{Ranges.CID, Ranges.LAC, Ranges.RSSI_MIN, STATUS, Ranges.MANAGE_CELL},
                    new int[]{R.id.cell_row_CID, R.id.cell_row_LAC, R.id.cell_row_range, R.id.cell_row_status, R.id.cell_manage});
        }

        setupBannerAd(rootView);
        mCursorAdapter.setViewBinder(mViewBinder);

        if (!TextUtils.isEmpty(mCells)) getLoaderManager().initLoader(DATA_LOADER, getCursorArguments(), this);

        return rootView;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        try {
            mListener = (ManageDataListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString());
        }
    }

    private void setupBannerAd(View rootView) {
        AdView adView = (AdView) rootView.findViewById(R.id.adView);
        if (!getActivity().getPackageName().toLowerCase().contains(Wapdroid.PRO)) {
            com.google.android.gms.ads.AdRequest adRequest = new com.google.android.gms.ads.AdRequest.Builder().build();
            adView.loadAd(adRequest);
        } else {
            adView.setVisibility(View.GONE);
        }
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
        } else if (id == R.id.menu_filter_all
                || id == R.id.menu_filter_in_area
                || id == R.id.menu_filter_out_area
                || id == R.id.menu_filter_connected) {
            mFilter = id;
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

            if (mNetwork == NETWORK_ALL) {
                contentResolver.delete(Networks.getContentUri(getActivity()), Networks._ID + "=?", new String[]{String.valueOf(id)});
            } else {
                contentResolver.delete(Pairs.getContentUri(getActivity()), Pairs._ID + "=?", new String[]{String.valueOf(id)});
            }

            BackupManager.dataChanged(getActivity());
        }

        return super.onContextItemSelected(item);
    }

    @Override
    public void onListItemClick(ListView list, final View view, int position, final long id) {
        super.onListItemClick(list, view, position, id);
        if (mNetwork == NETWORK_ALL && mListener != null) {
            mListener.onManageNetwork(id);
        }
    }

    private Bundle getCursorArguments() {
        return mNetwork == NETWORK_ALL ? getNetworksCursorArguments() : getCellsCursorArguments();
    }

    private Bundle getNetworksCursorArguments() {
        Bundle args = new Bundle();
        args.putStringArray(PROJECTION, getNetworksProjection());
        args.putString(SELECTION, getNetworksSelection());
        args.putStringArray(SELECTION_ARGS, getNetworksSelectionArgs());
        return args;
    }

    private Bundle getCellsCursorArguments() {
        Bundle args = new Bundle();
        args.putStringArray(PROJECTION, getCellsProjection());
        args.putString(SELECTION, getCellsSelection());
        args.putStringArray(SELECTION_ARGS, getCellsSelectionArgs());
        return args;
    }

    private String[] getNetworksProjection() {
        return new String[]{Networks._ID,
                Networks.SSID,
                Networks.BSSID,
                (mFilter == R.id.menu_filter_all ?
                        "case when " + Networks.SSID + "='" + mSsid + "' and " + Networks.BSSID + "='" + mBssid + "' then '" + getString(R.string.connected)
                                + "' when " + Networks._ID + " in (select " + Ranges.NETWORK + " from " + WapdroidProvider.VIEW_RANGES + " where" + mCells + ") then '" + getString(R.string.withinarea)
                                + "' else '" + getString(R.string.outofarea) + "' end"
                        : "'" + getString(mFilter == R.id.menu_filter_connected ?
                        R.string.connected
                        : (mFilter == R.id.menu_filter_in_area ?
                        R.string.withinarea :
                        R.string.outofarea)) + "'") + " as " + STATUS,
                Networks.MANAGE};
    }

    private String[] getCellsProjection() {
        return new String[]{Ranges._ID,
                Ranges.CID,
                "case when " + Ranges.LAC + "=" + UNKNOWN_CID + " then '" + getString(R.string.unknown)
                        + "' else " + Ranges.LAC + " end as " + Ranges.LAC + ",case when " + Ranges.RSSI_MIN + "=" + UNKNOWN_RSSI + " or " + Ranges.RSSI_MAX + "=" + UNKNOWN_RSSI + " then '" + getString(R.string.unknown) + "' else (" + Ranges.RSSI_MIN + "||'" + getString(R.string.colon) + "'||" + Ranges.RSSI_MAX + "||'" + getString(R.string.dbm) + "') end as " + Ranges.RSSI_MIN + ","
                        + (mFilter == R.id.menu_filter_all ?
                        "case when " + Ranges.CID + "='" + mCid + "' then '" + getString(R.string.connected)
                                + "' when " + Ranges.CELL + " in (select " + Ranges.CELL + " from " + WapdroidProvider.VIEW_RANGES + " where " + Ranges.NETWORK + "=" + mNetwork + " and" + mCells + ")" + " then '" + getString(R.string.withinarea)
                                + "' else '" + getString(R.string.outofarea) + "' end"
                        : "'" + getString(mFilter == R.id.menu_filter_connected ?
                        R.string.connected
                        : (mFilter == R.id.menu_filter_in_area ?
                        R.string.withinarea
                        : R.string.outofarea)) + "'") + " as " + STATUS,
                Ranges.MANAGE_CELL};
    }

    private String getNetworksSelection() {
        return (mFilter == R.id.menu_filter_all ? null
                        : (mFilter == R.id.menu_filter_connected ?
                        Networks.SSID + "=? and "
                                + Networks.BSSID + "=?"
                        : Networks._ID + (mFilter == R.id.menu_filter_out_area ?
                        " NOT"
                        : "") + " in (select " + Ranges.NETWORK
                        + " from " + WapdroidProvider.VIEW_RANGES + " where" + mCells + ")"));
    }

    private String getCellsSelection() {
        return Ranges.NETWORK + "=?" + (mFilter == R.id.menu_filter_all ? "" :
                " and " + (mFilter == R.id.menu_filter_connected ?
                        Ranges.CID + "=?"
                        : Ranges.CELL + (mFilter == R.id.menu_filter_out_area ?
                        " NOT"
                        : "") + " in (select " + Ranges.CELL
                        + " from " + WapdroidProvider.VIEW_RANGES
                        + " where " + Ranges.NETWORK + "=" + mNetwork + " and"
                        + mCells + ")"));
    }

    private String[] getNetworksSelectionArgs() {
        return (mFilter == R.id.menu_filter_connected ? new String[]{mSsid, mBssid} : null);
    }

    private String[] getCellsSelectionArgs() {
        return (mFilter == R.id.menu_filter_connected ? new String[]{Long.toString(mNetwork), Long.toString(mCid)} : new String[]{Long.toString(mNetwork)});
    }


    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        switch (id) {
            case DATA_LOADER:
                if (mNetwork == NETWORK_ALL) {
                    return new CursorLoader(getActivity(),
                            Networks.getContentUri(getActivity()),
                            args.getStringArray(PROJECTION),
                            args.getString(SELECTION),
                            args.getStringArray(SELECTION_ARGS),
                            STATUS);
                } else {
                    return new CursorLoader(getActivity(),
                            Ranges.getContentUri(getActivity()),
                            args.getStringArray(PROJECTION),
                            args.getString(SELECTION),
                            args.getStringArray(SELECTION_ARGS),
                            STATUS);
                }

            default:
                return null;
        }
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        mCursorAdapter.changeCursor(data);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mCursorAdapter.changeCursor(null);
    }
}