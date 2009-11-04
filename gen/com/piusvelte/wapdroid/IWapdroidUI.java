/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Original file: /home/bryan/Documents/development/android/wapdroid/src/com/piusvelte/wapdroid/IWapdroidUI.aidl
 */
package com.piusvelte.wapdroid;
import java.lang.String;
import android.os.RemoteException;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Binder;
import android.os.Parcel;
public interface IWapdroidUI extends android.os.IInterface
{
/** Local-side IPC implementation stub class. */
public static abstract class Stub extends android.os.Binder implements com.piusvelte.wapdroid.IWapdroidUI
{
private static final java.lang.String DESCRIPTOR = "com.piusvelte.wapdroid.IWapdroidUI";
/** Construct the stub at attach it to the interface. */
public Stub()
{
this.attachInterface(this, DESCRIPTOR);
}
/**
 * Cast an IBinder object into an IWapdroidUI interface,
 * generating a proxy if needed.
 */
public static com.piusvelte.wapdroid.IWapdroidUI asInterface(android.os.IBinder obj)
{
if ((obj==null)) {
return null;
}
android.os.IInterface iin = (android.os.IInterface)obj.queryLocalInterface(DESCRIPTOR);
if (((iin!=null)&&(iin instanceof com.piusvelte.wapdroid.IWapdroidUI))) {
return ((com.piusvelte.wapdroid.IWapdroidUI)iin);
}
return new com.piusvelte.wapdroid.IWapdroidUI.Stub.Proxy(obj);
}
public android.os.IBinder asBinder()
{
return this;
}
@Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
{
switch (code)
{
case INTERFACE_TRANSACTION:
{
reply.writeString(DESCRIPTOR);
return true;
}
case TRANSACTION_setCellLocation:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _arg0;
_arg0 = data.readString();
java.lang.String _arg1;
_arg1 = data.readString();
java.lang.String _arg2;
_arg2 = data.readString();
java.lang.String _arg3;
_arg3 = data.readString();
this.setCellLocation(_arg0, _arg1, _arg2, _arg3);
reply.writeNoException();
return true;
}
case TRANSACTION_setSignalStrength:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _arg0;
_arg0 = data.readString();
this.setSignalStrength(_arg0);
reply.writeNoException();
return true;
}
}
return super.onTransact(code, data, reply, flags);
}
private static class Proxy implements com.piusvelte.wapdroid.IWapdroidUI
{
private android.os.IBinder mRemote;
Proxy(android.os.IBinder remote)
{
mRemote = remote;
}
public android.os.IBinder asBinder()
{
return mRemote;
}
public java.lang.String getInterfaceDescriptor()
{
return DESCRIPTOR;
}
public void setCellLocation(java.lang.String mCID, java.lang.String mLAC, java.lang.String mMNC, java.lang.String mMCC) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeString(mCID);
_data.writeString(mLAC);
_data.writeString(mMNC);
_data.writeString(mMCC);
mRemote.transact(Stub.TRANSACTION_setCellLocation, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public void setSignalStrength(java.lang.String mRSSI) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeString(mRSSI);
mRemote.transact(Stub.TRANSACTION_setSignalStrength, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
}
static final int TRANSACTION_setCellLocation = (IBinder.FIRST_CALL_TRANSACTION + 0);
static final int TRANSACTION_setSignalStrength = (IBinder.FIRST_CALL_TRANSACTION + 1);
}
public void setCellLocation(java.lang.String mCID, java.lang.String mLAC, java.lang.String mMNC, java.lang.String mMCC) throws android.os.RemoteException;
public void setSignalStrength(java.lang.String mRSSI) throws android.os.RemoteException;
}
