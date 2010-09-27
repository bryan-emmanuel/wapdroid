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

import static com.piusvelte.wapdroid.Wapdroid.Pairs;
import static com.piusvelte.wapdroid.MapData.color_primary;
import static com.piusvelte.wapdroid.MapData.color_secondary;
import static com.piusvelte.wapdroid.MapData.drawable_cell;
import static com.piusvelte.wapdroid.MapData.drawable_network;
import static com.piusvelte.wapdroid.MapData.string_deleteCell;
import static com.piusvelte.wapdroid.MapData.string_deleteNetwork;
import static com.piusvelte.wapdroid.MapData.string_cancel;

import java.util.ArrayList;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.location.Location;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.ItemizedOverlay;
import com.google.android.maps.MapView;
import com.google.android.maps.Projection;
import com.piusvelte.wapdroid.Wapdroid.Cells;
import com.piusvelte.wapdroid.Wapdroid.Locations;
import com.piusvelte.wapdroid.Wapdroid.Networks;
import com.piusvelte.wapdroid.Wapdroid.Pairs;

public class WapdroidItemizedOverlay extends ItemizedOverlay<WapdroidOverlayItem> {
	private ArrayList<WapdroidOverlayItem> mOverlays = new ArrayList<WapdroidOverlayItem>();
	private MapData mMap;
	private static final int mNetworkAlpha = 64;
	private int mCellAlpha = 64;

	public WapdroidItemizedOverlay(MapData map, int cellCount) {
		super(boundCenterBottom(drawable_cell));
		mMap = map;
		mCellAlpha = Math.round(mNetworkAlpha / cellCount);
	}

	@Override
	public void draw(Canvas canvas, MapView mapView, boolean shadow) {
		for (WapdroidOverlayItem item : mOverlays) {
			int radius = 0;
			Paint paint = new Paint();
			GeoPoint gpt = item.getPoint();
			Point pt = new Point();
			Projection projection = mapView.getProjection();
			projection.toPixels(gpt, pt);
			double mercator = Math.cos(Math.toRadians(gpt.getLatitudeE6()/1E6));
			if (item.getTitle() == Pairs.NETWORK) {
				radius = 70;
				paint.setColor(color_primary);
				paint.setAlpha(mNetworkAlpha);
			} else {
				long stroke = item.getStroke();
				radius = item.getRadius();
				paint.setColor(color_secondary);
				if (stroke == 0) {
					paint.setAlpha(mCellAlpha);
					paint.setStyle(Paint.Style.FILL);
				}
				else {
					paint.setAlpha(mCellAlpha);
					paint.setStyle(Paint.Style.STROKE);
					paint.setStrokeWidth(projection.metersToEquatorPixels(Math.round(stroke/mercator)));
				}
			}
			canvas.drawCircle(pt.x, pt.y, projection.metersToEquatorPixels(Math.round(radius/mercator)), paint);
		}
		super.draw(canvas, mapView, shadow);
	}

	@Override
	protected WapdroidOverlayItem createItem(int i) {
		return mOverlays.get(i);
	}

	@Override
	public int size() {
		return mOverlays.size();
	}

	public void addOverlay(WapdroidOverlayItem overlay) {
		mOverlays.add(overlay);
		populate();
	}

	public void addOverlay(WapdroidOverlayItem overlay, Drawable marker) {
		overlay.setMarker(boundCenterBottom(marker));
		addOverlay(overlay);
	}

	public void setDistances(Location location) {
		for (WapdroidOverlayItem item : mOverlays) {
			if (item.getTitle() != Pairs.NETWORK) {
				GeoPoint gpt = item.getPoint();
				Location cell = new Location("");
				cell.setLatitude(gpt.getLatitudeE6()/1e6);
				cell.setLongitude(gpt.getLongitudeE6()/1e6);
				int radius = Math.round(location.distanceTo(cell));
				// avoid divide by 0
				double scale = (Math.abs(item.getRssiAvg()) - 51) > 0 ? radius / (Math.abs(item.getRssiAvg()) - 51) : radius;
				item.setRadius(radius);
				item.setStroke(Math.round(item.getRssiRange() * scale));
			}
		}
	}

	@Override
	protected boolean onTap(int i) {
		final int item = i;
		WapdroidOverlayItem overlay = mOverlays.get(item);
		final int network = overlay.getNetwork();
		final int pair = overlay.getPair();
		AlertDialog.Builder dialog = new AlertDialog.Builder(mMap);
		dialog.setIcon(pair == 0 ? drawable_network : drawable_cell);
		dialog.setTitle(overlay.getTitle());
		dialog.setMessage(overlay.getSnippet());
		dialog.setPositiveButton(pair == 0 ? string_deleteNetwork : string_deleteCell, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				ContentResolver resolver = mMap.getContentResolver();
				String[] projection;
				if (pair == 0) {
					resolver.delete(Networks.CONTENT_URI, Networks._ID + "=" + network, null);
					resolver.delete(Pairs.CONTENT_URI, Pairs.NETWORK + "=" + network, null);
				} else {
					// delete one pair from the mapped network or 
					// delete an individually mapped cell
					resolver.delete(Pairs.CONTENT_URI, Pairs._ID + "=" + pair, null);
					projection = new String[]{Pairs._ID};
					Cursor n = resolver.query(Pairs.CONTENT_URI, projection, Pairs.NETWORK + "=" + network, null, null);
					if (n.getCount() == 0) resolver.delete(Networks.CONTENT_URI, Networks._ID + "=" + network, null);
					n.close();
				}
				projection = new String[]{Cells._ID, Cells.LOCATION};
				Cursor c = resolver.query(Cells.CONTENT_URI, projection, null, null, null);
				if (c.getCount() > 0) {
					c.moveToFirst();
					int[] index = {c.getColumnIndex(Cells._ID), c.getColumnIndex(Cells.LOCATION)};
					while (!c.isAfterLast()) {
						int cell = c.getInt(index[0]);
						projection = new String[]{Pairs._ID};
						Cursor p = resolver.query(Pairs.CONTENT_URI, projection, Pairs.CELL + "=" + cell, null, null);
						if (p.getCount() == 0) {
							resolver.delete(Cells.CONTENT_URI, Cells._ID + "=" + cell, null);
							int location = c.getInt(index[1]);
							projection = new String[]{Cells.LOCATION};
							Cursor l = resolver.query(Cells.CONTENT_URI, projection, Cells.LOCATION + "=" + location, null, null);
							if (l.getCount() == 0) resolver.delete(Locations.CONTENT_URI, Locations._ID + "=" + location, null);
							l.close();
						}
						p.close();
						c.moveToNext();
					}
				}
				c.close();
				if (pair == 0) mMap.finish();
				else {
					if (mMap.mPair == 0) {
						mOverlays.remove(item);
						mMap.mMView.invalidate();
						mMap.mapData();
						dialog.cancel();
					} else mMap.finish();
				}
			}
		});
		dialog.setNegativeButton(string_cancel, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.cancel();
			}
		});
		dialog.show();
		return true;
	}
}
