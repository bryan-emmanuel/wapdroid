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

import static com.piusvelte.wapdroid.App.TAG;
import static com.piusvelte.wapdroid.App.TABLE_ID;
import static com.piusvelte.wapdroid.App.TABLE_NETWORKS;
import static com.piusvelte.wapdroid.App.NETWORKS_SSID;
import static com.piusvelte.wapdroid.App.NETWORKS_BSSID;
import static com.piusvelte.wapdroid.App.CELLS_CID;
import static com.piusvelte.wapdroid.App.LOCATIONS_LAC;
import static com.piusvelte.wapdroid.App.TABLE_PAIRS;
import static com.piusvelte.wapdroid.App.PAIRS_CELL;
import static com.piusvelte.wapdroid.App.PAIRS_NETWORK;
import static com.piusvelte.wapdroid.App.PAIRS_RSSI_MIN;
import static com.piusvelte.wapdroid.App.PAIRS_RSSI_MAX;
import static android.telephony.NeighboringCellInfo.UNKNOWN_CID;
import static android.telephony.NeighboringCellInfo.UNKNOWN_RSSI;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
//import android.database.SQLException;
//import android.database.sqlite.SQLiteDatabase;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.admob.android.ads.AdListener;
import com.admob.android.ads.AdView;
import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;

public class MapData extends MapActivity implements AdListener, DialogInterface.OnClickListener, DialogInterface.OnCancelListener {
	private static final int REFRESH_ID = Menu.FIRST;
	public static final String OPERATOR = "operator";
	public static final String CARRIER = "carrier";
	private static final String version = "version";
	private static final String gmaps_version = "1.1.0";
	private static final String host = "host";
	private static final String gmaps = "maps.google.com";
	private static final String access_token = "access_token";
	private static final String home_mcc = "home_mobile_country_code";
	private static final String home_mnc = "home_mobile_network_code";
	private static final String carrier = "carrier";
	private static final String cell_id = "cell_id";
	private static final String location_area_code = "location_area_code";
	private static final String mcc = "mobile_country_code";
	private static final String mnc = "mobile_network_code";
	private static final String cell_towers = "cell_towers";
	private static final String latitude = "latitude";
	private static final String longitude = "longitude";
	private static final String wifi_towers = "wifi_towers";
	private static final String mac_address = "mac_address";
	private static final String signal_strength = "signal_strength";
	public static int color_primary;
	public static int color_secondary;
	public static Drawable drawable_cell;
	public static Drawable drawable_network;
	public static String string_cancel;
	public static String string_deleteCell;
	public static String string_deleteNetwork;
	public static String string_cellWarning;
	public static String string_cid;
	public static String string_linefeed;
	public static String string_lac;
	public static String string_range;
	public static String string_colon;
	//	private SQLiteDatabase mDb;
	//	private DatabaseHelper mDbHelper;
	private App mApp;
	private Context mContext;
	private int mNetwork, mMCC = 0, mMNC = 0;
	public int mPair = 0;
	private String mCarrier = "", mToken = "", mMsg = "";
	public MapView mMView;
	private MapController mMController;
	private ProgressDialog mLoadingDialog;
	private Thread mThread;
	final Handler mHandler = new Handler();
	final Runnable mUpdtDialog = new Runnable() {
		public void run() {
			updtDialog();
		}
	};

	//@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.map);
		mContext = this;
		//		mDbHelper = new DatabaseHelper(this);
		mApp = (App) getApplication();
		mMView = (MapView) findViewById(R.id.mapview);
		mMView.setBuiltInZoomControls(true);
		mMController = mMView.getController();
		mMController.setZoom(12);
		Bundle extras = getIntent().getExtras();
		if (extras != null) {
			mNetwork = extras.getInt(TABLE_NETWORKS);
			mPair = extras.getInt(TABLE_PAIRS);
			String operator = extras.getString(OPERATOR);
			if (operator.length() > 0) {
				mMCC = Integer.parseInt(operator.substring(0, 3));
				mMNC = Integer.parseInt(operator.substring(3));
			}
			mCarrier = extras.getString(CARRIER);
		}
		color_primary = getResources().getColor(R.color.primary);
		color_secondary = getResources().getColor(R.color.secondary);
		drawable_cell = getResources().getDrawable(R.drawable.cell);
		drawable_network = getResources().getDrawable(R.drawable.network);
		string_cancel = getResources().getString(R.string.cancel);
		string_deleteCell = getResources().getString(R.string.menu_deleteCell);
		string_deleteNetwork = getResources().getString(R.string.menu_deleteNetwork);
		string_cellWarning = getResources().getString(R.string.cellwarning);
		string_cid = getResources().getString(R.string.label_CID);
		string_linefeed = getResources().getString(R.string.linefeed);
		string_lac = getResources().getString(R.string.label_LAC);
		string_range = getResources().getString(R.string.range);
		string_colon = getResources().getString(R.string.colon);
	}

	@Override
	protected void onResume() {
		super.onResume();
		//		try {
		//			mDb = mDbHelper.getWritableDatabase();
		//		} catch (SQLException se) {
		//			Log.e(TAG,"unexpected " + se);
		//		}
		mapData();
	}

	@Override
	public void onPause() {
		super.onPause();
		//		if (mDb.isOpen()) mDb.close();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		//		if (mDb.isOpen()) mDb.close();
		//		mDbHelper.close();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		boolean result = super.onCreateOptionsMenu(menu);
		menu.add(0, REFRESH_ID, 0, R.string.menu_refreshNetworks).setIcon(android.R.drawable.ic_menu_rotate);
		return result;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case REFRESH_ID:
			mapData();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected boolean isRouteDisplayed() {
		return false;
	}

	private void updtDialog() {
		mLoadingDialog.setMessage(mMsg);
	}

	public String getRequestHeader() {
		return addString(version, gmaps_version)
		+ "," + addString(host, gmaps)
		+ "," + addInt(home_mcc, mMCC)
		+ "," + addInt(home_mnc, mMNC)
		+ "," + addString(carrier, mCarrier);
	}

	public String addInt(String key, int i) {
		return "\"" + key + "\":" + Integer.toString(i);
	}

	public String addString(String key, String s) {
		return "\"" + key + "\":\"" + s + "\"";
	}

	public String addArray(String key, String a) {
		return "\"" + key + "\":[" + a + "]";
	}

	public String getValue(String dictionary, String key) {
		int key_index = dictionary.indexOf(key), end;
		String value = "";
		if (key_index != UNKNOWN_CID) {
			key_index += key.length() + 1;
			key_index = dictionary.indexOf(":", key_index) + 1;
			end = dictionary.indexOf(",", key_index);
			if (end == UNKNOWN_CID) end = dictionary.indexOf("}", key_index);
			value = dictionary.substring(key_index, end);
		}
		return value;
	}

	public int parseCoordinate(String source, String key) {
		String value = getValue(source, key);
		int parsed = 0;
		if (value != "") parsed = (int) (Double.parseDouble(value) * 1E6);
		return parsed;
	}

	public GeoPoint getGeoPoint(String query) {
		String response = "";
		DefaultHttpClient httpClient = new DefaultHttpClient();
		ResponseHandler <String> responseHandler = new BasicResponseHandler();
		HttpPost postMethod = new HttpPost("https://www.google.com/loc/json");
		try {
			postMethod.setEntity(new StringEntity(query));
		} catch (UnsupportedEncodingException e) {
			Log.e(TAG, "post:setEntity error: "+e);
		}
		postMethod.setHeader("Accept", "application/json");
		postMethod.setHeader("Content-type", "application/json");
		try {
			response = httpClient.execute(postMethod, responseHandler);
		} catch (ClientProtocolException e) {
			Log.e(TAG, "post:ClientProtocolException error: "+e);
		} catch (IOException e) {
			Log.e(TAG, "post:IOException error: "+e);
		}
		if (mToken == "") {
			mToken = getValue(response, access_token);
			if (mToken.length() > 0) {
				mToken = mToken.substring(1);
				mToken = mToken.substring(0, mToken.length()-1);
			}
		}
		int lat = parseCoordinate(response, latitude);
		int lon = parseCoordinate(response, longitude);
		return new GeoPoint(lat, lon);
	}

	private String bldRequest(String towers, String bssid) {
		String request = "{" + getRequestHeader();
		if (mToken != "") request += "," + addString(access_token, mToken);
		if (towers != "") request += "," + addArray(cell_towers, towers);
		if (bssid != "") request += "," + addArray(wifi_towers, "{" + addString(mac_address, bssid) + "}");
		return request + "}";
	}

	public void mapData() {
		mLoadingDialog = new ProgressDialog(this);
		mLoadingDialog.setTitle(R.string.loading);
		mLoadingDialog.setMessage((mPair == 0 ? PAIRS_NETWORK : PAIRS_CELL));
		mLoadingDialog.setCancelable(true);
		mLoadingDialog.setOnCancelListener(this);
		mLoadingDialog.setButton(ProgressDialog.BUTTON_NEGATIVE, getString(R.string.cancel), this);
		mLoadingDialog.show();
		mThread = new Thread() {
			public void run() {
				String ssid = "", bssid = "", towers = "";
				int ctr = 0;
				List<Overlay> mapOverlays = mMView.getOverlays();
				GeoPoint point = new GeoPoint(0, 0);
				//				Cursor pairs = mPair == 0 ? mDb.rawQuery("select " + TABLE_PAIRS + "." + TABLE_ID + " as " + TABLE_ID + ", " + NETWORKS_SSID + ", " + NETWORKS_BSSID + ", " + CELLS_CID + ", " + LOCATIONS_LAC + ", " + PAIRS_RSSI_MIN + ", " + PAIRS_RSSI_MAX
				//						+ " from " + TABLE_PAIRS + ", " + TABLE_NETWORKS + ", " + TABLE_CELLS + ", " + TABLE_LOCATIONS
				//						+ " where " + PAIRS_NETWORK + "=" + TABLE_NETWORKS + "." + TABLE_ID
				//						+ " and " + PAIRS_CELL + "=" + TABLE_CELLS + "." + TABLE_ID
				//						+ " and " + CELLS_LOCATION + "=" + TABLE_LOCATIONS + "." + TABLE_ID
				//						+ " and " + PAIRS_NETWORK + "=" + mNetwork, null) : mDb.rawQuery("select " + TABLE_PAIRS + "." + TABLE_ID + " as " + TABLE_ID + ", " + NETWORKS_SSID + ", " + NETWORKS_BSSID + ", " + CELLS_CID + ", " + LOCATIONS_LAC + ", " + PAIRS_RSSI_MIN + ", " + PAIRS_RSSI_MAX
				//								+ " from " + TABLE_PAIRS + ", " + TABLE_NETWORKS + ", " + TABLE_CELLS + ", " + TABLE_LOCATIONS
				//								+ " where " + PAIRS_NETWORK + "=" + TABLE_NETWORKS + "." + TABLE_ID
				//								+ " and " + PAIRS_CELL + "=" + TABLE_CELLS + "." + TABLE_ID
				//								+ " and " + CELLS_LOCATION + "=" + TABLE_LOCATIONS + "." + TABLE_ID
				//								+ " and " + TABLE_PAIRS + "." + TABLE_ID + "=" + mPair, null);
				if (mApp.mDb.isOpen()) {
					Cursor pairs = mPair == 0 ? mApp.fetchNetworkData(mNetwork) : mApp.fetchPairData(mPair);
					int ct = pairs.getCount();
					if (ct > 0) {
						WapdroidItemizedOverlay pinOverlays = new WapdroidItemizedOverlay((MapData) mContext, ct);
						pairs.moveToFirst();
						while (!interrupted() && !pairs.isAfterLast()) {
							ctr++;
							int cid = pairs.getInt(pairs.getColumnIndex(CELLS_CID)),
							lac = pairs.getInt(pairs.getColumnIndex(LOCATIONS_LAC)),
							rssi_min = pairs.getInt(pairs.getColumnIndex(PAIRS_RSSI_MIN)),
							rssi_max = pairs.getInt(pairs.getColumnIndex(PAIRS_RSSI_MAX)),
							rssi_avg = Math.round((rssi_min + rssi_max) / 2),
							rssi_range = Math.abs(rssi_min) - Math.abs(rssi_max);
							mMsg = string_cellWarning + PAIRS_CELL + " " + Integer.toString(ctr) + " of " + Integer.toString(ct);
							mHandler.post(mUpdtDialog);
							String tower = "{" + addInt(cell_id, cid) + "," + addInt(location_area_code, lac) + "," + addInt(mcc, mMCC) + "," + addInt(mnc, mMNC);
							if (rssi_avg != UNKNOWN_RSSI) tower += "," + addInt(signal_strength, rssi_avg);
							tower += "}";
							if (ssid == "") ssid = pairs.getString(pairs.getColumnIndex(NETWORKS_SSID));
							if (bssid == "") bssid = pairs.getString(pairs.getColumnIndex(NETWORKS_BSSID));
							if (towers != "") towers += ",";
							towers += tower;
							point = getGeoPoint(bldRequest(tower, bssid));
							pinOverlays.addOverlay(new WapdroidOverlayItem(point, PAIRS_CELL,
									string_cid + Integer.toString(cid)
									+ string_linefeed + string_lac + Integer.toString(lac)
									+ string_linefeed + string_range + Integer.toString(rssi_min) + string_colon + Integer.toString(rssi_max),
									mNetwork, pairs.getInt(pairs.getColumnIndex(TABLE_ID)), rssi_avg, rssi_range));
							pairs.moveToNext();
						}
						if (mPair == 0) {
							mMsg = PAIRS_NETWORK + ": " + ssid;
							mHandler.post(mUpdtDialog);
							point = getGeoPoint(bldRequest(towers, bssid));
							Location location = new Location("");
							location.setLatitude(point.getLatitudeE6()/1e6);
							location.setLongitude(point.getLongitudeE6()/1e6);
							pinOverlays.addOverlay(new WapdroidOverlayItem(point, PAIRS_NETWORK, ssid, mNetwork), drawable_network);
							pinOverlays.setDistances(location);
						}
						mapOverlays.add(pinOverlays);
						mMController.setCenter(point);
					}
					pairs.close();
				} else Log.e(TAG, "database unavailable");
				mLoadingDialog.dismiss();
				interrupt();
			}
		};
		mThread.start();
	}

	@Override
	public void onFailedToReceiveAd(AdView arg0) {}

	@Override
	public void onFailedToReceiveRefreshedAd(AdView arg0) {}

	@Override
	public void onReceiveAd(AdView arg0) {}

	@Override
	public void onReceiveRefreshedAd(AdView arg0) {}

	@Override
	public void onClick(DialogInterface dialog, int which) {
		mThread.interrupt();
		// if a cell was deleted, an array out of bounds error will be thrown if the map isn't redrawn, so finish if the redraw is cancelled
		finish();
	}

	@Override
	public void onCancel(DialogInterface dialog) {
		mThread.interrupt();
		// if a cell was deleted, an array out of bounds error will be thrown if the map isn't redrawn, so finish if the redraw is cancelled
		finish();
	}
}
