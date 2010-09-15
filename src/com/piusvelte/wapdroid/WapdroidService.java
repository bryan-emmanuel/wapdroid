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
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiManager;
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
	public static final String TAG = "Wapdroid";
	public static final String WAKE_SERVICE = "com.piusvelte.wapdroid.WAKE_SERVICE";
	public static final int LISTEN_SIGNAL_STRENGTHS = 256;
	public static final int PHONE_TYPE_CDMA = 2;
	private static final int START_STICKY = 1;
	int mCid = UNKNOWN_CID,
	mLac = UNKNOWN_CID,
	mRssi = UNKNOWN_RSSI,
	mLastWifiState = WifiManager.WIFI_STATE_UNKNOWN,
	mNotifications;
	int mInterval,
	mBatteryLimit,
	mLastBattPerc = 0;
	static int mPhoneType;
	boolean mPersistentStatus;
	boolean mManageWifi,
	mManualOverride,
	mLastScanEnableWifi,
	mNotify;
	String mSsid, mBssid;
	private static boolean mApi7;
	AlarmManager mAlarmMgr;
	PendingIntent mPendingIntent;
	IWapdroidUI mWapdroidUI;
	private BroadcastReceiver mReceiver = new BroadcastReceiver() {
		private static final String BATTERY_EXTRA_LEVEL = "level";
		private static final String BATTERY_EXTRA_SCALE = "scale";
		private static final String BATTERY_EXTRA_PLUGGED = "plugged";

		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(Intent.ACTION_BATTERY_CHANGED)) {
				// override low battery when charging
				if (intent.getIntExtra(BATTERY_EXTRA_PLUGGED, 0) != 0) mBatteryLimit = 0;
				else {
					// unplugged
					SharedPreferences sp = (SharedPreferences) context.getSharedPreferences(context.getString(R.string.key_preferences), WapdroidService.MODE_PRIVATE);
					if (sp.getBoolean(context.getString(R.string.key_battery_override), false)) mBatteryLimit = Integer.parseInt((String) sp.getString(context.getString(R.string.key_battery_percentage), "30"));
				}
				int currentBattPerc = Math.round(intent.getIntExtra(BATTERY_EXTRA_LEVEL, 0) * 100 / intent.getIntExtra(BATTERY_EXTRA_SCALE, 100));
				// check the threshold
				if (mManageWifi && !mManualOverride && (currentBattPerc < mBatteryLimit) && (mLastBattPerc >= mBatteryLimit)) {
					//					mWifiManager.setWifiEnabled(false);
					((WifiManager) getSystemService(Context.WIFI_SERVICE)).setWifiEnabled(false);
					((TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE)).listen(mPhoneListener, PhoneStateListener.LISTEN_NONE);
				} else if ((currentBattPerc >= mBatteryLimit) && (mLastBattPerc < mBatteryLimit)) ((TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE)).listen(mPhoneListener, (PhoneStateListener.LISTEN_CELL_LOCATION | PhoneStateListener.LISTEN_SIGNAL_STRENGTH | LISTEN_SIGNAL_STRENGTHS));
				mLastBattPerc = currentBattPerc;
				if (mWapdroidUI != null) {
					try {
						mWapdroidUI.setBattery(currentBattPerc);
					} catch (RemoteException e) {};
				}
			} else if (intent.getAction().equals(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION)) {
				// grab a lock to wait for a cell change occur
				// a connection was gained or lost
				if (!ManageWakeLocks.hasLock()) {
					ManageWakeLocks.acquire(context);
					mAlarmMgr.cancel(mPendingIntent);
				}
				if (intent.getBooleanExtra(WifiManager.EXTRA_SUPPLICANT_CONNECTED, false)) {
					WifiManager wm = (WifiManager) getSystemService(Context.WIFI_SERVICE);
					mSsid = wm.getConnectionInfo().getSSID();
					mBssid = wm.getConnectionInfo().getBSSID();
				} else {
					mSsid = null;
					mBssid = null;
				}
				getCellInfo(((TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE)).getCellLocation());
				if (mWapdroidUI != null) {
					try {
						mWapdroidUI.setWifiInfo(mLastWifiState, mSsid, mBssid);
					} catch (RemoteException e) {}
				}
			} else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
				mAlarmMgr.cancel(mPendingIntent);
				ManageWakeLocks.release();
				context.startService(new Intent(context, WapdroidService.class));
			} else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
				mManualOverride = false;
				if (mInterval > 0) mAlarmMgr.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + mInterval, mPendingIntent);
			} else if (intent.getAction().equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
				// grab a lock to create notification
				if (!ManageWakeLocks.hasLock()) {
					ManageWakeLocks.acquire(context);
					mAlarmMgr.cancel(mPendingIntent);
				}
				/*
				 * get wifi state
				 * initially, lastWifiState is unknown, otherwise state is evaluated either enabled or not
				 * when wifi enabled, register network receiver
				 * when wifi not enabled, unregister network receiver
				 */
				int state = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 4);
				if (state != WifiManager.WIFI_STATE_UNKNOWN) {
					// notify, when onCreate (no led, ringtone, vibrate), or a change to enabled or disabled
					if (mNotify	&& ((mLastWifiState == WifiManager.WIFI_STATE_UNKNOWN)
									|| ((state == WifiManager.WIFI_STATE_DISABLED) && (mLastWifiState != WifiManager.WIFI_STATE_DISABLED))
									|| ((state == WifiManager.WIFI_STATE_ENABLED) && (mLastWifiState != WifiManager.WIFI_STATE_ENABLED)))) createNotification((state == WifiManager.WIFI_STATE_ENABLED), (mLastWifiState != WifiManager.WIFI_STATE_UNKNOWN));
					mLastWifiState = state;
					if (mWapdroidUI != null) {
						try {
							mWapdroidUI.setWifiInfo(mLastWifiState, mSsid, mBssid);
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
		}
	};
	// db variables
	static final String TABLE_ID = "_id";
	static final String TABLE_CODE = "code";
	static final String TABLE_NETWORKS = "networks";
	static final String NETWORKS_SSID = "SSID";
	static final String NETWORKS_BSSID = "BSSID";
	static final String TABLE_CELLS = "cells";
	static final String CELLS_CID = "CID";
	static final String STATUS = "status";
	static final int FILTER_ALL = 0;
	static final int FILTER_INRANGE = 1;
	static final int FILTER_OUTRANGE = 2;
	static final int FILTER_CONNECTED = 3;
	static final String TABLE_LOCATIONS = "locations";
	static final String LOCATIONS_LAC = "LAC";
	static final String TABLE_PAIRS = "pairs";
	static final String PAIRS_CELL = "cell";
	static final String PAIRS_NETWORK = "network";
	static final String CELLS_LOCATION = "location";
	static final String PAIRS_RSSI_MIN = "RSSI_min";
	static final String PAIRS_RSSI_MAX = "RSSI_max";
	static final int UNKNOWN_CID = -1;
	static final int UNKNOWN_RSSI = 99;
	public static PhoneStateListener mPhoneListener;

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
			if ((mManageWifi ^ manage) || (mNotify ^ notify)) {
				mPersistentStatus = persistent_status;
				if (manage && notify) createNotification((mLastWifiState == WifiManager.WIFI_STATE_ENABLED), false);
			} else if (mPersistentStatus ^ persistent_status) {
				// changed the status icon persistence
				mPersistentStatus = persistent_status;
				if (mPersistentStatus) {
					if (manage && notify) createNotification((mLastWifiState == WifiManager.WIFI_STATE_ENABLED), false);
				} else if (notify) ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).cancel(NOTIFY_ID);
			}
			mNotify = notify;
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
					if (mLastBattPerc < mBatteryLimit) ((TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE)).listen(mPhoneListener, (PhoneStateListener.LISTEN_CELL_LOCATION | PhoneStateListener.LISTEN_SIGNAL_STRENGTH | LISTEN_SIGNAL_STRENGTHS));
					updateUI();
				} else if (mLastBattPerc < mBatteryLimit) ((TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE)).listen(mPhoneListener, PhoneStateListener.LISTEN_NONE);
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
		// initialize the cell info
		getCellInfo(((TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE)).getCellLocation());
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
		Intent i = new Intent(this, BootReceiver.class);
		i.setAction(WAKE_SERVICE);
		mPendingIntent = PendingIntent.getBroadcast(this, 0, i, 0);
		SharedPreferences sp = (SharedPreferences) getSharedPreferences(getString(R.string.key_preferences), WapdroidService.MODE_PRIVATE);
		// initialize preferences, updated by UI
		mManageWifi = sp.getBoolean(getString(R.string.key_manageWifi), false);
		mInterval = Integer.parseInt((String) sp.getString(getString(R.string.key_interval), "30000"));
		mNotify = sp.getBoolean(getString(R.string.key_notify), false);
		if (mNotify) {
			mPersistentStatus = sp.getBoolean(getString(R.string.key_persistent_status), false);
			mNotifications = 0;
			if (sp.getBoolean(getString(R.string.key_vibrate), false)) mNotifications |= Notification.DEFAULT_VIBRATE;
			if (sp.getBoolean(getString(R.string.key_led), false)) mNotifications |= Notification.DEFAULT_LIGHTS;
			if (sp.getBoolean(getString(R.string.key_ringtone), false)) mNotifications |= Notification.DEFAULT_SOUND;
		}
		mBatteryLimit = sp.getBoolean(getString(R.string.key_battery_override), false) ? Integer.parseInt((String) sp.getString(getString(R.string.key_battery_percentage), "30")) : 0;
		mManualOverride = sp.getBoolean(getString(R.string.key_manual_override), false);
		mAlarmMgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
		WifiManager wm = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		int state = wm.getWifiState();
		if (state != WifiManager.WIFI_STATE_UNKNOWN) {
			// notify, when onCreate (no led, ringtone, vibrate), or a change to enabled or disabled
			if (mNotify
					&& ((mLastWifiState == WifiManager.WIFI_STATE_UNKNOWN)
							|| ((state == WifiManager.WIFI_STATE_DISABLED) && (mLastWifiState != WifiManager.WIFI_STATE_DISABLED))
							|| ((state == WifiManager.WIFI_STATE_ENABLED) && (mLastWifiState != WifiManager.WIFI_STATE_ENABLED)))) createNotification((state == WifiManager.WIFI_STATE_ENABLED), (mLastWifiState != WifiManager.WIFI_STATE_UNKNOWN));
			mLastWifiState = state;
		}
		// to help avoid hysteresis, make sure that at least 2 consecutive scans were in/out of range
		mLastScanEnableWifi = (mLastWifiState == WifiManager.WIFI_STATE_ENABLED);
		// the ssid from wifimanager may not be null, even if disconnected, so check against the supplicant state
		if (wm.getConnectionInfo().getSupplicantState() == SupplicantState.COMPLETED) {
			mSsid = wm.getConnectionInfo().getSSID();
			mBssid = wm.getConnectionInfo().getBSSID();
		} else {
			mSsid = null;
			mBssid = null;
		}
		IntentFilter f = new IntentFilter();
		f.addAction(Intent.ACTION_BATTERY_CHANGED);
		f.addAction(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION);
		f.addAction(Intent.ACTION_SCREEN_OFF);
		f.addAction(Intent.ACTION_SCREEN_ON);
		f.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
		registerReceiver(mReceiver, f);
		TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		mPhoneType = tm.getPhoneType();
		tm.listen(mPhoneListener = (mApi7 ? new PhoneStateListener() {
			public void onCellLocationChanged(CellLocation location) {
				// this also calls signalStrengthChanged, since signalStrengthChanged isn't reliable enough by itself
				getCellInfo(location);
			}

			public void onSignalStrengthChanged(int asu) {
				// add cdma support, convert signal from gsm
				signalStrengthChanged((asu > 0) && (asu != UNKNOWN_RSSI) ? (2 * asu - 113) : asu);
			}

			public void onSignalStrengthsChanged(SignalStrength signalStrength) {
				if (mPhoneType == PHONE_TYPE_CDMA) signalStrengthChanged(signalStrength.getCdmaDbm() < signalStrength.getEvdoDbm() ? signalStrength.getCdmaDbm() : signalStrength.getEvdoDbm());
				else signalStrengthChanged((signalStrength.getGsmSignalStrength() > 0) && (signalStrength.getGsmSignalStrength() != UNKNOWN_RSSI) ? (2 * signalStrength.getGsmSignalStrength() - 113) : signalStrength.getGsmSignalStrength());
			}				
		} : (new PhoneStateListener() {
			public void onCellLocationChanged(CellLocation location) {
				// this also calls signalStrengthChanged, since onSignalStrengthChanged isn't reliable enough by itself
				getCellInfo(location);
			}

			public void onSignalStrengthChanged(int asu) {
				// add cdma support, convert signal from gsm
				signalStrengthChanged((asu > 0) && (asu != UNKNOWN_RSSI) ? (2 * asu - 113) : asu);
			}
		})), (PhoneStateListener.LISTEN_CELL_LOCATION | PhoneStateListener.LISTEN_SIGNAL_STRENGTH | LISTEN_SIGNAL_STRENGTHS));
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (mReceiver != null) {
			unregisterReceiver(mReceiver);
			mReceiver = null;
		}
		((TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE)).listen(mPhoneListener, PhoneStateListener.LISTEN_NONE);
		if (mNotify) ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).cancel(NOTIFY_ID);
		if (ManageWakeLocks.hasLock()) ManageWakeLocks.release();
	}

	private void updateUI() {
		String cells = "(" + CELLS_CID + "=" + Integer.toString(mCid) + " and (" + LOCATIONS_LAC + "=" + Integer.toString(mLac) + " or " + CELLS_LOCATION + "=" + UNKNOWN_CID + ")"
		+ ((mRssi == UNKNOWN_RSSI) ? ")" : " and (((" + PAIRS_RSSI_MIN + "=" + UNKNOWN_RSSI + ") or (" + PAIRS_RSSI_MIN + "<=" + Integer.toString(mRssi) + ")) and (" + PAIRS_RSSI_MAX + ">=" + Integer.toString(mRssi) + ")))");
		TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		if ((tm.getNeighboringCellInfo() != null) && !tm.getNeighboringCellInfo().isEmpty()) {
			for (NeighboringCellInfo nci : tm.getNeighboringCellInfo()) {
				int nci_rssi = (nci.getRssi() != UNKNOWN_RSSI) && (mPhoneType == TelephonyManager.PHONE_TYPE_GSM) ? 2 * nci.getRssi() - 113 : nci.getRssi(), nci_lac;
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
			mWapdroidUI.setOperator(((TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE)).getNetworkOperator());
			mWapdroidUI.setCellInfo(mCid, mLac);
			mWapdroidUI.setWifiInfo(mLastWifiState, mSsid, mBssid);
			mWapdroidUI.setSignalStrength(mRssi);
			mWapdroidUI.setCells(cells);
			mWapdroidUI.setBattery(mLastBattPerc);
		} catch (RemoteException e) {}
	}

	final void getCellInfo(CellLocation location) {
		if (location != null) {
			if (mPhoneType == TelephonyManager.PHONE_TYPE_GSM) {
				GsmCellLocation gcl = (GsmCellLocation) location;
				mCid = gcl.getCid();
				mLac = gcl.getLac();
			} else if (mPhoneType == PHONE_TYPE_CDMA) {
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
		// allow unknown mRssi, since signalStrengthChanged isn't reliable enough by itself
		signalStrengthChanged(UNKNOWN_RSSI);
	}

	final void signalStrengthChanged(int rssi) {
		// signalStrengthChanged releases any wakelocks IF mCid != UNKNOWN_CID && enableWif != mLastScanEnableWifi
		// keep last known rssi
		if (rssi != UNKNOWN_RSSI) mRssi = rssi;
		if (mWapdroidUI != null) {
			updateUI();
			try {
				mWapdroidUI.setSignalStrength(mRssi);
			} catch (RemoteException e) {}
		}
		// initialize enableWifi as mLastScanEnableWifi, so that wakelock is released by default
		boolean enableWifi = mLastScanEnableWifi;
		// allow unknown mRssi, since signalStrengthChanged isn't reliable enough by itself
		if (mManageWifi && (mCid != UNKNOWN_CID)) {
			DatabaseAdapter da = new DatabaseAdapter(this);
			da.open();
			if ((DatabaseAdapter.mDatabase != null) && DatabaseAdapter.mDatabase.isOpen()) {
				if (mSsid != null) {
					// upgrading, BSSID may not be set yet
					int network = da.fetchNetwork(mSsid, mBssid);
					da.createPair(mCid, mLac, network, mRssi);
					TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
					if ((tm.getNeighboringCellInfo() != null) && !tm.getNeighboringCellInfo().isEmpty()) {
						for (NeighboringCellInfo nci : tm.getNeighboringCellInfo()) {
							int nci_cid = nci.getCid() > 0 ? nci.getCid() : UNKNOWN_CID, nci_lac, nci_rssi = (nci.getRssi() != UNKNOWN_RSSI) && (mPhoneType == TelephonyManager.PHONE_TYPE_GSM) ? 2 * nci.getRssi() - 113 : nci.getRssi();
							if (mNciReflectGetLac != null) {
								/* feature is supported */
								try {
									nci_lac = nciGetLac(nci);
								} catch (IOException ie) {
									nci_lac = UNKNOWN_CID;
									Log.e(TAG, "unexpected " + ie);
								}
							} else nci_lac = UNKNOWN_CID;
							if (nci_cid != UNKNOWN_CID) da.createPair(nci_cid, nci_lac, network, nci_rssi);
						}
					}
				}
				// always allow disabling, but only enable if above the battery limit
				else if (!enableWifi || (mLastBattPerc >= mBatteryLimit)) {
					enableWifi = da.cellInRange(mCid, mLac, mRssi);
					if (enableWifi) {
						// check neighbors if it appears that we're in range, for both enabling and disabling
						TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
						if ((tm.getNeighboringCellInfo() != null) && !tm.getNeighboringCellInfo().isEmpty()) {
							for (NeighboringCellInfo nci : tm.getNeighboringCellInfo()) {
								int nci_cid = nci.getCid() > 0 ? nci.getCid() : UNKNOWN_CID, nci_rssi = (nci.getRssi() != UNKNOWN_RSSI) && (mPhoneType == TelephonyManager.PHONE_TYPE_GSM) ? 2 * nci.getRssi() - 113 : nci.getRssi(), nci_lac;
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
								if (nci_cid != UNKNOWN_CID) enableWifi = da.cellInRange(nci_cid, nci_lac, nci_rssi);
								if (!enableWifi) break;
							}
						}
					}
					// toggle if ((enable & not(enabled or enabling)) or (disable and (enabled or enabling))) and (disable and not(disabling))
					// to avoid hysteresis when on the edge of a network, require 2 consecutive, identical results before affecting a change
					if (!mManualOverride && (enableWifi ^ ((((mLastWifiState == WifiManager.WIFI_STATE_ENABLED) || (mLastWifiState == WifiManager.WIFI_STATE_ENABLING))))) && (enableWifi ^ (!enableWifi && (mLastWifiState != WifiManager.WIFI_STATE_DISABLING))) && (mLastScanEnableWifi == enableWifi)) ((WifiManager) getSystemService(Context.WIFI_SERVICE)).setWifiEnabled(enableWifi);
				}
				da.close();
			} else Log.e(TAG, "database unavailable");
			da.closeHelper();
			// release the service if it doesn't appear that we're entering or leaving a network
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
	}

	final void createNotification(boolean enabled, boolean update) {
		// service runs for ui, so if not managing, don't notify
		if (mManageWifi) {
			Notification notification = new Notification((enabled ? R.drawable.statuson : R.drawable.scanning), getString(R.string.label_WIFI) + " " + getString(enabled ? R.string.label_enabled : R.string.label_disabled), System.currentTimeMillis());
			notification.setLatestEventInfo(getBaseContext(), getString(R.string.label_WIFI) + " " + getString(enabled ? R.string.label_enabled : R.string.label_disabled), getString(R.string.app_name), PendingIntent.getActivity(getBaseContext(), 0, new Intent(getBaseContext(), WapdroidUI.class), 0));
			if (mPersistentStatus) notification.flags |= Notification.FLAG_NO_CLEAR;
			if (update) notification.defaults |= mNotifications;
			((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).notify(NOTIFY_ID, notification);
		}
	}

}