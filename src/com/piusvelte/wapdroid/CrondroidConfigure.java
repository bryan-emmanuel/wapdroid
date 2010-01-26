package com.piusvelte.wapdroid;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;

public class CrondroidConfigure extends Activity {
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //setContentView(R.layout.main);
		Intent startedBy = getIntent();
		if (startedBy.getAction().equals("com.piusvelte.crondroid.intent.action.CONFIGURE")) {
			Bundle extras = startedBy.getExtras();
			if (extras != null) {
				int interval = extras.getInt("com.piusvelte.crondroid.intent.action.INTERVAL");
				SharedPreferences preferences = (SharedPreferences) getSharedPreferences(getString(R.string.key_preferences), WapdroidService.MODE_PRIVATE);
				Editor editor = preferences.edit();
				if (interval > 0) {
					editor.putString(getString(R.string.key_interval), "0");}
				else {
					editor.putString(getString(R.string.key_interval), "300000");}
				setResult(RESULT_OK);}
			else {
				setResult(RESULT_CANCELED);}
			finish();}}}