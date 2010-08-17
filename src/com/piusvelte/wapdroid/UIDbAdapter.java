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

import static com.piusvelte.wapdroid.WapdroidService.TABLE_NETWORKS;
import static com.piusvelte.wapdroid.WapdroidService.TABLE_ID;
import static com.piusvelte.wapdroid.WapdroidService.NETWORKS_SSID;
import static com.piusvelte.wapdroid.WapdroidService.NETWORKS_BSSID;
import static com.piusvelte.wapdroid.WapdroidService.TABLE_CELLS;
import static com.piusvelte.wapdroid.WapdroidService.CELLS_CID;
import static com.piusvelte.wapdroid.WapdroidService.CELLS_LOCATION;
import static com.piusvelte.wapdroid.WapdroidService.UNKNOWN_CID;
import static com.piusvelte.wapdroid.WapdroidService.UNKNOWN_RSSI;
import static com.piusvelte.wapdroid.WapdroidService.TABLE_PAIRS;
import static com.piusvelte.wapdroid.WapdroidService.PAIRS_CELL;
import static com.piusvelte.wapdroid.WapdroidService.PAIRS_NETWORK;
import static com.piusvelte.wapdroid.WapdroidService.PAIRS_RSSI_MAX;
import static com.piusvelte.wapdroid.WapdroidService.PAIRS_RSSI_MIN;
import static com.piusvelte.wapdroid.WapdroidService.TABLE_LOCATIONS;
import static com.piusvelte.wapdroid.WapdroidService.LOCATIONS_LAC;
import static com.piusvelte.wapdroid.WapdroidService.FILTER_ALL;
import static com.piusvelte.wapdroid.WapdroidService.FILTER_CONNECTED;
import static com.piusvelte.wapdroid.WapdroidService.FILTER_INRANGE;
import static com.piusvelte.wapdroid.WapdroidService.FILTER_OUTRANGE;
import static com.piusvelte.wapdroid.WapdroidService.STATUS;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

public class UIDbAdapter {
    private DatabaseHelper mDbHelper;
    private SQLiteDatabase mDb;

    public final Context mContext;

    public UIDbAdapter(Context context) {
            this.mContext = context;
    }

    public UIDbAdapter open() throws SQLException {
            mDbHelper = new DatabaseHelper(mContext);
            mDb = mDbHelper.getWritableDatabase();
            return this;
    }

    public void close() {
            mDbHelper.close();
    }

    public Cursor fetchNetworks(int filter, String bssid, String cells) {
            return mDb.rawQuery("select " + TABLE_NETWORKS + "." + TABLE_ID + " as " + TABLE_ID + ", " + NETWORKS_SSID + ", " + NETWORKS_BSSID + ", "
                            + ((filter == FILTER_ALL) ?
                                            ("case when " + NETWORKS_BSSID + "='" + bssid + "' then '" + mContext.getResources().getString(R.string.connected)
                                                            + "' else (case when " + TABLE_NETWORKS + "." + TABLE_ID + " in (select " + PAIRS_NETWORK
                                                            + " from " + TABLE_PAIRS + ", " + TABLE_CELLS + ", " + TABLE_LOCATIONS
                                                            + " where " + PAIRS_CELL + "=" + TABLE_CELLS + "." + TABLE_ID
                                                            + " and " + CELLS_LOCATION + "=" + TABLE_LOCATIONS + "." + TABLE_ID
                                                            + " and (" + cells + "))" + " then '" + mContext.getResources().getString(R.string.withinarea)
                                                            + "' else '" + mContext.getResources().getString(R.string.outofarea) + "' end) end")
                                                            : "'" + (mContext.getResources().getString(filter == FILTER_CONNECTED ? R.string.connected : filter == FILTER_INRANGE ? R.string.withinarea : R.string.outofarea) + "'"))
                                                            + " as " + STATUS
                                                            + " from " + TABLE_NETWORKS
                                                            + (filter != FILTER_ALL ?
                                                                            " where "
                                                                            + (filter == FILTER_CONNECTED ?
                                                                                            NETWORKS_BSSID + "='" + bssid + "'"
                                                                                            : TABLE_NETWORKS + "." + TABLE_ID + (filter == FILTER_OUTRANGE ? " NOT" : "") + " in (select " + PAIRS_NETWORK
                                                                                            + " from " + TABLE_PAIRS + ", " + TABLE_CELLS + ", " + TABLE_LOCATIONS
                                                                                            + " where " + PAIRS_CELL + "=" + TABLE_CELLS + "." + TABLE_ID
                                                                                            + " and " + CELLS_LOCATION + "=" + TABLE_LOCATIONS + "." + TABLE_ID
                                                                                            + " and (" + cells + "))")
                                                                                            : " order by " + STATUS), null);
    }

    public Cursor fetchNetworkData(int network) {
            return mDb.rawQuery("select " + TABLE_PAIRS + "." + TABLE_ID + " as " + TABLE_ID + ", " + NETWORKS_SSID + ", " + NETWORKS_BSSID + ", " + CELLS_CID + ", " + LOCATIONS_LAC + ", " + PAIRS_RSSI_MIN + ", " + PAIRS_RSSI_MAX
                            + " from " + TABLE_PAIRS + ", " + TABLE_NETWORKS + ", " + TABLE_CELLS + ", " + TABLE_LOCATIONS
                            + " where " + PAIRS_NETWORK + "=" + TABLE_NETWORKS + "." + TABLE_ID
                            + " and " + PAIRS_CELL + "=" + TABLE_CELLS + "." + TABLE_ID
                            + " and " + CELLS_LOCATION + "=" + TABLE_LOCATIONS + "." + TABLE_ID
                            + " and " + PAIRS_NETWORK + "=" + network, null);
    }

    public Cursor fetchPairData(int pair) {
            return mDb.rawQuery("select " + TABLE_PAIRS + "." + TABLE_ID + " as " + TABLE_ID + ", " + NETWORKS_SSID + ", " + NETWORKS_BSSID + ", " + CELLS_CID + ", " + LOCATIONS_LAC + ", " + PAIRS_RSSI_MIN + ", " + PAIRS_RSSI_MAX
                            + " from " + TABLE_PAIRS + ", " + TABLE_NETWORKS + ", " + TABLE_CELLS + ", " + TABLE_LOCATIONS
                            + " where " + PAIRS_NETWORK + "=" + TABLE_NETWORKS + "." + TABLE_ID
                            + " and " + PAIRS_CELL + "=" + TABLE_CELLS + "." + TABLE_ID
                            + " and " + CELLS_LOCATION + "=" + TABLE_LOCATIONS + "." + TABLE_ID
                            + " and " + TABLE_PAIRS + "." + TABLE_ID + "=" + pair, null);
    }

    public Cursor fetchPairsByNetwork(int network) {
            return mDb.rawQuery("select " + TABLE_PAIRS + "." + TABLE_ID + " as " + TABLE_ID + ", " + CELLS_CID
                            + " from " + TABLE_PAIRS + ", " + TABLE_CELLS
                            + " where " + PAIRS_CELL + "=" + TABLE_CELLS + "." + TABLE_ID
                            + " and "+ PAIRS_NETWORK + "=" + network, null);
    }

    public Cursor fetchPairsByNetworkFilter(int filter, int network, int cid, String cells) {
            return mDb.rawQuery("select " + TABLE_PAIRS + "." + TABLE_ID + " as " + TABLE_ID + ", " + CELLS_CID + ", "
                            + "case when " + LOCATIONS_LAC + "=" + UNKNOWN_CID + " then '" + mContext.getResources().getString(R.string.unknown) + "' else " + LOCATIONS_LAC + " end as " + LOCATIONS_LAC + ", "
                            + "case when " + PAIRS_RSSI_MIN + "=" + UNKNOWN_RSSI + " then '" + mContext.getResources().getString(R.string.unknown) + "' else (" + PAIRS_RSSI_MIN + "||'" + mContext.getResources().getString(R.string.colon) + "'||" + PAIRS_RSSI_MAX + "||'" + mContext.getResources().getString(R.string.dbm) + "') end as " + PAIRS_RSSI_MIN + ", "
                            + ((filter == FILTER_ALL) ?
                                            ("case when " + CELLS_CID + "='" + cid + "' then '" + mContext.getResources().getString(R.string.connected)
                                                            + "' else (case when " + TABLE_CELLS + "." + TABLE_ID + " in (select " + TABLE_CELLS + "." + TABLE_ID
                                                            + " from " + TABLE_PAIRS + ", " + TABLE_CELLS + ", " + TABLE_LOCATIONS
                                                            + " where " + PAIRS_CELL + "=" + TABLE_CELLS + "." + TABLE_ID
                                                            + " and " + CELLS_LOCATION + "=" + TABLE_LOCATIONS + "." + TABLE_ID
                                                            + " and " + PAIRS_NETWORK + "=" + network
                                                            + " and " + cells + ")" + " then '" + mContext.getResources().getString(R.string.withinarea)
                                                            + "' else '" + mContext.getResources().getString(R.string.outofarea) + "' end) end as ")
                                                            : "'" + (mContext.getResources().getString(filter == FILTER_CONNECTED ? R.string.connected : filter == FILTER_INRANGE ? R.string.withinarea : R.string.outofarea) + "' as "))
                                                            + STATUS
                                                            + " from " + TABLE_PAIRS
                                                            + " left join " + TABLE_CELLS
                                                            + " on " + PAIRS_CELL + "=" + TABLE_CELLS + "." + TABLE_ID
                                                            + " left outer join " + TABLE_LOCATIONS
                                                            + " on " + CELLS_LOCATION + "=" + TABLE_LOCATIONS + "." + TABLE_ID
                                                            + " where "+ PAIRS_NETWORK + "=" + network
                                                            + (filter != FILTER_ALL ?
                                                                            " and "
                                                                            + (filter == FILTER_CONNECTED ?
                                                                                            CELLS_CID + "='" + cid + "'"
                                                                                            : TABLE_CELLS + "." + TABLE_ID + (filter == FILTER_OUTRANGE ? " NOT" : "") + " in (select " + TABLE_CELLS + "." + TABLE_ID
                                                                                            + " from " + TABLE_PAIRS + ", " + TABLE_CELLS + ", " + TABLE_LOCATIONS
                                                                                            + " where " + PAIRS_CELL + "=" + TABLE_CELLS + "." + TABLE_ID
                                                                                            + " and " + CELLS_LOCATION + "=" + TABLE_LOCATIONS + "." + TABLE_ID
                                                                                            + " and " + PAIRS_NETWORK + "=" + network
                                                                                            + " and " + cells + ")")
                                                                                            : " order by " + STATUS), null);
    }

    public void cleanCellsLocations() {
            Cursor c = mDb.rawQuery("select " + TABLE_ID + ", " + CELLS_LOCATION + " from " + TABLE_CELLS, null);
            if (c.getCount() > 0) {
                    c.moveToFirst();
                    while (!c.isAfterLast()) {
                            int cell = c.getInt(c.getColumnIndex(TABLE_ID));
                            Cursor p = mDb.rawQuery("select " + TABLE_ID + " from " + TABLE_PAIRS + " where " + PAIRS_CELL + "=" + cell, null);
                            if (p.getCount() == 0) {
                                    mDb.delete(TABLE_CELLS, TABLE_ID + "=" + cell, null);
                                    int location = c.getInt(c.getColumnIndex(CELLS_LOCATION));
                                    Cursor l = mDb.rawQuery("select " + TABLE_ID + " from " + TABLE_CELLS + " where " + CELLS_LOCATION + "=" + location, null);
                                    if (l.getCount() == 0) mDb.delete(TABLE_LOCATIONS, TABLE_ID + "=" + location, null);
                                    l.close();
                            }
                            p.close();
                            c.moveToNext();
                    }
            }
            c.close();
    }

    public void deleteNetwork(int network) {
            mDb.delete(TABLE_NETWORKS, TABLE_ID + "=" + network, null);
            mDb.delete(TABLE_PAIRS, PAIRS_NETWORK + "=" + network, null);
            cleanCellsLocations();
    }

    public void deletePair(int network, int pair) {
            // get paired cell and location
            mDb.delete(TABLE_PAIRS, TABLE_ID + "=" + pair, null);
            Cursor n = fetchPairsByNetwork(network);
            if (n.getCount() == 0) mDb.delete(TABLE_NETWORKS, TABLE_ID + "=" + network, null);
            n.close();
            cleanCellsLocations();
    }
}