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

 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *  
 *  Bryan Emmanuel piusvelte@gmail.com
 */

package com.piusvelte.wapdroidpro;

import android.telephony.CellLocation;

public class CdmaCellLocation {
	private int mBaseStationId,
	mNetworkId;
	
	static {
		try {
			Class.forName("android.telephony.cdma.CdmaCellLocation");
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}
	
	public CdmaCellLocation(CellLocation location) {
		android.telephony.cdma.CdmaCellLocation cdma = (android.telephony.cdma.CdmaCellLocation) location;
		mBaseStationId = cdma.getBaseStationId();
		mNetworkId = cdma.getNetworkId();
	}
	
	public int getBaseStationId() {
		return mBaseStationId;
	}
	
	public int getNetworkId() {
		return mNetworkId;
	}
	
}
