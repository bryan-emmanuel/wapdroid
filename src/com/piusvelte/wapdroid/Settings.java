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

import com.piusvelte.wapdroid.R;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceActivity;

public class Settings extends PreferenceActivity implements OnSharedPreferenceChangeListener {
	private int mInterval = 0;
	private boolean mManageWifi = false, mNotify = false, mVibrate = false, mLed = false, mRingtone = false;
	SharedPreferences mSharedPreferences;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        getPreferenceManager().setSharedPreferencesName(getString(R.string.key_preferences));
		addPreferencesFromResource(R.xml.preferences);
		mSharedPreferences = getSharedPreferences(getString(R.string.key_preferences), MODE_PRIVATE);}

    @Override
    public void onPause() {
    	super.onPause();
    	mSharedPreferences.unregisterOnSharedPreferenceChangeListener(this);}
    
	@Override
    public void onResume() {
    	super.onResume();
        mSharedPreferences.registerOnSharedPreferenceChangeListener(this);}

	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (key.equals(getString(R.string.key_manageWifi))) {
			mManageWifi = sharedPreferences.getBoolean(key, true);
			if (mManageWifi) {
				startService(new Intent(this, WapdroidService.class));}
			else {
				stopService(new Intent(this, WapdroidService.class));}}
		else if (sharedPreferences.getBoolean(getString(R.string.key_manageWifi), true)) {
			mInterval = Integer.parseInt((String) sharedPreferences.getString(getString(R.string.key_interval), "0"));
			mNotify = sharedPreferences.getBoolean(getString(R.string.key_notify), true);
			mVibrate = mNotify ? sharedPreferences.getBoolean(getString(R.string.key_vibrate), false) : false;
			mLed = mNotify ? sharedPreferences.getBoolean(getString(R.string.key_led), false) : false;
			mRingtone = mNotify ? sharedPreferences.getBoolean(getString(R.string.key_ringtone), false) : false;
			WapdroidServiceConnection mWapdroidServiceConnection = new WapdroidServiceConnection();
			bindService(new Intent(this, WapdroidService.class), mWapdroidServiceConnection, BIND_AUTO_CREATE);
			unbindService(mWapdroidServiceConnection);
			mWapdroidServiceConnection = null;}}

	public class WapdroidServiceConnection implements ServiceConnection {
		public void onServiceConnected(ComponentName className, IBinder boundService) {
			IWapdroidService service = IWapdroidService.Stub.asInterface((IBinder) boundService);
			try {
				service.updatePreferences(mInterval, mNotify, mVibrate, mLed, mRingtone);}
			catch (RemoteException e) {}}
		
		public void onServiceDisconnected(ComponentName className) {}}}