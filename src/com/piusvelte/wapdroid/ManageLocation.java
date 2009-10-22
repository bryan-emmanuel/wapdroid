package com.piusvelte.wapdroid;

import java.util.List;

import android.content.Context;
import android.telephony.CellLocation;
import android.telephony.NeighboringCellInfo;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;

public class ManageLocation extends PhoneStateListener {
	private Wapdroid mWapdroid;
	public TelephonyManager mTeleManager;
	public GsmCellLocation mGsmCellLocation;
	private int mCID = -1;
	private int mLAC = -1;
	private String mMNC = null;
	private String mMCC = null;
	private int mRSSI = -1;
	
	public ManageLocation(Wapdroid wapdroid) {
		mWapdroid = wapdroid;
		mTeleManager = (TelephonyManager) mWapdroid.getSystemService(Context.TELEPHONY_SERVICE);
    	mTeleManager.listen(this, PhoneStateListener.LISTEN_CELL_LOCATION ^ PhoneStateListener.LISTEN_SIGNAL_STRENGTH);}
	
	@Override
	public void onSignalStrengthChanged(int asu) {
		super.onSignalStrengthChanged(asu);
		mRSSI = asu;
		mWapdroid.updateRSSI(mRSSI);
		mWapdroid.manageWifi(mCID, mLAC, mMNC, mMCC, mRSSI, mTeleManager.getNeighboringCellInfo());}
	
	@Override
	public void onCellLocationChanged(CellLocation location) {
		super.onCellLocationChanged(location);
		mGsmCellLocation = (GsmCellLocation) mTeleManager.getCellLocation();
		int mNewCID = mGsmCellLocation.getCid();
		int mNewLAC = mGsmCellLocation.getLac();
		if (mNewCID > 0) {
			mCID = mNewCID;}
		if (mNewLAC > 0) {
			mLAC = mNewLAC;}
		mMNC = mTeleManager.getNetworkOperatorName();
		mMCC = mTeleManager.getNetworkCountryIso();
		mWapdroid.updateLocation(mCID, mLAC, mMNC, mMCC);
		mWapdroid.manageWifi(mCID, mLAC, mMNC, mMCC, mRSSI, mTeleManager.getNeighboringCellInfo());}
    
	public int getCID() {
		return mCID;}
	
	public int getLAC() {
		return mLAC;}
	
	public String getMNC() {
		return mMNC;}
	
	public String getMCC() {
		return mMCC;}
	
	public int getRSSI() {
		return mRSSI;}
	
	public List<NeighboringCellInfo> getNeighboringCells() {
		return mTeleManager.getNeighboringCellInfo();}}