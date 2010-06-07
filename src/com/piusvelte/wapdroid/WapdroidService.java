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
	private String mSsid = "",
	mBssid = "",
	mOperator = "";
	private List<NeighboringCellInfo> mNeighboringCells;
	private WifiManager mWifiManager;
	private int mCid = WapdroidDbAdapter.UNKNOWN_CID,
	mLac = WapdroidDbAdapter.UNKNOWN_CID,
	mRssi = WapdroidDbAdapter.UNKNOWN_RSSI,
	mWifiState,
	mInterval,
	mBatteryLimit = 0,
	mBatteryRemaining;
	private boolean mWifiIsEnabled,
	mNotify,
	mVibrate,
	mLed,
	mRingtone,
	mInRange = true,
	mScreenOff = true;
	private AlarmManager mAlarmMgr;
	private PendingIntent mPendingIntent;
	private IWapdroidUI mWapdroidUI;
	private boolean mControlWifi = true;
	private static final String TAG = "Wapdroid";

	private final IWapdroidService.Stub mWapdroidService = new IWapdroidService.Stub() {
		public void updatePreferences(int interval, boolean notify,
				boolean vibrate, boolean led, boolean ringtone, boolean batteryOverride, int batteryPercentage)
		throws RemoteException {
			mInterval = interval;
			if (mNotify && !notify) {
				mNotificationManager.cancel(NOTIFY_ID);
				mNotificationManager = null;
			}
			else if (!mNotify && notify) {
				mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
				CharSequence contentTitle = getString(mWifiIsEnabled ? R.string.label_enabled : R.string.label_disabled);
				Notification notification = new Notification((mWifiIsEnabled ? R.drawable.scanning : R.drawable.status), contentTitle, System.currentTimeMillis());
				PendingIntent contentIntent = PendingIntent.getActivity(getBaseContext(), 0, new Intent(getBaseContext(), WapdroidService.class), 0);
				notification.setLatestEventInfo(getBaseContext(), contentTitle, getString(R.string.app_name), contentIntent);
				mNotificationManager.notify(NOTIFY_ID, notification);
			}
			mNotify = notify;
			mVibrate = vibrate;
			mLed = led;
			mRingtone = ringtone;// override && limit == 0, !override && limit > 0
			int limit = batteryOverride ? batteryPercentage : 0;
			if (limit != mBatteryLimit) batteryAction(limit);
		}
		public void setCallback(IBinder mWapdroidUIBinder)
		throws RemoteException {
			if (mWapdroidUIBinder != null) {
				mControlWifi = true;
				mScreenOff = false;
				if (ManageWakeLocks.hasLock()) ManageWakeLocks.release();
				mWapdroidUI = IWapdroidUI.Stub.asInterface(mWapdroidUIBinder);
				if (mWapdroidUI != null) {
					// if the service isn't running in the background, then register the wifi receiver
					if (mWifiReceiver == null) {
						Log.v(TAG,"register wifi receiver");
						mWifiReceiver = new BroadcastReceiver() {
							@Override
							public void onReceive(Context context, Intent intent) {
								if (intent.getAction().equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
									Log.v(TAG,"WIFI_STATE_CHANGED_ACTION");
									// if wifi is toggling, then it was probably caused by wapdroid, don't wait for another cell change
									//acquire();
									int mWifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 4);
									if (mWifiState == WifiManager.WIFI_STATE_ENABLED) {
										getWifiState(true);
									}
									else if (mWifiState != WifiManager.WIFI_STATE_UNKNOWN) {
										mSsid = null;
										mBssid = null;
										getWifiState(false);
									}
									updateUiWifi();
								}
							}
						};
						IntentFilter f = new IntentFilter();
						f.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
						registerReceiver(mWifiReceiver, f);
					}
					// register battery receiver for ui, if not already registered
					if (mBatteryReceiver == null) {
						Log.v(TAG,"register battery receiver for UI");
						mBatteryReceiver = new BroadcastReceiver() {
							@Override
							public void onReceive(Context context, Intent intent) {
								if (intent.getAction().equals(Intent.ACTION_BATTERY_CHANGED)) {
									Log.v(TAG,"ACTION_BATTERY_CHANGED");
									// don't wait for cell changes as this occurs too often, killing the battery
									//acquire();
									mBatteryRemaining = Math.round(intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100));
									Log.v(TAG,"battery:"+Integer.toString(mBatteryRemaining));
									if (mBatteryRemaining < mBatteryLimit) mWifiManager.setWifiEnabled(false);
									if (mWapdroidUI != null) {
										try {
											mWapdroidUI.setBattery(mBatteryRemaining);
										}
										catch (RemoteException e) {}
									}
								}
							}
						};
						IntentFilter f = new IntentFilter();
						f.addAction(Intent.ACTION_BATTERY_CHANGED);
						registerReceiver(mBatteryReceiver, f);
					}
					try {
						mWapdroidUI.setOperator(mOperator);
						mWapdroidUI.setCellInfo(mCid, mLac);
						mWapdroidUI.setWifiInfo(mWifiState, mSsid, mBssid);
						mWapdroidUI.setSignalStrength(mRssi);
						mWapdroidUI.setCells(cellsQuery());
						mWapdroidUI.setBattery(mBatteryRemaining);
						mWapdroidUI.inRange(mInRange);
					}
					catch (RemoteException e) {}
				}
				else if ((mBatteryReceiver != null) && (mBatteryLimit == 0)) {
					Log.v(TAG,"unregister battery receiver for UI");
					unregisterReceiver(mBatteryReceiver);
					mBatteryReceiver = null;
				}
			}
		}
		public void suspendWifiControl() throws RemoteException {
			mControlWifi = false;
		}
	};

	private final PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
		public void onCellLocationChanged(CellLocation location) {
			Log.v(TAG,"onCellLocationChanged");
			getCellInfo(location);
		}
		public void onSignalStrengthChanged(int asu) {
			Log.v(TAG,"onSignalStrengthChanged");
			if (mTeleManager.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM) {
				if (asu != WapdroidDbAdapter.UNKNOWN_RSSI) mRssi = 2 * asu - 113;
				signalStrengthChanged();
			}
			else release();
		}
		public void onSignalStrengthsChanged(SignalStrength signalStrength) {
			Log.v(TAG,"onSignalStrengthsChanged");
			if (mTeleManager.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM) {
				if (signalStrength.getGsmSignalStrength() != WapdroidDbAdapter.UNKNOWN_RSSI) {
					mRssi = 2 * signalStrength.getGsmSignalStrength() - 113;
					signalStrengthChanged();
				}
			}
			else if (mTeleManager.getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA) {
				mRssi = signalStrength.getCdmaDbm() < signalStrength.getEvdoDbm() ?
						signalStrength.getCdmaDbm()
						: signalStrength.getEvdoDbm();
						signalStrengthChanged();
			}
			else release();
		}
	};

	private BroadcastReceiver mScreenReceiver, mNetworkReceiver, mWifiReceiver, mBatteryReceiver;

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
		Log.v(TAG,"initializing the service");
		mWifiState = mWifiManager.getWifiState();
		getWifiState(mWifiState == WifiManager.WIFI_STATE_ENABLED);
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
		mScreenReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
					Log.v(TAG,"ACTION_SCREEN_ON");
					mScreenOff = false;
					mAlarmMgr.cancel(mPendingIntent);
					ManageWakeLocks.release();
					if (mWifiReceiver == null) {
						Log.v(TAG,"register wifi receiver");
						mWifiReceiver = new BroadcastReceiver() {
							@Override
							public void onReceive(Context context, Intent intent) {
								if (intent.getAction().equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
									Log.v(TAG,"WIFI_STATE_CHANGED_ACTION");
									// if wifi is toggling, then it was probably caused by wapdroid, don't wait for another cell change
									//acquire();
									int mWifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 4);
									if (mWifiState == WifiManager.WIFI_STATE_ENABLED) {
										getWifiState(true);
									}
									else if (mWifiState != WifiManager.WIFI_STATE_UNKNOWN) {
										mSsid = null;
										mBssid = null;
										getWifiState(false);
									}
									updateUiWifi();
								}
							}
						};
						IntentFilter f = new IntentFilter();
						f.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
						registerReceiver(mWifiReceiver, f);
					}
					context.startService(new Intent(context, WapdroidService.class));
				}
				else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
					Log.v(TAG, "ACTION_SCREEN_OFF");
					mScreenOff = true;
					mControlWifi = true;
					if (mWifiReceiver != null) {
						Log.v(TAG,"unregister wifi receiver");
						unregisterReceiver(mWifiReceiver);
						mWifiReceiver = null;
					}
					if (mInterval > 0) mAlarmMgr.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + mInterval, mPendingIntent);
				}
			}
		};
		IntentFilter f = new IntentFilter();
		f.addAction(Intent.ACTION_SCREEN_OFF);
		f.addAction(Intent.ACTION_SCREEN_ON);
		registerReceiver(mScreenReceiver, f);
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
		batteryAction(prefs.getBoolean(getString(R.string.key_battery_override), false) ? Integer.parseInt((String) prefs.getString(getString(R.string.key_battery_percentage), "30")) : 0);
		prefs = null;
		mDbHelper = new WapdroidDbAdapter(this);
		mTeleManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		mTeleManager.listen(mPhoneStateListener, (PhoneStateListener.LISTEN_CELL_LOCATION | PhoneStateListener.LISTEN_SIGNAL_STRENGTH| PhoneStateListener.LISTEN_SIGNAL_STRENGTHS));
		mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		mAlarmMgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
		// notify in onCreate, instead of init, as init will be called by each UI activity
		if (mNotify) {
			// wifi state is needed for the notification, though it'll be set again in init
			mWifiState = mWifiManager.getWifiState();
			getWifiState(mWifiState == WifiManager.WIFI_STATE_ENABLED);
			CharSequence contentTitle = getString(mWifiIsEnabled ? R.string.label_enabled : R.string.label_disabled);
			Notification notification = new Notification((mWifiIsEnabled ? R.drawable.statuson : R.drawable.scanning), contentTitle, System.currentTimeMillis());
			PendingIntent contentIntent = PendingIntent.getActivity(getBaseContext(), 0, new Intent(getBaseContext(), WapdroidUI.class), 0);
			notification.setLatestEventInfo(getBaseContext(), contentTitle, getString(R.string.app_name), contentIntent);
			mNotificationManager.notify(NOTIFY_ID, notification);
		}
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
		mTeleManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
		if (mNotify && (mNotificationManager != null)) mNotificationManager.cancel(NOTIFY_ID);
	}

	private void batteryAction(int limit) {
		mBatteryLimit = limit;
		if (mBatteryLimit > 0) {
			if (mBatteryReceiver == null) {
				Log.v(TAG,"register battery receiver");
				mBatteryReceiver = new BroadcastReceiver() {
					@Override
					public void onReceive(Context context, Intent intent) {
						if (intent.getAction().equals(Intent.ACTION_BATTERY_CHANGED)) {
							Log.v(TAG,"ACTION_BATTERY_CHANGED");
							// don't wait for cell changes as this occurs too often, killing the battery
							//acquire();
							mBatteryRemaining = Math.round(intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100));
							Log.v(TAG,"battery:"+Integer.toString(mBatteryRemaining));
							if (mBatteryRemaining < mBatteryLimit) setWifiState(false);
							if (mWapdroidUI != null) {
								try {
									mWapdroidUI.setBattery(mBatteryRemaining);
								}
								catch (RemoteException e) {}
							}}
					}};
					IntentFilter f = new IntentFilter();
					f.addAction(Intent.ACTION_BATTERY_CHANGED);
					registerReceiver(mBatteryReceiver, f);
			}
		}
		else if (mBatteryReceiver != null){
			Log.v(TAG,"unregister battery receiver");
			unregisterReceiver(mBatteryReceiver);
			mBatteryReceiver = null;
		}
	}

	private void acquire() {
		/*  
		 * the alarm acquires a lock
		 * so awakened by something
		 * other than the alarm, lock and start
		 */
		Log.v(TAG,"screen is "+(mScreenOff?"off":"on"));
		Log.v(TAG,"locked "+(ManageWakeLocks.hasLock()?"yes":"no"));
		if (mScreenOff && !ManageWakeLocks.hasLock()) {
			Log.v(TAG,"awake, cancel alarm, broadcast");
			mAlarmMgr.cancel(mPendingIntent);
			sendBroadcast(new Intent(this, BootReceiver.class).setAction(WAKE_SERVICE));
		}
	}

	private void release() {
		if (ManageWakeLocks.hasLock()) {
			if (mInterval > 0) mAlarmMgr.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + mInterval, mPendingIntent);
			ManageWakeLocks.release();
		}
	}

	private String cellsQuery() {
		String cells = "(" + WapdroidDbAdapter.CELLS_CID + "=" + Integer.toString(mCid)
		+ " and (" + WapdroidDbAdapter.LOCATIONS_LAC + "=" + Integer.toString(mLac) + " or " + WapdroidDbAdapter.CELLS_LOCATION + "=" + WapdroidDbAdapter.UNKNOWN_CID + ")"
		+ " and (" + Integer.toString(mRssi) + "=" + WapdroidDbAdapter.UNKNOWN_RSSI + " or (((" + WapdroidDbAdapter.PAIRS_RSSI_MIN + "=" + WapdroidDbAdapter.UNKNOWN_RSSI + ") or (" + WapdroidDbAdapter.PAIRS_RSSI_MIN + "<=" + Integer.toString(mRssi) + ")) and ((" + WapdroidDbAdapter.PAIRS_RSSI_MAX + "=" + WapdroidDbAdapter.UNKNOWN_RSSI + ") or (" + WapdroidDbAdapter.PAIRS_RSSI_MAX + ">=" + Integer.toString(mRssi) + ")))))";
		if (!mNeighboringCells.isEmpty()) {
			for (NeighboringCellInfo n : mNeighboringCells) {
				int rssi = (n.getRssi() != WapdroidDbAdapter.UNKNOWN_RSSI) && (mTeleManager.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM) ? 2 * n.getRssi() - 113 : n.getRssi();
				cells += " or (" + WapdroidDbAdapter.CELLS_CID + "=" + Integer.toString(n.getCid())
				+ " and (" + WapdroidDbAdapter.LOCATIONS_LAC + "=" + Integer.toString(n.getLac()) + " or " + WapdroidDbAdapter.CELLS_LOCATION + "=" + WapdroidDbAdapter.UNKNOWN_CID + ")"
				+ " and (" + Integer.toString(rssi) + "=" + WapdroidDbAdapter.UNKNOWN_RSSI + " or (((" + WapdroidDbAdapter.PAIRS_RSSI_MIN + "=" + WapdroidDbAdapter.UNKNOWN_RSSI + ") or (" + WapdroidDbAdapter.PAIRS_RSSI_MIN + "<=" + Integer.toString(rssi) + ")) and ((" + WapdroidDbAdapter.PAIRS_RSSI_MAX + "=" + WapdroidDbAdapter.UNKNOWN_RSSI + ") or (" + WapdroidDbAdapter.PAIRS_RSSI_MAX + ">=" + Integer.toString(rssi) + ")))))";
			}
		}
		return cells;
	}

	private void getCellInfo(CellLocation location) {
		mRssi = WapdroidDbAdapter.UNKNOWN_RSSI;
		mNeighboringCells = mTeleManager.getNeighboringCellInfo();
		if (mOperator == "") mOperator = mTeleManager.getNetworkOperator();
		if (mTeleManager.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM) {
			if (((GsmCellLocation) location).getCid() > 0) mCid = ((GsmCellLocation) location).getCid();
			if (((GsmCellLocation) location).getLac() > 0) mLac = ((GsmCellLocation) location).getLac();
		}
		else if (mTeleManager.getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA) {
			// check the phone type, cdma is not available before API 2.0, so use a wrapper
			try {
				CdmaCellLocation cdma = new CdmaCellLocation(location);
				if (cdma.getBaseStationId() > 0) mCid = cdma.getBaseStationId();
				if (cdma.getNetworkId() > 0) mLac = cdma.getNetworkId();
			}
			catch (Throwable t) {
				mCid = WapdroidDbAdapter.UNKNOWN_CID;
				mLac = WapdroidDbAdapter.UNKNOWN_CID;
			}
		}
		if (mCid != WapdroidDbAdapter.UNKNOWN_CID) {
			signalStrengthChanged();
			if (mWapdroidUI != null) {
				try {
					mWapdroidUI.setOperator(mOperator);
					mWapdroidUI.setCellInfo(mCid, mLac);
					mWapdroidUI.setSignalStrength(mRssi);
					mWapdroidUI.setCells(cellsQuery());
				}
				catch (RemoteException e) {}
			}
		}
	}

	private void signalStrengthChanged() {
		Log.v(TAG,"signalStrengthChanged");
		if (mWapdroidUI != null) {
			try {
				mWapdroidUI.setSignalStrength(mRssi);
			}
			catch (RemoteException e) {}
		}
		if ((mCid != WapdroidDbAdapter.UNKNOWN_CID) && (mRssi != WapdroidDbAdapter.UNKNOWN_RSSI) && (mDbHelper != null)) {
			mDbHelper.open();
			mInRange = mDbHelper.cellInRange(mCid, mLac, mRssi);
			if (mWapdroidUI != null) {
				try {
					mWapdroidUI.inRange(mInRange);
				}
				catch (RemoteException e) {}
			}
			if (mWifiIsEnabled && (mSsid != null) && (mBssid != null)) updateRange();
			else if (mControlWifi && mInRange) {
				for (NeighboringCellInfo n : mNeighboringCells) {
					int cid = n.getCid(),
					lac = n.getLac(),
					rssi = (n.getRssi() != WapdroidDbAdapter.UNKNOWN_RSSI) && (mTeleManager.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM) ? 2 * n.getRssi() - 113 : n.getRssi();
					if (mInRange && (cid > 0)) mInRange = mDbHelper.cellInRange(cid, lac, rssi);
				}
			}
			if ((mInRange && (mBatteryRemaining >= mBatteryLimit) && !mWifiIsEnabled && (mWifiState != WifiManager.WIFI_STATE_ENABLING)) || (!mInRange && mWifiIsEnabled)) {
				Log.v(TAG, "set wifi:"+mInRange);
				setWifiState(mInRange);
			}
			mDbHelper.close();
		}
		release();
	}

	private void updateUiWifi() {
		if (mWapdroidUI != null) {
			try {
				mWapdroidUI.setWifiInfo(mWifiState, mSsid, mBssid);
			}
			catch (RemoteException e) {}
		}}

	private void setWifiInfo() {
		mSsid = mWifiManager.getConnectionInfo().getSSID();
		mBssid = mWifiManager.getConnectionInfo().getBSSID();
		if (mWapdroidUI != null) {
			try {
				mWapdroidUI.setWifiInfo(mWifiState, mSsid, mBssid);
			}
			catch (RemoteException e) {}
		}}

	private void updateRange() {
		int network = mDbHelper.updateNetworkRange(mSsid, mBssid, mCid, mLac, mRssi);
		for (NeighboringCellInfo n : mNeighboringCells) {
			int cid = n.getCid(),
			lac = n.getLac(),
			rssi = (n.getRssi() != WapdroidDbAdapter.UNKNOWN_RSSI) && (mTeleManager.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM) ? 2 * n.getRssi() - 113 : n.getRssi();
			if (cid > 0) mDbHelper.createPair(cid, lac, network, rssi);
		}
	}
	
	private void setWifiState(boolean enable) {
		mWifiManager.setWifiEnabled(enable);
		getWifiState(enable);}

	private void getWifiState(boolean enabled) {
		if (enabled != mWifiIsEnabled){
			if (enabled) {
				if (mNetworkReceiver == null) {
					Log.v(TAG,"register network receiver");
					mNetworkReceiver = new BroadcastReceiver() {
						@Override
						public void onReceive(Context context, Intent intent) {
							if (intent.getAction().equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
								Log.v(TAG,"NETWORK_STATE_CHANGED_ACTION");
								NetworkInfo mNetworkInfo = (NetworkInfo) intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
								if (mNetworkInfo.isConnected()) {
									setWifiInfo();
									if (mWifiIsEnabled && (mSsid != null) && (mBssid != null) && (mCid != WapdroidDbAdapter.UNKNOWN_CID) && (mDbHelper != null)) {
										mDbHelper.open();
										updateRange();
										mDbHelper.close();
									}
								}
								else {
									// only check for a cell change if the network is not connected, indicating that the phone may have left the range
									// if the network is connected, then wifi is enabled, and wapdroid already knows that it's in range
									Log.v(TAG,"network not connected, check for cell changes");
									acquire();
									mSsid = null;
									mBssid = null;
								}
								updateUiWifi();
							}
						}};
						IntentFilter f = new IntentFilter();
						f.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
						registerReceiver(mNetworkReceiver, f);
				}
			}
			else {
				if (mNetworkReceiver != null) {
					Log.v(TAG,"unregister network receiver");
					unregisterReceiver(mNetworkReceiver);
					mNetworkReceiver = null;
				}
			}
			if (mNotify) {
				CharSequence contentTitle = getString(R.string.label_WIFI) + " " + getString(enabled ? R.string.label_enabled : R.string.label_disabled);
				Notification notification = new Notification((enabled ? R.drawable.statuson : R.drawable.scanning), contentTitle, System.currentTimeMillis());
				Intent i = new Intent(getBaseContext(), WapdroidService.class);
				PendingIntent contentIntent = PendingIntent.getActivity(getBaseContext(), 0, i, 0);
				notification.setLatestEventInfo(getBaseContext(), contentTitle, getString(R.string.app_name), contentIntent);
				if (mVibrate) notification.defaults |= Notification.DEFAULT_VIBRATE;
				if (mLed) notification.defaults |= Notification.DEFAULT_LIGHTS;
				if (mRingtone) notification.defaults |= Notification.DEFAULT_SOUND;
				mNotificationManager.notify(NOTIFY_ID, notification);
			}
			mWifiIsEnabled = enabled;
		}
		setWifiInfo();
	}
}