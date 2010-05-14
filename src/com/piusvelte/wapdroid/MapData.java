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

import android.app.ProgressDialog;
import android.database.Cursor;
import android.os.Bundle;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;
import com.google.android.maps.OverlayItem;

public class MapData extends MapActivity {
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
	private static final String lac = "location_area_code";
	private static final String mcc = "mobile_country_code";
	private static final String mnc = "mobile_network_code";
	private static final String cell_towers = "cell_towers";
	private static final String latitude = "latitude";
	private static final String longitude = "longitude";
	private WapdroidDbAdapter mDb;
	private int mNetwork, mCell, mMCC, mMNC;
	private String mCarrier = "", mToken = "";
	private MapView mMapView;
	private ProgressDialog mLoadingDialog;
	private Thread mLoadData;
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
			String operator = extras.getString(OPERATOR);
			mMCC = Integer.parseInt(operator.substring(0, 3));
			mMNC = Integer.parseInt(operator.substring(3));
			mCarrier = extras.getString(CARRIER);}
        mDb = new WapdroidDbAdapter(this);
		mLoadData = new Thread() {
			public void run() {
				mDb.open();
				List<Overlay> mapOverlays = mMapView.getOverlays();
				String network_request = "{";
				if (mCell == 0) {
					network_request += getRequestHeader();}
				String ssid = "";
    			String towers = "";
				Cursor c = mCell == 0 ? mDb.fetchNetworkData((int) mNetwork) : mDb.fetchCellData((int) mNetwork, (int) mCell);
		    	if (c.getCount() > 0) {
		    		c.moveToFirst();
		    		while (!c.isAfterLast()) {
		    			int cid = c.getInt(c.getColumnIndex(WapdroidDbAdapter.CELLS_CID));
		    			mLoadingDialog.setMessage(WapdroidDbAdapter.PAIRS_CELL + ": " + Integer.toString(cid));
		    			// get the wifi msg
		    			if ((mCell == 0) && (ssid == "")) ssid = c.getString(c.getColumnIndex(WapdroidDbAdapter.NETWORKS_SSID));
	    				// add tower to query, but also get location for each tower to add pins
		    			String cell_request = "{" + getRequestHeader();
		    			if (mToken != "") cell_request += "," + addString(access_token, mToken);
						String tower = "{" + addInt(cell_id, cid);
	    				tower += "," + addInt(lac, c.getInt(c.getColumnIndex(WapdroidDbAdapter.LOCATIONS_LAC)));
	    				tower += "," + addInt(mcc, mMCC);
	    				tower += "," + addInt(mnc, mMNC) + "}";
	    				cell_request += "," + addArray(cell_towers, tower) + "}";
	    				// add tower overlay
	    	    		CellOverlay overlay = new CellOverlay(getResources().getDrawable(R.drawable.cell));
	    	    		OverlayItem overlayitem = new OverlayItem(getGeoPoint(post(cell_request)), WapdroidDbAdapter.PAIRS_CELL, c.getString(c.getColumnIndex(WapdroidDbAdapter.CELLS_CID)));
	    	    		overlay.addOverlay(overlayitem);
	    	    		mapOverlays.add(overlay);
	    	    		if (towers != "") towers += ",";
	    	    		towers += tower;
		    			c.moveToNext();}}
		    	c.close();
				mDb.close();
		    	// the cells are added in above, this is only for the network
		    	if (mCell == 0) {
	    			mLoadingDialog.setMessage(WapdroidDbAdapter.PAIRS_NETWORK + ": " + ssid);
		    		network_request += "," + addString(access_token, mToken);
		    		network_request += "," + addArray(cell_towers, towers) + "}";
			    	CellOverlay overlay = new CellOverlay(getResources().getDrawable(R.drawable.wifi));
			    	OverlayItem overlayitem = new OverlayItem(getGeoPoint(post(network_request)), WapdroidDbAdapter.PAIRS_NETWORK, ssid);
			    	overlay.addOverlay(overlayitem);
			    	mapOverlays.add(overlay);}
		    	mLoadingDialog.dismiss();}};
			mLoadingDialog = ProgressDialog.show(this, getString(R.string.loading), (mCell == 0 ? WapdroidDbAdapter.PAIRS_NETWORK : WapdroidDbAdapter.PAIRS_CELL));
			mLoadingDialog.setCancelable(true);
		    mLoadData.run();}

	@Override
	protected boolean isRouteDisplayed() {
		return false;}
	
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
		if (key_index != -1) {
			key_index += key.length() + 1;
			key_index = dictionary.indexOf(":", key_index) + 1;
			end = dictionary.indexOf(",", key_index);
			if (end == -1) end = dictionary.indexOf("}", key_index);
			value = dictionary.substring(key_index, end);}
		return value;}
	
	public int parseCoordinate(String source, String key) {
		String value = getValue(source, key);
		int parsed = 0;
		if (value != "") parsed = (int) (Double.parseDouble(value) * 1E6);
		return parsed;}
	
	public GeoPoint getGeoPoint(String response) {
		if (mToken == "") {
			mToken = getValue(response, access_token);
			mToken = mToken.substring(1);
			mToken = mToken.substring(0, mToken.length() -1);}
		int lat = parseCoordinate(response, latitude);
		int lon = parseCoordinate(response, longitude);
		return new GeoPoint(lat,  lon);}
	
	public String post(String query) {
		String response = "";
		DefaultHttpClient httpClient = new DefaultHttpClient();
		ResponseHandler <String> responseHandler = new BasicResponseHandler();
		HttpPost postMethod = new HttpPost("https://www.google.com/loc/json");
		try {
			postMethod.setEntity(new StringEntity(query));}
		catch (UnsupportedEncodingException e) {}
		postMethod.setHeader("Accept", "application/json");
		postMethod.setHeader("Content-type", "application/json");
		try {
			response = httpClient.execute(postMethod, responseHandler);}
		catch (ClientProtocolException e) {}
		catch (IOException e) {}
		return response;}}
