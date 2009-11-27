package com.piusvelte.wapdroid;

import android.os.Bundle;
import android.telephony.CellLocation;
import android.telephony.cdma.CdmaCellLocation;

public class CdmaCellLocationWrapper extends CellLocation {
	private CdmaCellLocation mCdmaCellLocation;
	static {
		try {
			Class.forName("CdmaCellLocation");}
		catch (Exception ex) {
			throw new RuntimeException(ex);}}
	// initialize
	public static void checkAvailable() {}
	public CdmaCellLocationWrapper(Bundle bundleWithValues) {
		mCdmaCellLocation = new CdmaCellLocation(bundleWithValues);}
	public int getBaseStationId() {
		return mCdmaCellLocation.getBaseStationId();}}
