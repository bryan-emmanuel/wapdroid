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
	private static final String TAG = "ManageWakeLocks";
	private static final String POWER_SERVICE = Context.POWER_SERVICE;
	private static WakeLock sWakeLock;
	private static boolean sScreenOn = true;
	static boolean hasLock() {
		return (sWakeLock != null) && (sWakeLock.isHeld());
	}
	static void acquire(Context context) {
		if (hasLock()) {
			sWakeLock.release();
		}
		if (!sScreenOn) {
			if (sWakeLock == null) {
				sWakeLock = ((PowerManager) context.getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
			}
			sWakeLock.acquire();
		}
	}
	static void release() {
		if (hasLock()) {
			sWakeLock.release();
		}
	}
	static void setScreenState(boolean screenOn) {
		sScreenOn = screenOn;
		if (screenOn && hasLock()) {
			sWakeLock.release();
		}
	}
}
