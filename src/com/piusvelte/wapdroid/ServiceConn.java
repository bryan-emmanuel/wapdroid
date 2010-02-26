package com.piusvelte.wapdroid;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;

public class ServiceConn implements ServiceConnection {
	public IWapdroidService mIService;
	private IWapdroidUI.Stub mWapdroidUI;
	public void setUIStub(IWapdroidUI.Stub wapdroidUI) {
		mWapdroidUI = wapdroidUI;}
	public void onServiceConnected(ComponentName className, IBinder boundService) {
		mIService = IWapdroidService.Stub.asInterface((IBinder) boundService);
		if (mWapdroidUI != null) {
			try {
				mIService.setCallback(mWapdroidUI.asBinder());}
			catch (RemoteException e) {}}}
	public void onServiceDisconnected(ComponentName className) {
		mIService = null;}}
