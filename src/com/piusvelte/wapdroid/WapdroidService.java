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

import static com.piusvelte.wapdroid.Wapdroid.UNKNOWN_CID;
import static com.piusvelte.wapdroid.Wapdroid.UNKNOWN_RSSI;

import static android.content.Intent.ACTION_BOOT_COMPLETED;
import static android.content.Intent.ACTION_PACKAGE_REPLACED;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.piusvelte.wapdroid.Wapdroid.Locations;
import com.piusvelte.wapdroid.Wapdroid.Networks;
import com.piusvelte.wapdroid.Wapdroid.Ranges;
import com.piusvelte.wapdroid.Wapdroid.Cells;
import com.piusvelte.wapdroid.Wapdroid.Pairs;

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
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
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
	private static final String TAG = "WapdroidService";
	private static int NOTIFY_ID = 1;
	public static final String WAKE_SERVICE = "com.piusvelte.WAKE_SERVICE";
	public static final int LISTEN_SIGNAL_STRENGTHS = 256;
	public static final int PHONE_TYPE_CDMA = 2;
	private static final int START_STICKY = 1;
	private int mCid = UNKNOWN_CID,
	mLac = UNKNOWN_CID,
	mRssi = UNKNOWN_RSSI,
	mLastWiFiState = WifiManager.WIFI_STATE_UNKNOWN,
	mNotifications;
	private int mInterval,
	mBatteryLimit,
	mLastBattPerc = 0;
	private static int mPhoneType;
	private boolean mManageWifi,
	mManualOverride,
	mLastScanEnableWifi,
	mNotify,
	mPersistentStatus,
	mWifiSleep,
	mScreenOn = true;
	String mSsid, mBssid;
	private static boolean mApi7 = false;
	IWapdroidUI mWapdroidUI;
	private BroadcastReceiver mReceiver = new Receiver();
	private static final String BATTERY_EXTRA_LEVEL = "level";
	private static final String BATTERY_EXTRA_SCALE = "scale";
	private static final String BATTERY_EXTRA_PLUGGED = "plugged";
	private WifiManager mWifiManager;
	private TelephonyManager mTelephonyManager;
	private AlarmManager mAlarmManager;
	private ConnectivityManager mConnectivityManager;

	public static PhoneStateListener mPhoneListener;
	private final IWapdroidService.Stub mWapdroidService = new IWapdroidService.Stub() {
		public void setCallback(IBinder mWapdroidUIBinder)
		throws RemoteException {
			if (mWapdroidUIBinder != null) {
				if (ManageWakeLocks.hasLock()) ManageWakeLocks.release();
				mWapdroidUI = IWapdroidUI.Stub.asInterface(mWapdroidUIBinder);
				if (mWapdroidUI != null) {
					// listen to phone changes if a low battery condition caused this to stop
					if (mLastBattPerc < mBatteryLimit) mTelephonyManager.listen(mPhoneListener, (PhoneStateListener.LISTEN_CELL_LOCATION | PhoneStateListener.LISTEN_SIGNAL_STRENGTH | LISTEN_SIGNAL_STRENGTHS));
					updateUI();
				} else if (mLastBattPerc < mBatteryLimit) mTelephonyManager.listen(mPhoneListener, PhoneStateListener.LISTEN_NONE);
			}
		}
	};

	private static Method mNciReflectGetLac;

	// add onSignalStrengthsChanged for api >= 7
	static {
		try {
			Class.forName("android.telephony.SignalStrength");
			mApi7 = true;
			mNciReflectGetLac = android.telephony.NeighboringCellInfo.class.getMethod("getLac", new Class[] {});
		} catch (NoSuchMethodException nsme) {
			Log.e(TAG, "api < 5, " + nsme);
		} catch (Exception ex) {
			Log.e(TAG, "api < 7, " + ex);
		}
	}

	private static int nciGetLac(NeighboringCellInfo nci) {
		int lac;
		if (mNciReflectGetLac != null) {
			try {
				lac = (Integer) mNciReflectGetLac.invoke(nci);
			} catch (InvocationTargetException ite) {
				lac = UNKNOWN_CID;
				Log.e(TAG, "unexpected " + ite.toString());
			} catch (IllegalAccessException ie) {
				lac = UNKNOWN_CID;
				Log.e(TAG, "unexpected " + ie.toString());
			}
		} else lac = UNKNOWN_CID;
		return lac;
	}

	@Override
	public IBinder onBind(Intent intent) {
		mAlarmManager.cancel(PendingIntent.getBroadcast(this, 0, (new Intent(this, BootReceiver.class)).setAction(WAKE_SERVICE), 0));
		ManageWakeLocks.release();
		return mWapdroidService;
	}

	@Override
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);
		if ((intent != null) && (intent.getAction() != null) && !intent.getAction().equals(WAKE_SERVICE)) {
			if ((intent.getAction().equals(ACTION_BOOT_COMPLETED) || intent.getAction().equals(ACTION_PACKAGE_REPLACED)) && !mManageWifi) {
				// nothing to do
				ManageWakeLocks.release();
				stopSelf();
			} else if (intent.getAction().equals(Intent.ACTION_BATTERY_CHANGED)) {
				// override low battery when charging
				if (intent.getIntExtra(BATTERY_EXTRA_PLUGGED, 0) != 0) mBatteryLimit = 0;
				else {
					// unplugged, restore the user defined battery limit
					SharedPreferences sp = (SharedPreferences) getSharedPreferences(getString(R.string.key_preferences), WapdroidService.MODE_PRIVATE);
					if (sp.getBoolean(getString(R.string.key_battery_override), false)) mBatteryLimit = Integer.parseInt((String) sp.getString(getString(R.string.key_battery_percentage), "30"));
				}
				int currentBattPerc = Math.round(intent.getIntExtra(BATTERY_EXTRA_LEVEL, 0) * 100 / intent.getIntExtra(BATTERY_EXTRA_SCALE, 100));
				// check the threshold
				if (mManageWifi && !mManualOverride && (currentBattPerc < mBatteryLimit) && (mLastBattPerc >= mBatteryLimit)) {
					mWifiManager.setWifiEnabled(false);
					mTelephonyManager.listen(mPhoneListener, PhoneStateListener.LISTEN_NONE);
				} else if ((currentBattPerc >= mBatteryLimit) && (mLastBattPerc < mBatteryLimit)) mTelephonyManager.listen(mPhoneListener, (PhoneStateListener.LISTEN_CELL_LOCATION | PhoneStateListener.LISTEN_SIGNAL_STRENGTH | LISTEN_SIGNAL_STRENGTHS));
				mLastBattPerc = currentBattPerc;
				if (mWapdroidUI != null) {
					try {
						mWapdroidUI.setBattery(currentBattPerc);
					} catch (RemoteException e) {};
				}
			} else if (intent.getAction().equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
				// cell change will release lock
				// a connection was gained or lost
				mAlarmManager.cancel(PendingIntent.getBroadcast(this, 0, (new Intent(this, BootReceiver.class)).setAction(WAKE_SERVICE), 0));
				wifiConnection();
				getCellInfo(mTelephonyManager.getCellLocation());
				if (mWapdroidUI != null) {
					try {
						mWapdroidUI.setWifiInfo(mLastWiFiState, mSsid, mBssid);
					} catch (RemoteException e) {}
				}
			} else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
				mScreenOn = true;
				mAlarmManager.cancel(PendingIntent.getBroadcast(this, 0, (new Intent(this, BootReceiver.class)).setAction(WAKE_SERVICE), 0));
				getCellInfo(mTelephonyManager.getCellLocation());
			} else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
				mScreenOn = false;
				mManualOverride = false;
				if (mInterval > 0) mAlarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + mInterval, PendingIntent.getBroadcast(this, 0, (new Intent(this, BootReceiver.class)).setAction(WAKE_SERVICE), 0));
				if (mWifiSleep && mobileNetworksAvailable() && (mLastWiFiState == WifiManager.WIFI_STATE_ENABLED) || (mLastWiFiState == WifiManager.WIFI_STATE_ENABLING)) {
					// check sleep policy
					// check if the current network is managed
					if (mSsid != null) {
						Cursor c = this.getContentResolver().query(Networks.CONTENT_URI, new String[]{Networks._ID, Networks.SSID, Networks.BSSID}, Networks.SSID + "=? and (" + Networks.BSSID + "=? or " + Networks.BSSID + "='') and " + Networks.MANAGE + "=1", new String[]{(mSsid != null ? mSsid : ""), (mBssid != null ? mBssid : "")}, null);
						if (c.moveToFirst()) mWifiManager.setWifiEnabled(false);
						c.close();
					} else mWifiManager.setWifiEnabled(false);
				}
				ManageWakeLocks.release();
			} else if (intent.getAction().equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
				mAlarmManager.cancel(PendingIntent.getBroadcast(this, 0, (new Intent(this, BootReceiver.class)).setAction(WAKE_SERVICE), 0));
				/*
				 * get wifi state
				 * initially, lastWifiState is unknown, otherwise state is evaluated either enabled or not
				 * when wifi enabled, register network receiver
				 * when wifi not enabled, unregister network receiver
				 */
				wifiState(intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 4));
				// a lock was only needed to send the notification, no cell changes need to be evaluated until a network state change occurs
				if (mInterval > 0) mAlarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + mInterval, PendingIntent.getBroadcast(this, 0, (new Intent(this, BootReceiver.class)).setAction(WAKE_SERVICE), 0));
				ManageWakeLocks.release();
			} else if (intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
				// if mobile connection was lost, and wifi was put to sleep, enable wifi
				if (intent.hasExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY) && intent.hasExtra(ConnectivityManager.EXTRA_NETWORK_INFO) && (intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, true))) {
					NetworkInfo ni = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
					if ((ni.getType() != ConnectivityManager.TYPE_WIFI) && !mobileNetworksAvailable() && mLastScanEnableWifi) mWifiManager.setWifiEnabled(true);
				}
			}
		} else getCellInfo(mTelephonyManager.getCellLocation());
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		onStart(intent, startId);
		return START_STICKY;
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
		mWifiSleep = sp.getBoolean(getString(R.string.key_wifi_sleep), false);
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
		// only register the listener when ui is invoked
		sp.registerOnSharedPreferenceChangeListener(this);
		mAlarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
		mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		wifiState(mWifiManager.getWifiState());
		// to help avoid hysteresis, make sure that at least 2 consecutive scans were in/out of range
		mLastScanEnableWifi = (mLastWiFiState == WifiManager.WIFI_STATE_ENABLED);
		// the ssid from wifimanager may not be null, even if disconnected, so check against the supplicant state
		wifiConnection();
		IntentFilter f = new IntentFilter();
		f.addAction(Intent.ACTION_BATTERY_CHANGED);
		f.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
		f.addAction(Intent.ACTION_SCREEN_OFF);
		f.addAction(Intent.ACTION_SCREEN_ON);
		f.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
		f.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
		f.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
		registerReceiver(mReceiver, f);
		mTelephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		mPhoneType = mTelephonyManager.getPhoneType();
		mTelephonyManager.listen(mPhoneListener = (mApi7 ?
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
		mTelephonyManager.listen(mPhoneListener, PhoneStateListener.LISTEN_NONE);
		if (mNotify) ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).cancel(NOTIFY_ID);
		if (ManageWakeLocks.hasLock()) ManageWakeLocks.release();
	}

	private boolean mobileNetworksAvailable() {
		if (mConnectivityManager == null) mConnectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo nis[] = mConnectivityManager.getAllNetworkInfo();
		boolean networkAvailable = false;
		for (NetworkInfo ni : nis) {
			if ((ni.getType() != ConnectivityManager.TYPE_WIFI) && ((ni.getState() == NetworkInfo.State.CONNECTED) || ni.isAvailable())) {
				networkAvailable = true;
				break;
			}
		}
		return networkAvailable;
	}

	private void wifiState(int state) {
		if (state != WifiManager.WIFI_STATE_UNKNOWN) {
			// notify, when onCreate (no led, ringtone, vibrate), or a change to enabled or disabled
			if (mNotify	&& ((mLastWiFiState == WifiManager.WIFI_STATE_UNKNOWN)
					|| ((state == WifiManager.WIFI_STATE_DISABLED) && (mLastWiFiState != WifiManager.WIFI_STATE_DISABLED))
					|| ((state == WifiManager.WIFI_STATE_ENABLED) && (mLastWiFiState != WifiManager.WIFI_STATE_ENABLED)))) createNotification((state == WifiManager.WIFI_STATE_ENABLED), (mLastWiFiState != WifiManager.WIFI_STATE_UNKNOWN));
			mLastWiFiState = state;
			if (mLastWiFiState != WifiManager.WIFI_STATE_ENABLED) {
				mSsid = null;
				mBssid = null;
			}
			if (mWapdroidUI != null) {
				try {
					mWapdroidUI.setWifiInfo(mLastWiFiState, mSsid, mBssid);
				} catch (RemoteException e) {}
			}
		}
	}

	private void wifiConnection() {
		WifiInfo wi = mWifiManager.getConnectionInfo();
		if (wi.getSupplicantState() == SupplicantState.COMPLETED) {
			mSsid = wi.getSSID();
			mBssid = wi.getBSSID();
		} else {
			mSsid = null;
			mBssid = null;
		}
	}

	private void updateUI() {
		String cells = " (" + Ranges.CID + "=" + Integer.toString(mCid) + " and (" + Ranges.LAC + "=" + Integer.toString(mLac) + " or " + Ranges.LOCATION + "=" + UNKNOWN_CID + ")"
		+ ((mRssi == UNKNOWN_RSSI) ? ")" : " and (((" + Ranges.RSSI_MIN + "=" + UNKNOWN_RSSI + ") or (" + Ranges.RSSI_MIN + "<=" + Integer.toString(mRssi) + ")) and (" + Ranges.RSSI_MAX + ">=" + Integer.toString(mRssi) + ")))");
		if ((mTelephonyManager.getNeighboringCellInfo() != null) && !mTelephonyManager.getNeighboringCellInfo().isEmpty()) {
			for (NeighboringCellInfo nci : mTelephonyManager.getNeighboringCellInfo()) {
				int nci_rssi = (nci.getRssi() != UNKNOWN_RSSI) && (mPhoneType == TelephonyManager.PHONE_TYPE_GSM) ? 2 * nci.getRssi() - 113 : nci.getRssi();
				cells += " or (" + Ranges.CID + "=" + Integer.toString(nci.getCid()) + " and (" + Ranges.LAC + "=" + nciGetLac(nci) + " or " + Ranges.LOCATION + "=" + UNKNOWN_CID + ")"
				+ ((nci_rssi == UNKNOWN_RSSI) ? ")" : " and (((" + Ranges.RSSI_MIN + "=" + UNKNOWN_RSSI + ") or (" + Ranges.RSSI_MIN + "<=" + Integer.toString(nci_rssi) + ")) and (" + Ranges.RSSI_MAX + ">=" + Integer.toString(nci_rssi) + ")))");
			}
		}
		try {
			mWapdroidUI.setOperator(mTelephonyManager.getNetworkOperator());
			mWapdroidUI.setCellInfo(mCid, mLac);
			mWapdroidUI.setWifiInfo(mLastWiFiState, mSsid, mBssid);
			mWapdroidUI.setSignalStrength(mRssi);
			mWapdroidUI.setCells(cells);
			mWapdroidUI.setBattery(mLastBattPerc);
		} catch (RemoteException e) {}
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
				if ((mTelephonyManager.getNeighboringCellInfo() != null) && !mTelephonyManager.getNeighboringCellInfo().isEmpty()) {
					for (NeighboringCellInfo nci : mTelephonyManager.getNeighboringCellInfo()) {
						if (nci.getCid() > 0) createPair(nci.getCid(), nciGetLac(nci), network, (nci.getRssi() != UNKNOWN_RSSI) && (mPhoneType == TelephonyManager.PHONE_TYPE_GSM) ? 2 * nci.getRssi() - 113 : nci.getRssi());
					}
				}
			} else if (!enableWifi || (mLastBattPerc >= mBatteryLimit)) {
				enableWifi = cellInRange(mCid, mLac, mRssi);
				if (enableWifi) {
					// check neighbors if it appears that we're in range, for both enabling and disabling
					if ((mTelephonyManager.getNeighboringCellInfo() != null) && !mTelephonyManager.getNeighboringCellInfo().isEmpty()) {
						for (NeighboringCellInfo nci : mTelephonyManager.getNeighboringCellInfo()) {
							// break on out of range result
							if (nci.getCid() > 0) enableWifi = cellInRange(nci.getCid(), nciGetLac(nci), (nci.getRssi() != UNKNOWN_RSSI) && (mPhoneType == TelephonyManager.PHONE_TYPE_GSM) ? 2 * nci.getRssi() - 113 : nci.getRssi());
							if (!enableWifi) break;
						}
					}
				}
				// toggle if ((enable & not(enabled or enabling)) or (disable and (enabled or enabling))) and (disable and not(disabling))
				// to avoid hysteresis when on the edge of a network, require 2 consecutive, identical results before affecting a change
				if (!mManualOverride
						&& (enableWifi ^ ((((mLastWiFiState == WifiManager.WIFI_STATE_ENABLED) || (mLastWiFiState == WifiManager.WIFI_STATE_ENABLING)))))
						&& (enableWifi ^ (!enableWifi && (mLastWiFiState != WifiManager.WIFI_STATE_DISABLING)))
						&& (mLastScanEnableWifi == enableWifi)
						&& (!enableWifi || mScreenOn || !mWifiSleep || !mobileNetworksAvailable())) mWifiManager.setWifiEnabled(enableWifi);
			}
			// release the service if it doesn't appear that we're entering or leaving a network
			if (enableWifi == mLastScanEnableWifi) {
				if (ManageWakeLocks.hasLock()) {
					if (mInterval > 0) mAlarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + mInterval, PendingIntent.getBroadcast(this, 0, (new Intent(this, BootReceiver.class)).setAction(WAKE_SERVICE), 0));
					// if sleeping, re-initialize phone info
					mCid = UNKNOWN_CID;
					mLac = UNKNOWN_CID;
					mRssi = UNKNOWN_RSSI;
					ManageWakeLocks.release();
				}
			} else mLastScanEnableWifi = enableWifi;
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
			if (mPersistentStatus) createNotification((mLastWiFiState == WifiManager.WIFI_STATE_ENABLED), false);
			else ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).cancel(NOTIFY_ID);
		}
		else if (key.equals(getString(R.string.key_manual_override))) mManualOverride = sharedPreferences.getBoolean(key, false);
		else if (key.equals(getString(R.string.key_wifi_sleep))) mWifiSleep = sharedPreferences.getBoolean(key, false);
	}

	private long fetchNetwork(String ssid, String bssid) {
		int network;
		// handle nulls
		if (ssid == null) ssid = "";
		if (bssid == null) bssid = "";
		Cursor c = this.getContentResolver().query(Networks.CONTENT_URI, new String[]{Networks._ID, Networks.SSID, Networks.BSSID}, Networks.SSID + "=? and (" + Networks.BSSID + "=? or " + Networks.BSSID + "='')", new String[]{ssid, bssid}, null);
		if (c.moveToFirst()) {
			// ssid matches, only concerned if bssid is empty
			network = c.getInt(c.getColumnIndex(Networks._ID));
			if (c.getString(c.getColumnIndex(Networks.BSSID)).equals("")) {
				ContentValues values = new ContentValues();
				values.put(Networks.BSSID, bssid);
				this.getContentResolver().update(Networks.CONTENT_URI, values, Networks._ID + "=" + network, null);
			}
		} else {
			ContentValues values = new ContentValues();
			values.put(Networks.SSID, ssid);
			values.put(Networks.BSSID, bssid);
			// default to managing the network
			values.put(Networks.MANAGE, 1);
			network = Integer.parseInt(this.getContentResolver().insert(Networks.CONTENT_URI, values).getLastPathSegment());
		}
		c.close();
		return network;
	}

	private int fetchLocation(int lac) {
		// select or insert location
		if (lac > 0) {
			int location;
			Cursor c = this.getContentResolver().query(Locations.CONTENT_URI, new String[]{Locations._ID, Locations.LAC}, Locations.LAC + "=?", new String[]{Integer.toString(lac)}, null);
			if (c.moveToFirst()) location = c.getInt(c.getColumnIndex(Locations._ID));
			else {
				ContentValues values = new ContentValues();
				values.put(Locations.LAC, lac);
				location = Integer.parseInt(this.getContentResolver().insert(Locations.CONTENT_URI, values).getLastPathSegment());
			}
			c.close();
			return location;
		} else return UNKNOWN_CID;
	}

	private void createPair(int cid, int lac, long network, int rssi) {
		int cell, pair, location = fetchLocation(lac);
		// if location==-1, then match only on cid, otherwise match on location or -1
		// select or insert cell
		Cursor c = this.getContentResolver().query(Cells.CONTENT_URI, new String[]{Cells._ID, Cells.LOCATION},
				Cells.CID + "=?" + (location == UNKNOWN_CID ?
						""
						: " and (" + Cells.LOCATION + "=" + UNKNOWN_CID + " or " + Cells.LOCATION + "=?)"), (location == UNKNOWN_CID ? new String[]{Integer.toString(cid)} : new String[]{Integer.toString(cid), Integer.toString(location)}), null);
		if (c.moveToFirst()) {
			cell = c.getInt(c.getColumnIndex(Cells._ID));
			if ((location != UNKNOWN_CID) && (c.getInt(c.getColumnIndex(Cells.LOCATION)) == UNKNOWN_CID)) {
				ContentValues values = new ContentValues();
				values.put(Cells.LOCATION, location);
				this.getContentResolver().update(Cells.CONTENT_URI, values, Cells._ID + "=?", new String[]{Integer.toString(cell)});
			}
		} else {
			ContentValues values = new ContentValues();
			values.put(Cells.CID, cid);
			values.put(Cells.LOCATION, location);
			cell = Integer.parseInt(this.getContentResolver().insert(Cells.CONTENT_URI, values).getLastPathSegment());
		}
		c.close();
		// select and update or insert pair
		c = this.getContentResolver().query(Pairs.CONTENT_URI, new String[]{Pairs._ID, Pairs.RSSI_MIN, Pairs.RSSI_MAX}, Pairs.CELL + "=? and " + Pairs.NETWORK + "=?", new String[]{Integer.toString(cell), Long.toString(network)}, null);
		if (c.moveToFirst()) {
			if (rssi != UNKNOWN_RSSI) {
				pair = c.getInt(c.getColumnIndex(Pairs._ID));
				int rssi_min = c.getInt(c.getColumnIndex(Pairs.RSSI_MIN));
				int rssi_max = c.getInt(c.getColumnIndex(Pairs.RSSI_MAX));
				if (rssi_min > rssi) {
					ContentValues values = new ContentValues();
					values.put(Pairs.RSSI_MIN, rssi);
					this.getContentResolver().update(Pairs.CONTENT_URI, values, Pairs._ID + "=?", new String[]{Integer.toString(pair)});
				}
				else if ((rssi_max == UNKNOWN_RSSI) || (rssi_max < rssi)) {
					ContentValues values = new ContentValues();
					values.put(Pairs.RSSI_MAX, rssi);
					this.getContentResolver().update(Pairs.CONTENT_URI, values, Pairs._ID + "=?", new String[]{Integer.toString(pair)});
				}
			}
		} else {
			ContentValues values = new ContentValues();
			values.put(Pairs.CELL, cell);
			values.put(Pairs.NETWORK, network);
			values.put(Pairs.RSSI_MIN, rssi);
			values.put(Pairs.RSSI_MAX, rssi);
			values.put(Pairs.MANAGE_CELL, 1);
			this.getContentResolver().insert(Pairs.CONTENT_URI, values);
		}
		c.close();
	}

	private boolean cellInRange(int cid, int lac, int rssi) {
		Cursor c = this.getContentResolver().query(Ranges.CONTENT_URI, new String[]{Ranges._ID, Ranges.LOCATION}, Ranges.CID + "=? and (" + Ranges.LAC + "=? or " + Ranges.LOCATION + "=" + UNKNOWN_CID + ") and "
				+ Ranges.MANAGE + "=1"
				+ Ranges.MANAGE_CELL + "=1"
				+ (rssi == UNKNOWN_RSSI
						? ""
								: " and (((" + Ranges.RSSI_MIN + "=" + UNKNOWN_RSSI + ") or (" + Ranges.RSSI_MIN + "<=?)) and (" + Ranges.RSSI_MAX + ">=?))"), (rssi == UNKNOWN_RSSI ? new String[]{Integer.toString(cid), Integer.toString(lac)} : new String[]{Integer.toString(cid), Integer.toString(lac), Integer.toString(rssi), Integer.toString(rssi)}), null);
		boolean inRange = c.moveToFirst();
		if (inRange && (lac > 0)) {
			// check LAC, as this is a new column
			if (c.isNull(c.getColumnIndex(Ranges.LOCATION))) {
				ContentValues values = new ContentValues();
				values.put(Ranges.LOCATION, fetchLocation(lac));
				this.getContentResolver().update(Cells.CONTENT_URI, values, Cells._ID + "=?", new String[]{Integer.toString(c.getInt(c.getColumnIndex(Ranges._ID)))});
			}
		}
		c.close();
		return inRange;
	}

}