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

import static com.piusvelte.wapdroidpro.MapData.color_primary;
import static com.piusvelte.wapdroidpro.MapData.color_secondary;
import static com.piusvelte.wapdroidpro.MapData.drawable_cell;
import static com.piusvelte.wapdroidpro.MapData.drawable_network;
import static com.piusvelte.wapdroidpro.MapData.string_cancel;
import static com.piusvelte.wapdroidpro.MapData.string_deleteCell;
import static com.piusvelte.wapdroidpro.MapData.string_deleteNetwork;

import java.util.ArrayList;

import android.app.AlertDialog;
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
			if (item.getTitle() == Wapdroid.Ranges.NETWORK) {
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
				} else {
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
			if (item.getTitle() != Wapdroid.Ranges.NETWORK) {
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
		final long network = overlay.getNetwork();
		final int pair = overlay.getPair();
		AlertDialog.Builder dialog = new AlertDialog.Builder(mMap);
		dialog.setIcon(pair == 0 ? drawable_network : drawable_cell);
		dialog.setTitle(overlay.getTitle());
		dialog.setMessage(overlay.getSnippet());
		dialog.setPositiveButton(pair == 0 ? string_deleteNetwork : string_deleteCell, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				if (pair == 0) {
					mMap.getContentResolver().delete(Wapdroid.Networks.CONTENT_URI, Wapdroid.Networks._ID + "=?", new String[]{Long.toString(network)});
					mMap.getContentResolver().delete(Wapdroid.Pairs.CONTENT_URI, Wapdroid.Pairs.NETWORK + "=?", new String[]{Long.toString(network)});
				} else {
					// delete one pair from the mapped network or 
					// delete an individually mapped cell
					mMap.getContentResolver().delete(Wapdroid.Pairs.CONTENT_URI, Wapdroid.Pairs._ID + "=?", new String[]{Integer.toString(pair)});
					Cursor n = mMap.getContentResolver().query(Wapdroid.Pairs.CONTENT_URI, new String[]{Wapdroid.Pairs._ID}, Wapdroid.Pairs.NETWORK + "=?", new String[]{Long.toString(network)}, null);
					if (n.getCount() == 0) mMap.getContentResolver().delete(Wapdroid.Networks.CONTENT_URI, Wapdroid.Networks._ID + "=?", new String[]{Long.toString(network)});
					n.close();
				}
				Cursor c = mMap.getContentResolver().query(Wapdroid.Cells.CONTENT_URI, new String[]{Wapdroid.Cells._ID, Wapdroid.Cells.LOCATION}, null, null, null);
				if (c.moveToFirst()) {
					int[] index = {c.getColumnIndex(Wapdroid.Cells._ID), c.getColumnIndex(Wapdroid.Cells.LOCATION)};
					while (!c.isAfterLast()) {
						int cell = c.getInt(index[0]);
						Cursor p = mMap.getContentResolver().query(Wapdroid.Pairs.CONTENT_URI, new String[]{Wapdroid.Pairs._ID}, Wapdroid.Pairs.CELL + "=?", new String[]{Integer.toString(cell)}, null);
						if (p.getCount() == 0) {
							mMap.getContentResolver().delete(Wapdroid.Cells.CONTENT_URI, Wapdroid.Cells._ID + "=" + cell, null);
							int location = c.getInt(index[1]);
							Cursor l = mMap.getContentResolver().query(Wapdroid.Cells.CONTENT_URI, new String[]{Wapdroid.Cells.LOCATION}, Wapdroid.Cells.LOCATION + "=?", new String[]{Integer.toString(location)}, null);
							if (l.getCount() == 0) mMap.getContentResolver().delete(Wapdroid.Locations.CONTENT_URI, Wapdroid.Locations._ID + "=?", new String[]{Integer.toString(location)});
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
