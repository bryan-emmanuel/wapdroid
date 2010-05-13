package com.piusvelte.wapdroid;

import java.util.ArrayList;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.drawable.Drawable;

import com.google.android.maps.ItemizedOverlay;
import com.google.android.maps.OverlayItem;

public class CellOverlay extends ItemizedOverlay<OverlayItem> {
	private ArrayList<OverlayItem> mOverlays = new ArrayList<OverlayItem>();
	private Context mContext;

	public CellOverlay(Drawable defaultMarker) {
		super(boundCenterBottom(defaultMarker));}
	
	public CellOverlay(Drawable defaultMarker, Context context) {
		super(defaultMarker);
		mContext = context;}

	@Override
	protected OverlayItem createItem(int i) {
		return mOverlays.get(i);}

	@Override
	public int size() {
		return mOverlays.size();}
	
	public void addOverlay(OverlayItem overlay) {
		mOverlays.add(overlay);
		populate();}
	
	@Override
	protected boolean onTap(int i) {
		OverlayItem item = mOverlays.get(i);
		AlertDialog.Builder dialog = new AlertDialog.Builder(mContext);
		dialog.setTitle(item.getTitle());
		dialog.setMessage(item.getSnippet());
		dialog.show();
		return true;}}
