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
package com.piusvelte.wapdroid.core;

import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;

public class StatusFragment extends Fragment {

    private static final String CID = "cid";
    private static final String LAC = "lac";
    private static final String WIFI = "wifi";
    private static final String RSSI = "rssi";
    private static final String BATT = "batt";

    private TextView mCID;
    private TextView mWifiState;
    private TextView mWifiBSSID;
    private TextView mSignal;
    private TextView mBattery;
    private TextView mLAC;

    public static StatusFragment newInstance(int cid, int lac, int wifi, String ssid, String bssid, int rssi, int battery) {
        StatusFragment statusFragment = new StatusFragment();
        Bundle arguments = new Bundle();
        arguments.putInt(CID, cid);
        arguments.putInt(LAC, lac);
        arguments.putInt(WIFI, wifi);
        arguments.putString(Wapdroid.Networks.SSID, ssid);
        arguments.putString(Wapdroid.Networks.BSSID, bssid);
        arguments.putInt(RSSI, rssi);
        arguments.putInt(BATT, battery);
        statusFragment.setArguments(arguments);
        return statusFragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View rootView = inflater.inflate(R.layout.fragment_status, container, false);
        Wapdroid.setupBannerAd(rootView);
        mCID = (TextView) rootView.findViewById(R.id.field_CID);
        mWifiState = (TextView) rootView.findViewById(R.id.field_wifiState);
        mWifiBSSID = (TextView) rootView.findViewById(R.id.field_wifiBSSID);
        mSignal = (TextView) rootView.findViewById(R.id.field_signal);
        mBattery = (TextView) rootView.findViewById(R.id.field_battery);
        mLAC = (TextView) rootView.findViewById(R.id.field_LAC);

        Bundle arguments = getArguments();
        setCellInfo(arguments.getInt(CID), arguments.getInt(LAC));
        setWifiState(arguments.getInt(WIFI), arguments.getString(Wapdroid.Networks.SSID), arguments.getString(Wapdroid.Networks.BSSID));
        setSignalStatus(arguments.getInt(RSSI));
        setBatteryStatus(arguments.getInt(BATT));

        return rootView;
    }

    public void setCellInfo(int cid, int lac) {
        mCID.setText(Integer.toString(cid));
        mLAC.setText(Integer.toString(lac));
    }

    public void setWifiState(int state, String ssid, String bssid) {
        if (state == WifiManager.WIFI_STATE_ENABLED) {
            if (ssid != null) {
                mWifiState.setText(ssid);
                mWifiBSSID.setText(bssid);
            } else {
                mWifiState.setText(getString(R.string.label_enabled));
                mWifiBSSID.setText("");
            }
        } else if (state != WifiManager.WIFI_STATE_UNKNOWN) {
            mWifiState.setText((state == WifiManager.WIFI_STATE_ENABLING ?
                    getString(R.string.label_enabling)
                    : (state == WifiManager.WIFI_STATE_DISABLING ?
                    getString(R.string.label_disabling)
                    : getString(R.string.label_disabled))));
            mWifiBSSID.setText("");
        }
    }

    public void setSignalStatus(int status) {
        mSignal.setText((status != Wapdroid.UNKNOWN_RSSI ? (Integer.toString(status) + getString(R.string.dbm)) : getString(R.string.scanning)));
    }

    public void setBatteryStatus(int status) {
        mBattery.setText(Integer.toString(status) + "%");
    }
}
