package com.piusvelte.wapdroid;

import android.content.Context;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;

public class ManageWakeLocks {
	private static final String TAG = "WapdroidWakeLock";
	private static final String POWER_SERVICE = Context.POWER_SERVICE;
	private static WakeLock sWakeLock;
	static void acquire(Context context) {
		if (sWakeLock != null) {
			sWakeLock.release();}
		PowerManager pm = (PowerManager) context.getSystemService(POWER_SERVICE);
		sWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
		sWakeLock.acquire();}
	static void release() {
		if (sWakeLock != null) {
			sWakeLock.release();
			sWakeLock = null;}}}