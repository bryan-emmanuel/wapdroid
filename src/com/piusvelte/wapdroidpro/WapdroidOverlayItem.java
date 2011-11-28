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

package com.piusvelte.wapdroidpro;

import android.graphics.drawable.Drawable;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.OverlayItem;

class WapdroidOverlayItem extends OverlayItem {
	protected GeoPoint mPoint;
	protected String mSnippet;
	protected String mTitle;
	protected Drawable mMarker;
	protected long mNetwork = 0;
	protected int mPair = 0;
	protected int mRssi_avg = 0;
	protected int mRssi_range = 0;
	protected int mRadius = 0;
	protected long mStroke = 0;
	public WapdroidOverlayItem(GeoPoint point, String title, String snippet, long network) {
		super(point, title, snippet);
		mNetwork = network;
	}
	
	public WapdroidOverlayItem(GeoPoint point, String title, String snippet, long network, int pair, int rssi_avg, int rssi_range) {
		super(point, title, snippet);
		mRssi_avg = rssi_avg;
		mRssi_range = rssi_range;
		mNetwork = network;
		mPair = pair;
	}
	
	public long getNetwork() {
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