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
package com.piusvelte.wapdroid.core;

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
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.LinearLayout;

import com.google.ads.*;
import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;

public class MapData extends MapActivity implements DialogInterface.OnClickListener, DialogInterface.OnCancelListener {
	private static final String TAG = "MapData";
	private static final int REFRESH_ID = Menu.FIRST;
	protected static final String OPERATOR = "operator";
	protected static final String CARRIER = "carrier";
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
	protected static int color_primary;
	protected static int color_secondary;
	protected static Drawable drawable_cell;
	protected static Drawable drawable_network;
	protected static String string_cancel;
	protected static String string_deleteCell;
	protected static String string_deleteNetwork;
	protected static String string_cellWarning;
	protected static String string_cid;
	protected static String string_linefeed;
	protected static String string_lac;
	protected static String string_range;
	protected static String string_colon;
	private Context mContext;
	private int mNetwork, mMCC = 0, mMNC = 0;
	protected int mPair = 0;
	private String mCarrier = "", mToken = "", mMsg = "";
	protected MapView mMView;
	private MapController mMController;
	private ProgressDialog mLoadingDialog;
	private Thread mThread;
	protected final Handler mHandler = new Handler();
	protected final Runnable mUpdtDialog = new Runnable() {
		public void run() {
			updtDialog();
		}
	};

	private static final String SAddInt = "\"%s\":%d";
	private static final String SAddString = "\"%s\":\"%s\"";
	private static final String SAddArray = "\"%s\":[%s]";

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.map);
		mContext = this;
		mMView = (MapView) findViewById(R.id.mapview);
		mMView.setBuiltInZoomControls(true);
		mMController = mMView.getController();
		mMController.setZoom(12);
		Bundle extras = getIntent().getExtras();
		if (extras != null) {
			mNetwork = extras.getInt(WapdroidProvider.TABLE_NETWORKS);
			mPair = extras.getInt(WapdroidProvider.TABLE_PAIRS);
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
		string_cancel = getString(android.R.string.cancel);
		string_deleteCell = String.format(getString(R.string.forget), getString(R.string.cell));
		string_deleteNetwork = String.format(getString(R.string.forget), getString(R.string.network));
		string_cellWarning = getString(R.string.cellwarning);
		string_cid = getString(R.string.label_CID);
		string_linefeed = getString(R.string.linefeed);
		string_lac = getString(R.string.label_LAC);
		string_range = getString(R.string.range);
		string_colon = getString(R.string.colon);
		if (!getPackageName().toLowerCase().contains(Wapdroid.PRO)) {
			AdView adView = new AdView(this, AdSize.BANNER, Wapdroid.GOOGLE_AD_ID);
			((LinearLayout) findViewById(R.id.ad)).addView(adView);
			adView.loadAd(new AdRequest());
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		mapData();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		boolean result = super.onCreateOptionsMenu(menu);
		menu.add(0, REFRESH_ID, 0, String.format(getString(R.string.refresh), getString(mNetwork == 0 ? R.string.network : R.string.cell))).setIcon(android.R.drawable.ic_menu_rotate);
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
		return String.format(SAddString, version, gmaps_version)
		+ "," + String.format(SAddString, host, gmaps)
		+ "," + String.format(SAddInt, home_mcc, mMCC)
		+ "," + String.format(SAddInt, home_mnc, mMNC)
		+ "," + String.format(SAddString, carrier, mCarrier);
	}

	public String getValue(String dictionary, String key) {
		int key_index = dictionary.indexOf(key), end;
		String value = "";
		if (key_index != Wapdroid.UNKNOWN_CID) {
			key_index += key.length() + 1;
			key_index = dictionary.indexOf(":", key_index) + 1;
			end = dictionary.indexOf(",", key_index);
			if (end == Wapdroid.UNKNOWN_CID) end = dictionary.indexOf("}", key_index);
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
		if (mToken != "") request += "," + String.format(SAddString, access_token, mToken);
		if (towers != "") request += "," + String.format(SAddArray, cell_towers, towers);
		if (bssid != "") request += "," + String.format(SAddArray, wifi_towers, "{" + String.format(SAddString, mac_address, bssid) + "}");
		return request + "}";
	}

	public void mapData() {
		mLoadingDialog = new ProgressDialog(this);
		mLoadingDialog.setTitle(R.string.loading);
		mLoadingDialog.setMessage((mPair == 0 ? Wapdroid.Ranges.NETWORK : Wapdroid.Ranges.CELL));
		mLoadingDialog.setCancelable(true);
		mLoadingDialog.setOnCancelListener(this);
		mLoadingDialog.setButton(ProgressDialog.BUTTON_NEGATIVE, getString(android.R.string.cancel), this);
		mLoadingDialog.show();
		mThread = new Thread() {
			public void run() {
				String ssid = "", bssid = "", towers = "";
				int ctr = 0;
				List<Overlay> mapOverlays = mMView.getOverlays();
				GeoPoint point = new GeoPoint(0, 0);
				Cursor pairs = MapData.this.getContentResolver().query(Wapdroid.Ranges.CONTENT_URI, new String[]{Wapdroid.Ranges._ID, Wapdroid.Ranges.SSID, Wapdroid.Ranges.BSSID, Wapdroid.Ranges.CID, Wapdroid.Ranges.LAC, Wapdroid.Ranges.RSSI_MIN, Wapdroid.Ranges.RSSI_MAX}, mPair == 0 ? Wapdroid.Ranges.NETWORK + "=" + mNetwork : Wapdroid.Ranges._ID + "=" + mPair, null, null);
				int ct = pairs.getCount();
				if (pairs.moveToFirst()) {
					WapdroidItemizedOverlay pinOverlays = new WapdroidItemizedOverlay((MapData) mContext, ct);
					while (!interrupted() && !pairs.isAfterLast()) {
						ctr++;
						int cid = pairs.getInt(pairs.getColumnIndex(Wapdroid.Ranges.CID)),
						lac = pairs.getInt(pairs.getColumnIndex(Wapdroid.Ranges.LAC)),
						rssi_min = pairs.getInt(pairs.getColumnIndex(Wapdroid.Ranges.RSSI_MIN)),
						rssi_max = pairs.getInt(pairs.getColumnIndex(Wapdroid.Ranges.RSSI_MAX)),
						rssi_avg = Math.round((rssi_min + rssi_max) / 2),
						rssi_range = Math.abs(rssi_min) - Math.abs(rssi_max);
						mMsg = string_cellWarning + Wapdroid.Ranges.CELL + " " + Integer.toString(ctr) + " of " + Integer.toString(ct);
						mHandler.post(mUpdtDialog);
						String tower = "{" + String.format(SAddInt, cell_id, cid) + "," + String.format(SAddInt, location_area_code, lac) + "," + String.format(SAddInt, mcc, mMCC) + "," + String.format(SAddInt, mnc, mMNC);
						if (rssi_avg != Wapdroid.UNKNOWN_RSSI) tower += "," + String.format(SAddInt, signal_strength, rssi_avg);
						tower += "}";
						if (ssid == "") ssid = pairs.getString(pairs.getColumnIndex(Wapdroid.Ranges.SSID));
						if (bssid == "") bssid = pairs.getString(pairs.getColumnIndex(Wapdroid.Ranges.BSSID));
						if (towers != "") towers += ",";
						towers += tower;
						point = getGeoPoint(bldRequest(tower, bssid));
						pinOverlays.addOverlay(new WapdroidOverlayItem(point, Wapdroid.Ranges.CELL,
								string_cid + Integer.toString(cid)
								+ string_linefeed + string_lac + Integer.toString(lac)
								+ string_linefeed + string_range + Integer.toString(rssi_min) + string_colon + Integer.toString(rssi_max),
								mNetwork, pairs.getInt(pairs.getColumnIndex(Wapdroid.Ranges._ID)), rssi_avg, rssi_range));
						pairs.moveToNext();
					}
					if (mPair == 0) {
						mMsg = Wapdroid.Ranges.NETWORK + ": " + ssid;
						mHandler.post(mUpdtDialog);
						point = getGeoPoint(bldRequest(towers, bssid));
						Location location = new Location("");
						location.setLatitude(point.getLatitudeE6()/1e6);
						location.setLongitude(point.getLongitudeE6()/1e6);
						pinOverlays.addOverlay(new WapdroidOverlayItem(point, Wapdroid.Ranges.NETWORK, ssid, mNetwork), drawable_network);
						pinOverlays.setDistances(location);
					}
					mapOverlays.add(pinOverlays);
					mMController.setCenter(point);
				}
				pairs.close();
				mLoadingDialog.dismiss();
				interrupt();
			}
		};
		mThread.start();
	}

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
