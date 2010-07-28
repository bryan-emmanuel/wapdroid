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
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *  
 *  Bryan Emmanuel piusvelte@gmail.com
 */

package com.piusvelte.wapdroid;

import static com.piusvelte.wapdroid.WapdroidService.PHONE_TYPE_CDMA;
import static android.telephony.NeighboringCellInfo.UNKNOWN_RSSI;

import android.telephony.CellLocation;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

// PhoneStateListener for 3 <= api < 7
public class PhoneListenerApi3 extends PhoneStateListener {
	private WapdroidService mService;

	public PhoneListenerApi3(WapdroidService service) {
		mService = service;
	}	

	public void onCellLocationChanged(CellLocation location) {
		// this also calls signalStrengthChanged, since onSignalStrengthChanged isn't reliable enough by itself
		mService.getCellInfo(location);
	}

	public void onSignalStrengthChanged(int asu) {
		// add cdma support, convert signal from gsm
		if ((mService.mTeleManager.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM) || (mService.mTeleManager.getPhoneType() == PHONE_TYPE_CDMA)) mService.signalStrengthChanged((asu > 0) && (asu != UNKNOWN_RSSI) ? (2 * asu - 113) : asu);
		else mService.release();
	}
}
