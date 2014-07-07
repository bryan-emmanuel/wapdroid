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

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;

import java.util.Locale;

public class MainActivity extends ActionBarActivity implements ServiceConnection, DialogInterface.OnClickListener, ActionBar.TabListener {

    private String mBssid = "";
    private String mSsid = "";
    private int mBattery;
    private int mWifiState = WifiManager.WIFI_STATE_UNKNOWN;
    private IWapdroidUI.Stub mWapdroidUI = new IWapdroidUI.Stub() {
        public void setWifiInfo(int state, String ssid, String bssid)
                throws RemoteException {
            mWifiState = state;
            mSsid = ssid;
            mBssid = bssid;

            int fragmentPosition = mViewPager.getCurrentItem();
            Fragment fragment = mPagerAdapter.getFragment(fragmentPosition);

            switch (fragmentPosition) {
                case WapdroidPagerAdapter.FRAGMENT_STATUS:
                    if (fragment instanceof StatusFragment) {
                        ((StatusFragment) fragment).setWifiState(mWifiState, mSsid, mBssid);
                    }
                    break;
                case WapdroidPagerAdapter.FRAGMENT_NETWORKS:
                    if (fragment instanceof ManageData) {
                        ((ManageData) fragment).setWifi(mSsid, mBssid);
                    }
                    break;
            }
        }

        public void setBattery(int batteryPercentage) throws RemoteException {
            mBattery = batteryPercentage;

            if (mViewPager.getCurrentItem() == WapdroidPagerAdapter.FRAGMENT_STATUS) {
                Fragment fragment = mPagerAdapter.getFragment(mViewPager.getCurrentItem());
                if (fragment instanceof StatusFragment) {
                    ((StatusFragment) fragment).setBatteryStatus(mBattery);
                }
            }
        }
    };
    private IWapdroidService mIService;
    private ViewPager mViewPager;
    private WapdroidPagerAdapter mPagerAdapter;
    private Dialog mDialog;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        final ActionBar actionBar = getSupportActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        mPagerAdapter = new WapdroidPagerAdapter(getSupportFragmentManager());
        mViewPager = (ViewPager) findViewById(R.id.pager);
        mViewPager.setAdapter(mPagerAdapter);
        mViewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                actionBar.setSelectedNavigationItem(position);
            }
        });

        for (int i = 0; i < mPagerAdapter.getCount(); i++) {
            actionBar.addTab(actionBar.newTab()
                    .setText(mPagerAdapter.getPageTitle(i))
                    .setTabListener(this));
        }

        mViewPager.setCurrentItem(WapdroidPagerAdapter.FRAGMENT_STATUS);
    }

    /*
     * Handle results returned to the FragmentActivity
     * by Google Play services
     */
//    @Override
//    protected void onActivityResult(
//            int requestCode, int resultCode, Intent data) {
//        // Decide what to do based on the original request code
//        switch (requestCode) {
//            case CONNECTION_FAILURE_RESOLUTION_REQUEST :
//            /*
//             * If the result code is Activity.RESULT_OK, try
//             * to connect again
//             */
//                switch (resultCode) {
//                    case Activity.RESULT_OK :
//                    /*
//                     * Try the request again
//                     */
//                        ...
//                        break;
//                }
//                ...
//        }
//        ...
//    }

    private void checkLocationServices() {
        int connectionResult = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);

        if (connectionResult != ConnectionResult.SUCCESS) {
            // TODO no play services
            // Get the error code
//            int errorCode = connectionResult.getErrorCode();
//            // Get the error dialog from Google Play services
//            Dialog errorDialog = GooglePlayServicesUtil.getErrorDialog(
//                    errorCode,
//                    this,
//                    CONNECTION_FAILURE_RESOLUTION_REQUEST);
//
//            // If Google Play services can provide an error dialog
//            if (errorDialog != null) {
//                // Create a new DialogFragment for the error dialog
//                ErrorDialogFragment errorFragment = new ErrorDialogFragment();
//                // Set the dialog in the DialogFragment
//                errorFragment.setDialog(errorDialog);
//                // Show the error dialog in the DialogFragment
//                errorFragment.show(
//                        getSupportFragmentManager(),
//                        "Geofence Detection");
//            }
        }
    }

    public static class ErrorDialogFragment extends DialogFragment {
        // Global field to contain the error dialog
        private Dialog mDialog = null;

        // Set the dialog to display
        public void setDialog(Dialog dialog) {
            mDialog = dialog;
        }

        // Return a Dialog to the DialogFragment.
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return mDialog;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (super.onCreateOptionsMenu(menu)) {
            getMenuInflater().inflate(R.menu.status, menu);
            return true;
        }

        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.menu_settings) {
            startActivity(Wapdroid.getPackageIntent(this, Settings.class));
            return true;
        } else if (id == R.id.menu_wifi) {
            startActivity(new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS));
            return true;
        } else if (id == R.id.menu_about) {
            if (mDialog != null) mDialog.dismiss();

            mDialog = new Dialog(this);
            mDialog.setContentView(R.layout.about);
            mDialog.setTitle(R.string.label_about);
            mDialog.show();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onPause() {
        if (mDialog != null) mDialog.dismiss();

        if (mIService != null) {
            try {
                mIService.setCallback(null);
            } catch (RemoteException e) {
            }
        }

        unbindService(this);
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences sp = getSharedPreferences(getString(R.string.key_preferences), MODE_PRIVATE);

        if (sp.getBoolean(getString(R.string.key_manageWifi), false)) {
            startService(Wapdroid.getPackageIntent(this, WapdroidService.class));
        } else {
            if (mDialog != null) mDialog.dismiss();

            mDialog = (new AlertDialog.Builder(this))
                    .setMessage(R.string.service_info)
                    .setNegativeButton(android.R.string.ok, this)
                    .create();
            mDialog.show();
        }

        bindService(Wapdroid.getPackageIntent(this, WapdroidService.class), this, BIND_AUTO_CREATE);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        mIService = IWapdroidService.Stub.asInterface((IBinder) service);

        if (mWapdroidUI != null) {
            try {
                mIService.setCallback(mWapdroidUI.asBinder());
            } catch (RemoteException e) {
            }
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        mIService = null;
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        dialog.cancel();
    }

    @Override
    public void onTabSelected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
        final int position = tab.getPosition();
        mViewPager.setCurrentItem(position);
        Fragment fragment = mPagerAdapter.getFragment(position);

        switch (position) {
            case WapdroidPagerAdapter.FRAGMENT_STATUS:
                if (fragment instanceof StatusFragment) {
                    StatusFragment statusFragment = (StatusFragment) fragment;
                    statusFragment.setBatteryStatus(mBattery);
                    statusFragment.setWifiState(mWifiState, mSsid, mBssid);
                }
                break;
            case WapdroidPagerAdapter.FRAGMENT_NETWORKS:
                if (fragment instanceof ManageData) {
                    ManageData manageData = (ManageData) fragment;
                    manageData.setWifi(mSsid, mBssid);
                }
                break;
        }
    }

    @Override
    public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
        // NO-OP
    }

    @Override
    public void onTabReselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
        // NO-OP
    }

    public class WapdroidPagerAdapter extends FragmentPagerAdapter {

        public static final int FRAGMENT_STATUS = 0;
        public static final int FRAGMENT_NETWORKS = 1;
        public static final int FRAGMENT_COUNT = 2;

        public WapdroidPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        public Fragment getFragment(int position) {
            if (position < FRAGMENT_COUNT) {
                return getSupportFragmentManager().findFragmentByTag("android:switcher:" + R.id.pager + ":" + position);
            }

            return null;
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case FRAGMENT_STATUS:
                    return StatusFragment.newInstance(mWifiState, mSsid, mBssid, mBattery);
                case FRAGMENT_NETWORKS:
                    return ManageData.newInstance(mSsid, mBssid);
                default:
                    return null;
            }
        }

        @Override
        public int getCount() {
            return FRAGMENT_COUNT;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            Locale l = Locale.getDefault();

            switch (position) {
                case FRAGMENT_STATUS:
                    return getString(R.string.tab_status).toUpperCase(l);
                case FRAGMENT_NETWORKS:
                    return getString(R.string.tab_networks).toUpperCase(l);
                default:
                    return null;
            }
        }
    }
}
