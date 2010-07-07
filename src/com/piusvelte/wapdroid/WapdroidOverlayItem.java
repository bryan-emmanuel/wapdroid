package com.piusvelte.wapdroid;

import android.graphics.drawable.Drawable;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.OverlayItem;

class WapdroidOverlayItem extends OverlayItem {
	protected GeoPoint mPoint;
	protected String mSnippet;
	protected String mTitle;
	protected Drawable mMarker;
	protected int mNetwork = 0;
	protected int mPair = 0;
	protected int mRssi_avg = 0;
	protected int mRssi_range = 0;
	protected int mRadius = 0;
	protected long mStroke = 0;
	public WapdroidOverlayItem(GeoPoint point, String title, String snippet, int network) {
		super(point, title, snippet);
		mNetwork = network;
	}
	
	public WapdroidOverlayItem(GeoPoint point, String title, String snippet, int network, int pair, int rssi_avg, int rssi_range) {
		super(point, title, snippet);
		mRssi_avg = rssi_avg;
		mRssi_range = rssi_range;
		mNetwork = network;
		mPair = pair;
	}
	
	public int getNetwork() {
		return mNetwork;
	}
	
	public int getPair() {
		return mPair;
	}
	
	public int getRssiAvg() {
		return mRssi_avg;
	}
	
	public int getRssiRange() {
		return mRssi_range;
	}
	
	public void setRadius(int radius) {
		mRadius = radius;
	}
	
	public int getRadius() {
		return mRadius;
	}
	
	public void setStroke(long stroke) {
		mStroke = stroke;
	}
	
	public long getStroke() {
		return mStroke;
	}
}