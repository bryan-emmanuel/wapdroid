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

import android.net.Uri;
import android.provider.BaseColumns;

public final class Wapdroid {
	public static final String TAG = "Wapdroid";
	public static final String AUTHORITY = "com.piusvelte.wapdroid.providers.WapdroidContentProvider";

	private Wapdroid() {}

	public static final class Networks implements BaseColumns {
		private Networks() {}

		public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/networks");
		public static final String CONTENT_TYPE = "";
		public static final String CONTENT_ITEM_TYPE = "";
		public static final String SSID = "SSID";
		public static final String BSSID = "BSSID";		
	}

	public static final class Cells implements BaseColumns {
		private Cells() {}

		public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/cells");
		public static final String CONTENT_TYPE = "";
		public static final String CONTENT_ITEM_TYPE = "";
		public static final String CID = "CID";
		public static final String LOCATION = "location";
	}

	public static final class Pairs implements BaseColumns {
		private Pairs() {}

		public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/pairs");
		public static final String CONTENT_TYPE = "";
		public static final String CONTENT_ITEM_TYPE = "";
		public static final String CELL = "cell";
		public static final String NETWORK = "network";
		public static final String RSSI_MIN = "RSSI_min";
		public static final String RSSI_MAX = "RSSI_max";
	}


	public static final class Locations implements BaseColumns {
		private Locations() {}

		public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/locations");
		public static final String CONTENT_TYPE = "";
		public static final String CONTENT_ITEM_TYPE = "";
		public static final String LAC = "LAC";
	}

}
