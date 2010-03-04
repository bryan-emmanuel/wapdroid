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
import android.os.IBinder;
import android.os.RemoteException;
import android.telephony.CellLocation;
import android.telephony.NeighboringCellInfo;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;

public class WapdroidService extends Service {
	private static int NOTIFY_ID = 1;
	private static int NOTIFY_SCANNING = 2;
	public static final String WAKE_SERVICE = "com.piusvelte.wapdroid.WAKE_SERVICE";
	private WapdroidDbAdapter mDbHelper;
	private NotificationManager mNotificationManager;
	private TelephonyManager mTeleManager;
	private String mSSID, mBSSID, mMNC, mMCC;
	private List<NeighboringCellInfo> mNeighboringCells;
	private WifiManager mWifiManager;
	private int mCID = -1, mWifiState, mInterval;
	private boolean mWifiIsEnabled, mNotify, mVibrate, mLed, mRingtone;
	private IWapdroidUI mWapdroidUI;
	private AlarmManager mAlarmManager;
	private PendingIntent mPendingIntent;
	
    private final IWapdroidService.Stub mWapdroidService = new IWapdroidService.Stub() {		
		public void setCallback(IBinder mWapdroidUIBinder) throws RemoteException {
			if (mWapdroidUIBinder != null) {
		    	mAlarmManager.cancel(mPendingIntent);
				mWapdroidUI = IWapdroidUI.Stub.asInterface(mWapdroidUIBinder);
	        	if ((mWapdroidUI != null) && (mCID > 0)) {
	        		try {
		        		mWapdroidUI.setCellLocation((String) "" + mCID, (String) "" + mMNC, (String) "" + mMCC);}
	        		catch (RemoteException e) {}}}
			else {
				mWapdroidUI = null;}}

		public void updatePreferences(int interval, boolean notify,
				boolean vibrate, boolean led, boolean ringtone)
				throws RemoteException {
			mInterval = interval;
			if (mNotify && !notify) {
				mNotificationManager.cancel(NOTIFY_SCANNING);
				mNotificationManager = null;}
			else if (!mNotify && notify) {
				mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
				CharSequence contentTitle = getString(R.string.scanning);
			   	Notification notification = new Notification(R.drawable.scanning, contentTitle, System.currentTimeMillis());
				PendingIntent contentIntent = PendingIntent.getActivity(getBaseContext(), 0, new Intent(getBaseContext(), WapdroidService.class), 0);
			   	notification.setLatestEventInfo(getBaseContext(), contentTitle, getString(R.string.app_name), contentIntent);
				mNotificationManager.notify(NOTIFY_SCANNING, notification);}
			mNotify = notify;
			mVibrate = vibrate;
			mLed = led;
			mRingtone = ringtone;}};
	
	private final PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
    	public void onCellLocationChanged(CellLocation location) {
    		checkLocation(location);
    		if (ManageWakeLocks.hasLock()) {
    			if (mInterval > 0) {
    				mAlarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + mInterval, mPendingIntent);}
    			ManageWakeLocks.release();}}};
	
	private BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
		    	mAlarmManager.cancel(mPendingIntent);
				ManageWakeLocks.release();
				context.startService(new Intent(context, WapdroidService.class));}
			else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
				if (mInterval > 0) {
					mAlarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + mInterval, mPendingIntent);}}
			else if (intent.getAction().equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
				// save the state as it's checked elsewhere, like the phonestatelistener
				int mWifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 4);
				if (mWifiState == WifiManager.WIFI_STATE_ENABLED) {
					mWifiIsEnabled = true;
					wifiChanged();}
				else if (mWifiState != WifiManager.WIFI_STATE_UNKNOWN) {
					mWifiIsEnabled = false;
					mSSID = null;
					mBSSID = null;}}
			else if (intent.getAction().equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
				NetworkInfo mNetworkInfo = (NetworkInfo) intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
				if (mNetworkInfo.isConnected()) {
					setWifiInfo();
					wifiChanged();}
				else {
					mSSID = null;
					mBSSID = null;}}}};
	
	@Override
	public IBinder onBind(Intent intent) {
    	mAlarmManager.cancel(mPendingIntent);
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
		if (mWifiIsEnabled) {
			setWifiInfo();}
		if (mNotify) {
			CharSequence contentTitle = getString(R.string.scanning);
		   	Notification notification = new Notification(R.drawable.scanning, contentTitle, System.currentTimeMillis());
			PendingIntent contentIntent = PendingIntent.getActivity(getBaseContext(), 0, new Intent(getBaseContext(), WapdroidService.class), 0);
		   	notification.setLatestEventInfo(getBaseContext(), contentTitle, getString(R.string.app_name), contentIntent);
			mNotificationManager.notify(NOTIFY_SCANNING, notification);}
		checkLocation(mTeleManager.getCellLocation());
		if (mCID == -1) {
			mTeleManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CELL_LOCATION);}
		else {
			if (ManageWakeLocks.hasLock()) {
				if (mInterval > 0) {
					mAlarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + mInterval, mPendingIntent);}
				ManageWakeLocks.release();}}}
	
    @Override
    public void onCreate() {
        super.onCreate();
		IntentFilter intentfilter = new IntentFilter();
		intentfilter.addAction(Intent.ACTION_SCREEN_OFF);
		intentfilter.addAction(Intent.ACTION_SCREEN_ON);
		intentfilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
		intentfilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
		registerReceiver(mReceiver, intentfilter);
		mAlarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
		Intent i = new Intent(this, BootReceiver.class);
		i.setAction(WAKE_SERVICE);
		mPendingIntent = PendingIntent.getBroadcast(this, 0, i, 0);
		mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		SharedPreferences prefs = (SharedPreferences) getSharedPreferences(getString(R.string.key_preferences), WapdroidService.MODE_PRIVATE);
		// initialize preferences, updated by UI
		mInterval = Integer.parseInt((String) prefs.getString(getString(R.string.key_interval), "0"));
		mNotify = prefs.getBoolean(getString(R.string.key_notify), false);
		if (mNotify) {
			mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);}
		mVibrate = prefs.getBoolean(getString(R.string.key_vibrate), false);
		mLed = prefs.getBoolean(getString(R.string.key_led), false);
		mRingtone = prefs.getBoolean(getString(R.string.key_ringtone), false);
		prefs = null;
		mDbHelper = new WapdroidDbAdapter(this);
		mTeleManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);}
    
    @Override
    public void onDestroy() {
    	super.onDestroy();
    	if (mReceiver != null) {
    		unregisterReceiver(mReceiver);
    		mReceiver = null;}
		mTeleManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);}
    
    private void checkLocation(CellLocation location) {
    	// check that the DB is open
    	if (mDbHelper != null) {
    		if (mTeleManager.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM) {
    			mCID = ((GsmCellLocation) location).getCid();
    			processLocation();}
	       	else if (mTeleManager.getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA) {
	    		// check the phone type, cdma is not available before API 2.0, so use a wrapper
	       		try {
	       			mCID = (new CdmaCellLocationWrapper(location)).getBaseStationId();}
	       		catch (Throwable t) {
	       			mCID = -1;}
	       		if (mCID > 0) {
	       			processLocation();}}}}
    
    private void processLocation() {
       	if (mCID > 0) {
    		mDbHelper.open();
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
					if (mNotify) {
						CharSequence contentTitle = getString(R.string.label_WIFI) + " " + getString(mInRange ? R.string.label_enabled : R.string.label_disabled);
					   	Notification notification = new Notification((mInRange ? R.drawable.statuson : R.drawable.status), contentTitle, System.currentTimeMillis());
					   	Intent i = new Intent(getBaseContext(), WapdroidService.class);
						PendingIntent contentIntent = PendingIntent.getActivity(getBaseContext(), 0, i, 0);
					   	notification.setLatestEventInfo(getBaseContext(), contentTitle, getString(R.string.app_name), contentIntent);
					   	if (mVibrate) {
					   		notification.defaults |= Notification.DEFAULT_VIBRATE;}
					   	if (mLed) {
					   		notification.defaults |= Notification.DEFAULT_LIGHTS;}
					   	if (mRingtone) {
					   		notification.defaults |= Notification.DEFAULT_SOUND;}
						mNotificationManager.notify(NOTIFY_ID, notification);}}}
	    	mDbHelper.close();}}
    
    private void setWifiInfo() {
		mSSID = mWifiManager.getConnectionInfo().getSSID();
		mBSSID = mWifiManager.getConnectionInfo().getBSSID();}
    
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
		if (mWifiIsEnabled && (mSSID != null) && (mBSSID != null) && (mCID > 0) && (mDbHelper != null)) {
			mDbHelper.open();
	    	updateRange();
	    	mDbHelper.close();}}}