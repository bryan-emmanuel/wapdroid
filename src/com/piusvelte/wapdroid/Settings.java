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

import com.piusvelte.wapdroid.R;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.CompoundButton.OnCheckedChangeListener;

public class Settings extends Activity {
	private TextView text_vibrate, text_led, text_ringtone;
	private CheckBox checkbox_notify, checkbox_vibrate, checkbox_led, checkbox_ringtone;
	private Button button_save;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.settings);
		Bundle extras = getIntent().getExtras();
		text_vibrate = (TextView) findViewById(R.id.text_vibrate);
		text_led = (TextView) findViewById(R.id.text_led);
		text_ringtone = (TextView) findViewById(R.id.text_ringtone);
    	checkbox_notify = (CheckBox) findViewById(R.id.checkbox_notify);
    	checkbox_vibrate = (CheckBox) findViewById(R.id.checkbox_vibrate);
    	checkbox_led = (CheckBox) findViewById(R.id.checkbox_led);
    	checkbox_ringtone = (CheckBox) findViewById(R.id.checkbox_ringtone);
    	button_save = (Button) findViewById(R.id.button_save);
    	checkbox_notify.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				text_vibrate.setEnabled(isChecked);
				text_led.setEnabled(isChecked);
				text_ringtone.setEnabled(isChecked);
				checkbox_vibrate.setEnabled(isChecked);
				checkbox_led.setEnabled(isChecked);
				checkbox_ringtone.setEnabled(isChecked);}});
		if (extras != null) {
		    checkbox_notify.setChecked(extras.getBoolean(WapdroidService.PREFERENCE_NOTIFY));
		    checkbox_vibrate.setChecked(extras.getBoolean(WapdroidService.PREFERENCE_VIBRATE));
		    checkbox_led.setChecked(extras.getBoolean(WapdroidService.PREFERENCE_LED));
		    checkbox_ringtone.setChecked(extras.getBoolean(WapdroidService.PREFERENCE_RINGTONE));}
		button_save.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
		    	Bundle bundle = new Bundle();
		    	bundle.putBoolean(WapdroidService.PREFERENCE_NOTIFY, checkbox_notify.isChecked());
		    	bundle.putBoolean(WapdroidService.PREFERENCE_VIBRATE, checkbox_vibrate.isChecked());
		    	bundle.putBoolean(WapdroidService.PREFERENCE_LED, checkbox_led.isChecked());
		    	bundle.putBoolean(WapdroidService.PREFERENCE_RINGTONE, checkbox_ringtone.isChecked());
		    	Intent mIntent = new Intent();
		    	mIntent.putExtras(bundle);
		    	setResult(RESULT_OK, mIntent);
		    	finish();}});}}