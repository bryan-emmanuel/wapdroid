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
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.ItemizedOverlay;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;
import com.google.android.maps.OverlayItem;
import com.google.android.maps.Projection;

public class MapData extends MapActivity {
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
	private static final String lac = "location_area_code";
	private static final String mcc = "mobile_country_code";
	private static final String mnc = "mobile_network_code";
	private static final String cell_towers = "cell_towers";
	private static final String latitude = "latitude";
	private static final String longitude = "longitude";
	private static final String wifi_towers = "wifi_towers";
	private static final String mac_address = "mac_address";
	private static final String signal_strength = "signal_strength";
	private WapdroidDbAdapter mDb;
	private Context mContext;
	private int mNetwork, mCell = 0, mMCC = 0, mMNC = 0, mCID;
	private String mCarrier = "", mToken = "", mMsg = "";
	private MapView mMView;
	private MapController mMController;
	private ProgressDialog mLoadingDialog;
	private Thread mThread;
	final Handler mHandler = new Handler();
	final Runnable mUpdtDialog = new Runnable() {
		public void run() {
			updtDialog();}};
	//@Override
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
			mNetwork = extras.getInt(WapdroidDbAdapter.PAIRS_NETWORK);
			mCell = extras.getInt(WapdroidDbAdapter.PAIRS_CELL);
			String operator = extras.getString(OPERATOR);
			if (operator.length() > 0) {
				mMCC = Integer.parseInt(operator.substring(0, 3));
				mMNC = Integer.parseInt(operator.substring(3));}
			mCarrier = extras.getString(CARRIER);}
		Log.v(TAG,"map mCell :"+mCell);
        mDb = new WapdroidDbAdapter(this);}
	
    @Override
    public void onPause() {
    	super.onPause();
    	mMView.getOverlays().clear();
   		mDb.close();}
    
	@Override
	protected void onResume() {
		super.onResume();
		mDb.open();
		mLoadingDialog = LoadingDialog.show(this, getString(R.string.loading), (mCell == 0 ? WapdroidDbAdapter.PAIRS_NETWORK : WapdroidDbAdapter.PAIRS_CELL));
		mLoadingDialog.setCancelable(true);
		mThread = new Thread() {
			public void run() {
				String ssid = "", bssid = "", towers = "", ct = "";
				int ctr = 0;
				List<Overlay> mapOverlays = mMView.getOverlays();
				WapdroidItemizedOverlay pinOverlays = new WapdroidItemizedOverlay(mContext.getResources().getDrawable(R.drawable.cell));
				GeoPoint point = new GeoPoint(0, 0);
				Cursor cells = mCell == 0 ? mDb.fetchNetworkData((int) mNetwork) : mDb.fetchCellData((int) mNetwork, (int) mCell);
		    	if (cells.getCount() > 0) {
		    		ct = Integer.toString(cells.getCount());
		    		Log.v(TAG, "cell count: " + ct);
		    		cells.moveToFirst();
		    		while (!interrupted() && !cells.isAfterLast()) {
		    			ctr++;
			    		mCID = cells.getInt(cells.getColumnIndex(WapdroidDbAdapter.CELLS_CID));
			    		mMsg = WapdroidDbAdapter.PAIRS_CELL + " " + Integer.toString(ctr) + " of " + ct;
			    		mHandler.post(mUpdtDialog);
			    		String tower = "{" + addInt(cell_id, mCID) + "," + addInt(lac, cells.getInt(cells.getColumnIndex(WapdroidDbAdapter.LOCATIONS_LAC))) + "," + addInt(mcc, mMCC) + "," + addInt(mnc, mMNC);
			    		int rssi_min = cells.getInt(cells.getColumnIndex(WapdroidDbAdapter.PAIRS_RSSI_MIN)),
			    			rssi_max = cells.getInt(cells.getColumnIndex(WapdroidDbAdapter.PAIRS_RSSI_MAX)),
			    			rssi_avg = Math.round((rssi_min + rssi_max)/2),
		    				rssi_range = getScaleSignal(rssi_min) - getScaleSignal(rssi_max);
			    		if (rssi_avg != WapdroidDbAdapter.UNKNOWN_RSSI) tower += "," + addInt(signal_strength, rssi_avg);
			    		tower += "}";
			    		if (ssid == "") ssid = cells.getString(cells.getColumnIndex(WapdroidDbAdapter.NETWORKS_SSID));
			    		if (bssid == "") bssid = cells.getString(cells.getColumnIndex(WapdroidDbAdapter.NETWORKS_BSSID));
			    		if (towers != "") towers += ",";
			    		towers += tower;
			    		point = getGeoPoint(bldRequest(tower, bssid));
						pinOverlays.addOverlay(new WapdroidOverlayItem(point, WapdroidDbAdapter.PAIRS_CELL, Integer.toString(mCID), rssi_avg, rssi_range));
			    		cells.moveToNext();}
		    		if (mCell == 0) {
		    			mMsg = WapdroidDbAdapter.PAIRS_NETWORK + ": " + ssid;
		    			mHandler.post(mUpdtDialog);
		    			point = getGeoPoint(bldRequest(towers, bssid));
		    			Location location = new Location("");
		    			location.setLatitude(point.getLatitudeE6()/1e6);
		    			location.setLongitude(point.getLongitudeE6()/1e6);
		    			Log.v(TAG,"location:"+Double.toString(location.getLatitude())+","+Double.toString(location.getLongitude()));
						pinOverlays.addOverlay(new WapdroidOverlayItem(point, WapdroidDbAdapter.PAIRS_NETWORK, ssid), mContext.getResources().getDrawable(R.drawable.network));
						pinOverlays.setDistances(location);}}
				cells.close();
				mapOverlays.add(pinOverlays);
			   	mMController.setCenter(point);
			   	mLoadingDialog.dismiss();
			   	interrupt();}};
		mThread.start();}

	@Override
	protected boolean isRouteDisplayed() {
		return false;}
	
	private int getScaleSignal(int signal) {
		return Math.round(20000 * (Math.abs(signal) - 51) / 62);}
	
	public String getRequestHeader() {
		return addString(version, gmaps_version)
		+ "," + addString(host, gmaps)
		+ "," + addInt(home_mcc, mMCC)
		+ "," + addInt(home_mnc, mMNC)
		+ "," + addString(carrier, mCarrier);}
	
	public String addInt(String key, int i) {
		return "\"" + key + "\":" + Integer.toString(i);}
	
	public String addString(String key, String s) {
		return "\"" + key + "\":\"" + s + "\"";}
	
	public String addArray(String key, String a) {
		return "\"" + key + "\":[" + a + "]";}
	
	public String getValue(String dictionary, String key) {
		int key_index = dictionary.indexOf(key), end;
		String value = "";
		if (key_index != WapdroidDbAdapter.UNKNOWN_CID) {
			key_index += key.length() + 1;
			key_index = dictionary.indexOf(":", key_index) + 1;
			end = dictionary.indexOf(",", key_index);
			if (end == WapdroidDbAdapter.UNKNOWN_CID) end = dictionary.indexOf("}", key_index);
			value = dictionary.substring(key_index, end);}
		return value;}
	
	public int parseCoordinate(String source, String key) {
		String value = getValue(source, key);
		int parsed = 0;
		if (value != "") parsed = (int) (Double.parseDouble(value) * 1E6);
		return parsed;}
	
	public GeoPoint getGeoPoint(String query) {
		String response = "";
		DefaultHttpClient httpClient = new DefaultHttpClient();
		ResponseHandler <String> responseHandler = new BasicResponseHandler();
		HttpPost postMethod = new HttpPost("https://www.google.com/loc/json");
		try {
			postMethod.setEntity(new StringEntity(query));}
		catch (UnsupportedEncodingException e) {
			Log.v(TAG, "post:setEntity error: "+e);}
		postMethod.setHeader("Accept", "application/json");
		postMethod.setHeader("Content-type", "application/json");
		try {
			response = httpClient.execute(postMethod, responseHandler);}
		catch (ClientProtocolException e) {
			Log.v(TAG, "post:ClientProtocolException error: "+e);}
		catch (IOException e) {
			Log.v(TAG, "post:IOException error: "+e);}
		if (mToken == "") {
			mToken = getValue(response, access_token);
			if (mToken.length() > 0) {
				mToken = mToken.substring(1);
				mToken = mToken.substring(0, mToken.length()-1);}}
		int lat = parseCoordinate(response, latitude);
		int lon = parseCoordinate(response, longitude);
		return new GeoPoint(lat, lon);}
	
	private String bldRequest(String towers, String bssid) {
		String request = "{" + getRequestHeader();
		if (mToken != "") request += "," + addString(access_token, mToken);
		if (towers != "") request += "," + addArray(cell_towers, towers);
		if (bssid != "") request += "," + addArray(wifi_towers, "{" + addString(mac_address, bssid) + "}");
		return request + "}";}
	
	private void updtDialog() {
		mLoadingDialog.setMessage(mMsg);}
	
	class LoadingDialog extends ProgressDialog {
		public LoadingDialog(Context context) {
			super(context);}
		@Override
		public void onBackPressed() {
			super.onBackPressed();
			Log.v(TAG,"backpressed: interrupt thread");
			mThread.interrupt();
			return;}}
	
	class WapdroidOverlayItem extends OverlayItem {
		protected GeoPoint mPoint;
		protected String mSnippet;
		protected String mTitle;
		protected Drawable mMarker;
		protected int mRssi_avg = 0;
		protected int mRssi_range = 0;
		protected int mRadius = 0;
		protected long mStroke = 0;
		public WapdroidOverlayItem(GeoPoint point, String title, String snippet) {
			super(point, title, snippet);}
		public WapdroidOverlayItem(GeoPoint point, String title, String snippet, int rssi_avg, int rssi_range) {
			super(point, title, snippet);
			mRssi_avg = rssi_avg;
			mRssi_range = rssi_range;}
		public int getRssiAvg() {
			return mRssi_avg;}
		public int getRssiRange() {
			return mRssi_range;}
		public void setRadius(int radius) {
			mRadius = radius;}
		public int getRadius() {
			return mRadius;}
		public void setStroke(long stroke) {
			mStroke = stroke;}
		public long getStroke() {
			return mStroke;}}
	
	class WapdroidItemizedOverlay extends ItemizedOverlay<WapdroidOverlayItem> {
		private ArrayList<WapdroidOverlayItem> mOverlays = new ArrayList<WapdroidOverlayItem>();
		public WapdroidItemizedOverlay(Drawable defaultMarker) {
			super(boundCenterBottom(defaultMarker));}
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
					paint.setColor(getResources().getColor(R.color.text_primary));
					paint.setAlpha(16);}
				else {
					long stroke = item.getStroke();
					radius = item.getRadius();
					Log.v(TAG,"distance:"+Integer.toString(radius));
					paint.setColor(getResources().getColor(R.color.text_secondary));
					paint.setAlpha(8);
					if (stroke == 0) paint.setStyle(Paint.Style.FILL);
					else {
						paint.setStyle(Paint.Style.STROKE);
						Log.v(TAG,"stroke:"+Long.toString(stroke));
						paint.setStrokeWidth(projection.metersToEquatorPixels(Math.round(stroke/mercator)));}}
				Log.v(TAG,"draw:"+Integer.toString(radius));
				canvas.drawCircle(pt.x, pt.y, projection.metersToEquatorPixels(Math.round(radius/mercator)), paint);}
			super.draw(canvas, mapView, shadow);}
		@Override
		protected WapdroidOverlayItem createItem(int i) {
			return mOverlays.get(i);}
		@Override
		public int size() {
			return mOverlays.size();}
		public void addOverlay(WapdroidOverlayItem overlay) {
			mOverlays.add(overlay);
			populate();}
		public void addOverlay(WapdroidOverlayItem overlay, Drawable marker) {
			overlay.setMarker(boundCenterBottom(marker));
			addOverlay(overlay);}
		public void setDistances(Location location) {
			for (WapdroidOverlayItem item : mOverlays) {
				if (item.getTitle() != WapdroidDbAdapter.PAIRS_NETWORK) {
					GeoPoint gpt = item.getPoint();
					Location cell = new Location("");
					cell.setLatitude(gpt.getLatitudeE6()/1e6);
					cell.setLongitude(gpt.getLongitudeE6()/1e6);
	    			int radius = Math.round(location.distanceTo(cell));
	    			double scale = radius / (item.getRssiAvg() - 51);
	    			item.setRadius(radius);
	    			item.setStroke(Math.round(item.getRssiRange() * scale));}}}
		@Override
		protected boolean onTap(int i) {
			OverlayItem item = mOverlays.get(i);
			AlertDialog.Builder dialog = new AlertDialog.Builder(mContext);
			dialog.setTitle(item.getTitle());
			dialog.setMessage(item.getSnippet());
			dialog.show();
			return true;}}}
