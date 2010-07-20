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

import static com.piusvelte.wapdroid.WapdroidDbAdapter.CELLS_CID;
import static com.piusvelte.wapdroid.WapdroidDbAdapter.CELLS_LOCATION;
import static com.piusvelte.wapdroid.WapdroidDbAdapter.LOCATIONS_LAC;
import static com.piusvelte.wapdroid.WapdroidDbAdapter.PAIRS_RSSI_MIN;
import static com.piusvelte.wapdroid.WapdroidDbAdapter.PAIRS_RSSI_MAX;
import static android.telephony.NeighboringCellInfo.UNKNOWN_CID;
import static android.telephony.NeighboringCellInfo.UNKNOWN_RSSI;

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
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
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
	private WapdroidDbAdapter mDbHelper;
	private NotificationManager mNotificationManager;
	public TelephonyManager mTeleManager;
	private String mSsid = "",
	mBssid = "",
	mOperator = "";
	private List<NeighboringCellInfo> mNeighboringCells;
	private WifiManager mWifiManager;
	private int mCid = UNKNOWN_CID,
	mLac = UNKNOWN_CID,
	mRssi = UNKNOWN_RSSI,
	mLastWifiState = WifiManager.WIFI_STATE_UNKNOWN,
	mNotifications = 0;
	public int mInterval,
	mBatteryLimit = 0,
	mLastBattPerc;
	public boolean mManageWifi,
	mRelease = false,
	mManualOverride = false,
	mLastScanEnableWifi = false;
	public static boolean mApi7;
	public AlarmManager mAlarmMgr;
	public PendingIntent mPendingIntent;
	public IWapdroidUI mWapdroidUI;
	private BroadcastReceiver mScreenReceiver, mNetworkReceiver, mWifiReceiver, mBatteryReceiver;
	public PhoneStateListener mPhoneListener;
	public WapdroidService mService;

	private final IWapdroidService.Stub mWapdroidService = new IWapdroidService.Stub() {
		public void updatePreferences(boolean manage, int interval, boolean notify, boolean vibrate, boolean led, boolean ringtone, boolean batteryOverride, int batteryPercentage)
		throws RemoteException {
			if ((mManageWifi ^ manage) || ((mNotificationManager != null) ^ notify)) {
				if (manage && notify) {
					if (mNotificationManager == null) mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
					createNotification((mLastWifiState == WifiManager.WIFI_STATE_ENABLED), false);
				} else if (mNotificationManager != null) {
					mNotificationManager.cancel(NOTIFY_ID);
					mNotificationManager = null;
				}
			}
			mManageWifi = manage;
			mInterval = interval;
			mNotifications = 0;
			if (vibrate) mNotifications |= Notification.DEFAULT_VIBRATE;
			if (led) mNotifications |= Notification.DEFAULT_LIGHTS;
			if (ringtone) mNotifications |= Notification.DEFAULT_SOUND;
			int limit = batteryOverride ? batteryPercentage : 0;
			if (limit != mBatteryLimit) batteryLimitChanged(limit);
		}

		public void setCallback(IBinder mWapdroidUIBinder)
		throws RemoteException {
			if (mWapdroidUIBinder != null) {
				if (ManageWakeLocks.hasLock()) ManageWakeLocks.release();
				mWapdroidUI = IWapdroidUI.Stub.asInterface(mWapdroidUIBinder);
				if (mWapdroidUI != null) {
					// may have returned from wifi systems
					mManualOverride = false;
					// register battery receiver for ui, if not already registered
					if (mBatteryReceiver == null) {
						mBatteryReceiver = new BatteryReceiver();
						IntentFilter f = new IntentFilter();
						f.addAction(Intent.ACTION_BATTERY_CHANGED);
						registerReceiver(mBatteryReceiver, f);
					}
					// listen to phone changes if a low battery condition caused this to stop
					if ((mPhoneListener == null)) mTeleManager.listen(mPhoneListener = (mApi7 ? (new PhoneListenerApi7(mService)) : (new PhoneListenerApi3(mService))), (PhoneStateListener.LISTEN_CELL_LOCATION | PhoneStateListener.LISTEN_SIGNAL_STRENGTH | LISTEN_SIGNAL_STRENGTHS));
					try {
						mWapdroidUI.setOperator(mOperator);
						mWapdroidUI.setCellInfo(mCid, mLac);
						mWapdroidUI.setWifiInfo(mLastWifiState, mSsid, mBssid);
						mWapdroidUI.setSignalStrength(mRssi);
						mWapdroidUI.setCells(cellsQuery());
						mWapdroidUI.setBattery(mLastBattPerc);
					} catch (RemoteException e) {}
				} else {
					// stop any receivers or listeners that were starting just for ui
					if ((mBatteryReceiver != null) && (mBatteryLimit == 0)) {
						unregisterReceiver(mBatteryReceiver);
						mBatteryReceiver = null;
					}
					if ((mLastBattPerc < mBatteryLimit) && (mPhoneListener != null)) {
						mTeleManager.listen(mPhoneListener, PhoneStateListener.LISTEN_NONE);
						mPhoneListener = null;
						}
				}
			}
		}
		public void manualOverride() throws RemoteException {
			mManualOverride = true;
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
	};

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
		// setting the wifi state is done in onCreate also, but it's need here for running in the background
		//the receivers should handle this
		//mWifiState = mWifiManager.getWifiState();
		//wifiStateChanged(mWifiState == WifiManager.WIFI_STATE_ENABLED);
		// if wifi or network receiver took a lock, and the alarm went off, stop them from releasing the lock
		mRelease = false;
		// initialize the cell info
		// celllocation may be null
		CellLocation cl = mTeleManager.getCellLocation();
		if (cl != null) getCellInfo(cl);
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
		mService = this;
		mScreenReceiver = new ScreenReceiver();
		IntentFilter f = new IntentFilter();
		f.addAction(Intent.ACTION_SCREEN_OFF);
		f.addAction(Intent.ACTION_SCREEN_ON);
		registerReceiver(mScreenReceiver, f);
		Intent i = new Intent(this, BootReceiver.class);
		i.setAction(WAKE_SERVICE);
		mPendingIntent = PendingIntent.getBroadcast(this, 0, i, 0);
		SharedPreferences prefs = (SharedPreferences) getSharedPreferences(getString(R.string.key_preferences), WapdroidService.MODE_PRIVATE);
		// initialize preferences, updated by UI
		mManageWifi = prefs.getBoolean(getString(R.string.key_manageWifi), false);
		mInterval = Integer.parseInt((String) prefs.getString(getString(R.string.key_interval), "30000"));
		if (prefs.getBoolean(getString(R.string.key_notify), false)) mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		if (prefs.getBoolean(getString(R.string.key_vibrate), false)) mNotifications |= Notification.DEFAULT_VIBRATE;
		if (prefs.getBoolean(getString(R.string.key_led), false)) mNotifications |= Notification.DEFAULT_LIGHTS;
		if (prefs.getBoolean(getString(R.string.key_ringtone), false)) mNotifications |= Notification.DEFAULT_SOUND;
		batteryLimitChanged(prefs.getBoolean(getString(R.string.key_battery_override), false) ? Integer.parseInt((String) prefs.getString(getString(R.string.key_battery_percentage), "30")) : 0);
		prefs = null;
		mDbHelper = new WapdroidDbAdapter(this);
		mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		mAlarmMgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
		wifiStateChanged(mWifiManager.getWifiState());
		// to help avoid hysteresis, make sure that at least 2 consecutive scans were in/out of range
		mLastScanEnableWifi = (mLastWifiState == WifiManager.WIFI_STATE_ENABLED);
		// the ssid from wifimanager may not be null, even if disconnected, so check against the wifi state
		networkStateChanged(mLastWifiState == WifiManager.WIFI_STATE_ENABLED);
		mTeleManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		mTeleManager.listen(mPhoneListener = (mApi7 ? (new PhoneListenerApi7(mService)) : (new PhoneListenerApi3(mService))), (PhoneStateListener.LISTEN_CELL_LOCATION | PhoneStateListener.LISTEN_SIGNAL_STRENGTH | LISTEN_SIGNAL_STRENGTHS));
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (mScreenReceiver != null) {
			unregisterReceiver(mScreenReceiver);
			mScreenReceiver = null;
		}
		if (mWifiReceiver != null) {
			unregisterReceiver(mWifiReceiver);
			mWifiReceiver = null;
		}
		if (mNetworkReceiver != null) {
			unregisterReceiver(mNetworkReceiver);
			mNetworkReceiver = null;
		}
		if (mBatteryReceiver != null) {
			unregisterReceiver(mBatteryReceiver);
			mBatteryReceiver = null;
		}
		if (mPhoneListener != null) {
			mTeleManager.listen(mPhoneListener, PhoneStateListener.LISTEN_NONE);
			mPhoneListener = null;
		}
		if (mNotificationManager != null) mNotificationManager.cancel(NOTIFY_ID);
	}

	private void batteryLimitChanged(int limit) {
		mBatteryLimit = limit;
		if (mBatteryLimit > 0) {
			if (mBatteryReceiver == null) {
				mBatteryReceiver = new BatteryReceiver();
				IntentFilter f = new IntentFilter();
				f.addAction(Intent.ACTION_BATTERY_CHANGED);
				registerReceiver(mBatteryReceiver, f);
			}
		} else if (mBatteryReceiver != null){
			unregisterReceiver(mBatteryReceiver);
			mBatteryReceiver = null;
		}
	}

	public void release() {
		if (ManageWakeLocks.hasLock()) {
			if (mInterval > 0) mAlarmMgr.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + mInterval, mPendingIntent);
			ManageWakeLocks.release();
		}
	}

	public boolean getRelease() {
		return mRelease;
	}

	public void setRelease(boolean release) {
		mRelease = release;
	}

	private String cellsQuery() {
		String cells = "(" + CELLS_CID + "=" + Integer.toString(mCid)
		+ " and (" + LOCATIONS_LAC + "=" + Integer.toString(mLac) + " or " + CELLS_LOCATION + "=" + UNKNOWN_CID + ")"
		+ " and (" + Integer.toString(mRssi) + "=" + UNKNOWN_RSSI + " or (((" + PAIRS_RSSI_MIN + "=" + UNKNOWN_RSSI + ") or (" + PAIRS_RSSI_MIN + "<=" + Integer.toString(mRssi) + ")) and ((" + PAIRS_RSSI_MAX + "=" + UNKNOWN_RSSI + ") or (" + PAIRS_RSSI_MAX + ">=" + Integer.toString(mRssi) + ")))))";
		if ((mNeighboringCells != null) && !mNeighboringCells.isEmpty()) {
			for (NeighboringCellInfo nci : mNeighboringCells) {
				int rssi = (nci.getRssi() != UNKNOWN_RSSI) && (mTeleManager.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM) ? 2 * nci.getRssi() - 113 : nci.getRssi(),
						lac = UNKNOWN_CID;
				if (mNciReflectGetLac != null) {
					/* feature is supported */
					try {
						lac = nciGetLac(nci);
					} catch (IOException ie) {
						Log.e(TAG, "unexpected " + ie);
					}
				}
				cells += " and (" + CELLS_CID + "=" + Integer.toString(nci.getCid())
				+ " and (" + LOCATIONS_LAC + "=" + lac + " or " + CELLS_LOCATION + "=" + UNKNOWN_CID + ")"
				+ " and (" + Integer.toString(rssi) + "=" + UNKNOWN_RSSI + " or (((" + PAIRS_RSSI_MIN + "=" + UNKNOWN_RSSI + ") or (" + PAIRS_RSSI_MIN + "<=" + Integer.toString(rssi) + ")) and ((" + PAIRS_RSSI_MAX + "=" + UNKNOWN_RSSI + ") or (" + PAIRS_RSSI_MAX + ">=" + Integer.toString(rssi) + ")))))";
			}
		}
		return cells;
	}

	public void getCellInfo(CellLocation location) {
		mNeighboringCells = mTeleManager.getNeighboringCellInfo();
		if (mOperator == "") mOperator = mTeleManager.getNetworkOperator();
		if (mTeleManager.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM) {
			mCid = ((GsmCellLocation) location).getCid() > 0 ? ((GsmCellLocation) location).getCid() : UNKNOWN_CID;
			mLac = ((GsmCellLocation) location).getLac() > 0 ? ((GsmCellLocation) location).getLac() : UNKNOWN_CID;
		} else if (mTeleManager.getPhoneType() == PHONE_TYPE_CDMA) {
			// check the phone type, cdma is not available before API 2.0, so use a wrapper
			try {
				CdmaCellLocation cdma = new CdmaCellLocation(location);
				mCid = cdma.getBaseStationId() > 0 ? cdma.getBaseStationId() : UNKNOWN_CID;
				mLac = cdma.getNetworkId() > 0 ? cdma.getNetworkId() : UNKNOWN_CID;
			} catch (Throwable t) {
				Log.e(TAG, "unexpected " + t);
				mCid = UNKNOWN_CID;
				mLac = UNKNOWN_CID;
			}
		}
		if (mCid != UNKNOWN_CID) {
			// allow unknown mRssi, since signalStrengthChanged isn't reliable enough by itself
			signalStrengthChanged(UNKNOWN_RSSI);
			if (mWapdroidUI != null) {
				try {
					mWapdroidUI.setOperator(mOperator);
					mWapdroidUI.setCellInfo(mCid, mLac);
					mWapdroidUI.setSignalStrength(mRssi);
					mWapdroidUI.setCells(cellsQuery());
				} catch (RemoteException e) {}
			}
		}
	}

	public void signalStrengthChanged(int rssi) {
		mRssi = rssi;
		if (mWapdroidUI != null) {
			try {
				mWapdroidUI.setSignalStrength(mRssi);
			} catch (RemoteException e) {}
		}
		// initialize enableWifi as mLastScanEnableWifi, so that wakelock is released by default
		boolean enableWifi = mLastScanEnableWifi;
		// allow unknown mRssi, since signalStrengthChanged isn't reliable enough by itself
		// check that the service is in control, and minimum values are set
		if (mManageWifi
				&& !mManualOverride
				&& (mCid != UNKNOWN_CID)
				&& (mDbHelper != null)) {
			mDbHelper.open();
			enableWifi = mDbHelper.cellInRange(mCid, mLac, mRssi);
			if ((mLastWifiState == WifiManager.WIFI_STATE_ENABLED) && (mSsid != null) && (mBssid != null)) updateRange();
			// needs to be !enableWifi, either turning off, or (turning on && battery is above limit)
			// to avoid hysteresis when on the edge of a network, require 2 consecutive, identical results before affecting a change
			// make sure that the wifi isn't already in the correct state
			else if ((!enableWifi || (mLastBattPerc >= mBatteryLimit))
					&& (mLastScanEnableWifi == enableWifi)
					&& (enableWifi ^ ((mLastWifiState == WifiManager.WIFI_STATE_ENABLED) || (mLastWifiState == WifiManager.WIFI_STATE_ENABLING)))) {
				if (enableWifi) {
					// confirm neighbors before enabling as cell tower range is greater than that of wifi
					for (NeighboringCellInfo nci : mNeighboringCells) {
						int cid = nci.getCid() > 0 ? nci.getCid() : UNKNOWN_CID,
								nci_rssi = (nci.getRssi() != UNKNOWN_RSSI) && (mTeleManager.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM) ? 2 * nci.getRssi() - 113 : nci.getRssi(),
										lac = UNKNOWN_CID;
								if (mNciReflectGetLac != null) {
									/* feature is supported */
									try {
										lac = nciGetLac(nci);
									} catch (IOException ie) {
										Log.e(TAG, "unexpected " + ie);
									}
								}
								// break on out of range result
								if (cid != UNKNOWN_CID) enableWifi = mDbHelper.cellInRange(cid, lac, nci_rssi);
								if (!enableWifi) break;
					}
					if (enableWifi) setWifiState(enableWifi);
				}
				else setWifiState(enableWifi);
			}
			mDbHelper.close();
		}
		// only release the service if it doesn't appear that we're entering or leaving a network
		if (enableWifi == mLastScanEnableWifi) release();
		else mLastScanEnableWifi = enableWifi;
	}

	private void updateRange() {
		int network = mDbHelper.updateNetworkRange(mSsid, mBssid, mCid, mLac, mRssi);
		for (NeighboringCellInfo nci : mNeighboringCells) {
			int cid = nci.getCid() > 0 ? nci.getCid() : UNKNOWN_CID,
					rssi = (nci.getRssi() != UNKNOWN_RSSI) && (mTeleManager.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM) ? 2 * nci.getRssi() - 113 : nci.getRssi(),
							lac = UNKNOWN_CID;
					if (mNciReflectGetLac != null) {
						/* feature is supported */
						try {
							lac = nciGetLac(nci);
						} catch (IOException ie) {
							Log.e(TAG, "unexpected " + ie);
						}
					}
					if (cid != UNKNOWN_CID) mDbHelper.createPair(cid, lac, network, rssi);
		}
	}

	public void setWifiState(boolean enable) {
		/*
		 *  when a low battery disabled occurs,
		 *  register the wifi receiver in case the network is connected at the time
		 */
		if (!enable && (mSsid != null)) {
			if (mWifiReceiver == null) {
				mWifiReceiver = new WifiReceiver();
				IntentFilter f = new IntentFilter();
				f.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
				registerReceiver(mWifiReceiver, f);
			}			
		}
		mWifiManager.setWifiEnabled(enable);
	}

	public void networkStateChanged(boolean connected) {
		/*
		 * get network state
		 * the ssid from wifimanager may not be null, even if disconnected, so taking boolean connected
		 * when network connected, unregister wifi receiver
		 * when network disconnected, register wifi receiver
		 */
		mSsid = connected ? mWifiManager.getConnectionInfo().getSSID() : null;
		mBssid = connected ? mWifiManager.getConnectionInfo().getBSSID() : null;
		if (mSsid != null) {
			// connected, implies that wifi is on
			if ((mBssid != null) && (mCid != UNKNOWN_CID) && (mDbHelper != null)) {
				mDbHelper.open();
				updateRange();
				mDbHelper.close();
			}
			// the network receiver will be registered if connected
			if (mWifiReceiver != null) {
				unregisterReceiver(mWifiReceiver);
				mWifiReceiver = null;
			}
		} else {
			// if there's no connection, then fallback onto wifi receiver
			if (mWifiReceiver == null) {
				mWifiReceiver = new WifiReceiver();
				IntentFilter f = new IntentFilter();
				f.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
				registerReceiver(mWifiReceiver, f);
			}
		}
		if (mWapdroidUI != null) {
			try {
				mWapdroidUI.setWifiInfo(mLastWifiState, mSsid, mBssid);
			} catch (RemoteException e) {}
		}
	}

	public String getSsid() {
		return mSsid;
	}

	private void createNotification(boolean enabled, boolean update) {
		if (mManageWifi) {
			CharSequence contentTitle = getString(R.string.label_WIFI) + " " + getString(enabled ? R.string.label_enabled : R.string.label_disabled);
			Notification notification = new Notification((enabled ? R.drawable.statuson : R.drawable.scanning), contentTitle, System.currentTimeMillis());
			Intent i = new Intent(getBaseContext(), WapdroidUI.class);
			PendingIntent contentIntent = PendingIntent.getActivity(getBaseContext(), 0, i, 0);
			notification.setLatestEventInfo(getBaseContext(), contentTitle, getString(R.string.app_name), contentIntent);
			notification.flags |= Notification.FLAG_NO_CLEAR;
			if (update) notification.defaults = mNotifications;
			mNotificationManager.notify(NOTIFY_ID, notification);
		}
	}

	public void wifiStateChanged(int state) {
		/*
		 * get wifi state
		 * initially, lastWifiState is unknown, otherwise state is evaluated either enabled or not
		 * when wifi enabled, register network receiver
		 * when wifi not enabled, unregister network receiver
		 */
		if (state != WifiManager.WIFI_STATE_UNKNOWN) {
			if (state == WifiManager.WIFI_STATE_ENABLED) {
				// listen for a connection
				if (mNetworkReceiver == null) {
					mNetworkReceiver = new NetworkReceiver();
					IntentFilter f = new IntentFilter();
					f.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
					registerReceiver(mNetworkReceiver, f);
				}
			} else if (state != WifiManager.WIFI_STATE_ENABLING) {
				// network receiver isn't need if wifi is off
				if (mNetworkReceiver != null) {
					unregisterReceiver(mNetworkReceiver);
					mNetworkReceiver = null;
				}
			}
			// notify, when onCreate (no led, ringtone, vibrate), or a change to enabled or disabled
			if ((mNotificationManager != null)
					&& ((mLastWifiState == WifiManager.WIFI_STATE_UNKNOWN)
							|| ((state == WifiManager.WIFI_STATE_DISABLED) && (mLastWifiState != WifiManager.WIFI_STATE_DISABLED))
							|| ((state == WifiManager.WIFI_STATE_ENABLED) && (mLastWifiState != WifiManager.WIFI_STATE_ENABLED))))  createNotification((state == WifiManager.WIFI_STATE_ENABLED), (mLastWifiState != WifiManager.WIFI_STATE_UNKNOWN));
			mLastWifiState = state;
			if (mWapdroidUI != null) {
				try {
					mWapdroidUI.setWifiInfo(mLastWifiState, mSsid, mBssid);
				} catch (RemoteException e) {}
			}
		}
	}
}