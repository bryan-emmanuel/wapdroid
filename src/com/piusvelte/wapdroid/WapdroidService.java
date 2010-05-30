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
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.IBinder;
import android.os.RemoteException;
import android.telephony.CellLocation;
import android.telephony.NeighboringCellInfo;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;

public class WapdroidService extends Service {
	private static int NOTIFY_ID = 1;
	public static final String WAKE_SERVICE = "com.piusvelte.wapdroid.WAKE_SERVICE";
	private WapdroidDbAdapter mDbHelper;
	private NotificationManager mNotificationManager;
	private TelephonyManager mTeleManager;
	private String mSsid = "", mBssid = "", mOperator = "", mOperatorName = "", mMcc = "";
	private List<NeighboringCellInfo> mNeighboringCells;
	private WifiManager mWifiManager;
	private int mCid = WapdroidDbAdapter.UNKNOWN_CID, mLac = WapdroidDbAdapter.UNKNOWN_CID, mRssi = 99, mWifiState, mInterval, mPhoneType = TelephonyManager.PHONE_TYPE_NONE, mNetworkType = TelephonyManager.NETWORK_TYPE_UNKNOWN;
	private boolean mWifiIsEnabled, mNotify, mVibrate, mLed, mRingtone, mBatteryOverride, mBatteryLock = true;
	private AlarmManager mAlarmMgr;
	private PendingIntent mPendingIntent;
    private IWapdroidUI mWapdroidUI;
    private boolean mControlWifi = true;
	private static final String TAG = "Wapdroid";
	private double mBatteryPercentage = 100.0, mBatteryRemaining;
	
    private final IWapdroidService.Stub mWapdroidService = new IWapdroidService.Stub() {
		public void updatePreferences(int interval, boolean notify,
				boolean vibrate, boolean led, boolean ringtone, boolean batteryOverride, int batteryPercentage)
				throws RemoteException {
			mInterval = interval;
			if (mNotify && !notify) {
				mNotificationManager.cancel(NOTIFY_ID);
				mNotificationManager = null;}
			else if (!mNotify && notify) {
				mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
				CharSequence contentTitle = getString(mWifiIsEnabled ? R.string.label_enabled : R.string.label_disabled);
			   	Notification notification = new Notification((mWifiIsEnabled ? R.drawable.scanning : R.drawable.status), contentTitle, System.currentTimeMillis());
				PendingIntent contentIntent = PendingIntent.getActivity(getBaseContext(), 0, new Intent(getBaseContext(), WapdroidService.class), 0);
			   	notification.setLatestEventInfo(getBaseContext(), contentTitle, getString(R.string.app_name), contentIntent);
				mNotificationManager.notify(NOTIFY_ID, notification);}
			mNotify = notify;
			mVibrate = vibrate;
			mLed = led;
			mRingtone = ringtone;
			mBatteryOverride = batteryOverride;
			mBatteryPercentage = batteryPercentage;}
		public void setCallback(IBinder mWapdroidUIBinder)
				throws RemoteException {
            if (mWapdroidUIBinder != null) {
    			Log.v(TAG, "setCallback, enable wifi control");
    			mControlWifi = true;
                mWapdroidUI = IWapdroidUI.Stub.asInterface(mWapdroidUIBinder);
                if ((mWapdroidUI != null) && (mCid != WapdroidDbAdapter.UNKNOWN_CID)) {
                	try {
                  		String cells = "'" + Integer.toString(mCid) + "'";
               			if (!mNeighboringCells.isEmpty()) {
               				for (NeighboringCellInfo n : mNeighboringCells) cells += ",'" + Integer.toString(n.getCid()) + "'";}
               			mWapdroidUI.setOperator(mOperatorName, mMcc, mOperator);
                		mWapdroidUI.setCellInfo(Integer.toString(mCid), Integer.toString(mLac));
                		mWapdroidUI.setWifiInfo(mWifiState, mSsid, mBssid);
                		mWapdroidUI.setSignalStrength(mRssi);
                		mWapdroidUI.setCells(cells);
                		mWapdroidUI.setBattery(mBatteryRemaining);}
                    catch (RemoteException e) {}}}}
		public void suspendWifiControl() throws RemoteException {
			Log.v(TAG, "running wifi settings, disable wifi control");
			mControlWifi = false;}};
	
	private final PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
    	public void onCellLocationChanged(CellLocation location) {
    		getCellInfo(location);}
    	public void onSignalStrengthChanged(int asu) {
    		Log.v(TAG,"onSignalStrengthChanged:"+Integer.toString(asu));
			if (asu != WapdroidDbAdapter.UNKNOWN_RSSI) {
				mRssi = getDbm(asu);
				if ((mNetworkType == TelephonyManager.NETWORK_TYPE_GPRS) || (mNetworkType == TelephonyManager.NETWORK_TYPE_EDGE)) signalStrengthChanged();
				suspend();}
			else Log.v(TAG,"onSignalStrengthChanged:99");}
    	public void onSignalStrengthsChanged(SignalStrength signalStrength) {
			Log.v(TAG, "onSignalStrengthsChanged");
			Log.v(TAG, "PhoneType:"+Integer.toString(mPhoneType));
			Log.v(TAG, "NetworkType:"+Integer.toString(mNetworkType));
    		if (mPhoneType == TelephonyManager.PHONE_TYPE_GSM) {
    			Log.v(TAG, "GSM");
    			if ((mNetworkType == TelephonyManager.NETWORK_TYPE_GPRS) || (mNetworkType == TelephonyManager.NETWORK_TYPE_EDGE)) {
    				Log.v(TAG, "GPRS");
    				mRssi = getDbm(signalStrength.getGsmSignalStrength());
    				signalStrengthChanged();
    				suspend();}
    			else if ((mNetworkType == TelephonyManager.NETWORK_TYPE_UMTS) || (mNetworkType == TelephonyManager.NETWORK_TYPE_HSDPA) || (mNetworkType == TelephonyManager.NETWORK_TYPE_HSUPA) || (mNetworkType == TelephonyManager.NETWORK_TYPE_HSPA)) {
    				Log.v(TAG, "UMTS");}}
	       	else if (mPhoneType == TelephonyManager.PHONE_TYPE_CDMA) {
	       		Log.v(TAG, "CDMA");}
			Log.v(TAG, "GsmSignalStrength:"+Integer.toString(signalStrength.getGsmSignalStrength()));
    		Log.v(TAG, "CdmaDbm:"+Integer.toString(signalStrength.getCdmaDbm()));
			Log.v(TAG, "EvdoDbm:"+Integer.toString(signalStrength.getEvdoDbm()));}};
	
	private BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
		    	mAlarmMgr.cancel(mPendingIntent);
				ManageWakeLocks.release();
				context.startService(new Intent(context, WapdroidService.class));}
			else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
				Log.v(TAG, "screen off, enable wifi control");
				mControlWifi = true;
				if (mInterval > 0) mAlarmMgr.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + mInterval, mPendingIntent);}
			else if (intent.getAction().equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
				// save the state as it's checked elsewhere, like the phonestatelistener
				int mWifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 4);
				if (mWifiState == WifiManager.WIFI_STATE_ENABLED) {
					mWifiIsEnabled = true;
					wifiChanged();}
				else if (mWifiState != WifiManager.WIFI_STATE_UNKNOWN) {
					mWifiIsEnabled = false;
					mSsid = null;
					mBssid = null;}
                updateUiWifi();}
			else if (intent.getAction().equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
				NetworkInfo mNetworkInfo = (NetworkInfo) intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
				if (mNetworkInfo.isConnected()) {
					setWifiInfo();
					wifiChanged();}
				else {
					mSsid = null;
					mBssid = null;}
                updateUiWifi();}
			else if (intent.getAction().equals(Intent.ACTION_BATTERY_CHANGED)) {
				mBatteryRemaining = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
				Log.v(TAG,"battery:"+Double.toString(mBatteryRemaining));
				if (mBatteryOverride && (mBatteryRemaining < mBatteryPercentage)) {
					mBatteryLock = true;
					toggleWifi(false);}
				else mBatteryLock = false;
		    	if (mWapdroidUI != null) {
		        	try {
		        		mWapdroidUI.setBattery(mBatteryRemaining);}
		            catch (RemoteException e) {}}}}};
	
	@Override
	public IBinder onBind(Intent intent) {
    	mAlarmMgr.cancel(mPendingIntent);
		ManageWakeLocks.release();
		return mWapdroidService;}
	
	@Override
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);
		init();}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		super.onStart(intent, startId);
		init();
		return START_STICKY;}
	
	private void init() {
		/*
		 * started on boot, wake, screen_on, ui, settings
		 * boot and wake will wakelock and should set the alarm,
		 * others should release the lock and cancel the alarm
		 */
		mWifiState = mWifiManager.getWifiState();
		mWifiIsEnabled = (mWifiState == WifiManager.WIFI_STATE_ENABLED);
		if (mWifiIsEnabled) setWifiInfo();
		getCellInfo(mTeleManager.getCellLocation());}
	
    @Override
    public void onCreate() {
        super.onCreate();
		IntentFilter intentfilter = new IntentFilter();
		intentfilter.addAction(Intent.ACTION_SCREEN_OFF);
		intentfilter.addAction(Intent.ACTION_SCREEN_ON);
		intentfilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
		intentfilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
		intentfilter.addAction(Intent.ACTION_BATTERY_CHANGED);
		registerReceiver(mReceiver, intentfilter);
		Intent i = new Intent(this, BootReceiver.class);
		i.setAction(WAKE_SERVICE);
		mPendingIntent = PendingIntent.getBroadcast(this, 0, i, 0);
		SharedPreferences prefs = (SharedPreferences) getSharedPreferences(getString(R.string.key_preferences), WapdroidService.MODE_PRIVATE);
		// initialize preferences, updated by UI
		mInterval = Integer.parseInt((String) prefs.getString(getString(R.string.key_interval), "30000"));
		mNotify = prefs.getBoolean(getString(R.string.key_notify), false);
		if (mNotify) mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		mVibrate = prefs.getBoolean(getString(R.string.key_vibrate), false);
		mLed = prefs.getBoolean(getString(R.string.key_led), false);
		mRingtone = prefs.getBoolean(getString(R.string.key_ringtone), false);
		mBatteryOverride = prefs.getBoolean(getString(R.string.key_battery_override), false);
		mBatteryPercentage = Integer.parseInt((String) prefs.getString(getString(R.string.key_battery_percentage), "30"));
		prefs = null;
		mDbHelper = new WapdroidDbAdapter(this);
		mTeleManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		mTeleManager.listen(mPhoneStateListener, (PhoneStateListener.LISTEN_CELL_LOCATION | PhoneStateListener.LISTEN_SIGNAL_STRENGTH| PhoneStateListener.LISTEN_SIGNAL_STRENGTHS));
		mPhoneType = mTeleManager.getPhoneType();
		mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		mAlarmMgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
		if (mNotify) {
			CharSequence contentTitle = getString(mWifiIsEnabled ? R.string.label_enabled : R.string.label_disabled);
		   	Notification notification = new Notification((mWifiIsEnabled ? R.drawable.statuson : R.drawable.scanning), contentTitle, System.currentTimeMillis());
			PendingIntent contentIntent = PendingIntent.getActivity(getBaseContext(), 0, new Intent(getBaseContext(), WapdroidService.class), 0);
		   	notification.setLatestEventInfo(getBaseContext(), contentTitle, getString(R.string.app_name), contentIntent);
			mNotificationManager.notify(NOTIFY_ID, notification);}}
    
    @Override
    public void onDestroy() {
    	super.onDestroy();
    	if (mReceiver != null) {
    		unregisterReceiver(mReceiver);
    		mReceiver = null;}
		mTeleManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
		if (mNotify && (mNotificationManager != null)) mNotificationManager.cancel(NOTIFY_ID);}
     
    private int getDbm(int asu) {
    	int dBm = asu;
		if (dBm != WapdroidDbAdapter.UNKNOWN_RSSI) {
			if ((mNetworkType == TelephonyManager.NETWORK_TYPE_GPRS) || (mNetworkType == TelephonyManager.NETWORK_TYPE_EDGE)) {
				dBm = 2 * asu - 113;}
			else if ((mNetworkType == TelephonyManager.NETWORK_TYPE_UMTS) || (mNetworkType == TelephonyManager.NETWORK_TYPE_HSDPA) || (mNetworkType == TelephonyManager.NETWORK_TYPE_HSUPA) || (mNetworkType == TelephonyManager.NETWORK_TYPE_HSPA)) {}}
		else Log.v(TAG,"unknown signal");
    	return dBm;}
    
    private void suspend() {
    	if (ManageWakeLocks.hasLock()) {
    		if (mInterval > 0) mAlarmMgr.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + mInterval, mPendingIntent);
    		ManageWakeLocks.release();}}

    private void getCellInfo(CellLocation location) {
		if (mTeleManager.getNetworkType() != TelephonyManager.NETWORK_TYPE_UNKNOWN) mNetworkType = mTeleManager.getNetworkType();
		mNeighboringCells = mTeleManager.getNeighboringCellInfo();
		if (mOperatorName == "") mOperatorName = mTeleManager.getNetworkOperatorName();
   		if (mMcc == "") mMcc = mTeleManager.getNetworkCountryIso();
   		if (mOperator == "") mOperator = mTeleManager.getNetworkOperator();
   		if (mPhoneType == TelephonyManager.PHONE_TYPE_GSM) {
   			int cid = ((GsmCellLocation) location).getCid();
   			if (cid > 0) mCid = cid;
			int lac = ((GsmCellLocation) location).getLac();
			if (lac > 0) mLac = lac;}
       	else if (mPhoneType == TelephonyManager.PHONE_TYPE_CDMA) {
    		// check the phone type, cdma is not available before API 2.0, so use a wrapper
       		try {
       			CdmaCellLocation cdma = new CdmaCellLocation(location);
       			int cid = cdma.getBaseStationId();
       			if (cid > 0) mCid = cid;
    			int lac = cdma.getNetworkId();
    			if (lac > 0) mLac = lac;}
       		catch (Throwable t) {
       			mCid = WapdroidDbAdapter.UNKNOWN_CID;
       			mLac = WapdroidDbAdapter.UNKNOWN_CID;}}
        if ((mWapdroidUI != null) && (mCid != WapdroidDbAdapter.UNKNOWN_CID)) {
        	try {
          		String cells = "'" + Integer.toString(mCid) + "'";
       			if (!mNeighboringCells.isEmpty()) {
       				for (NeighboringCellInfo n : mNeighboringCells) cells += ",'" + Integer.toString(n.getCid()) + "'";}
       			Log.v(TAG,"getCellInfo:"+Integer.toString(mCid)+","+Integer.toString(mLac)+","+mOperatorName+","+mMcc+","+mOperator+","+cells);
       			mWapdroidUI.setOperator(mOperatorName, mMcc, mOperator);
        		mWapdroidUI.setCellInfo(Integer.toString(mCid), Integer.toString(mLac));
        		mWapdroidUI.setCells(cells);}
            catch (RemoteException e) {
            	Log.e(TAG, "error in mWapdroidUI.setCellInfo"+mCid+","+mLac+","+mTeleManager.getNetworkOperatorName()+","+mTeleManager.getNetworkCountryIso());}}}
    
    private void signalStrengthChanged() {
        if (mWapdroidUI != null) {
        	try {
        		mWapdroidUI.setSignalStrength(mRssi);}
            catch (RemoteException e) {}}
       	if ((mCid != WapdroidDbAdapter.UNKNOWN_CID) && (mDbHelper != null)) {
    		mDbHelper.open();
			if (mWifiIsEnabled && (mSsid != null) && (mBssid != null)) updateRange();
			else if (mControlWifi) {
				boolean isInRange = false;
				if (mDbHelper.cellInRange(mCid, mLac, mRssi)) {
					isInRange = true;
					for (NeighboringCellInfo n : mNeighboringCells) {
						int cid = WapdroidDbAdapter.UNKNOWN_CID, lac = WapdroidDbAdapter.UNKNOWN_CID, rssi = WapdroidDbAdapter.UNKNOWN_RSSI;
		    			if ((mNetworkType == TelephonyManager.NETWORK_TYPE_GPRS) || (mNetworkType == TelephonyManager.NETWORK_TYPE_EDGE)) {
		    				Log.v(TAG, "GSM");
		    				cid = n.getCid();
		    				lac = n.getLac();
		    				if (lac < 1) lac = WapdroidDbAdapter.UNKNOWN_CID;
		    				rssi = getDbm(n.getRssi());}
		    			else if ((mNetworkType == TelephonyManager.NETWORK_TYPE_UMTS) || (mNetworkType == TelephonyManager.NETWORK_TYPE_HSDPA) || (mNetworkType == TelephonyManager.NETWORK_TYPE_HSUPA) || (mNetworkType == TelephonyManager.NETWORK_TYPE_HSPA)) {
		    				Log.v(TAG, "UMTS");}
	    				Log.v(TAG, "cid: "+n.getCid());
	    				Log.v(TAG, "lac: "+n.getLac());
	    				Log.v(TAG, "rssi: "+n.getRssi());
	    				Log.v(TAG, "psc: "+n.getPsc());
						if (isInRange && (cid > 0)) isInRange = mDbHelper.cellInRange(cid, lac, rssi);}}
				if ((isInRange && !mBatteryLock && !mWifiIsEnabled && (mWifiState != WifiManager.WIFI_STATE_ENABLING)) || (!isInRange && mWifiIsEnabled)) {
					Log.v(TAG, "set wifi:"+isInRange);
					toggleWifi(isInRange);}}
	    	mDbHelper.close();}}
    
    private void toggleWifi(boolean enable) {
    	mWifiManager.setWifiEnabled(enable);
		if (mNotify) {
			CharSequence contentTitle = getString(R.string.label_WIFI) + " " + getString(enable ? R.string.label_enabled : R.string.label_disabled);
		   	Notification notification = new Notification((enable ? R.drawable.statuson : R.drawable.scanning), contentTitle, System.currentTimeMillis());
		   	Intent i = new Intent(getBaseContext(), WapdroidService.class);
			PendingIntent contentIntent = PendingIntent.getActivity(getBaseContext(), 0, i, 0);
		   	notification.setLatestEventInfo(getBaseContext(), contentTitle, getString(R.string.app_name), contentIntent);
		   	if (mVibrate) notification.defaults |= Notification.DEFAULT_VIBRATE;
		   	if (mLed) notification.defaults |= Notification.DEFAULT_LIGHTS;
		   	if (mRingtone) notification.defaults |= Notification.DEFAULT_SOUND;
			mNotificationManager.notify(NOTIFY_ID, notification);}}
    
    private void updateUiWifi() {
    	if (mWapdroidUI != null) {
        	try {
        		mWapdroidUI.setWifiInfo(mWifiState, mSsid, mBssid);}
            catch (RemoteException e) {}}}
    
    private void setWifiInfo() {
		mSsid = mWifiManager.getConnectionInfo().getSSID();
		mBssid = mWifiManager.getConnectionInfo().getBSSID();
        if (mWapdroidUI != null) {
        	try {
        		mWapdroidUI.setWifiInfo(mWifiState, mSsid, mBssid);}
            catch (RemoteException e) {}}}
    
    private void updateRange() {
    	int network = mDbHelper.updateNetworkRange(mSsid, mBssid, mCid, mLac, mRssi);
		for (NeighboringCellInfo n : mNeighboringCells) {
			int cid = WapdroidDbAdapter.UNKNOWN_CID, lac = WapdroidDbAdapter.UNKNOWN_CID, rssi = WapdroidDbAdapter.UNKNOWN_RSSI;
			if ((mNetworkType == TelephonyManager.NETWORK_TYPE_GPRS) || (mNetworkType == TelephonyManager.NETWORK_TYPE_EDGE)) {
				Log.v(TAG, "GSM");
				cid = n.getCid();
				lac = n.getLac();
				if (lac < 1) lac = WapdroidDbAdapter.UNKNOWN_CID;
				rssi = getDbm(n.getRssi());}
			else if ((mNetworkType == TelephonyManager.NETWORK_TYPE_UMTS) || (mNetworkType == TelephonyManager.NETWORK_TYPE_HSDPA) || (mNetworkType == TelephonyManager.NETWORK_TYPE_HSUPA) || (mNetworkType == TelephonyManager.NETWORK_TYPE_HSPA)) {
				Log.v(TAG, "UMTS");}
			Log.v(TAG, "cid: "+n.getCid());
			Log.v(TAG, "lac: "+n.getLac());
			Log.v(TAG, "rssi: "+n.getRssi());
			Log.v(TAG, "psc: "+n.getPsc());
			if (lac < 1) lac = WapdroidDbAdapter.UNKNOWN_CID;
			if (cid > 0) mDbHelper.createPair(cid, lac, network, rssi);}}
	
	private void wifiChanged() {
		if (mWifiIsEnabled && (mSsid != null) && (mBssid != null) && (mCid != WapdroidDbAdapter.UNKNOWN_CID) && (mDbHelper != null)) {
			mDbHelper.open();
	    	updateRange();
	    	mDbHelper.close();}}}