package com.piusvelte.wapdroid;

import android.os.Parcel;
import android.os.Parcelable;
import static android.telephony.TelephonyManager.NETWORK_TYPE_UNKNOWN;
import static android.telephony.TelephonyManager.NETWORK_TYPE_EDGE;
import static android.telephony.TelephonyManager.NETWORK_TYPE_GPRS;
import static android.telephony.TelephonyManager.NETWORK_TYPE_UMTS;
import static android.telephony.TelephonyManager.NETWORK_TYPE_HSDPA;
import static android.telephony.TelephonyManager.NETWORK_TYPE_HSUPA;
import static android.telephony.TelephonyManager.NETWORK_TYPE_HSPA;

/**
 * Represents the neighboring cell information, including
 * Received Signal Strength and Cell ID location.
 */
public class NeighboringCellInfoWrapper implements Parcelable
{
    /**
     * Signal strength is not available
     */
    static final public int UNKNOWN_RSSI = 99;
    /**
     * Cell location is not available
     */
    static final public int UNKNOWN_CID = -1;

    /**
     * In GSM, mRssi is the Received RSSI;
     * In UMTS, mRssi is the Level index of CPICH Received Signal Code Power
     */
    private int mRssi;
    /**
     * CID in 16 bits format in GSM. Return UNKNOWN_CID in UMTS and CMDA.
     */
    private int mCid;
    /**
     * LAC in 16 bits format in GSM. Return UNKNOWN_CID in UMTS and CMDA.
     */
    private int mLac;
    /**
     * Primary Scrambling Code in 9 bits format in UMTS
     * Return UNKNOWN_CID in GSM and CMDA.
     */
    private int mPsc;
    /**
     * Radio network type, value is one of following
     * TelephonyManager.NETWORK_TYPE_XXXXXX.
     */
    private int mNetworkType;
 
     /**
      * Initialize the object from rssi, location string, and radioType
      * radioType is one of following
      * {@link TelephonyManager#NETWORK_TYPE_GPRS TelephonyManager.NETWORK_TYPE_GPRS},
      * {@link TelephonyManager#NETWORK_TYPE_EDGE TelephonyManager.NETWORK_TYPE_EDGE},
      * {@link TelephonyManager#NETWORK_TYPE_UMTS TelephonyManager.NETWORK_TYPE_UMTS},
      * {@link TelephonyManager#NETWORK_TYPE_HSDPA TelephonyManager.NETWORK_TYPE_HSDPA},
      * {@link TelephonyManager#NETWORK_TYPE_HSUPA TelephonyManager.NETWORK_TYPE_HSUPA},
      * and {@link TelephonyManager#NETWORK_TYPE_HSPA TelephonyManager.NETWORK_TYPE_HSPA}.
      */
     public NeighboringCellInfoWrapper(int rssi, String location, int radioType) {
         // set default value
         mRssi = rssi;
         mNetworkType = NETWORK_TYPE_UNKNOWN;
         mPsc = UNKNOWN_CID;
         mLac = UNKNOWN_CID;
         mCid = UNKNOWN_CID;
 
         // pad location string with leading "0"
         int l = location.length();
         if (l > 8) return;
         if (l < 8) {
             for (int i = 0; i < (8-l); i++) {
                 location = "0" + location;
             }
         }
 
         try {// set LAC/CID or PSC based on radioType
             switch (radioType) {
             case NETWORK_TYPE_GPRS:
             case NETWORK_TYPE_EDGE:
                 mNetworkType = radioType;
                 // check if 0xFFFFFFFF for UNKNOWN_CID
                 if (!location.equalsIgnoreCase("FFFFFFFF")) {
                     mCid = Integer.valueOf(location.substring(4), 16);
                     mLac = Integer.valueOf(location.substring(0, 4), 16);
                 }
                 break;
             case NETWORK_TYPE_UMTS:
             case NETWORK_TYPE_HSDPA:
             case NETWORK_TYPE_HSUPA:
             case NETWORK_TYPE_HSPA:
                 mNetworkType = radioType;
                 mPsc = Integer.valueOf(location, 16);
                 break;
             }
         } catch (NumberFormatException e) {
             // parsing location error
             mPsc = UNKNOWN_CID;
             mLac = UNKNOWN_CID;
             mCid = UNKNOWN_CID;
             mNetworkType = NETWORK_TYPE_UNKNOWN;
         }
     }
 
     /**
      * Initialize the object from a parcel.
      */
     public NeighboringCellInfoWrapper(Parcel in) {
         mRssi = in.readInt();
         mLac = in.readInt();
         mCid = in.readInt();
         mPsc = in.readInt();
         mNetworkType = in.readInt();
     }
 
     /**
      * @return received signal strength or UNKNOWN_RSSI if unknown
      *
      * For GSM, it is in "asu" ranging from 0 to 31 (dBm = -113 + 2*asu)
      * 0 means "-113 dBm or less" and 31 means "-51 dBm or greater"
      * For UMTS, it is the Level index of CPICH RSCP defined in TS 25.125
      */
     public int getRssi() {
         return mRssi;
     }
 
     /**
      * @return LAC in GSM, 0xffff max legal value
      *  UNKNOWN_CID if in UMTS or CMDA or unknown
      */
     public int getLac() {
         return mLac;
     }
 
     /**
      * @return cell id in GSM, 0xffff max legal value
      *  UNKNOWN_CID if in UMTS or CDMA or unknown
      */
     public int getCid() {
         return mCid;
     }
 
     /**
      * @return Primary Scrambling Code in 9 bits format in UMTS, 0x1ff max value
      *  UNKNOWN_CID if in GSM or CMDA or unknown
      */
     public int getPsc() {
         return mPsc;
     }
 
     /**
      * @return Radio network type while neighboring cell location is stored.
      *
      * Return {@link TelephonyManager#NETWORK_TYPE_UNKNOWN TelephonyManager.NETWORK_TYPE_UNKNOWN}
      * means that the location information is unavailable.
      *
      * Return {@link TelephonyManager#NETWORK_TYPE_GPRS TelephonyManager.NETWORK_TYPE_GPRS} or
      * {@link TelephonyManager#NETWORK_TYPE_EDGE TelephonyManager.NETWORK_TYPE_EDGE}
      * means that Neighboring Cell information is stored for GSM network, in
      * which {@link NeighboringCellInfoWrapper#getLac NeighboringCellInfoWrapper.getLac} and
      * {@link NeighboringCellInfoWrapper#getCid NeighboringCellInfoWrapper.getCid} should be
      * called to access location.
      *
      * Return {@link TelephonyManager#NETWORK_TYPE_UMTS TelephonyManager.NETWORK_TYPE_UMTS},
      * {@link TelephonyManager#NETWORK_TYPE_HSDPA TelephonyManager.NETWORK_TYPE_HSDPA},
      * {@link TelephonyManager#NETWORK_TYPE_HSUPA TelephonyManager.NETWORK_TYPE_HSUPA},
      * or {@link TelephonyManager#NETWORK_TYPE_HSPA TelephonyManager.NETWORK_TYPE_HSPA}
      * means that Neighboring Cell information is stored for UMTS network, in
      * which {@link NeighboringCellInfoWrapper#getPsc NeighboringCellInfoWrapper.getPsc}
      * should be called to access location.
      */
     public int getNetworkType() {
         return mNetworkType;
     }
 
     @Override
     public String toString() {
         StringBuilder sb = new StringBuilder();
 
         sb.append("[");
         if (mPsc != UNKNOWN_CID) {
             sb.append(Integer.toHexString(mPsc))
                     .append("@").append(((mRssi == UNKNOWN_RSSI)? "-" : mRssi));
         } else if(mLac != UNKNOWN_CID && mCid != UNKNOWN_CID) {
             sb.append(Integer.toHexString(mLac))
                     .append(Integer.toHexString(mCid))
                     .append("@").append(((mRssi == UNKNOWN_RSSI)? "-" : mRssi));
         }
         sb.append("]");
 
         return sb.toString();
     }
 
     public int describeContents() {
         return 0;
     }
 
     public void writeToParcel(Parcel dest, int flags) {
         dest.writeInt(mRssi);
         dest.writeInt(mLac);
         dest.writeInt(mCid);
         dest.writeInt(mPsc);
         dest.writeInt(mNetworkType);
     }
 
     public static final Parcelable.Creator<NeighboringCellInfoWrapper> CREATOR
     = new Parcelable.Creator<NeighboringCellInfoWrapper>() {
         public NeighboringCellInfoWrapper createFromParcel(Parcel in) {
             return new NeighboringCellInfoWrapper(in);
         }
 
         public NeighboringCellInfoWrapper[] newArray(int size) {
             return new NeighboringCellInfoWrapper[size];
         }
     };
}