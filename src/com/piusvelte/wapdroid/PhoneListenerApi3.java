package com.piusvelte.wapdroid;

import static com.piusvelte.wapdroid.WapdroidService.PHONE_TYPE_CDMA;

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
		if ((mService.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM) || (mService.getPhoneType() == PHONE_TYPE_CDMA)) mService.signalStrengthChanged(asu > 0 ? (2 * asu - 113) : asu);
		else mService.release();
	}
}
