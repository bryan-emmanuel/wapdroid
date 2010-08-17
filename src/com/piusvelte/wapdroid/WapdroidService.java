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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.net.wifi.SupplicantState;
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
	public static final String WAKE_SERVICE = "com.piusvelte.wapdroid.WAKE_SERVICE";
	public static final String TAG = "Wapdroid";
	public static final int LISTEN_SIGNAL_STRENGTHS = 256;
	public static final int PHONE_TYPE_CDMA = 2;
	private static final int START_STICKY = 1;
	private NotificationManager mNotificationManager;
	public TelephonyManager mTeleManager;
	private List<NeighboringCellInfo> mNeighboringCells;
	public WifiManager mWifiManager;
	private int mCid = UNKNOWN_CID,
	mLac = UNKNOWN_CID,
	mRssi = UNKNOWN_RSSI,
	mLastWifiState = WifiManager.WIFI_STATE_UNKNOWN,
	mNotifications;
	public int mInterval,
	mBatteryLimit,
	mLastBattPerc = 0;
	private boolean mPersistentStatus;
	public boolean mManageWifi,
//	mRelease = false,
	mManualOverride,
	mLastScanEnableWifi;
	private static boolean mApi7;
	public AlarmManager mAlarmMgr;
	public PendingIntent mPendingIntent;
	public IWapdroidUI mWapdroidUI;
	private BroadcastReceiver mReceiver;
	public PhoneStateListener mPhoneListener;
	// db variables
	public static final String TABLE_ID = "_id";
	public static final String TABLE_CODE = "code";
	public static final String TABLE_NETWORKS = "networks";
	public static final String NETWORKS_SSID = "SSID";
	public static final String NETWORKS_BSSID = "BSSID";
	public static final String TABLE_CELLS = "cells";
	public static final String CELLS_CID = "CID";
	public static final String STATUS = "status";
	public static final int FILTER_ALL = 0;
	public static final int FILTER_INRANGE = 1;
	public static final int FILTER_OUTRANGE = 2;
	public static final int FILTER_CONNECTED = 3;
	public static final String TABLE_LOCATIONS = "locations";
	public static final String LOCATIONS_LAC = "LAC";
	public static final String TABLE_PAIRS = "pairs";
	public static final String PAIRS_CELL = "cell";
	public static final String PAIRS_NETWORK = "network";
	public static final String CELLS_LOCATION = "location";
	public static final String PAIRS_RSSI_MIN = "RSSI_min";
	public static final String PAIRS_RSSI_MAX = "RSSI_max";
	public static final int UNKNOWN_CID = -1;
	public static final int UNKNOWN_RSSI = 99;
	private SQLiteDatabase mDb;
	private DatabaseHelper mDbHelper;

	private final IWapdroidService.Stub mWapdroidService = new IWapdroidService.Stub() {
		public void updatePreferences(boolean manage, int interval, boolean notify, boolean vibrate, boolean led, boolean ringtone, boolean batteryOverride, int batteryPercentage, boolean persistent_status)
		throws RemoteException {
			mManageWifi = manage;
			mInterval = interval;
			int limit = batteryOverride ? batteryPercentage : 0;
			if (limit != mBatteryLimit) mBatteryLimit = limit;
			mNotifications = 0;
			if (vibrate) mNotifications |= Notification.DEFAULT_VIBRATE;
			if (led) mNotifications |= Notification.DEFAULT_LIGHTS;
			if (ringtone) mNotifications |= Notification.DEFAULT_SOUND;
			if ((mManageWifi ^ manage) || ((mNotificationManager != null) ^ notify)) {
				mPersistentStatus = persistent_status;
				if (manage && notify) {
					if (mNotificationManager == null) mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
					createNotification((mLastWifiState == WifiManager.WIFI_STATE_ENABLED), false);
				} else if (mNotificationManager != null) {
					mNotificationManager.cancel(NOTIFY_ID);
					mNotificationManager = null;
				}
			} else if (mPersistentStatus ^ persistent_status) {
				// changed the status icon persistence
				mPersistentStatus = persistent_status;
				if (mPersistentStatus) {
					if (manage && notify) {
						if (mNotificationManager == null) mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
						createNotification((mLastWifiState == WifiManager.WIFI_STATE_ENABLED), false);						
					}
				} else if (mNotificationManager != null) mNotificationManager.cancel(NOTIFY_ID);
			}
		}

		public void setCallback(IBinder mWapdroidUIBinder)
		throws RemoteException {
			if (mWapdroidUIBinder != null) {
				if (ManageWakeLocks.hasLock()) ManageWakeLocks.release();
				mWapdroidUI = IWapdroidUI.Stub.asInterface(mWapdroidUIBinder);
				if (mWapdroidUI != null) {
					// may have returned from wifi systems
					mManualOverride = false;
					SharedPreferences sp = (SharedPreferences) getSharedPreferences(getString(R.string.key_preferences), WapdroidService.MODE_PRIVATE);
					SharedPreferences.Editor spe = sp.edit();
					spe.putBoolean(getString(R.string.key_manual_override), mManualOverride);
					spe.commit();
					// listen to phone changes if a low battery condition caused this to stop
					if (mLastBattPerc < mBatteryLimit) mTeleManager.listen(mPhoneListener, (PhoneStateListener.LISTEN_CELL_LOCATION | PhoneStateListener.LISTEN_SIGNAL_STRENGTH | LISTEN_SIGNAL_STRENGTHS));
					updateUI();
				} else if (mLastBattPerc < mBatteryLimit) mTeleManager.listen(mPhoneListener, PhoneStateListener.LISTEN_NONE);
			}
		}

		public void manualOverride() throws RemoteException {
			// if the service is killed, such as in a low memory situation, this override will be lost
			// store in preferences for persistence
			mManualOverride = true;
			SharedPreferences sp = (SharedPreferences) getSharedPreferences(getString(R.string.key_preferences), WapdroidService.MODE_PRIVATE);
			SharedPreferences.Editor spe = sp.edit();
			spe.putBoolean(getString(R.string.key_manual_override), mManualOverride);
			spe.commit();
		}
	};

	// add onSignalStrengthsChanged for api >= 7
	static {
		try {
			Class.forName("android.telephony.SignalStrength");
			mApi7 = true;
		} catch (Exception ex) {
			Log.e(TAG, "api < 7, " + ex);
			mApi7 = false;
		}
	}

	private static Method mNciReflectGetLac;

	static {
		getLacReflection();
	}

	private static void getLacReflection() {
		try {
			mNciReflectGetLac = android.telephony.NeighboringCellInfo.class.getMethod("getLac", new Class[] {} );
		} catch (NoSuchMethodException nsme) {
			Log.e(TAG, "api < 5, " + nsme);
		}
	}

	private static int nciGetLac(NeighboringCellInfo nci) throws IOException {
		int lac;
		try {
			lac = (Integer) mNciReflectGetLac.invoke(nci);
		} catch (InvocationTargetException ite) {
			lac = UNKNOWN_CID;
			Throwable cause = ite.getCause();
			if (cause instanceof IOException) throw (IOException) cause;
			else if (cause instanceof RuntimeException) throw (RuntimeException) cause;
			else if (cause instanceof Error) throw (Error) cause;
			else throw new RuntimeException(ite);
		} catch (IllegalAccessException ie) {
			lac = UNKNOWN_CID;
			Log.e(TAG, "unexpected " + ie);
		}
		return lac;
	}

	@Override
	public IBinder onBind(Intent intent) {
		mAlarmMgr.cancel(mPendingIntent);
		ManageWakeLocks.release();
		return mWapdroidService;
	}

	@Override
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);
		init();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		super.onStart(intent, startId);
		init();
		return START_STICKY;
	}

	private void init() {
		/*
		 * started on boot, wake, screen_on, ui, settings
		 * boot and wake will wakelock and should set the alarm,
		 * others should release the lock and cancel the alarm
		 */
		// if wifi or network receiver took a lock, and the alarm went off, stop them from releasing the lock
		// receiver cancels alarm, this is obsolete
//		mRelease = false;
		// initialize the cell info
		getCellInfo(mTeleManager.getCellLocation());
	}

	@Override
	public void onCreate() {
		super.onCreate();
		/*
		 * only register the receiver on intents that are relevant
		 * listen to network when: wifi is enabled
		 * listen to wifi when: screenon
		 * listen to battery when: disabling on battery level, UI is in foreground
		 */
		Intent i = new Intent(this, Receiver.class);
		i.setAction(WAKE_SERVICE);
		mPendingIntent = PendingIntent.getBroadcast(this, 0, i, 0);
		SharedPreferences sp = (SharedPreferences) getSharedPreferences(getString(R.string.key_preferences), WapdroidService.MODE_PRIVATE);
		// initialize preferences, updated by UI
		mManageWifi = sp.getBoolean(getString(R.string.key_manageWifi), false);
		mInterval = Integer.parseInt((String) sp.getString(getString(R.string.key_interval), "30000"));
		if (sp.getBoolean(getString(R.string.key_notify), false)) {
			mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			mPersistentStatus = sp.getBoolean(getString(R.string.key_persistent_status), false);
			mNotifications = 0;
			if (sp.getBoolean(getString(R.string.key_vibrate), false)) mNotifications |= Notification.DEFAULT_VIBRATE;
			if (sp.getBoolean(getString(R.string.key_led), false)) mNotifications |= Notification.DEFAULT_LIGHTS;
			if (sp.getBoolean(getString(R.string.key_ringtone), false)) mNotifications |= Notification.DEFAULT_SOUND;
		}
		mBatteryLimit = sp.getBoolean(getString(R.string.key_battery_override), false) ? Integer.parseInt((String) sp.getString(getString(R.string.key_battery_percentage), "30")) : 0;
		mManualOverride = sp.getBoolean(getString(R.string.key_manual_override), false);
		mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		mAlarmMgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
		mDbHelper = new DatabaseHelper(this);
		try {
			mDb = mDbHelper.getWritableDatabase();
		} catch (SQLException se) {
			Log.e(TAG,"unexpected " + se);
		}
		wifiStateChanged(mWifiManager.getWifiState());
		// to help avoid hysteresis, make sure that at least 2 consecutive scans were in/out of range
		mLastScanEnableWifi = (mLastWifiState == WifiManager.WIFI_STATE_ENABLED);
		// the ssid from wifimanager may not be null, even if disconnected, so check against the supplicant state
		networkStateChanged();
		IntentFilter f = new IntentFilter();
		f.addAction(Intent.ACTION_BATTERY_CHANGED);
		f.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
		f.addAction(Intent.ACTION_SCREEN_OFF);
		f.addAction(Intent.ACTION_SCREEN_ON);
		f.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
		mReceiver = new Receiver();
		registerReceiver(mReceiver, f);
		mTeleManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		mTeleManager.listen(mPhoneListener = (mApi7 ? (new PhoneListenerApi7(this)) : (new PhoneListenerApi3(this))), (PhoneStateListener.LISTEN_CELL_LOCATION | PhoneStateListener.LISTEN_SIGNAL_STRENGTH | LISTEN_SIGNAL_STRENGTHS));
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (mReceiver != null) {
			unregisterReceiver(mReceiver);
			mReceiver = null;
		}
		mTeleManager.listen(mPhoneListener, PhoneStateListener.LISTEN_NONE);
		if (mDbHelper != null) {
			if (mDb.isOpen()) mDb.close();
			mDbHelper.close();
		}
		if (mNotificationManager != null) mNotificationManager.cancel(NOTIFY_ID);
		if (ManageWakeLocks.hasLock()) ManageWakeLocks.release();
	}

//	public void release() {
//		if (ManageWakeLocks.hasLock()) {
//			if (mInterval > 0) mAlarmMgr.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + mInterval, mPendingIntent);
//			// if sleeping, re-initialize phone info
//			mCid = UNKNOWN_CID;
//			mLac = UNKNOWN_CID;
//			mRssi = UNKNOWN_RSSI;
//			ManageWakeLocks.release();
//		}
//	}

	private void updateUI() {
		String cells = "(" + CELLS_CID + "=" + Integer.toString(mCid) + " and (" + LOCATIONS_LAC + "=" + Integer.toString(mLac) + " or " + CELLS_LOCATION + "=" + UNKNOWN_CID + ")"
		+ ((mRssi == UNKNOWN_RSSI) ? ")" : " and (((" + PAIRS_RSSI_MIN + "=" + UNKNOWN_RSSI + ") or (" + PAIRS_RSSI_MIN + "<=" + Integer.toString(mRssi) + ")) and (" + PAIRS_RSSI_MAX + ">=" + Integer.toString(mRssi) + ")))");
		if ((mNeighboringCells != null) && !mNeighboringCells.isEmpty()) {
			for (NeighboringCellInfo nci : mNeighboringCells) {
				int nci_rssi = (nci.getRssi() != UNKNOWN_RSSI) && (mTeleManager.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM) ? 2 * nci.getRssi() - 113 : nci.getRssi(), nci_lac;
				if (mNciReflectGetLac != null) {
					/* feature is supported */
					try {
						nci_lac = nciGetLac(nci);
					} catch (IOException ie) {
						nci_lac = UNKNOWN_CID;
						Log.e(TAG, "unexpected " + ie);
					}
				} else nci_lac = UNKNOWN_CID;
				cells += " or (" + CELLS_CID + "=" + Integer.toString(nci.getCid())
				+ " and (" + LOCATIONS_LAC + "=" + nci_lac + " or " + CELLS_LOCATION + "=" + UNKNOWN_CID + ")"
				+ ((nci_rssi == UNKNOWN_RSSI) ? ")" : " and (((" + PAIRS_RSSI_MIN + "=" + UNKNOWN_RSSI + ") or (" + PAIRS_RSSI_MIN + "<=" + Integer.toString(nci_rssi) + ")) and (" + PAIRS_RSSI_MAX + ">=" + Integer.toString(nci_rssi) + ")))");
			}
		}
		try {
			mWapdroidUI.setOperator(mTeleManager.getNetworkOperator());
			mWapdroidUI.setCellInfo(mCid, mLac);
			mWapdroidUI.setWifiInfo(mLastWifiState, mWifiManager.getConnectionInfo().getSSID(), mWifiManager.getConnectionInfo().getBSSID());
			mWapdroidUI.setSignalStrength(mRssi);
			mWapdroidUI.setCells(cells);
			mWapdroidUI.setBattery(mLastBattPerc);
		} catch (RemoteException e) {}
	}

	public void getCellInfo(CellLocation location) {
		mNeighboringCells = mTeleManager.getNeighboringCellInfo();
		if (location != null) {
			if (mTeleManager.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM) {
				mCid = ((GsmCellLocation) location).getCid();
				mLac = ((GsmCellLocation) location).getLac();
			} else if (mTeleManager.getPhoneType() == PHONE_TYPE_CDMA) {
				// check the phone type, cdma is not available before API 2.0, so use a wrapper
				try {
					CdmaCellLocation cdma = new CdmaCellLocation(location);
					mCid = cdma.getBaseStationId();
					mLac = cdma.getNetworkId();
				} catch (Throwable t) {
					Log.e(TAG, "unexpected " + t);
					mCid = UNKNOWN_CID;
					mLac = UNKNOWN_CID;
				}
			}
		} else {
			mCid = UNKNOWN_CID;
			mLac = UNKNOWN_CID;
		}
		if (mCid != UNKNOWN_CID) {
			// allow unknown mRssi, since signalStrengthChanged isn't reliable enough by itself
			signalStrengthChanged(UNKNOWN_RSSI);
			if (mWapdroidUI != null) updateUI();
		}
	}

	public void signalStrengthChanged(int rssi) {
		// keep last known rssi
		if (rssi != UNKNOWN_RSSI) mRssi = rssi;
		if (mWapdroidUI != null) {
			try {
				mWapdroidUI.setSignalStrength(mRssi);
			} catch (RemoteException e) {}
		}
		// initialize enableWifi as mLastScanEnableWifi, so that wakelock is released by default
		boolean enableWifi = mLastScanEnableWifi;
		// allow unknown mRssi, since signalStrengthChanged isn't reliable enough by itself
		// check that the service is in control, and minimum values are set
		if (mManageWifi && (mCid != UNKNOWN_CID) && mDb.isOpen()) {
			enableWifi = cellInRange(mCid, mLac, mRssi);
			// if connected, only update the range
			if (mWifiManager.getConnectionInfo().getSupplicantState() == SupplicantState.COMPLETED) updateRange();
			// always allow disabling, but only enable if above the battery limit
			else if (!enableWifi || (mLastBattPerc >= mBatteryLimit)) {
				if (enableWifi) {
					// check neighbors if it appears that we're in range, for both enabling and disabling
					for (NeighboringCellInfo nci : mNeighboringCells) {
						int nci_cid = nci.getCid() > 0 ? nci.getCid() : UNKNOWN_CID, nci_rssi = (nci.getRssi() != UNKNOWN_RSSI) && (mTeleManager.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM) ? 2 * nci.getRssi() - 113 : nci.getRssi(), nci_lac;
						if (mNciReflectGetLac != null) {
							/* feature is supported */
							try {
								nci_lac = nciGetLac(nci);
							} catch (IOException ie) {
								nci_lac = UNKNOWN_CID;
								Log.e(TAG, "unexpected " + ie);
							}
						} else nci_lac = UNKNOWN_CID;
						// break on out of range result
						if (nci_cid != UNKNOWN_CID) enableWifi = cellInRange(nci_cid, nci_lac, nci_rssi);
						if (!enableWifi) break;
					}
				}
				// toggle if ((enable & not(enabled or enabling)) or (disable and (enabled or enabling))) and (disable and not(disabling))
				// to avoid hysteresis when on the edge of a network, require 2 consecutive, identical results before affecting a change
				if (!mManualOverride && (enableWifi ^ ((((mLastWifiState == WifiManager.WIFI_STATE_ENABLED) || (mLastWifiState == WifiManager.WIFI_STATE_ENABLING))))) && (enableWifi ^ (!enableWifi && (mLastWifiState != WifiManager.WIFI_STATE_DISABLING))) && (mLastScanEnableWifi == enableWifi)) {
					Log.v(TAG,"TOGGLE WIFI " + (enableWifi ? "ON" : "OFF"));
					mWifiManager.setWifiEnabled(enableWifi);
				}
			}
		}
		// only release the service if it doesn't appear that we're entering or leaving a network
//		if (enableWifi == mLastScanEnableWifi) release();
		if (enableWifi == mLastScanEnableWifi) {
			if (ManageWakeLocks.hasLock()) {
				if (mInterval > 0) mAlarmMgr.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + mInterval, mPendingIntent);
				// if sleeping, re-initialize phone info
				mCid = UNKNOWN_CID;
				mLac = UNKNOWN_CID;
				mRssi = UNKNOWN_RSSI;
				ManageWakeLocks.release();
			}
		}
		else mLastScanEnableWifi = enableWifi;
	}

	private void updateRange() {
		if ((mWifiManager.getConnectionInfo().getSSID() != null) && (mWifiManager.getConnectionInfo().getBSSID() != null)) {
			int network = UNKNOWN_CID;
			String ssid_orig, bssid_orig;
			ContentValues cv = new ContentValues();
			// upgrading, BSSID may not be set yet
			Cursor c = mDb.rawQuery("select " + TABLE_ID + ", " + NETWORKS_SSID + ", " + NETWORKS_BSSID + " from " + TABLE_NETWORKS + " where " + NETWORKS_BSSID + "=\"" + mWifiManager.getConnectionInfo().getBSSID() + "\" OR (" + NETWORKS_SSID + "=\"" + mWifiManager.getConnectionInfo().getSSID() + "\" and " + NETWORKS_BSSID + "=\"\")", null);
			if (c.getCount() > 0) {
				c.moveToFirst();
				network = c.getInt(c.getColumnIndex(TABLE_ID));
				ssid_orig = c.getString(c.getColumnIndex(NETWORKS_SSID));
				bssid_orig = c.getString(c.getColumnIndex(NETWORKS_BSSID));
				if (bssid_orig.equals("")) {
					cv.put(NETWORKS_BSSID, mWifiManager.getConnectionInfo().getBSSID());
					mDb.update(TABLE_NETWORKS, cv, TABLE_ID + "=" + network, null);
				} else if (!ssid_orig.equals(mWifiManager.getConnectionInfo().getSSID())) {
					cv.put(NETWORKS_SSID, mWifiManager.getConnectionInfo().getSSID());
					mDb.update(TABLE_NETWORKS, cv, TABLE_ID + "=" + network, null);
				}
			} else {
				cv.put(NETWORKS_SSID, mWifiManager.getConnectionInfo().getSSID());
				cv.put(NETWORKS_BSSID, mWifiManager.getConnectionInfo().getBSSID());
				network = (int) mDb.insert(TABLE_NETWORKS, null, cv);
			}
			c.close();
			createPair(mCid, mLac, network, mRssi);
			for (NeighboringCellInfo nci : mNeighboringCells) {
				int nci_cid = nci.getCid() > 0 ? nci.getCid() : UNKNOWN_CID, nci_rssi = (nci.getRssi() != UNKNOWN_RSSI) && (mTeleManager.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM) ? 2 * nci.getRssi() - 113 : nci.getRssi(), nci_lac;
				if (mNciReflectGetLac != null) {
					/* feature is supported */
					try {
						nci_lac = nciGetLac(nci);
					} catch (IOException ie) {
						nci_lac = UNKNOWN_CID;
						Log.e(TAG, "unexpected " + ie);
					}
				} else nci_lac = UNKNOWN_CID;
				if (nci_cid != UNKNOWN_CID) createPair(nci_cid, nci_lac, network, nci_rssi);
			}
		}
	}

	public void networkStateChanged() {
		/*
		 * get network state
		 * the ssid from wifimanager may not be null, even if disconnected, so taking boolean connected
		 * when network connected, unregister wifi receiver
		 * when network disconnected, register wifi receiver
		 */
		if ((mWifiManager.getConnectionInfo().getSupplicantState() == SupplicantState.COMPLETED) && (mCid != UNKNOWN_CID) && mDb.isOpen()) updateRange();
		if (mWapdroidUI != null) {
			try {
				mWapdroidUI.setWifiInfo(mLastWifiState, mWifiManager.getConnectionInfo().getSSID(), mWifiManager.getConnectionInfo().getBSSID());
			} catch (RemoteException e) {}
		}
	}

	private void createNotification(boolean enabled, boolean update) {
//		if (mManageWifi) {
			CharSequence contentTitle = getString(R.string.label_WIFI) + " " + getString(enabled ? R.string.label_enabled : R.string.label_disabled);
			Notification notification = new Notification((enabled ? R.drawable.statuson : R.drawable.scanning), contentTitle, System.currentTimeMillis());
			Intent i = new Intent(getBaseContext(), WapdroidUI.class);
			PendingIntent contentIntent = PendingIntent.getActivity(getBaseContext(), 0, i, 0);
			notification.setLatestEventInfo(getBaseContext(), contentTitle, getString(R.string.app_name), contentIntent);
			if (mPersistentStatus) notification.flags |= Notification.FLAG_NO_CLEAR;
			if (update) notification.defaults |= mNotifications;
			mNotificationManager.notify(NOTIFY_ID, notification);
//		}
	}

	public void wifiStateChanged(int state) {
		/*
		 * get wifi state
		 * initially, lastWifiState is unknown, otherwise state is evaluated either enabled or not
		 * when wifi enabled, register network receiver
		 * when wifi not enabled, unregister network receiver
		 */
		if (state != WifiManager.WIFI_STATE_UNKNOWN) {
			// notify, when onCreate (no led, ringtone, vibrate), or a change to enabled or disabled
			if ((mNotificationManager != null)
					&& ((mLastWifiState == WifiManager.WIFI_STATE_UNKNOWN)
							|| ((state == WifiManager.WIFI_STATE_DISABLED) && (mLastWifiState != WifiManager.WIFI_STATE_DISABLED))
							|| ((state == WifiManager.WIFI_STATE_ENABLED) && (mLastWifiState != WifiManager.WIFI_STATE_ENABLED)))) createNotification((state == WifiManager.WIFI_STATE_ENABLED), (mLastWifiState != WifiManager.WIFI_STATE_UNKNOWN));
			mLastWifiState = state;
			if (mWapdroidUI != null) {
				try {
					mWapdroidUI.setWifiInfo(mLastWifiState, mWifiManager.getConnectionInfo().getSSID(), mWifiManager.getConnectionInfo().getBSSID());
				} catch (RemoteException e) {}
			}
		}
		// a lock was only needed to send the notification, no cell changes need to be evaluated until a network state change occurs
		if (ManageWakeLocks.hasLock()) {
			if (mInterval > 0) mAlarmMgr.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + mInterval, mPendingIntent);
			// if sleeping, re-initialize phone info
			mCid = UNKNOWN_CID;
			mLac = UNKNOWN_CID;
			mRssi = UNKNOWN_RSSI;
			ManageWakeLocks.release();
		}
	}

	public void createPair(int cid, int lac, int network, int rssi) {
		int cell, pair, location;
		// select or insert location
		if (lac > 0) {
			Cursor c = mDb.rawQuery("select " + TABLE_ID + " from " + TABLE_LOCATIONS + " where " + LOCATIONS_LAC + "=" + lac, null);
			if (c.getCount() > 0) {
				c.moveToFirst();
				location = c.getInt(c.getColumnIndex(TABLE_ID));
			} else {
				ContentValues cv = new ContentValues();
				cv.put(LOCATIONS_LAC, lac);
				location = (int) mDb.insert(TABLE_LOCATIONS, null, cv);
			}
			c.close();
		} else location = UNKNOWN_CID;
		// if location==-1, then match only on cid, otherwise match on location or -1
		// select or insert cell
		Cursor c = mDb.rawQuery("select " + TABLE_ID + ", " + CELLS_LOCATION + " from " + TABLE_CELLS + " where " + CELLS_CID + "=" + cid + (location == UNKNOWN_CID ? "" : " and (" + CELLS_LOCATION + "=" + UNKNOWN_CID + " or " + CELLS_LOCATION + "=" + location + ")"), null);
		if (c.getCount() > 0) {
			c.moveToFirst();
			cell = c.getInt(c.getColumnIndex(TABLE_ID));
			if ((location != UNKNOWN_CID) && (c.getInt(c.getColumnIndex(CELLS_LOCATION)) == UNKNOWN_CID)){
				// update the location
				ContentValues cv = new ContentValues();
				cv.put(CELLS_LOCATION, location);
				mDb.update(TABLE_CELLS, cv, TABLE_ID + "=" + cell, null);
			}
		} else {
			ContentValues cv = new ContentValues();
			cv.put(CELLS_CID, cid);
			cv.put(CELLS_LOCATION, location);
			cell = (int) mDb.insert(TABLE_CELLS, null, cv);
		}
		c.close();
		// select and update or insert pair
		c = mDb.rawQuery("select " + TABLE_ID + ", " + PAIRS_RSSI_MIN + ", " + PAIRS_RSSI_MAX + " from " + TABLE_PAIRS + " where " + PAIRS_CELL + "=" + cell + " and " + PAIRS_NETWORK + "=" + network, null);
		if (c.getCount() > 0) {
			if (rssi != UNKNOWN_RSSI) {
				c.moveToFirst();
				pair = c.getInt(c.getColumnIndex(TABLE_ID));
				int rssi_min = c.getInt(c.getColumnIndex(PAIRS_RSSI_MIN));
				int rssi_max = c.getInt(c.getColumnIndex(PAIRS_RSSI_MAX));
				boolean update = false;
				ContentValues cv = new ContentValues();
				if (rssi_min > rssi) {
					update = true;
					cv.put(PAIRS_RSSI_MIN, rssi);
				} else if ((rssi_max == UNKNOWN_RSSI) || (rssi_max < rssi)) {
					update = true;
					cv.put(PAIRS_RSSI_MAX, rssi);
				}
				if (update) mDb.update(TABLE_PAIRS, cv, TABLE_ID + "=" + pair, null);
			}
		} else {
			ContentValues cv = new ContentValues();
			cv.put(PAIRS_CELL, cell);
			cv.put(PAIRS_NETWORK, network);
			cv.put(PAIRS_RSSI_MIN, rssi);
			cv.put(PAIRS_RSSI_MAX, rssi);
			mDb.insert(TABLE_PAIRS, null, cv);
		}
		c.close();
	}

	public boolean cellInRange(int cid, int lac, int rssi) {
		boolean inRange;
		Cursor c = mDb.rawQuery("select " + TABLE_CELLS + "." + TABLE_ID + " as " + TABLE_ID + ", " + CELLS_LOCATION + (rssi != UNKNOWN_RSSI ? ", (select min(" + PAIRS_RSSI_MIN + ") from " + TABLE_PAIRS + " where " + PAIRS_CELL + "=" + TABLE_CELLS + "." + TABLE_ID + ") as " + PAIRS_RSSI_MIN + ", (select max(" + PAIRS_RSSI_MAX + ") from " + TABLE_PAIRS + " where " + PAIRS_CELL + "=" + TABLE_CELLS + "." + TABLE_ID + ") as " + PAIRS_RSSI_MAX : "") + " from " + TABLE_CELLS + " left outer join " + TABLE_LOCATIONS + " on " + CELLS_LOCATION + "=" + TABLE_LOCATIONS + "." + TABLE_ID + " where "+ CELLS_CID + "=" + cid + " and (" + LOCATIONS_LAC + "=" + lac + " or " + CELLS_LOCATION + "=" + UNKNOWN_CID + ")"
				+ (rssi == UNKNOWN_RSSI ? "" : " and (((" + PAIRS_RSSI_MIN + "=" + UNKNOWN_RSSI + ") or (" + PAIRS_RSSI_MIN + "<=" + rssi + ")) and (" + PAIRS_RSSI_MAX + ">=" + rssi + "))"), null);
		inRange = (c.getCount() > 0);
		if (inRange && (lac > 0)) {
			// check LAC, as this is a new column
			c.moveToFirst();
			if (c.isNull(c.getColumnIndex(CELLS_LOCATION))) {
				// select or insert location
				int location;
				if (lac > 0) {
					Cursor l = mDb.rawQuery("select " + TABLE_ID + " from " + TABLE_LOCATIONS + " where " + LOCATIONS_LAC + "=" + lac, null);
					if (l.getCount() > 0) {
						l.moveToFirst();
						location = l.getInt(l.getColumnIndex(TABLE_ID));
					} else {
						ContentValues cv = new ContentValues();
						cv.put(LOCATIONS_LAC, lac);
						location = (int) mDb.insert(TABLE_LOCATIONS, null, cv);
					}
					l.close();
				} else location = UNKNOWN_CID;
				ContentValues cv = new ContentValues();
				cv.put(CELLS_LOCATION, location);
				mDb.update(TABLE_CELLS, cv, TABLE_ID + "=" + c.getInt(c.getColumnIndex(TABLE_ID)), null);
			}
		}
		c.close();
		return inRange;
	}
}