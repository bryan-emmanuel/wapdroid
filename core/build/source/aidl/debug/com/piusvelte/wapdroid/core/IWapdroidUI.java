/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Original file: /Users/bemmanuel/Documents/Development/personal/Wapdroid/core/src/com/piusvelte/wapdroid/core/IWapdroidUI.aidl
 */
package com.piusvelte.wapdroid.core;
public interface IWapdroidUI extends android.os.IInterface
{
/** Local-side IPC implementation stub class. */
public static abstract class Stub extends android.os.Binder implements com.piusvelte.wapdroid.core.IWapdroidUI
{
private static final java.lang.String DESCRIPTOR = "com.piusvelte.wapdroid.core.IWapdroidUI";
/** Construct the stub at attach it to the interface. */
public Stub()
{
this.attachInterface(this, DESCRIPTOR);
}
/**
 * Cast an IBinder object into an com.piusvelte.wapdroid.core.IWapdroidUI interface,
 * generating a proxy if needed.
 */
public static com.piusvelte.wapdroid.core.IWapdroidUI asInterface(android.os.IBinder obj)
{
if ((obj==null)) {
return null;
}
android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
if (((iin!=null)&&(iin instanceof com.piusvelte.wapdroid.core.IWapdroidUI))) {
return ((com.piusvelte.wapdroid.core.IWapdroidUI)iin);
}
return new com.piusvelte.wapdroid.core.IWapdroidUI.Stub.Proxy(obj);
}
@Override public android.os.IBinder asBinder()
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
case TRANSACTION_setCellInfo:
{
data.enforceInterface(DESCRIPTOR);
int _arg0;
_arg0 = data.readInt();
int _arg1;
_arg1 = data.readInt();
this.setCellInfo(_arg0, _arg1);
reply.writeNoException();
return true;
}
case TRANSACTION_setCells:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _arg0;
_arg0 = data.readString();
this.setCells(_arg0);
reply.writeNoException();
return true;
}
case TRANSACTION_setBattery:
{
data.enforceInterface(DESCRIPTOR);
int _arg0;
_arg0 = data.readInt();
this.setBattery(_arg0);
reply.writeNoException();
return true;
}
case TRANSACTION_setWifiInfo:
{
data.enforceInterface(DESCRIPTOR);
int _arg0;
_arg0 = data.readInt();
java.lang.String _arg1;
_arg1 = data.readString();
java.lang.String _arg2;
_arg2 = data.readString();
this.setWifiInfo(_arg0, _arg1, _arg2);
reply.writeNoException();
return true;
}
case TRANSACTION_setSignalStrength:
{
data.enforceInterface(DESCRIPTOR);
int _arg0;
_arg0 = data.readInt();
this.setSignalStrength(_arg0);
reply.writeNoException();
return true;
}
}
return super.onTransact(code, data, reply, flags);
}
private static class Proxy implements com.piusvelte.wapdroid.core.IWapdroidUI
{
private android.os.IBinder mRemote;
Proxy(android.os.IBinder remote)
{
mRemote = remote;
}
@Override public android.os.IBinder asBinder()
{
return mRemote;
}
public java.lang.String getInterfaceDescriptor()
{
return DESCRIPTOR;
}
@Override public void setCellInfo(int cid, int lac) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeInt(cid);
_data.writeInt(lac);
mRemote.transact(Stub.TRANSACTION_setCellInfo, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
@Override public void setCells(java.lang.String cells) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeString(cells);
mRemote.transact(Stub.TRANSACTION_setCells, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
@Override public void setBattery(int batteryPercentage) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeInt(batteryPercentage);
mRemote.transact(Stub.TRANSACTION_setBattery, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
@Override public void setWifiInfo(int state, java.lang.String ssid, java.lang.String bssid) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeInt(state);
_data.writeString(ssid);
_data.writeString(bssid);
mRemote.transact(Stub.TRANSACTION_setWifiInfo, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
@Override public void setSignalStrength(int rssi) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeInt(rssi);
mRemote.transact(Stub.TRANSACTION_setSignalStrength, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
}
static final int TRANSACTION_setCellInfo = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
static final int TRANSACTION_setCells = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
static final int TRANSACTION_setBattery = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
static final int TRANSACTION_setWifiInfo = (android.os.IBinder.FIRST_CALL_TRANSACTION + 3);
static final int TRANSACTION_setSignalStrength = (android.os.IBinder.FIRST_CALL_TRANSACTION + 4);
}
public void setCellInfo(int cid, int lac) throws android.os.RemoteException;
public void setCells(java.lang.String cells) throws android.os.RemoteException;
public void setBattery(int batteryPercentage) throws android.os.RemoteException;
public void setWifiInfo(int state, java.lang.String ssid, java.lang.String bssid) throws android.os.RemoteException;
public void setSignalStrength(int rssi) throws android.os.RemoteException;
}
