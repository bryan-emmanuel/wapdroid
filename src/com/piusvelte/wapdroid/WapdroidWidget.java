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

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.RemoteViews;

public class WapdroidWidget extends AppWidgetProvider {
	private static final String TAG = "WapdroidWidget";
	
	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
		// check the service state, and update widget
		Log.v(TAG,"onUpdate");
		RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget);
		SharedPreferences sp = context.getSharedPreferences(context.getString(R.string.key_preferences), Context.MODE_PRIVATE);
		if (sp.getBoolean(context.getString(R.string.key_manageWifi), false)) {
			views.setImageViewResource(R.id.widget_icon, R.drawable.statuson);
			views.setTextViewText(R.id.widget_status, context.getString(R.string.widget_on));
		} else {
			views.setImageViewResource(R.id.widget_icon, R.drawable.status);
			views.setTextViewText(R.id.widget_status, context.getString(R.string.widget_off));			
		}
		views.setOnClickPendingIntent(R.id.widget, PendingIntent.getActivity(context, 0, new Intent(context, Settings.class), 0));
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		final String action = intent.getAction();
		Log.v(TAG, action);
		if (action.equals(Wapdroid.ACTION_TOGGLE_SERVICE)) {
			SharedPreferences sp = context.getSharedPreferences(context.getString(R.string.key_preferences), Context.MODE_PRIVATE);
			sp.edit().putBoolean(context.getString(R.string.key_manageWifi), !sp.getBoolean(context.getString(R.string.key_manageWifi), false));
			sp.edit().commit();
		} else super.onReceive(context, intent);
	}

}
