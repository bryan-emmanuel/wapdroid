package com.piusvelte.wapdroid;

import android.telephony.CellLocation;
import android.telephony.PhoneStateListener;
import android.telephony.gsm.GsmCellLocation;

public class ManageLocation extends PhoneStateListener {
	private Wapdroid mWapdroid;
	
	public ManageLocation(Wapdroid wapdroid) {
		mWapdroid = wapdroid;}
	
	@Override
	public void onSignalStrengthChanged(int asu) {
		super.onSignalStrengthChanged(asu);
		mWapdroid.signalChanged(asu);}
	
	@Override
	public void onCellLocationChanged(CellLocation location) {
		super.onCellLocationChanged(location);
		GsmCellLocation mGsmCellLocation = (GsmCellLocation) location;
		int mCID = mGsmCellLocation.getCid();
		int mLAC = mGsmCellLocation.getLac();
		mWapdroid.locationChanged(mCID, mLAC);}}