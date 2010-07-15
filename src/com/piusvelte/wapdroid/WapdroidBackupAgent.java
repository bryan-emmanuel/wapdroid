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
 
import static com.piusvelte.wapdroid.DatabaseHelper.DATABASE_NAME;

import android.app.backup.BackupAgentHelper;
import android.app.backup.FileBackupHelper;
import android.app.backup.SharedPreferencesBackupHelper;

public class WapdroidBackupAgent extends BackupAgentHelper {
	static final String BACKUP_KEY_PREFS = "prefs";
	static final String BACKUP_KEY_DB = "db";
	public void onCreate() {
		SharedPreferencesBackupHelper spbh = new SharedPreferencesBackupHelper(this, getString(R.string.key_preferences));
		addHelper(BACKUP_KEY_PREFS, spbh);
		FileBackupHelper fbh = new FileBackupHelper(this, DATABASE_NAME);
		addHelper(BACKUP_KEY_DB, fbh);
	}
}
