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
import android.widget.RemoteViews;

public class WapdroidWidget extends AppWidgetProvider {

	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
		// check the service state, and update widget
		for (int appWidgetId : appWidgetIds) {
			RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget);
			SharedPreferences sp = context.getSharedPreferences(context.getString(R.string.key_preferences), Context.MODE_PRIVATE);
			if (sp.getBoolean(context.getString(R.string.key_manageWifi), false)) {
				views.setImageViewResource(R.id.widget_icon, R.drawable.statuson);
				views.setTextViewText(R.id.widget_status, context.getString(R.string.widget_on));
			} else {
				views.setImageViewResource(R.id.widget_icon, R.drawable.status);
				views.setTextViewText(R.id.widget_status, context.getString(R.string.widget_off));			
			}
			views.setOnClickPendingIntent(R.id.widget, PendingIntent.getBroadcast(context, 0, new Intent(context, WapdroidWidget.class).setAction(Wapdroid.ACTION_TOGGLE_SERVICE), 0));
			appWidgetManager.updateAppWidget(appWidgetId, views);
		}
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		if (intent.getAction().equals(Wapdroid.ACTION_TOGGLE_SERVICE)) {
			SharedPreferences sp = context.getSharedPreferences(context.getString(R.string.key_preferences), Context.MODE_PRIVATE);
			boolean state = sp.getBoolean(context.getString(R.string.key_manageWifi), false);
			sp.edit().putBoolean(context.getString(R.string.key_manageWifi), !state);
			sp.edit().commit();
			if (!state) context.startService(new Intent(context, WapdroidService.class));
			else context.stopService(new Intent(context, WapdroidService.class));
		} else super.onReceive(context, intent);
	}

}
