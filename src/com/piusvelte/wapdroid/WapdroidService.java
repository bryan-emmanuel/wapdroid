package com.piusvelte.wapdroid;

import java.util.List;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.os.RemoteException;
import android.telephony.CellLocation;
import android.telephony.NeighboringCellInfo;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;

public class WapdroidService extends Service {
	private WapdroidDbAdapter mDbHelper;
	private WapdroidWifiReceiver mWifiReceiver;
	private WapdroidPhoneStateListener mPhoneStateListener;
	private TelephonyManager mTeleManager;
	private GsmCellLocation mGsmCellLocation;
	private String mMNC = null, mMCC = null, mSSID = null;
	private List<NeighboringCellInfo> mNeighboringCells;
	private WifiManager mWifiManager;
	private WifiInfo mWifiInfo;
	private int mCID = -1, mLAC = -1, mRSSI = -1, mWifiState, mWifiEnabling, mWifiEnabled, mWifiUnknown;
	private boolean mWifiIsEnabled = false;
	private IWapdroidUI mWapdroidUI;

	@Override
	public IBinder onBind(Intent arg0) {
		return mWapdroidService;}
	
	@Override
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);
	}
	
    @Override
    public void onCreate() {
        super.onCreate();
		mWifiEnabled = WifiManager.WIFI_STATE_ENABLED;
		mWifiEnabling = WifiManager.WIFI_STATE_ENABLING;
		mWifiUnknown = WifiManager.WIFI_STATE_UNKNOWN;
		mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		mWifiInfo = mWifiManager.getConnectionInfo();
		mDbHelper = new WapdroidDbAdapter(this);
		mDbHelper.open();
		mWifiReceiver = new WapdroidWifiReceiver();
		registerReceiver(mWifiReceiver, new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION));
		registerReceiver(mWifiReceiver, new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION));
		mPhoneStateListener = new WapdroidPhoneStateListener();
		mTeleManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		mGsmCellLocation = (GsmCellLocation) mTeleManager.getCellLocation();
		mTeleManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CELL_LOCATION ^ PhoneStateListener.LISTEN_SIGNAL_STRENGTH);}
    
    @Override
    public void onDestroy() {
    	super.onDestroy();
    	unregisterReceiver(mWifiReceiver);
    	mTeleManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);}
    
    private final IWapdroidService.Stub mWapdroidService = new IWapdroidService.Stub() {
		public void setCallback(IBinder mWapdroidUIBinder) throws RemoteException {
			// UI started or stopped
			if (mWapdroidUIBinder != null) {
				mWapdroidUI = IWapdroidUI.Stub.asInterface(mWapdroidUIBinder);
        		try {
        			mWapdroidUI.locationChanged((String) "" + mCID, (String) "" + mLAC, (String) "" + mMNC, (String) "" + mMCC);
        			mWapdroidUI.signalChanged((String) "" + (-113 + 2 * mRSSI) + "dBm");}
        		catch (RemoteException e) {}}
			else {
				mWapdroidUI = null;}}};
    
    private boolean hasCell() {
    	return (mCID > 0) && (mLAC > 0) && (mMNC != null) && (mMNC != "") && (mMCC != null) && (mMCC != "") && (mRSSI > 0);}
    
    private void updateRange() {
		mDbHelper.updateCellRange(mSSID, mCID, mLAC, mMNC, mMCC, mRSSI);
		int mNeighborCID;
		int mNeighborRSSI;
		for (NeighboringCellInfo n : mNeighboringCells) {
			mNeighborCID = n.getCid();
			mNeighborRSSI = convertRSSIToASU(n.getRssi());
			if ((mNeighborCID > 0) && (mNeighborRSSI > 0)) {
				mDbHelper.updateNeighborRange(mSSID, mNeighborCID, mNeighborRSSI);}}}
	
	private void wifiChanged() {
		if (mWifiIsEnabled && (mSSID != null) && hasCell()) {
			    	updateRange();}}

    private int convertRSSIToASU(int mSignal) {
		/*
		 * ASU is reported by the signal strength with a range of 0-31
		 * RSSI = -113 + 2 * ASU
		 * NeighboringCellInfo reports a positive RSSI
		 * and needs to be converted to ASU
		 * ASU = ((-1 * RSSI) + 113) / 2
		 */
    	return mSignal > 31 ? Math.round(((-1 * mSignal) + 113) / 2) : mSignal;}
    
    private void manageWifi() {
		if (hasCell()) {
			if (mWifiIsEnabled && (mSSID != null)) {
				updateRange();}
			else {
				boolean mInRange = false;
				// coarse range check
				Cursor c = mDbHelper.cellsInRange(mCID, mRSSI);
				if (c.getCount() > 0) {
					mInRange = true;
					boolean mCheckNeighbors = true;
					String mNetworkColIdx = WapdroidDbAdapter.CELLS_NETWORK;
					c.moveToFirst();
					while (mCheckNeighbors && !c.isAfterLast()) {
						mCheckNeighbors = mDbHelper.hasNeighbors(c.getInt(c.getColumnIndex(mNetworkColIdx)));
						c.moveToNext();}
					// if there are neighbors for all networks in range, then perform fine range checking
					if (mCheckNeighbors) {
						int mNeighborCID, mNeighborRSSI;
						for (NeighboringCellInfo n : mNeighboringCells) {
							mNeighborCID = n.getCid();
							mNeighborRSSI = convertRSSIToASU(n.getRssi());
							if (mInRange && (mNeighborCID > 0) && (mNeighborRSSI > 0)) {
								mInRange = mDbHelper.neighborInRange(mNeighborCID, mNeighborRSSI);}}}}
				c.close();
				if ((mInRange && !mWifiIsEnabled && (mWifiState != mWifiEnabling)) || (!mInRange && mWifiIsEnabled)) {
					mWifiManager.setWifiEnabled(mInRange);}}}
		else if (mWifiIsEnabled && (mSSID == null)) {
			mWifiManager.setWifiEnabled(false);}}
    
    public class WapdroidWifiReceiver extends BroadcastReceiver {    	
    	@Override
    	public void onReceive(Context context, Intent intent) {
    		if (intent.getAction().equals(WifiManager.WIFI_STATE_CHANGED_ACTION)){
    			int mWifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 4);
    	    	if (mWifiState != mWifiUnknown) {
    	    		mWifiIsEnabled = (mWifiState == mWifiEnabled);
    	    		if (!mWifiIsEnabled) {
    	    			mSSID = null;}}
    			wifiChanged();}
    		else if (intent.getAction().equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
    			NetworkInfo mNetworkInfo = (NetworkInfo) intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
    	    	if (mNetworkInfo.isConnected()) {
    	    		mWifiInfo = mWifiManager.getConnectionInfo();
    	    		mSSID = mWifiInfo.getSSID();}
    	    	else {
    	    		mSSID = null;}
    	    	wifiChanged();}}}
    
    public class WapdroidPhoneStateListener extends PhoneStateListener {
    	@Override
    	public void onSignalStrengthChanged(int asu) {
    		super.onSignalStrengthChanged(asu);
    		//phone state intent receiver: 0-31, for GSM, dBm = -113 + 2 * asu
        	mRSSI = asu;
        	if (mWapdroidUI != null) {
        		try {
        			mWapdroidUI.signalChanged((String) "" + (-113 + 2 * asu) + "dBm");}
        		catch (RemoteException e) {}}
        	mNeighboringCells = mTeleManager.getNeighboringCellInfo();
    		manageWifi();}    	
    	@Override
    	public void onCellLocationChanged(CellLocation location) {
    		super.onCellLocationChanged(location);
    		mGsmCellLocation = (GsmCellLocation) location;
        	mCID = mGsmCellLocation.getCid();
        	mLAC = mGsmCellLocation.getLac();
    		mMNC = mTeleManager.getNetworkOperatorName();
    		mMCC = mTeleManager.getNetworkCountryIso();
        	mNeighboringCells = mTeleManager.getNeighboringCellInfo();
        	if (mWapdroidUI != null) {
        		try {
        			mWapdroidUI.locationChanged((String) "" + mCID, (String) "" + mLAC, (String) "" + mMNC, (String) "" + mMCC);}
        		catch (RemoteException e) {}}
    		manageWifi();}}}
