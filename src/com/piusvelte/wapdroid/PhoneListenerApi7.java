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
		if ((mService.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM) || (mService.getPhoneType() == PHONE_TYPE_CDMA)) mService.signalStrengthChanged(asu > 0 ? (2 * asu - 113) : asu);
		else mService.release();
	}

	public void onSignalStrengthsChanged(SignalStrength signalStrength) {
		if (mService.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM) mService.signalStrengthChanged(2 * signalStrength.getGsmSignalStrength() - 113);
		else if (mService.getPhoneType() == PHONE_TYPE_CDMA) mService.signalStrengthChanged(signalStrength.getCdmaDbm() < signalStrength.getEvdoDbm() ?
				signalStrength.getCdmaDbm()
				: signalStrength.getEvdoDbm());
		else mService.release();
	}
}
