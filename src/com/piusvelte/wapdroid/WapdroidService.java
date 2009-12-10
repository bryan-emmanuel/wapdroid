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

import java.util.List;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
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
	private static int NOTIFY_ID = 1;
	public static final String WAKE_SERVICE = "com.piusvelte.wapdroid.WAKE_SERVICE";
	public static final String SCREEN_ON = "com.piusvelte.wapdroid.SCREEN_ON";
	private WapdroidDbAdapter mDbHelper;
	private NotificationManager mNotificationManager;
	private TelephonyManager mTeleManager;
	private String mSSID = null, mBSSID, mMNC = null, mMCC = null;
	private List<NeighboringCellInfo> mNeighboringCells;
	private WifiManager mWifiManager;
	private int mCID = -1, mWifiState, mInterval = 300000; // 5min interval
	private boolean mWifiIsEnabled = false;
	private IWapdroidUI mWapdroidUI;
	private SharedPreferences mPreferences;
	private AlarmManager mAlarmManager;
	private PendingIntent mPendingIntent;
	
    private final IWapdroidService.Stub mWapdroidService = new IWapdroidService.Stub() {		
		public void setCallback(IBinder mWapdroidUIBinder) throws RemoteException {
			if (mWapdroidUIBinder != null) {
				mWapdroidUI = IWapdroidUI.Stub.asInterface(mWapdroidUIBinder);
	        	if ((mWapdroidUI != null) && (mCID > 0)) {
	        		try {
		        		mWapdroidUI.setCellLocation((String) "" + mCID, (String) "" + mMNC, (String) "" + mMCC);}
	        		catch (RemoteException e) {}}}
			else {
				mWapdroidUI = null;}}};
	
	private final PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
    	public void onCellLocationChanged(CellLocation location) {
    		checkLocation(location);
    		if (mCID > 0) {
    			checkForUIBeforeStopping();}}};
    
	private BroadcastReceiver mReceiver = null;

	@Override
	public IBinder onBind(Intent intent) {
		// stop the Alarm if UI binds, it'll be reset in onDestroy, if appropriate
		mAlarmManager.cancel(mPendingIntent);
		IntentFilter intentfilter = new IntentFilter();
		intentfilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
		intentfilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
		registerReceiver(mReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
	    		if (intent.getAction().equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
	        		// save the state as it's checked elsewhere, like the phonestatelistener
	    			int mWifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 4);
	   	    		switch (mWifiState) {
	   	    			case WifiManager.WIFI_STATE_UNKNOWN:
	   	    				break;
	   	    			case WifiManager.WIFI_STATE_ENABLED:
	   	    				mWifiIsEnabled = true;
	       	    			wifiChanged();
	   	    				break;
	   	    			case WifiManager.WIFI_STATE_DISABLED:
	   	    				mWifiIsEnabled = false;
		    	    		clearWifiInfo();
	   	    				break;
	   	    			default:
	   	    				mWifiIsEnabled = false;
		    	    		clearWifiInfo();
	   	    				break;}}
	    		else if (intent.getAction().equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
	    			NetworkInfo mNetworkInfo = (NetworkInfo) intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
	    	    	if (mNetworkInfo.isConnected()) {
	    	    		setWifiInfo(mWifiManager.getConnectionInfo());
	    	    		wifiChanged();}
	    	    	else {
	    	    		clearWifiInfo();}}}}, intentfilter);
		return mWapdroidService;}
	
	@Override
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		super.onStart(intent, startId);
		return 0;}
	
    @Override
    public void onCreate() {
        super.onCreate();
		mAlarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
		Intent i = new Intent(this, WapdroidServiceManager.class);
		i.setAction(WAKE_SERVICE);
		mPendingIntent = PendingIntent.getBroadcast(this, 0, i, 0);
		mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		mWifiIsEnabled = mWifiManager.isWifiEnabled();
		// set up to record connected cells
		if (mWifiIsEnabled) {
    		setWifiInfo(mWifiManager.getConnectionInfo());}
		mPreferences = (SharedPreferences) getSharedPreferences(getString(R.string.key_preferences), WapdroidService.MODE_PRIVATE);
		mDbHelper = new WapdroidDbAdapter(this);
		mDbHelper.open();
		mTeleManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		checkLocation(mTeleManager.getCellLocation());
		mTeleManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CELL_LOCATION);}
    
    @Override
    public void onDestroy() {
    	super.onDestroy();
    	if (mReceiver != null) {
    		unregisterReceiver(mReceiver);}
    	mTeleManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
    	if (mDbHelper != null) {
    		mDbHelper.close();
    		mDbHelper = null;}
    	if (mPreferences.getBoolean(getString(R.string.key_manageWifi), true)) {
       		mAlarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + mInterval, mPendingIntent);}
		ManageWakeLocks.release();}
    
    private void checkForUIBeforeStopping() {
    	/*
    	 * upon UI binding, this prevents the service from dying
    	 * onDestroy will unregister the receiver, since UI will stop the service
    	 */
    	if (mReceiver == null) {
    		stopSelf();}}
    
    private void checkLocation(CellLocation location) {
       	if (mTeleManager.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM) {
       		mCID = ((GsmCellLocation) location).getCid();}
       	else if (mTeleManager.getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA) {
    		// check the phone type, cdma is not available before API 2.0, so use a wrapper
       		try {
       			mCID = (new CdmaCellLocationWrapper(location)).getBaseStationId();}
       		catch (Throwable t) {
       			mCID = -1;}}
       	else {
   			mCID = -1;}
       	if (mCID > 0) {
    		mMNC = mTeleManager.getNetworkOperatorName();
    		mMCC = mTeleManager.getNetworkCountryIso();
    		mNeighboringCells = mTeleManager.getNeighboringCellInfo();
        	if (mWapdroidUI != null) {
        		try {
	        		mWapdroidUI.setCellLocation((String) "" + mCID, (String) "" + mMNC, (String) "" + mMCC);}
        		catch (RemoteException e) {}}
			if (mWifiIsEnabled && (mSSID != null) && (mBSSID != null)) {
				updateRange();}
			else {
				boolean mInRange = false;
				if (mDbHelper.cellInRange(mCID)) {
					mInRange = true;
						int cid;
						for (NeighboringCellInfo n : mNeighboringCells) {
							cid = n.getCid();
							if (mInRange && (cid > 0)) {
								mInRange = mDbHelper.cellInRange(cid);}}}
				if ((mInRange && !mWifiIsEnabled && (mWifiState != WifiManager.WIFI_STATE_ENABLING)) || (!mInRange && mWifiIsEnabled)) {
					mWifiManager.setWifiEnabled(mInRange);
					if (mPreferences.getBoolean(getString(R.string.key_notify), true)) {
						int icon = mInRange ? R.drawable.statuson : R.drawable.status;
						CharSequence contentTitle = getString(R.string.label_WIFI) + " " + getString(mInRange ? R.string.label_enabled : R.string.label_disabled);
					  	long when = System.currentTimeMillis();
					   	Notification notification = new Notification(icon, contentTitle, when);
					   	Intent i = new Intent(getBaseContext(), WapdroidService.class);
						PendingIntent contentIntent = PendingIntent.getActivity(getBaseContext(), 0, i, 0);
					   	notification.setLatestEventInfo(getBaseContext(), contentTitle, getString(R.string.app_name), contentIntent);
					   	if (mPreferences.getBoolean(getString(R.string.key_vibrate), false)) {
					   		notification.defaults |= Notification.DEFAULT_VIBRATE;}
					   	if (mPreferences.getBoolean(getString(R.string.key_led), false)) {
					   		notification.defaults |= Notification.DEFAULT_LIGHTS;}
					   	if (mPreferences.getBoolean(getString(R.string.key_ringtone), false)) {
					   		notification.defaults |= Notification.DEFAULT_SOUND;}
						mNotificationManager.notify(NOTIFY_ID, notification);}}}}}
    
    private void setWifiInfo(WifiInfo info) {
		mSSID = info.getSSID();
		mBSSID = info.getBSSID();}
    
    private void clearWifiInfo() {
		mSSID = null;
		mBSSID = null;}
    
    private void updateRange() {
    	int network = mDbHelper.updateCellRange(mSSID, mBSSID, mCID);
		int cid;
    	if (mWapdroidUI != null) {
    		try {
        		mWapdroidUI.newCell((String) "" + mCID);}
    		catch (RemoteException e) {}}
		for (NeighboringCellInfo n : mNeighboringCells) {
			cid = n.getCid();
			if (cid > 0) {
				mDbHelper.updateCellNeighbor(network, cid);}}}
	
	private void wifiChanged() {
		if (mWifiIsEnabled && (mSSID != null) && (mBSSID != null) && (mCID > 0)) {
	    	updateRange();}}}