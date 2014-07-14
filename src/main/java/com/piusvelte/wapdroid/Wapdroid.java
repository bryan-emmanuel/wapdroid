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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.provider.BaseColumns;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.location.Geofence;

public final class Wapdroid {

	private static final String TAG = "Wapdroid";
	public static final int UNKNOWN_CID = -1;
	public static final int UNKNOWN_RSSI = 99;

	public static final String ACTION_TOGGLE_SERVICE = "com.piusvelte.wapdroid.TOGGLE_SERVICE";
    private static final boolean HAS_ADS = "free".equals(BuildConfig.FLAVOR);

	protected static final int[] sDatabaseLock = new int[0];

	private Wapdroid() {}

	public static final class Networks implements BaseColumns {
		private Networks() {}

        public static final Uri CONTENT_URI = Uri.parse("content://" + WapdroidProvider.AUTHORITY + "/networks");
		public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.piusvelte.networks";

        public static final String TABLE_NAME = "networks";

		public static final String SSID = "ssid";
		public static final String BSSID = "bssid";
		public static final String MANAGE = "manage";

        // geofencing
        public static final float INVALID_FLOAT = -999.0f;
        /** [double] */
        public static final String LATITUDE = "latitude";
        /** [double] */
        public static final String LONGITUDE = "longitude";
        /** [float] */
        public static final String RADIUS = "radius";
        /** rssi signal strength at the coordinates [int] */
        public static final String COORD_RSSI = "coord_rssi";

        public static void createTable(SQLiteDatabase database) {
            database.execSQL("CREATE TABLE " + TABLE_NAME + " ("
                    + _ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + SSID + " TEXT NOT NULL,"
                    + BSSID + " TEXT NOT NULL,"
                    + LATITUDE + " REAL DEFAULT -999.0,"
                    + LONGITUDE + " REAL DEFAULT -999.0,"
                    + RADIUS + " REAL DEFAULT -999.0,"
                    + COORD_RSSI + " INTEGER DEFAULT 0,"
                    + MANAGE + " INTEGER DEFAULT 1);");
        }

        public static void getGeoFence(Cursor cursor) {
            new Geofence.Builder()
                    .setRequestId(String.valueOf(cursor.getInt(cursor.getColumnIndexOrThrow(_ID))))
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT)
                    .setCircularRegion(cursor.getDouble(cursor.getColumnIndexOrThrow(LATITUDE)),
                            cursor.getDouble(cursor.getColumnIndexOrThrow(LONGITUDE)),
                            cursor.getFloat(cursor.getColumnIndexOrThrow(RADIUS)))
                    .build();
        }
	}

	public static final class Cells implements BaseColumns {
		private Cells() {}

        public static final Uri CONTENT_URI = Uri.parse("content://" + WapdroidProvider.AUTHORITY + "/cells");
		public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.piusvelte.cells";

        public static final String TABLE_NAME = "cells";

		public static final String CID = "cid";
		public static final String LOCATION = "location";

        public static void createTable(SQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " ("
                    + Cells._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + Cells.CID + " INTEGER, "
                    + Cells.LOCATION + " INTEGER);");
        }
	}

	public static final class Locations implements BaseColumns {
		private Locations() {}

        public static final Uri CONTENT_URI = Uri.parse("content://" + WapdroidProvider.AUTHORITY + "/locations");
		public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.piusvelte.locations";

        public static final String TABLE_NAME = "locations";

		public static final String LAC = "lac";

        public static void createTable(SQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " ("
                    + Locations._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + Locations.LAC + " INTEGER);");
        }
	}

	public static final class Pairs implements BaseColumns {
		private Pairs() {}

        public static final Uri CONTENT_URI = Uri.parse("content://" + WapdroidProvider.AUTHORITY + "/pairs");
		public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.piusvelte.pairs";

        public static final String TABLE_NAME = "pairs";

		public static final String CELL = "cell";
		public static final String NETWORK = "network";
		public static final String RSSI_MIN = "rssi_min";
		public static final String RSSI_MAX = "rssi_max";
		public static final String MANAGE_CELL = "manage_cell";

        public static void createTable(SQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " ("
                    + Pairs._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + Pairs.CELL + " INTEGER, "
                    + Pairs.NETWORK + " INTEGER, "
                    + Pairs.RSSI_MIN + " INTEGER, "
                    + Pairs.RSSI_MAX + " INTEGER, "
                    + Pairs.MANAGE_CELL + " INTEGER);");
        }
	}

	public static final class Ranges implements BaseColumns {
		private Ranges() {}

        public static final Uri CONTENT_URI = Uri.parse("content://" + WapdroidProvider.AUTHORITY + "/ranges");
	 	public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.piusvelte.ranges";

        public static final String VIEW_NAME = "ranges";

		public static final String RSSI_MIN = "rssi_min";
		public static final String RSSI_MAX = "rssi_max";
		public static final String CID = "cid";
		public static final String LAC = "lac";
		public static final String LOCATION = "location";
		public static final String SSID = "ssid";
		public static final String BSSID = "bssid";
		public static final String CELL = "cell";
		public static final String NETWORK = "network";
		public static final String MANAGE = "manage";
		public static final String MANAGE_CELL = "manage_cell";

        public static void createView(SQLiteDatabase database) {
            database.execSQL("CREATE VIEW IF NOT EXISTS " + VIEW_NAME + " AS SELECT "
                    + Pairs.TABLE_NAME + "." + Ranges._ID + " AS " + Ranges._ID
                    + "," + Ranges.RSSI_MAX
                    + "," + Ranges.RSSI_MIN
                    + "," + Ranges.CID
                    + "," + Ranges.LAC
                    + "," + Ranges.LOCATION
                    + "," + Ranges.SSID
                    + "," + Ranges.BSSID
                    + "," + Ranges.CELL
                    + "," + Ranges.NETWORK
                    + "," + Ranges.MANAGE
                    + "," + Ranges.MANAGE_CELL
                    + " FROM " + Pairs.TABLE_NAME
                    + " LEFT JOIN " + Cells.TABLE_NAME + " ON " + Cells.TABLE_NAME + "." + Ranges._ID + "=" + Ranges.CELL
                    + " LEFT JOIN " + Locations.TABLE_NAME + " ON " + Locations.TABLE_NAME + "." + Ranges._ID + "=" + Ranges.LOCATION
                    + " LEFT JOIN " + Networks.TABLE_NAME + " ON " + Networks.TABLE_NAME + "." + Ranges._ID + "=" + Ranges.NETWORK + ";");
        }
	}

	protected static Class getPackageClass(Context context, Class cls) {
		try {
			return Class.forName(context.getPackageName() + "." + cls.getSimpleName());
		} catch (ClassNotFoundException e) {
			Log.e(TAG, e.getMessage());
		}
		return cls;
	}

	protected static Intent getPackageIntent(Context context, Class cls) {
		return new Intent(context, getPackageClass(context, cls));
	}
	
	protected static String stripQuotes(@Nullable String quotedStr) {
        if (quotedStr == null) return "";
		int strLen = quotedStr.length();
		if ((strLen > 1) && quotedStr.substring(0, 1).equals("\"") && quotedStr.subSequence((strLen - 1), strLen).equals("\""))
			return quotedStr.substring(1, (strLen - 1));
		else
			return quotedStr;
	}

    private static void setupBannerAd(AdView adView) {
        if (HAS_ADS) {
            AdRequest adRequest = new AdRequest.Builder().build();
            adView.loadAd(adRequest);
        } else {
            adView.setVisibility(View.GONE);
        }
    }

    protected static void setupBannerAd(Activity activity) {
        setupBannerAd((AdView) activity.findViewById(R.id.adView));
    }

    protected static void setupBannerAd(View rootView) {
        setupBannerAd((AdView) rootView.findViewById(R.id.adView));
    }
}
