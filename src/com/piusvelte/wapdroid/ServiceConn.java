/*
 * Wapdroid - Android Location based Wifi Manager
 * Copyright (C) 2009 Bryan Emmanuel
 * 
 * This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *  
 *  Bryan Emmanuel piusvelte@gmail.com
 */

package com.piusvelte.wapdroid;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;

public class ServiceConn implements ServiceConnection {
	public IWapdroidService mIService;
	private IWapdroidUI.Stub mWapdroidUI;
	public ServiceConn() {}
	public ServiceConn(IWapdroidUI.Stub wapdroidUI) {
		mWapdroidUI = wapdroidUI;
	}
	//@Override
	public void onServiceConnected(ComponentName className, IBinder boundService) {
		mIService = IWapdroidService.Stub.asInterface((IBinder) boundService);
		if (mWapdroidUI != null) {
			try {
				mIService.setCallback(mWapdroidUI.asBinder());
			} catch (RemoteException e) {}
		}
	}
	//@Override
	public void onServiceDisconnected(ComponentName className) {
		mIService = null;
	}
}
