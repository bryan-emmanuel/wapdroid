package com.piusvelte.wapdroid.core;

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
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;

import java.util.Locale;

import static com.piusvelte.wapdroid.core.Wapdroid.UNKNOWN_RSSI;

public class MainActivity extends ActionBarActivity implements ServiceConnection, DialogInterface.OnClickListener, ManageData.ManageDataListener, ActionBar.TabListener {

    private String mBssid = "";
    private String mCells = "";
    private String mSsid = "";
    private int mCid = 0;
    private int mLac = 0;
    private int mBattery;
    private int mWifiState = WifiManager.WIFI_STATE_UNKNOWN;
    private int mRSSI = UNKNOWN_RSSI;
	private IWapdroidService mIService;

    private ViewPager mViewPager;
    private WapdroidPagerAdapter mPagerAdapter;

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
			Dialog dialog = new Dialog(this);
			dialog.setContentView(R.layout.about);
			dialog.setTitle(R.string.label_about);
			dialog.show();
			return true;
		}

		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onPause() {
		super.onPause();

		if (mIService != null) {
			try {
				mIService.setCallback(null);
			} catch (RemoteException e) {}
		}

		unbindService(this);
	}

	@Override
	public void onResume() {
		super.onResume();
		SharedPreferences sp = getSharedPreferences(getString(R.string.key_preferences), MODE_PRIVATE);

		if (sp.getBoolean(getString(R.string.key_manageWifi), false)) {
			startService(Wapdroid.getPackageIntent(this, WapdroidService.class));
		} else {
			AlertDialog.Builder dialog = new AlertDialog.Builder(this);
			dialog.setMessage(R.string.service_info);
			dialog.setNegativeButton(android.R.string.ok, this);
			dialog.show();
		}

		bindService(Wapdroid.getPackageIntent(this, WapdroidService.class), this, BIND_AUTO_CREATE);
	}

	private IWapdroidUI.Stub mWapdroidUI = new IWapdroidUI.Stub() {
		public void setCellInfo(int cid, int lac) throws RemoteException {
			mCid = cid;
            mLac = lac;

            if (mViewPager.getCurrentItem() == WapdroidPagerAdapter.FRAGMENT_STATUS) {
                Fragment fragment = mPagerAdapter.getFragment(mViewPager.getCurrentItem());
                if (fragment instanceof StatusFragment) {
                    ((StatusFragment) fragment).setCellInfo(mCid, mLac);
                }
            }
		}

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

		public void setSignalStrength(int rssi) throws RemoteException {
            mRSSI = rssi;

            if (mViewPager.getCurrentItem() == WapdroidPagerAdapter.FRAGMENT_STATUS) {
                Fragment fragment = mPagerAdapter.getFragment(mViewPager.getCurrentItem());
                if (fragment instanceof StatusFragment) {
                    ((StatusFragment) fragment).setSignalStatus(mRSSI);
                }
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

		public void setCells(String cells) throws RemoteException {
			mCells = cells;

            if (mViewPager.getCurrentItem() == WapdroidPagerAdapter.FRAGMENT_NETWORKS) {
                Fragment fragment = mPagerAdapter.getFragment(mViewPager.getCurrentItem());
                if (fragment instanceof ManageData) {
                    ((ManageData) fragment).setCells(mCells);
                }
            }
		}
	};

	@Override
	public void onServiceConnected(ComponentName name, IBinder service) {
		mIService = IWapdroidService.Stub.asInterface((IBinder) service);

		if (mWapdroidUI != null) {
			try {
				mIService.setCallback(mWapdroidUI.asBinder());
			} catch (RemoteException e) {}
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
    public void onManageNetwork(long network) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.add(ManageData.newInstance(network, mCid, mSsid, mBssid, mCells), null);
        transaction.addToBackStack(null);
        transaction.commit();
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
                    statusFragment.setSignalStatus(mRSSI);
                    statusFragment.setWifiState(mWifiState, mSsid, mBssid);
                    statusFragment.setCellInfo(mCid, mLac);
                }
                break;
            case WapdroidPagerAdapter.FRAGMENT_NETWORKS:
                if (fragment instanceof ManageData) {
                    ManageData manageData = (ManageData) fragment;
                    manageData.setWifiAndCells(mSsid, mBssid, mCells);
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
                    return StatusFragment.newInstance(mCid, mLac, mWifiState, mSsid, mBssid, mRSSI, mBattery);
                case FRAGMENT_NETWORKS:
                    return ManageData.newInstance(ManageData.NETWORK_ALL, ManageData.CID_ALL, mSsid, mBssid, mCells);
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