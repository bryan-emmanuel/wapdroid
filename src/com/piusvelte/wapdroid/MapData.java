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
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;
import com.google.android.maps.OverlayItem;

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
	private WapdroidDbAdapter mDb;
	private int mNetwork, mCell, mMCC, mMNC, mCID, mPin;
	private String mCarrier = "", mToken = "", mResponse = "", mTitle = "", mSnippet = "", mMsg = "";
	private MapView mMView;
	private MapController mMController;
	private List<Overlay> mMOverlays;
	private ProgressDialog mLoadingDialog;
	private GeoPoint mPoint = new GeoPoint(0, 0);
	private Thread mThread;
	final Handler mHandler = new Handler();
	final Runnable mUpdtDialog = new Runnable() {
		public void run() {
			updtDialog();}};
	final Runnable mDropPin = new Runnable() {
		public void run() {
			dropPin();}};
	//@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.map);
		mMView = (MapView) findViewById(R.id.mapview);
		mMView.setBuiltInZoomControls(true);
		mMController = mMView.getController();
		mMOverlays = mMView.getOverlays();
		Bundle extras = getIntent().getExtras();
		if (extras != null) {
			mNetwork = extras.getInt(WapdroidDbAdapter.PAIRS_NETWORK);
			mCell = extras.getInt(WapdroidDbAdapter.PAIRS_CELL);
			String operator = extras.getString(OPERATOR);
			mMCC = Integer.parseInt(operator.substring(0, 3));
			mMNC = Integer.parseInt(operator.substring(3));
			mCarrier = extras.getString(CARRIER);}
        mDb = new WapdroidDbAdapter(this);}
	
    @Override
    public void onPause() {
    	super.onPause();
   		mDb.close();}
    
	@Override
	protected void onResume() {
		super.onResume();
		mDb.open();
		mLoadingDialog = LoadingDialog.show(this, getString(R.string.loading), (mCell == 0 ? WapdroidDbAdapter.PAIRS_NETWORK : WapdroidDbAdapter.PAIRS_CELL));
		mLoadingDialog.setCancelable(true);
		mThread = new Thread() {
			public void run() {
				String ssid = "", towers = "";
				int ctr = 0;
				Cursor cells = mCell == 0 ? mDb.fetchNetworkData((int) mNetwork) : mDb.fetchCellData((int) mNetwork, (int) mCell);
		    	if (cells.getCount() > 0) {
		    		mPin = R.drawable.cell;
		    		String ct = Integer.toString(cells.getCount());
		    		Log.v(TAG, "cell count: " + ct);
		    		cells.moveToFirst();
		    		while (!cells.isAfterLast()) {
		    			ctr++;
			    		mCID = cells.getInt(cells.getColumnIndex(WapdroidDbAdapter.CELLS_CID));
			    		mMsg = WapdroidDbAdapter.PAIRS_CELL + ": " + Integer.toString(mCID) + " (" + Integer.toString(ctr) + "/" + ct + ")";
			    		mHandler.post(mUpdtDialog);
			    		String tower = "{" + addInt(cell_id, mCID);
			    		tower += "," + addInt(lac, cells.getInt(cells.getColumnIndex(WapdroidDbAdapter.LOCATIONS_LAC)));
			    		tower += "," + addInt(mcc, mMCC);
			    		tower += "," + addInt(mnc, mMNC) + "}";
			    		if (ssid == "") ssid = cells.getString(cells.getColumnIndex(WapdroidDbAdapter.NETWORKS_SSID));
			    		if (towers != "") towers += ",";
			    		towers += tower;
			    		mTitle = WapdroidDbAdapter.PAIRS_CELL;
			    		mSnippet = Integer.toString(mCID);
						mResponse = sendRequest(bldRequest(tower));
						mHandler.post(mDropPin);
			    		cells.moveToNext();}
		    		if (mCell == 0) {
		    			mPin = R.drawable.wifi;
		    			mMsg = WapdroidDbAdapter.PAIRS_NETWORK + ": " + ssid;
		    			mHandler.post(mUpdtDialog);
		        		mTitle = WapdroidDbAdapter.PAIRS_NETWORK;
		        		mSnippet = ssid;
						mResponse = sendRequest(bldRequest(towers));
						mHandler.post(mDropPin);}}
				cells.close();
			   	mLoadingDialog.dismiss();}};
		mThread.start();}

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
		int lat = parseCoordinate(response, latitude);
		int lon = parseCoordinate(response, longitude);
		return new GeoPoint(lat, lon);}
	
	public String sendRequest(String query) {
		Log.v(TAG,"post: "+query);
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
		Log.v(TAG,"response: "+response);
		if (mToken == "") {
			mToken = getValue(response, access_token);
			if (mToken.length() > 0) {
				mToken = mToken.substring(1);
				mToken = mToken.substring(0, mToken.length() -1);}}
		Log.v(TAG,access_token + ": " + mToken);
		return response;}
	
	private String bldRequest(String towers) {
		String request = "{" + getRequestHeader();
		if (mToken != "") request += "," + addString(access_token, mToken);
		return request + "," + addArray(cell_towers, towers) + "}";}
	
	private void dropPin() {
		CellOverlay overlay = new CellOverlay(getResources().getDrawable(mPin));
		mPoint = getGeoPoint(mResponse);
		OverlayItem overlayitem = new OverlayItem(mPoint, mTitle, mSnippet);
		overlay.addOverlay(overlayitem);
		mMOverlays.add(overlay);
	   	mMController.setCenter(mPoint);
	   	mMController.setZoom(12);}
	
	private void updtDialog() {
		Log.v(TAG, "Loading: " + mMsg);
		mLoadingDialog.setMessage(mMsg);}
	
	private class LoadingDialog extends ProgressDialog {
		public LoadingDialog(Context context) {
			super(context);}
		@Override
		public void onBackPressed() {
			super.onBackPressed();
			Log.v(TAG,"backpressed: interrupt thread");
			mThread.interrupt();
			return;}}}
