package com.piusvelte.wapdroid;

import static com.piusvelte.wapdroid.WapdroidDbAdapter.PAIRS_NETWORK;
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
	private static final int mNetworkAlpha = 32;
	private int mCellAlpha = 32;

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
				double scale = radius / (Math.abs(item.getRssiAvg()) - 51);
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
				WapdroidDbAdapter db = new WapdroidDbAdapter(mMap);
				db.open();
				if (pair == 0) {
					db.deleteNetwork(network);
					mMap.finish();
				} else if (mMap.mPair == 0) {
					// delete one pair from the mapped network
					db.deletePair(network, pair);
					mOverlays.remove(item);
					mMap.mMView.invalidate();
					mMap.mapData();
					dialog.cancel();
				} else {
					// delete an individually mapped cell
					db.deletePair(network, pair);
					mMap.finish();
				}
				db.close();
				db = null;
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
