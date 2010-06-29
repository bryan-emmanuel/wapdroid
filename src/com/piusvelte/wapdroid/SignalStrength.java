package com.piusvelte.wapdroid;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

public class SignalStrength implements Parcelable {
	private static final String TAG = "Wapdroid";
	private int mGsmSignalStrength,
	mCdmaDbm,
	mEvdoDbm;
	static {
		try {
			Class.forName("android.telephony.SignalStrength");
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	public static SignalStrength newFromBundle(Bundle m) {
		Log.v(TAG,"newFromBundle");
		SignalStrength ret;
		ret = new SignalStrength();
		ret.setFromNotifierBundle(m);
		return ret;
	}

	private void setFromNotifierBundle(Bundle m) {
		Log.v(TAG,"setFromNotifierBundle");
		mGsmSignalStrength = m.getInt("GsmSignalStrength");
		mCdmaDbm = m.getInt("CdmaDbm");
		mEvdoDbm = m.getInt("EvdoDbm");
	}

	public SignalStrength() {
		Log.v(TAG,"SignalStrength");
		mGsmSignalStrength = 99;
		mCdmaDbm = -1;
		mEvdoDbm = -1;
	}

	public SignalStrength(int gsmSignalStrength, int gsmBitErrorRate,
			int cdmaDbm, int cdmaEcio,
			int evdoDbm, int evdoEcio, int evdoSnr, boolean gsm) {
		Log.v(TAG,"SignalStrength(int gsmSignalStrength, ...");
		mGsmSignalStrength = gsmSignalStrength;
		mCdmaDbm = cdmaDbm;
		mEvdoDbm = evdoDbm;
	}

	public SignalStrength(SignalStrength s) {
		Log.v(TAG,"SignalStrength(SignalStrength s)");
		copyFrom(s);
	}

	protected void copyFrom(SignalStrength s) {
		Log.v(TAG,"copyFrom");
		mGsmSignalStrength = s.mGsmSignalStrength;
		mCdmaDbm = s.mCdmaDbm;
		mEvdoDbm = s.mEvdoDbm;
	}

	public SignalStrength(Parcel in) {
		Log.v(TAG,"SignalStrength(Parcel in)");
		mGsmSignalStrength = in.readInt();
		mCdmaDbm = in.readInt();
		mEvdoDbm = in.readInt();
	}

	public void writeToParcel(Parcel out, int flags) {
		Log.v(TAG,"writeToParcel");
		out.writeInt(mGsmSignalStrength);
		out.writeInt(mCdmaDbm);
		out.writeInt(mEvdoDbm);
	}

	public int describeContents() {
		return 0;
	}

	public int getGsmSignalStrength() {
		return mGsmSignalStrength;
	}

	public int getCdmaDbm() {
		return mCdmaDbm;
	}

	public int getEvdoDbm() {
		return mEvdoDbm;
	}
}
