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

import static com.piusvelte.wapdroid.WapdroidDatabaseHelper.TAG;
import static com.piusvelte.wapdroid.WapdroidDatabaseHelper.UNKNOWN_CID;
import static com.piusvelte.wapdroid.WapdroidDatabaseHelper.UNKNOWN_RSSI;
import static com.piusvelte.wapdroid.WapdroidDatabaseHelper.TABLE_CELLS;
import static com.piusvelte.wapdroid.WapdroidDatabaseHelper.TABLE_LOCATIONS;
import static com.piusvelte.wapdroid.WapdroidDatabaseHelper.TABLE_NETWORKS;
import static com.piusvelte.wapdroid.WapdroidDatabaseHelper.TABLE_PAIRS;
import static com.piusvelte.wapdroid.WapdroidDatabaseHelper.VIEW_RANGES;
import static com.piusvelte.wapdroid.WapdroidDatabaseHelper._ID;
import static com.piusvelte.wapdroid.WapdroidDatabaseHelper.BSSID;
import static com.piusvelte.wapdroid.WapdroidDatabaseHelper.CELL;
import static com.piusvelte.wapdroid.WapdroidDatabaseHelper.CID;
import static com.piusvelte.wapdroid.WapdroidDatabaseHelper.LAC;
import static com.piusvelte.wapdroid.WapdroidDatabaseHelper.LOCATION;
import static com.piusvelte.wapdroid.WapdroidDatabaseHelper.NETWORK;
import static com.piusvelte.wapdroid.WapdroidDatabaseHelper.RSSI_MAX;
import static com.piusvelte.wapdroid.WapdroidDatabaseHelper.RSSI_MIN;
import static com.piusvelte.wapdroid.WapdroidDatabaseHelper.SSID;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

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
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
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

public class WapdroidService extends Service implements OnSharedPreferenceChangeListener {
	private static int NOTIFY_ID = 1;
	public static final String WAKE_SERVICE = "com.piusvelte.wapdroid.WAKE_SERVICE";
	public static final int LISTEN_SIGNAL_STRENGTHS = 256;
	public static final int PHONE_TYPE_CDMA = 2;
	private static final int START_STICKY = 1;
	protected int mCid = UNKNOWN_CID,
	mLac = UNKNOWN_CID,
	mRssi = UNKNOWN_RSSI,
	mLastWifiState = WifiManager.WIFI_STATE_UNKNOWN,
	mNotifications;
	protected int mInterval,
	mBatteryLimit,
	mLastBattPerc = 0;
	protected static int mPhoneType;
	protected boolean mManageWifi,
	mManualOverride,
	mLastScanEnableWifi,
	mNotify,
	mPersistentStatus;
	String mSsid, mBssid;
	private static boolean mApi7 = false;
	IWapdroidUI mWapdroidUI;
	private BroadcastReceiver mReceiver = new Receiver();
	
	public static PhoneStateListener mPhoneListener;
	private final IWapdroidService.Stub mWapdroidService = new IWapdroidService.Stub() {
		public void setCallback(IBinder mWapdroidUIBinder)
		throws RemoteException {
			if (mWapdroidUIBinder != null) {
				if (ManageWakeLocks.hasLock()) ManageWakeLocks.release();
				mWapdroidUI = IWapdroidUI.Stub.asInterface(mWapdroidUIBinder);
				if (mWapdroidUI != null) {
					// listen to phone changes if a low battery condition caused this to stop
					if (mLastBattPerc < mBatteryLimit) ((TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE)).listen(mPhoneListener, (PhoneStateListener.LISTEN_CELL_LOCATION | PhoneStateListener.LISTEN_SIGNAL_STRENGTH | LISTEN_SIGNAL_STRENGTHS));
					updateUI();
				} else if (mLastBattPerc < mBatteryLimit) ((TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE)).listen(mPhoneListener, PhoneStateListener.LISTEN_NONE);
			}
		}
	};
	private WapdroidDatabaseHelper mWapdroidDatabaseHelper;
	private SQLiteDatabase mDatabase;

	// add onSignalStrengthsChanged for api >= 7
	static {
		try {
			Class.forName("android.telephony.SignalStrength");
			mApi7 = true;
		} catch (Exception ex) {
			Log.e(TAG, "api < 7, " + ex);
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
		int lac = UNKNOWN_CID;
		try {
			lac = (Integer) mNciReflectGetLac.invoke(nci);
		} catch (InvocationTargetException ite) {
			Throwable cause = ite.getCause();
			if (cause instanceof IOException) throw (IOException) cause;
			else if (cause instanceof RuntimeException) throw (RuntimeException) cause;
			else if (cause instanceof Error) throw (Error) cause;
			else throw new RuntimeException(ite);
		} catch (IllegalAccessException ie) {
			Log.e(TAG, "unexpected " + ie);
		}
		return lac;
	}

	@Override
	public IBinder onBind(Intent intent) {
		cancelAlarm();
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
		sp.registerOnSharedPreferenceChangeListener(this);
		WifiManager wm = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		wifiState(wm.getWifiState());
		// to help avoid hysteresis, make sure that at least 2 consecutive scans were in/out of range
		mLastScanEnableWifi = (mLastWifiState == WifiManager.WIFI_STATE_ENABLED);
		// the ssid from wifimanager may not be null, even if disconnected, so check against the supplicant state
		wifiConnection(wm);
		mWapdroidDatabaseHelper = new WapdroidDatabaseHelper(this);
		mDatabase = mWapdroidDatabaseHelper.getWritableDatabase();
		IntentFilter f = new IntentFilter();
		f.addAction(Intent.ACTION_BATTERY_CHANGED);
		f.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
		f.addAction(Intent.ACTION_SCREEN_OFF);
		f.addAction(Intent.ACTION_SCREEN_ON);
		f.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
		registerReceiver(mReceiver, f);
		TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		mPhoneType = tm.getPhoneType();
		tm.listen(mPhoneListener = (mApi7 ?
				new PhoneStateListener() {
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
		}
		: (new PhoneStateListener() {
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
		ignorePhone();
		if (mNotify) ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).cancel(NOTIFY_ID);
		mDatabase.close();
		mWapdroidDatabaseHelper.close();
		if (ManageWakeLocks.hasLock()) ManageWakeLocks.release();
	}
	
	protected void wifiState(int state) {
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
	}
	
	protected int wifiConnection(WifiManager wm) {
		if (wm.getConnectionInfo().getSupplicantState() == SupplicantState.COMPLETED) {
			mSsid = wm.getConnectionInfo().getSSID();
			mBssid = wm.getConnectionInfo().getBSSID();
		} else {
			mSsid = null;
			mBssid = null;
		}
		return wm.getWifiState();
	}

	private void updateUI() {
		String cells = " (" + CID + "=" + Integer.toString(mCid) + " and (" + LAC + "=" + Integer.toString(mLac) + " or " + LOCATION + "=" + UNKNOWN_CID + ")"
		+ ((mRssi == UNKNOWN_RSSI) ? ")" : " and (((" + RSSI_MIN + "=" + UNKNOWN_RSSI + ") or (" + RSSI_MIN + "<=" + Integer.toString(mRssi) + ")) and (" + RSSI_MAX + ">=" + Integer.toString(mRssi) + ")))");
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
				cells += " or (" + CID + "=" + Integer.toString(nci.getCid()) + " and (" + LAC + "=" + nci_lac + " or " + LOCATION + "=" + UNKNOWN_CID + ")"
				+ ((nci_rssi == UNKNOWN_RSSI) ? ")" : " and (((" + RSSI_MIN + "=" + UNKNOWN_RSSI + ") or (" + RSSI_MIN + "<=" + Integer.toString(nci_rssi) + ")) and (" + RSSI_MAX + ">=" + Integer.toString(nci_rssi) + ")))");
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
	
	private PendingIntent alarmPendingIntent() {
		return PendingIntent.getBroadcast(this, 0, (new Intent(this, BootReceiver.class)).setAction(WAKE_SERVICE), 0);
	}
	
	protected void setAlarm() {
		if (mInterval > 0) ((AlarmManager) getSystemService(Context.ALARM_SERVICE)).set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + mInterval, alarmPendingIntent());
	}
	
	protected void cancelAlarm() {
		((AlarmManager) getSystemService(Context.ALARM_SERVICE)).cancel(alarmPendingIntent());
	}
	
	protected void listenPhone() {
		((TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE)).listen(mPhoneListener, (PhoneStateListener.LISTEN_CELL_LOCATION | PhoneStateListener.LISTEN_SIGNAL_STRENGTH | LISTEN_SIGNAL_STRENGTHS));
	}
	
	protected void ignorePhone() {
		((TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE)).listen(mPhoneListener, PhoneStateListener.LISTEN_NONE);		
	}

	protected void getCellInfo(CellLocation location) {
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
		}
		// allow unknown mRssi, since signalStrengthChanged isn't reliable enough by itself
		signalStrengthChanged(UNKNOWN_RSSI);
	}

	private void signalStrengthChanged(int rssi) {
		// signalStrengthChanged releases any wakelocks IF mCid != UNKNOWN_CID && enableWif != mLastScanEnableWifi
		// rssi may be unknown
		mRssi = rssi;
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
			if (mSsid != null) {
				// upgrading, BSSID may not be set yet
				long network = fetchNetwork(mSsid, mBssid);
				createPair(mCid, mLac, network, mRssi);
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
						if (nci_cid != UNKNOWN_CID) createPair(nci_cid, nci_lac, network, nci_rssi);
					}
				}
			} else if (!enableWifi || (mLastBattPerc >= mBatteryLimit)) {
				enableWifi = cellInRange(mCid, mLac, mRssi);
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
							if (nci_cid != UNKNOWN_CID) enableWifi = cellInRange(nci_cid, nci_lac, nci_rssi);
							if (!enableWifi) break;
						}
					}
				}
				// toggle if ((enable & not(enabled or enabling)) or (disable and (enabled or enabling))) and (disable and not(disabling))
				// to avoid hysteresis when on the edge of a network, require 2 consecutive, identical results before affecting a change
				if (!mManualOverride && (enableWifi ^ ((((mLastWifiState == WifiManager.WIFI_STATE_ENABLED) || (mLastWifiState == WifiManager.WIFI_STATE_ENABLING))))) && (enableWifi ^ (!enableWifi && (mLastWifiState != WifiManager.WIFI_STATE_DISABLING))) && (mLastScanEnableWifi == enableWifi)) ((WifiManager) getSystemService(Context.WIFI_SERVICE)).setWifiEnabled(enableWifi);
			}
			// release the service if it doesn't appear that we're entering or leaving a network
			if (enableWifi == mLastScanEnableWifi) {
				if (ManageWakeLocks.hasLock()) {
					if (mInterval > 0) ((AlarmManager) getSystemService(Context.ALARM_SERVICE)).set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + mInterval, PendingIntent.getBroadcast(this, 0, (new Intent(this, BootReceiver.class)).setAction(WAKE_SERVICE), 0));
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

	private void createNotification(boolean enabled, boolean update) {
		// service runs for ui, so if not managing, don't notify
		if (mManageWifi) {
			Notification notification = new Notification((enabled ? R.drawable.statuson : R.drawable.scanning), getString(R.string.label_WIFI) + " " + getString(enabled ? R.string.label_enabled : R.string.label_disabled), System.currentTimeMillis());
			notification.setLatestEventInfo(getBaseContext(), getString(R.string.label_WIFI) + " " + getString(enabled ? R.string.label_enabled : R.string.label_disabled), getString(R.string.app_name), PendingIntent.getActivity(this, 0, (new Intent(this, WapdroidUI.class)), 0));
			if (mPersistentStatus) notification.flags |= Notification.FLAG_NO_CLEAR;
			if (update) notification.defaults |= mNotifications;
			((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).notify(NOTIFY_ID, notification);
		}
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (key.equals(getString(R.string.key_manageWifi))) {
			mManageWifi = sharedPreferences.getBoolean(key, false);
			if (mManageWifi) {
				mNotify = sharedPreferences.getBoolean(getString(R.string.key_notify), false);
				if (mNotify) {
					mPersistentStatus = sharedPreferences.getBoolean(getString(R.string.key_persistent_status), false);
					mNotifications = 0;
					if (sharedPreferences.getBoolean(getString(R.string.key_led), false)) mNotifications |= Notification.DEFAULT_LIGHTS;
					if (sharedPreferences.getBoolean(getString(R.string.key_ringtone), false)) mNotifications |= Notification.DEFAULT_SOUND;
					if (sharedPreferences.getBoolean(getString(R.string.key_vibrate), false)) mNotifications |= Notification.DEFAULT_VIBRATE;				
				}
			}
		}
		else if (key.equals(getString(R.string.key_interval))) mInterval = Integer.parseInt((String) sharedPreferences.getString(key, "30000"));
		else if (key.equals(getString(R.string.key_battery_override))) mBatteryLimit = (sharedPreferences.getBoolean(key, false)) ? Integer.parseInt((String) sharedPreferences.getString(getString(R.string.key_battery_percentage), "30")) : 0;
		else if (key.equals(getString(R.string.key_battery_percentage))) mBatteryLimit = Integer.parseInt((String) sharedPreferences.getString(key, "30"));
		else if (key.equals(getString(R.string.key_led)) || key.equals(getString(R.string.key_ringtone)) || key.equals(getString(R.string.key_vibrate))) {
			mNotifications = 0;
			if (sharedPreferences.getBoolean(getString(R.string.key_led), false)) mNotifications |= Notification.DEFAULT_LIGHTS;
			if (sharedPreferences.getBoolean(getString(R.string.key_ringtone), false)) mNotifications |= Notification.DEFAULT_SOUND;
			if (sharedPreferences.getBoolean(getString(R.string.key_vibrate), false)) mNotifications |= Notification.DEFAULT_VIBRATE;
		}
		else if (key.equals(getString(R.string.key_notify))) {
			mNotify = sharedPreferences.getBoolean(key, false);
			if (mNotify) {
				mPersistentStatus = sharedPreferences.getBoolean(getString(R.string.key_persistent_status), false);
				mNotifications = 0;
				if (sharedPreferences.getBoolean(getString(R.string.key_led), false)) mNotifications |= Notification.DEFAULT_LIGHTS;
				if (sharedPreferences.getBoolean(getString(R.string.key_ringtone), false)) mNotifications |= Notification.DEFAULT_SOUND;
				if (sharedPreferences.getBoolean(getString(R.string.key_vibrate), false)) mNotifications |= Notification.DEFAULT_VIBRATE;				
			}
		}
		else if (key.equals(getString(R.string.key_persistent_status))) {
			// to change this, manage & notify must me enabled
			mPersistentStatus = sharedPreferences.getBoolean(key, false);
			if (mPersistentStatus) createNotification((mLastWifiState == WifiManager.WIFI_STATE_ENABLED), false);
			else ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).cancel(NOTIFY_ID);
		}
		else if (key.equals(getString(R.string.key_manual_override))) mManualOverride = sharedPreferences.getBoolean(key, false);
	}

	private long fetchNetwork(String ssid, String bssid) {
		int network;
		Cursor c = mDatabase.rawQuery(
				"SELECT " + _ID + "," + SSID + "," + BSSID
				+ " FROM " + TABLE_NETWORKS
				+ " WHERE " + SSID + "='" + ssid + "' and (" + BSSID + "='" + bssid + "' or " + BSSID + "='')", null);
		if (c.getCount() > 0) {
			// ssid matches, only concerned if bssid is empty
			c.moveToFirst();
			network = c.getInt(c.getColumnIndex(_ID));
			if (c.getString(c.getColumnIndex(BSSID)).equals("")) {
				ContentValues values = new ContentValues();
				values.put(BSSID, bssid);
				mDatabase.update(TABLE_NETWORKS, values, _ID + "=" + network, null);
			}
		} else {
			ContentValues values = new ContentValues();
			values.put(SSID, ssid);
			values.put(BSSID, bssid);
			network = (int) mDatabase.insert(TABLE_NETWORKS, SSID, values);
		}
		c.close();
		return network;
	}

	private int fetchLocation(int lac) {
		// select or insert location
		if (lac > 0) {
			int location;
			Cursor c = mDatabase.rawQuery(
					"SELECT " + _ID
					+ " FROM " + TABLE_LOCATIONS
					+ " WHERE " + LAC + "=" + lac, null);
			if (c.getCount() > 0) {
				c.moveToFirst();
				location = c.getInt(c.getColumnIndex(_ID));
			} else {
				ContentValues values = new ContentValues();
				values.put(LAC, lac);
				location = (int) mDatabase.insert(TABLE_LOCATIONS, LAC, values);
			}
			c.close();
			return location;
		} else return UNKNOWN_CID;
	}

	private void createPair(int cid, int lac, long network, int rssi) {
		int cell, pair, location = fetchLocation(lac);
		// if location==-1, then match only on cid, otherwise match on location or -1
		// select or insert cell
		Cursor c = mDatabase.rawQuery(
				"SELECT " + _ID + "," + LOCATION
				+ " FROM " + TABLE_CELLS
				+ " WHERE " + CID + "=" + cid + (location == UNKNOWN_CID ?
				""
				: " and (" + LOCATION + "=" + UNKNOWN_CID + " or " + LOCATION + "=" + location + ")"), null);
		if (c.getCount() > 0) {
			c.moveToFirst();
			cell = c.getInt(c.getColumnIndex(_ID));
			if ((location != UNKNOWN_CID) && (c.getInt(c.getColumnIndex(LOCATION)) == UNKNOWN_CID)) {
				ContentValues values = new ContentValues();
				values.put(LOCATION, location);
				mDatabase.update(TABLE_CELLS, values, _ID + "=" + cell, null);
			}
		} else {
			ContentValues values = new ContentValues();
			values.put(CID, cid);
			values.put(LOCATION, location);
			cell = (int) mDatabase.insert(TABLE_CELLS, CID, values);
		}
		c.close();
		// select and update or insert pair
		c = mDatabase.rawQuery(
				"SELECT " + _ID + "," + RSSI_MIN + "," + RSSI_MAX
				+ " FROM " + TABLE_PAIRS
				+ " WHERE " + CELL + "=" + cell + " and " + NETWORK + "=" + network, null);
		if (c.getCount() > 0) {
			if (rssi != UNKNOWN_RSSI) {
				c.moveToFirst();
				pair = c.getInt(c.getColumnIndex(_ID));
				int rssi_min = c.getInt(c.getColumnIndex(RSSI_MIN));
				int rssi_max = c.getInt(c.getColumnIndex(RSSI_MAX));
				if (rssi_min > rssi) {
					ContentValues values = new ContentValues();
					values.put(RSSI_MIN, rssi);
					mDatabase.update(TABLE_PAIRS, values, _ID + "=" + pair, null);
				}
				else if ((rssi_max == UNKNOWN_RSSI) || (rssi_max < rssi)) {
					ContentValues values = new ContentValues();
					values.put(RSSI_MAX, rssi);
					mDatabase.update(TABLE_PAIRS, values, _ID + "=" + pair, null);
				}
			}
		} else {
			ContentValues values = new ContentValues();
			values.put(CELL, cell);
			values.put(NETWORK, network);
			values.put(RSSI_MIN, rssi);
			values.put(RSSI_MAX, rssi);
			mDatabase.insert(TABLE_PAIRS, CELL, values);
		}
		c.close();
	}

	private boolean cellInRange(int cid, int lac, int rssi) {
		Cursor c = mDatabase.rawQuery(
				"SELECT " + _ID + "," + LOCATION
				+ " FROM " + VIEW_RANGES
				+ " WHERE " + CID + "=" + cid + " and (" + LAC + "=" + lac + " or " + LOCATION + "=" + UNKNOWN_CID + ")" +
				(rssi == UNKNOWN_RSSI
						? ""
								: " and (((" + RSSI_MIN + "=" + UNKNOWN_RSSI + ") or (" + RSSI_MIN + "<="+ rssi + ")) and (" + RSSI_MAX + ">=" + rssi + "))"), null);
		boolean inRange = (c.getCount() > 0);
		if (inRange && (lac > 0)) {
			// check LAC, as this is a new column
			c.moveToFirst();
			if (c.isNull(c.getColumnIndex(LOCATION))) {
				ContentValues values = new ContentValues();
				values.put(LOCATION, fetchLocation(lac));
				mDatabase.update(TABLE_CELLS, values, _ID + "=" + c.getInt(c.getColumnIndex(_ID)), null);
			}
		}
		c.close();
		return inRange;
	}

}