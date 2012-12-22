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

import java.io.File;
import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.provider.BaseColumns;
import android.util.Log;

public final class Wapdroid {

	private static final String TAG = "Wapdroid";
	public static final int UNKNOWN_CID = -1;
	public static final int UNKNOWN_RSSI = 99;

	public static final String ACTION_TOGGLE_SERVICE = "com.piusvelte.wapdroid.Wapdroid.TOGGLE_SERVICE";
	protected static final String GOOGLE_AD_ID = "a14c03f0ced257b";
	protected static final String PRO = "pro";

	private static FileHandler sLogFileHandler = null;
	private static Logger sLogger = null;
	protected static final int[] sDatabaseLock = new int[0];

	private Wapdroid() {}

	public static final class Networks implements BaseColumns {
		private Networks() {}

		public static Uri getContentUri(Context context) {
			return Uri.parse("content://" + Wapdroid.getAuthority(context) + "/networks");
		}

		public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.piusvelte.networks";

		public static final String SSID = "ssid";
		public static final String BSSID = "bssid";
		public static final String MANAGE = "manage";
	}

	public static final class Cells implements BaseColumns {
		private Cells() {}

		public static Uri getContentUri(Context context) {
			return Uri.parse("content://" + Wapdroid.getAuthority(context) + "/cells");
		}

		public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.piusvelte.cells";

		public static final String CID = "cid";
		public static final String LOCATION = "location";
	}

	public static final class Locations implements BaseColumns {
		private Locations() {}

		public static Uri getContentUri(Context context) {
			return Uri.parse("content://" + Wapdroid.getAuthority(context) + "/locations");
		}

		public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.piusvelte.locations";

		public static final String LAC = "lac";
	}

	public static final class Pairs implements BaseColumns {
		private Pairs() {}

		public static Uri getContentUri(Context context) {
			return Uri.parse("content://" + Wapdroid.getAuthority(context) + "/pairs");
		}

		public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.piusvelte.pairs";

		public static final String CELL = "cell";
		public static final String NETWORK = "network";
		public static final String RSSI_MIN = "rssi_min";
		public static final String RSSI_MAX = "rssi_max";
		public static final String MANAGE_CELL = "manage_cell";
	}

	public static final class Ranges implements BaseColumns {
		private Ranges() {}

		public static Uri getContentUri(Context context) {
			return Uri.parse("content://" + Wapdroid.getAuthority(context) + "/ranges");
		}

		public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.piusvelte.ranges";

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
	}

	public static String getAuthority(Context context) {
		return !context.getPackageName().toLowerCase().contains(PRO) ? WapdroidProvider.AUTHORITY : WapdroidProvider.PRO_AUTHORITY;
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

	protected static synchronized void startLogging() {
		if (!hasLogger()) {
			String state = Environment.getExternalStorageState();
			if (Environment.MEDIA_MOUNTED.equals(state) && !Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
				String wapdroidDirPath = Environment.getExternalStorageDirectory().getPath() + "/wapdroid";
				File wapdroidDir = new File(wapdroidDirPath);
				boolean hasDir = wapdroidDir.exists();
				if (!hasDir)
					hasDir = wapdroidDir.mkdir();
				if (hasDir) {
					// remove the old log file
					File logFile = new File(wapdroidDirPath + "/wapdroid.log");
					if (logFile.exists())
						logFile.delete();
					try {
						sLogFileHandler = new FileHandler(wapdroidDirPath + "/wapdroid.log");
					} catch (IOException e) {
						Log.e(TAG, e.getMessage());
					}
					if (sLogFileHandler != null) {
						sLogger = Logger.getLogger("Wapdroid");
						sLogger.setUseParentHandlers(false);
						sLogger.addHandler(sLogFileHandler);
						sLogFileHandler.setFormatter(new SimpleFormatter(){
							@Override
							public String format(LogRecord record) {
								return new java.util.Date() + " " + record.getLevel() + " " + record.getMessage() + "\r\n";
							}
						});
					}
				}
			}
		}
	}
	
	private static boolean hasLogger() {
		return sLogFileHandler != null;
	}

	protected static synchronized void logInfo(String message) {
		if (hasLogger())
			sLogger.info(message);
	}

	protected static synchronized void stopLogging() {
		if (hasLogger()) {
			sLogger = null;
			sLogFileHandler.close();
			sLogFileHandler = null;
		}
	}
}
