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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
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
import com.google.android.maps.ItemizedOverlay;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;
import com.google.android.maps.OverlayItem;
import com.google.android.maps.Projection;

public class MapData extends MapActivity implements AdListener {
	private static final int REFRESH_ID = Menu.FIRST;
	public static final String OPERATOR = "operator";
	public static final String CARRIER = "carrier";
	private static final String TAG = "Wapdroid";
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
	private static final int mNetwork_alpha = 32;
	private WapdroidDbAdapter mDbHelper;
	private Context mContext;
	private int mNetwork, mPair = 0, mMCC = 0, mMNC = 0, mCell_alpha = 32;
	private String mCarrier = "", mToken = "", mMsg = "";
	private MapView mMView;
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
		mDbHelper = new WapdroidDbAdapter(this);
		mMView = (MapView) findViewById(R.id.mapview);
		mMView.setBuiltInZoomControls(true);
		mMController = mMView.getController();
		mMController.setZoom(12);
		Bundle extras = getIntent().getExtras();
		if (extras != null) {
			mNetwork = extras.getInt(WapdroidDbAdapter.TABLE_NETWORKS);
			mPair = extras.getInt(WapdroidDbAdapter.TABLE_PAIRS);
			String operator = extras.getString(OPERATOR);
			if (operator.length() > 0) {
				mMCC = Integer.parseInt(operator.substring(0, 3));
				mMNC = Integer.parseInt(operator.substring(3));
			}
			mCarrier = extras.getString(CARRIER);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		mDbHelper.open();
		mapData();
	}


	@Override
	public void onPause() {
		super.onPause();
		mDbHelper.close();
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
		if (key_index != WapdroidDbAdapter.UNKNOWN_CID) {
			key_index += key.length() + 1;
			key_index = dictionary.indexOf(":", key_index) + 1;
			end = dictionary.indexOf(",", key_index);
			if (end == WapdroidDbAdapter.UNKNOWN_CID) end = dictionary.indexOf("}", key_index);
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
		}
		catch (UnsupportedEncodingException e) {
			Log.v(TAG, "post:setEntity error: "+e);
		}
		postMethod.setHeader("Accept", "application/json");
		postMethod.setHeader("Content-type", "application/json");
		try {
			response = httpClient.execute(postMethod, responseHandler);
		}
		catch (ClientProtocolException e) {
			Log.v(TAG, "post:ClientProtocolException error: "+e);
		}
		catch (IOException e) {
			Log.v(TAG, "post:IOException error: "+e);
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
		Log.v(TAG,"lat:"+Integer.toString(lat));
		Log.v(TAG,"lon:"+Integer.toString(lon));
		return new GeoPoint(lat, lon);
	}

	private String bldRequest(String towers, String bssid) {
		String request = "{" + getRequestHeader();
		if (mToken != "") request += "," + addString(access_token, mToken);
		if (towers != "") request += "," + addArray(cell_towers, towers);
		if (bssid != "") request += "," + addArray(wifi_towers, "{" + addString(mac_address, bssid) + "}");
		return request + "}";
	}

	private void mapData() {
		mLoadingDialog = new ProgressDialog(this);
		mLoadingDialog.setTitle(R.string.loading);
		mLoadingDialog.setMessage((mPair == 0 ? WapdroidDbAdapter.PAIRS_NETWORK : WapdroidDbAdapter.PAIRS_CELL));
		mLoadingDialog.setCancelable(true);
		mLoadingDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
			@Override
			public void onCancel(DialogInterface dialog) {
				mThread.interrupt();
			}
		});
		mLoadingDialog.setButton(ProgressDialog.BUTTON_NEGATIVE, getString(R.string.cancel), new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				mThread.interrupt();
			}
		});
		mLoadingDialog.show();
		mThread = new Thread() {
			public void run() {
				String ssid = "", bssid = "", towers = "";
				int ctr = 0;
				List<Overlay> mapOverlays = mMView.getOverlays();
				WapdroidItemizedOverlay pinOverlays = new WapdroidItemizedOverlay(mContext.getResources().getDrawable(R.drawable.cell));
				GeoPoint point = new GeoPoint(0, 0);
				Cursor pairs = mPair == 0 ? mDbHelper.fetchNetworkData(mNetwork) : mDbHelper.fetchPairData(mPair);
				int ct = pairs.getCount();
				if (ct > 0) {
					mCell_alpha = Math.round(mNetwork_alpha / ct);
					Log.v(TAG, "pair count: " + Integer.toString(ct));
					pairs.moveToFirst();
					while (!interrupted() && !pairs.isAfterLast()) {
						ctr++;
						int cid = pairs.getInt(pairs.getColumnIndex(WapdroidDbAdapter.CELLS_CID)),
						lac = pairs.getInt(pairs.getColumnIndex(WapdroidDbAdapter.LOCATIONS_LAC)),
						rssi_min = pairs.getInt(pairs.getColumnIndex(WapdroidDbAdapter.PAIRS_RSSI_MIN)),
						rssi_max = pairs.getInt(pairs.getColumnIndex(WapdroidDbAdapter.PAIRS_RSSI_MAX)),
						rssi_avg = Math.round((rssi_min + rssi_max) / 2),
						rssi_range = Math.abs(rssi_min) - Math.abs(rssi_max);
						mMsg = WapdroidDbAdapter.PAIRS_CELL + " " + Integer.toString(ctr) + " of " + Integer.toString(ct);
						mHandler.post(mUpdtDialog);
						String tower = "{" + addInt(cell_id, cid) + "," + addInt(location_area_code, lac) + "," + addInt(mcc, mMCC) + "," + addInt(mnc, mMNC);
						if (rssi_avg != WapdroidDbAdapter.UNKNOWN_RSSI) tower += "," + addInt(signal_strength, rssi_avg);
						tower += "}";
						if (ssid == "") ssid = pairs.getString(pairs.getColumnIndex(WapdroidDbAdapter.NETWORKS_SSID));
						if (bssid == "") bssid = pairs.getString(pairs.getColumnIndex(WapdroidDbAdapter.NETWORKS_BSSID));
						if (towers != "") towers += ",";
						towers += tower;
						Log.v(TAG,"cid:"+Integer.toString(cid));
						Log.v(TAG,"lac:"+Integer.toString(pairs.getInt(pairs.getColumnIndex(WapdroidDbAdapter.LOCATIONS_LAC))));
						point = getGeoPoint(bldRequest(tower, bssid));
						pinOverlays.addOverlay(new WapdroidOverlayItem(point, WapdroidDbAdapter.PAIRS_CELL,
								mContext.getResources().getString(R.string.label_CID) + Integer.toString(cid)
								+ mContext.getResources().getString(R.string.linefeed) + mContext.getResources().getString(R.string.label_LAC) + Integer.toString(lac)
								+ mContext.getResources().getString(R.string.linefeed) + mContext.getResources().getString(R.string.range) + Integer.toString(rssi_min) + mContext.getString(R.string.colon) + Integer.toString(rssi_max),
								mNetwork, pairs.getInt(pairs.getColumnIndex(WapdroidDbAdapter.TABLE_ID)), rssi_avg, rssi_range));
						pairs.moveToNext();
					}
					if (mPair == 0) {
						mMsg = WapdroidDbAdapter.PAIRS_NETWORK + ": " + ssid;
						mHandler.post(mUpdtDialog);
						point = getGeoPoint(bldRequest(towers, bssid));
						Location location = new Location("");
						location.setLatitude(point.getLatitudeE6()/1e6);
						location.setLongitude(point.getLongitudeE6()/1e6);
						Log.v(TAG,"location:"+Double.toString(location.getLatitude())+","+Double.toString(location.getLongitude()));
						pinOverlays.addOverlay(new WapdroidOverlayItem(point, WapdroidDbAdapter.PAIRS_NETWORK, ssid, mNetwork), mContext.getResources().getDrawable(R.drawable.network));
						pinOverlays.setDistances(location);
					}
				}
				pairs.close();
				mapOverlays.add(pinOverlays);
				mMController.setCenter(point);
				mLoadingDialog.dismiss();
				interrupt();
			}
		};
		mThread.start();
	}

	class WapdroidOverlayItem extends OverlayItem {
		protected GeoPoint mPoint;
		protected String mSnippet;
		protected String mTitle;
		protected Drawable mMarker;
		protected int mNetwork = 0;
		protected int mPair = 0;
		protected int mRssi_avg = 0;
		protected int mRssi_range = 0;
		protected int mRadius = 0;
		protected long mStroke = 0;
		public WapdroidOverlayItem(GeoPoint point, String title, String snippet, int network) {
			super(point, title, snippet);
			mNetwork = network;
		}
		public WapdroidOverlayItem(GeoPoint point, String title, String snippet, int network, int pair, int rssi_avg, int rssi_range) {
			super(point, title, snippet);
			mRssi_avg = rssi_avg;
			mRssi_range = rssi_range;
			mNetwork = network;
			mPair = pair;
		}
		public int getNetwork() {
			return mNetwork;
		}
		public int getPair() {
			return mPair;
		}
		public int getRssiAvg() {
			return mRssi_avg;
		}
		public int getRssiRange() {
			return mRssi_range;
		}
		public void setRadius(int radius) {
			mRadius = radius;
		}
		public int getRadius() {
			return mRadius;
		}
		public void setStroke(long stroke) {
			mStroke = stroke;
		}
		public long getStroke() {
			return mStroke;
		}
	}

	class WapdroidItemizedOverlay extends ItemizedOverlay<WapdroidOverlayItem> {
		private ArrayList<WapdroidOverlayItem> mOverlays = new ArrayList<WapdroidOverlayItem>();
		public WapdroidItemizedOverlay(Drawable defaultMarker) {
			super(boundCenterBottom(defaultMarker));
		}
		@Override
		public void draw(Canvas canvas, MapView mapView, boolean shadow) {
			for (WapdroidOverlayItem item : mOverlays) {
				int radius = 0;
				Paint paint = new Paint();
				GeoPoint gpt = item.getPoint();
				Point pt = new Point();
				Projection projection = mapView.getProjection();
				projection.toPixels(gpt, pt);
				double mercator = Math.cos(Math.toRadians(gpt.getLatitudeE6()/1E6));
				if (item.getTitle() == WapdroidDbAdapter.PAIRS_NETWORK) {
					radius = 70;
					paint.setColor(getResources().getColor(R.color.primary));
					paint.setAlpha(mNetwork_alpha);
				}
				else {
					long stroke = item.getStroke();
					radius = item.getRadius();
					paint.setColor(getResources().getColor(R.color.secondary));
					if (stroke == 0) {
						paint.setAlpha(mCell_alpha);
						paint.setStyle(Paint.Style.FILL);
					}
					else {
						paint.setAlpha(mCell_alpha);
						paint.setStyle(Paint.Style.STROKE);
						paint.setStrokeWidth(projection.metersToEquatorPixels(Math.round(stroke/mercator)));
					}
				}
				canvas.drawCircle(pt.x, pt.y, projection.metersToEquatorPixels(Math.round(radius/mercator)), paint);
			}
			super.draw(canvas, mapView, shadow);
		}

		@Override
		protected WapdroidOverlayItem createItem(int i) {
			return mOverlays.get(i);
		}

		@Override
		public int size() {
			return mOverlays.size();
		}

		public void addOverlay(WapdroidOverlayItem overlay) {
			mOverlays.add(overlay);
			populate();
		}

		public void addOverlay(WapdroidOverlayItem overlay, Drawable marker) {
			overlay.setMarker(boundCenterBottom(marker));
			addOverlay(overlay);
		}

		public void setDistances(Location location) {
			for (WapdroidOverlayItem item : mOverlays) {
				if (item.getTitle() != WapdroidDbAdapter.PAIRS_NETWORK) {
					GeoPoint gpt = item.getPoint();
					Location cell = new Location("");
					cell.setLatitude(gpt.getLatitudeE6()/1e6);
					cell.setLongitude(gpt.getLongitudeE6()/1e6);
					int radius = Math.round(location.distanceTo(cell));
					double scale = radius / (Math.abs(item.getRssiAvg()) - 51);
					item.setRadius(radius);
					Log.v(TAG,"lat:"+Double.toString(gpt.getLatitudeE6()/1e6));
					Log.v(TAG,"lat:"+Double.toString(gpt.getLongitudeE6()/1e6));
					Log.v(TAG,"rad:"+Integer.toString(radius));
					Log.v(TAG,"scl:"+Double.toString(scale));
					Log.v(TAG,"stk:"+Long.toString(Math.round(item.getRssiRange() * scale)));
					item.setStroke(Math.round(item.getRssiRange() * scale));
				}
			}
		}
		@Override
		protected boolean onTap(int i) {
			Log.v(TAG,"onTap("+Integer.toString(i)+")");
			Log.v(TAG,"mOverlays.size()"+Integer.toString(mOverlays.size()));
			final int item = i;
			WapdroidOverlayItem overlay = mOverlays.get(item);
			final int network = overlay.getNetwork();
			final int pair = overlay.getPair();
			AlertDialog.Builder dialog = new AlertDialog.Builder(mContext);
			dialog.setIcon(mContext.getResources().getDrawable(pair == 0 ? R.drawable.network : R.drawable.cell));
			dialog.setTitle(overlay.getTitle());
			dialog.setMessage(overlay.getSnippet());
			dialog.setPositiveButton(mContext.getResources().getString(pair == 0 ? R.string.menu_deleteNetwork : R.string.menu_deleteCell), new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					Log.v(TAG,"onClick("+Integer.toString(which)+")");
					if (pair == 0) {
						Log.v(TAG,"deleteNetwork:"+Integer.toString(network));
						//mDbHelper.deleteNetwork(network);
						finish();
					}
					else {
						Log.v(TAG,"deletePair:"+Integer.toString(network)+","+Integer.toString(pair));
						//mDbHelper.deletePair(network, pair);
						Log.v(TAG,"mOverlays.size()"+Integer.toString(mOverlays.size()));
						Log.v(TAG,"mOverlays.remove("+Integer.toString(item)+")");
						mOverlays.remove(item);
						mMView.invalidate();
						dialog.cancel();
					}
				}
			});
			dialog.setNegativeButton(mContext.getResources().getString(R.string.cancel), new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.cancel();
				}
			});
			dialog.show();
			return true;
		}
	}

	@Override
	public void onFailedToReceiveAd(AdView arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onFailedToReceiveRefreshedAd(AdView arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onReceiveAd(AdView arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onReceiveRefreshedAd(AdView arg0) {
		// TODO Auto-generated method stub
		
	}
}
