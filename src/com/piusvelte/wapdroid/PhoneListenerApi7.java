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
import android.telephony.CellLocation;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;

// PhoneStateListener for api >= 7
public class PhoneListenerApi7 extends PhoneStateListener {
	private WapdroidService mService;

	public PhoneListenerApi7(WapdroidService service) {
		mService = service;
	}

	public void onCellLocationChanged(CellLocation location) {
		// this also calls signalStrengthChanged, since signalStrengthChanged isn't reliable enough by itself
		mService.getCellInfo(location);
	}

	public void onSignalStrengthChanged(int asu) {
		// add cdma support, convert signal from gsm
		if ((mService.mTeleManager.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM) || (mService.mTeleManager.getPhoneType() == PHONE_TYPE_CDMA)) mService.signalStrengthChanged(asu > 0 ? (2 * asu - 113) : asu);
		else mService.release();
	}

	public void onSignalStrengthsChanged(SignalStrength signalStrength) {
		if (mService.mTeleManager.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM) mService.signalStrengthChanged(2 * signalStrength.getGsmSignalStrength() - 113);
		else if (mService.mTeleManager.getPhoneType() == PHONE_TYPE_CDMA) mService.signalStrengthChanged(signalStrength.getCdmaDbm() < signalStrength.getEvdoDbm() ?
				signalStrength.getCdmaDbm()
				: signalStrength.getEvdoDbm());
		else mService.release();
	}
}
