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

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.os.RemoteException;
import android.preference.PreferenceActivity;

public class Settings extends PreferenceActivity implements OnSharedPreferenceChangeListener {
	private SharedPreferences mSharedPreferences;
	private ServiceConn mServiceConn;
	private Intent mServiceIntent;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        getPreferenceManager().setSharedPreferencesName(getString(R.string.key_preferences));
		addPreferencesFromResource(R.xml.preferences);
		mSharedPreferences = getSharedPreferences(getString(R.string.key_preferences), MODE_PRIVATE);
		mServiceIntent = new Intent(this, WapdroidService.class);}

    @Override
    public void onPause() {
    	super.onPause();
    	mSharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
   		if (mServiceConn != null) releaseService();}
    
	@Override
    public void onResume() {
    	super.onResume();
        mSharedPreferences.registerOnSharedPreferenceChangeListener(this);
        if (mSharedPreferences.getBoolean(getString(R.string.key_manageWifi), true)) {
        	startService(mServiceIntent);
        	if (mServiceConn == null) captureService();}}
	
	public void captureService() {
    	mServiceConn = new ServiceConn();
    	android.util.Log.v("Wapdroid", "bindService");
    	bindService(mServiceIntent, mServiceConn, BIND_AUTO_CREATE);}
	
	public void releaseService() {
    	android.util.Log.v("Wapdroid", "unbindService");
   		unbindService(mServiceConn);
   		mServiceConn = null;}
	
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (key.equals(getString(R.string.key_manageWifi))) {
			if (sharedPreferences.getBoolean(key, true)) {
		    	android.util.Log.v("Wapdroid", "startService");
				startService(mServiceIntent);
				captureService();}
			else {
				releaseService();
		    	android.util.Log.v("Wapdroid", "stopService");
				stopService(mServiceIntent);}}
		else if (sharedPreferences.getBoolean(getString(R.string.key_manageWifi), true)) {
			try {
				mServiceConn.mIService.updatePreferences(Integer.parseInt((String) sharedPreferences.getString(getString(R.string.key_interval), "30000")),
					sharedPreferences.getBoolean(getString(R.string.key_notify), true),
					sharedPreferences.getBoolean(getString(R.string.key_vibrate), false),
					sharedPreferences.getBoolean(getString(R.string.key_led), false),
					sharedPreferences.getBoolean(getString(R.string.key_ringtone), false),
					sharedPreferences.getBoolean(getString(R.string.key_battery_override), false),
					Integer.parseInt((String) sharedPreferences.getString(getString(R.string.key_battery_percentage), "30")));}
			catch (RemoteException e) {}}}}