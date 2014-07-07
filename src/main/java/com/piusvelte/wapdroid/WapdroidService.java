/*
 * Wapdroid - Android Location based Wifi Manager
 * Copyright (C) 2012 Bryan Emmanuel
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
import java.util.List;

import com.piusvelte.wapdroid.Wapdroid.Cells;
import com.piusvelte.wapdroid.Wapdroid.Locations;
import com.piusvelte.wapdroid.Wapdroid.Networks;
import com.piusvelte.wapdroid.Wapdroid.Pairs;
import com.piusvelte.wapdroid.Wapdroid.Ranges;

import android.annotation.SuppressLint;
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
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
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
import android.text.TextUtils;
import android.util.Log;

public class WapdroidService extends Service implements OnSharedPreferenceChangeListener {
    // conditions:
    // screen-off > wifi-sleep?
    // wifi connection lost > scan networks
    // data connection lost > wifi on?
    // wifi on > scan networks
    // cell towers changed > in range?
    // in range > scan networks
    private static final String TAG = WapdroidService.class.getSimpleName();

    private static int NOTIFY_ID = 1;
    public static final String WAKE_SERVICE = "com.piusvelte.wapdroid.WAKE_SERVICE";
    public static final int LISTEN_SIGNAL_STRENGTHS = 256;
    public static final int PHONE_TYPE_CDMA = 2;
    private static final int START_STICKY = 1;
    private int mCid = UNKNOWN_CID;
    private int mLac = UNKNOWN_CID;
    private int mRssi = UNKNOWN_RSSI;
    private int mWiFiState = WifiManager.WIFI_STATE_UNKNOWN;
    private int mNotifications;
    long mSuspendUntil = 0;
    private int mInterval;
    private int mBatteryLimit;
    private int mLastBattPerc = 0;
    private static int mPhoneType;
    private boolean mManageWifi;
    private boolean mCellTowersInRange;
    private boolean mNotify;
    private boolean mPersistentStatus;
    private boolean mWiFiSleepScreen;
    private boolean mWiFiSleepMobNet;
    private boolean mWiFiSleepCharging;
    private boolean mWiFiOverrideCharging;
    private boolean mScanWiFi = false;
    private boolean mWapdroidToggledWiFi = true;// default to true to avoid setting mSuspendUntil onCreate
    private String mSsid;
    private String mBssid;
    private static boolean mApi7 = false;
    private IWapdroidUI mWapdroidUI;
    private BroadcastReceiver mReceiver = new Receiver();
    private static final String BATTERY_EXTRA_LEVEL = "level";
    private static final String BATTERY_EXTRA_SCALE = "scale";
    private static final String BATTERY_EXTRA_STATUS = "status";
    private WifiManager mWifiManager;
    private TelephonyManager mTelephonyManager;
    private AlarmManager mAlarmManager;
    private ConnectivityManager mConnectivityManager;

    public static PhoneStateListener mPhoneListener;
    private final IWapdroidService.Stub mWapdroidService = new IWapdroidService.Stub() {
        public void setCallback(IBinder mWapdroidUIBinder)
                throws RemoteException {
            if (mWapdroidUIBinder != null) {
                if (ManageWakeLocks.hasLock()) {
                    ManageWakeLocks.release();
                }

                mWapdroidUI = IWapdroidUI.Stub.asInterface(mWapdroidUIBinder);

                if (mWapdroidUI != null) {
                    // listen to phone changes if a low battery condition caused this to stop
                    if (mLastBattPerc < mBatteryLimit) {
                        if (requirePhoneListener()) {
                            startPhoneListener();
                        } else {
                            // TODO start geofencing?
                        }
                    }

                    updateUI();
                } else if (mLastBattPerc < mBatteryLimit) {
                    stopPhoneListener();
                }
            }
        }
    };

    private static Method mNciReflectGetLac;

    // add onSignalStrengthsChanged for api >= 7
    static {
        try {
            Class.forName("android.telephony.SignalStrength");
            mApi7 = true;
            mNciReflectGetLac = android.telephony.NeighboringCellInfo.class.getMethod("getLac", new Class[]{});
        } catch (NoSuchMethodException nsme) {
            Log.d(TAG, "api < 5, " + nsme);
        } catch (Exception ex) {
            Log.d(TAG, "api < 7, " + ex);
        }
    }

    private static int nciGetLac(NeighboringCellInfo nci) {
        int lac;

        if (mNciReflectGetLac != null) {
            try {
                lac = (Integer) mNciReflectGetLac.invoke(nci);
            } catch (InvocationTargetException ite) {
                lac = UNKNOWN_CID;
                Log.e(TAG, ite.getMessage());
            } catch (IllegalAccessException ie) {
                lac = UNKNOWN_CID;
                Log.e(TAG, ie.getMessage());
            }
        } else {
            lac = UNKNOWN_CID;
        }

        return lac;
    }

    @Override
    public IBinder onBind(Intent intent) {
        mAlarmManager.cancel(PendingIntent.getBroadcast(this, 0, Wapdroid.getPackageIntent(this, BootReceiver.class).setAction(WAKE_SERVICE), 0));
        ManageWakeLocks.release();
        return mWapdroidService;
    }

    @Override
    public void onStart(Intent intent, int startId) {
        start(intent);
    }

    private void start(Intent intent) {
        if (intent != null && !TextUtils.isEmpty(intent.getAction()) && !intent.getAction().equals(WAKE_SERVICE)) {
            String action = intent.getAction();
            Wapdroid.logInfo("start < " + action);

            if ((action.equals(ACTION_BOOT_COMPLETED) || action.equals(ACTION_PACKAGE_REPLACED)) && !mManageWifi) {
                sleep();
            } else if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
                // don't make changes to the alarm as this doesn't involve the location or networks
                int currentBattPerc = getBatteryChargedPercent(intent.getIntExtra(BATTERY_EXTRA_LEVEL, 0), intent.getIntExtra(BATTERY_EXTRA_SCALE, 100));
                Wapdroid.logInfo("currentBattPerc = " + currentBattPerc);

                if (isCharging(intent.getIntExtra(BATTERY_EXTRA_STATUS, -1))) {
                    Wapdroid.logInfo("charging");
                    // charging
                    mBatteryLimit = 0;

                    if (mManageWifi && (persistentWiFiWake() || !sleepPolicyActive()) && !wiFiEnabledOrEnabling()) {
                        mScanWiFi = false;
                        setWifiEnabled(true);
                    }

                    // if charged passed the threshold for starting the phone listener
                    if ((currentBattPerc >= mBatteryLimit) && (mLastBattPerc < mBatteryLimit)) {
                        if (requirePhoneListener()) {
                            startPhoneListener();
                        } else {
                            // TODO start geofencing?
                        }
                    }
                } else {
                    Wapdroid.logInfo("discharging");
                    // unplugged, restore the user defined battery limit
                    SharedPreferences sp = getSharedPreferences(getString(R.string.key_preferences), WapdroidService.MODE_PRIVATE);
                    mBatteryLimit = sp.getBoolean(getString(R.string.key_battery_override), false) ? Integer.parseInt((String) sp.getString(getString(R.string.key_battery_percentage), "30")) : 0;

                    // if discharged passed the threshold
                    if (mManageWifi && (currentBattPerc < mBatteryLimit) && (mLastBattPerc >= mBatteryLimit) && sleepPolicyActive() && !wiFiDisabledOrDisabling()) {
                        mScanWiFi = false;
                        // just passed threshhold
                        setWifiEnabled(false);
                        stopPhoneListener();
                    }
                }

                mLastBattPerc = currentBattPerc;

                if (mWapdroidUI != null) {
                    try {
                        mWapdroidUI.setBattery(currentBattPerc);
                    } catch (RemoteException e) {
                    }
                }

                // battery change should not reset the alarm as doing so will prevent all other events from occurring
                if (ManageWakeLocks.hasLock()) {
                    // if sleeping, re-initialize phone info
                    mCid = UNKNOWN_CID;
                    mLac = UNKNOWN_CID;
                    mRssi = UNKNOWN_RSSI;
                    ManageWakeLocks.release();
                }
            } else if (action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
                // cell change will release lock
                // a connection was gained or lost
                wifiConnection();
                // the alarm will be cancelled and set by getCellInfo
                if (requirePhoneListener()) getCellInfo(mTelephonyManager.getCellLocation());

                if (mWapdroidUI != null) {
                    try {
                        mWapdroidUI.setWifiInfo(mWiFiState, mSsid, mBssid);
                    } catch (RemoteException e) {
                    }
                }
            } else if (action.equals(Intent.ACTION_SCREEN_ON)) {
                ManageWakeLocks.setScreenState(true);
                // the alarm will be cancelled and set by getCellInfo
                if (requirePhoneListener()) getCellInfo(mTelephonyManager.getCellLocation());
            } else if (action.equals(Intent.ACTION_SCREEN_OFF)) {
                ManageWakeLocks.setScreenState(false);
                ManageWakeLocks.acquire(this);

                // check sleep policy, considering overrides
                if (sleepPolicyActive() && !wiFiDisabledOrDisabling()) {
                    // mWifiSleep override mScanWiFi
                    mScanWiFi = false;

                    // check if the current network is managed
                    if (mSsid != null) {
                        Cursor c = this.getContentResolver().query(Networks.CONTENT_URI, new String[]{Networks._ID, Networks.SSID, Networks.BSSID}, Networks.SSID + "=? and (" + Networks.BSSID + "=? or " + Networks.BSSID + "='') and " + Networks.MANAGE + "=1", new String[]{(mSsid != null ? mSsid : ""), (mBssid != null ? mBssid : "")}, null);

                        if (c.moveToFirst()) {
                            setWifiEnabled(false);
                        }

                        c.close();
                    } else {
                        setWifiEnabled(false);
                    }
                }

                // if waiting for scan results, allow them to finish
                if (!mScanWiFi) {
                    sleep();
                }
            } else if (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
                /*
                 * get wifi state
				 * initially, lastWifiState is unknown, otherwise state is evaluated either enabled or not
				 * when wifi enabled, register network receiver
				 * when wifi not enabled, unregister network receiver
				 */
                wifiState(intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 4));

                // if scanning networks, keep the lock until there are results
                if (!mScanWiFi) {
                    sleep();
                } else {
                    Wapdroid.logInfo("wifi enabled, wait scan results");
                }
            } else if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                // if mobile connection was lost, and wifi was put to sleep, enable wifi
                if (intent.hasExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY) && intent.hasExtra(ConnectivityManager.EXTRA_NETWORK_INFO) && (intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, true))) {
                    NetworkInfo ni = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);

                    if (ni.getType() == ConnectivityManager.TYPE_WIFI) {
                        // lost wifi connection, scan, check range
                        if (mWifiManager.startScan()) {
                            mScanWiFi = true;
                        } else {
                            sleep();
                        }
                    } else {
                        // enable wifi if mobile networks lost and last scan was in range
                        if (mCellTowersInRange && !sleepPolicyActive() && !wiFiEnabledOrEnabling()) {
                            mScanWiFi = false;
                            setWifiEnabled(true);
                        }

                        sleep();
                    }
                } else {
                    sleep();
                }
            } else if (action.equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) {
                // network scan
                // if the service enabled wifi, check the scan results to confirm that wifi should remain on
                // if the service wants to disable wifi, check the scan results to confirm that wifi should be disabled
                // if SSID isn't null, then the network is in range
                boolean networkInRange = (mSsid != null);

                if (networkInRange) {
                    Wapdroid.logInfo("ssid is already set, networkInRange");
                    // notification may have been postponed while a scan was performed
                    if (mScanWiFi) {
                        mScanWiFi = false;
                        createNotification((mWiFiState == WifiManager.WIFI_STATE_ENABLED), (mWiFiState != WifiManager.WIFI_STATE_UNKNOWN));
                    }
                } else {
                    List<ScanResult> lsr = mWifiManager.getScanResults();

                    if (lsr != null) {
                        int ssidCount = 0;
                        int bssidCount = 0;
                        for (ScanResult sr : lsr) {
                            if ((sr.SSID != null) && (sr.SSID.length() > 0)) {
                                ssidCount++;
                            }

                            if ((sr.BSSID != null) && (sr.BSSID.length() > 0)) {
                                bssidCount++;
                            }
                        }

                        Wapdroid.logInfo("scan results, " + ssidCount + " networks");

                        if (ssidCount > 0) {
                            String[] args = new String[ssidCount + bssidCount];
                            bssidCount = 0;
                            StringBuilder selection = new StringBuilder();
                            selection.append(Networks.SSID + " in (");

                            for (ScanResult sr : lsr) {
                                if ((sr.SSID != null) && (sr.SSID.length() > 0)) {
                                    if (bssidCount > 0) {
                                        selection.append(",");
                                    }

                                    selection.append("?");
                                    args[bssidCount++] = Wapdroid.stripQuotes(sr.SSID);
                                }
                            }

                            selection.append(")");

                            if (bssidCount < args.length) {
                                selection.append(" and (" + Networks.BSSID + " in (");

                                for (ScanResult sr : lsr) {
                                    if ((sr.BSSID != null) && (sr.BSSID.length() > 0)) {
                                        if (bssidCount > ssidCount) {
                                            selection.append(",");
                                        }

                                        selection.append("?");
                                        args[bssidCount++] = sr.BSSID;
                                    }
                                }

                                selection.append(") or " + Networks.BSSID + "='')");
                            } else {
                                selection.append(" and " + Networks.BSSID + "=''");
                            }

                            selection.append(" and " + Networks.MANAGE + "=1");
                            // TODO do off main thread
                            Cursor c = this.getContentResolver().query(Networks.CONTENT_URI, new String[]{Networks._ID}, selection.toString(), args, null);
                            networkInRange = c.moveToFirst();
                            c.close();
                        }

                        Wapdroid.logInfo("networkInRange ? " + networkInRange);
                        if (networkInRange) {
                            // notification may have been postponed while a scan was performed
                            if (mScanWiFi) {
                                mScanWiFi = false;
                                createNotification((mWiFiState == WifiManager.WIFI_STATE_ENABLED), (mWiFiState != WifiManager.WIFI_STATE_UNKNOWN));
                            }
                        } else if ((mSuspendUntil < System.currentTimeMillis())) {
                            // out of network range
                            // don't disable if override
                            if (!persistentWiFiWake() && !wiFiDisabledOrDisabling()) {
                                //TODO: should the database be updated to remove the in range cells?
                                mScanWiFi = false;
                                // only disable if the service isn't suspend, which will happen if the user has enabled the wifi
                                // prevent hysteresis near networks
                                Wapdroid.logInfo("suspending service");
                                mSuspendUntil = System.currentTimeMillis() + mInterval;
                                // network in range based on cell towers, but not found in scan, override
                                setWifiEnabled(false);
                            } else if (mScanWiFi) {
                                mScanWiFi = false;
                                createNotification((mWiFiState == WifiManager.WIFI_STATE_ENABLED), (mWiFiState != WifiManager.WIFI_STATE_UNKNOWN));
                            }
                        }
                    } else if (!mCellTowersInRange) {
                        Wapdroid.logInfo("no results, allow disable");

                        // no results
                        // don't disable if override
                        if (!persistentWiFiWake() && !wiFiDisabledOrDisabling()) {
                            //TODO: should the database be updated to remove the in range cells?
                            mScanWiFi = false;
                            // only disable if the service isn't suspend, which will happen if the user has enabled the wifi
                            // prevent hysteresis near networks
                            Wapdroid.logInfo("suspending service, cell towers out of range");
                            mSuspendUntil = System.currentTimeMillis() + mInterval;
                            // network in range based on cell towers, but not found in scan, override
                            setWifiEnabled(false);
                        } else if (mScanWiFi) {
                            mScanWiFi = false;
                            createNotification((mWiFiState == WifiManager.WIFI_STATE_ENABLED), (mWiFiState != WifiManager.WIFI_STATE_UNKNOWN));
                        }
                    }
                }

                sleep();
            } else {
                sleep();
            }
        } else {
            // if wifi is on, check connection
            // if no connection, scan network
            // if no network, check range
            // ..
            // if wifi is off, check range
            // if in range, scan network
            // if no network, wifi off, sleep
            if (requirePhoneListener()) getCellInfo(mTelephonyManager.getCellLocation());
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        start(intent);
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
        SharedPreferences sp = getSharedPreferences(getString(R.string.key_preferences), MODE_PRIVATE);
        // initialize preferences, updated by UI
        if (!sp.contains(getString(R.string.key_wifi_sleep_screen))) {
            // changed the sleep policy prefs
            boolean wifiSleep = sp.getBoolean("wifi_sleep", false);
            sp.edit()
                    .putBoolean(getString(R.string.key_wifi_sleep_screen), wifiSleep)
                    .putBoolean(getString(R.string.key_wifi_sleep_mob_net), wifiSleep)
                    .putBoolean(getString(R.string.key_wifi_sleep_charging), false)
                    .commit();
        }

        if (sp.getBoolean(getString(R.string.key_logging), false)) {
            Wapdroid.startLogging();
        } else {
            Wapdroid.stopLogging();
        }

        setupSleepPolicy(sp);
        mManageWifi = sp.getBoolean(getString(R.string.key_manageWifi), false);
        mInterval = Integer.parseInt(sp.getString(getString(R.string.key_interval), "30000"));
        setupNotificationSettings(sp);

        mBatteryLimit = sp.getBoolean(getString(R.string.key_battery_override), false) ? Integer.parseInt((String) sp.getString(getString(R.string.key_battery_percentage), "30")) : 0;
        // only register the listener when ui is invoked
        sp.registerOnSharedPreferenceChangeListener(this);
        // handle version changes
        int currVer = 0;

        try {
            currVer = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }

        if (!sp.contains(getString(R.string.key_version)) || (currVer > sp.getInt(getString(R.string.key_version), 0))) {
            sp.edit().putInt(getString(R.string.key_version), currVer).commit();
        }

        mAlarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        wifiState(mWifiManager.getWifiState());

        // initiate the notification
        if (mPersistentStatus) {
            createNotification((mWiFiState == WifiManager.WIFI_STATE_ENABLED), false);
        }

        // to help avoid hysteresis, make sure that at least 2 consecutive scans were in/out of range
        mCellTowersInRange = (mWiFiState == WifiManager.WIFI_STATE_ENABLED);
        // the ssid from wifimanager may not be null, even if disconnected, so check against the supplicant state
        wifiConnection();
        // initialize battery charge
        IntentFilter battInitfilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent battInit = registerReceiver(null, battInitfilter);
        mLastBattPerc = getBatteryChargedPercent(battInit.getIntExtra(BATTERY_EXTRA_LEVEL, 0), battInit.getIntExtra(BATTERY_EXTRA_SCALE, 100));

        if (isCharging(battInit.getIntExtra(BATTERY_EXTRA_STATUS, -1))) {
            mBatteryLimit = 0;
        }

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_BATTERY_CHANGED);
        f.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        f.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        f.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        // network scan
        f.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        registerReceiver(mReceiver, f);

        if (requirePhoneListener()) startPhoneListener();
    }

    private void setupSleepPolicy(SharedPreferences sharedPreferences) {
        mWiFiSleepScreen = sharedPreferences.getBoolean(getString(R.string.key_wifi_sleep_screen), false);
        mWiFiSleepMobNet = sharedPreferences.getBoolean(getString(R.string.key_wifi_sleep_mob_net), false);
        mWiFiSleepCharging = sharedPreferences.getBoolean(getString(R.string.key_wifi_sleep_charging), false);
        mWiFiOverrideCharging = sharedPreferences.getBoolean(getString(R.string.key_wifi_override_charging), false);
    }

    private void setupNotificationSettings(SharedPreferences sharedPreferences) {
        mNotify = sharedPreferences.getBoolean(getString(R.string.key_notify), false);

        if (mNotify) {
            mPersistentStatus = sharedPreferences.getBoolean(getString(R.string.key_persistent_status), false);
            mNotifications = 0;

            if (sharedPreferences.getBoolean(getString(R.string.key_vibrate), false)) {
                mNotifications |= Notification.DEFAULT_VIBRATE;
            }

            if (sharedPreferences.getBoolean(getString(R.string.key_led), false)) {
                mNotifications |= Notification.DEFAULT_LIGHTS;
            }

            if (sharedPreferences.getBoolean(getString(R.string.key_ringtone), false)) {
                mNotifications |= Notification.DEFAULT_SOUND;
            }
        } else {
            mPersistentStatus = false;
            mNotifications = 0;
        }
    }

    /**
     * Google Play Location Services and Geofencing are now used, but fallback on using cell towers if needed
     */
    private boolean requirePhoneListener() {
        // TODO: this needs to be determined based on existence of
        return false;
    }

    private void startPhoneListener() {
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

                    @SuppressLint("NewApi")
                    public void onSignalStrengthsChanged(SignalStrength signalStrength) {
                        if (mPhoneType == PHONE_TYPE_CDMA) {
                            signalStrengthChanged(signalStrength.getCdmaDbm() < signalStrength.getEvdoDbm() ? signalStrength.getCdmaDbm() : signalStrength.getEvdoDbm());
                        } else {
                            signalStrengthChanged((signalStrength.getGsmSignalStrength() > 0) && (signalStrength.getGsmSignalStrength() != UNKNOWN_RSSI) ? (2 * signalStrength.getGsmSignalStrength() - 113) : signalStrength.getGsmSignalStrength());
                        }
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

    private void stopPhoneListener() {
        if (mTelephonyManager != null) {
            mTelephonyManager.listen(mPhoneListener, PhoneStateListener.LISTEN_NONE);
            mTelephonyManager = null;
            mPhoneListener = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Wapdroid.stopLogging();

        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
            mReceiver = null;
        }

        stopPhoneListener();

        if (mNotify) {
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).cancel(NOTIFY_ID);
        }

        if (ManageWakeLocks.hasLock()) {
            ManageWakeLocks.release();
        }
    }

    private int getBatteryChargedPercent(int level, int scale) {
        return Math.round(level * 100 / scale);
    }

    private boolean isCharging(int status) {
        return (status == BatteryManager.BATTERY_STATUS_CHARGING) || (status == BatteryManager.BATTERY_STATUS_FULL);
    }

    private boolean setWifiEnabled(boolean enable) {
        Wapdroid.logInfo("setWifiEnabled < " + enable);
        mWapdroidToggledWiFi = true;
        return mWifiManager.setWifiEnabled(enable);
    }

    private boolean wiFiEnabledOrEnabling() {
        Wapdroid.logInfo("wiFiEnabledOrEnabling > " + ((mWiFiState == WifiManager.WIFI_STATE_ENABLED) || (mWiFiState == WifiManager.WIFI_STATE_ENABLING)));
        return (mWiFiState == WifiManager.WIFI_STATE_ENABLED) || (mWiFiState == WifiManager.WIFI_STATE_ENABLING);
    }

    private boolean wiFiDisabledOrDisabling() {
        Wapdroid.logInfo("wiFiDisabledOrDisabling > " + ((mWiFiState == WifiManager.WIFI_STATE_DISABLED) || (mWiFiState == WifiManager.WIFI_STATE_DISABLING)));
        return (mWiFiState == WifiManager.WIFI_STATE_DISABLED) || (mWiFiState == WifiManager.WIFI_STATE_DISABLING);
    }

    // if any conditions pass, sleep policy is active:
    // ((screen off and [mob data] and [!charging])
    //  or low battery)
    // and not persistent wifi wake
    private boolean sleepPolicyActive() {
        Wapdroid.logInfo("sleepPolicyActive > " + (((mWiFiSleepScreen && ManageWakeLocks.hasLock() && (!mWiFiSleepMobNet || (mWiFiSleepMobNet && mobileNetworksAvailable())) && (!mWiFiSleepCharging || (mWiFiSleepCharging || (mBatteryLimit > 0)))) || (mLastBattPerc < mBatteryLimit)) && !persistentWiFiWake()));
        return ((mWiFiSleepScreen && ManageWakeLocks.hasLock() && (!mWiFiSleepMobNet || (mWiFiSleepMobNet && mobileNetworksAvailable())) && (!mWiFiSleepCharging || (mWiFiSleepCharging || (mBatteryLimit > 0)))) || (mLastBattPerc < mBatteryLimit)) && !persistentWiFiWake();
    }

    private boolean persistentWiFiWake() {
        Wapdroid.logInfo("persistentWiFiWake > " + (mWiFiOverrideCharging && (mBatteryLimit == 0)));
        return (mWiFiOverrideCharging && (mBatteryLimit == 0));
    }

    private boolean mobileNetworksAvailable() {
        if (mConnectivityManager == null) {
            mConnectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        }

        NetworkInfo nis[] = mConnectivityManager.getAllNetworkInfo();
        boolean networkAvailable = false;

        for (NetworkInfo ni : nis) {
            if ((ni.getType() != ConnectivityManager.TYPE_WIFI) && ((ni.getState() == NetworkInfo.State.CONNECTED) || ni.isAvailable())) {
                networkAvailable = true;
                break;
            }
        }

        Wapdroid.logInfo("mobileNetworksAvailable > " + networkAvailable);
        return networkAvailable;
    }

    private String getState(int state) {
        return state == WifiManager.WIFI_STATE_ENABLED ? "enabled" : state == WifiManager.WIFI_STATE_ENABLING ? "enabling" : state == WifiManager.WIFI_STATE_DISABLING ? "disabling" : state == WifiManager.WIFI_STATE_DISABLED ? "disabled" : "unknown";
    }

    private void wifiState(int state) {
        Wapdroid.logInfo("wifiState < " + getState(state));

        // the wifi state changed
        if (state != WifiManager.WIFI_STATE_UNKNOWN) {
            // either the user or wapdroid may start the wifi
            // regardless, set the suspenduntil and start scan
            // if the scan comes back empty, then shut wifi off
            if (state == WifiManager.WIFI_STATE_ENABLED) {
                // check if just enabled
                if (mWiFiState != WifiManager.WIFI_STATE_ENABLED) {
                    if (mScanWiFi) {
                        mScanWiFi = mWifiManager.startScan();
                    } else if (!mWapdroidToggledWiFi) {
                        mSuspendUntil = System.currentTimeMillis() + mInterval;
                    }

                    mWapdroidToggledWiFi = false;
                }
            } else if (state == WifiManager.WIFI_STATE_DISABLED) {
                mSsid = null;
                mBssid = null;

                if (mWiFiState != WifiManager.WIFI_STATE_DISABLED) {
                    mWapdroidToggledWiFi = false;
                }
            } else {
                mSsid = null;
                mBssid = null;
            }

            // notify, when onCreate (no led, ringtone, vibrate), or a change to enabled or disabled
            // don't notify when waiting for a scan, as the wifi may immediately be disabled
            if (!mScanWiFi && ((mWiFiState == WifiManager.WIFI_STATE_UNKNOWN)
                    || ((state == WifiManager.WIFI_STATE_DISABLED) && (mWiFiState != WifiManager.WIFI_STATE_DISABLED))
                    || ((state == WifiManager.WIFI_STATE_ENABLED) && (mWiFiState != WifiManager.WIFI_STATE_ENABLED)))) {
                createNotification((state == WifiManager.WIFI_STATE_ENABLED), (mWiFiState != WifiManager.WIFI_STATE_UNKNOWN));
            }

            mWiFiState = state;
            if (mWapdroidUI != null) {
                try {
                    mWapdroidUI.setWifiInfo(mWiFiState, mSsid, mBssid);
                } catch (RemoteException e) {
                }
            }
        }
    }

    private void wifiConnection() {
        mSsid = null;
        mBssid = null;
        WifiInfo wi = mWifiManager.getConnectionInfo();

        if ((wi != null) && (wi.getSupplicantState() == SupplicantState.COMPLETED) && (wi.getSSID() != null) && (wi.getBSSID() != null)) {
            mSsid = Wapdroid.stripQuotes(wi.getSSID());
            mBssid = wi.getBSSID();
        }

        Wapdroid.logInfo("wifiConnection > " + mSsid + "," + mBssid);
    }

    private void updateUI() {
        try {
            mWapdroidUI.setWifiInfo(mWiFiState, mSsid, mBssid);
            mWapdroidUI.setBattery(mLastBattPerc);
        } catch (RemoteException e) {
        }
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
                    mCid = UNKNOWN_CID;
                    mLac = UNKNOWN_CID;
                }
            }
        }

        // allow unknown mRssi, since signalStrengthChanged isn't reliable enough by itself
        signalStrengthChanged(UNKNOWN_RSSI);
    }

    private void signalStrengthChanged(int rssi) {
        Wapdroid.logInfo("signalStrengthChanged < " + rssi);
        // cancel any pending alarms
        mAlarmManager.cancel(PendingIntent.getBroadcast(this, 0, Wapdroid.getPackageIntent(this, BootReceiver.class).setAction(WAKE_SERVICE), 0));
        // signalStrengthChanged releases any wakelocks IF mCid != UNKNOWN_CID && enableWif != mLastScanEnableWifi
        // rssi may be unknown
        mRssi = rssi;

        // initialize enableWifi as mLastScanEnableWifi, so that wakelock is released by default
        // allow unknown mRssi, since signalStrengthChanged isn't reliable enough by itself
        if (mManageWifi && (mCid != UNKNOWN_CID)) {
            if (mSsid != null) {
                // upgrading, BSSID may not be set yet
                long network = fetchNetwork(mSsid, mBssid);
                createPair(mCid, mLac, network, mRssi);

                if ((mTelephonyManager.getNeighboringCellInfo() != null) && !mTelephonyManager.getNeighboringCellInfo().isEmpty()) {
                    for (NeighboringCellInfo nci : mTelephonyManager.getNeighboringCellInfo()) {
                        if (nci.getCid() > 0) {
                            createPair(nci.getCid(), nciGetLac(nci), network, (nci.getRssi() != UNKNOWN_RSSI) && (mPhoneType == TelephonyManager.PHONE_TYPE_GSM) ? 2 * nci.getRssi() - 113 : nci.getRssi());
                        }
                    }
                }
            } else {
                mCellTowersInRange = cellInRange(mCid, mLac, mRssi);

                if (mCellTowersInRange) {
                    // check neighbors if it appears that we're in range, for both enabling and disabling
                    if ((mTelephonyManager.getNeighboringCellInfo() != null) && !mTelephonyManager.getNeighboringCellInfo().isEmpty()) {
                        for (NeighboringCellInfo nci : mTelephonyManager.getNeighboringCellInfo()) {
                            // break on out of range result
                            if (nci.getCid() > 0) {
                                mCellTowersInRange = cellInRange(nci.getCid(), nciGetLac(nci), (nci.getRssi() != UNKNOWN_RSSI) && (mPhoneType == TelephonyManager.PHONE_TYPE_GSM) ? 2 * nci.getRssi() - 113 : nci.getRssi());
                            }

                            if (!mCellTowersInRange) {
                                break;
                            }
                        }
                    }
                }

                Wapdroid.logInfo("mCellTowersInRange = " + mCellTowersInRange);
                // check that the state isn't already changing
                if ((mCellTowersInRange ^ wiFiEnabledOrEnabling()) || (!mCellTowersInRange ^ wiFiDisabledOrDisabling())) {
                    // check that the sleep or persistent policies aren't violated
                    if ((mCellTowersInRange && !sleepPolicyActive()) || (!mCellTowersInRange && !persistentWiFiWake())) {
                        // check that the service isn't suspended
                        Wapdroid.logInfo("not suspended ? " + (mSuspendUntil < System.currentTimeMillis()));
                        if (mSuspendUntil < System.currentTimeMillis()) {
                            // check that this isn't the verification scan
                            Wapdroid.logInfo("scanning ? " + mScanWiFi);
                            Wapdroid.logInfo("wifi enabled ? " + (mWiFiState == WifiManager.WIFI_STATE_ENABLED));

                            if (!(mScanWiFi && (mWiFiState == WifiManager.WIFI_STATE_ENABLED))) {
                                // always scan before disabling and after enabling
                                if (mCellTowersInRange) {
                                    // after WiFi is enabled, trigger a scan of the networks
                                    mScanWiFi = true;
                                    setWifiEnabled(mCellTowersInRange);
                                } else {
                                    // should turn wifi off, but run a scan first
                                    if (!(mScanWiFi = mWifiManager.startScan())) {
                                        setWifiEnabled(mCellTowersInRange);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (!mScanWiFi) {
                sleep();
            }
        }
    }

    private void sleep() {
        if (ManageWakeLocks.hasLock()) {
            // cancel any pending alarm
            mAlarmManager.cancel(PendingIntent.getBroadcast(this, 0, Wapdroid.getPackageIntent(this, BootReceiver.class).setAction(WAKE_SERVICE), 0));

            // if hasLock, then screen is off, set alarm
            if (mInterval > 0) {
                mAlarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + mInterval, PendingIntent.getBroadcast(this, 0, Wapdroid.getPackageIntent(this, BootReceiver.class).setAction(WAKE_SERVICE), 0));
            }

            // if sleeping, re-initialize phone info
            mCid = UNKNOWN_CID;
            mLac = UNKNOWN_CID;
            mRssi = UNKNOWN_RSSI;
            ManageWakeLocks.release();
        }
    }

    private void createNotification(boolean enabled, boolean update) {
        Wapdroid.logInfo("createNotification < " + enabled + ", " + update);
        // service runs for ui, so if not managing, don't notify
        if (mManageWifi && mNotify) {
            Wapdroid.logInfo("notify!");
            Notification notification = new Notification((enabled ? R.drawable.statuson : R.drawable.scanning), getString(R.string.label_WIFI) + " " + getString(enabled ? R.string.label_enabled : R.string.label_disabled), System.currentTimeMillis());
            notification.setLatestEventInfo(getBaseContext(), getString(R.string.label_WIFI) + " " + getString(enabled ? R.string.label_enabled : R.string.label_disabled), getString(R.string.app_name), PendingIntent.getActivity(this, 0, Wapdroid.getPackageIntent(this, MainActivity.class), 0));

            if (mPersistentStatus) {
                notification.flags |= Notification.FLAG_NO_CLEAR;
            }

            if (update) {
                notification.defaults |= mNotifications;
            }

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
                    if (sharedPreferences.getBoolean(getString(R.string.key_led), false))
                        mNotifications |= Notification.DEFAULT_LIGHTS;
                    if (sharedPreferences.getBoolean(getString(R.string.key_ringtone), false))
                        mNotifications |= Notification.DEFAULT_SOUND;
                    if (sharedPreferences.getBoolean(getString(R.string.key_vibrate), false))
                        mNotifications |= Notification.DEFAULT_VIBRATE;
                }
            }
        } else if (key.equals(getString(R.string.key_interval))) {
            mInterval = Integer.parseInt((String) sharedPreferences.getString(key, "30000"));
        } else if (key.equals(getString(R.string.key_battery_override))) {
            mBatteryLimit = (sharedPreferences.getBoolean(key, false)) ? Integer.parseInt((String) sharedPreferences.getString(getString(R.string.key_battery_percentage), "30")) : 0;
        } else if (key.equals(getString(R.string.key_battery_percentage))) {
            mBatteryLimit = Integer.parseInt((String) sharedPreferences.getString(key, "30"));
        } else if (key.equals(getString(R.string.key_led)) || key.equals(getString(R.string.key_ringtone)) || key.equals(getString(R.string.key_vibrate))) {
            mNotifications = 0;

            if (sharedPreferences.getBoolean(getString(R.string.key_led), false)) {
                mNotifications |= Notification.DEFAULT_LIGHTS;
            }

            if (sharedPreferences.getBoolean(getString(R.string.key_ringtone), false)) {
                mNotifications |= Notification.DEFAULT_SOUND;
            }

            if (sharedPreferences.getBoolean(getString(R.string.key_vibrate), false)) {
                mNotifications |= Notification.DEFAULT_VIBRATE;
            }
        } else if (key.equals(getString(R.string.key_notify))) {
            mNotify = sharedPreferences.getBoolean(key, false);

            if (mNotify) {
                mPersistentStatus = sharedPreferences.getBoolean(getString(R.string.key_persistent_status), false);
                mNotifications = 0;

                if (sharedPreferences.getBoolean(getString(R.string.key_led), false)) {
                    mNotifications |= Notification.DEFAULT_LIGHTS;
                }

                if (sharedPreferences.getBoolean(getString(R.string.key_ringtone), false)) {
                    mNotifications |= Notification.DEFAULT_SOUND;
                }

                if (sharedPreferences.getBoolean(getString(R.string.key_vibrate), false)) {
                    mNotifications |= Notification.DEFAULT_VIBRATE;
                }
            } else {
                mPersistentStatus = false;
                mNotifications = 0;
                //cancel the notification
                ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).cancel(NOTIFY_ID);
            }
        } else if (key.equals(getString(R.string.key_persistent_status))) {
            // to change this, manage & notify must me enabled
            mPersistentStatus = sharedPreferences.getBoolean(key, false);

            if (mPersistentStatus) {
                createNotification((mWiFiState == WifiManager.WIFI_STATE_ENABLED), false);
            } else {
                ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).cancel(NOTIFY_ID);
            }
        } else if (key.equals(getString(R.string.key_wifi_sleep_screen))) {
            mWiFiSleepScreen = sharedPreferences.getBoolean(key, false);
        } else if (key.equals(getString(R.string.key_wifi_sleep_mob_net))) {
            mWiFiSleepMobNet = sharedPreferences.getBoolean(key, false);
        } else if (key.equals(getString(R.string.key_wifi_sleep_charging))) {
            mWiFiSleepCharging = sharedPreferences.getBoolean(key, false);
        } else if (key.equals(getString(R.string.key_wifi_override_charging))) {
            mWiFiOverrideCharging = sharedPreferences.getBoolean(key, false);
            if (persistentWiFiWake() && !wiFiEnabledOrEnabling()) {
                mScanWiFi = false;
                setWifiEnabled(true);
            }
        } else if (key.equals(getString(R.string.key_logging))) {
            if (sharedPreferences.getBoolean(key, false)) {
                Wapdroid.startLogging();
            } else {
                Wapdroid.stopLogging();
            }
        }

        try {
            BackupManager.dataChanged(this);
        } catch (Throwable t) {
            Log.d(TAG, "backupagent not supported");
        }
    }

    private long fetchNetwork(String ssid, String bssid) {
        int network;

        // handle nulls
        if (ssid == null) {
            ssid = "";
        }

        if (bssid == null) {
            bssid = "";
        }

        Cursor c = this.getContentResolver().query(Networks.CONTENT_URI, new String[]{Networks._ID, Networks.SSID, Networks.BSSID}, Networks.SSID + "=? and (" + Networks.BSSID + "=? or " + Networks.BSSID + "='')", new String[]{ssid, bssid}, null);

        if (c.moveToFirst()) {
            // ssid matches, only concerned if bssid is empty
            network = c.getInt(c.getColumnIndex(Networks._ID));

            if (c.getString(c.getColumnIndex(Networks.BSSID)).equals("")) {
                ContentValues values = new ContentValues();
                values.put(Networks.BSSID, bssid);
                this.getContentResolver().update(Networks.CONTENT_URI, values, Networks._ID + "=" + network, null);
                BackupManager.dataChanged(this);
            }
        } else {
            ContentValues values = new ContentValues();
            values.put(Networks.SSID, ssid);
            values.put(Networks.BSSID, bssid);
            // default to managing the network
            values.put(Networks.MANAGE, 1);
            network = Integer.parseInt(this.getContentResolver().insert(Networks.CONTENT_URI, values).getLastPathSegment());
            BackupManager.dataChanged(this);
        }

        c.close();
        return network;
    }

    private int fetchLocation(int lac) {
        // select or insert location
        if (lac > 0) {
            int location;
            Cursor c = this.getContentResolver().query(Locations.CONTENT_URI, new String[]{Locations._ID, Locations.LAC}, Locations.LAC + "=?", new String[]{Integer.toString(lac)}, null);

            if (c.moveToFirst()) {
                location = c.getInt(c.getColumnIndex(Locations._ID));
            } else {
                ContentValues values = new ContentValues();
                values.put(Locations.LAC, lac);
                location = Integer.parseInt(getContentResolver().insert(Locations.CONTENT_URI, values).getLastPathSegment());
                BackupManager.dataChanged(this);
            }

            c.close();
            return location;
        } else {
            return UNKNOWN_CID;
        }
    }

    private void createPair(int cid, int lac, long network, int rssi) {
        int cell, pair, location = fetchLocation(lac);
        // if location==-1, then match only on cid, otherwise match on location or -1
        // select or insert cell
        Cursor c = getContentResolver().query(Cells.CONTENT_URI, new String[]{Cells._ID, Cells.LOCATION},
                Cells.CID + "=?" + (location == UNKNOWN_CID ?
                        ""
                        : " and (" + Cells.LOCATION + "=" + UNKNOWN_CID + " or " + Cells.LOCATION + "=?)"), (location == UNKNOWN_CID ? new String[]{Integer.toString(cid)} : new String[]{Integer.toString(cid), Integer.toString(location)}), null
        );

        if (c.moveToFirst()) {
            cell = c.getInt(c.getColumnIndex(Cells._ID));

            if ((location != UNKNOWN_CID) && (c.getInt(c.getColumnIndex(Cells.LOCATION)) == UNKNOWN_CID)) {
                ContentValues values = new ContentValues();
                values.put(Cells.LOCATION, location);
                getContentResolver().update(Cells.CONTENT_URI, values, Cells._ID + "=?", new String[]{Integer.toString(cell)});
                BackupManager.dataChanged(this);
            }
        } else {
            ContentValues values = new ContentValues();
            values.put(Cells.CID, cid);
            values.put(Cells.LOCATION, location);
            cell = Integer.parseInt(getContentResolver().insert(Cells.CONTENT_URI, values).getLastPathSegment());
            BackupManager.dataChanged(this);
        }

        c.close();
        // select and update or insert pair
        c = getContentResolver().query(Pairs.CONTENT_URI, new String[]{Pairs._ID, Pairs.RSSI_MIN, Pairs.RSSI_MAX}, Pairs.CELL + "=? and " + Pairs.NETWORK + "=?", new String[]{Integer.toString(cell), Long.toString(network)}, null);

        if (c.moveToFirst()) {
            if (rssi != UNKNOWN_RSSI) {
                pair = c.getInt(c.getColumnIndex(Pairs._ID));
                int rssi_min = c.getInt(c.getColumnIndex(Pairs.RSSI_MIN));
                int rssi_max = c.getInt(c.getColumnIndex(Pairs.RSSI_MAX));

                if (rssi_min > rssi) {
                    ContentValues values = new ContentValues();
                    values.put(Pairs.RSSI_MIN, rssi);
                    getContentResolver().update(Pairs.CONTENT_URI, values, Pairs._ID + "=?", new String[]{Integer.toString(pair)});
                    BackupManager.dataChanged(this);
                } else if ((rssi_max == UNKNOWN_RSSI) || (rssi_max < rssi)) {
                    ContentValues values = new ContentValues();
                    values.put(Pairs.RSSI_MAX, rssi);
                    getContentResolver().update(Pairs.CONTENT_URI, values, Pairs._ID + "=?", new String[]{Integer.toString(pair)});
                    BackupManager.dataChanged(this);
                }
            }
        } else {
            ContentValues values = new ContentValues();
            values.put(Pairs.CELL, cell);
            values.put(Pairs.NETWORK, network);
            values.put(Pairs.RSSI_MIN, rssi);
            values.put(Pairs.RSSI_MAX, rssi);
            values.put(Pairs.MANAGE_CELL, 1);
            getContentResolver().insert(Pairs.CONTENT_URI, values);
            BackupManager.dataChanged(this);
        }

        c.close();
    }

    private boolean cellInRange(int cid, int lac, int rssi) {
        Cursor c = getContentResolver().query(Ranges.CONTENT_URI, new String[]{Ranges._ID, Ranges.LOCATION}, Ranges.CID + "=? and (" + Ranges.LAC + "=? or " + Ranges.LOCATION + "=" + UNKNOWN_CID + ") and "
                + Ranges.MANAGE + "=1 and " + Ranges.MANAGE_CELL + "=1"
                + (rssi == UNKNOWN_RSSI
                ? ""
                : " and (((" + Ranges.RSSI_MIN + "=" + UNKNOWN_RSSI + ") or (" + Ranges.RSSI_MIN + "<=?)) and (" + Ranges.RSSI_MAX + ">=?))"), (rssi == UNKNOWN_RSSI ? new String[]{Integer.toString(cid), Integer.toString(lac)} : new String[]{Integer.toString(cid), Integer.toString(lac), Integer.toString(rssi), Integer.toString(rssi)}), null);
        boolean inRange = c.moveToFirst();

        if (inRange && (lac > 0)) {
            // check LAC, as this is a new column
            if (c.isNull(c.getColumnIndex(Ranges.LOCATION))) {
                ContentValues values = new ContentValues();
                values.put(Ranges.LOCATION, fetchLocation(lac));
                getContentResolver().update(Cells.CONTENT_URI, values, Cells._ID + "=?", new String[]{Integer.toString(c.getInt(c.getColumnIndex(Ranges._ID)))});
                BackupManager.dataChanged(this);
            }
        }

        c.close();
        return inRange;
    }
}
