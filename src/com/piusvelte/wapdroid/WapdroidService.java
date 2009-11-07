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
import android.util.Log;

public class WapdroidService extends Service {
	private static int NOTIFY_ID = 1;
	private static final String WIFI_CHANGE = WifiManager.WIFI_STATE_CHANGED_ACTION;
	private static final String NETWORK_CHANGE = WifiManager.NETWORK_STATE_CHANGED_ACTION;
	private static final int WIFI_STATE_ENABLING = WifiManager.WIFI_STATE_ENABLING;
	private static final int WIFI_STATE_ENABLED = WifiManager.WIFI_STATE_ENABLED;
	private static final int WIFI_STATE_UNKNOWN = WifiManager.WIFI_STATE_UNKNOWN;
	public static final String WAKE_SERVICE = "com.piusvelte.wapdroid.WAKE_SERVICE";
	public static final String SCREEN_ON = "com.piusvelte.wapdroid.SCREEN_ON";
	public static final String PREF_FILE_NAME = "wapdroid";
	public static final String PREFERENCE_MANAGE = "manageWifi";
	public static final String PREFERENCE_NOTIFY = "notify";
	public static final String PREFERENCE_VIBRATE = "vibrate";
	public static final String PREFERENCE_LED = "led";
	public static final String PREFERENCE_RINGTONE = "ringtone";
	private static final String TAG = "WapdroidService";
	private WapdroidDbAdapter mDbHelper;
	private NotificationManager mNotificationManager;
	private TelephonyManager mTeleManager;
	private String mSSID = null, mMNC = null, mMCC = null;
	private List<NeighboringCellInfo> mNeighboringCells;
	private WifiManager mWifiManager;
	private WifiInfo mWifiInfo;
	private int mCID = -1, mLAC = -1, mWifiState, mInterval = 300000; // 5min interval
	private boolean mWifiIsEnabled = false, mSetWifi = false;
	private IWapdroidUI mWapdroidUI;
	private SharedPreferences mPreferences;
	private AlarmManager mAlarmManager;
	private PendingIntent mPendingIntent;
	
    private final IWapdroidService.Stub mWapdroidService = new IWapdroidService.Stub() {		
		public void setCallback(IBinder mWapdroidUIBinder) throws RemoteException {
			// UI started or stopped
			if (mWapdroidUIBinder != null) {
				mWapdroidUI = IWapdroidUI.Stub.asInterface(mWapdroidUIBinder);
	        	if ((mWapdroidUI != null) && (mCID > 0)) {
	        		try {
		        		mWapdroidUI.setCellLocation((String) "" + mCID, (String) "" + mLAC, (String) "" + mMNC, (String) "" + mMCC);}
	        		catch (RemoteException e) {}}}
			else {
				mWapdroidUI = null;}}};
	
	private final PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
    	public void onCellLocationChanged(CellLocation location) {
    		Log.v(TAG,"onCellLocationChanged, wifi currently "+(mWifiIsEnabled?"on":"off"));
    		GsmCellLocation cell = (GsmCellLocation) location;
    		mCID = cell.getCid();
    		mLAC = cell.getLac();
    		mMNC = mTeleManager.getNetworkOperatorName();
    		mMCC = mTeleManager.getNetworkCountryIso();
    		mNeighboringCells = mTeleManager.getNeighboringCellInfo();
        	if ((mWapdroidUI != null) && (mCID > 0)) {
        		try {
	        		mWapdroidUI.setCellLocation((String) "" + mCID, (String) "" + mLAC, (String) "" + mMNC, (String) "" + mMCC);}
        		catch (RemoteException e) {}}
    		if (mCID > 0) {
    			if (mWifiIsEnabled && (mSSID != null)) {
    				updateRange();
    				stopSelf();}
    			else {
    				boolean mInRange = false;
    				if (mDbHelper.cellInRange(mCID)) {
    					mInRange = true;
   						int mNeighborCID;
   						for (NeighboringCellInfo n : mNeighboringCells) {
   							mNeighborCID = n.getCid();
   							if (mInRange && (mNeighborCID > 0)) {
   								mInRange = mDbHelper.cellInRange(mNeighborCID);}}}
    				if ((mInRange && !mWifiIsEnabled && (mWifiState != WIFI_STATE_ENABLING)) || (!mInRange && mWifiIsEnabled)) {
    					mSetWifi = true;
    	        		Log.v(TAG,"wifi "+(mInRange?"on":"off"));
    					mWifiManager.setWifiEnabled(mInRange);}
    				else {
    	    			stopSelf();}}}}};
    
	private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
    		if (intent.getAction().equals(WIFI_CHANGE)) {
        		Log.v(TAG,WIFI_CHANGE);
    			int mWifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 4);
    	    	if (mWifiState != WIFI_STATE_UNKNOWN) {
    	    		boolean notify = mPreferences.getBoolean(PREFERENCE_NOTIFY, true) && (mWifiIsEnabled ^ (mWifiState == WIFI_STATE_ENABLED));
    	    		mWifiIsEnabled = (mWifiState == WIFI_STATE_ENABLED);
    	    		if (notify && mSetWifi) {
    	    				int icon = mWifiIsEnabled ? R.drawable.statuson : R.drawable.status;
    	    				CharSequence contentTitle = getString(R.string.label_WIFI) + " " + getString(mWifiIsEnabled ? R.string.label_enabled : R.string.label_disabled);
    	    			  	long when = System.currentTimeMillis();
    	    			   	Notification notification = new Notification(icon, contentTitle, when);
    	    			   	Intent i = new Intent(context, WapdroidService.class);
    	    				PendingIntent contentIntent = PendingIntent.getActivity(context, 0, i, 0);
    	    			   	notification.setLatestEventInfo(context, contentTitle, getString(R.string.app_name), contentIntent);
    	    			   	if (mPreferences.getBoolean(PREFERENCE_VIBRATE, false)) {
    	    			   		notification.defaults |= Notification.DEFAULT_VIBRATE;}
    	    			   	if (mPreferences.getBoolean(PREFERENCE_LED, false)) {
    	    			   		notification.defaults |= Notification.DEFAULT_LIGHTS;}
    	    			   	if (mPreferences.getBoolean(PREFERENCE_RINGTONE, false)) {
    	    			   		notification.defaults |= Notification.DEFAULT_SOUND;}
    	    				mNotificationManager.notify(NOTIFY_ID, notification);}
    	    		if (!mWifiIsEnabled) {
    	    			mSSID = null;}}
    			wifiChanged();
    			if (mSetWifi) {
    				mSetWifi = false;
    				stopSelf();}}
    		else if (intent.getAction().equals(NETWORK_CHANGE)) {
        		Log.v(TAG,NETWORK_CHANGE);
    			NetworkInfo mNetworkInfo = (NetworkInfo) intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
    	    	if (mNetworkInfo.isConnected()) {
    	    		mWifiInfo = mWifiManager.getConnectionInfo();
    	    		mSSID = mWifiInfo.getSSID();}
    	    	else {
    	    		mSSID = null;}
    	    	wifiChanged();}}};

	@Override
	public IBinder onBind(Intent intent) {
		// stop the Alarm if UI binds, it'll be reset in onDestroy, if appropriate
		mAlarmManager.cancel(mPendingIntent);
		return mWapdroidService;}
	
	@Override
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);}
	
    @Override
    public void onCreate() {
        super.onCreate();
		mAlarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
		Intent i = new Intent(this, WapdroidServiceManager.class);
		i.setAction(WAKE_SERVICE);
		mPendingIntent = PendingIntent.getBroadcast(this, 0, i, 0);
		mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		mWifiInfo = mWifiManager.getConnectionInfo();
		mWifiIsEnabled = mWifiManager.isWifiEnabled();
		mPreferences = (SharedPreferences) getSharedPreferences(PREF_FILE_NAME, WapdroidUI.MODE_PRIVATE);
		mDbHelper = new WapdroidDbAdapter(this);
		mDbHelper.open();
		IntentFilter intentfilter = new IntentFilter();
		intentfilter.addAction(WIFI_CHANGE);
		intentfilter.addAction(NETWORK_CHANGE);
		registerReceiver(mReceiver, intentfilter);
		mTeleManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		mTeleManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CELL_LOCATION);}
    
    @Override
    public void onDestroy() {
    	super.onDestroy();
    	mNotificationManager.cancel(NOTIFY_ID);
    	unregisterReceiver(mReceiver);
    	mTeleManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
    	if (mPreferences.getBoolean(PREFERENCE_MANAGE, false)) {
       		mAlarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + mInterval, mPendingIntent);}
		ManageWakeLocks.release();}
    
    private void updateRange() {
		mDbHelper.updateCellRange(mSSID, mCID);
		int cid;
		for (NeighboringCellInfo n : mNeighboringCells) {
			cid = n.getCid();
			if (cid > 0) {
				mDbHelper.updateCellRange(mSSID, cid);}}}
	
	private void wifiChanged() {
		if (mWifiIsEnabled && (mSSID != null) && (mCID > 0)) {
	    	updateRange();}}}