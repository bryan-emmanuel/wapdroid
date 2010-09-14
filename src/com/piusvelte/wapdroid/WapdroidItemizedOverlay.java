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

import static com.piusvelte.wapdroid.WapdroidService.CELLS_CID;
import static com.piusvelte.wapdroid.WapdroidService.CELLS_LOCATION;
import static com.piusvelte.wapdroid.WapdroidService.PAIRS_CELL;
import static com.piusvelte.wapdroid.WapdroidService.PAIRS_NETWORK;
import static com.piusvelte.wapdroid.WapdroidService.TAG;
import static com.piusvelte.wapdroid.WapdroidService.TABLE_CELLS;
import static com.piusvelte.wapdroid.WapdroidService.TABLE_ID;
import static com.piusvelte.wapdroid.WapdroidService.TABLE_LOCATIONS;
import static com.piusvelte.wapdroid.WapdroidService.TABLE_NETWORKS;
import static com.piusvelte.wapdroid.WapdroidService.TABLE_PAIRS;
import static com.piusvelte.wapdroid.MapData.color_primary;
import static com.piusvelte.wapdroid.MapData.color_secondary;
import static com.piusvelte.wapdroid.MapData.drawable_cell;
import static com.piusvelte.wapdroid.MapData.drawable_network;
import static com.piusvelte.wapdroid.MapData.string_deleteCell;
import static com.piusvelte.wapdroid.MapData.string_deleteNetwork;
import static com.piusvelte.wapdroid.MapData.string_cancel;

import java.util.ArrayList;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.util.Log;
//import android.util.Log;

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
			if (item.getTitle() == PAIRS_NETWORK) {
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
			if (item.getTitle() != PAIRS_NETWORK) {
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
				DatabaseHelper dh = new DatabaseHelper(mMap);
				SQLiteDatabase sd = null;
				try {
					sd = dh.getWritableDatabase();
				} catch (SQLException se) {
					Log.e(TAG,"unexpected " + se);
				}
				if (sd.isOpen()) {
					//				App a = (App) mMap.getApplication();
					//				if (a.mDb.isOpen()) {
					if (pair == 0) {
						sd.delete(TABLE_NETWORKS, TABLE_ID + "=" + network, null);
						sd.delete(TABLE_PAIRS, PAIRS_NETWORK + "=" + network, null);
						Cursor c = sd.rawQuery("select " + TABLE_ID + ", " + CELLS_LOCATION + " from " + TABLE_CELLS, null);
						if (c.getCount() > 0) {
							c.moveToFirst();
							while (!c.isAfterLast()) {
								int cell = c.getInt(c.getColumnIndex(TABLE_ID));
								Cursor p = sd.rawQuery("select " + TABLE_ID + " from " + TABLE_PAIRS + " where " + PAIRS_CELL + "=" + cell, null);
								if (p.getCount() == 0) {
									sd.delete(TABLE_CELLS, TABLE_ID + "=" + cell, null);
									int location = c.getInt(c.getColumnIndex(CELLS_LOCATION));
									Cursor l = sd.rawQuery("select " + TABLE_ID + " from " + TABLE_CELLS + " where " + CELLS_LOCATION + "=" + location, null);
									if (l.getCount() == 0) sd.delete(TABLE_LOCATIONS, TABLE_ID + "=" + location, null);
									l.close();
								}
								p.close();
								c.moveToNext();
							}
						}
						c.close();
						mMap.finish();
					} else if (mMap.mPair == 0) {
						// delete one pair from the mapped network
						sd.delete(TABLE_PAIRS, TABLE_ID + "=" + pair, null);
						Cursor n = sd.rawQuery("select " + TABLE_PAIRS + "." + TABLE_ID + " as " + TABLE_ID + ", " + CELLS_CID
								+ " from " + TABLE_PAIRS + ", " + TABLE_CELLS
								+ " where " + PAIRS_CELL + "=" + TABLE_CELLS + "." + TABLE_ID
								+ " and "+ PAIRS_NETWORK + "=" + network, null);
						if (n.getCount() == 0) sd.delete(TABLE_NETWORKS, TABLE_ID + "=" + network, null);
						n.close();
						Cursor c = sd.rawQuery("select " + TABLE_ID + ", " + CELLS_LOCATION + " from " + TABLE_CELLS, null);
						if (c.getCount() > 0) {
							c.moveToFirst();
							while (!c.isAfterLast()) {
								int cell = c.getInt(c.getColumnIndex(TABLE_ID));
								Cursor p = sd.rawQuery("select " + TABLE_ID + " from " + TABLE_PAIRS + " where " + PAIRS_CELL + "=" + cell, null);
								if (p.getCount() == 0) {
									sd.delete(TABLE_CELLS, TABLE_ID + "=" + cell, null);
									int location = c.getInt(c.getColumnIndex(CELLS_LOCATION));
									Cursor l = sd.rawQuery("select " + TABLE_ID + " from " + TABLE_CELLS + " where " + CELLS_LOCATION + "=" + location, null);
									if (l.getCount() == 0) sd.delete(TABLE_LOCATIONS, TABLE_ID + "=" + location, null);
									l.close();
								}
								p.close();
								c.moveToNext();
							}
						}
						c.close();
						//						a.deletePair(network, pair);
						mOverlays.remove(item);
						mMap.mMView.invalidate();
						mMap.mapData();
						dialog.cancel();
					} else {
						// delete an individually mapped cell
						sd.delete(TABLE_PAIRS, TABLE_ID + "=" + pair, null);
						Cursor n = sd.rawQuery("select " + TABLE_PAIRS + "." + TABLE_ID + " as " + TABLE_ID + ", " + CELLS_CID
								+ " from " + TABLE_PAIRS + ", " + TABLE_CELLS
								+ " where " + PAIRS_CELL + "=" + TABLE_CELLS + "." + TABLE_ID
								+ " and "+ PAIRS_NETWORK + "=" + network, null);
						if (n.getCount() == 0) sd.delete(TABLE_NETWORKS, TABLE_ID + "=" + network, null);
						n.close();
						Cursor c = sd.rawQuery("select " + TABLE_ID + ", " + CELLS_LOCATION + " from " + TABLE_CELLS, null);
						if (c.getCount() > 0) {
							c.moveToFirst();
							while (!c.isAfterLast()) {
								int cell = c.getInt(c.getColumnIndex(TABLE_ID));
								Cursor p = sd.rawQuery("select " + TABLE_ID + " from " + TABLE_PAIRS + " where " + PAIRS_CELL + "=" + cell, null);
								if (p.getCount() == 0) {
									sd.delete(TABLE_CELLS, TABLE_ID + "=" + cell, null);
									int location = c.getInt(c.getColumnIndex(CELLS_LOCATION));
									Cursor l = sd.rawQuery("select " + TABLE_ID + " from " + TABLE_CELLS + " where " + CELLS_LOCATION + "=" + location, null);
									if (l.getCount() == 0) sd.delete(TABLE_LOCATIONS, TABLE_ID + "=" + location, null);
									l.close();
								}
								p.close();
								c.moveToNext();
							}
						}
						c.close();
						//						a.deletePair(network, pair);
						mMap.finish();
					}
					sd.close();
				} else Log.e(TAG, "database unavailable");
				dh.close();
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
