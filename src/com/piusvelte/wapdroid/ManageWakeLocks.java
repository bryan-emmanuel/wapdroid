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

import android.content.Context;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;

public class ManageWakeLocks {
	private static final String TAG = "Wapdroid";
	private static final String POWER_SERVICE = Context.POWER_SERVICE;
	private static WakeLock sWakeLock;
	static boolean hasLock() {
		return (sWakeLock != null);}
	static void acquire(Context context) {
		if (hasLock()) sWakeLock.release();
		PowerManager pm = (PowerManager) context.getSystemService(POWER_SERVICE);
		sWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
		sWakeLock.acquire();
	}
	static void release() {
		if (hasLock()) {
			sWakeLock.release();
			sWakeLock = null;
		}
	}
}