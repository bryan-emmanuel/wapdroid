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

package com.piusvelte.wapdroid;

import android.os.Bundle;
import android.telephony.CellLocation;
import android.telephony.cdma.CdmaCellLocation;

public class CdmaCellLocationWrapper extends CellLocation {
	private CdmaCellLocation mCdmaCellLocation;
	static {
		try {
			Class.forName("CdmaCellLocation");}
		catch (Exception ex) {
			throw new RuntimeException(ex);}}
	// initialize
	public static void checkAvailable() {}
	public CdmaCellLocationWrapper(Bundle bundleWithValues) {
		mCdmaCellLocation = new CdmaCellLocation(bundleWithValues);}
	public int getBaseStationId() {
		return mCdmaCellLocation.getBaseStationId();}}
