package com.piusvelte.wapdroid;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONException;
import org.json.JSONObject;

import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;
import com.google.android.maps.OverlayItem;

public class MapData extends MapActivity {
	public static final String OPERATOR = "operator";
	public static final String CARRIER = "carrier";
	private static final String TAG = "MapData";
	private WapdroidDbAdapter mDb;
	private int mNetwork, mCell;
	private String mOperator, mCarrier;
	private MapView mMapView;
	//@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.map);
		mMapView = (MapView) findViewById(R.id.mapview);
		mMapView.setBuiltInZoomControls(true);
		Bundle extras = getIntent().getExtras();
		if (extras != null) {
			mNetwork = extras.getInt(WapdroidDbAdapter.PAIRS_NETWORK);
			mCell = extras.getInt(WapdroidDbAdapter.PAIRS_CELL);
			mOperator = extras.getString(OPERATOR);
			mCarrier = extras.getString(CARRIER);}}

	@Override
	protected boolean isRouteDisplayed() {
		return false;}

    @Override
    public void onPause() {
    	super.onPause();
   		mDb.close();}
    
	@Override
	protected void onResume() {
		super.onResume();
		mDb.open();
		List<Overlay> mapOverlays = mMapView.getOverlays();
		JSONObject network_request = new JSONObject();
		if (mCell == 0) {
			try {
				network_request.put("version", "1.1.0");
				network_request.put("host", "maps.google.com");
				Log.v(TAG, "home_mobile_country_code:" + mOperator.substring(0, 3));
				network_request.put("home_mobile_country_code", mOperator.substring(0, 3));
				Log.v(TAG, "home_mobile_country_code:" + mOperator.substring(0, 3));
				network_request.put("home_mobile_network_code", mOperator.substring(3));
				Log.v(TAG, "home_mobile_network_code:" + mOperator.substring(3));
				network_request.put("carrier", mCarrier);
				Log.v(TAG, "carrier:" + mCarrier);}
			catch (JSONException e) {
				Log.e(TAG, "error building json query");}}
		String msg = "";
		Cursor c = mCell == 0 ? mDb.fetchNetworkData((int) mNetwork) : mDb.fetchCellData((int) mNetwork, (int) mCell);
    	if (c.getCount() > 0) {
    		c.moveToFirst();
    		while (!c.isAfterLast()) {
    			// get the wifi msg
    			if ((mCell == 0) && (msg == "")) msg = c.getString(c.getColumnIndex(WapdroidDbAdapter.NETWORKS_SSID));
    			JSONObject tower = new JSONObject();
    			try {
    				// add tower to query, but also get location for each tower to add pins
	    			JSONObject cell_request = new JSONObject();
					cell_request.put("version", "1.1.0");
					cell_request.put("host", "maps.google.com");
					cell_request.put("home_mobile_country_code", mOperator.substring(0, 3));
					cell_request.put("home_mobile_network_code", mOperator.substring(3));
					cell_request.put("carrier", mCarrier);
    				tower.put("cell_id", c.getInt(c.getColumnIndex(WapdroidDbAdapter.CELLS_CID)));
    				Log.v(TAG, "cell_id:" + c.getInt(c.getColumnIndex(WapdroidDbAdapter.CELLS_CID)));
    				tower.put("location_area_code", c.getInt(c.getColumnIndex(WapdroidDbAdapter.LOCATIONS_LAC)));
    				Log.v(TAG, "location_area_code:" + c.getInt(c.getColumnIndex(WapdroidDbAdapter.LOCATIONS_LAC)));
    				tower.put("mobile_country_code", mOperator.substring(0, 3));
    				Log.v(TAG, "mobile_country_code:" + mOperator.substring(0, 3));
    				tower.put("mobile_network_code", mOperator.substring(3));
    				Log.v(TAG, "mobile_network_code:" + mOperator.substring(3));
    				cell_request.accumulate("cell_towers", tower);
    				// add tower overlay
    	    		CellOverlay overlay = new CellOverlay(this.getResources().getDrawable(R.drawable.cell));
    	    		OverlayItem overlayitem = new OverlayItem(getGeoPoint(post(cell_request)), WapdroidDbAdapter.PAIRS_CELL, c.getString(c.getColumnIndex(WapdroidDbAdapter.CELLS_CID)));
    	    		overlay.addOverlay(overlayitem);
    	    		mapOverlays.add(overlay);    				
    				network_request.accumulate("cell_towers", tower);}
    			catch (JSONException e) {
    				Log.e(TAG, "error building json tower");}
    			c.moveToNext();}}
    		c.close();
    		// the cells are added in above, this is only for the network
    		if (mCell == 0) {
	    		CellOverlay overlay = new CellOverlay(this.getResources().getDrawable(R.drawable.wifi));
	    		OverlayItem overlayitem = new OverlayItem(getGeoPoint(post(network_request)), WapdroidDbAdapter.PAIRS_NETWORK, msg);
	    		overlay.addOverlay(overlayitem);
	    		mapOverlays.add(overlay);}}
	
	public GeoPoint getGeoPoint(String response) {
		Log.v(TAG, "response:" + response);
		int lat = 0, lon = 0;
		try {
			JSONObject results = new JSONObject(response);
			JSONObject location = results.getJSONObject("location");
			lat = (int) (location.getDouble("latitude") * 1E6);
			lon = (int) (location.getDouble("longitude") * 1E6);}
		catch (JSONException e1) {
			e1.printStackTrace();}
		Log.v(TAG, "geo:" + lat + "," + lon);
		return new GeoPoint(lat,  lon);}
	
	public String post(JSONObject query) {
		String response = "";
		DefaultHttpClient httpClient = new DefaultHttpClient();
		ResponseHandler <String> responseHandler = new BasicResponseHandler();
		HttpPost postMethod = new HttpPost("https://www.google.com/loc/json");
		try {
			postMethod.setEntity(new StringEntity(query.toString()));}
		catch (UnsupportedEncodingException e) {}
		postMethod.setHeader("Accept", "application/json");
		postMethod.setHeader("Content-type", "application/json");
		try {
			response = httpClient.execute(postMethod, responseHandler);}
		catch (ClientProtocolException e) {}
		catch (IOException e) {}
		return response;}}
