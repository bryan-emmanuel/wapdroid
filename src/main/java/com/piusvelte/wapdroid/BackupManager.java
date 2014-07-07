/*
 * Wapdroid - Android Location based Wifi Manager
 * Copyright (C) 2012 Bryan Emmanuel
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

import android.annotation.TargetApi;
import android.content.Context;

@TargetApi(8)
public class BackupManager {
	
	static {
		try {
			Class.forName("android.app.backup.BackupManager");
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}
	
	public static void dataChanged(Context context) {
		(new android.app.backup.BackupManager(context)).dataChanged();
	}
}
