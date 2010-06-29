package com.piusvelte.wapdroid;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

public class NeighboringCellInfo implements Parcelable {
	private static final String TAG = "Wapdroid";
	private static Method getLacSupport;
	private int mCid;
	private int mRssi;
	private int mLac;

	static {
		getLacSupport();
	};
	
	public NeighboringCellInfo(Parcel in) {
		android.telephony.NeighboringCellInfo nci = new android.telephony.NeighboringCellInfo(in);
		this.mCid = nci.getCid();
		this.mRssi = nci.getRssi();
		if (getLacSupport != null) this.mLac = nci.getLac();
	}
	
	public int getCid() {
		return this.mCid;
	}
	
	public int getRssi() {
		return this.mRssi;
	}
	
	public int getLac() {
		return this.mLac;
	}
	
	private static void getLacSupport() {
		try {
			getLacSupport = android.telephony.NeighboringCellInfo.class.getMethod("getLac", new Class[] {} );
		} catch (NoSuchMethodException nsme) {
			Log.e(TAG,"unexpected " + nsme);
		}
	}

	private static int getLacWrapper() throws IOException {
		int lac = WapdroidDbAdapter.UNKNOWN_CID;
		try {
			getLacSupport.invoke(null);
		} catch (InvocationTargetException ite) {
			Throwable cause = ite.getCause();
			if (cause instanceof IOException) throw (IOException) cause;
			else if (cause instanceof RuntimeException) throw (RuntimeException) cause;
			else if (cause instanceof Error) throw (Error) cause;
			else throw new RuntimeException(ite);
		} catch (IllegalAccessException ie) {
			System.err.println("unexpected " + ie);
		}
		return lac;
	}

//	public void fiddle() {
//		if (getLacSupport != null) {
//			/* feature is supported */
//			try {
//				getLacWrapper();
//			} catch (IOException ie) {
//				System.err.println("dump failed!");
//			}
//		} else {
//			/* feature not supported, do something else */
//			System.out.println("dump not supported");
//		}
//	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mRssi);
        dest.writeInt(mCid);		
	}
}
